package com.yeayyy.cameracatur.engine

import com.yeayyy.cameracatur.chess.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class EngineEvaluation(
    val bestMove: ChessMove?,
    val scoreCentipawns: Int,
    val isMate: Boolean = false,
    val mateInMoves: Int = 0,
    val principalVariation: List<String> = emptyList(),
    val commentary: String = ""
)

class StockfishEngineService {

    /**
     * Menganalisis langkah terbaik untuk White dan Black secara serentak
     */
    suspend fun evaluateDualSide(fen: String, depth: Int = 4): Pair<EngineEvaluation, EngineEvaluation> =
        withContext(Dispatchers.Default) {
            val boardWhite = ChessBoard().apply {
                loadFen(fen)
                activeColor = PieceColor.WHITE
            }
            val evalWhite = evaluatePosition(boardWhite, depth)

            val boardBlack = ChessBoard().apply {
                loadFen(fen)
                activeColor = PieceColor.BLACK
            }
            val evalBlack = evaluatePosition(boardBlack, depth)

            Pair(evalWhite, evalBlack)
        }

    /**
     * Enjin Minimax / Alpha-Beta Pruning dioptimumkan untuk Android Native
     */
    suspend fun evaluatePosition(board: ChessBoard, depth: Int = 4): EngineEvaluation =
        withContext(Dispatchers.Default) {
            val isWhiteTurn = board.activeColor == PieceColor.WHITE
            val allMoves = mutableListOf<ChessMove>()

            for (r in 0..7) {
                for (f in 0..7) {
                    val sq = Square(f, r)
                    val p = board.getPiece(sq)
                    if (p != null && p.color == board.activeColor) {
                        allMoves.addAll(board.generateLegalMovesForSquare(sq))
                    }
                }
            }

            if (allMoves.isEmpty()) {
                return@withContext EngineEvaluation(
                    bestMove = null,
                    scoreCentipawns = 0,
                    commentary = "Tiada langkah sah / Permainan tamat"
                )
            }

            var bestMove: ChessMove? = null
            var bestScore = if (isWhiteTurn) -100000 else 100000

            for (move in allMoves) {
                val tempBoard = ChessBoard().apply { loadFen(board.toFen()) }
                tempBoard.makeMove(move)
                val score = alphaBeta(tempBoard, depth - 1, -100000, 100000, !isWhiteTurn)

                if (isWhiteTurn) {
                    if (score > bestScore) {
                        bestScore = score
                        bestMove = move
                    }
                } else {
                    if (score < bestScore) {
                        bestScore = score
                        bestMove = move
                    }
                }
            }

            val evalScore = if (isWhiteTurn) bestScore else -bestScore
            val commentary = generateCommentary(bestMove, evalScore, isWhiteTurn)

            EngineEvaluation(
                bestMove = bestMove,
                scoreCentipawns = evalScore,
                principalVariation = listOf(bestMove?.uci ?: ""),
                commentary = commentary
            )
        }

    private fun alphaBeta(board: ChessBoard, depth: Int, alpha: Int, beta: Int, maximizing: Boolean): Int {
        if (depth == 0) {
            return evaluateBoardMaterial(board)
        }

        var curAlpha = alpha
        var curBeta = beta

        val moves = mutableListOf<ChessMove>()
        for (r in 0..7) {
            for (f in 0..7) {
                val sq = Square(f, r)
                val p = board.getPiece(sq)
                if (p != null && p.color == board.activeColor) {
                    moves.addAll(board.generateLegalMovesForSquare(sq))
                }
            }
        }

        if (moves.isEmpty()) {
            return if (maximizing) -20000 else 20000
        }

        if (maximizing) {
            var maxEval = -100000
            for (move in moves) {
                val nextBoard = ChessBoard().apply { loadFen(board.toFen()) }
                nextBoard.makeMove(move)
                val evaluation = alphaBeta(nextBoard, depth - 1, curAlpha, curBeta, false)
                maxEval = max(maxEval, evaluation)
                curAlpha = max(curAlpha, evaluation)
                if (curBeta <= curAlpha) break
            }
            return maxEval
        } else {
            var minEval = 100000
            for (move in moves) {
                val nextBoard = ChessBoard().apply { loadFen(board.toFen()) }
                nextBoard.makeMove(move)
                val evaluation = alphaBeta(nextBoard, depth - 1, curAlpha, curBeta, true)
                minEval = min(minEval, evaluation)
                curBeta = min(curBeta, evaluation)
                if (curBeta <= curAlpha) break
            }
            return minEval
        }
    }

    private fun evaluateBoardMaterial(board: ChessBoard): Int {
        var score = 0
        for (r in 0..7) {
            for (f in 0..7) {
                val p = board.getPiece(f, r) ?: continue
                val valPiece = p.type.value
                val positionalBonus = getPositionBonus(p.type, p.color, f, r)

                if (p.color == PieceColor.WHITE) {
                    score += (valPiece + positionalBonus)
                } else {
                    score -= (valPiece + positionalBonus)
                }
            }
        }
        return score
    }

    private fun getPositionBonus(type: PieceType, color: PieceColor, f: Int, r: Int): Int {
        val relRank = if (color == PieceColor.WHITE) r else 7 - r
        return when (type) {
            PieceType.PAWN -> (relRank * 10) + (if (f in 3..4) 15 else 0)
            PieceType.KNIGHT -> if (f in 2..5 && r in 2..5) 20 else 0
            PieceType.BISHOP -> if (f in 2..5 && r in 2..5) 15 else 5
            PieceType.ROOK -> if (relRank == 6) 25 else 0
            PieceType.QUEEN -> if (f in 2..5 && r in 2..5) 10 else 0
            PieceType.KING -> if (relRank == 0 && (f <= 2 || f >= 6)) 30 else -10
        }
    }

    private fun generateCommentary(move: ChessMove?, scoreCentipawns: Int, isWhite: Boolean): String {
        if (move == null) return "Tiada langkah cadangan"
        val side = if (isWhite) "Putih" else "Hitam"
        val moveText = move.uci
        val scoreAdvantage = scoreCentipawns / 100.0

        return when {
            scoreAdvantage > 2.0 -> "⭐ Langkah kukuh $side ($moveText) memberikan kelebihan material ketara (+${String.format("%.1f", scoreAdvantage)})."
            scoreAdvantage > 0.5 -> "👍 Langkah cadangan $side ($moveText) mengukuhkan kawalan petak tengah."
            scoreAdvantage < -1.5 -> "⚠️ $side perlu berhati-hati. Langkah pertahanan terbaik ialah $moveText."
            else -> "⚖️ Kedudukan seimbang. $side disyorkan memainkan $moveText."
        }
    }
}
