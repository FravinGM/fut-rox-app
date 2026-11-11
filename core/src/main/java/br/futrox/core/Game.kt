package br.futrox.core

import org.json.JSONArray
import org.json.JSONObject

/** Estado de uma partida ao vivo, derivado da lista de eventos. */
data class Game(val match: Match, val events: List<GoalEvent> = emptyList()) {

    fun score(team: Team) = events.count { it.team == team }

    fun goals(team: Team, playerId: String) =
        events.count { it.team == team && it.scorerId == playerId }

    fun assists(team: Team, playerId: String) =
        events.count { it.team == team && it.assistId == playerId }

    fun addGoal(team: Team, scorerId: String, assistId: String?): Game {
        val assist = assistId?.takeIf { it != scorerId }
        return copy(events = events + GoalEvent(team, scorerId, assist))
    }

    fun undo(): Game = copy(events = events.dropLast(1))

    /** Remove um lance específico (qualquer posição da lista). */
    fun removeAt(index: Int): Game =
        if (index in events.indices) copy(events = events.filterIndexed { i, _ -> i != index }) else this

    fun name(team: Team, id: String) =
        match.team(team).firstOrNull { it.id == id }?.name ?: "?"

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
        fun fromJson(match: Match, json: String): Game? = runCatching {
            val o = JSONObject(json)
            if (o.getString("matchId") != match.id) return null
            val arr = o.getJSONArray("events")
            Game(match, (0 until arr.length()).map {
                val e = arr.getJSONObject(it)
                GoalEvent(
                    if (e.getString("t") == "a") Team.A else Team.B,
                    e.getString("s"),
                    if (e.isNull("a")) null else e.getString("a"),
                )
            })
        }.getOrNull()
    }
}
