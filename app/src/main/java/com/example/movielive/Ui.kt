package com.example.movielive

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

private val ChipBg = Color(0xFF262626)
private val CardBg = Color(0xFF202020)
private val Muted = Color(0xFFA8A8A8)
private val Shape14 = RoundedCornerShape(14.dp)

@Composable
fun TopBar(onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("MovieLive", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        IconButton(onSearch) { Icon(Icons.Filled.Search, "Search") }
        IconButton(onSettings) { Icon(Icons.Filled.Settings, "Settings") }
    }
}

@Composable
fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(labels) { i, label ->
            val on = i == selected
            Text(
                label, color = if (on) Color.Black else Color.White,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Color.White else ChipBg)
                    .clickable { onSelect(i) }.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp, start = 4.dp))

@Composable
private fun Poster(item: Item, modifier: Modifier = Modifier, onClick: () -> Unit) =
    AsyncImage(
        item.poster, item.title, contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(ChipBg).clickable { onClick() },
    )

@Composable
private fun WideCard(item: Item, onClick: () -> Unit) =
    Column(Modifier.width(200.dp).clickable { onClick() }) {
        AsyncImage(
            item.backdrop ?: item.poster, item.title, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(110.dp).clip(Shape14).background(ChipBg),
        )
        Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        Text(if (item.kind == Kind.TV) "TV Show" else "Movie", color = Muted, fontSize = 13.sp)
    }

@Composable
fun HomeScreen(onItem: (Item) -> Unit) {
    var trending by remember { mutableStateOf<List<Item>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { try { trending = Tmdb.trending() } catch (e: Exception) { error = true } }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        if (error) item { Text("Couldn't load. Add a valid TMDB API key in Settings.", color = MaterialTheme.colorScheme.error) }
        trending.firstOrNull()?.let { hero ->
            item {
                AsyncImage(
                    hero.backdrop ?: hero.poster, hero.title, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(16.dp)).background(ChipBg).clickable { onItem(hero) },
                )
                Text(hero.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
            }
        }
        if (Store.history.isNotEmpty()) {
            item { SectionTitle("Watch History") }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(Store.history) { WideCard(it) { onItem(it) } } } }
        }
        item { SectionTitle("Trending") }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(trending) { WideCard(it) { onItem(it) } } } }
    }
}

private val movieGenres = listOf("All" to null, "Action" to 28, "Comedy" to 35, "Drama" to 18, "Thriller" to 53, "Sci-Fi" to 878)
private val tvGenres = listOf("All" to null, "Action" to 10759, "Comedy" to 35, "Drama" to 18, "Crime" to 80, "Sci-Fi" to 10765)

@Composable
fun BrowseScreen(kind: Kind, onItem: (Item) -> Unit) {
    val genres = if (kind == Kind.MOVIE) movieGenres else tvGenres
    var query by remember(kind) { mutableStateOf("") }
    var gi by remember(kind) { mutableIntStateOf(0) }
    var items by remember(kind) { mutableStateOf<List<Item>>(emptyList()) }
    var error by remember(kind) { mutableStateOf(false) }
    LaunchedEffect(kind, query, gi) {
        error = false
        try { items = Tmdb.browse(kind, genres[gi].second, query) } catch (e: Exception) { error = true }
    }
    Column(Modifier.padding(horizontal = 12.dp)) {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(20.dp),
            label = { Text(if (kind == Kind.MOVIE) "Search movies" else "Search TV shows") },
        )
        Chips(genres.map { it.first }, gi) { gi = it }
        if (error) Text("Couldn't load. Add a valid TMDB API key in Settings.", color = MaterialTheme.colorScheme.error)
        LazyVerticalGrid(
            GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { items(items) { Poster(it) { onItem(it) } } }
    }
}

