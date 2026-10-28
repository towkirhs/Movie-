package com.example.movielive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

sealed interface Screen {
    object Home : Screen
    data class Details(val movie: Movie) : Screen
    data class Play(val url: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sources.load(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable
fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(screen != Screen.Home) { screen = Screen.Home }
    when (val s = screen) {
        Screen.Home -> HomeScreen(onMovie = { screen = Screen.Details(it) }, onPlay = { screen = Screen.Play(it) })
        is Screen.Details -> DetailsScreen(s.movie, onPlay = { screen = Screen.Play(it) })
        is Screen.Play -> PlayerScreen(s.url)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onMovie: (Movie) -> Unit, onPlay: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.statusBarsPadding()) {
        TabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Movies") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Live") })
        }
        if (tab == 0) MoviesTab(onMovie) else LiveTab(onPlay)
    }
}

@Composable
fun MoviesTab(onMovie: (Movie) -> Unit) {
    var query by remember { mutableStateOf("") }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        error = try {
            movies = if (query.isBlank()) Tmdb.popular() else Tmdb.search(query); null
        } catch (e: Exception) { "Couldn't load movies. Check TMDB_API_KEY in local.properties." }
    }
    Column(Modifier.padding(8.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search movies") }, singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.padding(top = 8.dp)) {
            items(movies) { m ->
                AsyncImage(m.poster, m.title, Modifier.padding(4.dp).aspectRatio(2f / 3f).clickable { onMovie(m) })
            }
        }
    }
}

@Composable
fun LiveTab(onPlay: (String) -> Unit) {
    if (Sources.live.isEmpty()) {
        Text("No live links yet. Add them in app/src/main/assets/sources.json", Modifier.padding(16.dp)); return
    }
    LazyColumn {
        items(Sources.live) { c ->
            ListItem(headlineContent = { Text(c.name) }, modifier = Modifier.clickable { onPlay(c.url) })
            HorizontalDivider()
        }
    }
}

@Composable
fun DetailsScreen(movie: Movie, onPlay: (String) -> Unit) {
    val ctx = LocalContext.current
    var trailer by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(movie.id) { trailer = try { Tmdb.trailerKey(movie.id) } catch (e: Exception) { null } }
    val link = Sources.movies[movie.id]
    Column(Modifier.statusBarsPadding().padding(16.dp)) {
        Text(movie.title, style = MaterialTheme.typography.headlineSmall)
        Text(movie.overview, Modifier.padding(vertical = 12.dp))
        Button({ link?.let(onPlay) }, enabled = link != null) {
            Text(if (link != null) "Play" else "No link added (movie id ${movie.id})")
        }
        trailer?.let { key ->
            OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))) },
                Modifier.padding(top = 8.dp)) { Text("Watch trailer") }
        }
    }
}

@Composable
fun PlayerScreen(url: String) {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true } }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView({ PlayerView(it).apply { this.player = player } }, Modifier.fillMaxSize())
}
