package br.futrox.core

import org.json.JSONArray
import org.json.JSONObject

/** Estado de uma partida ao vivo, derivado da lista de eventos. */
data class Game(val match: Match, val events: List<GoalEvent> = emptyList()) {

    // Totais calculados uma vez por estado (a tela consulta várias vezes por quadro).
    private val scores by lazy(LazyThreadSafetyMode.NONE) { IntArray(2).also { s -> events.forEach { s[it.team.ordinal]++ } } }
    private val goalCount by lazy(LazyThreadSafetyMode.NONE) { events.groupingBy { it.team to it.scorerId }.eachCount() }
    private val assistCount by lazy(LazyThreadSafetyMode.NONE) {
        events.mapNotNull { e -> e.assistId?.let { e.team to it } }.groupingBy { it }.eachCount()
    }
    private val names by lazy(LazyThreadSafetyMode.NONE) {
        Team.entries.associateWith { t -> match.team(t).associate { it.id to it.name } }
    }

    fun score(team: Team) = scores[team.ordinal]

    fun goals(team: Team, playerId: String) = goalCount[team to playerId] ?: 0

    fun assists(team: Team, playerId: String) = assistCount[team to playerId] ?: 0

    private fun inTeam(team: Team, id: String) = names.getValue(team).containsKey(id)

    /** Só aceita autor/assistência do próprio time; assistência nunca é do autor. Ignora lances inválidos. */
    fun addGoal(team: Team, scorerId: String, assistId: String?): Game {
        if (!inTeam(team, scorerId) || events.size >= MAX_EVENTS) return this
        val assist = assistId?.takeIf { it != scorerId && inTeam(team, it) }
        return copy(events = events + GoalEvent(team, scorerId, assist))
    }

    fun undo(): Game = if (events.isEmpty()) this else copy(events = events.dropLast(1))

    /** Remove um lance específico (qualquer posição da lista). */
    fun removeAt(index: Int): Game =
        if (index in events.indices) copy(events = events.filterIndexed { i, _ -> i != index }) else this

    fun name(team: Team, id: String) = names.getValue(team)[id] ?: "?"

    /** Formato esperado pela RPC score_match: {a:{id:{goals,assists}}, b:{...}} */
    fun contributionsJson(): JSONObject {
        val root = JSONObject()
        for (t in Team.entries) {
            val o = JSONObject()
            for (p in match.team(t)) {
                o.put(p.id, JSONObject().put("goals", goals(t, p.id)).put("assists", assists(t, p.id)))
            }
            root.put(t.key, o)
        }
        return root
    }

    fun toJson(): String = JSONObject()
        .put("matchId", match.id)
        .put("events", JSONArray().also { arr ->
            events.forEach {
                arr.put(JSONObject().put("t", it.team.key).put("s", it.scorerId).put("a", it.assistId ?: JSONObject.NULL))
            }
        }).toString()

    companion object {
        private const val MAX_EVENTS = 200

        /** Recarrega o rascunho, revalidando cada lance contra a escalação atual da partida. */
        fun fromJson(match: Match, json: String): Game? = runCatching {
            val o = JSONObject(json)
            if (o.getString("matchId") != match.id) return null
            val arr = o.getJSONArray("events")
            var g = Game(match)
            for (i in 0 until minOf(arr.length(), MAX_EVENTS)) {
                val e = arr.getJSONObject(i)
                val team = when (e.getString("t")) { "a" -> Team.A; "b" -> Team.B; else -> continue }
                g = g.addGoal(team, e.getString("s"), if (e.isNull("a")) null else e.getString("a"))
            }
            g
        }.getOrNull()
    }
}
