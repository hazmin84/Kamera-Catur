package com.yeayyy.cameracatur.chess

data class Square(val file: Int, val rank: Int) {
    init {
        require(file in 0..7 && rank in 0..7) { "Invalid board square: file=$file, rank=$rank" }
    }

    val uciNotation: String
        get() = "${('a' + file)}${rank + 1}"

    val name: String
        get() = uciNotation

    val fileName: String
        get() = "${'a' + file}"

    val rankName: String
        get() = "${rank + 1}"

    companion object {
        fun fromUci(str: String): Square {
            require(str.length >= 2)
            val f = str[0] - 'a'
            val r = str[1] - '1'
            return Square(f, r)
        }
    }
}

data class ChessMove(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val capturedPiece: ChessPiece? = null
) {
    val uci: String
        get() = "${from.uciNotation}${to.uciNotation}${promotion?.symbol?.lowercaseChar() ?: ""}"

    override fun toString(): String = uci
}
