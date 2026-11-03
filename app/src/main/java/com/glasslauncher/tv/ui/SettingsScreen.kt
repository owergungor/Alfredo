package com.glasslauncher.tv.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasslauncher.tv.GlassApp
import com.glasslauncher.tv.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface SRow
private class Toggle(val title: String, val desc: String, val get: () -> Boolean, val set: (Boolean) -> Unit) : SRow
private class Adjust(val title: String, val desc: String, val value: () -> String, val step: (Int) -> Unit) : SRow
/** [ok]: true shows the value in green with a check mark (set and working), false in red. */
private class Act(val title: String, val desc: String, val value: String = "", val ok: Boolean? = null, val onClick: () -> Unit) : SRow
private class Note(val text: String) : SRow
private class AppItem(val entry: AppEntry) : SRow

private enum class Kind { Rows, About }
private class Category(val title: String, val kind: Kind = Kind.Rows, val rows: @Composable () -> List<SRow> = { emptyList() })

private fun <T> cycle(list: List<T>, cur: T, d: Int): T {
    val i = list.indexOf(cur).let { if (it < 0) 0 else it }
    return list[(i + d).mod(list.size)]
}
private fun styleName(v: String) = when (v) { "clear" -> "White Glass"; "liquid" -> "Black Glass"; "bow" -> "White"; "amoled" -> "Black"; "water_still" -> "Clear"; else -> "White Glass" }
private fun themeName(v: String) = when (v) { "clear" -> "White Glass"; "liquid" -> "Black Glass"; "bow" -> "White"; "amoled" -> "Black"; "water_still" -> "Clear"; else -> "Custom" }
private fun hostOf(u: String) = runCatching { java.net.URI(u).host }.getOrNull() ?: "add-on"
private fun pct(f: Float) = "${(f * 100).roundToInt()}%"
private fun tenth(f: Float, d: Int) = ((f * 10).roundToInt() + d) / 10f

/** Google Play page of [pkg], or a Play search for [query]; falls back to the web page. */
private fun openPlay(ctx: Context, pkg: String?, query: String?) {
    val q = android.net.Uri.encode(query.orEmpty())
    val market = if (pkg != null) "market://details?id=$pkg" else "market://search?q=$q&c=apps"
    val web = if (pkg != null) "https://play.google.com/store/apps/details?id=$pkg" else "https://play.google.com/store/search?q=$q&c=apps"
    for (u in listOf(market, web)) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
        catch (_: Exception) {}
    }
    toast(ctx, "Google Play is not available on this device")
}

