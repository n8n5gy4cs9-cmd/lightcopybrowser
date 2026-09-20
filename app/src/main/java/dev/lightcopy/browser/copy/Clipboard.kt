package dev.lightcopy.browser.copy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

interface Clipboard {
    fun copy(label: String, text: String)
}

class AndroidClipboard(context: Context) : Clipboard {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)
    var vibrationEnabled: Boolean = false

    override fun copy(label: String, text: String) {
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
        if (vibrationEnabled && vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator.vibrate(18)
        }
    }
}
