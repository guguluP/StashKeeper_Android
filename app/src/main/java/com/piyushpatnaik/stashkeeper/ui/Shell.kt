package com.piyushpatnaik.stashkeeper.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.piyushpatnaik.stashkeeper.data.StashStore
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector, val badge: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StashApp(store: StashStore, pending: MutableState<String?>) {
    val nav = rememberNavController()
    val metrics = LocalDisplayMetrics.current
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var photoTarget by remember { mutableStateOf<((ByteArray) -> Unit)?>(null) }
    val picker = rememberLauncherForActivityResult(StashImagePicker()) { uri ->
        val target = photoTarget ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            target(bytes)
        }
    }
    fun pick(target: (ByteArray) -> Unit) {
        photoTarget = target
        picker.launch(Unit)
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val attention = store.unhandledAttentionCount()
    val phoneTabs = listOf(
        Tab("home", "Home", Icons.Filled.Home),
        Tab("items", "Items", Icons.AutoMirrored.Filled.List),
        Tab("shopping", "Shopping", Icons.Filled.ShoppingCart, badge = true),
        Tab("more", "More", Icons.Filled.MoreHoriz),
        Tab("search", "Search", Icons.Filled.Search),
    )
    val railTabs = listOf(
        Tab("home", "Home", Icons.Filled.Home),
        Tab("items", "Items", Icons.AutoMirrored.Filled.List),
        Tab("locations", "Locations", Icons.Filled.Inventory2),
        Tab("shopping", "Shopping", Icons.Filled.ShoppingCart, badge = true),
        Tab("search", "Search", Icons.Filled.Search),
        Tab("household", "Household", Icons.Filled.People),
        Tab("milestones", "Milestones", Icons.Filled.EmojiEvents),
        Tab("settings", "Settings", Icons.Filled.Settings),
    )
    fun open(destination: String) {
        nav.navigate(destination) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(pending.value) {
        when (val request = pending.value) {
            null -> Unit
            "add" -> showAdd = true
            "shopping", "home", "items", "search", "more" -> open(request)
            else -> if (request.startsWith("item/")) nav.navigate(request)
        }
        pending.value = null
    }
    LaunchedEffect(store.undo) {
        val batch = store.undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(batch.label, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) store.undoDelete() else store.dismissUndo()
    }
    val brush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            Color.Transparent,
            Color(0xFFE07A2F).copy(alpha = 0.06f),
        ),
    )
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).background(brush)) {
        Row(Modifier.fillMaxSize()) {
            if (metrics.wide) {
                NavigationRail {
                    FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.padding(bottom = 12.dp)) {
                        Icon(Icons.Filled.Add, "Add item")
                    }
                    Text("Stash", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
                    railTabs.take(5).forEach { tab ->
                        RailItem(tab, selectedOf(route) == tab.route, attention) { open(tab.route) }
                    }
                    Text("You", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    railTabs.drop(5).forEach { tab ->
                        RailItem(tab, selectedOf(route) == tab.route, attention) { open(tab.route) }
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    val root = route?.substringBefore("?")
                    if (!metrics.wide && root in phoneTabs.map { it.route }) {
                        NavigationBar {
                            phoneTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedOf(route) == tab.route,
                                    onClick = { open(tab.route) },
                                    icon = {
                                        if (tab.badge && attention > 0) {
                                            BadgedIcon(tab.icon, tab.label, attention)
                                        } else {
                                            Icon(tab.icon, tab.label)
                                        }
                                    },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                val motion = if (metrics.reduceMotion) 1 else if (metrics.refreshRate >= 120) 360 else 420
                NavHost(
                    nav,
                    startDestination = "home",
                    modifier = Modifier.padding(padding),
                    enterTransition = { fadeIn(tween(motion)) + slideInHorizontally(tween(motion)) { it / 8 } },
                    exitTransition = { fadeOut(tween(motion / 2)) },
                    popEnterTransition = { fadeIn(tween(motion)) },
                    popExitTransition = { fadeOut(tween(motion)) + slideOutHorizontally(tween(motion)) { it / 6 } },
                ) {
                    composable("home") {
                        HomeScreen(
                            store,
                            onOpen = { nav.navigate("item/$it") },
                            onAdd = { showAdd = true },
                            onBarcode = { nav.navigate("barcode") },
                            onReceipt = { nav.navigate("receipt") },
                            onAsk = { nav.navigate("assistant") },
                            onCook = { nav.navigate("cook") },
                            onLocations = { nav.navigate("locations") },
                            onMilestones = { nav.navigate("milestones") },
                            onLocation = { nav.navigate("location/$it") },
                        )
                    }
                    composable("items") { ItemsScreen(store, onOpen = { nav.navigate("item/$it") }, onAdd = { showAdd = true }) }
                    composable("shopping") { ShoppingScreen(store, onAdd = { showAdd = true }) }
                    composable("more") {
                        MoreScreen(
                            onLocations = { nav.navigate("locations") },
                            onHousehold = { nav.navigate("household") },
                            onMilestones = { nav.navigate("milestones") },
                            onSettings = { nav.navigate("settings") },
                            onAdd = { showAdd = true },
                        )
                    }
                    composable("search") { SearchScreen(store, onOpen = { nav.navigate("item/$it") }, onAdd = { showAdd = true }) }
                    composable("locations") { LocationsScreen(store, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("location/$it") }) }
                    composable("location/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        LocationDetailScreen(store, it.arguments?.getString("id").orEmpty(), { nav.popBackStack() }, { id -> nav.navigate("item/$id") })
                    }
                    composable("milestones") { MilestonesScreen(store, { nav.popBackStack() }) }
                    composable("household") { HouseholdScreen(store, { nav.popBackStack() }) }
                    composable("settings") { SettingsScreen(store, { nav.popBackStack() }) }
                    composable("assistant") { AssistantScreen(store, { nav.popBackStack() }, cook = false) }
                    composable("cook") { AssistantScreen(store, { nav.popBackStack() }, cook = true) }
                    composable("receipt") { ReceiptScreen(store, { nav.popBackStack() }) }
                    composable("pack") { PackScreen(store, { nav.popBackStack() }) }
                    composable("barcode") { BarcodeAddScreen(store, { nav.popBackStack() }) }
                    composable("add") {
                        AddScreen(store, { nav.popBackStack() }, { nav.navigate("barcode") }) { consumer -> pick(consumer) }
                    }
                    composable("item/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        ItemDetailScreen(store, id, { nav.popBackStack() }) {
                            pick { bytes ->
                                store.items.firstOrNull { it.id == id }?.let { item ->
                                    store.update(item.copy(photoFiles = item.photoFiles + store.savePhoto(bytes)))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showAdd) {
            ModalBottomSheet(onDismissRequest = { showAdd = false }) {
                AddSheet(
                    store,
                    onScan = { showAdd = false; nav.navigate("barcode") },
                    onReceipt = { showAdd = false; nav.navigate("receipt") },
                    onPack = { showAdd = false; nav.navigate("pack") },
                    onDismiss = { showAdd = false },
                    onPhotoBytes = { consumer -> pick(consumer) },
                )
            }
        }
    }
}

private fun selectedOf(route: String?): String = when {
    route == null -> "home"
    route.startsWith("item") -> "items"
    route.startsWith("location") -> "locations"
    else -> route.substringBefore("?")
}

@Composable
private fun RailItem(tab: Tab, selected: Boolean, attention: Int, onClick: () -> Unit) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (tab.badge && attention > 0) BadgedIcon(tab.icon, tab.label, attention) else Icon(tab.icon, tab.label)
        },
        label = { Text(tab.label) },
    )
}

@Composable
private fun BadgedIcon(icon: ImageVector, label: String, count: Int) {
    Box {
        Icon(icon, label)
        Badge(Modifier.padding(start = 14.dp)) { Text(if (count > 99) "99+" else count.toString()) }
    }
}

