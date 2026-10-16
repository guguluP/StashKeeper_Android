package com.piyushpatnaik.stashkeeper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import com.piyushpatnaik.stashkeeper.logic.PurchaseNeed
import com.piyushpatnaik.stashkeeper.logic.StashItem
import com.piyushpatnaik.stashkeeper.logic.StashPlace
import com.piyushpatnaik.stashkeeper.logic.adjustQuantity
import com.piyushpatnaik.stashkeeper.logic.attentionItems
import com.piyushpatnaik.stashkeeper.logic.clampWarningDays
import com.piyushpatnaik.stashkeeper.logic.findSameLot
import com.piyushpatnaik.stashkeeper.logic.handledEpoch
import com.piyushpatnaik.stashkeeper.logic.importStatus
import com.piyushpatnaik.stashkeeper.logic.mergeHousehold
import com.piyushpatnaik.stashkeeper.logic.noteChange
import com.piyushpatnaik.stashkeeper.logic.placePath
import com.piyushpatnaik.stashkeeper.logic.snoozeExpiry
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.max

data class UndoBatch(val items: List<StashItem>, val label: String)

data class ChatLine(val fromUser: Boolean, val text: String)

class StashStore(context: Context) {
    private val app = context.applicationContext
    private val file = File(app.filesDir, "stash.json")
    private val photos = File(app.filesDir, "photos").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("stashkeeper", Context.MODE_PRIVATE)
    

    val items = mutableStateListOf<StashItem>()
    val places = mutableStateListOf<StashPlace>()
    val needs = mutableStateListOf<PurchaseNeed>()
    var revision by mutableIntStateOf(0)
        private set
    var householdName by mutableStateOf("Me")
        private set
    var warningDays by mutableIntStateOf(com.piyushpatnaik.stashkeeper.logic.defaultWarningWindowDays)
        private set
    var buyListNotification by mutableStateOf(false)
        private set
    var streakCurrent by mutableIntStateOf(1)
        private set
    var streakLongest by mutableIntStateOf(1)
        private set
    var undo by mutableStateOf<UndoBatch?>(null)
        private set
    var chat by mutableStateOf<List<ChatLine>>(emptyList())
        private set

    private var streakDay: String = LocalDate.now().toString()

    init {
        load()
    }

    fun today(): LocalDate = LocalDate.now()

    fun placeName(id: String?): String = places.firstOrNull { it.id == id }?.name ?: "Unsorted"

    fun path(id: String?): String = placePath(id, places).ifBlank { "Unsorted" }

    fun attention(): List<StashItem> = attentionItems(items, today(), warningDays)

    fun unhandledAttentionCount(): Int = attention().count { !isHandled(it) }

    fun isHandled(item: StashItem): Boolean = handledMap()[item.id] == handledEpoch(item.expiry)

    fun setHandled(item: StashItem, handled: Boolean) {
        val map = handledMap().toMutableMap()
        if (handled) map[item.id] = handledEpoch(item.expiry) else map.remove(item.id)
        prefs.edit().putString(handledKey, JSONObject(map.mapValues { it.value }).toString()).apply()
        bump()
    }

    fun updateHouseholdName(name: String) {
        householdName = name.trim().ifEmpty { "Me" }
        prefs.edit().putString(nameKey, householdName).apply()
    }

    fun updateWarningDays(days: Int) {
        warningDays = clampWarningDays(days)
        prefs.edit().putInt(warningKey, warningDays).apply()
        bump()
        ExpiryScheduler.sync(app)
    }

    fun updateBuyListNotification(enabled: Boolean) {
        buyListNotification = enabled
        prefs.edit().putBoolean(buyKey, enabled).apply()
        ExpiryScheduler.sync(app)
    }

