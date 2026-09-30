package com.yeayyy.cameracatur.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.yeayyy.cameracatur.R
import com.yeayyy.cameracatur.chess.*

class ChessBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val chessBoard = ChessBoard()
    var isFlipped = false
    var selectedSquare: Square? = null
    var lastMove: ChessMove? = null
    var hintMoveWhite: ChessMove? = null
    var hintMoveBlack: ChessMove? = null

    var onMoveListener: ((ChessMove) -> Unit)? = null

    private val lightSquarePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.chess_board_light)
        style = Paint.Style.FILL
    }

    private val darkSquarePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.chess_board_dark)
        style = Paint.Style.FILL
    }

    private val highlightPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.chess_highlight_to)
        style = Paint.Style.FILL
        alpha = 180
    }

    private val selectedPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.chess_highlight_from)
        style = Paint.Style.FILL
        alpha = 200
    }

    private val hintArrowPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.chess_hint_arrow)
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val pieceTextPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    private val coordPaint = Paint().apply {
        textSize = 28f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (w < h && w > 0) w else if (h > 0) h else w
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val squareSize = width / 8f
        pieceTextPaint.textSize = squareSize * 0.75f

        // 1. Draw 64 Squares (Chess.com Style)
        for (r in 0..7) {
            for (f in 0..7) {
                val drawCol = if (isFlipped) 7 - f else f
                val drawRow = if (isFlipped) r else 7 - r

                val isLight = (f + r) % 2 != 0
                val paint = if (isLight) lightSquarePaint else darkSquarePaint

                val left = drawCol * squareSize
                val top = drawRow * squareSize
                canvas.drawRect(left, top, left + squareSize, top + squareSize, paint)

                // Selected square highlight
                if (selectedSquare?.file == f && selectedSquare?.rank == r) {
                    canvas.drawRect(left, top, left + squareSize, top + squareSize, selectedPaint)
                }

                // Last move highlight
                if (lastMove != null && ((lastMove!!.from.file == f && lastMove!!.from.rank == r) ||
                            (lastMove!!.to.file == f && lastMove!!.to.rank == r))) {
                    canvas.drawRect(left, top, left + squareSize, top + squareSize, highlightPaint)
                }
            }
        }

        // 2. Draw Coordinates (a-h, 1-8)
        for (i in 0..7) {
            val fileChar = if (isFlipped) ('h' - i) else ('a' + i)
            val rankChar = if (isFlipped) (i + 1) else (8 - i)

            // File letters at bottom
            coordPaint.color = if (i % 2 == 0) ContextCompat.getColor(context, R.color.chess_board_dark) else ContextCompat.getColor(context, R.color.chess_board_light)
            canvas.drawText("$fileChar", (i + 0.85f) * squareSize, height - 8f, coordPaint)

            // Rank numbers at left
            canvas.drawText("$rankChar", 8f, (i + 0.35f) * squareSize, coordPaint)
        }

        // 3. Draw Chess Pieces
        for (r in 0..7) {
            for (f in 0..7) {
                val piece = chessBoard.getPiece(f, r) ?: continue
                val drawCol = if (isFlipped) 7 - f else f
                val drawRow = if (isFlipped) r else 7 - r

                val cx = (drawCol + 0.5f) * squareSize
                val cy = (drawRow + 0.72f) * squareSize

                pieceTextPaint.color = if (piece.color == PieceColor.WHITE) Color.WHITE else Color.BLACK
                pieceTextPaint.setShadowLayer(
                    8f, 0f, 4f,
                    if (piece.color == PieceColor.WHITE) Color.parseColor("#80000000") else Color.parseColor("#80FFFFFF")
                )
                canvas.drawText(piece.displaySymbol, cx, cy, pieceTextPaint)
                pieceTextPaint.clearShadowLayer()
            }
        }

        // 4. Draw Hint Arrows (AI Stockfish Suggestions)
        drawHintArrow(canvas, hintMoveWhite, squareSize, Color.parseColor("#4CAF50"))
        drawHintArrow(canvas, hintMoveBlack, squareSize, Color.parseColor("#FF9800"))
    }

    private fun drawHintArrow(canvas: Canvas, move: ChessMove?, squareSize: Float, arrowColor: Int) {
        if (move == null) return
        hintArrowPaint.color = arrowColor

        val fromCol = if (isFlipped) 7 - move.from.file else move.from.file
        val fromRow = if (isFlipped) move.from.rank else 7 - move.from.rank
        val toCol = if (isFlipped) 7 - move.to.file else move.to.file
        val toRow = if (isFlipped) move.to.rank else 7 - move.to.rank

        val x1 = (fromCol + 0.5f) * squareSize
        val y1 = (fromRow + 0.5f) * squareSize
        val x2 = (toCol + 0.5f) * squareSize
        val y2 = (toRow + 0.5f) * squareSize

        canvas.drawLine(x1, y1, x2, y2, hintArrowPaint)
        canvas.drawCircle(x2, y2, squareSize * 0.18f, Paint().apply { color = arrowColor; style = Paint.Style.FILL })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val squareSize = width / 8f
            val col = (event.x / squareSize).toInt().coerceIn(0, 7)
            val row = (event.y / squareSize).toInt().coerceIn(0, 7)

            val file = if (isFlipped) 7 - col else col
            val rank = if (isFlipped) row else 7 - row
            val clickedSq = Square(file, rank)

            if (selectedSquare == null) {
                val piece = chessBoard.getPiece(clickedSq)
                if (piece != null && piece.color == chessBoard.activeColor) {
                    selectedSquare = clickedSq
                    invalidate()
                }
            } else {
                val fromSq = selectedSquare!!
                if (fromSq == clickedSq) {
                    selectedSquare = null
                    invalidate()
                } else {
                    val move = ChessMove(fromSq, clickedSq)
                    val success = chessBoard.makeMove(move)
                    if (success) {
                        lastMove = move
                        selectedSquare = null
                        invalidate()
                        onMoveListener?.invoke(move)
                    } else {
                        // Reselect another piece of current turn
                        val newPiece = chessBoard.getPiece(clickedSq)
                        if (newPiece != null && newPiece.color == chessBoard.activeColor) {
                            selectedSquare = clickedSq
                        } else {
                            selectedSquare = null
                        }
                        invalidate()
                    }
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    fun loadFen(fen: String) {
        chessBoard.loadFen(fen)
        selectedSquare = null
        lastMove = null
        invalidate()
    }

    fun flipBoard() {
        isFlipped = !isFlipped
        invalidate()
    }
}
