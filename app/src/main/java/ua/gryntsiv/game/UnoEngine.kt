package ua.gryntsiv.game

import kotlin.random.Random

/** Core rules engine for GRYNTSIV. UI and networking must treat GameState as immutable. */
enum class CardColor { RED, BLUE, GREEN, YELLOW }
enum class CardType { NUMBER, DRAW_TWO, DRAW_FOUR, SKIP, REVERSE, WILD }

data class Card(
    val id: String,
    val color: CardColor?,
    val type: CardType,
    val number: Int? = null
) {
    val label: String get() = when (type) {
        CardType.NUMBER -> "${color?.name}:$number"
        CardType.DRAW_TWO -> "${color?.name} +2"
        CardType.DRAW_FOUR -> "+4"
        CardType.SKIP -> "${color?.name} SKIP"
        CardType.REVERSE -> "${color?.name} REV"
        CardType.WILD -> "WILD"
    }

    fun points(): Int = when (type) {
        CardType.DRAW_TWO, CardType.SKIP, CardType.REVERSE -> 20
        CardType.DRAW_FOUR, CardType.WILD -> 50
        CardType.NUMBER -> number ?: 0
    }
}

data class Player(
    val id: String,
    val name: String,
    val isBot: Boolean,
    val hand: List<Card>,
    val totalPoints: Int = 0
)

data class GameSettings(
    val autoPass: Boolean = false,
    val cardTheme: String = "Класична"
)

data class RoundResult(
    val winnerId: String,
    val scores: Map<String, Int>,
    val totals: Map<String, Int>
)

data class GameState(
    val players: List<Player>,
    val drawPile: List<Card>,
    val discardPile: List<Card>,
    val currentPlayerId: String,
    val clockwise: Boolean = true,
    val chosenColor: CardColor? = null,
    val pendingDraw: Int = 0,
    val pendingType: CardType? = null,
    val skipColor: CardColor? = null,
    val drawnCardId: String? = null,
    val hasDrawnThisTurn: Boolean = false,
    val roundOver: Boolean = false,
    val roundScores: Map<String, Int> = emptyMap(),
    val winnerId: String? = null,
    val roundNumber: Int = 1,
    val lastAction: String = "Гру розпочато"
)

class UnoEngine(private val random: Random = Random.Default) {
    private val colors = CardColor.entries

    private fun createDeck(): MutableList<Card> {
        val cards = mutableListOf<Card>()
        var id = 0
        fun add(color: CardColor?, type: CardType, number: Int? = null, copies: Int = 1) {
            repeat(copies) { cards += Card("card_${id++}", color, type, number) }
        }
        colors.forEach { color ->
            add(color, CardType.NUMBER, 0)
            (1..9).forEach { add(color, CardType.NUMBER, it, 2) }
            add(color, CardType.DRAW_TWO, copies = 2)
            add(color, CardType.SKIP, copies = 2)
            add(color, CardType.REVERSE, copies = 2)
        }
        repeat(4) { add(null, CardType.WILD); add(null, CardType.DRAW_FOUR) }
        return cards
    }

    /** Backward-compatible overload used by the original prototype UI. */
    fun newGame(names: List<String>, botCount: Int): GameState =
        newGame(names.firstOrNull() ?: "Гравець 1", botCount)

    /** Starts a local game: one human plus zero to five bots. */
    fun newGame(humanName: String, botCount: Int, roundNumber: Int = 1, previousWinnerId: String? = null): GameState {
        val safeBotCount = botCount.coerceIn(0, 5)
        val names = listOf(humanName.trim().ifBlank { "Гравець 1" }) +
            (1..safeBotCount).map { "Бот $it" }
        val players = names.mapIndexed { index, name ->
            Player("player_$index", name, index > 0, emptyList())
        }
        val deck = createDeck().shuffled(random).toMutableList()
        val dealt = players.map { player ->
            player.copy(hand = List(7) { deck.removeAt(deck.lastIndex) })
        }
        var first = deck.removeAt(deck.lastIndex)
        while (first.type == CardType.WILD || first.type == CardType.DRAW_FOUR) {
            deck.add(first)
            deck.shuffle(random)
            first = deck.removeAt(deck.lastIndex)
        }
        val starter = dealt.firstOrNull { it.id == previousWinnerId } ?: dealt.random(random)
        return GameState(
            players = dealt,
            drawPile = deck,
            discardPile = listOf(first),
            currentPlayerId = starter.id,
            roundNumber = roundNumber,
            lastAction = "Перший хід: ${starter.name}"
        )
    }

