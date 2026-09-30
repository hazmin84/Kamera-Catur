package com.yeayyy.cameracatur.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yeayyy.cameracatur.databinding.ActivityAnalysisDashboardBinding
import com.yeayyy.cameracatur.engine.StockfishEngineService
import kotlinx.coroutines.launch

class AnalysisDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisDashboardBinding
    private val engineService = StockfishEngineService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fen = intent.getStringExtra("EXTRA_FEN") ?: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        binding.chessBoardView.loadFen(fen)

        binding.btnBack.setOnClickListener {
            finish()
        }

        analyzeDetailedPosition(fen)
    }

    private fun analyzeDetailedPosition(fen: String) {
        lifecycleScope.launch {
            val (evalWhite, evalBlack) = engineService.evaluateDualSide(fen, depth = 5)

            // Update UI with Stockfish insights
            binding.tvWhiteMove.text = "Langkah Terbaik Putih: ${evalWhite.bestMove?.uci?.uppercase() ?: "N/A"}"
            binding.tvWhiteCommentary.text = evalWhite.commentary

            binding.tvBlackMove.text = "Langkah Terbaik Hitam: ${evalBlack.bestMove?.uci?.uppercase() ?: "N/A"}"
            binding.tvBlackCommentary.text = evalBlack.commentary

            binding.chessBoardView.hintMoveWhite = evalWhite.bestMove
            binding.chessBoardView.hintMoveBlack = evalBlack.bestMove
            binding.chessBoardView.invalidate()
        }
    }
}
