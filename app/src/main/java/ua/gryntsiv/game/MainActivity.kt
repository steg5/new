package ua.gryntsiv.game

import android.os.Bundle
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
    var screen by remember { mutableStateOf("home") }
    var settings by remember { mutableStateOf(GameSettings()) }
    var showScores by remember { mutableStateOf(false) }
    val engine = remember { UnoEngine() }
    var game by remember { mutableStateOf<GameState?>(null) }

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
                "history" -> HistoryScreen(onBack = { screen = "home" })
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
fun HistoryScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Історія ігор", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(15.dp))
        Text(
            "Збереження завершених партій та історія результатів ще потребують реалізації.",
            color = Color.LightGray
        )
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
    var reaction by remember { mutableStateOf("") }

    val human = state.players.firstOrNull()
    val current = state.players.firstOrNull { it.id == state.currentPlayerId }
    val isMyTurn = current?.id == human?.id
    val playable = remember(state, human) {
        if (human != null) {
            state.players.firstOrNull { it.id == human.id }?.hand
                ?.filter { card ->
                    // Перевірка доступності через стан рушія виконується під час натискання.
                    true
                }.orEmpty()
        } else emptyList()
    }

    Column(
        Modifier.fillMaxSize().background(BG).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Верхня панель
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("GRYNTSIV", color = GOLD, fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text("РАУНД ${state.roundNumber}", color = Color.Gray, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (state.roundOver) "РАУНД ЗАВЕРШЕНО"
                    else if (isMyTurn) "ТВІЙ ХІД" else "ХІД: ${current?.name ?: "—"}",
                    color = if (isMyTurn) GOLD else Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text("Вийти", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }

        // Суперники
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            state.players.drop(1).forEach { player ->
                val active = player.id == state.currentPlayerId
                Surface(
                    color = if (active) Color(0xFF39483E) else PANEL,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                        .border(
                            if (active) 1.dp else 0.dp,
                            if (active) GOLD else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Column(
                        Modifier.padding(vertical = 4.dp, horizontal = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (player.isBot) "🤖" else "👤", fontSize = 19.sp)
                        Text(
                            player.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text("${player.hand.size} карт", color = Color.LightGray, fontSize = 9.sp)
                    }
                }
            }
        }

        // Ігровий стіл
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1C7555), TABLE, Color(0xFF104633))
                    ),
                    RoundedCornerShape(28.dp)
                )
                .border(2.dp, Color(0xFF397C5D), RoundedCornerShape(28.dp))
                .padding(12.dp)
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
                    Text(
                        if (state.clockwise) "↻" else "↺",
                        fontSize = 26.sp,
                        color = Color.White
                    )
                    Text(
                        "КАРТ: ${state.drawPile.size}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ВЗЯТИ", color = Color.White, fontSize = 9.sp)
                        Box(Modifier.clickable(enabled = isMyTurn && !state.roundOver, onClick = onDraw)) {
                            CardBack(width = 54.dp, height = 80.dp)
                        }
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("СТІЛ", color = Color.White, fontSize = 9.sp)
                        val top = state.discardPile.lastOrNull()
                        if (top != null) {
                            CardFace(top, settings.cardTheme, 54.dp, 80.dp)
                        } else {
                            Box(
                                Modifier.size(54.dp, 80.dp)
                                    .border(1.dp, Color.White, RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.pendingDraw > 0) {
                        Text(
                            "ШТРАФ: +${state.pendingDraw}",
                            color = Color(0xFFFFD56A),
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp
                        )
                    } else if (state.pendingType == CardType.SKIP) {
                        Text("ПРОПУСК ХОДУ", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Text("GRYNTSIV", color = Color.White.copy(alpha = 0.25f), fontSize = 12.sp)
                    }
                    Text(
                        state.lastAction,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }

        // Реакції
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("😂", "🔥", "😎", "👏", "😮", "😡", "❤️", "🤔", "🙈", "💪", "🎉", "😭")
                .forEach { emoji ->
                    Text(
                        emoji,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable { reaction = emoji }.padding(1.dp)
                    )
                }
        }
        if (reaction.isNotEmpty()) {
            Text(
                "Твоя реакція: $reaction",
                color = GOLD,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Рука гравця
        Row(
            Modifier.fillMaxWidth().padding(top = 1.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ТВОЇ КАРТИ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("${human?.hand?.size ?: 0} карт", color = Color.LightGray, fontSize = 11.sp)
        }

        LazyRow(
            Modifier.fillMaxWidth().height(82.dp),
            horizontalArrangement = Arrangement.spacedBy((-9).dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
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
                    CardFace(card, settings.cardTheme, 52.dp, 80.dp)
                }
            }
        }

        // Кнопки керування ходом
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Button(
                onClick = onDraw,
                enabled = isMyTurn && !state.roundOver,
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(13.dp)
            ) {
                Text(
                    if (state.pendingDraw > 0) "Взяти +${state.pendingDraw}"
                    else "Добрати карту",
                    fontSize = 12.sp
                )
            }
            OutlinedButton(
                onClick = onPass,
                enabled = isMyTurn && !state.roundOver &&
                    (state.hasDrawnThisTurn || state.pendingDraw > 0 ||
                        state.pendingType == CardType.SKIP),
                modifier = Modifier.weight(0.75f).height(36.dp),
                shape = RoundedCornerShape(13.dp)
            ) {
                Text("ПАС", fontSize = 12.sp)
            }
        }
    }

    // Вибір кольору для дикої карти
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
                        val c = cardColor(color, settings.cardTheme)
                        Surface(
                            onClick = {
                                colorDialog = null
                                onPlay(card, color)
                            },
                            color = c,
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
                            "${player.name}: +${state.roundScores[player.id] ?: 0} очок  •  Разом: ${player.totalPoints}"
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