    fun recordToday() {
        val today = today().toString()
        if (streakDay == today) return
        val previous = runCatching { LocalDate.parse(streakDay) }.getOrNull()
        val now = today()
        streakCurrent = if (previous != null && previous.plusDays(1) == now) streakCurrent + 1 else 1
        streakLongest = max(streakLongest, streakCurrent)
        streakDay = today
        touch()
    }

    fun add(item: StashItem) {
        items.add(0, item)
        learn(item)
        touch()
    }

    fun update(item: StashItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) items[index] = item.copy(updatedAt = System.currentTimeMillis())
        learn(item)
        touch()
    }

    fun adjust(item: StashItem, delta: Int) {
        update(adjustQuantity(item, delta, householdName, System.currentTimeMillis()))
    }

    fun consume(item: StashItem) = adjust(item, -1)

    fun snooze(item: StashItem) {
        update(snoozeExpiry(item, 1, today(), System.currentTimeMillis()))
        setHandled(item, false)
    }

    fun saveLot(item: StashItem): String {
        val match = findSameLot(items, item.barcode.orEmpty(), item.expiry)
        if (match != null && match.id != item.id) {
            val added = (match.quantity + item.quantity).coerceAtMost(999) - match.quantity
            update(noteChange(match.copy(quantity = match.quantity + added), "added $added", householdName, System.currentTimeMillis()))
            return "Added to the lot you already keep."
        }
        add(item)
        return "Saved."
    }

    fun delete(item: StashItem) {
        items.removeAll { it.id == item.id }
        undo = UndoBatch(listOf(item), "Deleted ${item.name}")
        touch()
    }

    fun undoDelete() {
        val batch = undo ?: return
        batch.items.forEach { item ->
            if (items.none { it.id == item.id }) items.add(0, item)
        }
        undo = null
        touch()
    }

    fun dismissUndo() {
        val batch = undo
        undo = null
        batch?.items?.flatMap { it.photoFiles }?.forEach { name ->
            if (items.none { name in it.photoFiles }) File(photos, name).delete()
        }
    }

    fun ensurePlace(name: String, parentId: String? = null, icon: String = "archivebox"): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return places.firstOrNull()?.id ?: newPlace("Home", null, icon)
        places.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.id }
        return newPlace(trimmed, parentId, icon)
    }

    fun newPlace(name: String, parentId: String? = null, icon: String = "archivebox"): String {
        val place = StashPlace(UUID.randomUUID().toString(), name.trim(), icon, parentId, places.size)
        places.add(place)
        touch()
        return place.id
    }

    fun updatePlace(place: StashPlace) {
        val index = places.indexOfFirst { it.id == place.id }
        if (index >= 0) places[index] = place
        touch()
    }

    fun deletePlace(id: String) {
        places.removeAll { it.id == id }
        val cleared = places.map { if (it.parentId == id) it.copy(parentId = null) else it }
        places.clear()
        places.addAll(cleared)
        val moved = items.map { if (it.locationId == id) it.copy(locationId = null) else it }
        items.clear()
        items.addAll(moved)
        touch()
    }

    fun movePlace(id: String, up: Boolean) {
        val ordered = places.sortedBy { it.sortIndex }.toMutableList()
        val index = ordered.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in ordered.indices) return
        val swap = ordered[index]
        ordered[index] = ordered[target]
        ordered[target] = swap
        places.clear()
        places.addAll(ordered.mapIndexed { position, place -> place.copy(sortIndex = position) })
        touch()
    }

    fun addNeed(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        needs.add(0, PurchaseNeed(UUID.randomUUID().toString(), trimmed, createdAt = System.currentTimeMillis()))
        touch()
    }

    fun checkNeed(id: String) {
        val index = needs.indexOfFirst { it.id == id }
        if (index >= 0) needs[index] = needs[index].copy(isChecked = true)
        touch()
    }

    fun markParBought(item: StashItem) {
        val target = max(item.quantity, item.parQuantity)
        val added = target - item.quantity
        if (added > 0) update(noteChange(item.copy(quantity = target), "added $added", householdName, System.currentTimeMillis()))
    }

    fun savePhoto(bytes: ByteArray): String {
        val upright = uprightJpeg(bytes)
        val name = "${UUID.randomUUID()}.jpg"
        File(photos, name).writeBytes(upright)
        return name
    }

    fun photoFile(name: String?): File? = name?.let { File(photos, it) }?.takeIf { it.isFile }

    fun thumbnail(name: String?, pixels: Int): Bitmap? {
        val file = photoFile(name) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > pixels * 2 && bounds.outHeight / sample > pixels * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    fun lookup(raw: String): LookedUpProduct? {
        knownInStash(raw)?.let { known ->
            learnBarcode(raw, known.name, known.category)
            return known
        }
        val (product, record) = ProductLookup.lookup(raw, learned())
        rememberLookup(record)
        return product
    }

    fun lookupRecords(): List<LookupRecord> = readLookups()

    fun clearLookups() {
        prefs.edit().remove(lookupKey).apply()
        bump()
    }

    fun celebrated(): Set<String> = prefs.getStringSet(celebratedKey, emptySet()).orEmpty()

    fun celebrate(ids: Collection<String>) {
        if (ids.isEmpty()) return
        prefs.edit().putStringSet(celebratedKey, celebrated() + ids).apply()
    }

    fun sendChat(message: String): String {
        val reply = com.piyushpatnaik.stashkeeper.logic.assistantReply(message, items.toList(), places.toList(), today(), warningDays)
        chat = (chat + ChatLine(true, message) + ChatLine(false, reply)).takeLast(40)
        saveChat()
        return reply
    }

    fun clearChat() {
        chat = emptyList()
        saveChat()
    }

    fun exportJson(): String = householdJson(includePhotos = true)

    suspend fun importHousehold(text: String): String {
        val existingPhotos = items.associate { it.id to it.photoFiles }
        val parsed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            decodeBackup(text, existingPhotos)
        }
        val outcome = mergeHousehold(items.toList(), places.toList(), parsed.items, parsed.places)
        items.clear()
        items.addAll(outcome.items)
        places.clear()
        places.addAll(outcome.places)
        touch()
        return importStatus(outcome.inserted, outcome.updated)
    }

    private fun bump() {
        revision++
    }

    private fun touch() {
        bump()
        synchronized(fileLock) { file.writeText(localJson()) }
        ExpiryScheduler.sync(app)
    }

    private fun learn(item: StashItem) {
        learnBarcode(item.barcode.orEmpty(), item.name, item.category)
    }

    private fun learnBarcode(raw: String, name: String, category: String) {
        val cleaned = name.trim()
        val forms = com.piyushpatnaik.stashkeeper.logic.barcodeCandidates(raw)
        if (cleaned.isEmpty() || forms.isEmpty()) return
        val map = learnedJson()
        val row = JSONObject()
            .put("name", cleaned)
            .put("category", category)
            .put("source", "Learned on this device")
        for (form in forms) map.put(form, row)
        prefs.edit().putString(learnedKey, map.toString()).apply()
    }

    private fun knownInStash(raw: String): LookedUpProduct? {
        val wanted = com.piyushpatnaik.stashkeeper.logic.barcodeCandidates(raw).toSet()
        if (wanted.isEmpty()) return null
        val matches = items.filter { item ->
            val code = item.barcode ?: return@filter false
            com.piyushpatnaik.stashkeeper.logic.barcodeCandidates(code).any { it in wanted }
        }
        if (matches.map { it.name }.toSet().size != 1) return null
        val item = matches.first()
        return LookedUpProduct(
            name = item.name,
            category = item.category,
            source = "Already in your stash",
            barcode = item.barcode.orEmpty(),
        )
    }

    private fun learned(): Map<String, LookedUpProduct> {
        val map = learnedJson()
        return map.keys().asSequence().associate { key ->
            val row = map.getJSONObject(key)
            key to LookedUpProduct(
                name = row.optString("name"),
                category = row.optString("category", "Other"),
                source = row.optString("source", "Learned on this device"),
                barcode = key,
            )
        }
    }

    private fun learnedJson(): JSONObject =
        runCatching { JSONObject(prefs.getString(learnedKey, null) ?: "{}") }.getOrDefault(JSONObject())

    private fun rememberLookup(record: LookupRecord) {
        val rows = JSONArray()
        readLookups().takeLast(199).forEach { rows.put(recordJson(it)) }
        rows.put(recordJson(record))
        prefs.edit().putString(lookupKey, rows.toString()).apply()
    }

    private fun readLookups(): List<LookupRecord> {
        val array = runCatching { JSONArray(prefs.getString(lookupKey, "[]")) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val outcome = runCatching { LookupOutcome.valueOf(row.optString("outcome")) }.getOrNull() ?: continue
                add(LookupRecord(row.optString("barcode"), outcome, row.optString("source").ifBlank { null }, row.optLong("at")))
            }
        }
    }

    private fun recordJson(record: LookupRecord): JSONObject = JSONObject()
        .put("barcode", record.barcode)
        .put("outcome", record.outcome.name)
        .put("source", record.source ?: "")
        .put("at", record.at)

    private fun handledMap(): Map<String, Double> {
        val root = runCatching { JSONObject(prefs.getString(handledKey, null) ?: "{}") }.getOrDefault(JSONObject())
        return root.keys().asSequence().associateWith { root.optDouble(it) }
    }

    private fun saveChat() {
        val array = JSONArray()
        chat.forEach { line ->
            array.put(JSONObject().put("fromUser", line.fromUser).put("text", line.text))
        }
        prefs.edit().putString(chatKey, array.toString()).apply()
    }

    private fun loadChat() {
        val array = runCatching { JSONArray(prefs.getString(chatKey, "[]")) }.getOrDefault(JSONArray())
        chat = buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                add(ChatLine(row.optBoolean("fromUser"), row.optString("text")))
            }
        }
    }

    private fun load() {
        householdName = prefs.getString(nameKey, null)?.trim()?.ifEmpty { null } ?: "Me"
        warningDays = clampWarningDays(prefs.getInt(warningKey, com.piyushpatnaik.stashkeeper.logic.defaultWarningWindowDays))
        buyListNotification = prefs.getBoolean(buyKey, false)
        loadChat()
        val text = synchronized(fileLock) { if (file.exists()) file.readText() else return }
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (root.has("exportedAt")) {
            val parsed = decodeBackup(root.toString(), emptyMap())
            items.addAll(parsed.items)
            places.addAll(parsed.places)
            return
        }
        streakCurrent = root.optJSONObject("streak")?.optInt("current", 1) ?: 1
        streakLongest = root.optJSONObject("streak")?.optInt("longest", streakCurrent) ?: streakCurrent
        streakDay = root.optJSONObject("streak")?.optString("lastDay").orEmpty().ifBlank { today().toString() }
        val locationRows = root.optJSONArray("locations") ?: JSONArray()
        for (index in 0 until locationRows.length()) {
            val row = locationRows.optJSONObject(index) ?: continue
            places += StashPlace(
                id = row.optString("id", UUID.randomUUID().toString()),
                name = row.optString("name", "Place"),
                icon = row.optString("icon", row.optString("iconSystemName", "archivebox")),
                parentId = row.optString("parentId", row.optString("parentID")).ifBlank { null },
                sortIndex = row.optInt("sortIndex", index),
            )
        }
        val needRows = root.optJSONArray("needs") ?: JSONArray()
        for (index in 0 until needRows.length()) {
            val row = needRows.optJSONObject(index) ?: continue
            needs += PurchaseNeed(
                id = row.optString("id"),
                name = row.optString("name"),
                quantity = row.optInt("quantity", 1),
                createdAt = row.optLong("createdAt"),
                isChecked = row.optBoolean("isChecked"),
            )
        }
        val itemRows = root.optJSONArray("items") ?: JSONArray()
        for (index in 0 until itemRows.length()) {
            val row = itemRows.optJSONObject(index) ?: continue
            items += if (row.has("barcodePayload") || row.has("locationID")) {
                itemFromHousehold(row, keepExistingPhotos = false)
            } else {
                itemFromLocal(row)
            }
        }
    }

    private fun localJson(): String {
        val root = JSONObject()
        root.put("version", 2)
        val locationRows = JSONArray()
        places.forEach { place ->
            locationRows.put(
                JSONObject()
                    .put("id", place.id)
                    .put("name", place.name)
                    .put("icon", place.icon)
                    .put("parentId", place.parentId ?: "")
                    .put("sortIndex", place.sortIndex)
            )
        }
        val itemRows = JSONArray()
        items.forEach { item -> itemRows.put(localItem(item)) }
        val needRows = JSONArray()
        needs.forEach { need ->
            needRows.put(
                JSONObject()
                    .put("id", need.id)
                    .put("name", need.name)
                    .put("quantity", need.quantity)
                    .put("createdAt", need.createdAt)
                    .put("isChecked", need.isChecked)
            )
        }
        root.put("locations", locationRows)
        root.put("items", itemRows)
        root.put("needs", needRows)
        root.put(
            "streak",
            JSONObject().put("current", streakCurrent).put("longest", streakLongest).put("lastDay", streakDay),
        )
        return root.toString()
    }

    private fun localItem(item: StashItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("category", item.category)
        .put("subcategory", item.subcategory ?: "")
        .put("quantity", item.quantity)
        .put("unit", item.unit ?: "")
        .put("parQuantity", item.parQuantity)
        .put("isPerishable", item.isPerishable)
        .put("expiry", item.expiry ?: "")
        .put("expiryUserConfirmed", item.expiryUserConfirmed)
        .put("expiryConfidence", item.expiryConfidence ?: JSONObject.NULL)
        .put("notes", item.notes ?: "")
        .put("recognizedText", item.recognizedText ?: "")
        .put("tags", JSONArray(item.tags))
        .put("priceAmount", item.priceAmount ?: JSONObject.NULL)
        .put("priceCurrency", item.priceCurrency ?: "")
        .put("priceDetectedAutomatically", item.priceDetectedAutomatically)
        .put("barcode", item.barcode ?: "")
        .put("barcodeSource", item.barcodeSource ?: "")
        .put("locationId", item.locationId ?: "")
        .put("photoFiles", JSONArray(item.photoFiles))
        .put("nutritionServingSize", item.nutritionServingSize ?: "")
        .put("nutritionCalories", item.nutritionCalories ?: JSONObject.NULL)
        .put("nutritionProteinGrams", item.nutritionProteinGrams ?: JSONObject.NULL)
        .put("nutritionCarbsGrams", item.nutritionCarbsGrams ?: JSONObject.NULL)
        .put("nutritionSugarGrams", item.nutritionSugarGrams ?: JSONObject.NULL)
        .put("nutritionFatGrams", item.nutritionFatGrams ?: JSONObject.NULL)
        .put("nutritionFiberGrams", item.nutritionFiberGrams ?: JSONObject.NULL)
        .put("nutritionSodiumMilligrams", item.nutritionSodiumMilligrams ?: JSONObject.NULL)
        .put("ingredients", item.ingredients ?: "")
        .put("lastChangedBy", item.lastChangedBy)
        .put("lastChangeSummary", item.lastChangeSummary)
        .put("createdAt", item.createdAt)
        .put("updatedAt", item.updatedAt)

    private fun itemFromLocal(row: JSONObject): StashItem {
        val photos = stringList(row.optJSONArray("photoFiles")).ifEmpty {
            listOfNotNull(row.optString("photoFile").ifBlank { null })
        }
        return StashItem(
            id = row.optString("id", UUID.randomUUID().toString()),
            name = row.optString("name", "Item"),
            category = row.optString("category", "Other"),
            subcategory = row.optString("subcategory").ifBlank { null },
            quantity = row.optInt("quantity", 1),
            unit = row.optString("unit").ifBlank { null },
            parQuantity = row.optInt("parQuantity", 0),
            isPerishable = row.optBoolean("isPerishable", row.optString("expiry").isNotBlank()),
            expiry = row.optString("expiry").take(10).ifBlank { null },
            expiryUserConfirmed = row.optBoolean("expiryUserConfirmed"),
            expiryConfidence = row.optDouble("expiryConfidence").takeIf { row.has("expiryConfidence") && !row.isNull("expiryConfidence") },
            notes = row.optString("notes").ifBlank { null },
            recognizedText = row.optString("recognizedText").ifBlank { null },
            tags = stringList(row.optJSONArray("tags")),
            priceAmount = row.optDouble("priceAmount").takeIf { row.has("priceAmount") && !row.isNull("priceAmount") },
            priceCurrency = row.optString("priceCurrency").ifBlank { null },
            priceDetectedAutomatically = row.optBoolean("priceDetectedAutomatically"),
            barcode = row.optString("barcode").ifBlank { null },
            barcodeSource = row.optString("barcodeSource").ifBlank { null },
            locationId = row.optString("locationId", row.optString("placeId")).ifBlank { null },
            photoFiles = photos,
            nutritionServingSize = row.optString("nutritionServingSize").ifBlank { null },
            nutritionCalories = if (row.has("nutritionCalories") && !row.isNull("nutritionCalories")) row.optInt("nutritionCalories") else null,
            nutritionProteinGrams = optionalDouble(row, "nutritionProteinGrams"),
            nutritionCarbsGrams = optionalDouble(row, "nutritionCarbsGrams"),
            nutritionSugarGrams = optionalDouble(row, "nutritionSugarGrams"),
            nutritionFatGrams = optionalDouble(row, "nutritionFatGrams"),
            nutritionFiberGrams = optionalDouble(row, "nutritionFiberGrams"),
            nutritionSodiumMilligrams = optionalDouble(row, "nutritionSodiumMilligrams"),
            ingredients = row.optString("ingredients").ifBlank { null },
            lastChangedBy = row.optString("lastChangedBy"),
            lastChangeSummary = row.optString("lastChangeSummary"),
            createdAt = row.optLong("createdAt"),
            updatedAt = row.optLong("updatedAt"),
        )
    }

    fun householdJson(includePhotos: Boolean): String {
        val root = JSONObject()
        root.put("exportedAt", Instant.now().toString())
        val locationRows = JSONArray()
        places.sortedBy { it.sortIndex }.forEach { place ->
            val row = JSONObject()
                .put("id", place.id)
                .put("name", place.name)
                .put("iconSystemName", place.icon)
            place.parentId?.let { row.put("parentID", it) }
            locationRows.put(row)
        }
        val itemRows = JSONArray()
        items.forEach { item ->
            val row = JSONObject()
            row.put("id", item.id)
            row.put("name", item.name)
            row.put("category", item.category)
            item.subcategory?.let { row.put("subcategory", it) }
            row.put("quantity", item.quantity)
            item.unit?.let { row.put("unit", it) }
            row.put("isPerishable", item.isPerishable)
            item.expiry?.let { row.put("expiryDate", "${it}T00:00:00Z") }
            item.notes?.let { row.put("notes", it) }
            row.put("tags", JSONArray(item.tags))
            item.recognizedText?.let { row.put("recognizedText", it) }
            item.priceAmount?.let { row.put("priceAmount", it) }
            item.priceCurrency?.let { row.put("priceCurrency", it) }
            row.put("priceDetectedAutomatically", item.priceDetectedAutomatically)
            item.barcode?.let { row.put("barcodePayload", it) }
            item.barcodeSource?.let { row.put("barcodeLookupSource", it) }
            row.put("expiryUserConfirmed", item.expiryUserConfirmed)
            item.locationId?.let { row.put("locationID", it) }
            row.put("photoFilenames", JSONArray(item.photoFiles))
            item.nutritionServingSize?.let { row.put("nutritionServingSize", it) }
            item.nutritionCalories?.let { row.put("nutritionCalories", it) }
            item.nutritionProteinGrams?.let { row.put("nutritionProteinGrams", it) }
            item.nutritionCarbsGrams?.let { row.put("nutritionCarbsGrams", it) }
            item.nutritionSugarGrams?.let { row.put("nutritionSugarGrams", it) }
            item.nutritionFatGrams?.let { row.put("nutritionFatGrams", it) }
            item.nutritionFiberGrams?.let { row.put("nutritionFiberGrams", it) }
            item.nutritionSodiumMilligrams?.let { row.put("nutritionSodiumMilligrams", it) }
            item.ingredients?.let { row.put("ingredients", it) }
            if (includePhotos) {
                val blobs = JSONArray()
                item.photoFiles.mapNotNull { photoFile(it)?.readBytes() }.forEach { bytes ->
                    blobs.put(Base64.encodeToString(bytes, Base64.NO_WRAP))
                }
                if (blobs.length() > 0) row.put("photoFiles", blobs)
            }
            itemRows.put(row)
        }
        root.put("locations", locationRows)
        root.put("items", itemRows)
        return root.toString(2)
    }

    private data class ParsedBackup(val items: List<StashItem>, val places: List<StashPlace>)

    private fun decodeBackup(text: String, existingPhotos: Map<String, List<String>>): ParsedBackup {
        val root = JSONObject(text)
        val places = mutableListOf<StashPlace>()
        val locationRows = root.optJSONArray("locations") ?: JSONArray()
        for (index in 0 until locationRows.length()) {
            val row = locationRows.optJSONObject(index) ?: continue
            places += StashPlace(
                id = row.optString("id", UUID.randomUUID().toString()),
                name = row.optString("name", "Place"),
                icon = row.optString("iconSystemName", row.optString("icon", "archivebox")),
                parentId = row.optString("parentID", row.optString("parentId")).ifBlank { null },
                sortIndex = row.optInt("sortIndex", index),
            )
        }
        val items = mutableListOf<StashItem>()
        val itemRows = root.optJSONArray("items") ?: JSONArray()
        for (index in 0 until itemRows.length()) {
            val row = itemRows.optJSONObject(index) ?: continue
            val id = row.optString("id", UUID.randomUUID().toString())
            val keep = existingPhotos[id].orEmpty()
            items += if (row.has("barcodePayload") || row.has("locationID") || row.has("expiryDate") || row.has("photoFilenames")) {
                itemFromHousehold(row, keepExistingPhotos = keep.isNotEmpty()).let { item ->
                    if (keep.isNotEmpty()) item.copy(photoFiles = keep) else item
                }
            } else {
                itemFromLocal(row)
            }
        }
        return ParsedBackup(items, places)
    }

    private fun itemFromHousehold(row: JSONObject, keepExistingPhotos: Boolean): StashItem {
        val id = row.optString("id", UUID.randomUUID().toString())
        val photos = if (keepExistingPhotos) {
            emptyList()
        } else {
            restorePhotos(row.optJSONArray("photoFiles") ?: row.optJSONArray("photoJPEGs"))
                .ifEmpty { stringList(row.optJSONArray("photoFilenames")).filter { photoFile(it) != null } }
        }
        val expiry = row.optString("expiryDate", row.optString("expiry")).take(10).ifBlank { null }
        return StashItem(
            id = id,
            name = row.optString("name", "Item"),
            category = row.optString("category", "Other"),
            subcategory = row.optString("subcategory").ifBlank { null },
            quantity = row.optInt("quantity", 1),
            unit = row.optString("unit").ifBlank { null },
            isPerishable = row.optBoolean("isPerishable", expiry != null),
            expiry = expiry,
            expiryUserConfirmed = row.optBoolean("expiryUserConfirmed"),
            notes = row.optString("notes").ifBlank { null },
            recognizedText = row.optString("recognizedText").ifBlank { null },
            tags = stringList(row.optJSONArray("tags")),
            priceAmount = optionalDouble(row, "priceAmount"),
            priceCurrency = row.optString("priceCurrency").ifBlank { null },
            priceDetectedAutomatically = row.optBoolean("priceDetectedAutomatically"),
            barcode = row.optString("barcodePayload", row.optString("barcode")).ifBlank { null },
            barcodeSource = row.optString("barcodeLookupSource", row.optString("barcodeSource")).ifBlank { null },
            locationId = row.optString("locationID", row.optString("locationId")).ifBlank { null },
            photoFiles = photos,
            nutritionServingSize = row.optString("nutritionServingSize").ifBlank { null },
            nutritionCalories = if (row.has("nutritionCalories") && !row.isNull("nutritionCalories")) row.optInt("nutritionCalories") else null,
            nutritionProteinGrams = optionalDouble(row, "nutritionProteinGrams"),
            nutritionCarbsGrams = optionalDouble(row, "nutritionCarbsGrams"),
            nutritionSugarGrams = optionalDouble(row, "nutritionSugarGrams"),
            nutritionFatGrams = optionalDouble(row, "nutritionFatGrams"),
            nutritionFiberGrams = optionalDouble(row, "nutritionFiberGrams"),
            nutritionSodiumMilligrams = optionalDouble(row, "nutritionSodiumMilligrams"),
            ingredients = row.optString("ingredients").ifBlank { null },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun restorePhotos(blobs: JSONArray?): List<String> {
        if (blobs == null) return emptyList()
        val names = mutableListOf<String>()
        for (index in 0 until blobs.length()) {
            val text = blobs.optString(index)
            if (text.length < 32 || text.endsWith(".jpg")) continue
            val bytes = runCatching { Base64.decode(text, Base64.DEFAULT) }.getOrNull() ?: continue
            if (bytes.isEmpty()) continue
            names += savePhoto(bytes)
        }
        return names
    }

    private fun uprightJpeg(bytes: ByteArray): ByteArray {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bytes
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val stream = java.io.ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        if (rotated !== bitmap) rotated.recycle()
        bitmap.recycle()
        return stream.toByteArray()
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun optionalDouble(row: JSONObject, key: String): Double? =
        if (row.has(key) && !row.isNull(key)) row.optDouble(key).takeIf { !it.isNaN() } else null

    companion object {
        private val fileLock = Any()
        private const val nameKey = "stashkeeper.householdName"
        private const val warningKey = "stashkeeper.warningWindowDays"
        private const val buyKey = "stashkeeper.buyListNotification"
        private const val handledKey = "StashKeeper.shoppingHandled"
        private const val learnedKey = "stashkeeper.learnedBarcodes"
        private const val lookupKey = "StashKeeper.barcodeLookupTelemetry"
        private const val celebratedKey = "StashKeeper.celebratedMilestoneIDs"
        private const val chatKey = "StashKeeper.chatHistory"
    }
}

fun routeOf(intent: android.content.Intent?): String? {
    val extra = intent?.getStringExtra("route")
    if (!extra.isNullOrBlank()) return extra
    val data = intent?.data ?: return null
    if (data.scheme != "stashkeeper") return null
    return when (data.host) {
        "add" -> "add"
        "shopping" -> "shopping"
        "item" -> data.lastPathSegment?.let { "item/$it" }
        else -> null
    }
}
