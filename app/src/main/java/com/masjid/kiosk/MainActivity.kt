package com.masjid.kiosk

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        // ── CONFIGURE THIS ──────────────────────────────────────────────────
        // AbleSign package name. To find it on the Fire TV:
        //   Settings > Applications > Manage Installed Apps > AbleSign > scroll to "Version"
        //   OR via ADB: adb shell pm list packages | grep -i sign
        const val TARGET_PACKAGE = "tv.ablesign.app"

        // How many times the user must quickly return to this screen to unlock
        const val UNLOCK_RETURNS = 3

        // Window in ms — returns must happen within this time to count as consecutive
        const val CONSECUTIVE_WINDOW_MS = 6000L
        // ────────────────────────────────────────────────────────────────────
    }

    private var returnCount = 0
    private var lastReturnTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()

        if (now - lastReturnTime > CONSECUTIVE_WINDOW_MS) {
            returnCount = 0
        }

        returnCount++
        lastReturnTime = now

        if (returnCount < UNLOCK_RETURNS) {
            showStatus("Launching AbleSign…\n\n(Return here ${UNLOCK_RETURNS - returnCount} more time(s) quickly to unlock)")
            launchTargetApp()
        } else {
            returnCount = 0
            showStatus("Kiosk unlocked.\n\nPress HOME to return to AbleSign.")
        }
    }

    private fun launchTargetApp() {
        val intent = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(intent)
        } else {
            showStatus(
                "AbleSign not found!\n\n" +
                "Make sure AbleSign is installed first.\n\n" +
                "Expected package:\n$TARGET_PACKAGE\n\n" +
                "If wrong, update TARGET_PACKAGE in MainActivity.kt and rebuild."
            )
        }
    }

    private fun showStatus(message: String) {
        findViewById<TextView>(R.id.statusText)?.text = message
    }
}
