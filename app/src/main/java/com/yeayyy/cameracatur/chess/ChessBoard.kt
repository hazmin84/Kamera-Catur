package com.yeayyy.cameracatur.chess

import kotlin.math.abs

class ChessBoard {

    // 8x8 Board: board[rank][file], where rank 0 is 1st rank, rank 7 is 8th rank
    private val squares = Array(8) { arrayOfNulls<ChessPiece>(8) }
    var activeColor: PieceColor = PieceColor.WHITE
    var castlingRights: String = "KQkq"
    var enPassantTarget: Square? = null
    var halfmoveClock: Int = 0
    var fullmoveNumber: Int = 1

    init {
        setupStandardBoard()
    }

    fun clear() {
        for (r in 0..7) {
            for (f in 0..7) {
                squares[r][f] = null
            }
        }
        activeColor = PieceColor.WHITE
        castlingRights = "-"
        enPassantTarget = null
        halfmoveClock = 0
        fullmoveNumber = 1
    }

    fun setupStandardBoard() {
        loadFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    }

    fun getPiece(file: Int, rank: Int): ChessPiece? {
        if (file !in 0..7 || rank !in 0..7) return null
        return squares[rank][file]
    }

    fun getPiece(square: Square): ChessPiece? = getPiece(square.file, square.rank)

    fun setPiece(file: Int, rank: Int, piece: ChessPiece?) {
        if (file in 0..7 && rank in 0..7) {
            squares[rank][file] = piece
        }
    }

    fun setPiece(square: Square, piece: ChessPiece?) = setPiece(square.file, square.rank, piece)

    fun loadFen(fen: String): Boolean {
        val parts = fen.trim().split("\\s+".toRegex())
        if (parts.isEmpty()) return false

        clear()
        val boardPart = parts[0]
        val ranks = boardPart.split("/")
        if (ranks.size != 8) return false

        for (r in 0..7) {
            val rankIndex = 7 - r // FEN starts from 8th rank (index 7)
            val rankStr = ranks[r]
            var fileIndex = 0

            for (ch in rankStr) {
                if (ch.isDigit()) {
                    fileIndex += ch.digitToInt()
                } else {
                    if (fileIndex < 8) {
                        squares[rankIndex][fileIndex] = ChessPiece.fromFenChar(ch)
                        fileIndex++
                    }
                }
            }
        }

        if (parts.size > 1) {
            activeColor = if (parts[1].equals("w", ignoreCase = true)) PieceColor.WHITE else PieceColor.BLACK
        }
        if (parts.size > 2) {
            castlingRights = parts[2]
        }
        if (parts.size > 3 && parts[3] != "-") {
            try {
                enPassantTarget = Square.fromUci(parts[3])
            } catch (_: Exception) {
                enPassantTarget = null
            }
        }
        if (parts.size > 4) {
            halfmoveClock = parts[4].toIntOrNull() ?: 0
        }
        if (parts.size > 5) {
            fullmoveNumber = parts[5].toIntOrNull() ?: 1
        }

        return true
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var emptyCount = 0
            for (f in 0..7) {
                val piece = squares[r][f]
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(piece.fenChar)
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount)
            }
            if (r > 0) {
                sb.append('/')
            }
        }

        sb.append(' ')
        sb.append(if (activeColor == PieceColor.WHITE) 'w' else 'b')
        sb.append(' ')
        sb.append(if (castlingRights.isEmpty()) "-" else castlingRights)
        sb.append(' ')
        sb.append(enPassantTarget?.uciNotation ?: "-")
        sb.append(' ')
        sb.append(halfmoveClock)
        sb.append(' ')
        sb.append(fullmoveNumber)

        return sb.toString()
    }

    fun makeMove(move: ChessMove): Boolean {
        val piece = getPiece(move.from) ?: return false
        if (piece.color != activeColor) return false

        // Check if destination is valid basic path
        val destPiece = getPiece(move.to)
        if (destPiece != null && destPiece.color == piece.color) return false

        // Execute move
        setPiece(move.to, move.promotion?.let { ChessPiece(it, piece.color) } ?: piece)
        setPiece(move.from, null)

        // Switch side
        activeColor = if (activeColor == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        if (activeColor == PieceColor.WHITE) {
            fullmoveNumber++
        }
        return true
    }

    fun generateLegalMovesForSquare(square: Square): List<ChessMove> {
        val piece = getPiece(square) ?: return emptyList()
        val moves = mutableListOf<ChessMove>()
        val (f, r) = square.file to square.rank

        when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (piece.color == PieceColor.WHITE) 1 else -1
                val startRank = if (piece.color == PieceColor.WHITE) 1 else 6

                // 1 step forward
                val nextRank = r + dir
                if (nextRank in 0..7 && getPiece(f, nextRank) == null) {
                    moves.add(ChessMove(square, Square(f, nextRank)))
                    // 2 steps forward from start
                    val twoRanks = r + 2 * dir
                    if (r == startRank && getPiece(f, twoRanks) == null) {
                        moves.add(ChessMove(square, Square(f, twoRanks)))
                    }
                }

                // Captures
                for (df in listOf(-1, 1)) {
                    val targetF = f + df
                    if (targetF in 0..7 && nextRank in 0..7) {
                        val targetPiece = getPiece(targetF, nextRank)
                        if (targetPiece != null && targetPiece.color != piece.color) {
                            moves.add(ChessMove(square, Square(targetF, nextRank), capturedPiece = targetPiece))
                        }
                    }
                }
            }
            PieceType.KNIGHT -> {
                val offsets = listOf(
                    -2 to -1, -2 to 1, -1 to -2, -1 to 2,
                    1 to -2, 1 to 2, 2 to -1, 2 to 1
                )
                for ((df, dr) in offsets) {
                    val tf = f + df
                    val tr = r + dr
                    if (tf in 0..7 && tr in 0..7) {
                        val target = getPiece(tf, tr)
                        if (target == null || target.color != piece.color) {
                            moves.add(ChessMove(square, Square(tf, tr), capturedPiece = target))
                        }
                    }
                }
            }
            PieceType.BISHOP -> addSlidingMoves(square, piece, listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1), moves)
            PieceType.ROOK -> addSlidingMoves(square, piece, listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1), moves)
            PieceType.QUEEN -> addSlidingMoves(square, piece, listOf(
                -1 to -1, -1 to 1, 1 to -1, 1 to 1,
                -1 to 0, 1 to 0, 0 to -1, 0 to 1
            ), moves)
            PieceType.KING -> {
                for (df in -1..1) {
                    for (dr in -1..1) {
                        if (df == 0 && dr == 0) continue
                        val tf = f + df
                        val tr = r + dr
                        if (tf in 0..7 && tr in 0..7) {
                            val target = getPiece(tf, tr)
                            if (target == null || target.color != piece.color) {
                                moves.add(ChessMove(square, Square(tf, tr), capturedPiece = target))
                            }
                        }
                    }
                }
            }
        }
        return moves
    }

    private fun addSlidingMoves(from: Square, piece: ChessPiece, directions: List<Pair<Int, Int>>, out: MutableList<ChessMove>) {
        for ((df, dr) in directions) {
            var curF = from.file + df
            var curR = from.rank + dr
            while (curF in 0..7 && curR in 0..7) {
                val targetPiece = getPiece(curF, curR)
                if (targetPiece == null) {
                    out.add(ChessMove(from, Square(curF, curR)))
                } else {
                    if (targetPiece.color != piece.color) {
                        out.add(ChessMove(from, Square(curF, curR), capturedPiece = targetPiece))
                    }
                    break
                }
                curF += df
                curR += dr
            }
        }
    }
}
