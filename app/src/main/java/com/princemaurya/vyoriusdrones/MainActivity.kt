package com.princemaurya.vyoriusdrones

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

        videoLayout = findViewById(R.id.videoLayout)
        rtspUrlEditText = findViewById(R.id.rtspUrlEditText)
        playButton = findViewById(R.id.playButton)
        recordButton = findViewById(R.id.recordButton)
        pipButton = findViewById(R.id.pipButton)
        buttonContainer = findViewById(R.id.buttonContainer)

        setupUI()
        checkPermissions()
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
        if (isInPipMode) {
            rtspUrlEditText.visibility = View.GONE
            playButton.visibility = View.GONE
            buttonContainer.visibility = View.GONE
        } else {
            rtspUrlEditText.visibility = View.VISIBLE
            playButton.visibility = View.VISIBLE
            buttonContainer.visibility = View.VISIBLE
        }
    }

    private fun setupUI() {
        playButton.setOnClickListener {
            val rtspUrl = rtspUrlEditText.text.toString()
            if (rtspUrl.isNotBlank()) {
                mediaPlayer.stop() // Stop any previous media
                val media = Media(libVLC, Uri.parse(rtspUrl))
                media.setHWDecoderEnabled(true, false)
                media.addOption(":network-caching=150") // Optional: lower latency
                mediaPlayer.media = media
                media.release()
                mediaPlayer.play()
            } else {
                Toast.makeText(this, "Please enter a valid RTSP URL", Toast.LENGTH_SHORT).show()
            }
        }

        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        pipButton.setOnClickListener {
            enterPipMode()
        }
    }

    private fun checkPermissions() {
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
    }

    private fun setupMediaPlayer() {
        val options = ArrayList<String>()
        options.add("--aout=opensles")
        options.add("--audio-time-stretch")
        options.add("--avcodec-codec=h264")
        options.add("--file-logging")
        options.add("--logfile=vlc-log.txt")

        libVLC = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer.attachViews(videoLayout, null, false, false)
    }

    private fun playStream(url: String) {
        val media = Media(libVLC, url)
        mediaPlayer.media = media
        mediaPlayer.play()
    }

    private fun startRecording() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "recording_$timestamp.mp4"
        recordingFile = File(getExternalFilesDir(null), fileName)
        
        mediaPlayer.record(recordingFile?.absolutePath)
        isRecording = true
        recordButton.text = "Stop Recording"
    }

    private fun stopRecording() {
        mediaPlayer.record(null)
        isRecording = false
        recordButton.text = "Record"
        Toast.makeText(this, "Recording saved: ${recordingFile?.name}", Toast.LENGTH_SHORT).show()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
        libVLC.release()
    }
}