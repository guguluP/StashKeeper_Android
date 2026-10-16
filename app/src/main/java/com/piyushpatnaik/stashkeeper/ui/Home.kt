@file:OptIn(ExperimentalMaterial3Api::class)

package com.piyushpatnaik.stashkeeper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.piyushpatnaik.stashkeeper.data.StashStore
import com.piyushpatnaik.stashkeeper.logic.CategoryMilestone
import com.piyushpatnaik.stashkeeper.logic.StashItem
import com.piyushpatnaik.stashkeeper.logic.StashPlace
import com.piyushpatnaik.stashkeeper.logic.daysUntilExpiry
import com.piyushpatnaik.stashkeeper.logic.expiryStatus
import com.piyushpatnaik.stashkeeper.logic.greeting
import com.piyushpatnaik.stashkeeper.logic.nextMilestones
import java.time.LocalTime

@Composable
fun HomeScreen(
    store: StashStore,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onBarcode: () -> Unit,
    onReceipt: () -> Unit,
    onAsk: () -> Unit,
    onCook: () -> Unit,
    onLocations: () -> Unit,
    onMilestones: () -> Unit,
    onLocation: (String) -> Unit,
) {
    val metrics = LocalDisplayMetrics.current
    val revision = store.revision
    val items = remember(revision) { store.items.toList() }
    val places = remember(revision) { store.places.toList() }
    val attention = remember(revision, store.warningDays) { store.attention() }
    val milestones = remember(revision) { nextMilestones(items).take(5) }
    var appeared by remember { mutableStateOf(metrics.reduceMotion) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appeared = true }
    val offset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (appeared || metrics.reduceMotion) 0.dp else 12.dp,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = metrics.springStiffness()),
        label = "enter",
    )
    Scaffold(containerColor = Color.Transparent) { padding ->
        LazyColumn(
            Modifier.padding(padding).graphicsOffset(offset),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
        ) {
            item { PageTitle(greeting(LocalTime.now().hour)) }
            item {
                Page {
                    Text(
                        heroLine(items.size, places.size, attention, store),
                        modifier = Modifier.padding(horizontal = metrics.horizontalPadding),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (attention.isNotEmpty()) {
                item { Page { SectionTitle("Needs attention") } }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = metrics.horizontalPadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(attention, key = { it.id }) { item ->
                            Pressable(onClick = { onOpen(item.id) }) { AttentionCard(store, item) }
                        }
                    }
                }
            }
            item { Page { QuickActions(onBarcode, onReceipt, onAdd) } }
            if (places.isNotEmpty()) {
                item {
                    Page {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Locations")
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onLocations) { Text("See all") }
                        }
                    }
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = metrics.horizontalPadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(places.take(8), key = { it.id }) { place ->
                            Pressable(onClick = { onLocation(place.id) }) { LocationChip(store, place) }
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                item { Page { EmptyStart(onBarcode, onReceipt, onAdd) } }
            } else {
                item { Page { SectionTitle("Recently added") } }
                items(items.take(6), key = { it.id }) { item ->
                    Page {
                        Pressable(onClick = { onOpen(item.id) }, modifier = Modifier.padding(horizontal = metrics.horizontalPadding, vertical = 4.dp).glass().padding(12.dp)) {
                            ItemRow(store, item)
                        }
                    }
                }
            }
            item { Page { Stats(items.size, places.size, attention.size) } }
            if (milestones.isNotEmpty()) {
                item {
                    Page {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Almost there")
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onMilestones) { Text("See all") }
                        }
                    }
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = metrics.horizontalPadding), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(milestones, key = { it.id }) { milestone ->
                            Pressable(onClick = onMilestones) { MilestoneChip(milestone) }
                        }
                    }
                }
            }
            item {
                Page {
                    Row(Modifier.padding(horizontal = metrics.horizontalPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onAsk) { Text("Ask") }
                        TextButton(onClick = onCook) { Text("Cook") }
                    }
                }
            }
        }
    }
}

private fun heroLine(count: Int, places: Int, attention: List<StashItem>, store: StashStore): String {
    val expired = attention.count { expiryStatus(it, store.today(), store.warningDays) == com.piyushpatnaik.stashkeeper.logic.ExpiryStatus.Expired }
    return when {
        count == 0 -> "Add the first thing you keep."
        expired > 0 -> "$expired expired · ${attention.size} need a look"
        attention.isNotEmpty() -> "Use the soonest items first."
        else -> "$count items across $places places."
    }
}

@Composable
private fun Modifier.graphicsOffset(offset: androidx.compose.ui.unit.Dp): Modifier {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { offset.toPx() }
    return graphicsLayer { translationY = px }
}

