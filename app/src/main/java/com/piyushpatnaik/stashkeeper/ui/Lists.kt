package com.piyushpatnaik.stashkeeper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.piyushpatnaik.stashkeeper.data.StashStore
import com.piyushpatnaik.stashkeeper.logic.SearchIntent
import com.piyushpatnaik.stashkeeper.logic.StashItem
import com.piyushpatnaik.stashkeeper.logic.applySearch
import com.piyushpatnaik.stashkeeper.logic.expiryStatus
import com.piyushpatnaik.stashkeeper.logic.groceryLines
import com.piyushpatnaik.stashkeeper.logic.keywordFallback

private enum class ItemSort(val label: String) { Newest("Newest"), Name("Name"), Expiry("Expiry date") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(store: StashStore, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val revision = store.revision
    var sort by remember { mutableStateOf(ItemSort.Newest) }
    var category by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<StashItem?>(null) }
    val rows = remember(revision, sort, category) {
        store.items.filter { category == null || it.category == category }.let { list ->
            when (sort) {
                ItemSort.Newest -> list.sortedByDescending { it.createdAt }
                ItemSort.Name -> list.sortedBy { it.name.lowercase() }
                ItemSort.Expiry -> list.sortedBy { it.expiry ?: "9999" }
            }
        }
    }
    val categories = remember(revision) { store.items.map { it.category }.distinct().sorted() }
    ListScaffold("Items", onAdd, extra = {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.FilterList, "Filter and sort") }
        DropdownMenu(menu, { menu = false }) {
            ItemSort.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { sort = option; menu = false })
            }
            DropdownMenuItem(text = { Text("All categories") }, onClick = { category = null; menu = false })
            categories.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { category = name; menu = false })
            }
        }
    }) { padding ->
        if (rows.isEmpty()) {
            EmptyCopy("No items", "Add something you keep, or import a household file from the Apple app.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.id }) { item ->
                    SwipeRow(item, onAddOne = { store.adjust(item, 1) }, onDelete = { pending = item }) {
                        Pressable(onClick = { onOpen(item.id) }, modifier = Modifier.glass(16.dp).padding(10.dp)) {
                            ItemRow(store, item)
                        }
                    }
                }
            }
        }
    }
    pending?.let { item ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Delete \"${item.name}\"?") },
            confirmButton = {
                TextButton(onClick = { store.delete(item); pending = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(item: StashItem, onAddOne: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val haptics = rememberHaptics()
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { haptics.alignment(); onAddOne() }
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                else -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val add = state.targetValue == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = if (add) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(if (add) "Add one" else "Delete") }
        },
        content = { content() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(store: StashStore, onAdd: () -> Unit) {
    val context = LocalContext.current
    val revision = store.revision
    var showChecked by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val attention = remember(revision, store.warningDays) { store.attention() }
    val hidden = attention.count { store.isHandled(it) }
    val rows = attention.filter { showChecked || !store.isHandled(it) }
    val shortfalls = remember(revision) { store.items.filter { it.parQuantity > it.quantity }.sortedBy { it.name.lowercase() } }
    val openNeeds = remember(revision) { store.needs.filter { !it.isChecked } }
    ListScaffold(
        "Shopping",
        onAdd,
        extra = {
            TextButton(onClick = {
                val lines = groceryLines(store.items, store.needs)
                val text = if (lines.isEmpty()) "Nothing to buy." else lines.joinToString("\n") { "Buy ${it.quantity} ${it.name}" }
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, "Share the buy list"))
            }) { Text("Share") }
            if (hidden > 0) {
                TextButton(onClick = { showChecked = !showChecked }) { Text(if (showChecked) "Hide checked" else "Show checked") }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "To buy is what you are short. Use or replace is what is expiring.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item { SectionTitle("To buy") }
            if (shortfalls.isEmpty() && openNeeds.isEmpty()) {
                item { Text("Nothing to buy. Add a name here, or set how many to keep on an item.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(shortfalls, key = { "par-${it.id}" }) { item ->
                Column(Modifier.glass(16.dp).padding(12.dp)) {
                    Text(item.name)
                    Text(
                        "Have ${item.quantity}, keep ${item.parQuantity}. Buy ${item.parQuantity - item.quantity}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { store.markParBought(item) }) { Text("Bought") }
                }
            }
            items(openNeeds, key = { it.id }) { need ->
                Row(Modifier.glass(16.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(need.name)
                        Text("×${need.quantity}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { store.checkNeed(need.id) }) { Text("Bought") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), label = { Text("Add something to buy") }, singleLine = true)
                    TextButton(onClick = { store.addNeed(draft); draft = "" }, enabled = draft.isNotBlank()) { Text("Add") }
                }
            }
            item { SectionTitle("Use or replace") }
            if (rows.isEmpty()) {
                item {
                    Text(
                        if (attention.isEmpty()) "Nothing is expiring." else "Checked items stay off this list until their expiry date changes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(rows, key = { it.id }) { item ->
                val handled = store.isHandled(item)
                val status = expiryStatus(item, store.today(), store.warningDays)
                val place = store.placeName(item.locationId)
                val days = com.piyushpatnaik.stashkeeper.logic.daysUntilExpiry(item.expiry, store.today())
                val caption = when {
                    days == null -> place
                    days < 0 -> "Expired · $place"
                    days == 0 -> "Today · $place"
                    else -> "$days days left · $place"
                }
                Row(Modifier.glass(16.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(onClick = { store.setHandled(item, !handled) }) {
                        Icon(
                            if (handled) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            if (handled) "Mark ${item.name} as still needed" else "Check off ${item.name}",
                            tint = if (handled) expiryColor(com.piyushpatnaik.stashkeeper.logic.ExpiryStatus.Fresh) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = if (handled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                        ExpiryCapsule(caption.substringBefore(" ·"), status)
                    }
                    if (item.quantity > 0) TextButton(onClick = { store.consume(item) }) { Text("Used one") }
                }
            }
            item {
                val lines = groceryLines(store.items, store.needs)
                if (lines.isNotEmpty()) Text("${lines.size} on the buy list", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(store: StashStore, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val revision = store.revision
    var query by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val places = remember(revision) { store.places.toList() }
    val items = remember(revision) { store.items.toList() }
    val intent = remember(query, revision) {
        if (query.isBlank()) null else keywordFallback(query, items.map { it.category }.distinct(), places.map { it.name })
    }
    val results = remember(query, revision, store.warningDays) {
        applySearch(items, places, query, intent, store.today(), store.warningDays)
    }
    ListScaffold("Search", onAdd) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Page {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = LocalDisplayMetrics.current.horizontalPadding, vertical = 8.dp).focusRequester(focus),
                    placeholder = { Text("Try \"expired food\" or \"passport\"") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear search") }
                    },
                    singleLine = true,
                )
            }
            if (query.isEmpty()) {
                val chips = listOf("expiring soon", "in the fridge", "dairy", "passport", "what can I cook")
                LazyRow(contentPadding = PaddingValues(horizontal = LocalDisplayMetrics.current.horizontalPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chips) { chip ->
                        Pressable(onClick = { query = chip }, modifier = Modifier.glass(20.dp).padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text(chip, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else if (intent != null && intent.hasFilter) {
                FilterChips(intent)
            }
            if (query.isNotEmpty() && results.isEmpty()) {
                EmptyCopy("No matches", "Nothing in the stash matches \"$query\".")
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(if (query.isEmpty()) items else results, key = { it.id }) { item ->
                        Pressable(onClick = { onOpen(item.id) }, modifier = Modifier.glass(16.dp).padding(10.dp)) {
                            ItemRow(store, item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(intent: SearchIntent) {
    val chips = buildList {
        if (intent.category.isNotEmpty()) add(intent.category)
        if (intent.location.isNotEmpty()) add(intent.location)
        if (intent.expiryFocused) add("Expiring / expired")
        if (intent.priceFocused) add("Priced")
        addAll(intent.keywords)
    }
    LazyRow(contentPadding = PaddingValues(horizontal = LocalDisplayMetrics.current.horizontalPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips) { chip ->
            Text(chip, Modifier.glass(20.dp).padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ListScaffold(
    title: String,
    onAdd: () -> Unit,
    extra: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PageTitle(title) {
            extra()
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add item") }
        }
        Box(Modifier.weight(1f)) { content(PaddingValues(0.dp)) }
    }
}
