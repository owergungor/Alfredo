package com.glasslauncher.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import com.glasslauncher.tv.ui.HomeScreen
import com.glasslauncher.tv.ui.Screen
import com.glasslauncher.tv.ui.UiStrings
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(Screen.Home)
    @Volatile private var lastInput = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        com.glasslauncher.tv.data.StartupTracker.onActivityCreated()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideBars()
        val app = application as GlassApp
        UiStrings.setLanguage(app.settings.language)
        handleExtras(intent)
        setContent {
            // A launcher never exits on Back; it just returns to its home view.
            BackHandler(enabled = true) { screen = Screen.Home }
            com.glasslauncher.tv.ui.GlassType(app.settings.uiFont) { HomeScreen(app, screen) { screen = it } }
        }
    }

    private fun hideBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        lastInput = System.currentTimeMillis()
        hideBars()
        (application as GlassApp).cw.pollNow()
    }

    /** Terminal helper: am start -n com.glasslauncher.tv/.MainActivity --es icon_pack <package> ("none" clears it). */
    private fun handleExtras(i: Intent?) {
        val app = application as GlassApp
        i?.getStringExtra("reset_icon")?.let { app.icons.reset(it) }
        if (i?.getBooleanExtra("open_folder", false) == true) app.requestFolder.value = true
        if (i?.hasExtra("hero_autoplay") == true) app.settings.heroAutoplay = i.getBooleanExtra("hero_autoplay", true)
        if (i?.hasExtra("page_glass") == true) com.glasslauncher.tv.ui.PageGlass.on.value = i.getBooleanExtra("page_glass", true)
        i?.getIntExtra("open_settings", -1)?.takeIf { it >= 0 }?.let { app.requestSettings.intValue = it; app.requestSettingsRev.intValue++ }
        if (i?.getBooleanExtra("open_menu", false) == true) { app.requestSettings.intValue = 100; app.requestSettingsRev.intValue++ }
        if (i?.getBooleanExtra("open_library", false) == true) { app.requestSettings.intValue = 101; app.requestSettingsRev.intValue++ }
        if (i?.hasExtra("timing") == true) com.glasslauncher.tv.data.Glog.timing = i.getBooleanExtra("timing", false)
        if (i?.hasExtra("dbg_hide") == true) com.glasslauncher.tv.ui.DebugHide.intValue = i.getIntExtra("dbg_hide", 0)
        i?.getStringExtra("dock_style")?.let { app.settings.dockStyle = it }
        i?.getStringExtra("panel_style")?.let { app.settings.panelStyle = it }
        i?.getStringExtra("tile_style")?.let { app.settings.tileStyle = it }
        i?.getStringExtra("theme")?.let { app.settings.applyTheme(it) }
        i?.getStringExtra("menu_mode")?.let { app.settings.menuMode = it }
        i?.getStringExtra("bg_mode")?.let { if (it in setOf("carousel", "image", "video")) app.settings.bgMode = it }
        if (i?.hasExtra("menu_city") == true) app.settings.menuCity = i.getBooleanExtra("menu_city", true)
        if (i?.hasExtra("focus_color") == true) app.settings.focusColor = i.getBooleanExtra("focus_color", false)
        i?.getStringExtra("slide_anim")?.let { if (it in setOf("fade", "slide", "apple")) app.settings.slideAnim = it }
        i?.getStringExtra("dots_mode")?.let { if (it in setOf("window", "liquid", "progress", "hidden")) app.settings.dotsMode = it }
        i?.getStringExtra("continue_mode")?.let { if (it in setOf("off", "first", "mixed")) app.settings.continueMode = it }
        if (i?.getBooleanExtra("export_backup", false) == true) {
            val res = com.glasslauncher.tv.data.BackupManager.exportBackup(this)
            com.glasslauncher.tv.data.Glog.i("Auto export result: $res")
        }
        if (i?.getBooleanExtra("import_backup", false) == true) {
            val f = com.glasslauncher.tv.data.BackupManager.getPreferredBackupFile(this)
            val res = com.glasslauncher.tv.data.BackupManager.importBackup(this, f)
            com.glasslauncher.tv.data.Glog.i("Auto import result: $res")
        }
        if (i?.getBooleanExtra("dump_prefs", false) == true) app.settings.dumpAll().toSortedMap().forEach { (k, v) ->
            android.util.Log.i("GlassPrefs", "$k=${v.toString().take(120)}")
        }
        if (i?.getBooleanExtra("dump_settings", false) == true) app.settings.run {
            android.util.Log.i("GlassSettings", "dockStyle=$dockStyle tileStyle=$tileStyle panelStyle=$panelStyle blur=$blurRadius refraction=$refraction dockScale=$dockScale continueMode=$continueMode cwLead=$cwLead cw=${(application as GlassApp).cw.entries.value.size}")
        }
        i?.getStringExtra("perf_mode")?.let { if (it in setOf("auto", "performance", "balanced", "quality")) app.settings.applyMode(it) }
        i?.getIntExtra("refraction", -1)?.takeIf { it >= 0 }?.let { app.settings.refraction = it; app.settings.perfMode = "custom" }
        i?.getIntExtra("blur", -1)?.takeIf { it >= 0 }?.let { app.settings.blurRadius = it }
        i?.getStringExtra("dock_style")?.let { if (it in setOf("liquid", "clear", "amoled", "bow", "water", "water_still")) app.settings.dockStyle = it }
        i?.getStringExtra("tile_style")?.let { if (it in setOf("liquid", "clear", "amoled", "bow", "water", "water_still")) app.settings.tileStyle = it }
        i?.getStringExtra("icon_pack")?.let { p ->
            if (p == "none") app.settings.iconPack = ""
            else if (app.packs.installed().any { it.pkg == p }) app.settings.iconPack = p
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExtras(intent)
        // Home pressed while we are already in front: go back to the plain home view.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            screen = Screen.Home
            (application as GlassApp).homeRev.intValue++
        }
        lastInput = System.currentTimeMillis()
    }

    override fun onUserInteraction() {
        lastInput = System.currentTimeMillis()
        (application as GlassApp).touch()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            screen = if (screen == Screen.Settings) Screen.Home else Screen.Settings
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
