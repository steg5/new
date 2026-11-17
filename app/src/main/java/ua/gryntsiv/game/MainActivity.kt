package ua.gryntsiv.game

import android.os.Bundle
import android.content.SharedPreferences
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BG = Color(0xFF10151D)
private val PANEL = Color(0xFF1C2630)
private val GOLD = Color(0xFFFFC857)
private val TABLE = Color(0xFF155B43)


data class GameHistoryEntry(
    val timestamp: Long,
    val round: Int,
    val winner: String,
    val results: List<String>
)

private fun loadGameHistory(prefs: SharedPreferences): List<GameHistoryEntry> {
    return try {
        val array = JSONArray(prefs.getString("entries", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val resultArray = item.optJSONArray("results") ?: JSONArray()
            GameHistoryEntry(
                timestamp = item.optLong("timestamp"),
                round = item.optInt("round", 1),
                winner = item.optString("winner", "—"),
                results = (0 until resultArray.length()).map {
                    resultArray.optString(it)
                }
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun encodeGameHistory(entries: List<GameHistoryEntry>): String {
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(
            JSONObject()
                .put("timestamp", entry.timestamp)
                .put("round", entry.round)
                .put("winner", entry.winner)
                .put("results", JSONArray(entry.results))
        )
    }
    return array.toString()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        setContent { GryntsivApp() }
    }
    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }
}

@Composable
fun GryntsivApp() {
    val context = LocalContext.current
    val historyPrefs = remember {
        context.getSharedPreferences("gryntsiv_history", android.content.Context.MODE_PRIVATE)
    }
    var history by remember { mutableStateOf(loadGameHistory(historyPrefs)) }
    var recordedRoundKey by remember { mutableStateOf("") }

    var screen by remember { mutableStateOf("home") }
    var settings by remember { mutableStateOf(GameSettings()) }
    var showScores by remember { mutableStateOf(false) }
    val engine = remember { UnoEngine() }
    var game by remember { mutableStateOf<GameState?>(null) }

    // Автоматичне збереження завершеної партії.
    LaunchedEffect(game?.roundOver, game?.winnerId) {
        val finished = game
        if (finished != null && finished.roundOver) {
            val key = "${finished.roundNumber}:${finished.winnerId}:${finished.players.map { it.name }}"
            if (key != recordedRoundKey) {
                val winner = finished.players.firstOrNull { it.id == finished.winnerId }?.name ?: "—"
                val entry = GameHistoryEntry(
                    timestamp = System.currentTimeMillis(),
                    round = finished.roundNumber,
                    winner = winner,
                    results = finished.players.map { player ->
                        "${player.name}: ${finished.roundScores[player.id] ?: 0} очок (разом ${player.totalPoints})"
                    }
                )
                history = (listOf(entry) + history).take(100)
                historyPrefs.edit()
                    .putString("entries", encodeGameHistory(history))
                    .apply()
                recordedRoundKey = key
            }
        }
    }

    // Автоматичний хід ботів.
    LaunchedEffect(game?.currentPlayerId, game?.lastAction, game?.roundOver) {
        val current = game
        val player = current?.players?.firstOrNull { it.id == current.currentPlayerId }
        if (current != null && player?.isBot == true && !current.roundOver) {
            delay(750)
            val latest = game
            if (latest != null && latest.currentPlayerId == player.id && !latest.roundOver) {
                game = engine.botAction(latest)
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GOLD,
            background = BG,
            surface = PANEL,
            onPrimary = Color(0xFF171717),
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = BG) {
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
                    onStart = { name, bots ->
                        recordedRoundKey = ""
                        game = engine.newGame(name, bots)
                        screen = "game"
                    }
                )
                "join" -> JoinRoomScreen(onBack = { screen = "home" })
                "settings" -> SettingsScreen(
                    settings,
                    { settings = it },
                    { screen = "home" }
                )
                "history" -> HistoryScreen(
                    entries = history,
                    onClear = {
                        history = emptyList()
                        historyPrefs.edit().remove("entries").apply()
                    },
                    onBack = { screen = "home" }
                )
                "game" -> GameScreen(
                    state = game,
                    settings = settings,
                    onPlay = { card, color ->
                        game = game?.let {
                            engine.play(it, it.currentPlayerId, card, color)
                        }
                    },
                    onDraw = { game = game?.let { engine.draw(it, it.currentPlayerId) } },
                    onPass = { game = game?.let { engine.pass(it, it.currentPlayerId) } },
                    onBack = { screen = "home" }
                )
            }

            if (showScores) {
                AlertDialog(
                    onDismissRequest = { showScores = false },
                    title = { Text("Загальні очки") },
                    text = {
                        Text(
                            game?.players?.joinToString("\n") {
                                "${it.name}: ${it.totalPoints}"
                            } ?: "Поки немає зіграних партій."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showScores = false }) {
                            Text("Закрити")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onScores: () -> Unit
) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(0.9f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GRYNTSIV", fontSize = 38.sp, fontWeight = FontWeight.Black, color = GOLD)
                Text("КАРТКОВА ГРА", color = Color.LightGray, letterSpacing = 4.sp, fontSize = 11.sp)
            }
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF263B38), Color(0xFF17231F))),
                        RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-17).dp)) {
                    listOf(CardColor.RED, CardColor.BLUE, CardColor.GREEN, CardColor.YELLOW)
                        .forEachIndexed { i, c ->
                            Box(Modifier.rotate(if (i % 2 == 0) -8f else 8f)) {
                                CardBack(color = c, width = 52.dp, height = 80.dp)
                            }
                        }
                }
            }
            Text("GRYNTSIV  •  0.3.0", color = Color.Gray, fontSize = 10.sp)
        }
        Column(
            Modifier.weight(1.2f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MainMenuButton("НОВА ГРА", "Створити локальну партію", onCreate, true,
                    Modifier.weight(1f).fillMaxHeight())
                MainMenuButton("ПРИЄДНАТИСЯ", "Домашня Wi-Fi мережа", onJoin,
                    modifier = Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MainMenuButton("РЕЗУЛЬТАТИ", "Очки гравців", onScores,
                    modifier = Modifier.weight(1f).fillMaxHeight())
                MainMenuButton("ІСТОРІЯ", "Попередні партії", onHistory,
                    modifier = Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f)) {
                MainMenuButton("НАЛАШТУВАННЯ", "Теми карт та правила", onSettings,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight())
            }
        }
    }
}

