package com.example.movielive

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLEncoder

enum class Kind { MOVIE, TV }

data class Item(
    val id: Int, val kind: Kind, val title: String,
    val poster: String?, val backdrop: String?, val overview: String, val year: String,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("kind", kind.name).put("title", title)
        .put("poster", poster ?: "").put("backdrop", backdrop ?: "")
        .put("overview", overview).put("year", year)

    companion object {
        fun fromJson(o: JSONObject) = Item(
            o.getInt("id"), Kind.valueOf(o.getString("kind")), o.getString("title"),
            o.optString("poster").ifBlank { null }, o.optString("backdrop").ifBlank { null },
            o.optString("overview"), o.optString("year"),
        )
    }
}

data class Channel(val name: String, val url: String, val group: String)
data class Entry(val item: Item, val status: String)

private val http = OkHttpClient()

/** Builds playable URLs from the templates and playlists the user enters in Settings. */
object Sources {
    fun movieUrl(id: Int): String? =
        Store.movieTpl.takeIf { it.isNotBlank() }?.replace("{id}", id.toString())

    fun hasTv(): Boolean = Store.tvTpl.isNotBlank()

    fun episodeUrl(id: Int, season: Int, episode: Int): String? =
        Store.tvTpl.takeIf { it.isNotBlank() }
            ?.replace("{id}", id.toString())?.replace("{s}", season.toString())?.replace("{e}", episode.toString())

    /** Channels from all saved M3U playlists; a playlist that fails to load is skipped. */
    suspend fun playlistChannels(): List<Channel> = withContext(Dispatchers.IO) {
        Store.playlists.flatMap { url ->
            try {
                val text = if (url.startsWith("file:")) File(URI(url)).readText()
                else http.newCall(Request.Builder().url(url).build()).execute().use { it.body!!.string() }
                parseM3u(text)
            } catch (e: Exception) { emptyList() }
        }
    }

    private fun parseM3u(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var name: String? = null
        var group = ""
        for (line in text.lineSequence().map { it.trim() }) {
            if (line.startsWith("#EXTINF")) {
                name = line.substringAfterLast(",").trim()
                group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
            } else if (line.isNotEmpty() && !line.startsWith("#") && name != null) {
                out += Channel(name, line, group); name = null
            }
        }
        return out
    }
}