    fun currentPlayer(state: GameState): Player? = state.players.firstOrNull { it.id == state.currentPlayerId }

    fun playableCards(state: GameState, playerId: String = state.currentPlayerId): List<Card> =
        state.players.firstOrNull { it.id == playerId }?.hand?.filter { isPlayable(state, it) }.orEmpty()

    fun isPlayable(state: GameState, card: Card): Boolean {
        if (state.roundOver) return false
        val top = state.discardPile.lastOrNull() ?: return true
        if (state.pendingDraw > 0) {
            return when (state.pendingType) {
                CardType.DRAW_TWO -> card.type == CardType.DRAW_TWO
                CardType.DRAW_FOUR -> card.type == CardType.DRAW_TWO && card.color == state.chosenColor
                else -> false
            }
        }
        if (state.pendingType == CardType.SKIP) {
            return card.type == CardType.SKIP && card.color == state.skipColor
        }
        if (card.type == CardType.WILD || card.type == CardType.DRAW_FOUR) return true
        return card.color == state.chosenColor ||
            card.color == top.color ||
            (card.type == top.type && card.type in setOf(CardType.DRAW_TWO, CardType.SKIP, CardType.REVERSE)) ||
            (card.type == CardType.NUMBER && top.type == CardType.NUMBER && card.number == top.number)
    }

    /** Backward-compatible overload used by the original prototype UI. */
    fun play(state: GameState, playerId: String, card: Card, declaredColor: CardColor? = null): GameState =
        play(state, playerId, card.id, declaredColor)

    fun play(state: GameState, playerId: String, cardId: String, declaredColor: CardColor? = null): GameState {
        if (state.roundOver || state.currentPlayerId != playerId) return state
        val playerIndex = state.players.indexOfFirst { it.id == playerId }
        if (playerIndex < 0) return state
        val player = state.players[playerIndex]
        val card = player.hand.firstOrNull { it.id == cardId } ?: return state
        if (!isPlayable(state, card)) return state
        if ((card.type == CardType.WILD || card.type == CardType.DRAW_FOUR) && declaredColor == null) return state

        val players = state.players.toMutableList()
        players[playerIndex] = player.copy(hand = player.hand.filterNot { it.id == card.id })
        var next = state.copy(
            players = players,
            discardPile = state.discardPile + card,
            chosenColor = declaredColor ?: card.color,
            drawnCardId = null,
            hasDrawnThisTurn = false,
            lastAction = "${player.name} зіграв ${card.label}"
        )

        // A final +2/+4 still makes the next player take the penalty before scoring.
        if (players[playerIndex].hand.isEmpty()) {
            if (card.type == CardType.DRAW_TWO || card.type == CardType.DRAW_FOUR) {
                val nextId = nextPlayerId(next, playerId)
                val amount = if (card.type == CardType.DRAW_TWO) 2 else 4
                next = next.copy(pendingDraw = amount, pendingType = card.type, currentPlayerId = nextId)
                next = drawPenaltyAndFinish(next, playerId)
            } else {
                next = finishRound(next, playerId)
            }
            return next
        }

        next = when (card.type) {
            CardType.REVERSE -> {
                val reversed = next.copy(clockwise = !next.clockwise)
                reversed.copy(currentPlayerId = nextPlayerId(reversed, playerId))
            }
            CardType.SKIP -> next.copy(
                currentPlayerId = nextPlayerId(next, playerId),
                pendingType = CardType.SKIP,
                skipColor = card.color
            )
            CardType.DRAW_TWO -> next.copy(
                currentPlayerId = nextPlayerId(next, playerId),
                pendingDraw = if (state.pendingType == CardType.DRAW_FOUR) 2 else state.pendingDraw + 2,
                pendingType = CardType.DRAW_TWO,
                skipColor = null
            )
            CardType.DRAW_FOUR -> next.copy(
                currentPlayerId = nextPlayerId(next, playerId),
                pendingDraw = if (state.pendingType == CardType.DRAW_TWO) state.pendingDraw + 4 else 4,
                pendingType = CardType.DRAW_FOUR,
                skipColor = null
            )
            else -> next.copy(currentPlayerId = nextPlayerId(next, playerId), pendingType = null, skipColor = null)
        }
        return next
    }

