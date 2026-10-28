package com.example.movielive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Movies("Movies", Icons.Filled.PlayArrow),
    Tv("TV Shows", Icons.Filled.Star),
    Live("Live", Icons.Filled.DateRange),
    MyList("My List", Icons.Filled.Favorite),
    Settings("Settings", Icons.Filled.Home), // not shown in the bottom bar
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val scheme = darkColorScheme(background = Color.Black, surface = Color.Black)
        setContent { MaterialTheme(colorScheme = scheme) { Surface(color = Color.Black) { App() } } }
    }
}

@Composable
fun App() {
    var tab by remember { mutableStateOf(Tab.Home) }
    var details by remember { mutableStateOf<Item?>(null) }
    var playUrl by remember { mutableStateOf<String?>(null) }
    BackHandler(playUrl != null) { playUrl = null }
    BackHandler(playUrl == null && details != null) { details = null }

    val url = playUrl
    if (url != null) { PlayerScreen(url); return }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (details == null) NavigationBar(containerColor = Color.Black) {
                Tab.entries.filter { it != Tab.Settings }.forEach {
                    NavigationBarItem(tab == it, { tab = it }, { Icon(it.icon, it.label) }, label = { Text(it.label) })
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            val d = details
            if (d != null) DetailsScreen(d) { playUrl = it }
            else {
                TopBar(onSearch = { tab = Tab.Movies }, onSettings = { tab = Tab.Settings })
                when (tab) {
                    Tab.Home -> HomeScreen { details = it }
                    Tab.Movies -> BrowseScreen(Kind.MOVIE) { details = it }
                    Tab.Tv -> BrowseScreen(Kind.TV) { details = it }
                    Tab.Live -> LiveScreen { playUrl = it }
                    Tab.MyList -> MyListScreen { details = it }
                    Tab.Settings -> SettingsScreen()
                }
            }
        }
    }
}
