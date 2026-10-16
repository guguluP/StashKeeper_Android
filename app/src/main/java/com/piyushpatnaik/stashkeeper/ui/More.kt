package com.piyushpatnaik.stashkeeper.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.piyushpatnaik.stashkeeper.data.ExpiryScheduler
import com.piyushpatnaik.stashkeeper.data.LookupOutcome
import com.piyushpatnaik.stashkeeper.data.StashStore
import com.piyushpatnaik.stashkeeper.logic.CategoryMilestone
import com.piyushpatnaik.stashkeeper.logic.allMilestones
import com.piyushpatnaik.stashkeeper.logic.warningWindowChoices
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(onLocations: () -> Unit, onHousehold: () -> Unit, onMilestones: () -> Unit, onSettings: () -> Unit, onAdd: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("More", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add item") }
            }
            MoreRow("Locations", "The places you keep things.", Icons.Filled.Inventory2, onLocations)
            MoreRow("Household", "Send or import the shared stash file.", Icons.Filled.People, onHousehold)
            MoreRow("Milestones", "Streaks and what you are close to.", Icons.Filled.EmojiEvents, onMilestones)
            MoreRow("Settings", "Your name, reminders, and backup.", Icons.Filled.Settings, onSettings)
    }
}

@Composable
private fun MoreRow(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(store: StashStore, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    Screen("Locations", onBack, { IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add location") } }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            TextButton(onClick = { editing = !editing }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(if (editing) "Done" else "Reorder")
            }
            if (store.places.isEmpty()) {
                EmptyCopy("No locations yet", "Locations are created when you add items, or you can add one here.")
            }
            store.places.sortedBy { it.sortIndex }.forEach { place ->
                val count = store.items.count { it.locationId == place.id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pressable(onClick = { onOpen(place.id) }, modifier = Modifier.weight(1f)) {
                        ListItem(
                            headlineContent = { Text(place.name) },
                            supportingContent = { Text(if (place.parentId == null) "$count items" else "In ${store.placeName(place.parentId)} · $count items") },
                            leadingContent = { Icon(locationIcon(place.icon), contentDescription = null) },
                        )
                    }
                    if (editing) {
                        TextButton(onClick = { store.movePlace(place.id, true) }) { Text("Up") }
                        TextButton(onClick = { store.movePlace(place.id, false) }) { Text("Down") }
                        TextButton(onClick = { store.deletePlace(place.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
    if (adding) AddPlaceDialog(onDismiss = { adding = false }) { name, icon ->
        store.newPlace(name, icon = icon)
        adding = false
    }
}

@Composable
private fun AddPlaceDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("archivebox") }
    val icons = listOf("archivebox", "cabinet", "refrigerator", "cross.case", "car", "bed.double", "table.furniture", "shippingbox")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Location name") })
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(icons) { option ->
                        Pressable(onClick = { icon = option }) {
                            Icon(locationIcon(option), option, tint = if (icon == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onAdd(name.trim(), icon) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MilestonesScreen(store: StashStore, onBack: () -> Unit) {
    val metrics = LocalDisplayMetrics.current
    val milestones = remember(store.revision) { allMilestones(store.items) }
    val fresh = remember(milestones) { milestones.filter { it.isUnlocked && it.id !in store.celebrated() } }
    var show by remember { mutableStateOf(fresh.isNotEmpty() && !metrics.reduceMotion) }
    LaunchedEffect(fresh) {
        if (fresh.isNotEmpty()) store.celebrate(fresh.map { it.id })
    }
    val grouped = milestones.groupBy { it.category }.toSortedMap()
    Screen("Milestones", onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)) {
                item {
                    Row(Modifier.glass(20.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MilestoneRing(
                            progress = (store.streakCurrent / 30f).coerceAtMost(1f),
                            color = Color(0xFFE07A2F),
                            icon = Icons.Filled.EmojiEvents,
                            size = 76.dp,
                        )
                        Column {
                            Text("Current streak", style = MaterialTheme.typography.titleSmall)
                            Text("${store.streakCurrent} days", style = MaterialTheme.typography.headlineSmall)
                            Text("Longest: ${store.streakLongest} day${if (store.streakLongest == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Open StashKeeper daily to keep it going.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (grouped.isEmpty()) {
                    item { EmptyCopy("No milestones yet", "Start adding items to unlock achievements for each category.") }
                }
                grouped.forEach { (category, rows) ->
                    item {
                        Text(category, style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                            items(rows, key = { it.id }) { milestone -> MilestoneCard(milestone) }
                        }
                    }
                }
            }
            if (show) {
                Confetti(metrics.refreshRate, metrics.reduceMotion)
                Column(Modifier.align(Alignment.Center).glass(24.dp).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("New milestone", style = MaterialTheme.typography.titleLarge)
                    fresh.take(3).forEach { Text("${it.category} · ${it.tier.label}") }
                    TextButton(onClick = { show = false }) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun MilestoneCard(milestone: CategoryMilestone) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        MilestoneRing(milestone.progress.toFloat(), tierColor(milestone.tier), categoryIcon(milestone.category))
        Text(milestone.tier.label, style = MaterialTheme.typography.labelMedium)
        Text("${milestone.currentCount}/${milestone.tier.threshold}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class Particle(var x: Float, var y: Float, val vx: Float, var vy: Float, val color: Color, val radius: Float)

@Composable
private fun Confetti(refreshRate: Int, reduceMotion: Boolean) {
    if (reduceMotion) return
    val particles = remember {
        List(80) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -0.2f,
                vx = (Random.nextFloat() - 0.5f) * 0.15f,
                vy = 0.15f + Random.nextFloat() * 0.35f,
                color = listOf(Color(0xFF31C398), Color(0xFFE07A2F), Color(0xFF6A4C93), Color(0xFFFFDB59)).random(),
                radius = 4f + Random.nextFloat() * 5f,
            )
        }
    }
    val metrics = LocalDisplayMetrics.current
    var frame by remember { mutableStateOf(0) }
    LaunchedEffect(refreshRate) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) metrics.frameIntervalSeconds.toFloat() else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                particles.forEach { particle ->
                    particle.x += particle.vx * dt
                    particle.y += particle.vy * dt
                    particle.vy += 0.35f * dt
                    if (particle.y > 1.2f) {
                        particle.y = -0.1f
                        particle.vy = 0.2f
                    }
                }
            }
            frame++
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val drawnFrame = frame
        particles.forEach { drawParticle(it) }
    }
}

private fun DrawScope.drawParticle(particle: Particle) {
    drawCircle(particle.color, particle.radius, Offset(particle.x * size.width, particle.y * size.height))
}

@Composable
fun HouseholdScreen(store: StashStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) }
            status = "Export saved."
        }.onFailure { status = it.message }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            status = if (text.isNullOrBlank()) "Import failed." else runCatching { store.importHousehold(text) }.getOrElse { "Import failed: ${it.message}" }
        }
    }
    Screen("Household", onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Send your stash to someone else as a file. Import adds new items and places, and updates items that share an id. Photos already on this device stay here.")
            Text("Quantity changes record the household name from Settings. Photos stay on the device that took them.")
            TextButton(onClick = {
                val file = File(context.cacheDir, "StashKeeper-household.json")
                file.writeText(store.exportJson())
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val share = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(share, "Share stash"))
                status = "File ready to send."
            }) { Text("Prepare stash file") }
            TextButton(onClick = { export.launch("StashKeeper-household.json") }) { Text("Save a copy") }
            TextButton(onClick = { import.launch(arrayOf("application/json")) }) { Text("Import a household file") }
            status?.let { Text(it) }
        }
    }
}

@Composable
fun SettingsScreen(store: StashStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(store.householdName) { mutableStateOf(if (store.householdName == "Me") "" else store.householdName) }
    var status by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = if (it) "Notifications are on." else "Notifications stay off until you allow them."
        if (it) ExpiryScheduler.show(context)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) }
            status = "Export saved."
        }.onFailure { status = it.message }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            status = if (text.isNullOrBlank()) "Import failed." else runCatching { store.importHousehold(text) }.getOrElse { "Import failed: ${it.message}" }
        }
    }
    val lookups = store.lookupRecords()
    val hits = lookups.count { it.outcome == LookupOutcome.Hit }
    val misses = lookups.count { it.outcome == LookupOutcome.Miss }
    val failed = lookups.count { it.outcome == LookupOutcome.NetworkFailure }
    Screen("Settings", onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Household", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(name, {
                name = it
                store.updateHouseholdName(it)
            }, Modifier.fillMaxWidth(), label = { Text("Your name") }, placeholder = { Text("Me") })
            Text("This name is stored on each quantity change.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Reminders", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep a buy-list notification", Modifier.weight(1f))
                Switch(store.buyListNotification, { store.updateBuyListNotification(it); if (it) ExpiryScheduler.show(context) })
            }
            Text("Android has no Reminders app. When this is on, the buy list stays in a notification other apps can leave visible.")
            Text("Warn me", style = MaterialTheme.typography.titleSmall)
            warningWindowChoices.forEach { days ->
                TextButton(onClick = { store.updateWarningDays(days) }) {
                    Text(if (store.warningDays == days) "$days days before expiry  ✓" else "$days days before expiry")
                }
            }
            TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Request notification access") }
            Text(
                "Animations follow this display at ${LocalDisplayMetrics.current.refreshRate} Hz.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (Build.VERSION.SDK_INT >= 37) {
                Text("Assistant", style = MaterialTheme.typography.titleMedium)
                Text(
                    "While StashKeeper is open, Android 17 can find an item, list what is expiring, add something by name, or read and add to the shopping list.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text("Barcode lookup", style = MaterialTheme.typography.titleSmall)
            if (lookups.isEmpty()) {
                Text("No scans recorded yet. Each barcode lookup is kept on this device so you can see whether a miss is a real gap or a failed request.")
            } else {
                Text("Recorded lookups  ${lookups.size}")
                Text("Hits  $hits")
                Text("Not in a database  $misses")
                Text("Couldn't reach a database  $failed")
                val decided = hits + misses
                if (decided > 0) Text("Miss rate  ${misses * 100 / decided}%")
            }
            Text("Names come from Open Food Facts, Open Beauty Facts, Open Products Facts, and Open Pet Food Facts. Photos and the stash stay on this device.")
            if (lookups.isNotEmpty()) TextButton(onClick = { store.clearLookups() }) { Text("Clear lookup history") }
            TextButton(onClick = { export.launch("StashKeeper-backup.json") }) { Text("Export inventory as JSON") }
            TextButton(onClick = { import.launch(arrayOf("application/json")) }) { Text("Import inventory JSON") }
            Text("Photos travel inside the file when you export from this phone. A file from the Apple app brings its photos along too.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status?.let { Text(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { actions() },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        content = content,
    )
}
