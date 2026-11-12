package br.futrox.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.Text
import br.futrox.core.AppViewModel
import br.futrox.core.Game
import br.futrox.core.Match
import br.futrox.core.Team

private val ColorA = Color(0xFFD8F494)
private val ColorB = Color(0xFFB79643)
private fun teamColor(t: Team) = if (t == Team.A) ColorA else ColorB

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Root(vm, onExit = { finish() }) } }
    }
}

@Composable
private fun Root(vm: AppViewModel, onExit: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val g = s.game
    if (!s.loggedIn || g == null) {
        val rootKey = if (!s.loggedIn) "login" else "matches"
        key(rootKey) {
            SwipeToDismissBox(onDismissed = onExit) { isBg ->
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (!isBg) {
                        if (!s.loggedIn) Login(vm, s.loading, s.error)
                        else MatchList(vm, s.matches, s.loading, s.error)
                    }
                }
            }
        }
    } else {
        GameScreen(vm, g, s.loading, s.error, s.saved)
    }
}

@Composable
private fun Field(value: String, hint: String, onChange: (String) -> Unit, password: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().background(Color(0xFF202124), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) Text(hint, color = Color.Gray, fontSize = 13.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(Color.White),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Email),
        )
    }
}

@Composable
private fun Login(vm: AppViewModel, loading: Boolean, error: String?) {
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Fut Rox", fontSize = 18.sp, color = ColorA) }
        item { Field(email, "e-mail", { email = it }) }
        item { Field(pass, "senha", { pass = it }, password = true) }
        if (error != null) item { Text(error, color = Color(0xFFFF6B6B), fontSize = 11.sp, textAlign = TextAlign.Center) }
        item {
            Chip(
                onClick = { vm.login(email, pass) }, enabled = !loading && email.isNotBlank() && pass.isNotBlank(),
                label = { Text(if (loading) "Entrando…" else "Entrar") },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    }
}

@Composable
private fun MatchList(vm: AppViewModel, matches: List<Match>, loading: Boolean, error: String?) {
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Partidas", fontSize = 16.sp) }
        if (error != null) item { Text(error, color = Color(0xFFFF6B6B), fontSize = 11.sp, textAlign = TextAlign.Center) }
        if (loading && matches.isEmpty()) item { Text("Carregando…", color = Color.Gray) }
        if (!loading && matches.isEmpty() && error == null) item { Text("Nenhuma partida", color = Color.Gray) }
        items(matches) { m ->
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.open(m) },
                label = { Text(m.location.ifBlank { "Partida" }, maxLines = 1) },
                secondaryLabel = {
                    Text(
                        m.startsAt.take(10).split("-").reversed().joinToString("/") +
                            if (m.hasScore) "  ${m.scoreA}x${m.scoreB}" else "",
                        maxLines = 1,
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item { Chip(onClick = { vm.loadMatches() }, label = { Text("Atualizar") }, colors = ChipDefaults.secondaryChipColors()) }
        item { Chip(onClick = { vm.logout() }, label = { Text("Sair") }, colors = ChipDefaults.secondaryChipColors()) }
    }
}

private sealed interface Step {
    data object Main : Step
    data class Scorer(val team: Team) : Step
    data class Assist(val team: Team, val scorerId: String) : Step
    data object Confirm : Step
}

@Composable
private fun GameScreen(vm: AppViewModel, game: Game, loading: Boolean, error: String?, saved: Boolean) {
    var step by rememberSaveable { mutableStateOf<String>("main") }
    var scorerTeam by rememberSaveable { mutableStateOf(Team.A) }
    var scorer by rememberSaveable { mutableStateOf("") }

    fun go(s: Step) {
        step = when (s) {
            Step.Main -> "main"; Step.Confirm -> "confirm"
            is Step.Scorer -> { scorerTeam = s.team; "scorer" }
            is Step.Assist -> { scorerTeam = s.team; scorer = s.scorerId; "assist" }
        }
    }

    val goBack: () -> Unit = {
        when {
            saved -> vm.close()
            step == "assist" -> step = "scorer"
            step != "main" -> step = "main"
            else -> vm.close()
        }
    }

    BackHandler(onBack = goBack)

    val screenKey = if (saved) "saved" else "${game.match.id}:$step"
    key(screenKey) {
        SwipeToDismissBox(onDismissed = goBack) { isBg ->
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (isBg) return@Box

                if (saved) {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Placar salvo!", fontSize = 18.sp, color = ColorA)
                        Text("${game.score(Team.A)} x ${game.score(Team.B)}", fontSize = 24.sp)
                        Chip(onClick = { vm.close() }, label = { Text("Voltar") }, colors = ChipDefaults.primaryChipColors())
                    }
                    return@Box
                }

                when (step) {
                    "scorer" -> PlayerPicker(
                        title = "Quem fez o gol?", team = scorerTeam,
                        players = game.match.team(scorerTeam).map { it.id to it.name },
                    ) { go(Step.Assist(scorerTeam, it)) }

                    "assist" -> PlayerPicker(
                        title = "Assistência?", team = scorerTeam,
                        players = listOf("" to "Sem assistência") +
                            game.match.team(scorerTeam).filter { it.id != scorer }.map { it.id to it.name },
                    ) {
                        vm.goal(scorerTeam, scorer, it.ifEmpty { null })
                        step = "main"
                    }

                    "confirm" -> Column(
                        Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Encerrar partida?", fontSize = 15.sp)
                        Text("${game.score(Team.A)} x ${game.score(Team.B)}", fontSize = 26.sp)
                        if (error != null) Text(error, color = Color(0xFFFF6B6B), fontSize = 11.sp, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { step = "main" }, colors = ButtonDefaults.secondaryButtonColors()) { Text("Não") }
                            Button(onClick = { vm.finish() }, enabled = !loading, colors = ButtonDefaults.primaryButtonColors()) {
                                Text(if (loading) "…" else "Sim")
                            }
                        }
                    }

                    else -> Column(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${game.score(Team.A)} x ${game.score(Team.B)}", fontSize = 34.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            for (t in Team.entries) {
                                Button(
                                    onClick = { go(Step.Scorer(t)) },
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                    colors = ButtonDefaults.buttonColors(backgroundColor = teamColor(t)),
                                ) { Text("⚽ ${t.name}", fontSize = 14.sp, color = Color(0xFF10372D)) }
                            }
                        }
                        val last = game.events.lastOrNull()
                        Text(
                            last?.let { "${game.name(it.team, it.scorerId)}" + (it.assistId?.let { a -> " (ass. ${game.name(it.team, a)})" } ?: "") } ?: "Toque no time que marcou",
                            fontSize = 11.sp, color = Color.Gray, maxLines = 2, textAlign = TextAlign.Center,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(onClick = { vm.undo() }, enabled = game.events.isNotEmpty(), colors = ButtonDefaults.secondaryButtonColors()) { Text("↩") }
                            Button(onClick = { go(Step.Confirm) }, colors = ButtonDefaults.primaryButtonColors()) { Text("✓") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPicker(title: String, team: Team, players: List<Pair<String, String>>, onPick: (String) -> Unit) {
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text(title, fontSize = 14.sp, color = teamColor(team)) }
        items(players) { (id, name) ->
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPick(id) },
                label = { Text(name, maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}
