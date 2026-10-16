package com.piyushpatnaik.stashkeeper.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.piyushpatnaik.stashkeeper.data.StashStore
import com.piyushpatnaik.stashkeeper.data.ProductLookup
import com.piyushpatnaik.stashkeeper.data.ScanLiveUpdate
import com.piyushpatnaik.stashkeeper.logic.ReceiptLine
import com.piyushpatnaik.stashkeeper.logic.StashItem
import com.piyushpatnaik.stashkeeper.logic.itemCategoryOptions
import com.piyushpatnaik.stashkeeper.logic.expiryStatus
import com.piyushpatnaik.stashkeeper.logic.nutritionEligible
import com.piyushpatnaik.stashkeeper.logic.pantryRecipes
import com.piyushpatnaik.stashkeeper.logic.parseReceipt
import com.piyushpatnaik.stashkeeper.logic.readPack
import com.piyushpatnaik.stashkeeper.logic.suggestNameFromOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(
    store: StashStore,
    onScan: () -> Unit,
    onReceipt: () -> Unit,
    onPack: () -> Unit,
    onDismiss: () -> Unit,
    onPhotoBytes: ((ByteArray) -> Unit) -> Unit,
) {
    var typing by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(itemCategoryOptions.first()) }
    var quantity by remember { mutableIntStateOf(1) }
    var par by remember { mutableIntStateOf(0) }
    var place by remember { mutableStateOf(store.places.firstOrNull()?.name ?: "None") }
    var expiryOn by remember { mutableStateOf(false) }
    var expiry by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var price by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState()
    val places = listOf("None") + store.places.map { it.name }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add to your stash", style = MaterialTheme.typography.headlineSmall)
        if (!typing) {
            Text("How do you want to add it?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SheetChoice("Scan a barcode", "Best when the pack has a code.", onScan)
            SheetChoice("Photograph a receipt", "Turns each line into an item.", onReceipt)
            SheetChoice("Type a name", "For something already in your hand.", { typing = true })
            SheetChoice("Photograph the pack", "Reads the name, a printed date, and a nutrition panel.", onPack)
        } else {
            Field("Name", name) { name = it }
            MenuField("Place", place, places) { place = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantity", Modifier.weight(1f))
                QuantityStepper(quantity, { quantity = it }, 1..999)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Has an expiry date", Modifier.weight(1f))
                Switch(expiryOn, { expiryOn = it })
            }
            if (expiryOn) TextButton(onClick = { showDate = true }) { Text(expiry) }
            TextButton(onClick = { more = !more }) { Text(if (more) "Hide details" else "More details") }
            if (more) {
                MenuField("Category", category, itemCategoryOptions) { category = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep at least", Modifier.weight(1f))
                    QuantityStepper(par, { par = it })
                }
                Field("Price", price) { price = it.filter { char -> char.isDigit() || char == '.' } }
                Field("Notes", notes) { notes = it }
                TextButton(onClick = { onPhotoBytes { bytes -> photo = store.savePhoto(bytes) } }) {
                    Text(if (photo == null) "Add a photo" else "Photo added")
                }
            }
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) return@Button
                    val placeId = if (place == "None") null else store.ensurePlace(place)
                    store.add(
                        StashItem(
                            id = UUID.randomUUID().toString(),
                            name = trimmed,
                            category = category,
                            quantity = quantity,
                            parQuantity = par,
                            isPerishable = expiryOn,
                            expiry = if (expiryOn) expiry else null,
                            priceAmount = price.toDoubleOrNull(),
                            priceCurrency = if (price.toDoubleOrNull() == null) null else "INR",
                            notes = notes.ifBlank { null },
                            locationId = placeId,
                            photoFiles = listOfNotNull(photo),
                            lastChangedBy = store.householdName,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        }
    }
    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { expiry = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    showDate = false
                }) { Text("Save") }
            },
        ) { DatePicker(dateState) }
    }
}