/** Watch history and My List, kept in SharedPreferences. */
object Store {
    private lateinit var sp: SharedPreferences
    var history by mutableStateOf<List<Item>>(emptyList()); private set
    var list by mutableStateOf<List<Entry>>(emptyList()); private set
    var tmdbKey by mutableStateOf(""); private set
    var movieTpl by mutableStateOf(""); private set
    var tvTpl by mutableStateOf(""); private set
    var playlists by mutableStateOf<List<String>>(emptyList()); private set
    var channels by mutableStateOf<List<Channel>>(emptyList()); private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("store", Context.MODE_PRIVATE)
        history = read("history").map { Item.fromJson(it) }
        list = read("list").map { Entry(Item.fromJson(it.getJSONObject("item")), it.getString("status")) }
        tmdbKey = sp.getString("tmdbKey", "") ?: ""
        movieTpl = sp.getString("movieTpl", "") ?: ""
        tvTpl = sp.getString("tvTpl", "") ?: ""
        playlists = JSONArray(sp.getString("playlists", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
        channels = read("channels").map { Channel(it.getString("name"), it.getString("url"), "") }
    }

    private fun put(key: String, value: String) = sp.edit().putString(key, value).apply()

    fun saveTmdbKey(v: String) { tmdbKey = v.trim(); put("tmdbKey", tmdbKey) }
    fun saveMovieTpl(v: String) { movieTpl = v.trim(); put("movieTpl", movieTpl) }
    fun saveTvTpl(v: String) { tvTpl = v.trim(); put("tvTpl", tvTpl) }

    fun addPlaylist(url: String) { playlists = playlists + url.trim(); put("playlists", JSONArray(playlists).toString()) }
    fun removePlaylist(url: String) {
        if (url.startsWith("file:")) File(URI(url)).delete()
        playlists = playlists - url; put("playlists", JSONArray(playlists).toString())
    }

    /** Copies a playlist file the user picked into app storage and adds it as a playlist. */
    fun importPlaylist(ctx: Context, uri: Uri) {
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
        val file = File(ctx.filesDir, "playlist-${System.currentTimeMillis()}.m3u")
        file.writeText(text)
        addPlaylist(file.toURI().toString())
    }

    private fun saveChannels() =
        put("channels", JSONArray(channels.map { JSONObject().put("name", it.name).put("url", it.url) }).toString())
    fun addChannel(name: String, url: String) { channels = channels + Channel(name.trim(), url.trim(), ""); saveChannels() }
    fun removeChannel(c: Channel) { channels = channels - c; saveChannels() }

    private fun read(key: String): List<JSONObject> {
        val a = JSONArray(sp.getString(key, "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun same(a: Item, b: Item) = a.id == b.id && a.kind == b.kind

    fun addHistory(item: Item) {
        history = (listOf(item) + history.filterNot { same(it, item) }).take(20)
        sp.edit().putString("history", JSONArray(history.map { it.toJson() }).toString()).apply()
    }

    fun statusOf(item: Item): String? = list.firstOrNull { same(it.item, item) }?.status

    /** status == null removes the item from My List. */
    fun setStatus(item: Item, status: String?) {
        list = list.filterNot { same(it.item, item) } + (status?.let { listOf(Entry(item, it)) } ?: emptyList())
        sp.edit().putString("list", JSONArray(list.map { JSONObject().put("item", it.item.toJson()).put("status", it.status) }).toString()).apply()
    }
}

object Tmdb {
    private const val IMG = "https://image.tmdb.org/t/p/"

    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val sep = if ('?' in path) '&' else '?'
        val url = "https://api.themoviedb.org/3$path${sep}api_key=${Store.tmdbKey}"
        val json = http.newCall(Request.Builder().url(url).build()).execute().use { JSONObject(it.body!!.string()) }
        if (!json.optBoolean("success", true)) throw IllegalStateException(json.optString("status_message"))
        json
    }

    private fun parse(j: JSONObject, kind: Kind?): List<Item> {
        val r = j.optJSONArray("results") ?: return emptyList()
        return (0 until r.length()).map { r.getJSONObject(it) }.mapNotNull { o ->
            val k = kind ?: when (o.optString("media_type")) { "movie" -> Kind.MOVIE; "tv" -> Kind.TV; else -> null }
                ?: return@mapNotNull null
            val date = o.optString("release_date").ifBlank { o.optString("first_air_date") }
            Item(
                o.getInt("id"), k, o.optString("title").ifBlank { o.optString("name") },
                o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }?.let { IMG + "w342" + it },
                o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }?.let { IMG + "w780" + it },
                o.optString("overview"), date.take(4),
            )
        }
    }

    suspend fun trending() = parse(get("/trending/all/day"), null)

    suspend fun browse(kind: Kind, genre: Int?, query: String): List<Item> {
        val p = if (kind == Kind.MOVIE) "movie" else "tv"
        val path = when {
            query.isNotBlank() -> "/search/$p?query=" + URLEncoder.encode(query, "UTF-8")
            genre != null -> "/discover/$p?with_genres=$genre"
            else -> "/$p/popular"
        }
        return parse(get(path), kind)
    }

    /** YouTube key of an official trailer, or null. */
    suspend fun trailerKey(kind: Kind, id: Int): String? {
        val p = if (kind == Kind.MOVIE) "movie" else "tv"
        val r = get("/$p/$id/videos").optJSONArray("results") ?: return null
        for (i in 0 until r.length()) {
            val o = r.getJSONObject(i)
            if (o.optString("site") == "YouTube" && o.optString("type") == "Trailer") return o.getString("key")
        }
        return null
    }

    suspend fun seasons(id: Int): Int = get("/tv/$id").optInt("number_of_seasons", 1).coerceAtLeast(1)

    suspend fun episodes(id: Int, season: Int): List<Pair<Int, String>> {
        val r = get("/tv/$id/season/$season").optJSONArray("episodes") ?: return emptyList()
        return (0 until r.length()).map { r.getJSONObject(it) }
            .map { it.getInt("episode_number") to it.optString("name") }
    }
}
