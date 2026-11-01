package com.glasslauncher.tv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** One title being watched. season/episode are 0 for movies. */
data class CwEntry(val imdb: String, val season: Int, val episode: Int, val percent: Int, val at: Long, val app: String) {
    val type: String get() = if (season > 0 || episode > 0) "series" else "movie"
    val key: String get() = "$type:$imdb"
}

/** Continue Watching sourced from the user's Trakt playback history. */
class ContinueWatching(
    private val ctx: Context,
    private val settings: Settings,
    private val http: Http,
    private val scope: CoroutineScope,
) {
    private val sp = ctx.getSharedPreferences("glass_cw", Context.MODE_PRIVATE)
    val entries = MutableStateFlow(load())
    val items = MutableStateFlow<List<HeroItem>>(emptyList())
    /** Key of the title you were watching last; bumped when you leave the player (carousel jumps to it). */
    val lastWatched = MutableStateFlow<String?>(null)
    val jumpRev = MutableStateFlow(0)

    @Volatile private var started = false

    val trakt = Trakt(ctx)
    @Volatile private var traktAt = 0L
    @Volatile private var woke = false
    /** Last Trakt sync result for Settings ("12 titles", or the error). */
    val traktStatus = MutableStateFlow("")
    /** Released to wake the poll loop early (Glass came back to the front). */
    private val wake = java.util.concurrent.Semaphore(0)

    fun start() {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO) { rebuildItems() }
        Thread({
            while (true) {
                if (settings.cwTrakt && settings.continueMode != "off" && trakt.connected &&
                    System.currentTimeMillis() - traktAt > (if (woke) 45_000L else 180_000L)) {
                    traktAt = System.currentTimeMillis()
                    syncTrakt()
                }
                woke = false
                runCatching { wake.tryAcquire(20, java.util.concurrent.TimeUnit.SECONDS) }
                wake.drainPermits()
            }
        }, "glass-cw-trakt").apply { isDaemon = true; start() }
    }

    /** Wake the Trakt poll loop when Glassio returns to the foreground. */
    fun pollNow() { woke = true; wake.release() }

    /** Trakt sign-in in progress: the line to show ("Go to … and enter CODE"), null when idle. */
    val traktCode = MutableStateFlow<String?>(null)
    @Volatile var traktUrl: String = "https://trakt.tv/activate"
    @Volatile private var signIn: kotlinx.coroutines.Job? = null

    /** Starts the device sign-in; it keeps going when you leave Settings. [say] shows a short message. */
    fun connectTrakt(say: (String) -> Unit) {
        if (signIn?.isActive == true) return
        signIn = scope.launch(Dispatchers.IO) {
            val dc = runCatching { trakt.deviceCode() }.getOrElse { say("Trakt: ${it.message}"); return@launch }
            traktUrl = dc.url
            traktCode.value = dc.userCode
            val ok = trakt.waitForApproval(dc) { signIn?.isCancelled == true }
            traktCode.value = null
            say(if (ok) "Trakt connected" + (trakt.user.takeIf { it.isNotBlank() }?.let { " as $it" } ?: "") else "Trakt: ${trakt.lastError}. Try again")
            if (ok) { settings.cwTrakt = true; syncTraktNow() }
        }
    }

    /** Forces a Trakt sync on the next round (after signing in). */
    fun syncTraktNow() { traktAt = 0L; pollNow() }

    private fun traktIds(): JSONObject = runCatching { JSONObject(sp.getString("traktIds", null) ?: "{}") }.getOrDefault(JSONObject())

    /** Rebuild Continue Watching from currently paused Trakt items. */
    private fun syncTrakt() {
        val paused = runCatching { trakt.playback() }.getOrElse { Glog.w("Trakt sync", it); traktStatus.value = "Sync failed: ${it.message}"; return }
        synchronized(this) {
            val ids = JSONObject()
            val list = mutableListOf<CwEntry>()
            for (p in paused) {
                if (p.percent >= 92) continue
                val e = CwEntry(p.imdb, p.season, p.episode, p.percent, p.at, "Trakt")
                val other = list.firstOrNull { it.imdb == p.imdb }
                if (other != null && other.at >= p.at) continue
                list.removeAll { it.imdb == p.imdb }
                list.add(e)
                ids.put(e.key, p.id)
            }
            val trimmed = list.sortedByDescending { it.at }.take(30)
            entries.value = trimmed
            save(trimmed)
            lastWatched.value = trimmed.firstOrNull()?.key
            jumpRev.value = jumpRev.value + 1
            sp.edit().putString("traktIds", ids.toString()).apply()
        }
        traktStatus.value = "${paused.size} paused on Trakt"
        rebuildItems()
    }

    fun clear() { entries.value = emptyList(); save(emptyList()); items.value = emptyList() }

    fun remove(key: String) {
        if (entries.value.any { it.key == key && it.app == "Trakt" }) {
            val id = traktIds().optLong(key, 0L)
            if (id > 0) scope.launch(Dispatchers.IO) { trakt.removePlayback(id) }
        }
        val l = entries.value.filterNot { it.key == key }
        entries.value = l; save(l)
        scope.launch(Dispatchers.IO) { rebuildItems() }
    }

    /** Turns entries into carousel items using Cinemeta (name, art, logo, synopsis, rating). */
    private fun rebuildItems() {
        val out = entries.value.filter { it.percent >= 2 }.mapNotNull { e ->
            runCatching {
                val meta = http.json("https://v3-cinemeta.strem.io/meta/${e.type}/${e.imdb}.json", 7L * 24 * 3600 * 1000).optJSONObject("meta")
                    ?: return@runCatching null
                fun str(k: String) = meta.optString(k).takeIf { it.isNotBlank() && it != "null" }.orEmpty()
                val rating = meta.optString("imdbRating").toDoubleOrNull()?.let { String.format(Locale.US, "%.1f", it) }.orEmpty()
                val year = Regex("(19|20)\\d{2}").find(str("releaseInfo").ifBlank { str("year") })?.value.orEmpty()
                val genres = meta.optJSONArray("genres")?.let { a -> (0 until minOf(3, a.length())).map { a.optString(it) } }.orEmpty()
                val sub = if (e.type == "series") "S${e.season} · E${e.episode}" else ""
                HeroItem(
                    key = e.key, id = e.imdb, type = e.type, name = str("name"),
                    poster = str("poster"), background = str("background").ifBlank { str("poster") }, logo = str("logo"),
                    overview = str("description"), rating = rating, year = year, genres = genres, platform = "",
                    progress = e.percent / 100f, sub = sub,
                )
            }.getOrNull()
        }
        items.value = out
    }

    private fun load(): List<CwEntry> = runCatching {
        val a = JSONArray(sp.getString("entries", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            CwEntry(o.getString("imdb"), o.optInt("s"), o.optInt("e"), o.optInt("p"), o.optLong("at"), o.optString("app"))
        }.filter { it.app == "Trakt" }
    }.getOrDefault(emptyList())

    private fun save(l: List<CwEntry>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("imdb", it.imdb).put("s", it.season).put("e", it.episode).put("p", it.percent).put("at", it.at).put("app", it.app)) }
        sp.edit().putString("entries", a.toString()).apply()
    }
}
