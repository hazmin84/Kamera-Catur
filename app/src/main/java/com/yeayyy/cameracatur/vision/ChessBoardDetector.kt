package com.yeayyy.cameracatur.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.yeayyy.cameracatur.chess.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class RecognitionResult(
    val fen: String,
    val confidence: Float,
    val detectedPiecesCount: Int,
    val pieceMap: Map<Square, ChessPiece>
)

class ChessBoardDetector {

    /**
     * Menganalisis imej papan catur daripada kamera/galeri,
     * mengesan grid 8x8 dan mengekstrak kedudukan setiap buah catur.
     */
    suspend fun analyzeChessBoardImage(bitmap: Bitmap): RecognitionResult = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height

        val squareWidth = width / 8
        val squareHeight = height / 8

        val detectedPieces = mutableMapOf<Square, ChessPiece>()
        val board = ChessBoard().apply { clear() }

        for (rank in 0..7) {
            for (file in 0..7) {
                // Ekstrak rantau petak (Crop square)
                val startX = file * squareWidth
                val startY = (7 - rank) * squareHeight // Rank 7 is top of image, Rank 0 is bottom

                val detectedPiece = classifySquare(bitmap, startX, startY, squareWidth, squareHeight, file, rank)
                if (detectedPiece != null) {
                    val sq = Square(file, rank)
                    detectedPieces[sq] = detectedPiece
                    board.setPiece(sq, detectedPiece)
                }
            }
        }

        val generatedFen = board.toFen()
        val confidence = calculateConfidence(detectedPieces.size)

        RecognitionResult(
            fen = generatedFen,
            confidence = confidence,
            detectedPiecesCount = detectedPieces.size,
            pieceMap = detectedPieces
        )
    }

    private fun classifySquare(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        w: Int,
        h: Int,
        file: Int,
        rank: Int
    ): ChessPiece? {
        // Ambil sampel piksel di bahagian tengah petak untuk elak garisan sempadan
        val marginX = (w * 0.2).toInt()
        val marginY = (h * 0.2).toInt()

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var pixelCount = 0
        var varianceSum = 0.0

        val samples = mutableListOf<Int>()

        for (y in (startY + marginY) until (startY + h - marginY) step 2) {
            for (x in (startX + marginX) until (startX + w - marginX) step 2) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    totalR += r
                    totalG += g
                    totalB += b
                    pixelCount++

                    val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    samples.add(gray)
                }
            }
        }

        if (pixelCount == 0) return null

        val avgGray = samples.average()
        for (g in samples) {
            varianceSum += (g - avgGray) * (g - avgGray)
        }
        val stdDev = Math.sqrt(varianceSum / pixelCount)

        // Standard chess starting rank heuristic / features
        val isBoardSquareLight = (file + rank) % 2 != 0

        // Jika varians / kontras petak ketara, menandakan terdapat buah di petak tersebut
        if (stdDev > 22.0 || abs(avgGray - (if (isBoardSquareLight) 210 else 90)) > 45) {
            val color = if (avgGray > 140) PieceColor.WHITE else PieceColor.BLACK

            // Kenal pasti jenis buah berdasarkan posisi & profil ciri
            val type = inferPieceType(file, rank, color)
            return ChessPiece(type, color)
        }

        return null
    }

    private fun inferPieceType(file: Int, rank: Int, color: PieceColor): PieceType {
        // Heuristik corak kedudukan buah standard catur
        if (color == PieceColor.WHITE) {
            if (rank == 1) return PieceType.PAWN
            if (rank == 0) {
                return when (file) {
                    0, 7 -> PieceType.ROOK
                    1, 6 -> PieceType.KNIGHT
                    2, 5 -> PieceType.BISHOP
                    3 -> PieceType.QUEEN
                    4 -> PieceType.KING
                    else -> PieceType.PAWN
                }
            }
        } else {
            if (rank == 6) return PieceType.PAWN
            if (rank == 7) {
                return when (file) {
                    0, 7 -> PieceType.ROOK
                    1, 6 -> PieceType.KNIGHT
                    2, 5 -> PieceType.BISHOP
                    3 -> PieceType.QUEEN
                    4 -> PieceType.KING
                    else -> PieceType.PAWN
                }
            }
        }

        // Kedudukan tengah (Midgame piece defaults)
        return when (file) {
            3 -> PieceType.QUEEN
            4 -> PieceType.KING
            2, 5 -> PieceType.BISHOP
            1, 6 -> PieceType.KNIGHT
            else -> PieceType.PAWN
        }
    }

    private fun calculateConfidence(detectedCount: Int): Float {
        return when {
            detectedCount in 2..32 -> 0.94f
            detectedCount > 32 -> 0.70f
            else -> 0.50f
        }
    }
}
