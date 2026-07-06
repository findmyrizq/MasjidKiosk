package com.masjid.kiosk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object Kiosk {
    // How long the kiosk stays paused after unlocking
    const val UNLOCK_PAUSE_MS = 5 * 60_000L

    @Volatile
    var unlockedUntil = 0L

    val locked: Boolean
        get() = System.currentTimeMillis() > unlockedUntil

    // Known/likely AbleSign package names, most likely first
    private val CANDIDATES = listOf(
        "tv.ablesign.app",
        "app.ablesign.tv",
        "com.ablesign.app",
        "com.ablesign.tv",
        "com.ablesign.signage",
        "tv.ablesign"
    )

    fun launchIntent(pm: PackageManager, pkg: String): Intent? =
        pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)

    fun resolveTarget(ctx: Context): String? {
        val pm = ctx.packageManager
        val prefs = ctx.getSharedPreferences("kiosk", Context.MODE_PRIVATE)

        // 1. Previously discovered package, if still launchable
        prefs.getString("target", null)?.let {
            if (launchIntent(pm, it) != null) return it
        }

        // 2. Known candidates
        for (pkg in CANDIDATES) {
            if (launchIntent(pm, pkg) != null) {
                prefs.edit().putString("target", pkg).apply()
                return pkg
            }
        }

        // 3. Scan every installed app for an AbleSign-looking package/label
        val found = try {
            pm.getInstalledApplications(0).firstOrNull { app ->
                val label = try {
                    pm.getApplicationLabel(app).toString().lowercase()
                } catch (e: Exception) {
                    ""
                }
                val pkg = app.packageName.lowercase()
                (pkg.contains("ablesign") || label.contains("ablesign") ||
                    (label.contains("digital") && label.contains("signage"))) &&
                    launchIntent(pm, app.packageName) != null
            }?.packageName
        } catch (e: Exception) {
            null
        }
        if (found != null) prefs.edit().putString("target", found).apply()
        return found
    }

    fun launchTarget(ctx: Context): Boolean {
        val pkg = resolveTarget(ctx) ?: return false
        val intent = launchIntent(ctx.packageManager, pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
