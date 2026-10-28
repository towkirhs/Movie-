package com.example.movielive

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
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

data class Channel(val name: String, val url: String, val time: String, val league: String)
data class Entry(val item: Item, val status: String)

/** Reads assets/sources.json — the ONE place the user adds their own links. */
object Sources {
    private var movies: Map<Int, String> = emptyMap()
    private var tv: Map<Int, String> = emptyMap()
    var live: List<Channel> = emptyList()

    fun load(ctx: Context) {
        val json = JSONObject(ctx.assets.open("sources.json").bufferedReader().use { it.readText() })
        movies = idMap(json.optJSONObject("movies"))
        tv = idMap(json.optJSONObject("tv"))
        val l = json.optJSONArray("live")
        live = (0 until (l?.length() ?: 0)).map { l!!.getJSONObject(it) }
            .map { Channel(it.getString("name"), it.optString("url"), it.optString("time").ifBlank { "Anytime" }, it.optString("league")) }
            .filter { it.url.isNotBlank() }
    }

    private fun idMap(o: JSONObject?): Map<Int, String> =
        o?.keys()?.asSequence()
            ?.mapNotNull { k -> k.toIntOrNull()?.let { it to o.getString(k) } }
            ?.filter { it.second.isNotBlank() }?.toMap() ?: emptyMap()

    fun movieUrl(id: Int): String? = movies[id]
    fun hasTv(id: Int): Boolean = tv.containsKey(id)
    fun episodeUrl(id: Int, season: Int, episode: Int): String? =
        tv[id]?.replace("{s}", season.toString())?.replace("{e}", episode.toString())
}

/** Watch history and My List, kept in SharedPreferences. */
object Store {
    private lateinit var sp: SharedPreferences
    var history by mutableStateOf<List<Item>>(emptyList()); private set
    var list by mutableStateOf<List<Entry>>(emptyList()); private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("store", Context.MODE_PRIVATE)
        history = read("history").map { Item.fromJson(it) }
        list = read("list").map { Entry(Item.fromJson(it.getJSONObject("item")), it.getString("status")) }
    }

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
    private val client = OkHttpClient()
    private const val IMG = "https://image.tmdb.org/t/p/"

    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val sep = if ('?' in path) '&' else '?'
        val url = "https://api.themoviedb.org/3$path${sep}api_key=${BuildConfig.TMDB_API_KEY}"
        client.newCall(Request.Builder().url(url).build()).execute().use { JSONObject(it.body!!.string()) }
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
