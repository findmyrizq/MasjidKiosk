package com.masjid.kiosk

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

        // BACK presses on the setup screen before skipping is offered
        const val SETUP_ATTEMPTS_BEFORE_SKIP = 3
    }

    private var returnCount = 0
    private var lastReturnTime = 0L
    private var settingUp = false
    private var setupAttempts = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        KioskService.start(this)
    }

    override fun onResume() {
        super.onResume()

        // Overlay permission unblocks background relaunch on Fire OS 8+.
        // Hold the kiosk in setup mode until it is granted (or explicitly skipped).
        if (overlayNeeded()) {
            settingUp = true
            setupAttempts++
            val skipHint = if (setupAttempts >= SETUP_ATTEMPTS_BEFORE_SKIP)
                "\n\n(Press BACK to skip this setup — the kiosk will be weaker on this Fire OS)" else ""
            showStatus(
                "ONE-TIME SETUP\n\n" +
                    "In the screen that opens, select \"Masjid Kiosk\" " +
                    "and turn ON \"Display over other apps\".\n\n" +
                    "Then press BACK to return here.\n\nOpening settings…$skipHint"
            )
            handler.postDelayed({ if (settingUp && overlayNeeded()) openOverlaySettings() }, 1500)
            return
        }
        settingUp = false

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

    private fun overlayNeeded(): Boolean =
        Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(this) &&
            !getSharedPreferences("kiosk", MODE_PRIVATE).getBoolean("overlay_skipped", false)

    override fun onBackPressed() {
        if (settingUp && setupAttempts >= SETUP_ATTEMPTS_BEFORE_SKIP) {
            getSharedPreferences("kiosk", MODE_PRIVATE)
                .edit().putBoolean("overlay_skipped", true).apply()
            settingUp = false
            showStatus("Setup skipped. Starting kiosk…")
            if (!Kiosk.launchTarget(this)) showDiagnostics()
            return
        }
        super.onBackPressed()
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
            // Settings screen missing on this Fire OS build — don't trap the kiosk
            getSharedPreferences("kiosk", MODE_PRIVATE)
                .edit().putBoolean("overlay_skipped", true).apply()
            settingUp = false
            showStatus(
                "This Fire OS build does not expose the overlay setting.\n\n" +
                    "Optional, via ADB:\n" +
                    "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow\n\n" +
                    "Starting kiosk anyway…"
            )
            handler.postDelayed({ Kiosk.launchTarget(this) }, 6000)
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
