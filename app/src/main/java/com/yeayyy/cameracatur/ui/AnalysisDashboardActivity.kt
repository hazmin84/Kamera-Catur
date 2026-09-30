package com.yeayyy.cameracatur.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yeayyy.cameracatur.R
import com.yeayyy.cameracatur.engine.StockfishEngineService
import kotlinx.coroutines.launch

class AnalysisDashboardActivity : AppCompatActivity() {

    private lateinit var chessBoardView: ChessBoardView
    private lateinit var btnBack: ImageButton
    private lateinit var tvWhiteMove: TextView
    private lateinit var tvWhiteCommentary: TextView
    private lateinit var tvBlackMove: TextView
    private lateinit var tvBlackCommentary: TextView

    private val engineService = StockfishEngineService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_dashboard)

        chessBoardView = findViewById(R.id.chessBoardView)
        btnBack = findViewById(R.id.btnBack)
        tvWhiteMove = findViewById(R.id.tvWhiteMove)
        tvWhiteCommentary = findViewById(R.id.tvWhiteCommentary)
        tvBlackMove = findViewById(R.id.tvBlackMove)
        tvBlackCommentary = findViewById(R.id.tvBlackCommentary)

        val fen = intent.getStringExtra("EXTRA_FEN") ?: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        chessBoardView.loadFen(fen)

        btnBack.setOnClickListener {
            finish()
        }

        analyzeDetailedPosition(fen)
    }

    private fun analyzeDetailedPosition(fen: String) {
        lifecycleScope.launch {
            val (evalWhite, evalBlack) = engineService.evaluateDualSide(fen, depth = 5)

            // Update UI with Stockfish insights
            tvWhiteMove.text = "Langkah Terbaik Putih: ${evalWhite.bestMove?.uci?.uppercase() ?: "N/A"}"
            tvWhiteCommentary.text = evalWhite.commentary

            tvBlackMove.text = "Langkah Terbaik Hitam: ${evalBlack.bestMove?.uci?.uppercase() ?: "N/A"}"
            tvBlackCommentary.text = evalBlack.commentary

            chessBoardView.hintMoveWhite = evalWhite.bestMove
            chessBoardView.hintMoveBlack = evalBlack.bestMove
            chessBoardView.invalidate()
        }
    }
}
