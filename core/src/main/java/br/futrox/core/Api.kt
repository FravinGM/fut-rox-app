package br.futrox.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente mínimo do Supabase usado pelo site https://fut-rox.vercel.app.
 * A chave "publishable" é pública (já vai no bundle do site); a segurança é feita pelo login + RLS.
 */
class Api(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("futrox", Context.MODE_PRIVATE)

    val hasSession get() = prefs.getString("refresh", null) != null
    val email get() = prefs.getString("email", "") ?: ""

    fun logout() = prefs.edit().clear().apply()

    fun login(email: String, password: String) {
        val res = request(
            "POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email.trim()).put("password", password), auth = false,
        )
        if (res.code !in 200..299) {
            throw ApiException(
                if (res.json()?.optString("error_code") == "email_not_confirmed") "Confirme seu e-mail antes de entrar."
                else "Não foi possível entrar. Confira e-mail e senha.", res.code,
            )
        }
        saveSession(res.json()!!)
        prefs.edit().putString("email", email.trim()).apply()
    }

    fun matches(): List<Match> {
        val select = "id,starts_at,location,mode,team_a,team_b,score_a,score_b,status,version"
        val res = authed("GET", "/rest/v1/matches?select=$select&order=starts_at.desc&limit=20")
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map { parseMatch(arr.getJSONObject(it)) }
    }

    fun scoreMatch(game: Game) {
        val body = JSONObject()
            .put("p_id", game.match.id)
            .put("p_expected_version", game.match.version)
            .put("p_score_a", game.score(Team.A))
            .put("p_score_b", game.score(Team.B))
            .put("p_contributions", game.contributionsJson())
        val res = authed("POST", "/rest/v1/rpc/score_match", body)
        if (res.code !in 200..299) {
            val j = res.json()
            val msg = when (j?.optString("code")) {
                "40001" -> "A partida foi alterada em outro lugar. Atualize a lista."
                "42501" -> "Sua conta não tem permissão para lançar o placar."
                else -> j?.optString("message").orEmpty().ifBlank { "Erro ${res.code} ao salvar." }
            }
            throw ApiException(msg, res.code)
        }
    }

    // ---- internals ----

    private class Res(val code: Int, val body: String) {
        fun json(): JSONObject? = runCatching { JSONObject(body) }.getOrNull()
    }

    private fun parseMatch(o: JSONObject): Match {
        fun players(k: String): List<Player> {
            val arr = o.optJSONObject(k)?.optJSONArray("players") ?: return emptyList()
            return (0 until arr.length()).map {
                val p = arr.getJSONObject(it)
                Player(p.get("id").toString(), p.optString("name", "?"), p.optInt("slot"))
            }.sortedBy { it.slot }
        }
        return Match(
            id = o.get("id").toString(),
            version = o.optLong("version"),
            startsAt = o.optString("starts_at"),
            location = o.optString("location"),
            mode = o.optString("mode"),
            teamA = players("team_a"),
            teamB = players("team_b"),
            scoreA = if (o.isNull("score_a")) null else o.getInt("score_a"),
            scoreB = if (o.isNull("score_b")) null else o.getInt("score_b"),
            status = o.optString("status"),
        )
    }

    private fun saveSession(j: JSONObject) {
        prefs.edit()
            .putString("access", j.getString("access_token"))
            .putString("refresh", j.getString("refresh_token"))
            .apply()
    }

    private fun refresh(): Boolean {
        val rt = prefs.getString("refresh", null) ?: return false
        val res = request("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", rt), auth = false)
        if (res.code in 200..299) { saveSession(res.json()!!); return true }
        if (res.code in 400..403) logout()
        return false
    }

    private fun authed(method: String, path: String, body: JSONObject? = null): Res {
        if (REQUIRE_LOGIN && !hasSession) throw ApiException("Faça login.", 401)
        var res = request(method, path, body)
        if (res.code == 401 && hasSession && refresh()) res = request(method, path, body)
        if (res.code == 401 && REQUIRE_LOGIN) throw ApiException("Sessão expirada. Entre novamente.", 401)
        if (res.code !in 200..299 && method == "GET") throw ApiException("Erro ${res.code} ao carregar.", res.code)
        return res
    }

    private fun request(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): Res {
        try {
            val c = URL(SUPABASE_URL + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("apikey", SUPABASE_KEY)
            c.setRequestProperty("Authorization", "Bearer " + if (auth) prefs.getString("access", SUPABASE_KEY) else SUPABASE_KEY)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Res(code, text)
        } catch (e: java.io.IOException) {
            throw ApiException("Sem conexão com a internet.")
        }
    }

    companion object {
        /** Mude para true para voltar a exigir login com a conta do site. */
        const val REQUIRE_LOGIN = true
        const val SUPABASE_URL = "https://lfsguhwziloanyxkpskf.supabase.co"
        const val SUPABASE_KEY = "sb_publishable_UV6pUwJyUjRmFlOEo_LvUg_QeSJ6h0z"
    }
}

/** Guarda o rascunho do jogo ao vivo para sobreviver a fechar o app / tela apagada. */
class DraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("futrox_draft", Context.MODE_PRIVATE)
    fun save(game: Game) = prefs.edit().putString("game", game.toJson()).apply()
    fun load(match: Match): Game? = prefs.getString("game", null)?.let { Game.fromJson(match, it) }
    fun clear() = prefs.edit().clear().apply()
}

