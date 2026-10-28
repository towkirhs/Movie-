package com.example.movielive

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class Movie(val id: Int, val title: String, val poster: String?, val overview: String)
data class Channel(val name: String, val url: String)

/** Reads assets/sources.json — the ONE place the user adds their own links. */
object Sources {
    var movies: Map<Int, String> = emptyMap()
    var live: List<Channel> = emptyList()

    fun load(ctx: Context) {
        val json = JSONObject(ctx.assets.open("sources.json").bufferedReader().use { it.readText() })
        val m = json.optJSONObject("movies")
        movies = m?.keys()?.asSequence()
            ?.mapNotNull { k -> k.toIntOrNull()?.let { it to m.getString(k) } }
            ?.filter { it.second.isNotBlank() }?.toMap() ?: emptyMap()
        val l = json.optJSONArray("live")
        live = (0 until (l?.length() ?: 0)).map { l!!.getJSONObject(it) }
            .map { Channel(it.getString("name"), it.optString("url")) }
            .filter { it.url.isNotBlank() }
    }
}

object Tmdb {
    private val client = OkHttpClient()

    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val sep = if ('?' in path) '&' else '?'
        val url = "https://api.themoviedb.org/3$path${sep}api_key=${BuildConfig.TMDB_API_KEY}"
        client.newCall(Request.Builder().url(url).build()).execute().use {
            JSONObject(it.body!!.string())
        }
    }

    private fun parse(j: JSONObject): List<Movie> {
        val r = j.optJSONArray("results") ?: return emptyList()
        return (0 until r.length()).map {
            val o = r.getJSONObject(it)
            Movie(o.getInt("id"), o.optString("title"),
                o.optString("poster_path").takeIf { p -> p.isNotBlank() && p != "null" }
                    ?.let { p -> "https://image.tmdb.org/t/p/w342$p" },
                o.optString("overview"))
        }
    }

    suspend fun popular() = parse(get("/movie/popular"))
    suspend fun search(q: String) = parse(get("/search/movie?query=" + URLEncoder.encode(q, "UTF-8")))

    /** YouTube key of an official trailer, or null. */
    suspend fun trailerKey(id: Int): String? {
        val r = get("/movie/$id/videos").optJSONArray("results") ?: return null
        for (i in 0 until r.length()) {
            val o = r.getJSONObject(i)
            if (o.optString("site") == "YouTube" && o.optString("type") == "Trailer") return o.getString("key")
        }
        return null
    }
}
