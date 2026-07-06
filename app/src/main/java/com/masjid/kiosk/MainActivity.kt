package com.masjid.kiosk

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        // How many quick consecutive BACK returns unlock the kiosk
        const val UNLOCK_RETURNS = 3

        // Window in ms — returns must happen within this time to count as consecutive
        const val CONSECUTIVE_WINDOW_MS = 6000L
    }

    private var returnCount = 0
    private var lastReturnTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        KioskService.start(this)
    }

    override fun onResume() {
        super.onResume()

        if (!Kiosk.locked) {
            showStatus(pausedMessage())
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastReturnTime > CONSECUTIVE_WINDOW_MS) {
            returnCount = 0
        }
        returnCount++
        lastReturnTime = now

        if (returnCount >= UNLOCK_RETURNS) {
            returnCount = 0
            Kiosk.unlockedUntil = now + Kiosk.UNLOCK_PAUSE_MS
            showStatus(pausedMessage())
        } else {
            showStatus(
                "Launching AbleSign…\n\n" +
                    "(Press BACK ${UNLOCK_RETURNS - returnCount} more time(s) quickly to pause the kiosk)"
            )
            if (!Kiosk.launchTarget(this)) showDiagnostics()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!Kiosk.locked &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            Kiosk.unlockedUntil = 0L
            returnCount = 0
            if (!Kiosk.launchTarget(this)) showDiagnostics()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openOverlaySettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun pausedMessage(): String {
        val mins = Kiosk.UNLOCK_PAUSE_MS / 60000
        var msg = "Kiosk paused for $mins minutes.\n\n" +
            "Press SELECT (centre button) to resume signage now.\n" +
            "Press HOME to use the Fire TV normally."
        if (Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(this)) {
            msg += "\n\nOptional: press MENU (☰) to grant \"Display over other apps\" " +
                "for stronger auto-relaunch on this Fire OS version."
        }
        return msg
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) return
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            showStatus(
                "This Fire OS build does not expose the overlay setting.\n\n" +
                    "Grant it via ADB instead:\n" +
                    "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow"
            )
        }
    }

    private fun showDiagnostics() {
        val pm = packageManager
        val apps = try {
            pm.getInstalledApplications(0)
                .filter { Kiosk.launchIntent(pm, it.packageName) != null }
                .take(25)
                .joinToString("\n") { "${pm.getApplicationLabel(it)} — ${it.packageName}" }
        } catch (e: Exception) {
            "(could not list apps)"
        }
        showStatus(
            "AbleSign not found!\n\n" +
                "Install \"Digital Signage\" by AbleSign from the Appstore, then reopen this app.\n\n" +
                "Launchable apps on this device:\n$apps"
        )
    }

    private fun showStatus(message: String) {
        findViewById<TextView>(R.id.statusText)?.text = message
    }
}
