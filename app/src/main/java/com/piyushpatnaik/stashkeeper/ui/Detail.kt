package com.piyushpatnaik.stashkeeper.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.piyushpatnaik.stashkeeper.data.StashStore
import com.piyushpatnaik.stashkeeper.logic.StashItem
import com.piyushpatnaik.stashkeeper.logic.daysUntilExpiry
import com.piyushpatnaik.stashkeeper.logic.expiryCaption
import com.piyushpatnaik.stashkeeper.logic.expiryStatus
import com.piyushpatnaik.stashkeeper.logic.itemCategoryOptions
import com.piyushpatnaik.stashkeeper.logic.parentChoices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(store: StashStore, id: String, onBack: () -> Unit, onPhoto: () -> Unit) {
    val item = store.items.firstOrNull { it.id == id }
    var confirm by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(item?.name ?: "Missing") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        if (item == null) {
            Text("This item is gone.", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        val status = expiryStatus(item, store.today(), store.warningDays)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoPager(store, item)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.isPerishable) ExpiryCapsule(expiryCaption(item, store.today()), status)
                    Text(store.placeName(item.locationId), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                QuantityStepper(item.quantity, { store.adjust(item, it - item.quantity) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { store.consume(item) }, enabled = item.quantity > 0, modifier = Modifier.weight(1f)) { Text("Used 1") }
                if (item.isPerishable) OutlinedButton(onClick = { store.snooze(item) }, modifier = Modifier.weight(1f)) { Text("Tomorrow") }
            }
            SectionTitle("Details")
            Field("Name", item.name) { store.update(item.copy(name = it)) }
            MenuField("Category", item.category, itemCategoryOptions) { store.update(item.copy(category = it)) }
            Field("Subcategory", item.subcategory.orEmpty()) { store.update(item.copy(subcategory = it.ifBlank { null })) }
            Stepper("Quantity", item.quantity) { store.adjust(item, it - item.quantity) }
            Stepper("Keep at least", item.parQuantity) { store.update(item.copy(parQuantity = it.coerceIn(0, 999))) }
            if (item.parQuantity > item.quantity) {
                Text("Buy ${item.parQuantity - item.quantity} more to reach the amount you keep.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.lastChangeSummary.isNotEmpty()) {
                val who = item.lastChangedBy.ifBlank { "Someone" }
                Text("$who ${item.lastChangeSummary}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Field("Unit", item.unit.orEmpty()) { store.update(item.copy(unit = it.trim().ifBlank { null })) }
            val placeNames = listOf("None") + store.places.map { store.path(it.id) }
            val selectedPlace = item.locationId?.let { store.path(it) } ?: "None"
            MenuField("Location", selectedPlace, placeNames) { label ->
                val place = store.places.firstOrNull { store.path(it.id) == label }
                store.update(item.copy(locationId = place?.id))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuField("Currency", item.priceCurrency ?: "INR", listOf("INR", "USD", "EUR", "GBP", "AUD", "CAD"), Modifier.weight(0.4f)) {
                    store.update(item.copy(priceCurrency = it, priceDetectedAutomatically = false))
                }
                Field("Amount", item.priceAmount?.toString().orEmpty(), Modifier.weight(0.6f)) {
                    store.update(item.copy(priceAmount = it.toDoubleOrNull(), priceDetectedAutomatically = false, priceCurrency = item.priceCurrency ?: "INR"))
                }
            }
            item.priceAmount?.let { amount ->
                Text(formatPrice(amount, item.priceCurrency ?: "INR"), style = MaterialTheme.typography.titleMedium)
            }
            item.barcode?.let { code ->
                Column(Modifier.glass(16.dp).padding(12.dp)) {
                    Text(code, style = MaterialTheme.typography.bodyLarge)
                    item.barcodeSource?.let { Text("Matched via $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Perishable", Modifier.weight(1f))
                Switch(item.isPerishable, { store.update(item.copy(isPerishable = it, expiry = if (it) item.expiry else null)) })
            }
            if (item.isPerishable) {
                val days = daysUntilExpiry(item.expiry, store.today())
                OutlinedButton(onClick = {
                    dateState.selectedDateMillis = item.expiry?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
                    showDate = true
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(item.expiry ?: "Set expiry")
                }
                if (days != null) Text(if (days < 0) "${-days}d ago" else "${days}d left", color = expiryColor(status))
            }
            if (item.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.tags.forEach { tag -> Text(tag, Modifier.glass(20.dp).padding(horizontal = 10.dp, vertical = 4.dp)) }
                }
            }
            Field("Notes", item.notes.orEmpty()) { store.update(item.copy(notes = it.ifBlank { null })) }
            var tagDraft by remember(item.id) { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Add a tag", tagDraft, Modifier.weight(1f)) { tagDraft = it }
                TextButton(onClick = {
                    val tag = tagDraft.trim()
                    if (tag.isNotEmpty() && tag !in item.tags) store.update(item.copy(tags = item.tags + tag))
                    tagDraft = ""
                }, enabled = tagDraft.isNotBlank()) { Text("Add") }
            }
            item.recognizedText?.takeIf { it.isNotBlank() }?.let { found ->
                SectionTitle("Text found on the pack")
                Text(found, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.ingredients?.let { Field("Ingredients", it) { value -> store.update(item.copy(ingredients = value.ifBlank { null })) } }
            if (item.hasNutrition) NutritionBlock(item)
            OutlinedButton(onClick = onPhoto, modifier = Modifier.fillMaxWidth()) { Text("Add photo") }
            TextButton(onClick = { confirm = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirm && item != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete \"${item.name}\"?") },
            confirmButton = { TextButton(onClick = { store.delete(item); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
    if (showDate && item != null) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        store.update(item.copy(expiry = day.toString(), expiryUserConfirmed = true, isPerishable = true))
                    }
                    showDate = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(dateState) }
    }
}

@Composable
private fun PhotoPager(store: StashStore, item: StashItem) {
    if (item.photoFiles.isEmpty()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ItemThumbnail(store, item, 168.dp) }
        return
    }
    val pager = rememberPagerState { item.photoFiles.size }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.height(220.dp).fillMaxWidth()) { page ->
            ZoomPhoto(store, item.photoFiles[page])
        }
        if (item.photoFiles.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                item.photoFiles.indices.forEach { index ->
                    val color = if (pager.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                }
            }
        }
    }
}

@Composable
private fun ZoomPhoto(store: StashStore, name: String) {
    val pixels = with(LocalDensity.current) { 220.dp.roundToPx() } * 2
    var bitmap by remember(name) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(name) { bitmap = withContext(Dispatchers.IO) { store.thumbnail(name, pixels) } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image.asImageBitmap(), null, Modifier.size(220.dp).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun NutritionBlock(item: StashItem) {
    val rows = listOfNotNull(
        item.nutritionCalories?.let { "Calories" to "$it" },
        item.nutritionProteinGrams?.let { "Protein" to "${it} g" },
        item.nutritionCarbsGrams?.let { "Carbs" to "${it} g" },
        item.nutritionSugarGrams?.let { "Sugars" to "${it} g" },
        item.nutritionFatGrams?.let { "Fat" to "${it} g" },
        item.nutritionFiberGrams?.let { "Fibre" to "${it} g" },
        item.nutritionSodiumMilligrams?.let { "Sodium" to "${it} mg" },
    )
    Column(Modifier.glass(16.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Nutrition" + (item.nutritionServingSize?.let { " (per $it)" } ?: ""), style = MaterialTheme.typography.titleSmall)
        rows.forEach { (label, value) -> Text("$label  $value") }
        if (item.barcode == null && !item.barcodeSource.isNullOrBlank()) {
            Text("From ${item.barcodeSource}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Field(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = label != "Notes" && label != "Ingredients")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuField(label: String, value: String, options: List<String>, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); open = false })
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    val haptics = rememberHaptics()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: $value", Modifier.weight(1f))
        IconButton(onClick = { haptics.alignment(); onChange((value - 1).coerceAtLeast(0)) }) { Icon(Icons.Filled.Remove, "Less") }
        IconButton(onClick = { haptics.alignment(); onChange((value + 1).coerceAtMost(999)) }) { Icon(Icons.Filled.Add, "More") }
    }
}

fun formatPrice(amount: Double, currency: String): String = runCatching {
    NumberFormat.getCurrencyInstance().apply { this.currency = Currency.getInstance(currency) }.format(amount)
}.getOrElse { "$currency ${"%.2f".format(amount)}" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(store: StashStore, id: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val place = store.places.firstOrNull { it.id == id }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(place?.name ?: "Place") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        if (place == null) {
            Text("This place is gone.", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        val choices = listOf("None") + parentChoices(place.id, store.places).map { store.path(it.id) }
        val kept = store.items.filter { it.locationId == place.id }
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Name", place.name) { name ->
                if (name.isNotBlank()) store.updatePlace(place.copy(name = name))
            }
            MenuField("Inside", place.parentId?.let { store.path(it) } ?: "None", choices) { label ->
                val parent = store.places.firstOrNull { store.path(it.id) == label }
                store.updatePlace(place.copy(parentId = parent?.id))
            }
            if (kept.isEmpty()) EmptyCopy("Nothing kept here yet", "Items you store in ${place.name} show up here.")
            kept.forEach { item ->
                Pressable(onClick = { onOpen(item.id) }, modifier = Modifier.glass(16.dp).padding(10.dp)) { ItemRow(store, item) }
            }
        }
    }
}