@Composable
fun LiveScreen(onPlay: (String) -> Unit) {
    var fetched by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Store.playlists) { loading = true; fetched = Sources.playlistChannels(); loading = false }
    val all = Store.channels + fetched
    if (all.isEmpty() && !loading) {
        Text("No live links yet. Add a playlist or channel in Settings.", Modifier.padding(16.dp), color = Muted)
        return
    }
    val groups = all.filter { it.name.contains(query, true) || it.group.contains(query, true) }
        .groupBy { it.group.ifBlank { "Channels" } }
    Column(Modifier.padding(horizontal = 12.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(20.dp), label = { Text("Search channels") })
        if (loading) Text("Loading playlists…", color = Muted, modifier = Modifier.padding(top = 8.dp))
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            groups.forEach { (group, channels) ->
                item { Text(group, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp)) }
                items(channels) { c ->
                    Text(c.name, Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(Shape14).background(CardBg)
                        .clickable { onPlay(c.url) }.padding(16.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private val statuses = listOf("Watching", "Plan to watch", "Completed")

@Composable
fun MyListScreen(onItem: (Item) -> Unit) {
    var f by remember { mutableIntStateOf(0) }
    val shown = Store.list.filter { f == 0 || it.status == statuses[f - 1] }
    Column(Modifier.padding(horizontal = 12.dp)) {
        Chips(listOf("All") + statuses, f) { f = it }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown) { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).clickable { onItem(e.item) }.padding(10.dp)) {
                    Poster(e.item, Modifier.width(80.dp)) { onItem(e.item) }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(e.item.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("${e.status} · ${if (e.item.kind == Kind.TV) "TV Show" else "Movie"}", color = Muted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) = Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))

@Composable
private fun Hint(text: String) = Text(text, color = Muted, fontSize = 13.sp)

@Composable
private fun Field(value: String, label: String, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) })

@Composable
private fun RemovableRow(text: String, onRemove: () -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), maxLines = 1)
        TextButton(onRemove) { Text("Remove") }
    }

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { Store.importPlaylist(ctx, it) } }
    var newPlaylist by remember { mutableStateOf("") }
    var chName by remember { mutableStateOf("") }
    var chUrl by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("TMDB API key")
        Hint("Free for personal use: create an account at themoviedb.org, then Settings > API > request a key. Paste the \"API Key\" value.")
        Field(Store.tmdbKey, "TMDB API key", Store::saveTmdbKey)

        Header("Movie link")
        Hint("A URL that plays one movie. Use {id} for the TMDB movie id, e.g. https://your-server/movies/{id}.m3u8")
        Field(Store.movieTpl, "Movie link", Store::saveMovieTpl)

        Header("TV show link")
        Hint("Use {id} for the show id, {s} for the season and {e} for the episode.")
        Field(Store.tvTpl, "TV show link", Store::saveTvTpl)

        Header("Live playlists (M3U)")
        Hint("Add as many playlist URLs as you like. Their channels appear in the Live tab.")
        Store.playlists.forEach { u -> RemovableRow(if (u.startsWith("file:")) "Imported file: " + u.substringAfterLast('/') else u) { Store.removePlaylist(u) } }
        Field(newPlaylist, "Playlist URL") { newPlaylist = it }
        Button({ if (newPlaylist.isNotBlank()) { Store.addPlaylist(newPlaylist); newPlaylist = "" } }) { Text("Add playlist") }
        OutlinedButton({ picker.launch(arrayOf("*/*")) }) { Text("Import playlist file") }

        Header("Single live channels")
        Store.channels.forEach { c -> RemovableRow(c.name) { Store.removeChannel(c) } }
        Field(chName, "Channel name") { chName = it }
        Field(chUrl, "Stream URL") { chUrl = it }
        Button({ if (chName.isNotBlank() && chUrl.isNotBlank()) { Store.addChannel(chName, chUrl); chName = ""; chUrl = "" } }) { Text("Add channel") }

        Header("About")
        Hint("MovieLive 0.1. This product uses the TMDB API but is not endorsed or certified by TMDB.")
        Hint("Only add links to content you have the right to watch.")
    }
}

@Composable
fun DetailsScreen(item: Item, onPlay: (String) -> Unit) {
    val ctx = LocalContext.current
    var trailer by remember(item) { mutableStateOf<String?>(null) }
    LaunchedEffect(item) { trailer = try { Tmdb.trailerKey(item.kind, item.id) } catch (e: Exception) { null } }
    var seasons by remember(item) { mutableIntStateOf(1) }
    var season by remember(item) { mutableIntStateOf(1) }
    var episodes by remember(item) { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    if (item.kind == Kind.TV) {
        LaunchedEffect(item) { seasons = try { Tmdb.seasons(item.id) } catch (e: Exception) { 1 } }
        LaunchedEffect(item, season) { episodes = try { Tmdb.episodes(item.id, season) } catch (e: Exception) { emptyList() } }
    }
    fun play(url: String) { Store.addHistory(item); onPlay(url) }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            AsyncImage(
                item.backdrop ?: item.poster, item.title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(ChipBg),
            )
            Text(item.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("${if (item.kind == Kind.TV) "TV Show" else "Movie"} · ${item.year}", color = Muted)
            Text(item.overview, color = Muted, modifier = Modifier.padding(vertical = 12.dp))
            if (item.kind == Kind.MOVIE) {
                val link = Sources.movieUrl(item.id)
                Button({ link?.let(::play) }, Modifier.fillMaxWidth(), enabled = link != null) {
                    Text(if (link != null) "Play" else "No movie link set. Add one in Settings")
                }
            } else if (!Sources.hasTv()) {
                Text("No TV show link set. Add one in Settings", color = Muted)
            }
            trailer?.let { key ->
                OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))) },
                    Modifier.fillMaxWidth()) { Text("Watch trailer") }
            }
            Text("My List", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            val current = Store.statusOf(item)
            Chips(statuses, statuses.indexOf(current)) { Store.setStatus(item, if (statuses[it] == current) null else statuses[it]) }
            if (item.kind == Kind.TV) {
                Chips((1..seasons).map { "Season $it" }, season - 1) { season = it + 1 }
            }
        }
        if (item.kind == Kind.TV) {
            items(episodes) { (n, name) ->
                val url = Sources.episodeUrl(item.id, season, n)
                Row(Modifier.fillMaxWidth().clickable(enabled = url != null) { play(url!!) }.padding(vertical = 14.dp)) {
                    Text("$n. $name", Modifier.weight(1f), color = if (url != null) Color.White else Muted)
                    Text(if (url != null) "▶" else "—", color = Muted)
                }
                HorizontalDivider(color = ChipBg)
            }
        }
    }
}

@Composable
fun PlayerScreen(url: String) {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true } }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView({ PlayerView(it).apply { this.player = player } }, Modifier.fillMaxSize().background(Color.Black).statusBarsPadding())
}
