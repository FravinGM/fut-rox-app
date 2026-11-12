package br.futrox.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val matches: List<Match> = emptyList(),
    val game: Game? = null,
    val saved: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val api = Api(app)
    private val drafts = DraftStore(app)
    private val canUse get() = !Api.REQUIRE_LOGIN || api.hasSession
    private val _state = MutableStateFlow(UiState(loggedIn = canUse))
    val state: StateFlow<UiState> = _state

    init { if (canUse) loadMatches() }

    /** Executa [block] fora da thread principal. Ignora se já houver uma operação em andamento (evita envio duplo). */
    private fun <T> run(block: () -> T, onOk: (T) -> Unit) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { block() }
                _state.update { it.copy(loading = false, loggedIn = canUse) }
                onOk(r)
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message, loggedIn = canUse) }
            } catch (e: Exception) {
                // Não mostra detalhes internos da exceção na tela.
                _state.update { it.copy(loading = false, error = "Algo deu errado. Tente de novo.", loggedIn = canUse) }
            }
        }
    }

    fun login(email: String, password: String) =
        run({ api.login(email, password) }) { loadMatches() }

    fun logout() {
        val token = api.logout()
        drafts.clear()
        _state.value = UiState(loggedIn = canUse)
        viewModelScope.launch(Dispatchers.IO) { api.revoke(token) }
    }

    fun loadMatches() = run({ api.matches() }) { list -> _state.update { it.copy(matches = list) } }

    fun open(match: Match) {
        val game = drafts.load(match) ?: Game(match)
        _state.update { it.copy(game = game, saved = false, error = null) }
    }

    fun close() = _state.update { it.copy(game = null, saved = false, error = null) }

    fun goal(team: Team, scorerId: String, assistId: String?) = mutate { it.addGoal(team, scorerId, assistId) }

    fun undo() = mutate { it.undo() }

    fun removeEvent(index: Int) = mutate { it.removeAt(index) }

    fun reset() = mutate { Game(it.match) }

    private fun mutate(f: (Game) -> Game) {
        val g = _state.value.game ?: return
        val n = f(g)
        if (n === g) return
        drafts.save(n)
        _state.update { it.copy(game = n, error = null) }
    }

    /** Envia o placar final e as contribuições para o site. */
    fun finish() {
        val g = _state.value.game ?: return
        run({ api.scoreMatch(g) }) {
            drafts.clear()
            _state.update { it.copy(saved = true) }
            loadMatches()
        }
    }
}
