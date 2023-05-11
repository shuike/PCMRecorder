package com.skit.pcmrecorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.log10

object RecordHelper {
    private const val TAG = "RecordHelper"

    private val handler = Handler(Looper.getMainLooper())
    private var recordingAudioThread: Thread? = null
    private var isRecording = false
    private val mSampleRate = 16000 // 此处的值必须与录音时的采样率一致
    private val mChannel = AudioFormat.CHANNEL_IN_STEREO //立体声
    private val mEncoding = AudioFormat.ENCODING_PCM_16BIT

    private var pcmName: String = ""

    @SuppressLint("MissingPermission")
    fun startRecord(
        name: String,
        decibelCallback: ((AudioRecord, Double) -> Unit)? = null,
    ): AudioRecord {
        pcmName = name
        val minBufferSize = AudioRecord.getMinBufferSize(
            mSampleRate,
            mChannel,
            mEncoding
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            mSampleRate,
            mChannel,
            mEncoding,
            minBufferSize
        )
        isRecording = true
        audioRecord.setRecordPositionUpdateListener(updateListener())
        audioRecord.startRecording()
        var recordingAudioThread: Thread? = null
        val audioCacheFilePath = getPCMCacheFilePath()
        var read = 0
        var decibelData: ByteArray? = null
        var timer: Timer? = null
        decibelCallback?.let { callback ->
            val _timer = Timer()
            timer = _timer
            _timer.schedule(object : TimerTask() {
                override fun run() {
                    decibelData?.let {
                        val decibel = getDecibelForPcm(read, it)
                        callback.invoke(audioRecord, decibel)
                    }
                }
            }, 100, 100)
        }
        recordingAudioThread = thread {
            val file = File(audioCacheFilePath)
            Log.i(TAG, "audio cache pcm file path:$audioCacheFilePath")

            /*
            *  以防万一，看一下这个文件是不是存在，如果存在的话，先删除掉
            */
            if (file.exists()) {
                file.delete()
            }

            try {
                file.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            var fos: FileOutputStream? = null
            try {
                fos = FileOutputStream(file)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                Log.e(TAG, "临时缓存文件未找到")
            }

            if (fos == null) {
                return@thread
            }
            val data = ByteArray(minBufferSize)
            while (isRecording && !recordingAudioThread?.isInterrupted!!) {
                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    read = audioRecord.read(data, 0, minBufferSize)
                    if (decibelCallback != null) {
                        decibelData = data.clone()
                    }
                    try {
                        fos.write(data)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                Log.d("RecordHelper", "getVolumeMax: ${getDecibelForPcm(read, data.clone())}")
            }
            timer?.cancel()
            try {
                // 关闭数据流
                fos.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return audioRecord

    }

    private fun getPCMCacheFilePath(): String =
        File(App.application.cacheDir, pcmName).absolutePath

    fun stop(audioRecord: AudioRecord, block: (String) -> Unit) {
        isRecording = false
        try {
            audioRecord.stop()
            audioRecord.release()
        } catch (e: java.lang.Exception) {
        }
        recordingAudioThread?.interrupt()
        recordingAudioThread = null
        handler.post {
            block(getPCMCacheFilePath())
        }
    }

    fun cancel(audioRecord: AudioRecord) {
        isRecording = false
        try {
            audioRecord.stop()
            audioRecord.release()
        } catch (e: java.lang.Exception) {
            // 报 called on an uninitialized AudioRecord.未查明原因，可能跟权限回收时micView UP事件调用这个方法有关系
        }
        recordingAudioThread?.interrupt()
        recordingAudioThread = null
    }


    /*
    * 获取某一段数据的分贝值-针对pcm的原始音频数据
    *   data : 某一时间段pcm数据
    *   dataLength :有效数据大小
    *   这个分贝值估计值
    * */
    private fun getDecibelForPcm(dataLength: Int, data: ByteArray): Double {
        var sum: Long = 0
        var temp: Long = 0
        var i = 0
        while (i < data.size) {
            temp = (data[i + 1] * 128 + data[i]).toLong() //累加求和
            temp *= temp
            sum += temp
            i += 2
        }

        //平方和除以数据长度，得到音量大小
        val square = sum / dataLength.toDouble() //音量大小
        return 10 * log10(square * 2)
    }

    private fun getVolume(r: Int, buffer: ByteArray): Double {
        var v: Long = 0
        // 将 buffer 内容取出，进行平方和运算
        for (i in buffer.indices) {
            v += buffer[i] * buffer[i]
        }
        // 平方和除以数据总长度，得到音量大小。
        val mean = v / r.toDouble()
        val volume = 10 * log10(mean)
        return volume
    }

    private fun getVolumeMax(r: Int, bytes_pkg: ByteArray): Int {
        //way 2
        val mShortArrayLength = r / 2
        val shortBuffer: ShortArray = byteArray2ShortArray(bytes_pkg, mShortArrayLength)
        var max = 0
        if (r > 0) {
            for (i in 0 until mShortArrayLength) {
                if (abs(shortBuffer[i].toInt()) > max) {
                    max = abs(shortBuffer[i].toInt())
                }
            }
        }
        return max
    }

    private fun byteArray2ShortArray(data: ByteArray, items: Int): ShortArray {
        val retVal = ShortArray(items)
        for (i in retVal.indices) retVal[i] =
            (data[i * 2].toInt() and 0xff or (data[i * 2 + 1].toInt() and 0xff shl 8)).toShort()
        return retVal
    }

    private fun updateListener(): AudioRecord.OnRecordPositionUpdateListener {
        return object : AudioRecord.OnRecordPositionUpdateListener {
            override fun onPeriodicNotification(recorder: AudioRecord) {
                Log.d(TAG, "onPeriodicNotification")
            }

            override fun onMarkerReached(recorder: AudioRecord) {
                // Не нужно
                Log.d(TAG, "onMarkerReached")
            }
        }
    }
}