/** Is Alfredo's Home-button accessibility service switched on? */
private fun isHomeKeyOn(ctx: Context): Boolean = runCatching {
    AndroidSettings.Secure.getString(ctx.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        .contains("${ctx.packageName}/")
}.getOrDefault(false)

private fun openSystem(ctx: Context, action: String) {
    try { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: Exception) { toast(ctx, "This screen is not available on this device") }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(app: GlassApp, initialCat: Int, onClose: () -> Unit) {
    val s = LocalScale.current
    val ctx = LocalContext.current
    val st = app.settings
    val scope = rememberCoroutineScope()

    val storagePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            scope.launch(Dispatchers.IO) {
                toast(ctx, tr("Exporting backup…"))
                val res = com.glasslauncher.tv.data.BackupManager.exportBackup(ctx)
                when (res) {
                    is com.glasslauncher.tv.data.BackupManager.BackupResult.Success -> {
                        toast(ctx, "Exported: ${res.file.name}\nSHA-256: ${res.sha256.take(12)}…")
                    }
                    is com.glasslauncher.tv.data.BackupManager.BackupResult.Error -> {
                        toast(ctx, "Export failed: ${res.message}")
                    }
                }
            }
        } else {
            toast(ctx, tr("Storage permission denied. Cannot export backup to Downloads."))
        }
    }

    val restorePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            scope.launch(Dispatchers.IO) {
                val file = com.glasslauncher.tv.data.BackupManager.getPreferredBackupFile(ctx)
                if (!file.exists()) {
                    toast(ctx, "Backup file not found in Downloads: ${file.name}")
                    return@launch
                }
                toast(ctx, tr("Restoring backup…"))
                val res = com.glasslauncher.tv.data.BackupManager.importBackup(ctx, file)
                when (res) {
                    is com.glasslauncher.tv.data.BackupManager.BackupResult.Success -> {
                        toast(ctx, tr("Backup restored! Reloading…"))
                        st.reload()
                        app.apps.reload()
                    }
                    is com.glasslauncher.tv.data.BackupManager.BackupResult.Error -> {
                        toast(ctx, "Restore failed: ${res.message}")
                    }
                }
            }
        } else {
            toast(ctx, tr("Storage permission denied. Cannot read backup file."))
        }
    }
    val onExportBackup: () -> Unit = {
        val needsStoragePerm = Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsStoragePerm) {
            storagePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            scope.launch(Dispatchers.IO) {
                toast(ctx, tr("Exporting backup…"))
                val res = com.glasslauncher.tv.data.BackupManager.exportBackup(ctx)
                when (res) {
                    is com.glasslauncher.tv.data.BackupManager.BackupResult.Success -> {
                        toast(ctx, "Exported: ${res.file.name}\nSHA-256: ${res.sha256.take(12)}…")
                    }
                    is com.glasslauncher.tv.data.BackupManager.BackupResult.Error -> {
                        toast(ctx, "Export failed: ${res.message}")
                    }
                }
            }
        }
    }

    val onRestoreBackup: () -> Unit = {
        val needsReadPerm = Build.VERSION.SDK_INT <= 32 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsReadPerm) {
            restorePermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            scope.launch(Dispatchers.IO) {
                val file = com.glasslauncher.tv.data.BackupManager.getPreferredBackupFile(ctx)
                if (!file.exists()) {
                    toast(ctx, "Backup file not found in Downloads: ${file.name}")
                    return@launch
                }
                toast(ctx, tr("Restoring backup…"))
                val res = com.glasslauncher.tv.data.BackupManager.importBackup(ctx, file)
                when (res) {
                    is com.glasslauncher.tv.data.BackupManager.BackupResult.Success -> {
                        toast(ctx, tr("Backup restored! Reloading…"))
                        st.reload()
                        app.apps.reload()
                    }
                    is com.glasslauncher.tv.data.BackupManager.BackupResult.Error -> {
                        toast(ctx, "Restore failed: ${res.message}")
                    }
                }
            }
        }
    }

    val light = st.panelStyle == "bow" || st.theme == "bow"
    val ink = if (light) Color(0xFF111114) else Color.White
    val soft = ink.copy(alpha = if (light) 0.60f else 0.65f)

    val traktCode by app.cw.traktCode.collectAsState()
    val tvRev = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var homeKeyOn by remember { mutableStateOf(isHomeKeyOn(ctx)) }
    var adbAccess by remember { mutableStateOf<Boolean?>(null) }
    val allApps by app.apps.apps.collectAsState()
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    var cat by remember { mutableIntStateOf(initialCat) }
    var shownCat by remember { mutableIntStateOf(initialCat) }
    LaunchedEffect(cat) { if (cat != shownCat) { kotlinx.coroutines.delay(120); shownCat = cat } }
    var rightFocused by remember { mutableStateOf(false) }
    val firstCat = remember { FocusRequester() }
    // One requester per category: leaving the right pane goes back to the category it belongs to,
    // instead of whichever category happens to sit next to the row (that switched the page).
    val catFocus = remember { List(12) { FocusRequester() } }

    fun io(block: suspend () -> String) = scope.launch(Dispatchers.IO) {
        toast(ctx, runCatching { block() }.getOrElse { it.message ?: "Failed" })
    }
    val categories = listOf(
        Category("Performance") {
            fun custom() { st.perfMode = "custom" }
            listOf(
                Adjust("Mode", when (st.perfMode) {
                    "auto" -> "Picked for this TV from its memory and graphics chip"
                    "performance" -> "Smoothest: lighter pictures, quick fades, flat glass"
                    "quality" -> "Sharpest: original pictures, slow fades, full glass"
                    "balanced" -> "Sharp pictures, smooth navigation"
                    else -> "Your own mix of the settings below"
                }, {
                    fun n(m: String) = when (m) { "performance" -> "Performance"; "quality" -> "Quality"; "balanced" -> "Balanced"; else -> "Custom" }
                    if (st.perfMode == "auto") "Auto (${n(st.autoTier)})" else n(st.perfMode)
                }, { st.applyMode(cycle(listOf("auto", "performance", "balanced", "quality"), st.perfMode.takeIf { m -> m != "custom" } ?: "auto", it)) }),
                Adjust("Glass detail", "Resolution of the picture the glass bends (higher = crisper glass, more memory)", {
                    when (st.glassRes) { 720 -> "Sharp (720 px)"; else -> "Standard (480 px)" }
                }, { st.glassRes = cycle(listOf(480, 720), st.glassRes, it); custom() }),
                Adjust("Picture quality", "Carousel backdrops", { if (st.imageQuality == "high") "High (1280 px)" else "Maximum (original)" },
                    { st.imageQuality = cycle(listOf("high", "max"), st.imageQuality, it); custom() }),
                Adjust("Prepare ahead", "Slides loaded before you get to them", { "${st.prefetchDepth}" }, { st.prefetchDepth += it; custom() }),
            )
        },
        Category("Home screen") {
            listOf(
                Toggle("Auto-play", "Slides change on their own", { st.heroAutoplay }, { st.heroAutoplay = it }),
                Adjust("Slide time", "Seconds before the next title", { "${st.heroIntervalSec} s" }, { st.heroIntervalSec += it }),
                Adjust("Transition speed", "Fade between slides (lower = faster)", { "${st.transitionMs} ms" }, { st.transitionMs += it * 100 }),
                Adjust("Slide animation", "Apple TV: the next picture opens up from the right (from the left going back) · Slide: pushed in · Fade",
                    { when (st.slideAnim) { "apple" -> "Apple TV"; "slide" -> "Slide"; else -> "Fade" } },
                    { st.slideAnim = cycle(listOf("apple", "slide", "fade"), st.slideAnim, it) }),
                Adjust("Buttons", "Play + Next: like Apple TV, Right or OK on the round button shows the next title, Left on Play the previous one",
                    { when (st.heroButtons) { "apple" -> "Play + Next"; "play" -> "Play only"; else -> "Hidden" } },
                    { st.heroButtons = cycle(listOf("apple", "play", "hidden"), st.heroButtons, it) }),
                Toggle("Dots auto-hide", "Show them only when the carousel is selected", { st.dotsAutoHide }, { st.dotsAutoHide = it }),
                Adjust("Title style", "Logo image, plain text, or nothing", {
                    when (st.titleMode) { "logo" -> "Logo"; "text" -> "Text"; else -> "Hidden" }
                }, { st.titleMode = cycle(listOf("logo", "text", "hidden"), st.titleMode, it) }),
                Adjust("Logo / title size", "Logos are evened out so wide and tall ones look the same size", { pct(st.titleScale) }, { st.titleScale = tenth(st.titleScale, it) }),
                Adjust("Rating, year, platform size", "", { pct(st.metaScale) }, { st.metaScale = tenth(st.metaScale, it) }),
                Adjust("Genres size", "", { pct(st.genresScale) }, { st.genresScale = tenth(st.genresScale, it) }),
                Toggle("Rating, year, platform", "The chips under the title", { st.showMeta }, { st.showMeta = it }),
                Toggle("Genres line", "", { st.showGenres }, { st.showGenres = it }),
                Adjust("Dots style", "", {
                    when (st.dotsMode) { "window" -> "Dots"; "liquid" -> "Liquid glass dots"; "progress" -> "Progress bar"; else -> "Hidden" }
                }, { st.dotsMode = cycle(listOf("window", "liquid", "progress", "hidden"), st.dotsMode, it) }),
                Adjust("Dots per group", "", { "${st.dotsMax}" }, { st.dotsMax += it }),
                Adjust("Total titles", "0 = every title, endless loop", { if (st.maxItems == 0) "Unlimited" else "${st.maxItems}" }, { st.maxItems = (st.maxItems + it * 10).coerceAtLeast(0) }),
                Adjust("Pictures per title", "", { "${st.galleryShots}" }, { st.galleryShots = (st.galleryShots + it).coerceIn(2, 6) }),
                Note("One title: on the carousel hold OK (or press Menu) to switch to its next picture. Alfredo remembers it."),
                Act("Reset pictures picked per title", "", "") { st.clearArtPicks(); toast(ctx, "Every title is back to the Artwork setting") },
            )
        },
        Category("Summary") {
            listOf(
                Toggle("Show summary", "Show the description for the selected title", { st.showSynopsis }, { st.showSynopsis = it }),
                Toggle("Only when the carousel is selected", "Hide the summary while browsing apps", { st.synopsisOnFocus }, { st.synopsisOnFocus = it }),
                Adjust("Summary size", "", { pct(st.synopsisScale) }, { st.synopsisScale = tenth(st.synopsisScale, it) }),
                Adjust("Summary lines", "", { "${st.synopsisLines}" }, { st.synopsisLines = (st.synopsisLines + it).coerceIn(1, 12) }),
            )
        },
        Category("Clock & info") {
            listOf(
                Toggle("Large center glass clock", "Show the oversized glass clock on the home screen", { st.largeCenterClock }, { st.largeCenterClock = it }),
                Toggle("Show date", "Display the date above the clock", { st.showDate }, { st.showDate = it }),
                Toggle("24-hour clock", "Use 19:30 instead of 7:30 PM", { st.clock24h }, { st.clock24h = it }),
                Adjust("Clock style", "Material used by the clock", { styleName(st.clockTheme) },
                    { st.clockTheme = cycle(listOf("clear", "liquid", "bow", "amoled"), st.clockTheme, it) }),
                Adjust("Top-bar clock position", "Position of the small clock by Settings", { if (st.clockPos == "right") "Right, by Settings" else "Left, like Apple TV" },
                    { st.clockPos = cycle(listOf("left", "right"), st.clockPos, it) }),
                Adjust("Weather", "Top bar, next to the settings button", {
                    when (st.weatherMode) { "off" -> "Off"; "temp" -> "Temperature"; "condition" -> "Temperature + sky"; "city" -> "Temperature + city"; else -> "Everything" }
                }, { st.weatherMode = cycle(listOf("all", "condition", "city", "temp", "off"), st.weatherMode, it); app.weather.reload() }),
                Act("Weather city", st.weatherCity + app.weather.found.value.takeIf { it.isNotBlank() }?.let { "  →  $it" }.orEmpty(), "Change") {
                    dialog = TextEntry("Weather city", st.weatherCity, "Bursa") { st.weatherCity = it; app.weather.reload() }
                },
                Toggle("Fahrenheit", "", { st.weatherF }, { st.weatherF = it }),
                Toggle("Auto-hide clock and settings button", "Hidden while you browse; they appear when you go up to the settings button", { st.topAutoHide }, { st.topAutoHide = it }),
                Adjust("Shown at start for", "Then hidden until you go up to them", { "${st.topHideSec} s" }, { st.topHideSec += it }),
            )
        },
        Category("Appearance") {
            val bgFolder = CustomBackground.folder(ctx).absolutePath
            fun pick(video: Boolean) {
                val files = CustomBackground.files(ctx, video)
                if (files.isEmpty()) { toast(ctx, "No ${if (video) "videos" else "pictures"} yet. Copy them to: $bgFolder"); return }
                dialog = MenuDialog(if (video) "Videos in the folder" else "Pictures in the folder", files.map { f ->
                    f.name to { if (video) { st.bgVideo = f.absolutePath; st.bgMode = "video" } else { st.bgImage = f.absolutePath; st.bgMode = "image" } }
                })
            }
            listOf(
                Adjust("Theme", "One look for the dock, the icons and the menu. Black ones keep the dock clear (matte)",
                    { if (st.theme.isBlank()) "Custom" else themeName(st.theme) },
                    { st.applyTheme(cycle(listOf("clear", "liquid", "bow", "amoled"), st.theme.ifBlank { "clear" }, it)) }),
                Adjust("UI font", "Changes the typeface throughout Alfredo, including the clock and date", { if (st.uiFont == "apple") "Apple-style (Inter)" else "System" },
                    { st.uiFont = cycle(listOf("apple", "system"), st.uiFont, it) }),
                Adjust("Settings background", "Behind settings and the icon picker", { styleName(st.panelStyle) },
                    { st.panelStyle = cycle(listOf("clear", "liquid", "bow", "amoled"), st.panelStyle, it) }),
                Adjust("Icon style", "How the plate behind every icon looks", { styleName(st.tileStyle) },
                    { st.tileStyle = cycle(listOf("clear", "liquid", "bow", "amoled"), st.tileStyle, it); st.theme = "" }),
                Adjust("Background", "Each title's own picture, or yours: a picture or a video, from a link or the folder below",
                    { when (st.bgMode) { "image" -> "My picture"; "video" -> "My video"; else -> "Title backdrops" } },
                    { st.bgMode = cycle(listOf("carousel", "image", "video"), st.bgMode, it) }),
                Act("Picture from a link", st.bgImage.ifBlank { "https://… (jpg, png, webp)" }, "") {
                    dialog = TextEntry("Picture link", st.bgImage.takeIf { it.startsWith("http") }.orEmpty(), "https://…") { v -> if (v.isNotBlank()) { st.bgImage = v.trim(); st.bgMode = "image" } }
                },
                Act("Picture from the folder", "", "") { pick(false) },
                Adjust("Background rotation", "Cycles through folder pictures; requires at least two images. Links stay fixed.",
                    {
                        when (st.bgRotationMinutes) {
                            5 -> "5 minutes"
                            15 -> "15 minutes"
                            30 -> "30 minutes"
                            60 -> "1 hour"
                            else -> "Off"
                        }
                    },
                    { step ->
                        val options = listOf(0, 5, 15, 30, 60)
                        val index = options.indexOf(st.bgRotationMinutes).coerceAtLeast(0)
                        val next = (index + if (step >= 0) 1 else options.size - 1) % options.size
                        st.bgRotationMinutes = options[next]
                    }),
                Act("Video from a link", st.bgVideo.ifBlank { "https://… (mp4, webm, mkv); plays muted, on a loop" }, "") {
                    dialog = TextEntry("Video link", st.bgVideo.takeIf { it.startsWith("http") }.orEmpty(), "https://…") { v -> if (v.isNotBlank()) { st.bgVideo = v.trim(); st.bgMode = "video" } }
                },
                Act("Video from the folder", "", "") { pick(true) },
                Note("Folder for your pictures and videos: $bgFolder (copy with adb push, a file manager or a USB stick). The glass shows the picture; with a video it shows one frame of it."),
                Adjust("Corner radius", "Dock and app tiles (0 = square)", { "${st.cornerRadius}" }, { st.cornerRadius += it * 2 }),
                Adjust("Glass blur", "0 turns blur off", { "${st.blurRadius}" }, { st.blurRadius += it * 4 }),
                Adjust("Glass brightness", "", { "${st.glassTint}" }, { st.glassTint += it * 2 }),
                Toggle("Colour on focus", "Like tvOS: icons stay white, the selected one shows its colours on a light plate", { st.focusColor }, { st.focusColor = it }),
                Adjust("Selected icon plate", "With Colour on focus", { PLATE_NAMES[st.focusPlate] ?: "Dark" },
                    { st.focusPlate = cycle(PLATES, st.focusPlate, it) }),
                Toggle("Bounce at the edges", "Pressing past the first or last icon nudges it, like tvOS", { st.edgeBounce }, { st.edgeBounce = it }),
                Toggle("Lift selected icon", "The selected icon rises a little, glass and all", { st.tileLift }, { st.tileLift = it }),
            )
        },
        Category("Apps & Dock") {
            val imported = st.dockOrder.size // read for recomposition on reorder
            @Suppress("UNUSED_VARIABLE") val unused = imported + allApps.size
            buildList {
                add(Toggle("Show Apps folder", "The folder with all your apps, in the dock. Hidden apps stay listed below", { st.showFolder }, { st.showFolder = it }))
                add(Adjust("Dock size", "Smaller makes the dock and its icons narrower", { pct(st.dockScale) }, { st.dockScale = ((st.dockScale * 20).roundToInt() + it) / 20f }))
                add(Toggle("Show phone apps too", "Apps without a TV launcher entry", { st.showMobileApps }, { st.showMobileApps = it; app.apps.reload() }))
                add(Note("Change an app icon from the home screen using OK → Change icon, or import PNG icons below."))
                add(Note("Import custom icons by copying <package>.png files into the icon folder below, then selecting Import."))
                add(Note("On the home screen, hold OK on an app for App info, Move, Change icon and Hide."))
                add(Note("Here: OK picks an app up, ◀ ▶ moves it, OK puts it down. Hold OK (or Menu) for its options."))
                add(Act("Import icons from the icon folder", "", "") {
                    val n = app.icons.importDropFolder(app.apps.apps.value.map { it.pkg })
                    toast(ctx, if (n > 0) "Imported $n icons" else "No new icons. Folder: ${app.icons.dropFolder.absolutePath}")
                })
                add(Note("Folder for <package>.png files: ${app.icons.dropFolder.absolutePath}"))
                app.apps.ordered(includeHidden = true).forEach { add(AppItem(it)) }
            }
        },
        Category("Menu & Library") {
            listOf(
                Adjust("Menu", "The Apple TV-style menu at the settings button",
                    { when (st.menuMode) { "auto" -> "Opens when you go up to it"; "manual" -> "Opens with OK"; else -> "Off (OK opens Settings)" } },
                    { st.menuMode = cycle(listOf("auto", "manual", "off"), st.menuMode, it) }),
                Toggle("Clock in the menu", "", { st.menuClock }, { st.menuClock = it }),
                Toggle("Date in the menu", "", { st.menuDate }, { st.menuDate = it }),
                Toggle("Weather in the menu", "Temperature, conditions, high / low, humidity and wind (city in Clock & glass)", { st.menuWeather }, { st.menuWeather = it; app.weather.reload() }),
                Toggle("City in the menu", "", { st.menuCity }, { st.menuCity = it }),
                Toggle("Library in the menu", "", { st.menuLibrary }, { st.menuLibrary = it }),
                Toggle("Android settings in the menu", "", { st.menuAndroid }, { st.menuAndroid = it }),
                Toggle("Wi-Fi in the menu", "", { st.menuWifi }, { st.menuWifi = it }),
                Toggle("Themes in the menu", "", { st.menuThemes }, { st.menuThemes = it }),
                Toggle("Background in the menu", "Switch between title backdrops, your picture and your video", { st.menuBackground }, { st.menuBackground = it }),
                Toggle("+ button on the carousel", "Between Play and Next: saves the title to your Library", { st.heroPlus }, { st.heroPlus = it }),
                Toggle("Trakt watchlist in the Library", "Needs Trakt (Continue Watching)", { st.libraryTrakt }, { st.libraryTrakt = it; scope.launch(Dispatchers.IO) { app.library.refreshWatchlist() } }),
                Toggle("Save to the Trakt watchlist too", "The + button also adds the title on Trakt", { st.libraryToTrakt }, { st.libraryToTrakt = it }),
                Toggle("Titles under posters", "In the Library", { st.libraryTitles }, { st.libraryTitles = it }),
                Note("In the Library: OK opens the title like the carousel. Hold OK (or Menu) to remove it."),
            )
        },
        Category("Continue") {
            val cwList by app.cw.entries.collectAsState()
            buildList {
                add(Adjust("Show Continue Watching", "Where titles you are watching appear", {
                    when (st.continueMode) { "off" -> "Off"; "mixed" -> "Mixed with catalogs"; else -> "At the start" }
                }, { st.continueMode = cycle(listOf("first", "mixed", "off"), st.continueMode, it) }))
                if (st.continueMode == "first") add(Adjust("Slides at the start", "How many titles in progress open the carousel", {
                    if (st.cwLead <= 0) "All" else "${st.cwLead}"
                }, { st.cwLead = cycle(listOf(1, 2, 3, 4, 5, 0), st.cwLead, it) }))
                add(Toggle("Open on what I just watched", "Return to the title most recently reported as watched", { st.continueJump }, { st.continueJump = it }))
                add(Toggle("Progress bar", "Shows Continue · S1 · E3 · 42% under the title", { st.cwProgress }, { st.cwProgress = it }))
                val trakt = app.cw.trakt
                val tStatus by app.cw.traktStatus.collectAsState()
                if (trakt.available) {
                    add(Toggle("Trakt", if (trakt.connected) "Signed in as ${trakt.user.ifBlank { "your account" }}. What you pause in apps that support Trakt" + (if (tStatus.isNotBlank()) " · $tStatus" else "")
                        else "Connect your account below to see what you paused in any app that uses Trakt", { st.cwTrakt }, { st.cwTrakt = it; app.cw.syncTraktNow() }))
                    if (!trakt.connected) add(Act("Connect Trakt account", traktCode?.let { "Enter $it at ${app.cw.traktUrl.removePrefix("https://")}" } ?: "Scan a QR code with your phone and enter a short code, no password on the TV", if (traktCode != null) "Waiting…" else "") {
                        val appCtx = ctx.applicationContext
                        app.cw.connectTrakt { msg -> toast(appCtx, msg) }
                        scope.launch {
                            // Wait for the code, then show it with a QR code of the sign-in page.
                            var n = 0
                            while (app.cw.traktCode.value == null && n++ < 50) kotlinx.coroutines.delay(100)
                            if (app.cw.traktCode.value != null) dialog = QrCodeDialog("Connect Trakt", app.cw.traktUrl, app.cw.traktCode,
                                "This closes by itself once you approve. The code works for about 10 minutes.")
                        }
                    })
                    else add(Act("Sign out of Trakt", trakt.user, "") { trakt.signOut(); st.cwTrakt = st.cwTrakt; toast(ctx, "Signed out of Trakt") })
                }
                add(Note("Other apps (YouTube, Netflix, Apple TV…) keep their Continue Watching private on Google TV; only the system launcher may read it."))
                add(Act("Clear the list", "${cwList.size} titles", "") { app.cw.clear() })
                cwList.forEach { e ->
                    add(Act(e.imdb + if (e.season > 0) "  S${e.season}E${e.episode}" else "", "${e.app} · ${e.percent}%", "Remove") { app.cw.remove(e.key) })
                }
            }
        },
        Category("System") {
            val devOn = runCatching { AndroidSettings.Global.getInt(ctx.contentResolver, AndroidSettings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }.getOrDefault(false)
            val adbOn = runCatching { AndroidSettings.Global.getInt(ctx.contentResolver, AndroidSettings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)
            listOf(
                Toggle("Home button opens Alfredo", "Needs the accessibility service below", { st.interceptHome }, { st.interceptHome = it; if (it) app.ensureHomeService() }),
                Act("Turn on the accessibility service", "Find Alfredo and switch it on", "") { openSystem(ctx, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS) },
                Act("Turn it on with ADB", if (homeKeyOn) "The accessibility service is on" else "When the TV doesn't let you (restricted setting / greyed out). Needs TV access (ADB), below", "") {
                    scope.launch(Dispatchers.IO) {
                        val svc = "${ctx.packageName}/com.glasslauncher.tv.HomeKeyService"
                        val cmd = "cur=\$(settings get secure enabled_accessibility_services); " +
                            "case \"\$cur\" in *$svc*) ;; null|'') settings put secure enabled_accessibility_services $svc ;; " +
                            "*) settings put secure enabled_accessibility_services \"\$cur:$svc\" ;; esac; " +
                            "settings put secure accessibility_enabled 1; appops set ${ctx.packageName} ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null; " +
                            // Lets Alfredo switch the service back on by itself after every update.
                            "pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; true"
                        val r = com.glasslauncher.tv.data.LocalShell.run(ctx, cmd)
                        homeKeyOn = isHomeKeyOn(ctx)
                        toast(ctx, if (r.isSuccess && homeKeyOn) "Accessibility service turned on" else if (r.isSuccess) "Done, but the TV didn't turn it on. Try the button above" else "Allow TV access (ADB) first: steps below")
                    }
                },
                Act("Choose default home app", "", "") { openSystem(ctx, AndroidSettings.ACTION_HOME_SETTINGS) },
                Adjust("Language", "Choose the interface language", { if (st.language == "tr") "Türkçe" else "English" }, {
                    st.language = if (st.language == "tr") "en" else "tr"
                    UiStrings.setLanguage(st.language)
                }),
                Note("TV access (ADB): silent updates and restricted system actions use the TV's debugging connection. Turn it on once:"),
                Note("1. Developer options: open About below and press OK on \"Android TV OS build\" (or \"Build\") 7 times, until it says you are a developer."),
                Note("2. Open Developer options below and turn on USB debugging (on some TVs also \"Network debugging\" / \"ADB over network\")."),
                Note("3. Press \"Allow Alfredo\" below. The TV asks \"Allow USB debugging?\": tick Always allow from this computer, then OK. It is asked only once."),
                Act("1 · Open About (enable Developer options)", if (devOn) "Developer options are on" else "Press OK on the build number 7 times", "") { openSystem(ctx, AndroidSettings.ACTION_DEVICE_INFO_SETTINGS) },
                Act("2 · Open Developer options", if (adbOn) "USB debugging is on" else "Turn on USB debugging", "") { openSystem(ctx, AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
                Act("3 · Allow Alfredo", when (adbAccess) { true -> "Allowed: Alfredo can use ADB"; false -> "Not allowed yet: press OK, then Always allow on the TV"; else -> "Press OK to check" }, "") {
                    scope.launch(Dispatchers.IO) {
                        val ok = com.glasslauncher.tv.data.LocalShell.run(ctx, "echo ok").isSuccess
                        adbAccess = ok
                        toast(ctx, if (ok) "Alfredo can use ADB" else if (!adbOn) "Turn on USB debugging first (step 2)" else "Choose Always allow on the TV, then press OK here again")
                    }
                },
            )
        },
        Category("About", Kind.About),

    )

    BackHandler { if (rightFocused) runCatching { catFocus[cat].requestFocus() } else onClose() }
    LaunchedEffect(Unit) { delay150(); runCatching { firstCat.requestFocus() } }

    Box(Modifier.fillMaxSize().background((if (light) Color.White else Color.Black).copy(alpha = 0.55f)).padding((28 * s).dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy((16 * s).dp)) {
            GlassPanel(Modifier.width((230 * s).dp).fillMaxHeight(), (30 * s).dp, style = st.panelStyle, darken = if (light) 0f else 0.42f) {
                // Scrolls when the list is taller than the screen (the last entry, About, was cut off).
                Column(Modifier.padding(horizontal = (14 * s).dp, vertical = (12 * s).dp).verticalScroll(rememberScrollState())) {
                    Text(tr("Settings"), color = ink, fontSize = (22 * s).sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = (8 * s).dp, bottom = (8 * s).dp))
                    categories.forEachIndexed { i, c ->
                        var f by remember { mutableStateOf(false) }
                        Text(
                            tr(c.title), color = if (cat == i) ink else soft, fontSize = (13.5f * s).sp,
                            fontWeight = if (cat == i) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().padding(vertical = (1.5f * s).dp)
                                .clip(RoundedCornerShape((14 * s).dp))
                                .background(if (f) (if (light) Color.Black.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.30f)) else if (cat == i) (if (light) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)) else Color.Transparent)
                                .focusRequester(catFocus[i])
                                .then(if (i == initialCat) Modifier.focusRequester(firstCat) else Modifier)
                                .onFocusChanged { f = it.isFocused; if (it.isFocused) cat = i }
                                .clickable { cat = i }
                                .padding(horizontal = (12 * s).dp, vertical = (7.5f * s).dp),
                        )
                    }
                }
            }
            GlassPanel(
                Modifier.weight(1f).fillMaxHeight().onFocusChanged { rightFocused = it.hasFocus }
                    // Left goes back to this page's own category; Up/Down at the ends stay in the page.
                    .focusProperties {
                        exit = { dir ->
                            when (dir) {
                                androidx.compose.ui.focus.FocusDirection.Left -> catFocus[cat]
                                androidx.compose.ui.focus.FocusDirection.Up, androidx.compose.ui.focus.FocusDirection.Down -> FocusRequester.Cancel
                                else -> FocusRequester.Default
                            }
                        }
                    }
                    .focusGroup(),
                (30 * s).dp, style = st.panelStyle, darken = if (light) 0f else 0.42f,
            ) {
                val c = categories[shownCat]
                Column(Modifier.padding((18 * s).dp)) {
                    Text(tr(c.title), color = ink, fontSize = (20 * s).sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = (6 * s).dp, bottom = (8 * s).dp))
                    if (c.kind == Kind.About) AboutPane(app, s, light = light, ink = ink, soft = soft, onExport = onExportBackup, onRestore = onRestoreBackup, onApp = { e -> appMenu(app, e, ctx, scope) { dialog = it } })
                    else {
                        val rows = c.rows()
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy((4 * s).dp)) {
                            items(rows.size) { i -> RowView(rows[i], s, app, onApp = { e -> appMenu(app, e, ctx, scope) { dialog = it } }, light = light, ink = ink, soft = soft) }
                        }
                    }
                }
            }
        }
    }
    DialogHost(dialog) { dialog = null }
}