    /** Backward-compatible method name used by the original prototype UI. */
    fun drawOne(state: GameState, playerId: String): GameState = draw(state, playerId)

    /** Draw one card during a normal turn, or take the full accumulated penalty. */
    fun draw(state: GameState, playerId: String): GameState {
        if (state.roundOver || state.currentPlayerId != playerId) return state
        if (state.pendingDraw > 0) return takePenalty(state, playerId)
        if (state.pendingType == CardType.SKIP) return skipTurn(state, playerId)
        if (state.hasDrawnThisTurn) return state
        val prepared = replenishIfNeeded(state)
        if (prepared.drawPile.isEmpty()) return pass(prepared, playerId)
        val card = prepared.drawPile.last()
        val index = prepared.players.indexOfFirst { it.id == playerId }
        if (index < 0) return state
        val players = prepared.players.toMutableList()
        players[index] = players[index].copy(hand = players[index].hand + card)
        var next = prepared.copy(
            players = players,
            drawPile = prepared.drawPile.dropLast(1),
            drawnCardId = card.id,
            hasDrawnThisTurn = true,
            lastAction = "${players[index].name} добрав карту"
        )
        if (isPlayable(next, card) && next.players[index].isBot) {
            return play(next, playerId, card.id, if (card.color == null) chooseColor(players[index].hand + card) else null)
        }
        return next
    }

    fun pass(state: GameState, playerId: String): GameState {
        if (state.roundOver || state.currentPlayerId != playerId) return state
        if (state.pendingDraw > 0) return takePenalty(state, playerId)
        if (state.pendingType == CardType.SKIP) return skipTurn(state, playerId)
        if (!state.hasDrawnThisTurn) return state
        val drawn = state.players.firstOrNull { it.id == playerId }?.hand?.firstOrNull { it.id == state.drawnCardId }
        if (drawn != null && isPlayable(state, drawn)) return state
        return advance(state.copy(lastAction = "${currentPlayer(state)?.name ?: "Гравець"} пропустив хід"), playerId)
    }

    fun skipTurn(state: GameState, playerId: String): GameState {
        if (state.roundOver || state.currentPlayerId != playerId || state.pendingType != CardType.SKIP) return state
        return advance(
            state.copy(pendingType = null, skipColor = null, hasDrawnThisTurn = false, drawnCardId = null,
                lastAction = "${currentPlayer(state)?.name ?: "Гравець"} пропустив хід"),
            playerId
        )
    }

    fun botAction(state: GameState): GameState {
        if (state.roundOver) return state
        val player = currentPlayer(state) ?: return state
        if (!player.isBot) return state
        if (state.pendingDraw > 0) {
            val response = playableCards(state, player.id).firstOrNull()
            return if (response != null) play(state, player.id, response.id,
                if (response.color == null) chooseColor(player.hand) else null)
            else takePenalty(state, player.id)
        }
        if (state.pendingType == CardType.SKIP) {
            val response = playableCards(state, player.id).firstOrNull()
            return if (response != null) play(state, player.id, response.id) else skipTurn(state, player.id)
        }
        val playable = playableCards(state, player.id)
        if (playable.isNotEmpty()) {
            val card = chooseBotCard(playable)
            return play(state, player.id, card.id, if (card.color == null) chooseColor(player.hand) else null)
        }
        val drawn = draw(state, player.id)
        if (drawn.roundOver || drawn.currentPlayerId != player.id) return drawn
        val justDrawn = drawn.players.first { it.id == player.id }.hand.firstOrNull { it.id == drawn.drawnCardId }
        return if (justDrawn != null && isPlayable(drawn, justDrawn)) {
            play(drawn, player.id, justDrawn.id, if (justDrawn.color == null) chooseColor(drawn.players.first { it.id == player.id }.hand) else null)
        } else pass(drawn, player.id)
    }

