package ua.gryntsiv.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GryntsivApp() }
    }
}

@Composable
fun GryntsivApp() {
    var screen by remember { mutableStateOf("home") }
    var settings by remember { mutableStateOf(GameSettings()) }
    var showScores by remember { mutableStateOf(false) }
    val engine = remember { UnoEngine() }
    var game by remember { mutableStateOf<GameState?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFFFC857),
        background = Color(0xFF10131A),
        surface = Color(0xFF1B2230)
    )) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF10131A)) {
            when (screen) {
                "home" -> HomeScreen(
                    onCreate = { screen = "create" },
                    onJoin = { screen = "join" },
                    onSettings = { screen = "settings" },
                    onHistory = { screen = "history" },
                    onScores = { showScores = true }
                )
                "create" -> CreateRoomScreen(
                    onBack = { screen = "home" },
                    onStart = { names, botCount ->
                        game = engine.newGame(names, botCount)
                        screen = "game"
                    }
                )
                "join" -> JoinRoomScreen(onBack = { screen = "home" })
                "settings" -> SettingsScreen(settings, { settings = it }, { screen = "home" })
                "history" -> HistoryScreen(onBack = { screen = "home" })
                "game" -> GameScreen(
                    state = game,
                    onPlay = { card ->
                        game = game?.let { engine.play(it, it.currentPlayerId, card) }
                    },
                    onDraw = { game = game?.let { engine.drawOne(it, it.currentPlayerId) } },
                    onPass = { game = game?.let { engine.pass(it, it.currentPlayerId) } },
                    onBack = { screen = "home" }
                )
            }
            if (showScores) {
                AlertDialog(
                    onDismissRequest = { showScores = false },
                    title = { Text("Загальні очки") },
                    text = { Text("Таблиця учасників з'явиться після завершення мережевого модуля.") },
                    confirmButton = { TextButton(onClick = { showScores = false }) { Text("Закрити") } }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(onCreate: () -> Unit, onJoin: () -> Unit, onSettings: () -> Unit,
               onHistory: () -> Unit, onScores: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        Text("GRYNTSIV", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFC857))
        Text("Карткова гра у домашній Wi-Fi мережі", color = Color.LightGray)
        Spacer(Modifier.height(22.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Створити кімнату") }
        OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth()) { Text("Приєднатися до кімнати") }
        OutlinedButton(onClick = onScores, modifier = Modifier.fillMaxWidth()) { Text("Мої очки: 0") }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Історія ігор") }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Налаштування") }
        Spacer(Modifier.weight(1f))
        Text("GRYNTSIV • версія 0.1.0", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun CreateRoomScreen(onBack: () -> Unit, onStart: (List<String>, Int) -> Unit) {
    var count by remember { mutableIntStateOf(2) }
    var bots by remember { mutableIntStateOf(1) }
    var playerName by remember { mutableStateOf("Гравець 1") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Створити кімнату", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(playerName, { playerName = it }, label = { Text("Твоє ім'я") }, modifier = Modifier.fillMaxWidth())
        Text("Кількість учасників: $count (разом із ботами)")
        Slider(value = count.toFloat(), onValueChange = { count = it.toInt().coerceIn(2, 6) }, valueRange = 2f..6f, steps = 3)
        Text("Ботів: $bots")
        Slider(value = bots.toFloat(), onValueChange = { bots = it.toInt().coerceIn(0, count - 1) }, valueRange = 0f..(count - 1).toFloat(),
            steps = (count - 2).coerceAtLeast(0))
        Text("Поки що це локальний прототип ігрового рушія. Мережеве створення кімнат буде наступним етапом.", color = Color.LightGray)
        Button(onClick = { onStart(listOf(playerName.ifBlank { "Гравець" }), bots) }, modifier = Modifier.fillMaxWidth()) {
            Text("Перейти до тестової партії")
        }
    }
}

@Composable
fun JoinRoomScreen(onBack: () -> Unit) {
    var ip by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Приєднатися", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(ip, { ip = it }, label = { Text("IP ведучого, наприклад 192.168.1.10") }, modifier = Modifier.fillMaxWidth())
        Text("Підключення через локальну Wi-Fi мережу буде активовано в наступному мережевому етапі.", color = Color.LightGray)
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Підключитися") }
    }
}

@Composable
fun SettingsScreen(settings: GameSettings, onChange: (GameSettings) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Налаштування", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Якщо добрана карта не підходить")
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(settings.autoPass, { onChange(settings.copy(autoPass = true)) })
            Text("Автоматично передавати хід")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(!settings.autoPass, { onChange(settings.copy(autoPass = false)) })
            Text("Показувати кнопку «Пас»")
        }
        Divider()
        Text("Тема карт")
        listOf("Класична", "Чорно-золота", "Неонова", "Пастельна").forEach { theme ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(settings.cardTheme == theme, { onChange(settings.copy(cardTheme = theme)) })
                Text(theme)
            }
        }
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Історія ігор", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Завершені партії з результатами зберігатимуться в наступній версії.", color = Color.LightGray)
    }
}

@Composable
fun GameScreen(state: GameState?, onPlay: (Card) -> Unit, onDraw: () -> Unit, onPass: () -> Unit, onBack: () -> Unit) {
    if (state == null) return
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("GRYNTSIV", color = Color(0xFFFFC857), fontWeight = FontWeight.Black)
            TextButton(onClick = onBack) { Text("Вийти") }
        }
        Text("Хід: ${state.players.firstOrNull { it.id == state.currentPlayerId }?.name ?: "—"}")
        Text("Напрямок: ${if (state.clockwise) "за годинниковою стрілкою" else "проти годинникової стрілки"}")
        Text("На столі: ${state.discardPile.lastOrNull()?.label ?: "—"}")
        Text("Карти на руках: ${state.players.joinToString { "${it.name}: ${it.hand.size}" }}", color = Color.LightGray)
        Spacer(Modifier.weight(1f))
        Text("Твої карти", fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f)) {
            items(state.players.firstOrNull()?.hand ?: emptyList()) { card ->
                CardRow(card, onClick = { onPlay(card) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDraw, modifier = Modifier.weight(1f)) { Text("Добрати") }
            OutlinedButton(onClick = onPass, modifier = Modifier.weight(1f)) { Text("Пас") }
        }
    }
}

@Composable
fun CardRow(card: Card, onClick: () -> Unit) {
    val color = when (card.color) {
        CardColor.RED -> Color(0xFFC62828)
        CardColor.BLUE -> Color(0xFF1565C0)
        CardColor.GREEN -> Color(0xFF2E7D32)
        CardColor.YELLOW -> Color(0xFFF9A825)
        null -> Color(0xFF424242)
    }
    Surface(onClick = onClick, color = color, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(card.label, Modifier.padding(16.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
