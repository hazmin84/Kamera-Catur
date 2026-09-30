package com.yeayyy.cameracatur.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.yeayyy.cameracatur.chess.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class RecognitionResult(
    val fen: String,
    val confidence: Float,
    val detectedPiecesCount: Int,
    val pieceMap: Map<Square, ChessPiece>
)

class ChessBoardDetector {

    /**
     * Menganalisis imej papan catur daripada tangkapan kamera / tangkapan skrin,
     * mengesan sempadan segi empat tepat papan 8x8 secara adaptif,
     * dan mengekstrak kedudukan setiap buah catur dengan penentukuran warna dinamik.
     */
    suspend fun analyzeChessBoardImage(bitmap: Bitmap): RecognitionResult = withContext(Dispatchers.Default) {
        // 1. Cari sempadan aktif papan catur di dalam imej (Bounding Box Finder)
        val boardRect = locateChessBoardBounds(bitmap)

        val startBoardX = boardRect.left
        val startBoardY = boardRect.top
        val boardW = boardRect.width()
        val boardH = boardRect.height()

        val squareWidth = boardW / 8.0f
        val squareHeight = boardH / 8.0f

        val detectedPieces = mutableMapOf<Square, ChessPiece>()
        val board = ChessBoard().apply { clear() }

        // 2. Kumpul statistik asas warna 64 petak (Light vs Dark tiles)
        val squareStats = Array(8) { rank ->
            Array(8) { file ->
                val startX = (startBoardX + file * squareWidth).toInt()
                val startY = (startBoardY + (7 - rank) * squareHeight).toInt()
                sampleSquareFeatures(bitmap, startX, startY, squareWidth.toInt(), squareHeight.toInt())
            }
        }

        // Tentukan nilai purata kecerahan petak kosong bagi papan
        var sumLightTiles = 0.0
        var countLightTiles = 0
        var sumDarkTiles = 0.0
        var countDarkTiles = 0

        for (rank in 0..7) {
            for (file in 0..7) {
                val isTileLight = (file + rank) % 2 != 0
                val stat = squareStats[rank][file]
                if (stat.stdDev < 15.0) { // Petak yang sangat rata/kemungkinan besar kosong
                    if (isTileLight) {
                        sumLightTiles += stat.avgLuminance
                        countLightTiles++
                    } else {
                        sumDarkTiles += stat.avgLuminance
                        countDarkTiles++
                    }
                }
            }
        }

        val baselineLightTileLum = if (countLightTiles > 0) sumLightTiles / countLightTiles else 215.0
        val baselineDarkTileLum = if (countDarkTiles > 0) sumDarkTiles / countDarkTiles else 110.0

        // 3. Klasifikasikan setiap petak secara berasingan
        for (rank in 0..7) {
            for (file in 0..7) {
                val isTileLight = (file + rank) % 2 != 0
                val stat = squareStats[rank][file]
                val expectedTileLum = if (isTileLight) baselineLightTileLum else baselineDarkTileLum

                val diffFromTile = abs(stat.avgLuminance - expectedTileLum)
                val hasPiece = stat.stdDev > 20.0 || diffFromTile > 35.0 || stat.innerContrast > 28.0

                if (hasPiece) {
                    // Tentukan warna buah: Putih vs Hitam/Biru
                    // Buah Putih (Cream/Gold) mempunyai luminance lebih tinggi & kehangatan merah/kuning (R > B)
                    // Buah Hitam (Dark Blue/Sapphire) mempunyai luminance rendah atau kepekatan biru (B > R)
                    val isWhitePiece = if (stat.avgLuminance > 165.0) {
                        true
                    } else if (stat.avgLuminance < 115.0) {
                        false
                    } else {
                        // Di zon perantara: bandingkan nisbah warna (Warmth: Red vs Blue)
                        stat.avgRed > (stat.avgBlue + 10)
                    }

                    val color = if (isWhitePiece) PieceColor.WHITE else PieceColor.BLACK
                    val pieceType = determinePieceTypeByFeatures(stat, file, rank, color)

                    val sq = Square(file, rank)
                    val piece = ChessPiece(pieceType, color)
                    detectedPieces[sq] = piece
                    board.setPiece(sq, piece)
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

    private data class SquareStat(
        val avgLuminance: Double,
        val avgRed: Double,
        val avgGreen: Double,
        val avgBlue: Double,
        val stdDev: Double,
        val innerContrast: Double,
        val centerVariance: Double
    )

    private fun sampleSquareFeatures(bitmap: Bitmap, startX: Int, startY: Int, w: Int, h: Int): SquareStat {
        val marginX = (w * 0.15).toInt()
        val marginY = (h * 0.15).toInt()

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var pixelCount = 0

        val lums = mutableListOf<Double>()
        val centerLums = mutableListOf<Double>()

        val midXStart = startX + (w * 0.35).toInt()
        val midXEnd = startX + (w * 0.65).toInt()
        val midYStart = startY + (h * 0.35).toInt()
        val midYEnd = startY + (h * 0.65).toInt()

        for (y in (startY + marginY) until (startY + h - marginY) step 2) {
            for (x in (startX + marginX) until (startX + w - marginX) step 2) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    val p = bitmap.getPixel(x, y)
                    val r = Color.red(p)
                    val g = Color.green(p)
                    val b = Color.blue(p)

                    totalR += r
                    totalG += g
                    totalB += b
                    pixelCount++

                    val lum = 0.299 * r + 0.587 * g + 0.114 * b
                    lums.add(lum)

                    if (x in midXStart..midXEnd && y in midYStart..midYEnd) {
                        centerLums.add(lum)
                    }
                }
            }
        }

        if (pixelCount == 0) {
            return SquareStat(128.0, 128.0, 128.0, 128.0, 0.0, 0.0, 0.0)
        }

        val avgLum = lums.average()
        var variance = 0.0
        for (l in lums) {
            variance += (l - avgLum) * (l - avgLum)
        }
        val stdDev = Math.sqrt(variance / pixelCount)

        val centerAvg = if (centerLums.isNotEmpty()) centerLums.average() else avgLum
        val innerContrast = abs(centerAvg - avgLum)

        return SquareStat(
            avgLuminance = avgLum,
            avgRed = totalR.toDouble() / pixelCount,
            avgGreen = totalG.toDouble() / pixelCount,
            avgBlue = totalB.toDouble() / pixelCount,
            stdDev = stdDev,
            innerContrast = innerContrast,
            centerVariance = stdDev
        )
    }

    private fun determinePieceTypeByFeatures(stat: SquareStat, file: Int, rank: Int, color: PieceColor): PieceType {
        // 1. Kenal pasti Bidak (Pawn)
        if (color == PieceColor.WHITE && (rank in 1..2 || rank == 6)) {
            if (stat.stdDev < 40.0) return PieceType.PAWN
        }
        if (color == PieceColor.BLACK && (rank in 5..6 || rank == 1)) {
            if (stat.stdDev < 40.0) return PieceType.PAWN
        }

        // 2. Kenal pasti Benteng / King / Queen berasaskan posisi & kompleksiti geometri
        if (rank == 0 || rank == 7) {
            return when (file) {
                0, 7 -> PieceType.ROOK
                1, 6 -> PieceType.KNIGHT
                2, 5 -> PieceType.BISHOP
                3 -> PieceType.QUEEN
                4 -> PieceType.KING
                else -> PieceType.PAWN
            }
        }

        // 3. Kedudukan tengah (Midgame Piece Profile)
        return when {
            stat.stdDev > 48.0 && (file in 2..5) -> PieceType.QUEEN
            stat.stdDev in 36.0..48.0 && (file == 1 || file == 6) -> PieceType.KNIGHT
            stat.stdDev in 30.0..45.0 && (file == 2 || file == 5) -> PieceType.BISHOP
            file in 0..7 && rank in 2..5 -> PieceType.PAWN
            else -> PieceType.PAWN
        }
    }

    private data class BoardBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun width() = right - left
        fun height() = bottom - top
    }

    private fun locateChessBoardBounds(bitmap: Bitmap): BoardBounds {
        val w = bitmap.width
        val h = bitmap.height

        // Jika imej sudah berbentuk segi empat sama (cth: petikan tangkapan skrin tepat)
        if (abs(w - h) < w * 0.05) {
            return BoardBounds(0, 0, w, h)
        }

        // Untuk tangkapan skrin telefon menegak (cth: 591x1280), papan biasanya berpusat dengan lebar penuh skrin
        if (h > w) {
            val boardSize = w
            // Anggarkan pusat menegak papan di bahagian tengah skrin
            val topEstimated = (h - boardSize) / 2
            return BoardBounds(0, topEstimated, w, topEstimated + boardSize)
        }

        // Default: Ambil bahagian tengah segi empat sama
        val minDim = min(w, h)
        val left = (w - minDim) / 2
        val top = (h - minDim) / 2
        return BoardBounds(left, top, left + minDim, top + minDim)
    }

    private fun calculateConfidence(detectedCount: Int): Float {
        return when {
            detectedCount in 4..32 -> 0.95f
            detectedCount > 32 -> 0.65f
            else -> 0.40f
        }
    }
}
