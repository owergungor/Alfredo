package com.glasslauncher.tv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Library: titles saved with the + button on the carousel (kept on the TV), and, when a Trakt account
 * is connected, its watchlist. Saving can also add the title to the Trakt watchlist.
 */
class Library(
    ctx: Context,
    private val settings: Settings,
    private val http: Http,
    private val trakt: Trakt,
    private val scope: CoroutineScope,
) {
    private val sp = ctx.getSharedPreferences("glass_library", Context.MODE_PRIVATE)
    val saved = MutableStateFlow(load())
    val watchlist = MutableStateFlow<List<HeroItem>>(emptyList())
    /** "" or why the watchlist could not be read. */
    val status = MutableStateFlow("")

    fun has(key: String) = saved.value.any { it.key == key }

    /** Saves or unsaves [item]; returns true when it is now in the Library. */
    fun toggle(item: HeroItem): Boolean {
        val now = !has(item.key)
        val l = if (now) listOf(item.copy(progress = -1f, sub = "", gallery = emptyList())) + saved.value.filterNot { it.key == item.key }
        else saved.value.filterNot { it.key == item.key }
        saved.value = l; save(l)
        if (settings.libraryToTrakt && trakt.connected && item.id.startsWith("tt")) scope.launch(Dispatchers.IO) {
            if (trakt.setWatchlist(now, item.type.lowercase(), item.id)) refreshWatchlist()
        }
        return now
    }

    fun remove(key: String) { val l = saved.value.filterNot { it.key == key }; saved.value = l; save(l) }

    fun removeFromWatchlist(item: HeroItem) {
        watchlist.value = watchlist.value.filterNot { it.key == item.key }
        scope.launch(Dispatchers.IO) { trakt.setWatchlist(false, item.type, item.id) }
    }

    /** Reads the Trakt watchlist and turns it into posters (Cinemeta, cached a week). Blocking. */
    fun refreshWatchlist() {
        if (!settings.libraryTrakt || !trakt.connected) { watchlist.value = emptyList(); return }
        val list = runCatching { trakt.watchlist() }.getOrElse { Glog.w("Trakt watchlist", it); status.value = "Trakt: ${it.message}"; return }
        status.value = ""
        watchlist.value = list.take(120).mapNotNull { meta(it.type, it.imdb) }
    }

    private fun meta(type: String, imdb: String): HeroItem? = runCatching {
        val m = http.json("https://v3-cinemeta.strem.io/meta/$type/$imdb.json", 7L * 24 * 3600 * 1000).optJSONObject("meta") ?: return null
        fun str(k: String) = m.optString(k).takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        HeroItem(
            key = "$type:$imdb", id = imdb, type = type, name = str("name"), poster = str("poster"),
            background = str("background").ifBlank { str("poster") }, logo = str("logo"), overview = str("description"),
            rating = "", year = Regex("(19|20)\\d{2}").find(str("releaseInfo"))?.value.orEmpty(), genres = emptyList(), platform = "",
        )
    }.getOrNull()

    private fun load(): List<HeroItem> = runCatching {
        val a = JSONArray(sp.getString("items", null) ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            HeroItem(o.getString("key"), o.getString("id"), o.optString("type"), o.optString("name"), o.optString("poster"),
                o.optString("background"), o.optString("logo"), o.optString("overview"), o.optString("rating"), o.optString("year"),
                emptyList(), "")
        }
    }.getOrDefault(emptyList())

    private fun save(l: List<HeroItem>) {
        val a = JSONArray()
        l.take(500).forEach {
            a.put(JSONObject().put("key", it.key).put("id", it.id).put("type", it.type).put("name", it.name).put("poster", it.poster)
                .put("background", it.background).put("logo", it.logo).put("overview", it.overview).put("rating", it.rating).put("year", it.year))
        }
        sp.edit().putString("items", a.toString()).apply()
    }
}