    private fun takePenalty(state: GameState, playerId: String): GameState {
        if (state.pendingDraw <= 0) return state
        val amount = state.pendingDraw
        val prepared = replenishIfNeeded(state)
        val actual = minOf(amount, prepared.drawPile.size)
        val cards = prepared.drawPile.takeLast(actual)
        val index = prepared.players.indexOfFirst { it.id == playerId }
        if (index < 0) return state
        val players = prepared.players.toMutableList()
        players[index] = players[index].copy(hand = players[index].hand + cards)
        return advance(
            prepared.copy(players = players, drawPile = prepared.drawPile.dropLast(actual), pendingDraw = 0,
                pendingType = null, skipColor = null, drawnCardId = null, hasDrawnThisTurn = false,
                lastAction = "${players[index].name} взяв штраф: $actual карт"), playerId
        )
    }

    private fun drawPenaltyAndFinish(state: GameState, winnerId: String): GameState {
        val nextId = state.currentPlayerId
        val amount = state.pendingDraw
        val prepared = replenishIfNeeded(state)
        val actual = minOf(amount, prepared.drawPile.size)
        val cards = prepared.drawPile.takeLast(actual)
        val index = prepared.players.indexOfFirst { it.id == nextId }
        if (index < 0) return finishRound(prepared, winnerId)
        val players = prepared.players.toMutableList()
        players[index] = players[index].copy(hand = players[index].hand + cards)
        return finishRound(prepared.copy(players = players, drawPile = prepared.drawPile.dropLast(actual)), winnerId)
    }

    private fun advance(state: GameState, fromPlayerId: String): GameState = state.copy(
        currentPlayerId = nextPlayerId(state, fromPlayerId),
        drawnCardId = null,
        hasDrawnThisTurn = false
    )

    private fun nextPlayerId(state: GameState, from: String): String {
        if (state.players.isEmpty()) return from
        val index = state.players.indexOfFirst { it.id == from }.let { if (it < 0) 0 else it }
        val step = if (state.clockwise) 1 else -1
        return state.players[(index + step + state.players.size) % state.players.size].id
    }

    private fun replenishIfNeeded(state: GameState): GameState {
        if (state.drawPile.isNotEmpty() || state.discardPile.size <= 1) return state
        val top = state.discardPile.last()
        val recycled = state.discardPile.dropLast(1).shuffled(random)
        return state.copy(drawPile = recycled, discardPile = listOf(top))
    }

    private fun finishRound(state: GameState, winnerId: String): GameState {
        val scores = state.players.associate { player ->
            player.id to if (player.id == winnerId) 0 else player.hand.sumOf(Card::points)
        }
        val updated = state.players.map { player -> player.copy(totalPoints = player.totalPoints + (scores[player.id] ?: 0)) }
        return state.copy(
            players = updated,
            roundOver = true,
            roundScores = scores,
            winnerId = winnerId,
            pendingDraw = 0,
            pendingType = null,
            skipColor = null,
            lastAction = "Раунд завершено. Переможець: ${updated.firstOrNull { it.id == winnerId }?.name ?: "—"}"
        )
    }

    fun chooseColor(hand: List<Card>): CardColor = colors.maxByOrNull { color -> hand.count { it.color == color } } ?: CardColor.RED

    private fun chooseBotCard(cards: List<Card>): Card = cards.maxByOrNull { card ->
        when (card.type) {
            CardType.DRAW_FOUR -> 8
            CardType.DRAW_TWO -> 7
            CardType.SKIP -> 6
            CardType.REVERSE -> 5
            CardType.WILD -> 4
            CardType.NUMBER -> 1
        }
    } ?: cards.first()
}
