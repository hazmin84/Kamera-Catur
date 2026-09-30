package com.yeayyy.cameracatur.chess

enum class PieceColor {
    WHITE, BLACK
}

enum class PieceType(val symbol: Char, val unicodeWhite: String, val unicodeBlack: String, val value: Int) {
    PAWN('P', "♙", "♟", 100),
    KNIGHT('N', "♘", "♞", 320),
    BISHOP('B', "♗", "♝", 330),
    ROOK('R', "♖", "♜", 500),
    QUEEN('Q', "♕", "♛", 900),
    KING('K', "♔", "♚", 20000)
}

data class ChessPiece(
    val type: PieceType,
    val color: PieceColor
) {
    val fenChar: Char
        get() = if (color == PieceColor.WHITE) type.symbol.uppercaseChar() else type.symbol.lowercaseChar()

    val displaySymbol: String
        get() = if (color == PieceColor.WHITE) type.unicodeWhite else type.unicodeBlack

    companion object {
        fun fromFenChar(c: Char): ChessPiece? {
            val color = if (c.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
            val type = when (c.uppercaseChar()) {
                'P' -> PieceType.PAWN
                'N' -> PieceType.KNIGHT
                'B' -> PieceType.BISHOP
                'R' -> PieceType.ROOK
                'Q' -> PieceType.QUEEN
                'K' -> PieceType.KING
                else -> return null
            }
            return ChessPiece(type, color)
        }
    }
}
