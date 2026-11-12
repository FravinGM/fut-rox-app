package br.futrox.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Cliente mínimo do Supabase usado pelo site https://fut-rox.vercel.app.
 * A chave "publishable" é pública (já vai no bundle do site); quem protege os dados é o login + RLS do banco.
 */
class Api(context: Context) {
    private val store = SecureStore(context)

    /** Token de acesso em memória: evita descriptografar a cada requisição. */
    @Volatile private var accessCache: String? = null
    private val refreshLock = Any()

    val hasSession get() = store.has(K_REFRESH)

    /** Apaga a sessão local na hora e devolve o token para revogação no servidor. */
    fun logout(): String? {
        val token = accessCache ?: store.get(K_ACCESS)
        accessCache = null
        store.clear()
        return token
    }

    /** Invalida a sessão também no Supabase (melhor esforço, chamar fora da thread principal). */
    fun revoke(token: String?) {
        if (token == null) return
        runCatching { request("POST", "/auth/v1/logout", JSONObject(), bearer = token) }
    }

    fun login(email: String, password: String) {
        val mail = email.trim()
        if (mail.length > 254 || !EMAIL.matches(mail)) throw ApiException("Confira o endereço de e-mail.")
        if (password.isEmpty() || password.length > 72) throw ApiException("Confira a senha.")

        val res = request(
            "POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", mail).put("password", password), bearer = SUPABASE_KEY,
        )
        if (!res.ok) {
            throw ApiException(
                when {
                    res.code == 429 -> "Muitas tentativas. Aguarde alguns minutos."
                    res.json()?.optString("error_code") == "email_not_confirmed" -> "Confirme seu e-mail antes de entrar."
                    else -> "Não foi possível entrar. Confira e-mail e senha."
                },
                res.code,
            )
        }
        saveSession(res.json() ?: throw ApiException("Resposta inválida do servidor."))
    }

    fun matches(): List<Match> {
        val select = "id,starts_at,location,mode,team_a,team_b,score_a,score_b,status,version"
        val res = authed("GET", "/rest/v1/matches?select=$select&order=starts_at.desc&limit=20")
        val arr = runCatching { JSONArray(res.body) }.getOrElse { throw ApiException("Resposta inválida do servidor.") }
        return (0 until arr.length()).mapNotNull { runCatching { parseMatch(arr.getJSONObject(it)) }.getOrNull() }
    }

    fun scoreMatch(game: Game) {
        val body = JSONObject()
            .put("p_id", game.match.id)
            .put("p_expected_version", game.match.version)
            .put("p_score_a", game.score(Team.A))
            .put("p_score_b", game.score(Team.B))
            .put("p_contributions", game.contributionsJson())
        val res = authed("POST", "/rest/v1/rpc/score_match", body)
        if (!res.ok) {
            // Nunca repassa a mensagem crua do servidor para a tela (pode expor detalhes internos).
            val msg = when (res.json()?.optString("code")) {
                "40001", "23505" -> "A partida foi alterada em outro lugar. Atualize a lista."
                "42501" -> "Sua conta não tem permissão para lançar o placar."
                else -> when (res.code) {
                    401, 403 -> "Entre com uma conta autorizada para salvar o placar."
                    429 -> "Muitas tentativas. Aguarde um pouco."
                    in 400..499 -> "O site recusou o placar. Confira os lances."
                    else -> "O servidor está indisponível. Tente de novo."
                }
            }
            throw ApiException(msg, res.code)
        }
    }

    // ---- internals ----

    private class Res(val code: Int, val body: String) {
        val ok get() = code in 200..299
        fun json(): JSONObject? = runCatching { JSONObject(body) }.getOrNull()
    }

    private fun parseMatch(o: JSONObject): Match {
        fun players(k: String): List<Player> {
            val arr = o.optJSONObject(k)?.optJSONArray("players") ?: return emptyList()
            return (0 until minOf(arr.length(), MAX_PLAYERS)).map {
                val p = arr.getJSONObject(it)
                Player(p.get("id").toString(), p.optString("name", "?").take(40), p.optInt("slot"))
            }.sortedBy { it.slot }
        }
        return Match(
            id = o.get("id").toString(),
            version = o.optLong("version"),
            startsAt = o.optString("starts_at"),
            location = o.optString("location").take(80),
            mode = o.optString("mode"),
            teamA = players("team_a"),
            teamB = players("team_b"),
            scoreA = if (o.isNull("score_a")) null else o.getInt("score_a"),
            scoreB = if (o.isNull("score_b")) null else o.getInt("score_b"),
            status = o.optString("status"),
        )
    }

    private fun saveSession(j: JSONObject) {
        val access = j.optString("access_token").ifEmpty { throw ApiException("Resposta inválida do servidor.") }
        val refresh = j.optString("refresh_token").ifEmpty { throw ApiException("Resposta inválida do servidor.") }
        store.put(mapOf(K_ACCESS to access, K_REFRESH to refresh))
        accessCache = access
    }

    private fun currentAccess(): String? = accessCache ?: store.get(K_ACCESS)?.also { accessCache = it }

    /** Renova a sessão; sincronizado para duas requisições não gastarem o mesmo refresh token. */
    private fun refresh(usedToken: String?): Boolean = synchronized(refreshLock) {
        val now = currentAccess()
        if (now != null && now != usedToken) return true // outra thread já renovou
        val rt = store.get(K_REFRESH) ?: return false
        val res = request("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", rt), bearer = SUPABASE_KEY)
        if (res.ok) { res.json()?.let { saveSession(it); return true } }
        if (res.code in 400..403) logout()
        false
    }

    private fun authed(method: String, path: String, body: JSONObject? = null): Res {
        if (REQUIRE_LOGIN && !hasSession) throw ApiException("Faça login.", 401)
        var token = currentAccess()
        var res = request(method, path, body, bearer = token ?: SUPABASE_KEY)
        if (res.code == 401 && hasSession && refresh(token)) {
            token = currentAccess()
            res = request(method, path, body, bearer = token ?: SUPABASE_KEY)
        }
        if (res.code == 401 && REQUIRE_LOGIN) throw ApiException("Sessão expirada. Entre novamente.", 401)
        if (!res.ok && method == "GET") throw ApiException("Não foi possível carregar (erro ${res.code}).", res.code)
        return res
    }

    private fun request(method: String, path: String, body: JSONObject? = null, bearer: String): Res {
        var c: HttpsURLConnection? = null
        try {
            c = URL(SUPABASE_URL + path).openConnection() as HttpsURLConnection
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.useCaches = false
            c.instanceFollowRedirects = false // redirecionamento poderia vazar o token para outro host
            c.setRequestProperty("apikey", SUPABASE_KEY)
            c.setRequestProperty("Authorization", "Bearer $bearer")
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                c.doOutput = true
                c.setFixedLengthStreamingMode(bytes.size)
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            if (code in 300..399) return Res(code, "")
            val stream = if (code < 400) c.inputStream else c.errorStream
            return Res(code, stream?.use { readCapped(it) }.orEmpty())
        } catch (e: IOException) {
            throw ApiException("Sem conexão com a internet.")
        } finally {
            c?.disconnect()
        }
    }

    /** Lê no máximo [MAX_BODY] bytes: uma resposta gigante não derruba o app (principalmente no relógio). */
    private fun readCapped(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > MAX_BODY) throw ApiException("Resposta grande demais do servidor.")
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    companion object {
        /** Mude para false para usar o app sem login (só leitura; o site não aceita placar sem conta). */
        const val REQUIRE_LOGIN = true
        const val SUPABASE_URL = "https://lfsguhwziloanyxkpskf.supabase.co"
        const val SUPABASE_KEY = "sb_publishable_UV6pUwJyUjRmFlOEo_LvUg_QeSJ6h0z"

        private const val K_ACCESS = "access"
        private const val K_REFRESH = "refresh"
        private const val MAX_BODY = 2 * 1024 * 1024
        private const val MAX_PLAYERS = 30
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/** Guarda o rascunho do jogo ao vivo para sobreviver a fechar o app / tela apagada. Não contém segredos. */
class DraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("futrox_draft", Context.MODE_PRIVATE)
    fun save(game: Game) = prefs.edit().putString("game", game.toJson()).apply()
    fun load(match: Match): Game? = prefs.getString("game", null)?.let { Game.fromJson(match, it) }
    fun clear() = prefs.edit().clear().apply()
}