@Composable
private fun SheetChoice(title: String, detail: String, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth().glass().padding(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(store: StashStore, onBack: () -> Unit, onBarcode: () -> Unit, onPhotoBytes: ((ByteArray) -> Unit) -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(itemCategoryOptions.first()) }
    var quantity by remember { mutableIntStateOf(1) }
    var par by remember { mutableIntStateOf(0) }
    var place by remember { mutableStateOf("None") }
    var expiryOn by remember { mutableStateOf(false) }
    var expiry by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var price by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState()
    val places = listOf("None") + store.places.map { it.name }
    FormScaffold("Add item", onBack) {
        Field("Name", name) { name = it }
        MenuField("Category", category, itemCategoryOptions) { category = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { quantity = (quantity - 1).coerceAtLeast(1) }) { Text("−") }
            Text("Quantity $quantity", Modifier.align(Alignment.CenterVertically))
            TextButton(onClick = { quantity = (quantity + 1).coerceAtMost(999) }) { Text("+") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { par = (par - 1).coerceAtLeast(0) }) { Text("−") }
            Text("Keep at least $par", Modifier.align(Alignment.CenterVertically))
            TextButton(onClick = { par = (par + 1).coerceAtMost(999) }) { Text("+") }
        }
        MenuField("Place", place, places) { place = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This lot has an expiry date", Modifier.weight(1f))
            Switch(expiryOn, { expiryOn = it })
        }
        if (expiryOn) TextButton(onClick = { showDate = true }) { Text(expiry) }
        Field("Price", price) { price = it.filter { char -> char.isDigit() || char == '.' } }
        Field("Notes", notes) { notes = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBarcode) { Text("Barcode") }
            TextButton(onClick = { onPhotoBytes { bytes -> photo = store.savePhoto(bytes) } }) { Text(if (photo == null) "Photo" else "Photo added") }
        }
        Button(
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) return@Button
                val placeId = if (place == "None") null else store.ensurePlace(place)
                store.add(
                    StashItem(
                        id = UUID.randomUUID().toString(),
                        name = trimmed,
                        category = category,
                        quantity = quantity,
                        parQuantity = par,
                        isPerishable = expiryOn,
                        expiry = if (expiryOn) expiry else null,
                        priceAmount = price.toDoubleOrNull(),
                        priceCurrency = if (price.toDoubleOrNull() == null) null else "INR",
                        notes = notes.ifBlank { null },
                        locationId = placeId,
                        photoFiles = listOfNotNull(photo),
                        lastChangedBy = store.householdName,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank(),
        ) { Text("Save") }
    }
    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { expiry = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    showDate = false
                }) { Text("Save") }
            },
        ) { DatePicker(dateState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeAddScreen(store: StashStore, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Pantry & Food") }
    var quantity by remember { mutableIntStateOf(1) }
    var place by remember { mutableStateOf("None") }
    var expiryOn by remember { mutableStateOf(false) }
    var expiry by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var status by remember { mutableStateOf("Point the camera at the barcode. A known product saves from here.") }
    var source by remember { mutableStateOf<String?>(null) }
    var looking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    fun lookup(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || looking) return
        looking = true
        status = "Checking catalogs…"
        scope.launch {
            val found = withContext(Dispatchers.IO) { store.lookup(trimmed) }
            looking = false
            if (found == null) {
                name = ""
                source = null
                status = "No catalog has this code. Type the name to save it."
            } else {
                code = found.barcode
                name = found.name
                category = found.category
                source = found.source
                status = "Matched via ${found.source}."
                haptics.success()
            }
        }
    }
    FormScaffold("Barcode", onBack) {
        CameraPreview(onCode = { scanned ->
            if (code.isBlank()) {
                code = scanned
                lookup(scanned)
            }
        })
        OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("Barcode") }, singleLine = true)
        TextButton(onClick = { lookup(code) }, enabled = !looking) { Text("Look up") }
        if (looking) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Field("Name", name) { name = it }
        MenuField("Category", category, itemCategoryOptions) { category = it }
        Row {
            TextButton(onClick = { quantity = (quantity - 1).coerceAtLeast(1) }) { Text("−") }
            Text("Quantity $quantity", Modifier.align(Alignment.CenterVertically))
            TextButton(onClick = { quantity = (quantity + 1).coerceAtMost(999) }) { Text("+") }
        }
        MenuField("Place", place, listOf("None") + store.places.map { it.name }) { place = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This lot has an expiry date", Modifier.weight(1f))
            Switch(expiryOn, { expiryOn = it })
        }
        if (expiryOn) Field("Expiry", expiry) { expiry = it }
        source?.let { Text("Matched via $it", style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                if (name.isBlank()) return@Button
                val message = store.saveLot(
                    StashItem(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        category = category,
                        quantity = quantity,
                        isPerishable = expiryOn,
                        expiry = if (expiryOn) expiry else null,
                        barcode = code.trim().ifBlank { null },
                        barcodeSource = source,
                        locationId = if (place == "None") null else store.ensurePlace(place),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                status = message
                if (message.startsWith("Saved") || message.startsWith("Added")) onBack()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { permission.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Text("Camera permission lets StashKeeper read a barcode. You can still type the code.")
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
            val future = ProcessCameraProvider.getInstance(viewContext)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val scanner = BarcodeScanning.getClient()
                var sent = false
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || sent) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { codes ->
                            val value = codes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                            if (!sent) {
                                sent = true
                                ContextCompat.getMainExecutor(viewContext).execute { onCode(value) }
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(viewContext))
            previewView
        },
        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(store: StashStore, onBack: () -> Unit) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<ReceiptLine>>(emptyList()) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var status by remember { mutableStateOf("Photograph a receipt. StashKeeper reads the lines on this device.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun read(uri: Uri) {
        busy = true
        scope.launch {
            ScanLiveUpdate.start(context, "Reading the receipt…")
            val bitmap = withContext(Dispatchers.IO) { decodeReceipt(context, uri) }
            if (bitmap == null) {
                lines = emptyList()
                status = "This photo couldn't be opened. HEIC and JPEG both work from Photos. Try the camera if this one stays blank."
                ScanLiveUpdate.end(context)
                busy = false
                return@launch
            }
            val text = readReceiptText(bitmap)
            val parsed = parseReceipt(text)
            if (parsed.isEmpty()) {
                lines = emptyList()
                status = receiptStatus(text, parsed)
                ScanLiveUpdate.finish(context, 0)
                busy = false
                return@launch
            }
            ScanLiveUpdate.update(context, "Found ${parsed.size} lines", 0.2f, parsed.size)
            lines = parsed
            picked = parsed.indices.toSet()
            lines = attachNutrition(context, parsed) { status = it }
            status = receiptStatus(text, lines)
            ScanLiveUpdate.finish(context, lines.size)
            busy = false
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            ScanLiveUpdate.start(context, "Reading the receipt…")
            val text = readReceiptText(scaleReceipt(bitmap, 2400))
            val parsed = parseReceipt(text)
            if (parsed.isEmpty()) {
                lines = emptyList()
                status = receiptStatus(text, parsed)
                ScanLiveUpdate.finish(context, 0)
                busy = false
                return@launch
            }
            ScanLiveUpdate.update(context, "Found ${parsed.size} lines", 0.2f, parsed.size)
            lines = parsed
            picked = parsed.indices.toSet()
            lines = attachNutrition(context, parsed) { status = it }
            status = receiptStatus(text, lines)
            ScanLiveUpdate.finish(context, lines.size)
            busy = false
        }
    }
    val gallery = rememberLauncherForActivityResult(StashImagePicker()) { uri ->
        if (uri != null) read(uri)
    }
    FormScaffold("Receipt", onBack) {
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { camera.launch(null) }) { Text("Camera") }
            TextButton(onClick = { gallery.launch(Unit) }) { Text("Photos") }
        }
        lines.forEachIndexed { index, line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(index in picked, { checked ->
                    picked = if (checked) picked + index else picked - index
                })
                Column {
                    Text(line.name)
                    Text(
                        buildString {
                            append("×${line.quantity}")
                            line.price?.let { append(" · ${formatPrice(it, "INR")}") }
                            append(" · ${line.category}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    nutritionCaption(line)?.let { caption ->
                        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Button(
            onClick = {
                lines.forEachIndexed { index, line ->
                    if (index !in picked) return@forEachIndexed
                    store.add(
                        StashItem(
                            id = UUID.randomUUID().toString(),
                            name = line.name,
                            category = line.category,
                            quantity = line.quantity,
                            priceAmount = line.price,
                            priceCurrency = if (line.price == null) null else "INR",
                            priceDetectedAutomatically = line.price != null,
                            barcodeSource = line.nutrition?.source,
                            nutritionServingSize = line.nutrition?.servingSize,
                            nutritionCalories = line.nutrition?.calories,
                            nutritionProteinGrams = line.nutrition?.proteinGrams,
                            nutritionCarbsGrams = line.nutrition?.carbsGrams,
                            nutritionSugarGrams = line.nutrition?.sugarGrams,
                            nutritionFatGrams = line.nutrition?.fatGrams,
                            nutritionFiberGrams = line.nutrition?.fiberGrams,
                            nutritionSodiumMilligrams = line.nutrition?.sodiumMilligrams,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
                onBack()
            },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val count = picked.size
            Text(if (count == 1) "Add 1 item" else "Add $count items")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackScreen(store: StashStore, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf<String?>(null) }
    var nutrition by remember { mutableStateOf<com.piyushpatnaik.stashkeeper.logic.NutritionFacts?>(null) }
    var ingredients by remember { mutableStateOf<String?>(null) }
    var recognized by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Photograph the package. A printed date and nutrition panel are kept. Nothing is guessed.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun apply(bitmap: android.graphics.Bitmap) {
        busy = true
        scope.launch {
            ScanLiveUpdate.start(context, "Reading the pack…")
            val text = readReceiptText(bitmap)
            val lines = text.lines()
            val read = readPack(lines)
            recognized = text
            suggestNameFromOcr(lines)?.let { if (name.isBlank()) name = it }
            expiry = read.expiry
            nutrition = read.nutrition
            ingredients = read.ingredients
            photo = withContext(Dispatchers.IO) {
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                store.savePhoto(stream.toByteArray())
            }
            status = when {
                text.isBlank() -> "Couldn't read this photo. Try a flatter, brighter shot of the pack."
                read.nutrition != null && read.expiry != null -> "Found a date and a nutrition panel."
                read.nutrition != null -> "Found a nutrition panel. No printed expiry date."
                read.expiry != null -> "Found a printed date. No nutrition panel."
                else -> "Kept the photo and the name. No date or nutrition panel was printed clearly."
            }
            ScanLiveUpdate.finish(context, if (name.isBlank() && text.isBlank()) 0 else 1)
            busy = false
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) apply(scaleReceipt(bitmap, 2400))
    }
    val gallery = rememberLauncherForActivityResult(StashImagePicker()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeReceipt(context, uri) } ?: return@launch
            apply(bitmap)
        }
    }
    FormScaffold("Pack", onBack) {
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { camera.launch(null) }) { Text("Camera") }
            TextButton(onClick = { gallery.launch(Unit) }) { Text("Photos") }
        }
        Field("Name", name) { name = it }
        Text(expiry?.let { "Printed expiry $it" } ?: "No printed expiry date", color = MaterialTheme.colorScheme.onSurfaceVariant)
        nutrition?.let { facts ->
            Text(
                listOfNotNull(facts.calories?.let { "$it kcal" }, facts.proteinGrams?.let { "$it g protein" }, facts.fatGrams?.let { "$it g fat" }).joinToString(" · ") + " per 100 g",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = {
                if (name.isBlank()) return@Button
                store.add(
                    StashItem(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        category = com.piyushpatnaik.stashkeeper.logic.receiptCategory(name),
                        isPerishable = expiry != null,
                        expiry = expiry,
                        expiryUserConfirmed = expiry != null,
                        recognizedText = recognized.ifBlank { null },
                        ingredients = ingredients,
                        photoFiles = listOfNotNull(photo),
                        nutritionServingSize = nutrition?.servingSize,
                        nutritionCalories = nutrition?.calories,
                        nutritionProteinGrams = nutrition?.proteinGrams,
                        nutritionCarbsGrams = nutrition?.carbsGrams,
                        nutritionSugarGrams = nutrition?.sugarGrams,
                        nutritionFatGrams = nutrition?.fatGrams,
                        nutritionFiberGrams = nutrition?.fiberGrams,
                        nutritionSodiumMilligrams = nutrition?.sodiumMilligrams,
                        barcodeSource = nutrition?.source,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                onBack()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}

private fun receiptStatus(text: String, lines: List<ReceiptLine>): String = when {
    text.isBlank() -> "Couldn't find any readable text on this photo. Try a clearer, well-lit shot of the receipt."
    lines.isEmpty() -> "No item lines stood out. The store heading was read, but not a row with a quantity and a price."
    else -> {
        val foods = lines.count { nutritionEligible(it.name, it.category) }
        val found = lines.count { it.nutrition?.published() == true }
        "${lines.size} item${if (lines.size == 1) "" else "s"}. Nutrition found for $found of $foods foods. Uncheck anything that is not yours."
    }
}

private suspend fun attachNutrition(
    context: android.content.Context,
    parsed: List<ReceiptLine>,
    onStatus: (String) -> Unit,
): List<ReceiptLine> {
    val current = parsed.toMutableList()
    val targets = current.indices.filter { nutritionEligible(current[it].name, current[it].category) }
    targets.forEachIndexed { step, index ->
        val message = "Checking nutrition ${step + 1} of ${targets.size}…"
        onStatus(message)
        val fraction = 0.2f + 0.75f * (step + 1) / targets.size.coerceAtLeast(1)
        ScanLiveUpdate.update(context, message, fraction, current.count { it.nutrition?.published() == true })
        val facts = withContext(Dispatchers.IO) { ProductLookup.nutritionForName(current[index].name) }
        if (facts != null) current[index] = current[index].copy(nutrition = facts)
    }
    return current
}

private fun nutritionCaption(line: ReceiptLine): String? {
    val facts = line.nutrition
    if (facts?.published() == true) {
        val parts = listOfNotNull(
            facts.calories?.let { "$it kcal" },
            facts.proteinGrams?.let { "${formatAmount(it)} g protein" },
            facts.fatGrams?.let { "${formatAmount(it)} g fat" },
            facts.carbsGrams?.let { "${formatAmount(it)} g carbs" },
        )
        return parts.joinToString(" · ") + " per ${facts.servingSize}"
    }
    return if (nutritionEligible(line.name, line.category)) "No published nutrition for this name" else null
}

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else ((value * 10).toInt() / 10.0).toString()

private suspend fun readReceiptText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        orderedReceiptText(result).ifBlank { result.text }
    } finally {
        recognizer.close()
    }
}

private fun orderedReceiptText(result: Text): String =
    result.textBlocks
        .flatMap { block -> block.lines }
        .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
        .joinToString("\n") { it.text }

private fun decodeReceipt(context: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= 28) {
        val decoded = runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val largest = maxOf(info.size.width, info.size.height)
                if (largest > 2400) {
                    val scale = 2400f / largest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }.getOrNull()
        if (decoded != null) return decoded
    }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)?.let { scaleReceipt(it, 2400) }
    }
}

private fun scaleReceipt(bitmap: Bitmap, maxEdge: Int): Bitmap {
    val largest = maxOf(bitmap.width, bitmap.height)
    if (largest <= maxEdge) return bitmap
    val scale = maxEdge.toFloat() / largest
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(store: StashStore, onBack: () -> Unit, cook: Boolean) {
    var draft by remember { mutableStateOf(if (cook) "what can I cook" else "") }
    FormScaffold(if (cook) "Cook" else "Ask", onBack) {
        if (cook) {
            val foods = store.items.filter { it.quantity > 0 }
            val expiring = foods.filter {
                val status = expiryStatus(it, store.today(), store.warningDays)
                status == com.piyushpatnaik.stashkeeper.logic.ExpiryStatus.ExpiringSoon || status == com.piyushpatnaik.stashkeeper.logic.ExpiryStatus.Expired
            }.map { it.name }
            val recipes = pantryRecipes(foods.map { it.name }, expiring)
            if (recipes.isEmpty()) {
                Text("No matching home recipe yet. Ask what you can cook from the names you keep.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            recipes.forEach { recipe ->
                Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                    Text(recipe.minutes, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(recipe.detail)
                    recipe.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                }
            }
        }
        store.chat.forEach { line ->
            Text(
                line.text,
                modifier = Modifier.glass(16.dp).padding(12.dp),
                color = if (line.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (store.chat.isEmpty()) {
            Text(
                "Ask what is expiring, what is in a place, how many you have, or what you can cook. Answers stay on this device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text("Message") })
        Row {
            Button(onClick = {
                val text = draft.trim()
                if (text.isEmpty()) return@Button
                store.sendChat(text)
                draft = ""
            }) { Text("Send") }
            TextButton(onClick = { store.clearChat() }) { Text("New conversation") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}
