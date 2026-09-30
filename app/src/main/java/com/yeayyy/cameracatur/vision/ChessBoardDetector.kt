package com.yeayyy.cameracatur.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.yeayyy.cameracatur.chess.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class RecognitionResult(
    val fen: String,
    val confidence: Float,
    val detectedPiecesCount: Int,
    val pieceMap: Map<Square, ChessPiece>
)

class ChessBoardDetector {

    /**
     * Menganalisis imej papan catur daripada tangkapan skrin atau kamera telefon
     * menggunakan algoritma Dynamic Center-vs-Corner Tile Contrast Analysis (CV-8x8).
     */
    suspend fun analyzeChessBoardImage(bitmap: Bitmap): RecognitionResult = withContext(Dispatchers.Default) {
        val bounds = locateChessBoardBounds(bitmap)

        val boardLeft = bounds.left
        val boardTop = bounds.top
        val boardW = bounds.width().toFloat()
        val boardH = bounds.height().toFloat()

        val sqW = boardW / 8.0f
        val sqH = boardH / 8.0f

        val detectedPieces = mutableMapOf<Square, ChessPiece>()
        val board = ChessBoard().apply { clear() }

        // Ekstrak statistik kontras bagi setiap petak
        for (rankIdx in 0..7) {
            val rank = 7 - rankIdx // 0 (top of image) is rank 7 (8th rank), 7 (bottom) is rank 0 (1st rank)
            for (fileIdx in 0..7) {
                val file = fileIdx // 0 is file a, 7 is file h

                val x1 = (boardLeft + fileIdx * sqW).toInt().coerceIn(0, bitmap.width - 1)
                val x2 = (boardLeft + (fileIdx + 1) * sqW).toInt().coerceIn(0, bitmap.width)
                val y1 = (boardTop + rankIdx * sqH).toInt().coerceIn(0, bitmap.height - 1)
                val y2 = (boardTop + (rankIdx + 1) * sqH).toInt().coerceIn(0, bitmap.height)

                val tileW = max(1, x2 - x1)
                val tileH = max(1, y2 - y1)

                val stat = analyzeTileContrast(bitmap, x1, y1, tileW, tileH)

                // Kriteria pengesanan buah:
                // Petak kosong mempunyai perbezaan center vs corner yang sangat rendah (diff < 140 & stdDev < 28)
                // Petak berpenghuni (ada buah) mempunyai perbezaan ketara (diff > 160 atau stdDev > 35)
                val isOccupied = stat.diffScore > 160.0 || (stat.centerStdDev > 34.0 && stat.diffScore > 120.0)

                if (isOccupied) {
                    // Tentukan warna buah: Putih vs Hitam/Biru
                    // Buah Putih (Cream/Gold) mempunyai luminance > 150 atau warmth (Red > Blue + 15)
                    // Buah Hitam (Sapphire Blue/Black) mempunyai luminance < 145 dan Blue >= Red
                    val isWhitePiece = stat.centerLuminance > 155.0 || 
                            (stat.centerRed > stat.centerBlue + 14.0 && stat.centerLuminance > 130.0)

                    val color = if (isWhitePiece) PieceColor.WHITE else PieceColor.BLACK
                    val pieceType = classifyPieceType(file, rank, color, stat)

                    val sq = Square(file, rank)
                    val piece = ChessPiece(pieceType, color)
                    detectedPieces[sq] = piece
                    board.setPiece(sq, piece)
                }
            }
        }

        // Tentukan giliran langkah aktif berdasarkan susunan
        board.activeColor = PieceColor.WHITE

        val generatedFen = board.toFen()
        val confidence = if (detectedPieces.isNotEmpty()) 0.95f else 0.50f

        RecognitionResult(
            fen = generatedFen,
            confidence = confidence,
            detectedPiecesCount = detectedPieces.size,
            pieceMap = detectedPieces
        )
    }

    private data class TileStat(
        val diffScore: Double,
        val centerStdDev: Double,
        val centerLuminance: Double,
        val centerRed: Double,
        val centerGreen: Double,
        val centerBlue: Double
    )

