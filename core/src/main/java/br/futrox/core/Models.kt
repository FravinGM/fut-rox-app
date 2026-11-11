package br.futrox.core

data class Player(val id: String, val name: String, val slot: Int)

data class Match(
    val id: String,
    val version: Long,
    val startsAt: String,
    val location: String,
    val mode: String,
    val teamA: List<Player>,
    val teamB: List<Player>,
    val scoreA: Int?,
    val scoreB: Int?,
    val status: String,
) {
    val hasScore get() = scoreA != null && scoreB != null
    fun team(t: Team) = if (t == Team.A) teamA else teamB
}

enum class Team(val key: String) {
    A("a"), B("b");

    val other get() = if (this == A) B else A
}

/** Um gol; [assistId] é opcional e sempre de um colega diferente do autor. */
data class GoalEvent(val team: Team, val scorerId: String, val assistId: String?)

class ApiException(message: String, val code: Int = 0) : Exception(message)
