package com.yeayyy.cameracatur

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yeayyy.cameracatur.ui.ChessBoardView
import com.yeayyy.cameracatur.engine.StockfishEngineService
import com.yeayyy.cameracatur.vision.ChessBoardDetector
import com.yeayyy.cameracatur.vision.ChessImageScannerActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var chessBoardView: ChessBoardView
    private lateinit var tvWhiteEval: TextView
    private lateinit var tvBlackEval: TextView
    private lateinit var btnScanCamera: Button
    private lateinit var btnPickGallery: Button
    private lateinit var btnFlipBoard: Button
    private lateinit var btnResetBoard: Button

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
        setContentView(R.layout.activity_main)

        chessBoardView = findViewById(R.id.chessBoardView)
        tvWhiteEval = findViewById(R.id.tvWhiteEval)
        tvBlackEval = findViewById(R.id.tvBlackEval)
        btnScanCamera = findViewById(R.id.btnScanCamera)
        btnPickGallery = findViewById(R.id.btnPickGallery)
        btnFlipBoard = findViewById(R.id.btnFlipBoard)
        btnResetBoard = findViewById(R.id.btnResetBoard)

        setupListeners()
        triggerAiEvaluation()
    }

    private fun setupListeners() {
        // 1. Live Camera Snap
        btnScanCamera.setOnClickListener {
            val intent = Intent(this, ChessImageScannerActivity::class.java)
            scanCameraLauncher.launch(intent)
        }

        // 2. Pick from Gallery
        btnPickGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // 3. Flip Board (Toggle Perspective)
        btnFlipBoard.setOnClickListener {
            chessBoardView.flipBoard()
        }

        // 4. Reset to Initial Standard Position
        btnResetBoard.setOnClickListener {
            chessBoardView.loadFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
            triggerAiEvaluation()
        }

        // 5. Board interactive move listener -> auto re-eval
        chessBoardView.onMoveListener = {
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
        chessBoardView.loadFen(fen)
        triggerAiEvaluation()
    }

    private fun triggerAiEvaluation() {
        val currentFen = chessBoardView.chessBoard.toFen()
        tvWhiteEval.text = "Mengira..."
        tvBlackEval.text = "Mengira..."

        lifecycleScope.launch {
            val (evalWhite, evalBlack) = engineService.evaluateDualSide(currentFen, depth = 4)

            chessBoardView.hintMoveWhite = evalWhite.bestMove
            chessBoardView.hintMoveBlack = evalBlack.bestMove
            chessBoardView.invalidate()

            tvWhiteEval.text = evalWhite.bestMove?.uci?.uppercase() ?: "Tiada"
            tvBlackEval.text = evalBlack.bestMove?.uci?.uppercase() ?: "Tiada"
        }
    }
}
