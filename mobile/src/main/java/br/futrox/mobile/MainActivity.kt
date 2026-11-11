package br.futrox.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import br.futrox.core.AppViewModel
import br.futrox.core.Game
import br.futrox.core.Match
import br.futrox.core.Team



class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("futrox_ui", MODE_PRIVATE)
        setContent {
            val system = isSystemInDarkTheme()
            // null = segue o sistema até o usuário escolher manualmente
            var choice by remember { mutableStateOf<Boolean?>(if (prefs.contains("dark")) prefs.getBoolean("dark", false) else null) }
            val dark = choice ?: system
            SideEffect {
                val bar = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            CompositionLocalProvider(
                LocalToggleTheme provides { choice = !dark; prefs.edit().putBoolean("dark", !dark).apply() },
            ) {
                FutTheme(dark) {
                    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
                        Column(Modifier.padding(pad).fillMaxSize()) { Root(vm) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeToggle() {
    TextButton(onClick = LocalToggleTheme.current) { Text(if (LocalDarkTheme.current) "☀️ Claro" else "🌙 Escuro") }
}

@Composable
private fun Root(vm: AppViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    when {
        !s.loggedIn -> Login(vm, s.loading, s.error)
        s.game == null -> Matches(vm, s.matches, s.loading, s.error)
        else -> GameScreen(vm, s.game!!, s.loading, s.error, s.saved)
    }
}

@Composable
private fun Login(vm: AppViewModel, loading: Boolean, error: String?) {
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("FUT ROX", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Text("Entre com a mesma conta do site", Modifier.padding(bottom = 24.dp))
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth(), label = { Text("E-mail") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            pass, { pass = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Senha") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = { vm.login(email, pass) }, enabled = !loading && email.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) { Text(if (loading) "Entrando…" else "Entrar") }
    }
}

@Composable
private fun Matches(vm: AppViewModel, matches: List<Match>, loading: Boolean, error: String?) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PARTIDAS", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Row {
                ThemeToggle()
                TextButton(onClick = { vm.loadMatches() }) { Text("Atualizar") }
                if (br.futrox.core.Api.REQUIRE_LOGIN) TextButton(onClick = { vm.logout() }) { Text("Sair") }
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        if (loading && matches.isEmpty()) Text("Carregando…")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(matches) { m ->
                Card(
                    Modifier.fillMaxWidth().clickable { vm.open(m) },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(m.location.ifBlank { "Partida" }, fontSize = 18.sp)
                        Text(
                            m.startsAt.take(10).split("-").reversed().joinToString("/") + " " + m.startsAt.drop(11).take(5) +
                                if (m.hasScore) "   •   ${m.scoreA} x ${m.scoreB}" else "   •   sem placar",
                            color = Color.Gray,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameScreen(vm: AppViewModel, game: Game, loading: Boolean, error: String?, saved: Boolean) {
    var pickTeam by rememberSaveable { mutableStateOf<Team?>(null) }
    var scorer by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    BackHandler { vm.close() }

    if (saved) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Placar salvo no site ✔", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text("${game.score(Team.A)} x ${game.score(Team.B)}", fontSize = 48.sp)
            Button(onClick = { vm.close() }) { Text("Voltar") }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(game.match.location.ifBlank { "Partida" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThemeToggle()
        }
        Text(
            "${game.score(Team.A)} x ${game.score(Team.B)}", style = MaterialTheme.typography.displayLarge, fontSize = 88.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (t in Team.entries) {
                val (bg, fg) = teamColors(t)
                Button(
                    onClick = { pickTeam = t; scorer = null },
                    modifier = Modifier.weight(1f).height(64.dp).padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
                ) { Text("⚽ GOL ${t.name}", style = MaterialTheme.typography.titleLarge, color = fg) }
            }
        }
        Text("Lances", fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
        if (game.events.isEmpty()) Text("Nenhum gol ainda", color = Color.Gray)
        game.events.forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${i + 1}. [${e.team.name}] ${game.name(e.team, e.scorerId)}" +
                        (e.assistId?.let { "  (ass. ${game.name(e.team, it)})" } ?: ""),
                    color = if (e.team == Team.A) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.removeEvent(i) }) {
                    Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 18.sp)
                }
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirmClear = true }, enabled = game.events.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Limpar") }
            Button(onClick = { confirm = true }, enabled = !loading, modifier = Modifier.weight(1f)) { Text(if (loading) "Salvando…" else "Encerrar") }
        }
        TextButton(onClick = { vm.close() }, Modifier.fillMaxWidth()) { Text("Voltar às partidas") }
    }

    val t = pickTeam
    if (t != null) {
        val players = game.match.team(t)
        if (scorer == null) {
            PickDialog("Quem fez o gol? (Time ${t.name})", players.map { it.id to it.name }, { pickTeam = null }) { scorer = it }
        } else {
            PickDialog(
                "Assistência?",
                listOf("" to "Sem assistência") + players.filter { it.id != scorer }.map { it.id to it.name },
                { pickTeam = null; scorer = null },
            ) { vm.goal(t, scorer!!, it.ifEmpty { null }); pickTeam = null; scorer = null }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar gols?") },
            text = { Text("Remove todos os ${game.events.size} gols da partida.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.reset() }) { Text("Limpar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Encerrar partida?") },
            text = { Text("Salvar ${game.score(Team.A)} x ${game.score(Team.B)} no site.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.finish() }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PickDialog(title: String, options: List<Pair<String, String>>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { (id, name) ->
                    Text(name, fontSize = 18.sp, modifier = Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
