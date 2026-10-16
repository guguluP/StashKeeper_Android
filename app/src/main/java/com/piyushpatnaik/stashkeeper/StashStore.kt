package com.piyushpatnaik.stashkeeper

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class StashPlace(val id: String, var name: String)

data class StashItem(
    val id: String,
    var name: String,
    var category: String,
    var quantity: Int,
    var placeId: String?,
    var expiry: String?,
    var barcode: String?,
    var notes: String?,
    var photoFile: String?,
)

class StashStore(context: Context) {
    private val file = File(context.filesDir, "stash.json")
    private val photos = File(context.filesDir, "photos").apply { mkdirs() }
    val items = mutableStateListOf<StashItem>()
    val places = mutableStateListOf<StashPlace>()

    init { load() }

    fun placeName(id: String?): String = places.firstOrNull { it.id == id }?.name ?: "Unsorted"

    fun itemForBarcode(code: String): StashItem? {
        val digits = code.filter { it.isDigit() }
        if (digits.length < 8) return null
        return items.firstOrNull { item ->
            val stored = item.barcode?.filter { it.isDigit() }.orEmpty()
            stored == digits || stored.padStart(14, '0') == digits.padStart(14, '0')
        }
    }

    fun attention(): List<StashItem> {
        val today = LocalDate.now()
        return items.filter { item ->
            val date = item.expiry?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@filter false
            !date.isAfter(today.plusDays(7))
        }.sortedBy { it.expiry }
    }

    fun add(item: StashItem) {
        items.add(0, item)
        save()
    }

    fun update(item: StashItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) items[index] = item
        save()
    }

    fun delete(id: String) {
        items.removeAll { it.id == id }
        save()
    }

    fun ensurePlace(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return places.firstOrNull()?.id ?: newPlace("Home")
        places.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.id }
        return newPlace(trimmed)
    }

    fun newPlace(name: String): String {
        val place = StashPlace(UUID.randomUUID().toString(), name)
        places.add(place)
        save()
        return place.id
    }

    fun exportJson(): String = toBackup().toString(2)

    fun importJson(text: String) {
        val root = JSONObject(text)
        val locationArray = root.optJSONArray("locations") ?: JSONArray()
        val importedPlaces = mutableListOf<StashPlace>()
        for (index in 0 until locationArray.length()) {
            val row = locationArray.getJSONObject(index)
            importedPlaces.add(StashPlace(row.getString("id"), row.optString("name", "Place")))
        }
        if (importedPlaces.isNotEmpty()) {
            places.clear()
            places.addAll(importedPlaces)
        }
        val itemArray = root.optJSONArray("items") ?: JSONArray()
        items.clear()
        for (index in 0 until itemArray.length()) {
            items.add(itemFrom(itemArray.getJSONObject(index)))
        }
        save()
    }

    fun savePhoto(bytes: ByteArray): String {
        val name = "${UUID.randomUUID()}.jpg"
        File(photos, name).writeBytes(bytes)
        return name
    }

    fun photoFile(name: String?): File? = name?.let { File(photos, it) }?.takeIf { it.exists() }

    private fun itemFrom(row: JSONObject): StashItem {
        val files = row.optJSONArray("photoFiles") ?: row.optJSONArray("photoJPEGs")
        var photo: String? = null
        if (files != null && files.length() > 0) {
            val bytes = Base64.decode(files.getString(0), Base64.DEFAULT)
            if (bytes.isNotEmpty()) photo = savePhoto(bytes)
        }
        val expiry = row.optString("expiryDate", "").take(10).ifBlank { null }
        return StashItem(
            id = row.optString("id", UUID.randomUUID().toString()),
            name = row.optString("name", "Item"),
            category = row.optString("category", "Other"),
            quantity = row.optInt("quantity", 1),
            placeId = row.optString("locationID").ifBlank { null },
            expiry = expiry,
            barcode = row.optString("barcodePayload").ifBlank { null },
            notes = row.optString("notes").ifBlank { null },
            photoFile = photo,
        )
    }

    private fun toBackup(): JSONObject {
        val root = JSONObject()
        root.put("exportedAt", Instant.now().toString())
        val locationRows = JSONArray()
        for (place in places) {
            locationRows.put(JSONObject().put("id", place.id).put("name", place.name).put("iconSystemName", "archivebox"))
        }
        val itemRows = JSONArray()
        for (item in items) {
            val row = JSONObject()
            row.put("id", item.id)
            row.put("name", item.name)
            row.put("category", item.category)
            row.put("quantity", item.quantity)
            row.put("isPerishable", item.expiry != null)
            row.put("expiryUserConfirmed", false)
            row.put("priceDetectedAutomatically", false)
            row.put("tags", JSONArray())
            row.put("photoFilenames", JSONArray())
            item.placeId?.let { row.put("locationID", it) }
            item.expiry?.let { row.put("expiryDate", "${it}T00:00:00Z") }
            item.barcode?.let { row.put("barcodePayload", it) }
            item.notes?.let { row.put("notes", it) }
            photoFile(item.photoFile)?.readBytes()?.let { bytes ->
                row.put("photoFiles", JSONArray().put(Base64.encodeToString(bytes, Base64.NO_WRAP)))
            }
            itemRows.put(row)
        }
        root.put("locations", locationRows)
        root.put("items", itemRows)
        return root
    }

    private fun load() {
        if (!file.exists()) return
        val root = JSONObject(file.readText())
        places.clear()
        items.clear()
        val locationArray = root.optJSONArray("locations") ?: JSONArray()
        for (index in 0 until locationArray.length()) {
            val row = locationArray.getJSONObject(index)
            places.add(StashPlace(row.getString("id"), row.optString("name", "Place")))
        }
        val itemArray = root.optJSONArray("items") ?: JSONArray()
        for (index in 0 until itemArray.length()) {
            val row = itemArray.getJSONObject(index)
            items.add(
                StashItem(
                    id = row.getString("id"),
                    name = row.optString("name", "Item"),
                    category = row.optString("category", "Other"),
                    quantity = row.optInt("quantity", 1),
                    placeId = row.optString("placeId").ifBlank { null },
                    expiry = row.optString("expiry").ifBlank { null },
                    barcode = row.optString("barcode").ifBlank { null },
                    notes = row.optString("notes").ifBlank { null },
                    photoFile = row.optString("photoFile").ifBlank { null },
                )
            )
        }
    }

    private fun save() {
        val root = JSONObject()
        val locationRows = JSONArray()
        for (place in places) locationRows.put(JSONObject().put("id", place.id).put("name", place.name))
        val itemRows = JSONArray()
        for (item in items) {
            itemRows.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("category", item.category)
                    .put("quantity", item.quantity)
                    .put("placeId", item.placeId ?: "")
                    .put("expiry", item.expiry ?: "")
                    .put("barcode", item.barcode ?: "")
                    .put("notes", item.notes ?: "")
                    .put("photoFile", item.photoFile ?: "")
            )
        }
        root.put("locations", locationRows)
        root.put("items", itemRows)
        file.writeText(root.toString())
    }

    companion object {
        val categories = listOf(
            "Pantry & Food", "Dairy", "Fresh Produce", "Beverages",
            "Medicine & Health", "Kitchenware", "Other",
        )

        fun lookupName(barcode: String): String? {
            val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty(
                "User-Agent",
                "StashKeeper Android - https://github.com/guguluP/StashKeeper"
            )
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                if (json.optInt("status") != 1) return null
                val product = json.optJSONObject("product") ?: return null
                val name = product.optString("product_name").ifBlank {
                    product.optString("product_name_en")
                }
                return name.ifBlank { null }
            }
        }
    }
}
