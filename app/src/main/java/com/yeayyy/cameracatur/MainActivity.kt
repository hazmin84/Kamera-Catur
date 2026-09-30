package com.yeayyy.cameracatur

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yeayyy.cameracatur.chess.ChessBoard
import com.yeayyy.cameracatur.databinding.ActivityMainBinding
import com.yeayyy.cameracatur.engine.StockfishEngineService
import com.yeayyy.cameracatur.vision.ChessBoardDetector
import com.yeayyy.cameracatur.vision.ChessImageScannerActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val engineService = StockfishEngineService()
    private val boardDetector = ChessBoardDetector()

    // Activity launcher for Gallery Pick
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processImageUri(it) }
    }

    // Activity launcher for Live Camera Capture
    private val scanCameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val fen = result.data?.getStringExtra("EXTRA_DETECTED_FEN")
            if (!fen.isNullOrEmpty()) {
                applyDetectedFen(fen)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        triggerAiEvaluation()
    }

    private fun setupListeners() {
        // 1. Live Camera Snap
        binding.btnScanCamera.setOnClickListener {
            val intent = Intent(this, ChessImageScannerActivity::class.java)
            scanCameraLauncher.launch(intent)
        }

        // 2. Pick from Gallery
        binding.btnPickGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // 3. Flip Board (Toggle Perspective)
        binding.btnFlipBoard.setOnClickListener {
            binding.chessBoardView.flipBoard()
        }

        // 4. Reset to Initial Standard Position
        binding.btnResetBoard.setOnClickListener {
            binding.chessBoardView.loadFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
            triggerAiEvaluation()
        }

        // 5. Board interactive move listener -> auto re-eval
        binding.chessBoardView.onMoveListener = {
            triggerAiEvaluation()
        }
    }

    private fun processImageUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    val result = boardDetector.analyzeChessBoardImage(bitmap)
                    applyDetectedFen(result.fen)
                    Toast.makeText(
                        this@MainActivity,
                        "Pengesanan berjaya! ${result.detectedPiecesCount} buah dikesan.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ralat membaca imej: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyDetectedFen(fen: String) {
        binding.chessBoardView.loadFen(fen)
        triggerAiEvaluation()
    }

    private fun triggerAiEvaluation() {
        val currentFen = binding.chessBoardView.chessBoard.toFen()
        binding.tvWhiteEval.text = "Mengira..."
        binding.tvBlackEval.text = "Mengira..."

        lifecycleScope.launch {
            val (evalWhite, evalBlack) = engineService.evaluateDualSide(currentFen, depth = 4)

            binding.chessBoardView.hintMoveWhite = evalWhite.bestMove
            binding.chessBoardView.hintMoveBlack = evalBlack.bestMove
            binding.chessBoardView.invalidate()

            binding.tvWhiteEval.text = evalWhite.bestMove?.uci?.uppercase() ?: "Tiada"
            binding.tvBlackEval.text = evalBlack.bestMove?.uci?.uppercase() ?: "Tiada"
        }
    }
}
