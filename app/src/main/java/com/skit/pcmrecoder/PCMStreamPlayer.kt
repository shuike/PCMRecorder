package com.skit.pcmrecorder

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.io.File

class PCMStreamPlayer(private val sampleRate: Int) {
    private var audioTrack: AudioTrack? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val bufferSize =
            AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
        audioTrack!!.play()
    }

    fun play(filePath: String) {
        val file = File(filePath)
        val readBytes = file.readBytes()
        audioTrack?.write(readBytes, 0, readBytes.size)
    }

    fun play(bytes: ByteArray) {
        audioTrack?.write(bytes, 0, bytes.size)
    }

    //停止
    fun stop() {
        if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
            //播放中停止播放
            audioTrack?.stop()
            //清除播放缓冲器
            audioTrack?.flush()
        }
    }

    //在结束时释放对象
    fun release() {
        try {
            stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}