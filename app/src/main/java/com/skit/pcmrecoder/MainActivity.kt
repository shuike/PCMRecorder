package com.skit.pcmrecorder

import android.content.Intent
import android.media.AudioRecord
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isGone
import androidx.core.view.isVisible
import java.io.File

class MainActivity : AppCompatActivity() {
    private var audioRecord: AudioRecord? = null
    private var pcmStreamPlayer: PCMStreamPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pcmStreamPlayer = PCMStreamPlayer(16000)

        var isStart = false
        val button = findViewById<Button>(R.id.bt)
        val pcmPlayButton = findViewById<Button>(R.id.bt2)
        val tvPath = findViewById<TextView>(R.id.tv_path)
        button.setOnClickListener {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
                return@setOnClickListener
            }
            pcmPlayButton.isGone = true
            if (isStart) {
                audioRecord?.let {
                    RecordHelper.stop(it) { pcmPath ->
                        pcmPlayButton.isVisible = true
                        button.text = "开始"
                        tvPath.text = "$pcmPath\n点击可分享"
                        pcmPlayButton.setOnClickListener {
                            pcmStreamPlayer?.play(pcmPath)
                        }
                        tvPath.setOnClickListener {
                            Intent().apply {
                                action = Intent.ACTION_SEND
                                FileProvider.getUriForFile(
                                    this@MainActivity,
                                    "${packageName}.fileProvider",
                                    File(pcmPath)
                                ).let {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                    putExtra(Intent.EXTRA_STREAM, it)
                                    setDataAndType(it, "file/*")
                                }
                                startActivity(Intent.createChooser(this, "分享到"))
                            }
                        }
                    }
                }
                isStart = false
                return@setOnClickListener
            }
            button.text = "停止"
            val startTime = System.currentTimeMillis()
            RecordHelper.startRecord("${System.currentTimeMillis()}.pcm") { audioRecord, d ->
                runOnUiThread {
                    tvPath.text =
                        "录制时间：${System.currentTimeMillis() - startTime}ms\n分贝：${d}dB"
                }
                isStart = true
                this.audioRecord = audioRecord
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pcmStreamPlayer?.release()
        audioRecord?.release()
    }
}