private suspend fun delay150() = kotlinx.coroutines.delay(150)

private fun appMenu(app: GlassApp, e: AppEntry, ctx: Context, scope: kotlinx.coroutines.CoroutineScope, show: (DialogState) -> Unit) {
    val hidden = e.pkg in app.settings.hiddenApps
    val opts = mutableListOf<Pair<String, () -> Unit>>()
    opts += (if (hidden) "Show in dock" else "Hide from dock") to { app.apps.toggleHidden(e.pkg) }
    opts += "Icon from a link" to {
        show(TextEntry("Icon link for ${e.label}", "", "https://…/icon.png") { u ->
            scope.launch(Dispatchers.IO) { toast(ctx, runCatching { app.icons.setFromUrl(e.pkg, u.trim()); "Icon updated" }.getOrElse { it.message ?: "Failed" }) }
        })
    }
    if (app.icons.has(e.pkg)) opts += "Reset to the original icon" to { app.icons.reset(e.pkg) }
    opts += "Open app" to { app.apps.launchIntent(e)?.let { runCatching { ctx.startActivity(it) } } }
    show(MenuDialog(e.label, opts))
}

@Composable
private fun RowView(row: SRow, s: Float, app: GlassApp, onApp: (AppEntry) -> Unit, light: Boolean = false, ink: Color = Color.White, soft: Color = Color.White.copy(alpha = 0.65f)) {
    when (row) {
        is Note -> Text(tr(row.text), color = soft, fontSize = (11.5f * s).sp, modifier = Modifier.padding(horizontal = (8 * s).dp, vertical = (6 * s).dp))
        is Toggle -> Frame(s, light = light, onClick = { row.set(!row.get()) }) { f ->
            Label(tr(row.title), tr(row.desc), s, Modifier.weight(1f), ink = ink, soft = soft)
            val on = row.get()
            Box(Modifier.width((38 * s).dp).height((22 * s).dp).clip(RoundedCornerShape(50)).background(if (on) Color(0xFF7B61FF) else if (light) Color.Black.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.22f)).padding((3 * s).dp)) {
                Box(Modifier.size((16 * s).dp).clip(RoundedCornerShape(50)).background(if (light && !on) Color(0xFF444444) else Color.White).align(if (on) Alignment.CenterEnd else Alignment.CenterStart))
            }
        }
        is Adjust -> {
            var editing by remember { mutableStateOf(false) }
            Frame(s, light = light, editing = editing, onKey = { ev ->
                when {
                    !editing -> false
                    ev.key == Key.DirectionLeft || ev.key == Key.DirectionRight -> {
                        if (ev.type == KeyEventType.KeyDown) row.step(if (ev.key == Key.DirectionLeft) -1 else 1); true
                    }
                    ev.key == Key.Back -> { if (ev.type == KeyEventType.KeyUp) editing = false; true }
                    // Up/Down while editing: stay on the row
                    ev.key == Key.DirectionUp || ev.key == Key.DirectionDown -> true
                    else -> false
                }
            }, onFocus = { if (!it) editing = false }, onClick = { editing = !editing }) { f ->
                Label(tr(row.title), tr(if (editing) "◀ ▶ to change · OK when done" else row.desc), s, Modifier.weight(1f), ink = ink, soft = soft)
                if (editing) Text(tr("‹  ${tr(row.value())}  ›"), color = if (light) Color(0xFF5B3DE6) else Color(0xFFB9ABFF), fontSize = (13.5f * s).sp, fontWeight = FontWeight.SemiBold)
                else Text(tr(row.value()), color = ink.copy(alpha = if (f) 1f else 0.75f), fontSize = (13 * s).sp)
            }
        }
        is Act -> Frame(s, light = light, onClick = row.onClick) { f ->
            Label(tr(row.title), tr(row.desc), s, Modifier.weight(1f), ink = ink, soft = soft)
            if (row.value.isNotEmpty()) Text(tr(if (row.ok == true) "✓  " + row.value else row.value),
                color = when (row.ok) { true -> Color(0xFF34C759); false -> Color(0xFFFF6961); else -> ink.copy(alpha = 0.7f) },
                fontSize = (12.5f * s).sp, fontWeight = if (row.ok != null) FontWeight.SemiBold else null)
        }
        is AppItem -> {
            val e = row.entry
            val hidden = e.pkg in app.settings.hiddenApps
            val art by produceState<ImageBitmap?>(null, e.pkg) { value = app.apps.artwork(e)?.bmp }
            var moving by remember { mutableStateOf(false) }
            Frame(s, light = light, editing = moving, onKey = { ev ->
                when {
                    ev.key == Key.Menu -> { if (ev.type == KeyEventType.KeyDown) onApp(e); true }
                    !moving -> false
                    ev.key == Key.DirectionLeft || ev.key == Key.DirectionRight -> {
                        if (ev.type == KeyEventType.KeyDown) app.apps.move(e.pkg, if (ev.key == Key.DirectionLeft) -1 else 1); true
                    }
                    ev.key == Key.Back -> { if (ev.type == KeyEventType.KeyUp) moving = false; true }
                    ev.key == Key.DirectionUp || ev.key == Key.DirectionDown -> true
                    else -> false
                }
            }, onFocus = { if (!it) moving = false }, onClick = { moving = !moving }, onLong = { onApp(e) }) { f ->
                Box(Modifier.size((34 * s).dp).clip(RoundedCornerShape((8 * s).dp)).background(if (light) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    art?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding((3 * s).dp)) }
                }
                Spacer(Modifier.width((10 * s).dp))
                Label(e.label, e.pkg, s, Modifier.weight(1f), ink = ink, soft = soft)
                val flags = listOfNotNull(if (hidden) "Hidden" else null, if (app.icons.has(e.pkg)) "Custom icon" else null).joinToString(" · ")
                if (flags.isNotEmpty()) Text(tr(flags), color = if (light) Color(0xFF5B3DE6) else Color(0xFFB9ABFF), fontSize = (11.5f * s).sp)
                Text(tr(if (moving) "  ◀ ▶ · OK" else ""), color = if (light) Color(0xFF5B3DE6) else Color(0xFFB9ABFF), fontSize = (12 * s).sp)
            }
        }
    }
}

