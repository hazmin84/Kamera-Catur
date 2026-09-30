package com.yeayyy.cameracatur.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.yeayyy.cameracatur.R
import com.yeayyy.cameracatur.chess.*
import kotlin.math.min

class ChessBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val chessBoard = ChessBoard()
    var isFlipped = false // false: White at bottom, true: Black at bottom
    var onMoveListener: (() -> Unit)? = null
    var onSquareLongClickListener: ((Square) -> Unit)? = null

    // Engine hint moves
    var hintMoveWhite: ChessMove? = null
    var hintMoveBlack: ChessMove? = null

    private var selectedSquare: Square? = null
    private var legalMovesForSelected = listOf<ChessMove>()

    // Paints
    private val lightSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chess_board_light)
    }
    private val darkSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chess_board_dark)
    }
    private val selectedSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chess_highlight_from)
    }
    private val legalMoveDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#77000000")
        style = Paint.Style.FILL
    }
    private val hintWhiteArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chess_best_move_glow)
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val hintBlackArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent_gold)
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var squareSize = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            val sq = getSquareFromTouch(e.x, e.y)
            if (sq != null) {
                onSquareLongClickListener?.invoke(sq)
            }
        }
    })

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val boardSize = min(w, h).toFloat()
        squareSize = boardSize / 8f
        boardLeft = (w - boardSize) / 2f
        boardTop = (h - boardSize) / 2f
        textPaint.textSize = squareSize * 0.75f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Lukis 64 Petak Papan Catur
        for (rank in 0..7) {
            for (file in 0..7) {
                val displayFile = if (isFlipped) 7 - file else file
                val displayRank = if (isFlipped) rank else 7 - rank

                val left = boardLeft + displayFile * squareSize
                val top = boardTop + displayRank * squareSize
                val right = left + squareSize
                val bottom = top + squareSize

                val isLightSquare = (file + rank) % 2 != 0
                val sq = Square(file, rank)

                // Highlight selected square
                val paint = when {
                    selectedSquare == sq -> selectedSquarePaint
                    isLightSquare -> lightSquarePaint
                    else -> darkSquarePaint
                }

                canvas.drawRect(left, top, right, bottom, paint)

                // Label koordinat (Files a-h pada rank bawah, Ranks 1-8 pada file kiri)
                if (displayRank == 7) {
                    labelPaint.color = if (isLightSquare) Color.parseColor("#739552") else Color.parseColor("#EBECD0")
                    canvas.drawText(sq.fileName, left + 6f, bottom - 6f, labelPaint)
                }
                if (displayFile == 0) {
                    labelPaint.color = if (isLightSquare) Color.parseColor("#739552") else Color.parseColor("#EBECD0")
                    canvas.drawText(sq.rankName, left + 6f, top + 24f, labelPaint)
                }

                // 2. Lukis Buah Catur (Unicode Chess Glyphs)
                val piece = chessBoard.getPiece(sq)
                if (piece != null) {
                    textPaint.color = if (piece.color == PieceColor.WHITE) Color.WHITE else Color.BLACK
                    val glyph = if (piece.color == PieceColor.WHITE) piece.type.unicodeWhite else piece.type.unicodeBlack

                    // Tambah sedikit bayang halus untuk buah putih/hitam
                    val fontMetrics = textPaint.fontMetrics
                    val baseline = top + (squareSize - fontMetrics.bottom + fontMetrics.top) / 2 - fontMetrics.top
                    canvas.drawText(glyph, left + squareSize / 2, baseline, textPaint)
                }
            }
        }

        // 3. Lukis Penunjuk Langkah Sah (Move Targets)
        for (move in legalMovesForSelected) {
            val displayFile = if (isFlipped) 7 - move.to.file else move.to.file
            val displayRank = if (isFlipped) move.to.rank else 7 - move.to.rank

            val cx = boardLeft + (displayFile + 0.5f) * squareSize
            val cy = boardTop + (displayRank + 0.5f) * squareSize

            val isCapture = chessBoard.getPiece(move.to) != null
            if (isCapture) {
                legalMoveDotPaint.style = Paint.Style.STROKE
                legalMoveDotPaint.strokeWidth = 6f
                canvas.drawCircle(cx, cy, squareSize * 0.4f, legalMoveDotPaint)
            } else {
                legalMoveDotPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, squareSize * 0.16f, legalMoveDotPaint)
            }
        }

        // 4. Lukis Hint Arrows Stockfish AI
        hintMoveWhite?.let { drawHintArrow(canvas, it, hintWhiteArrowPaint) }
        hintMoveBlack?.let { drawHintArrow(canvas, it, hintBlackArrowPaint) }
    }

    private fun drawHintArrow(canvas: Canvas, move: ChessMove, paint: Paint) {
        val fromFile = if (isFlipped) 7 - move.from.file else move.from.file
        val fromRank = if (isFlipped) move.from.rank else 7 - move.from.rank
        val toFile = if (isFlipped) 7 - move.to.file else move.to.file
        val toRank = if (isFlipped) move.to.rank else 7 - move.to.rank

        val startX = boardLeft + (fromFile + 0.5f) * squareSize
        val startY = boardTop + (fromRank + 0.5f) * squareSize
        val endX = boardLeft + (toFile + 0.5f) * squareSize
        val endY = boardTop + (toRank + 0.5f) * squareSize

        canvas.drawLine(startX, startY, endX, endY, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(endX, endY, 14f, paint)
        paint.style = Paint.Style.STROKE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP) {
            val clickedSq = getSquareFromTouch(event.x, event.y)
            if (clickedSq != null) {
                handleSquareClick(clickedSq)
            }
        }
        return true
    }

    private fun handleSquareClick(square: Square) {
        val selected = selectedSquare
        if (selected == null) {
            // Pilih buah jika ada di petak tersebut
            val piece = chessBoard.getPiece(square)
            if (piece != null) {
                selectedSquare = square
                legalMovesForSelected = chessBoard.generateLegalMovesForSquare(square)
                invalidate()
            }
        } else {
            // Cuba buat langkah
            val targetMove = legalMovesForSelected.firstOrNull { it.to == square }
            if (targetMove != null) {
                chessBoard.makeMove(targetMove)
                selectedSquare = null
                legalMovesForSelected = emptyList()
                invalidate()
                onMoveListener?.invoke()
            } else {
                // Pilih buah lain jika kepunyaan pemain
                val newPiece = chessBoard.getPiece(square)
                if (newPiece != null) {
                    selectedSquare = square
                    legalMovesForSelected = chessBoard.generateLegalMovesForSquare(square)
                } else {
                    selectedSquare = null
                    legalMovesForSelected = emptyList()
                }
                invalidate()
            }
        }
    }

    private fun getSquareFromTouch(x: Float, y: Float): Square? {
        if (x < boardLeft || x > boardLeft + 8 * squareSize || y < boardTop || y > boardTop + 8 * squareSize) {
            return null
        }
        val fileIdx = ((x - boardLeft) / squareSize).toInt().coerceIn(0, 7)
        val rankIdx = ((y - boardTop) / squareSize).toInt().coerceIn(0, 7)

        val file = if (isFlipped) 7 - fileIdx else fileIdx
        val rank = if (isFlipped) rankIdx else 7 - rankIdx

        return Square(file, rank)
    }

    fun loadFen(fen: String) {
        chessBoard.loadFen(fen)
        selectedSquare = null
        legalMovesForSelected = emptyList()
        invalidate()
    }

    fun setPieceAtSquare(square: Square, piece: ChessPiece?) {
        chessBoard.setPiece(square, piece)
        selectedSquare = null
        legalMovesForSelected = emptyList()
        invalidate()
        onMoveListener?.invoke()
    }

    fun flipBoard() {
        isFlipped = !isFlipped
        invalidate()
    }
}
