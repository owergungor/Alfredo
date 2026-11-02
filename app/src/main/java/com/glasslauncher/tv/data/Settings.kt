package com.glasslauncher.tv.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

/**
 * Every launcher option lives here. Reads touch a Compose state counter, so any composable that
 * reads a property recomposes automatically when a setter bumps it.
 */
class Settings(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)
    private val rev = mutableIntStateOf(0)

    fun reload() { touch() }
    private fun touch() { rev.intValue = rev.intValue + 1 }
    /** Test only: every stored value (am start --ez dump_prefs true → logcat GlassPrefs). */
    fun dumpAll(): Map<String, *> = sp.all

    private fun b(k: String, d: Boolean): Boolean { rev.intValue; return sp.getBoolean(k, d) }
    private fun i(k: String, d: Int): Int { rev.intValue; return sp.getInt(k, d) }
    private fun f(k: String, d: Float): Float { rev.intValue; return sp.getFloat(k, d) }
    private fun s(k: String, d: String): String { rev.intValue; return sp.getString(k, d) ?: d }
    private fun putB(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply(); touch() }
    private fun putI(k: String, v: Int) { sp.edit().putInt(k, v).apply(); touch() }
    private fun putF(k: String, v: Float) { sp.edit().putFloat(k, v).apply(); touch() }
    private fun putS(k: String, v: String) { sp.edit().putString(k, v).apply(); touch() }
    private fun list(k: String): List<String> =
        s(k, "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    private fun putList(k: String, v: List<String>) = putS(k, v.joinToString("\n"))


    /** UI language: en | tr. */
    var language: String get() = s("language", "en"); set(v) = putS("language", if (v == "tr") "tr" else "en")

    // ---- Hero / carousel
    var heroAutoplay: Boolean get() = b("heroAutoplay", true); set(v) = putB("heroAutoplay", v)
    /** auto | performance | balanced | quality | custom */
    var perfMode: String get() = s("perfMode", "auto"); set(v) = putS("perfMode", v)

    /**
     * What this TV can carry, from its memory and GPU: 2 GB / GLES 2 boxes get "performance",
     * ~4 GB boxes (Google TV Streamer, Shield) "balanced", bigger ones "quality".
     */
    fun isLowRam(): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gb = mi.totalMem / (1024.0 * 1024 * 1024)
        return am.isLowRamDevice || gb < 2.0
    }

    fun deviceTier(): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gb = mi.totalMem / (1024.0 * 1024 * 1024)
        val gles = am.deviceConfigurationInfo.reqGlEsVersion
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            am.isLowRamDevice || gb < 2.6 || gles < 0x30000 || android.os.Build.VERSION.SDK_INT < 33 -> "performance"
            gb < 5.0 || cores < 6 -> "balanced"
            else -> "quality"
        }
    }
    /** The tier Auto picked, for display. */
    val autoTier: String by lazy { deviceTier() }

    /** The page picture is prepared at this fraction of the screen size (2/3 in Performance: 2.25× less memory and drawing). */
    var pageScale: Float get() = f("pageScale", 1f); set(v) = putF("pageScale", if (v < 0.9f) 0.667f else 1f)
    /** 0 small, 1 medium, 2 large memory caches (pictures kept ready); set by the mode. */
    var memTier: Int get() = i("memTier", 1); set(v) = putI("memTier", v.coerceIn(0, 2))

    /** Glass copies of the backdrop: 480 px (light, default) or 720 px (crisper glass, more memory). */
    var glassRes: Int get() = i("glassRes", 480); set(v) = putI("glassRes", if (v >= 720) 720 else 480)

    /** Which picture a title uses: main | second | best (highest resolution) | addon (the add-on's own). */
    /** Per-title picture picked with a long press on the carousel: "key\turl" lines. */
    fun artPick(key: String): String? = list("artPicks").firstOrNull { it.startsWith("$key\t") }?.substringAfter('\t')
    fun setArtPick(key: String, url: String?) {
        val rest = list("artPicks").filterNot { it.startsWith("$key\t") }
        putList("artPicks", if (url == null) rest else (rest + "$key\t$url").takeLast(300))
    }
    fun clearArtPicks() = putList("artPicks", emptyList())
    /** max = original pictures (4K source, sharpest) | high = 1280 px pictures (faster, lighter) */
    var imageQuality: String get() = s("imageQuality", "max"); set(v) = putS("imageQuality", v)
    /** How many slides ahead are prepared (pictures, logos, glass). */
    var prefetchDepth: Int get() = i("prefetchDepth", 4); set(v) = putI("prefetchDepth", v.coerceIn(1, 8))

    fun applyMode(m: String) {
        when (if (m == "auto") deviceTier() else m) {
            // Performance: pictures at 1280 px and drawn at 2/3 resolution, no rim refraction (no GPU effects at
            // all), a plain crossfade and small caches: for 2 GB TVs and older graphics chips.
            "performance" -> { imageQuality = "high"; prefetchDepth = 2; transitionMs = 300; refraction = 0; heroGallery = false; glassRes = 480
                pageScale = 0.667f; slideAnim = "fade"; memTier = 0 }
            "balanced" -> { imageQuality = "high"; prefetchDepth = 3; transitionMs = 450; refraction = 6; heroGallery = true; galleryShots = 2; glassRes = 480
                pageScale = 1f; memTier = 1 }
            "quality" -> { imageQuality = "max"; prefetchDepth = 6; transitionMs = 700; refraction = 14; heroGallery = true; galleryShots = 3; glassRes = 720
                pageScale = 1f; memTier = 2 }
        }
        if (isLowRam()) {
            if (memTier > 1) memTier = 1
            if (pageScale > 0.667f) pageScale = 0.667f
            if (prefetchDepth > 2) prefetchDepth = 2
            heroGallery = false
            if (glassRes > 480) glassRes = 480
            refraction = 0
        }
        perfMode = m
    }

    /** Apple TV style: each title shows a few of its backdrops before the carousel moves on. */
    var heroGallery: Boolean get() = b("heroGallery", false); set(v) = putB("heroGallery", v)
    var galleryShots: Int get() = i("galleryShots", 3); set(v) = putI("galleryShots", v.coerceIn(2, 6))
    var heroIntervalSec: Int get() = i("heroIntervalSec", 6); set(v) = putI("heroIntervalSec", v.coerceIn(3, 60))
    /** logo | text | hidden */
    var titleMode: String get() = s("titleMode", "logo"); set(v) = putS("titleMode", v)
    var titleScale: Float get() = f("titleScale", 0.7f); set(v) = putF("titleScale", v.coerceIn(0.5f, 2.2f))
    var showMeta: Boolean get() = b("showMeta", true); set(v) = putB("showMeta", v)
    var showGenres: Boolean get() = b("showGenres", true); set(v) = putB("showGenres", v)
    var showSynopsis: Boolean get() = b("showSynopsis", true); set(v) = putB("showSynopsis", v)
    /** Synopsis appears only while the carousel is selected. */
    var synopsisOnFocus: Boolean get() = b("synopsisOnFocus", true); set(v) = putB("synopsisOnFocus", v)
    var synopsisScale: Float get() = f("synopsisScale", 1.2f); set(v) = putF("synopsisScale", v.coerceIn(0.6f, 2.0f))
    var synopsisLines: Int get() = i("synopsisLines", 7); set(v) = putI("synopsisLines", v.coerceIn(1, 8))
    /** window | progress | hidden */
    /** window (dots on a pill) | liquid (liquid-glass drops, no pill) | progress | hidden */
    var dotsMode: String get() = s("dotsMode", "liquid"); set(v) = putS("dotsMode", v)
    /** apple (Apple TV: the new picture opens up from the side with parallax) | slide (push) | fade */
    var slideAnim: String get() = s("slideAnim", "apple"); set(v) = putS("slideAnim", v)
    // ---- Your own background
    /** carousel (each title's backdrop) | image | video */
    var bgMode: String get() = s("bgMode", "carousel"); set(v) = putS("bgMode", v)
    /** A link or a file path. */
    var bgImage: String get() = s("bgImage", ""); set(v) = putS("bgImage", v)
    var bgVideo: String get() = s("bgVideo", ""); set(v) = putS("bgVideo", v)
    /** 0 = disabled; otherwise rotate folder pictures at the selected interval. */
    var backgroundNextRequest: Int get() = i("backgroundNextRequest", 0); set(v) = putI("backgroundNextRequest", v)
    var bgRotationMinutes: Int
        get() = i("bgRotationMinutes", 0)
        set(v) = putI("bgRotationMinutes", if (v in listOf(0, 5, 15, 30, 60)) v else 0)

    // ---- Menu (from the settings button) and Library
    /** auto (opens when you go up to the settings button) | manual (OK on it) | off (it opens Settings) */
    var menuMode: String get() = s("menuMode", "auto"); set(v) = putS("menuMode", v)
    var menuClock: Boolean get() = b("menuClock", true); set(v) = putB("menuClock", v)
    var menuDate: Boolean get() = b("menuDate", true); set(v) = putB("menuDate", v)
    var menuWeather: Boolean get() = b("menuWeather", true); set(v) = putB("menuWeather", v)
    var menuCity: Boolean get() = b("menuCity", true); set(v) = putB("menuCity", v)
    var menuLibrary: Boolean get() = b("menuLibrary", true); set(v) = putB("menuLibrary", v)
    var menuAndroid: Boolean get() = b("menuAndroid", true); set(v) = putB("menuAndroid", v)
    var menuWifi: Boolean get() = b("menuWifi", true); set(v) = putB("menuWifi", v)
    var menuThemes: Boolean get() = b("menuThemes", true); set(v) = putB("menuThemes", v)
    var menuBackground: Boolean get() = b("menuBackground", true); set(v) = putB("menuBackground", v)
    /** Look of the menu: same choices as the dock (set by Themes). */
    var menuStyle: String get() = s("menuStyle", "water_still"); set(v) = putS("menuStyle", v)
    /** The + button next to Play (save to Library). */
    var heroPlus: Boolean get() = b("heroPlus", true); set(v) = putB("heroPlus", v)
    var libraryTrakt: Boolean get() = b("libraryTrakt", true); set(v) = putB("libraryTrakt", v)
    /** Whether to show the Trakt watchlist in the Library. */
    var libraryToTrakt: Boolean get() = b("libraryToTrakt", true); set(v) = putB("libraryToTrakt", v)
    var libraryTitles: Boolean get() = b("libraryTitles", true); set(v) = putB("libraryTitles", v)
    /** bow (black on white) | amoled | liquid | clear | water_still | water */
    var theme: String get() = s("theme", "water_still"); set(v) = putS("theme", v)

    /** One choice for the dock, the icons and the menu (Settings keep their own look). Black ones keep the dock clear (matte). */
    fun applyTheme(t: String) {
        when (t) {
            "bow" -> { dockStyle = "clear"; tileStyle = "bow"; menuStyle = "bow" }
            "amoled" -> { dockStyle = "clear"; tileStyle = "amoled"; menuStyle = "amoled" }
            else -> { dockStyle = t; tileStyle = t; menuStyle = t }
        }
        theme = t
        // The clock follows the global theme by default; the Clock style control can still override it later.
        clockTheme = t
    }

    /** Buttons under the title: apple (Play + round Next, like Apple TV) | play (Play only, when selected) | hidden */
    var heroButtons: String get() = s("heroButtons", "apple"); set(v) = putS("heroButtons", v)
    var dotsMax: Int get() = i("dotsMax", 10); set(v) = putI("dotsMax", v.coerceIn(3, 20))
    var perCatalog: Int get() = i("perCatalog", 20); set(v) = putI("perCatalog", v.coerceIn(5, 500))
    /** 0 = unlimited */
    var maxItems: Int get() = i("maxItems", 50); set(v) = putI("maxItems", v.coerceAtLeast(0))
    var shuffle: Boolean get() = b("shuffle", true); set(v) = putB("shuffle", v)
    /** en | ar */

    var transitionMs: Int get() = i("transitionMs", 700); set(v) = putI("transitionMs", v.coerceIn(100, 2000))
    var dotsAutoHide: Boolean get() = b("dotsAutoHide", true); set(v) = putB("dotsAutoHide", v)

    // ---- Clock / dock look
    var topAutoHide: Boolean get() = b("topAutoHide", true); set(v) = putB("topAutoHide", v)
    var topHideSec: Int get() = i("topHideSec", 2); set(v) = putI("topHideSec", v.coerceIn(2, 30))
    var cornerRadius: Int get() = i("cornerRadius", 18); set(v) = putI("cornerRadius", v.coerceIn(0, 40))
    /** Index of the Apps folder among the dock apps; -1 = after the last app. */
    var folderPos: Int get() = i("folderPos", -1); set(v) = putI("folderPos", v)
    /** Continue Watching: off | first (at the start of the carousel) | mixed (among the catalogs) */
    var continueMode: String get() = s("continueMode", "first"); set(v) = putS("continueMode", v)
    /** Continue Watching also from the Trakt account (when one is connected). */
    var cwTrakt: Boolean get() = b("cwTrakt", true); set(v) = putB("cwTrakt", v)
    /** After you leave the player, the carousel opens on what you just watched. */
    /** How many titles in progress open the carousel (0 = all); the rest mix in with the catalogs. */
    var cwLead: Int get() = i("cwLead", 3); set(v) = putI("cwLead", v)
    var continueJump: Boolean get() = b("continueJump", true); set(v) = putB("continueJump", v)
    var cwProgress: Boolean get() = b("cwProgress", true); set(v) = putB("cwProgress", v)
    /** 0.6 .. 1.0 (1.0 = full width) */
    var dockScale: Float get() = f("dockScale", 1.0f); set(v) = putF("dockScale", v.coerceIn(0.6f, 1.0f))
    /** Background of the dock and the Apps folder: liquid | clear | amoled | bow */
    var dockStyle: String get() = s("dockStyle", "water_still"); set(v) = putS("dockStyle", v)
    /** Background of settings and the icon picker: liquid | clear | amoled */
    var panelStyle: String get() = s("panelStyle", "liquid"); set(v) = putS("panelStyle", v)
    /** liquid | clear | amoled | bow (black on white) */
    var tileStyle: String get() = s("tileStyle", "water_still"); set(v) = putS("tileStyle", v)
    /** How far the glass rim bends the background, dp. */
    var refraction: Int get() = i("refraction", 14); set(v) = putI("refraction", v.coerceIn(0, 40))
    var metaScale: Float get() = f("metaScale", 1.0f); set(v) = putF("metaScale", v.coerceIn(0.6f, 2.0f))
    var genresScale: Float get() = f("genresScale", 1.0f); set(v) = putF("genresScale", v.coerceIn(0.6f, 2.0f))
    /** Opaque dark plate behind every icon, so transparent pack icons don't show the poster through. */
    var tileBacking: Boolean get() = b("tileBacking", true); set(v) = putB("tileBacking", v)
    /** package name of the selected icon pack, "" = none */
    var iconPack: String get() = s("iconPack", ""); set(v) = putS("iconPack", v)
    /** Weather in the top bar: off | temp | condition | city | all */
    var weatherMode: String get() = s("weatherMode", "off"); set(v) = putS("weatherMode", v)
    var weatherCity: String get() = s("weatherCity", "Baghdad"); set(v) = putS("weatherCity", v.trim())
    var weatherF: Boolean get() = b("weatherF", false); set(v) = putB("weatherF", v)
    var showDate: Boolean get() = b("showDate", false); set(v) = putB("showDate", v)
    /** tvOS look: icons are white until selected, then show their colours on a light plate. */
    var focusColor: Boolean get() = b("focusColor", true); set(v) = putB("focusColor", v)
    /** The selected icon rises a little, like tvOS. */
    /** Plate behind the selected icon in "Colour on focus": light | dark | glass | amoled (pure black) */
    var focusPlate: String get() = s("focusPlate", "dark"); set(v) = putS("focusPlate", v)
    /** Clock: left (Apple TV) | right (next to the settings button, weather under it) */
    var clockPos: String get() = s("clockPos", "right"); set(v) = putS("clockPos", v)
    /** Pressing further at the edge of the dock nudges the icon, like tvOS. */
    var edgeBounce: Boolean get() = b("edgeBounce", true); set(v) = putB("edgeBounce", v)
    var tileLift: Boolean get() = b("tileLift", false); set(v) = putB("tileLift", v)
    var showClock: Boolean get() = b("showClock", true); set(v) = putB("showClock", v)
    /** Large liquid-glass clock centered behind the home carousel. */
    var largeCenterClock: Boolean get() = b("largeCenterClock", true); set(v) = putB("largeCenterClock", v)
    /** Existing Glassio surface theme used by the large center clock. */
    var clockTheme: String get() = s("clockTheme", "water_still"); set(v) = putS("clockTheme", v)
    /** Global UI typeface: apple = Inter (SF-style), system = Android default. Reads the old clockFont preference for migration. */
    var uiFont: String get() = s("uiFont", s("clockFont", "apple")); set(v) = putS("uiFont", if (v == "system") "system" else "apple")
    var clock24h: Boolean get() = b("clock24h", false); set(v) = putB("clock24h", v)
    var blurRadius: Int get() = i("blurRadius", 4); set(v) = putI("blurRadius", v.coerceIn(0, 80))
    var glassTint: Int get() = i("glassTint", 0); set(v) = putI("glassTint", v.coerceIn(0, 40))
    var showMobileApps: Boolean get() = b("showMobileApps", true); set(v) = putB("showMobileApps", v)
    var dockOrder: List<String> get() = list("dockOrder"); set(v) = putList("dockOrder", v)
    var hiddenApps: List<String> get() = list("hiddenApps"); set(v) = putList("hiddenApps", v)
    /** The Apps folder tile in the dock. */
    var showFolder: Boolean get() = b("showFolder", true); set(v) = putB("showFolder", v)

    // ---- System
    var interceptHome: Boolean get() = b("interceptHome", false); set(v) = putB("interceptHome", v)
}