@Composable
fun MainMenuButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (primary) GOLD else PANEL,
        shape = RoundedCornerShape(15.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title,
                color = if (primary) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp)
            Text(subtitle,
                color = if (primary) Color(0xFF51401C) else Color.LightGray,
                fontSize = 10.sp,
                maxLines = 2)
        }
    }
}

@Composable
fun CreateRoomScreen(onBack: () -> Unit, onStart: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("Гравець 1") }
    var participants by remember { mutableIntStateOf(4) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Нова партія", fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("Твоє ім'я", color = Color.LightGray)
        OutlinedTextField(
            name,
            { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text("Кількість гравців: $participants", fontWeight = FontWeight.Bold)
        Slider(
            value = participants.toFloat(),
            onValueChange = { participants = it.toInt().coerceIn(2, 6) },
            valueRange = 2f..6f,
            steps = 3
        )
        Text("Ти граєш проти ${participants - 1} суперників.")
        Text(
            "У локальному режимі суперниками керує комп'ютер. Мережева гра між телефонами буде окремим етапом.",
            color = Color.LightGray,
            fontSize = 13.sp
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onStart(name.ifBlank { "Гравець" }, participants - 1) },
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text("РОЗПОЧАТИ ГРУ", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun JoinRoomScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Приєднатися до гри", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(
            "Мережеве підключення ще не реалізоване. Зараз доступна локальна гра проти ботів.",
            color = Color.LightGray
        )
    }
}

@Composable
fun SettingsScreen(
    settings: GameSettings,
    onChange: (GameSettings) -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Налаштування", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Якщо добрана карта не підходить", color = Color.LightGray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(settings.autoPass, { onChange(settings.copy(autoPass = true)) })
            Text("Автоматично передавати хід")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(!settings.autoPass, { onChange(settings.copy(autoPass = false)) })
            Text("Показувати кнопку «Пас»")
        }
        HorizontalDivider(color = Color.DarkGray)
        Text("Оформлення карт", color = GOLD, fontWeight = FontWeight.Bold)
        listOf("Класична", "Чорно-золота", "Неонова", "Пастельна").forEach { theme ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    settings.cardTheme == theme,
                    { onChange(settings.copy(cardTheme = theme)) }
                )
                Text(theme)
            }
        }
    }
}

