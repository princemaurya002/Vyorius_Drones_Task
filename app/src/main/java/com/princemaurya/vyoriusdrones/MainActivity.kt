package com.princemaurya.vyoriusdrones

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Rational
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var rtspUrlEditText: EditText
    private lateinit var playButton: Button
    private lateinit var recordButton: Button
    private lateinit var pipButton: Button
    private lateinit var snapshotButton: Button
    private lateinit var buttonContainer: LinearLayout
    private var isRecording = false
    private var recordingFile: File? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupMediaPlayer()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            videoLayout = findViewById(R.id.videoLayout)
            rtspUrlEditText = findViewById(R.id.rtspUrlEditText)
            playButton = findViewById(R.id.playButton)
            recordButton = findViewById(R.id.recordButton)
            pipButton = findViewById(R.id.pipButton)
            snapshotButton = findViewById(R.id.snapshotButton)
            buttonContainer = findViewById(R.id.buttonContainer)

            setupUI()
            checkPermissions()
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing UI: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun setupUI() {
        try {
            playButton.setOnClickListener {
                val url = rtspUrlEditText.text.toString()
                if (url.isNotEmpty()) {
                    playStream(url)
                } else {
                    Toast.makeText(this, "Please enter RTSP URL", Toast.LENGTH_SHORT).show()
                }
            }

            recordButton.setOnClickListener {
                if (isRecording) {
                    stopRecording()
                } else {
                    startRecording()
                }
            }

            snapshotButton.setOnClickListener {
                takeSnapshot()
            }

            pipButton.setOnClickListener {
                enterPipMode()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting up UI: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun checkPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                when {
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        setupMediaPlayer()
                    }
                    else -> {
                        requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }
            } else {
                setupMediaPlayer()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error checking permissions: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun setupMediaPlayer() {
        try {
            val options = ArrayList<String>()
            options.add("--aout=opensles")
            options.add("--audio-time-stretch")
            options.add("--avcodec-codec=h264")
            options.add("--file-logging")
            options.add("--logfile=vlc-log.txt")

            libVLC = LibVLC(this, options)
            mediaPlayer = MediaPlayer(libVLC)
            mediaPlayer.attachViews(videoLayout, null, false, false)
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting up media player: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun playStream(url: String) {
        try {
            var cleanUrl = url.trim()
            if (!cleanUrl.startsWith("rtsp://")) {
                cleanUrl = "rtsp://$cleanUrl"
            }
            
            if (!cleanUrl.contains("/stream") && !cleanUrl.endsWith("/")) {
                cleanUrl = "$cleanUrl/stream"
            }

            val media = Media(libVLC, cleanUrl)
            media.addOption(":network-caching=1000")
            media.addOption(":rtsp-tcp")
            media.addOption(":rtsp-frame-buffer-size=500000")
            
            mediaPlayer.media = media
            mediaPlayer.play()
            
            Toast.makeText(this, "Connecting to stream...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error playing stream: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun takeSnapshot() {
        try {
            if (!mediaPlayer.isPlaying) {
                Toast.makeText(this, "No active stream to capture", Toast.LENGTH_SHORT).show()
                return
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "snapshot_$timestamp.jpg"
            val snapshotFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)

            // Create a bitmap from the video view
            val bitmap = Bitmap.createBitmap(
                videoLayout.width,
                videoLayout.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            videoLayout.draw(canvas)

            // Save the bitmap
            FileOutputStream(snapshotFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            Toast.makeText(this, "Snapshot saved: ${snapshotFile.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error taking snapshot: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun startRecording() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "recording_$timestamp.mp4"
            recordingFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)
            
            mediaPlayer.record(recordingFile?.absolutePath)
            isRecording = true
            recordButton.text = "Stop Recording"
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting recording: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            mediaPlayer.record(null)
            isRecording = false
            recordButton.text = "Record"
            Toast.makeText(this, "Recording saved: ${recordingFile?.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping recording: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        updateUIForPipMode(isInPictureInPictureMode)
    }

    private fun updateUIForPipMode(isInPipMode: Boolean) {
        try {
            if (isInPipMode) {
                rtspUrlEditText.visibility = View.GONE
                playButton.visibility = View.GONE
                buttonContainer.visibility = View.GONE
            } else {
                rtspUrlEditText.visibility = View.VISIBLE
                playButton.visibility = View.VISIBLE
                buttonContainer.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun enterPipMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error entering PIP mode: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer.release()
            libVLC.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}