@Composable
private fun Label(title: String, desc: String, s: Float, modifier: Modifier, ink: Color = Color.White, soft: Color = Color.White.copy(alpha = 0.5f)) {
    Column(modifier) {
        Text(tr(title), color = ink, fontSize = (14 * s).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (desc.isNotBlank()) Text(tr(desc), color = soft, fontSize = (10.5f * s).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Frame(
    s: Float,
    light: Boolean = false,
    onKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    onClick: () -> Unit,
    onLong: (() -> Unit)? = null,
    onFocus: (Boolean) -> Unit = {},
    editing: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.(Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
            .clip(RoundedCornerShape((14 * s).dp))
            .background(when {
                editing -> Color(0xFF7B61FF).copy(alpha = if (light) 0.25f else 0.34f)
                focused -> if (light) Color.Black.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.26f)
                else -> if (light) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.06f)
            })
            .tvPress(onLong = onLong, onKey = onKey, onClick = onClick)
            .padding(horizontal = (14 * s).dp, vertical = (9 * s).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content(focused) }
}

/** Version, updates from GitHub, the project page as a QR code, credits and the last crash (if any). */
@Composable
private fun AboutPane(
    app: GlassApp,
    s: Float,
    light: Boolean,
    ink: Color,
    soft: Color,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onApp: (com.glasslauncher.tv.data.AppEntry) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val rel by app.updater.available.collectAsState()
    val status by app.updater.status.collectAsState()
    val qr = remember { com.glasslauncher.tv.net.Net.qr(com.glasslauncher.tv.data.Updater.REPO_URL, 420).asImageBitmap() }
    val crash = remember { java.io.File(ctx.filesDir, "last-crash.txt").takeIf { it.exists() }?.readText()?.lineSequence()?.take(2)?.joinToString(" · ").orEmpty() }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy((18 * s).dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(qr, "GitHub", Modifier.size((150 * s).dp).clip(RoundedCornerShape((12 * s).dp)))
            Spacer(Modifier.height((6 * s).dp))
            Text(tr("github.com/" + com.glasslauncher.tv.data.Updater.REPO), color = soft, fontSize = (9.5f * s).sp)
            Text(tr("Scan with your phone"), color = soft.copy(alpha = 0.7f), fontSize = (9 * s).sp)
        }
        val rows = buildList<SRow> {
            add(Note("Alfredo ${app.updater.current}"))
            if (rel != null) {
                add(Act("Install update ${rel!!.version}", if (status.isNotBlank()) status else "Downloads from GitHub and restarts Alfredo", "Install") { app.updater.install() })
                rel!!.notes.lineSequence().map { it.trim().removePrefix("-").removePrefix("*").trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
                    .take(6).forEach { add(Note("• $it")) }
            } else add(Act("Check for updates", if (status.isNotBlank()) status else "Looks at the latest release on GitHub", "Check") {
                scope.launch(Dispatchers.IO) { toast(ctx, app.updater.check()) }
            })
            add(Note("A pill appears next to the clock when a new version is out."))
            add(Act("Export backup", "Saves settings, dock and custom media to Downloads", "Export", onClick = onExport))
            add(Act("Restore backup", "Restores settings from alfredo_backup.zip in Downloads", "Restore", onClick = onRestore))
            add(Note("Backup files are saved as ZIP in Downloads. Copy them to a computer or flash drive before reinstalling."))
            add(Note("made by crius (owergungor)"))
            add(Note("Fonts: Inter (SIL OFL) · Weather: Open-Meteo"))
            if (crash.isNotBlank()) add(Act("Last crash", crash.take(120), "Clear") { java.io.File(ctx.filesDir, "last-crash.txt").delete(); toast(ctx, "Cleared") })
        }
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy((4 * s).dp)) {
            items(rows.size) { i -> RowView(rows[i], s, app, onApp = onApp, light = light, ink = ink, soft = soft) }
        }
    }
}
