package com.glasslauncher.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Home button → Glassio.
 *
 * Google TV sends Home to its own launcher no matter which app is the default home. This service
 * (switched on by the user under Settings > Accessibility) receives the Home key *before* the system
 * does and consumes it, so the stock launcher never appears and there is no flash.
 * The old window-change reaction is kept only as a fallback for devices that don't deliver key events.
 */
class HomeKeyService : AccessibilityService() {
    private var lastLaunch = 0L
    private var homeDownAt = 0L

    private fun enabled(): Boolean = (application as? GlassApp)?.settings?.interceptHome == true

    private fun showGlass(openAppsFolder: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now - lastLaunch < 1000 && !openAppsFolder) return
        lastLaunch = now
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (openAppsFolder) intent.putExtra("open_folder", true)
        startActivity(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Apps should work independently of the optional Home-key interception setting.
        // The accessibility service itself must still be enabled in Android settings.
        if (event.keyCode == KeyEvent.KEYCODE_ALL_APPS) {
            if (event.action == KeyEvent.ACTION_UP) {
                (application as? GlassApp)?.requestFolder?.value = true
                showGlass(openAppsFolder = true)
            }
            return true
        }
        if (!enabled()) return false
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) homeDownAt = System.currentTimeMillis()
            KeyEvent.ACTION_UP -> showGlass()
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !enabled()) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != "com.google.android.apps.tv.launcherx" && pkg != "com.google.android.tvlauncher") return
        // Only reached when the Home key was not filtered above (e.g. Home from the Google TV remote app).
        if (System.currentTimeMillis() - homeDownAt < 1500) return
        showGlass()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Ask for key events explicitly; some TV builds ignore the flag from the XML alone.
        serviceInfo = serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onInterrupt() {}
}
