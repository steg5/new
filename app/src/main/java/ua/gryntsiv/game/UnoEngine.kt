package ua.gryntsiv.game

import kotlin.random.Random

enum class CardColor { RED, BLUE, GREEN, YELLOW }
enum class CardType { NUMBER, DRAW_TWO, DRAW_FOUR, SKIP, REVERSE, WILD }

data class Card(val id: String, val color: CardColor?, val type: CardType, val number: Int? = null) {
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

data class Player(val id: String, val name: String, val isBot: Boolean, val hand: List<Card>, val totalPoints: Int = 0)
data class GameSettings(val autoPass: Boolean = false, val cardTheme: String = "Класична")
data class GameState(
    val players: List<Player>,
    val drawPile: List<Card>,
    val discardPile: List<Card>,
    val currentPlayerId: String,
    val clockwise: Boolean = true,
    val chosenColor: CardColor? = null,
    val pendingDraw: Int = 0,
    val pendingType: CardType? = null,
    val drawnCardId: String? = null,
    val roundOver: Boolean = false,
    val roundScores: Map<String, Int> = emptyMap()
)

class UnoEngine {
    private val colors = CardColor.entries
    private fun deck(): MutableList<Card> {
        val cards = mutableListOf<Card>()
        var n = 0
        fun add(color: CardColor?, type: CardType, number: Int? = null, copies: Int = 1) {
            repeat(copies) { cards += Card("c${n++}", color, type, number) }
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

    fun newGame(names: List<String>, botCount: Int): GameState {
        val allNames = (names + (1..botCount).map { "Бот $it" }).take(6)
        val players = allNames.mapIndexed { i, name ->
            Player("p$i", name, name.startsWith("Бот"), emptyList())
        }
        val cards = deck().shuffled().toMutableList()
        val dealt = players.map { p -> p.copy(hand = List(7) { cards.removeAt(cards.lastIndex) }) }
        var first = cards.removeAt(cards.lastIndex)
        while (first.type == CardType.DRAW_FOUR || first.type == CardType.WILD) {
            cards.add(first); cards.shuffle(); first = cards.removeAt(cards.lastIndex)
        }
        return GameState(dealt, cards, listOf(first), dealt.random().id)
    }

    fun play(state: GameState, playerId: String, card: Card, declaredColor: CardColor? = null): GameState {
        if (state.roundOver || state.currentPlayerId != playerId) return state
        val index = state.players.indexOfFirst { it.id == playerId }
        if (index < 0) return state
        val player = state.players[index]
        if (player.hand.none { it.id == card.id } || !isPlayable(state, card)) return state
        if ((card.type == CardType.WILD || card.type == CardType.DRAW_FOUR) && declaredColor == null) return state
        val updatedPlayers = state.players.toMutableList()
        updatedPlayers[index] = player.copy(hand = player.hand.filterNot { it.id == card.id })
        var next = state.copy(players = updatedPlayers, discardPile = state.discardPile + card,
            chosenColor = declaredColor ?: card.color, drawnCardId = null)
        if (updatedPlayers[index].hand.isEmpty()) {
            return finishRound(next, playerId)
        }
        next = when (card.type) {
            CardType.REVERSE -> next.copy(clockwise = !next.clockwise)
            CardType.SKIP -> next.copy(currentPlayerId = nextPlayer(next, playerId))
            CardType.DRAW_TWO -> next.copy(pendingDraw = state.pendingDraw + 2, pendingType = CardType.DRAW_TWO,
                currentPlayerId = nextPlayer(next, playerId))
            CardType.DRAW_FOUR -> next.copy(pendingDraw = state.pendingDraw + 4, pendingType = CardType.DRAW_FOUR,
                currentPlayerId = nextPlayer(next, playerId))
            else -> next
        }
        if (card.type != CardType.SKIP && card.type != CardType.DRAW_TWO && card.type != CardType.DRAW_FOUR)
            next = next.copy(currentPlayerId = nextPlayer(next, playerId))
        return next
    }

    fun isPlayable(state: GameState, card: Card): Boolean {
        val top = state.discardPile.lastOrNull() ?: return true
        if (state.pendingDraw > 0) {
            return when (state.pendingType) {
                CardType.DRAW_TWO -> card.type == CardType.DRAW_TWO ||
                    (state.pendingType == CardType.DRAW_TWO && card.type == CardType.DRAW_FOUR && card.color == state.chosenColor)
                CardType.DRAW_FOUR -> card.type == CardType.DRAW_TWO && card.color == state.chosenColor
                else -> false
            }
        }
        if (card.type == CardType.WILD || card.type == CardType.DRAW_FOUR) return true
        return card.color == state.chosenColor || card.color == top.color ||
            (card.type == top.type && card.type in setOf(CardType.DRAW_TWO, CardType.SKIP, CardType.REVERSE)) ||
            (card.type == CardType.NUMBER && top.type == CardType.NUMBER && card.number == top.number)
    }

    fun drawOne(state: GameState, playerId: String): GameState {
        if (state.roundOver || state.currentPlayerId != playerId || state.drawPile.isEmpty()) return state
        val card = state.drawPile.last()
        val pIndex = state.players.indexOfFirst { it.id == playerId }
        if (pIndex < 0) return state
        val players = state.players.toMutableList()
        players[pIndex] = players[pIndex].copy(hand = players[pIndex].hand + card)
        return state.copy(players = players, drawPile = state.drawPile.dropLast(1), drawnCardId = card.id)
    }

    fun pass(state: GameState, playerId: String): GameState {
        if (state.roundOver || state.currentPlayerId != playerId) return state
        return state.copy(currentPlayerId = nextPlayer(state, playerId), drawnCardId = null)
    }

    private fun nextPlayer(state: GameState, from: String): String {
        val idx = state.players.indexOfFirst { it.id == from }
        if (idx < 0) return state.players.first().id
        val step = if (state.clockwise) 1 else -1
        return state.players[(idx + step + state.players.size) % state.players.size].id
    }

    private fun finishRound(state: GameState, winnerId: String): GameState {
        val scores = state.players.associate { p ->
            p.id to if (p.id == winnerId) 0 else p.hand.sumOf { it.points() }
        }
        val players = state.players.map { p -> p.copy(totalPoints = p.totalPoints + (scores[p.id] ?: 0)) }
        return state.copy(players = players, roundOver = true, roundScores = scores)
    }
}