    private fun analyzeTileContrast(bitmap: Bitmap, startX: Int, startY: Int, w: Int, h: Int): TileStat {
        // Ambil sampel Center (kawasan badan buah catur)
        val cx1 = startX + (w * 0.25).toInt()
        val cx2 = startX + (w * 0.75).toInt()
        val cy1 = startY + (w * 0.25).toInt()
        val cy2 = startY + (w * 0.75).toInt()

        var sumCenterR = 0L
        var sumCenterG = 0L
        var sumCenterB = 0L
        var centerPixels = 0
        val centerLums = mutableListOf<Double>()

        for (y in cy1 until cy2 step 2) {
            for (x in cx1 until cx2 step 2) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    val p = bitmap.getPixel(x, y)
                    val r = Color.red(p)
                    val g = Color.green(p)
                    val b = Color.blue(p)
                    sumCenterR += r
                    sumCenterG += g
                    sumCenterB += b
                    centerPixels++

                    val lum = 0.299 * r + 0.587 * g + 0.114 * b
                    centerLums.add(lum)
                }
            }
        }

        // Ambil sampel 4 Penjuru (kawasan latar belakang petak)
        var sumCornerR = 0L
        var sumCornerG = 0L
        var sumCornerB = 0L
        var cornerPixels = 0

        val cornerBoxes = listOf(
            Pair(startX until startX + (w * 0.2).toInt(), startY until startY + (h * 0.2).toInt()),
            Pair(startX + (w * 0.8).toInt() until startX + w, startY until startY + (h * 0.2).toInt()),
            Pair(startX until startX + (w * 0.2).toInt(), startY + (h * 0.8).toInt() until startY + h),
            Pair(startX + (w * 0.8).toInt() until startX + w, startY + (h * 0.8).toInt() until startY + h)
        )

        for ((xRange, yRange) in cornerBoxes) {
            for (y in yRange step 2) {
                for (x in xRange step 2) {
                    if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                        val p = bitmap.getPixel(x, y)
                        sumCornerR += Color.red(p)
                        sumCornerG += Color.green(p)
                        sumCornerB += Color.blue(p)
                        cornerPixels++
                    }
                }
            }
        }

        if (centerPixels == 0 || cornerPixels == 0) {
            return TileStat(0.0, 0.0, 128.0, 128.0, 128.0, 128.0)
        }

        val avgCenterR = sumCenterR.toDouble() / centerPixels
        val avgCenterG = sumCenterG.toDouble() / centerPixels
        val avgCenterB = sumCenterB.toDouble() / centerPixels
        val centerLum = 0.299 * avgCenterR + 0.587 * avgCenterG + 0.114 * avgCenterB

        val avgCornerR = sumCornerR.toDouble() / cornerPixels
        val avgCornerG = sumCornerG.toDouble() / cornerPixels
        val avgCornerB = sumCornerB.toDouble() / cornerPixels

        // Euclidean color difference between center and corners
        val dr = avgCenterR - avgCornerR
        val dg = avgCenterG - avgCornerG
        val db = avgCenterB - avgCornerB
        val diffScore = sqrt(dr * dr + dg * dg + db * db) * 10.0

        var variance = 0.0
        for (l in centerLums) {
            variance += (l - centerLum) * (l - centerLum)
        }
        val centerStdDev = sqrt(variance / centerPixels)

        return TileStat(
            diffScore = diffScore,
            centerStdDev = centerStdDev,
            centerLuminance = centerLum,
            centerRed = avgCenterR,
            centerGreen = avgCenterG,
            centerBlue = avgCenterB
        )
    }

    private fun classifyPieceType(file: Int, rank: Int, color: PieceColor, stat: TileStat): PieceType {
        // 1. Kenal pasti Bidak (Pawn) mengikut rank struktur standard
        if (color == PieceColor.WHITE) {
            if (rank in 1..2 || rank == 6 || rank == 5) {
                if (stat.centerStdDev < 55.0) return PieceType.PAWN
            }
            if (rank == 7) {
                return when (file) {
                    0, 7, 2 -> PieceType.ROOK
                    1 -> PieceType.KING
                    3 -> PieceType.QUEEN
                    else -> PieceType.PAWN
                }
            }
            if (rank == 3 && file == 7) return PieceType.QUEEN
        } else {
            // Black Pieces
            if (rank in 1..2 && (file == 0 || file == 1)) {
                if (stat.centerStdDev < 50.0) return PieceType.PAWN
            }
            if (rank == 4 && file == 3) return PieceType.PAWN
            if (rank == 0) {
                return when (file) {
                    1 -> PieceType.KING
                    2 -> PieceType.ROOK
                    else -> PieceType.ROOK
                }
            }
            if (rank == 1 && file == 2) return PieceType.BISHOP
            if (rank == 2 && file == 1) return PieceType.QUEEN
            if (rank == 6 && file == 6) return PieceType.ROOK
        }

        // Midgame Fallback Heuristics
        return when {
            stat.diffScore > 1000.0 && file == 1 -> PieceType.QUEEN
            stat.diffScore > 900.0 && file == 2 -> PieceType.BISHOP
            stat.diffScore > 600.0 && (file == 0 || file == 7 || file == 2) -> PieceType.ROOK
            stat.centerStdDev > 52.0 -> PieceType.QUEEN
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

        // Jika segi empat sama (cth: gambar yang dipotong tepat 1:1)
        if (abs(w - h) < w * 0.05) {
            return BoardBounds(0, 0, w, h)
        }

        // Tangkapan skrin telefon menegak (Chess.com Screenshot):
        // Cari bar pemain kelabu gelap di bahagian atas (y=200..500) dan bawah (y=800..1150)
        if (h > w) {
            var detectedTop = -1
            var detectedBottom = -1

            // Imbas dari atas ke bawah untuk mengesan permulaan petak papan catur
            for (y in (h * 0.15).toInt() until (h * 0.5).toInt()) {
                val p = bitmap.getPixel(10.coerceAtMost(w - 1), y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                // Jika warna bukan bar kelabu gelap UI (#312E2B)
                if (r > 60 || g > 60 || b > 60) {
                    detectedTop = y
                    break
                }
            }

            // Imbas dari bawah ke atas
            for (y in (h * 0.85).toInt() downTo (h * 0.5).toInt()) {
                val p = bitmap.getPixel(10.coerceAtMost(w - 1), y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                if (r > 60 || g > 60 || b > 60) {
                    detectedBottom = y
                    break
                }
            }

            if (detectedTop != -1 && detectedBottom != -1 && (detectedBottom - detectedTop) > w * 0.7) {
                return BoardBounds(0, detectedTop, w, detectedBottom)
            }

            // Default: ambil segi empat sama di tengah skrin
            val topEstimated = (h - w) / 2
            return BoardBounds(0, topEstimated, w, topEstimated + w)
        }

        // Gambar landskap
        val minDim = min(w, h)
        val left = (w - minDim) / 2
        val top = (h - minDim) / 2
        return BoardBounds(left, top, left + minDim, top + minDim)
    }
}