@Composable
fun HistoryScreen(
    entries: List<GameHistoryEntry>,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Очистити історію", color = Color(0xFFFF8A80))
                }
            }
        }

        Text("ІСТОРІЯ ІГОР", color = GOLD, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(
            "Завершені партії зберігаються на цьому пристрої.",
            color = Color.LightGray,
            fontSize = 13.sp
        )

        if (entries.isEmpty()) {
            Surface(
                color = PANEL,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Поки що порожньо", color = GOLD, fontWeight = FontWeight.Bold)
                    Text(
                        "Заверши першу партію, і її результат з’явиться тут.",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            entries.forEachIndexed { index, entry ->
                Surface(
                    color = PANEL,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            "#${entries.size - index}  •  " +
                                SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault())
                                    .format(Date(entry.timestamp)),
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        Text(
                            "Переможець: ${entry.winner}",
                            color = GOLD,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text("Раунд ${entry.round}", color = Color.LightGray, fontSize = 11.sp)
                        entry.results.forEach { result ->
                            Text(result, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameScreen(
    state: GameState?,
    settings: GameSettings,
    onPlay: (Card, CardColor?) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit,
    onBack: () -> Unit
) {
    if (state == null) return

    var colorDialog by remember { mutableStateOf<Card?>(null) }

    val human = state.players.firstOrNull()
    val current = state.players.firstOrNull { it.id == state.currentPlayerId }
    val isMyTurn = current?.id == human?.id

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val centerCardHeight = if (maxHeight < 175.dp) 46.dp else 58.dp
        val centerCardWidth = centerCardHeight * 0.68f
        val handCardHeight = centerCardHeight
        val handCardWidth = handCardHeight * 0.68f

        Column(
            Modifier.fillMaxSize()
                .background(BG)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Верхня панель
            Row(
                Modifier.fillMaxWidth().height(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GRYNTSIV", color = GOLD, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("РАУНД ${state.roundNumber}", color = Color.Gray, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.roundOver) "ЗАВЕРШЕНО"
                        else if (isMyTurn) "ТВІЙ ХІД" else "ХІД: ${current?.name ?: "—"}",
                        color = if (isMyTurn) GOLD else Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
                    ) {
                        Text("Вийти", color = Color.LightGray, fontSize = 10.sp)
                    }
                }
            }

            // Компактна панель суперників
            Row(
                Modifier.fillMaxWidth().height(27.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                state.players.drop(1).forEach { player ->
                    val active = player.id == state.currentPlayerId
                    Surface(
                        color = if (active) Color(0xFF39483E) else PANEL,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (player.isBot) "🤖" else "👤", fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${player.name} • ${player.hand.size}",
                                color = Color.White,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Стіл ліворуч, рука гравця праворуч
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    Modifier.weight(0.9f).fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1C7555), TABLE, Color(0xFF104633))
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .border(1.dp, Color(0xFF397C5D), RoundedCornerShape(16.dp))
                        .padding(4.dp)
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (state.clockwise) "↻" else "↺", color = Color.White, fontSize = 17.sp)
                            Text("КАРТ: ${state.drawPile.size}", color = Color.White, fontSize = 8.sp)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ВЗЯТИ", color = Color.White, fontSize = 8.sp)
                                Box(Modifier.clickable(
                                    enabled = isMyTurn && !state.roundOver,
                                    onClick = onDraw
                                )) {
                                    CardBack(width = centerCardWidth, height = centerCardHeight)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("СТІЛ", color = Color.White, fontSize = 8.sp)
                                val top = state.discardPile.lastOrNull()
                                if (top != null) {
                                    CardFace(top, settings.cardTheme, centerCardWidth, centerCardHeight)
                                } else {
                                    Box(
                                        Modifier.size(centerCardWidth, centerCardHeight)
                                            .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (state.pendingDraw > 0) {
                                Text(
                                    "ШТРАФ +${state.pendingDraw}",
                                    color = Color(0xFFFFD56A),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp
                                )
                            } else if (state.pendingType == CardType.SKIP) {
                                Text("ПРОПУСК", color = Color.White, fontSize = 9.sp)
                            }
                            Text(
                                state.lastAction,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }

                Column(
                    Modifier.weight(1.35f).fillMaxHeight()
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ТВОЇ КАРТИ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("${human?.hand?.size ?: 0}", color = Color.LightGray, fontSize = 10.sp)
                    }

                    LazyRow(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy((-7).dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(human?.hand ?: emptyList(), key = { it.id }) { card ->
                            Box(
                                Modifier.clickable(enabled = isMyTurn && !state.roundOver) {
                                    if (card.type == CardType.WILD || card.type == CardType.DRAW_FOUR) {
                                        colorDialog = card
                                    } else {
                                        onPlay(card, null)
                                    }
                                }
                            ) {
                                CardFace(card, settings.cardTheme, handCardWidth, handCardHeight)
                            }
                        }
                    }
                }
            }

            // Керування ходом
            Row(
                Modifier.fillMaxWidth().height(30.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Button(
                    onClick = onDraw,
                    enabled = isMyTurn && !state.roundOver,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(2.dp),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text(
                        if (state.pendingDraw > 0) "Взяти +${state.pendingDraw}" else "Добрати карту",
                        fontSize = 10.sp
                    )
                }
                OutlinedButton(
                    onClick = onPass,
                    enabled = isMyTurn && !state.roundOver &&
                        (state.hasDrawnThisTurn || state.pendingDraw > 0 ||
                            state.pendingType == CardType.SKIP),
                    modifier = Modifier.width(85.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(2.dp),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text("ПАС", fontSize = 10.sp)
                }
            }
        }
    }

    // Вибір кольору дикої карти
    colorDialog?.let { card ->
        AlertDialog(
            onDismissRequest = { colorDialog = null },
            title = { Text("Обери колір") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        CardColor.RED to "ЧЕРВОНИЙ",
                        CardColor.BLUE to "СИНІЙ",
                        CardColor.GREEN to "ЗЕЛЕНИЙ",
                        CardColor.YELLOW to "ЖОВТИЙ"
                    ).forEach { (color, label) ->
                        Surface(
                            onClick = {
                                colorDialog = null
                                onPlay(card, color)
                            },
                            color = cardColor(color, settings.cardTheme),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                label,
                                Modifier.padding(13.dp),
                                color = if (color == CardColor.YELLOW) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Підсумок раунду
    if (state.roundOver) {
        val winner = state.players.firstOrNull { it.id == state.winnerId }
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text("Раунд завершено", color = GOLD, fontWeight = FontWeight.Black)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Переможець: ${winner?.name ?: "—"}", fontWeight = FontWeight.Bold)
                    state.players.forEach { player ->
                        Text(
                            "${player.name}: +${state.roundScores[player.id] ?: 0} очок • Разом: ${player.totalPoints}"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onBack) { Text("До головного меню") }
            }
        )
    }
}

@Composable
fun CardBack(
    color: CardColor = CardColor.BLUE,
    width: androidx.compose.ui.unit.Dp = 73.dp,
    height: androidx.compose.ui.unit.Dp = 112.dp
) {
    Box(
        Modifier.size(width, height)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF19202D), Color(0xFF343D50), Color(0xFF151B27))
                ),
                RoundedCornerShape(12.dp)
            )
            .border(2.dp, GOLD, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxWidth(0.77f).fillMaxHeight(0.76f)
                .background(Color(0xFF202B39), RoundedCornerShape(50))
                .border(1.dp, GOLD.copy(alpha = 0.7f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text("GRYNTSIV", color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun CardFace(
    card: Card,
    theme: String,
    width: androidx.compose.ui.unit.Dp = 73.dp,
    height: androidx.compose.ui.unit.Dp = 112.dp
) {
    val color = card.color?.let { cardColor(it, theme) } ?: when (theme) {
        "Чорно-золота" -> Color(0xFF29231A)
        "Неонова" -> Color(0xFF21163B)
        "Пастельна" -> Color(0xFF8A789D)
        else -> Color(0xFF30313A)
    }
    val foreground = if (card.color == CardColor.YELLOW && theme == "Класична") {
        Color(0xFF202020)
    } else if (card.color == CardColor.YELLOW && theme == "Пастельна") {
        Color(0xFF292333)
    } else {
        Color.White
    }

    val symbol = when (card.type) {
        CardType.NUMBER -> card.number?.toString() ?: "?"
        CardType.DRAW_TWO -> "+2"
        CardType.DRAW_FOUR -> "+4"
        CardType.SKIP -> "⊘"
        CardType.REVERSE -> "⇄"
        CardType.WILD -> "✦"
    }

    Box(
        Modifier.size(width, height)
            .background(
                Brush.linearGradient(listOf(color, color.copy(alpha = 0.82f))),
                RoundedCornerShape(12.dp)
            )
            .border(
                2.dp,
                if (theme == "Чорно-золота") GOLD else Color.White.copy(alpha = 0.85f),
                RoundedCornerShape(12.dp)
            )
    ) {
        Text(
            symbol,
            Modifier.align(Alignment.Center),
            color = foreground,
            fontSize = if (symbol.length > 2) 24.sp else 34.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            symbol,
            Modifier.align(Alignment.TopStart).padding(5.dp),
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            symbol,
            Modifier.align(Alignment.BottomEnd).padding(5.dp).rotate(180f),
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun cardColor(color: CardColor, theme: String): Color {
    return when (theme) {
        "Чорно-золота" -> when (color) {
            CardColor.RED -> Color(0xFF7D2529)
            CardColor.BLUE -> Color(0xFF244A70)
            CardColor.GREEN -> Color(0xFF286044)
            CardColor.YELLOW -> Color(0xFFB18A2E)
        }
        "Неонова" -> when (color) {
            CardColor.RED -> Color(0xFFEF315F)
            CardColor.BLUE -> Color(0xFF287BFA)
            CardColor.GREEN -> Color(0xFF13A978)
            CardColor.YELLOW -> Color(0xFFE5B923)
        }
        "Пастельна" -> when (color) {
            CardColor.RED -> Color(0xFFE89A9E)
            CardColor.BLUE -> Color(0xFF8EADD9)
            CardColor.GREEN -> Color(0xFF8FC6A4)
            CardColor.YELLOW -> Color(0xFFE9D28C)
        }
        else -> when (color) {
            CardColor.RED -> Color(0xFFC62828)
            CardColor.BLUE -> Color(0xFF1565C0)
            CardColor.GREEN -> Color(0xFF2E7D32)
            CardColor.YELLOW -> Color(0xFFFFC928)
        }
    }

}