@Composable
private fun Hero(store: StashStore, count: Int, places: Int, attention: List<StashItem>) {
    val metrics = LocalDisplayMetrics.current
    val expired = attention.count { expiryStatus(it, store.today(), store.warningDays) == com.piyushpatnaik.stashkeeper.logic.ExpiryStatus.Expired }
    val insight = when {
        count == 0 -> "Photograph a shelf or package to start your stash."
        expired > 0 -> "$expired expired · ${attention.size} need a look"
        attention.isNotEmpty() -> "Use the soonest items first."
        else -> "Nothing urgent. $count items across $places places."
    }
    Column(
        Modifier
            .padding(horizontal = metrics.horizontalPadding)
            .fillMaxWidth()
            .glass(24.dp)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        Color(0xFF6A4C93).copy(alpha = 0.16f),
                        Color(0xFFE07A2F).copy(alpha = 0.12f),
                    )
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(greeting(LocalTime.now().hour), style = MaterialTheme.typography.headlineSmall)
        Text(insight, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickActions(onBarcode: () -> Unit, onReceipt: () -> Unit, onType: () -> Unit) {
    val metrics = LocalDisplayMetrics.current
    val actions = listOf(
        Triple("Scan", Icons.Filled.QrCodeScanner, onBarcode),
        Triple("Receipt", Icons.Filled.DocumentScanner, onReceipt),
        Triple("Type", Icons.Filled.Edit, onType),
    )
    Row(Modifier.padding(horizontal = metrics.horizontalPadding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.forEach { (title, icon, action) -> QuickTile(title, icon, action, Modifier.weight(1f)) }
    }
}

@Composable
private fun QuickTile(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Pressable(onClick, modifier.glass(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Stats(items: Int, places: Int, attention: Int) {
    val metrics = LocalDisplayMetrics.current
    val tiles = listOf(
        Triple("Total items", items.toString(), Icons.Filled.Inventory2),
        Triple("Locations", places.toString(), Icons.Filled.Inventory2),
        Triple("Expiring soon", attention.toString(), Icons.Filled.Schedule),
    )
    Column(Modifier.padding(horizontal = metrics.horizontalPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(metrics.statColumns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (title, value, icon) ->
                    Column(Modifier.weight(1f).glass(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(value, style = MaterialTheme.typography.headlineSmall)
                        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionCard(store: StashStore, item: StashItem) {
    val metrics = LocalDisplayMetrics.current
    val status = expiryStatus(item, store.today(), store.warningDays)
    val days = daysUntilExpiry(item.expiry, store.today())
    val label = when {
        days == null -> ""
        days < 0 -> "Expired"
        days == 0 -> "Today"
        else -> "${days}d left"
    }
    Column(Modifier.width(metrics.attentionCardWidth).glass(16.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            ItemThumbnail(store, item, metrics.attentionCardWidth - 28.dp)
            Icon(
                Icons.Filled.Warning,
                contentDescription = status.name,
                tint = expiryInk(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(expiryColor(status))
                    .padding(3.dp),
            )
        }
        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(label, color = expiryColor(status), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MilestoneChip(milestone: CategoryMilestone) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(LocalDisplayMetrics.current.locationChipWidth + 16.dp)) {
        MilestoneRing(milestone.progress.toFloat(), tierColor(milestone.tier), categoryIcon(milestone.category))
        Text(milestone.category, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MilestoneRing(progress: Float, color: Color, icon: ImageVector, size: androidx.compose.ui.unit.Dp = 52.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val stroke = 6.dp.toPx()
            drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.38f))
    }
}

@Composable
private fun LocationChip(store: StashStore, place: StashPlace) {
    val count = store.items.count { it.locationId == place.id }
    Column(Modifier.width(LocalDisplayMetrics.current.locationChipWidth), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(locationIcon(place.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(place.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyStart(onBarcode: () -> Unit, onReceipt: () -> Unit, onType: () -> Unit) {
    val metrics = LocalDisplayMetrics.current
    Column(Modifier.padding(horizontal = metrics.horizontalPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Start with one of these", style = MaterialTheme.typography.titleMedium)
        SampleCard("Scan a barcode", "A known product saves with its name.", Icons.Filled.QrCodeScanner, onBarcode)
        SampleCard("Photograph a receipt", "Each line becomes an item you can uncheck.", Icons.Filled.DocumentScanner, onReceipt)
        SampleCard("Type a name", "For the thing already in your hand.", Icons.Filled.Edit, onType)
    }
}

@Composable
private fun SampleCard(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth().glass(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedShape()).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun RoundedShape() = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
