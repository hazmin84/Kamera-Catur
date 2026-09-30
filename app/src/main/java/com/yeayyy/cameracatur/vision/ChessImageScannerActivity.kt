package com.yeayyy.cameracatur.vision

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yeayyy.cameracatur.databinding.ActivityChessScannerBinding
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ChessImageScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChessScannerBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val boardDetector = ChessBoardDetector()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Kebenaran kamera diperlukan untuk imbasan papan catur.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChessScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnCapture.setOnClickListener {
            takePhotoAndAnalyze()
        }

        binding.btnClose.setOnClickListener {
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Gagal memulakan kamera: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhotoAndAnalyze() {
        val imageCapture = imageCapture ?: return

        binding.progressBar.visibility = View.VISIBLE
        binding.tvScanningStatus.visibility = View.VISIBLE
        binding.btnCapture.isEnabled = false

        val photoFile = File(outputDirectory, "chess_snap_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvScanningStatus.visibility = View.GONE
                    binding.btnCapture.isEnabled = true
                    Toast.makeText(this@ChessImageScannerActivity, "Ralat tangkap foto: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    lifecycleScope.launch {
                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                        val recognition = boardDetector.analyzeChessBoardImage(bitmap)

                        val resultIntent = Intent().apply {
                            putExtra("EXTRA_DETECTED_FEN", recognition.fen)
                            putExtra("EXTRA_PIECES_COUNT", recognition.detectedPiecesCount)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                }
            }
        )
    }

    private val outputDirectory: File
        get() {
            val mediaDir = externalMediaDirs.firstOrNull()?.let {
                File(it, "KameraCatur").apply { mkdirs() }
            }
            return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
        }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
