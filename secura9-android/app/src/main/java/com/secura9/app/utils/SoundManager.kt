package com.secura9.app.utils

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.secura9.app.R
import kotlinx.coroutines.*

class SoundManager(private val context: Context) {

    private val toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 60)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var chimePlayer: MediaPlayer? = null
    private var approvePlayer: MediaPlayer? = null
    private var denyPlayer: MediaPlayer? = null

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun playBootSequence() {
        scope.launch {
            delay(100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 100)
            delay(300)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 80)
            delay(250)
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 150)
            delay(200)
            toneGen.startTone(ToneGenerator.TONE_CDMA_PRESSHOLDKEY_LITE, 200)
            delay(300)
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 300)
            vibrate(100)
        }
    }

    fun playClick() {
        toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 50)
    }

    fun playSuccess() {
        toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        vibrate(50)
    }

    fun playError() {
        toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 200)
        vibrate(100)
    }

    fun playAlert() {
        toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
        vibrate(200)
    }

    fun playDoorOpen() {
        scope.launch {
            toneGen.startTone(ToneGenerator.TONE_CDMA_PRESSHOLDKEY_LITE, 200)
            delay(150)
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 300)
            vibrate(150)
        }
    }

    fun playDoorClose() {
        toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 200)
        vibrate(100)
    }

    fun startApprovalChime() {
        if (chimePlayer?.isPlaying == true) return
        stopApprovalChime()
        chimePlayer = MediaPlayer.create(context, R.raw.approval_chime).apply {
            isLooping = true
            setVolume(0.6f, 0.6f)
            start()
        }
    }

    fun stopApprovalChime() {
        chimePlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        chimePlayer = null
    }

    fun playApprove() {
        stopDeny()
        approvePlayer = MediaPlayer.create(context, R.raw.approve).apply {
            setVolume(0.8f, 0.8f)
            start()
            setOnCompletionListener { release(); approvePlayer = null }
        }
    }

    fun playDeny() {
        stopApprove()
        denyPlayer = MediaPlayer.create(context, R.raw.deny).apply {
            setVolume(0.7f, 0.7f)
            start()
            setOnCompletionListener { release(); denyPlayer = null }
        }
    }

    private fun stopApprove() {
        approvePlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        approvePlayer = null
    }

    private fun stopDeny() {
        denyPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        denyPlayer = null
    }

    fun playApprovalReceived() {
        scope.launch {
            repeat(3) {
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 100)
                delay(150)
            }
            vibrate(300)
        }
    }

    fun playSystemReady() {
        scope.launch {
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 100)
            delay(120)
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 100)
            delay(120)
            toneGen.startTone(ToneGenerator.TONE_CDMA_PRESSHOLDKEY_LITE, 400)
            vibrate(200)
        }
    }

    private fun vibrate(ms: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    fun release() {
        stopApprovalChime()
        toneGen.release()
    }
}
