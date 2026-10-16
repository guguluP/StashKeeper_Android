package com.piyushpatnaik.stashkeeper.logic

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

val itemCategoryOptions = listOf(
    "Pantry & Food",
    "Fresh Produce",
    "Dairy",
    "Beverages",
    "Medicine & Health",
    "Cosmetics & Toiletries",
    "Electronics",
    "Watches & Jewelry",
    "Documents",
    "Clothing",
    "Tools & Hardware",
    "Kitchenware",
    "Cleaning Supplies",
    "Stationery",
    "Toys & Games",
    "Sports & Outdoors",
    "Automotive",
    "Other",
)

val warningWindowChoices = listOf(3, 5, 7, 14)
const val defaultWarningWindowDays = 5

data class StashPlace(
    val id: String,
    val name: String,
    val icon: String = "archivebox",
    val parentId: String? = null,
    val sortIndex: Int = 0,
)

data class StashItem(
    val id: String,
    val name: String,
    val category: String,
    val subcategory: String? = null,
    val quantity: Int = 1,
    val unit: String? = null,
    val parQuantity: Int = 0,
    val isPerishable: Boolean = false,
    val expiry: String? = null,
    val expiryUserConfirmed: Boolean = false,
    val expiryConfidence: Double? = null,
    val notes: String? = null,
    val recognizedText: String? = null,
    val tags: List<String> = emptyList(),
    val priceAmount: Double? = null,
    val priceCurrency: String? = null,
    val priceDetectedAutomatically: Boolean = false,
    val barcode: String? = null,
    val barcodeSource: String? = null,
    val locationId: String? = null,
    val photoFiles: List<String> = emptyList(),
    val nutritionServingSize: String? = null,
    val nutritionCalories: Int? = null,
    val nutritionProteinGrams: Double? = null,
    val nutritionCarbsGrams: Double? = null,
    val nutritionSugarGrams: Double? = null,
    val nutritionFatGrams: Double? = null,
    val nutritionFiberGrams: Double? = null,
    val nutritionSodiumMilligrams: Double? = null,
    val ingredients: String? = null,
    val lastChangedBy: String = "",
    val lastChangeSummary: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val hasNutrition: Boolean
        get() = nutritionCalories != null || nutritionProteinGrams != null || nutritionCarbsGrams != null
}

data class PurchaseNeed(
    val id: String,
    val name: String,
    val quantity: Int = 1,
    val createdAt: Long = 0L,
    val isChecked: Boolean = false,
)

data class GroceryLine(
    val id: String,
    val name: String,
    val quantity: Int,
    val detail: String,
)

enum class ExpiryStatus { NotPerishable, Fresh, ExpiringSoon, Expired }

enum class MilestoneTier(val label: String, val threshold: Int) {
    Bronze("Bronze", 5),
    Silver("Silver", 15),
    Gold("Gold", 40),
    Platinum("Platinum", 100),
}

data class CategoryMilestone(
    val category: String,
    val tier: MilestoneTier,
    val currentCount: Int,
    val isUnlocked: Boolean,
    val progress: Double,
) {
    val id: String = "$category-${tier.ordinal + 1}"
}

data class SearchIntent(
    val keywords: List<String> = emptyList(),
    val category: String = "",
    val location: String = "",
    val expiryFocused: Boolean = false,
    val perishabilityFilter: String = "any",
    val priceFocused: Boolean = false,
) {
    val hasFilter: Boolean
        get() = category.isNotEmpty() || location.isNotEmpty() || expiryFocused || keywords.isNotEmpty() || priceFocused
}

data class NutritionFacts(
    val servingSize: String = "100 g",
    val calories: Int? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val sugarGrams: Double? = null,
    val fatGrams: Double? = null,
    val fiberGrams: Double? = null,
    val sodiumMilligrams: Double? = null,
    val source: String? = null,
) {
    fun published(): Boolean =
        (calories ?: 0) > 0 || (proteinGrams ?: 0.0) > 0.0 || (carbsGrams ?: 0.0) > 0.0 || (fatGrams ?: 0.0) > 0.0
}

data class NutritionCandidate(
    val name: String,
    val brands: String = "",
    val code: String = "",
    val india: Boolean = false,
    val nutrition: NutritionFacts? = null,
)

data class ReceiptLine(
    val name: String,
    val price: Double?,
    val quantity: Int = 1,
    val category: String = "Pantry & Food",
    val nutrition: NutritionFacts? = null,
)

data class MergeOutcome(
    val items: List<StashItem>,
    val places: List<StashPlace>,
    val inserted: Int,
    val updated: Int,
)

fun clampWarningDays(value: Int): Int =
    if (value in warningWindowChoices) value else defaultWarningWindowDays

fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

fun parseExpiryDay(raw: String?): LocalDate? {
    val text = raw?.trim().orEmpty()
    if (text.length < 10) return null
    return runCatching { LocalDate.parse(text.take(10)) }.getOrNull()
}

fun daysUntilExpiry(expiry: String?, today: LocalDate): Int? {
    val date = parseExpiryDay(expiry) ?: return null
    return ChronoUnit.DAYS.between(today, date).toInt()
}

fun expiryStatus(item: StashItem, today: LocalDate, warningDays: Int): ExpiryStatus {
    if (!item.isPerishable) return ExpiryStatus.NotPerishable
    val days = daysUntilExpiry(item.expiry, today) ?: return ExpiryStatus.NotPerishable
    return when {
        days < 0 -> ExpiryStatus.Expired
        days <= warningDays -> ExpiryStatus.ExpiringSoon
        else -> ExpiryStatus.Fresh
    }
}

fun expiryCaption(item: StashItem, today: LocalDate): String {
    if (!item.isPerishable) return ""
    val days = daysUntilExpiry(item.expiry, today) ?: return ""
    return when {
        days < 0 -> "Expired ${-days}d ago"
        days == 0 -> "Expires today"
        days == 1 -> "Expires tomorrow"
        else -> "${days}d left"
    }
}

fun attentionItems(items: List<StashItem>, today: LocalDate, warningDays: Int): List<StashItem> =
    items.filter { item ->
        item.quantity > 0 && expiryStatus(item, today, warningDays).let {
            it == ExpiryStatus.ExpiringSoon || it == ExpiryStatus.Expired
        }
    }.sortedBy { it.expiry ?: "9999-99-99" }

fun handledEpoch(expiry: String?): Double {
    val date = parseExpiryDay(expiry) ?: return 0.0
    return date.atStartOfDay(ZoneOffset.UTC).toEpochSecond().toDouble()
}

fun groceryLines(items: List<StashItem>, needs: List<PurchaseNeed>): List<GroceryLine> {
    val lines = mutableListOf<GroceryLine>()
    for (item in items) {
        if (item.parQuantity > item.quantity) {
            val missing = item.parQuantity - item.quantity
            lines += GroceryLine(
                id = "par:${item.id}",
                name = item.name,
                quantity = missing,
                detail = "Have ${item.quantity}, keep ${item.parQuantity}",
            )
        }
    }
    for (need in needs) {
        if (need.isChecked) continue
        val name = need.name.trim()
        if (name.isEmpty()) continue
        lines += GroceryLine(
            id = "need:${need.id}",
            name = name,
            quantity = max(1, need.quantity),
            detail = "Added to the buy list",
        )
    }
    return lines.sortedBy { it.name.lowercase() }
}

fun sameLot(left: String?, right: String?): Boolean = parseExpiryDay(left) == parseExpiryDay(right)

fun findSameLot(items: List<StashItem>, barcode: String, expiry: String?): StashItem? {
    val code = barcode.trim()
    if (code.isEmpty()) return null
    return items.firstOrNull { it.barcode?.trim() == code && sameLot(it.expiry, expiry) }
}

fun noteChange(item: StashItem, summary: String, who: String, now: Long): StashItem =
    item.copy(lastChangedBy = who.ifBlank { "Me" }, lastChangeSummary = summary, updatedAt = now)

fun adjustQuantity(item: StashItem, delta: Int, who: String, now: Long): StashItem {
    val quantity = (item.quantity + delta).coerceIn(0, 999)
    val applied = quantity - item.quantity
    if (applied == 0) return item
    val summary = when {
        applied < 0 && quantity == 0 -> "used the last one"
        applied < 0 -> "used ${-applied}, $quantity left"
        else -> "added $applied"
    }
    return noteChange(item.copy(quantity = quantity), summary, who, now)
}

fun snoozeExpiry(item: StashItem, days: Int, today: LocalDate, now: Long): StashItem {
    val base = parseExpiryDay(item.expiry) ?: today
    return item.copy(
        expiry = base.plusDays(days.toLong()).toString(),
        expiryUserConfirmed = true,
        isPerishable = true,
        updatedAt = now,
    )
}

fun searchableText(item: StashItem, placeName: String?): String =
    listOfNotNull(item.name, item.category, item.subcategory, item.notes, item.recognizedText, placeName)
        .plus(item.tags)
        .joinToString(" ")

fun keywordFallback(query: String, knownCategories: List<String>, knownLocations: List<String>): SearchIntent {
    val lower = query.lowercase()
    var category = knownCategories.firstOrNull { lower.contains(it.lowercase()) }.orEmpty()
    if (category.isEmpty()) {
        category = itemCategoryOptions.firstOrNull { option ->
            val token = option.substringBefore(" ").lowercase()
            token.isNotEmpty() && lower.contains(token)
        }.orEmpty()
    }
    val location = knownLocations.firstOrNull { lower.contains(it.lowercase()) }.orEmpty()
    var expiryFocused = false
    var perishability = "any"
    when {
        lower.contains("expired") -> {
            expiryFocused = true
            perishability = "expired"
        }
        lower.contains("expir") || lower.contains("soon") || lower.contains("spoil") -> {
            expiryFocused = true
            perishability = "expiring_soon"
        }
        lower.contains("fresh") -> perishability = "fresh"
    }
    val priceFocused = listOf("price", "cost", "cheap", "expensive", "₹", "rs").any { lower.contains(it) }
    val stop = setOf(
        "the", "a", "an", "my", "in", "on", "at", "of", "for", "to", "and", "or",
        "where", "what", "which", "is", "are", "me", "show", "find", "items", "item",
        "stash", "expired", "expiring", "soon", "fresh",
    )
    var keywords = query.split(Regex("[^\\p{L}\\p{N}]+"))
        .map { it.lowercase() }
        .filter { it.length > 1 && it !in stop }
    if (category.isNotEmpty()) keywords = keywords.filterNot { category.lowercase().contains(it) }
    if (location.isNotEmpty()) keywords = keywords.filterNot { location.lowercase().contains(it) }
    return SearchIntent(keywords, category, location, expiryFocused, perishability, priceFocused)
}

fun applySearch(
    items: List<StashItem>,
    places: List<StashPlace>,
    query: String,
    intent: SearchIntent?,
    today: LocalDate,
    warningDays: Int,
): List<StashItem> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return items
    fun text(item: StashItem) = searchableText(item, places.firstOrNull { it.id == item.locationId }?.name)
    val fallback = items.filter { text(it).contains(trimmed, ignoreCase = true) }
    val parsed = intent ?: return fallback
    var filtered = items
    if (parsed.category.isNotEmpty()) {
        filtered = filtered.filter { it.category.equals(parsed.category, ignoreCase = true) }
    }
    if (parsed.location.isNotEmpty()) {
        val placeIds = places.filter { it.name.equals(parsed.location, ignoreCase = true) }.map { it.id }.toSet()
        filtered = filtered.filter { it.locationId in placeIds }
    }
    if (parsed.expiryFocused) {
        filtered = filtered.filter {
            val status = expiryStatus(it, today, warningDays)
            status == ExpiryStatus.ExpiringSoon || status == ExpiryStatus.Expired
        }
    }
    if (parsed.keywords.isNotEmpty()) {
        filtered = filtered.filter { item ->
            val haystack = text(item)
            parsed.keywords.any { haystack.contains(it, ignoreCase = true) }
        }
    }
    if (parsed.priceFocused) filtered = filtered.filter { it.priceAmount != null }
    return filtered.ifEmpty { fallback }
}

fun allMilestones(items: List<StashItem>): List<CategoryMilestone> {
    val counts = items.groupingBy { it.category }.eachCount()
    return counts.flatMap { (category, count) ->
        MilestoneTier.entries.map { tier ->
            CategoryMilestone(
                category = category,
                tier = tier,
                currentCount = count,
                isUnlocked = count >= tier.threshold,
                progress = min(1.0, count.toDouble() / tier.threshold),
            )
        }
    }.sortedWith(compareBy({ it.category }, { it.tier.ordinal }))
}

fun nextMilestones(items: List<StashItem>): List<CategoryMilestone> =
    allMilestones(items)
        .groupBy { it.category }
        .map { (_, milestones) -> milestones.firstOrNull { !it.isUnlocked } ?: milestones.last() }
        .sortedBy { it.category }

fun placePath(id: String?, places: List<StashPlace>, hops: Int = 16): String {
    if (id == null || hops <= 0) return ""
    val place = places.firstOrNull { it.id == id } ?: return ""
    val parent = place.parentId?.let { placePath(it, places, hops - 1) }.orEmpty()
    return if (parent.isEmpty()) place.name else "$parent > ${place.name}"
}

fun isUnder(placeId: String, ancestorId: String, places: List<StashPlace>): Boolean {
    var current = places.firstOrNull { it.id == placeId }?.parentId
    var hops = 0
    while (current != null && hops < 32) {
        if (current == ancestorId) return true
        current = places.firstOrNull { it.id == current }?.parentId
        hops++
    }
    return false
}

fun parentChoices(placeId: String, places: List<StashPlace>): List<StashPlace> =
    places.filter { it.id != placeId && !isUnder(it.id, placeId, places) }

fun barcodeCandidates(raw: String): List<String> {
    val codes = mutableListOf<String>()
    fun add(value: String) {
        val digits = value.filter { it.isDigit() }
        if (digits.length in 8..14 && digits !in codes) codes += digits
    }
    Regex("/01/(\\d{8,14})").find(raw)?.groupValues?.getOrNull(1)?.let(::add)
    Regex("\\(01\\)(\\d{8,14})").find(raw)?.groupValues?.getOrNull(1)?.let(::add)
    add(raw)
    for (code in codes.toList()) {
        if (code.length == 14 && code.startsWith("0")) add(code.drop(1))
        if (code.length == 12) add("0$code")
    }
    return codes
}

fun openFactsName(
    productName: String?,
    productNameEn: String?,
    productNameHi: String?,
    genericName: String?,
    abbreviatedName: String?,
    brands: String?,
    quantity: String?,
): String? {
    fun cleaned(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }
    val primary = listOf(productNameEn, productName, productNameHi, abbreviatedName).mapNotNull(::cleaned)
    var name = primary.maxByOrNull { it.length } ?: cleaned(genericName) ?: return null
    if (name == name.lowercase()) {
        name = name.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { char -> char.titlecase() }
        }
    }
    val brand = cleaned(brands?.substringBefore(","))
    if (!brand.isNullOrEmpty() && !name.contains(brand, ignoreCase = true)) name = "$brand $name"
    val amount = cleaned(quantity)
    if (!amount.isNullOrEmpty() && !name.contains(amount, ignoreCase = true)) name = "$name $amount"
    return name
}

fun stashCategory(catalogCategory: String?, name: String): String {
    val lowered = name.lowercase()
    if (lowered.contains("dairy milk") || lowered.contains("bournville")) return "Pantry & Food"
    val catalog = catalogCategory?.trim().orEmpty()
    itemCategoryOptions.firstOrNull { it.equals(catalog, ignoreCase = true) }?.let { return it }
    val text = catalog.lowercase()
    return when {
        text.contains("dairy") || text.contains("cheese") || text.contains("yogurt") || text.contains("milk") -> "Dairy"
        text.contains("beverage") || text.contains("drink") || text.contains("juice") -> "Beverages"
        text.contains("produce") || text.contains("fruit") || text.contains("vegetable") -> "Fresh Produce"
        text.contains("beauty") || text.contains("cosmetic") -> "Cosmetics & Toiletries"
        text.contains("medicine") || text.contains("pharma") || text.contains("drug") -> "Medicine & Health"
        text.contains("pet") -> "Other"
        text.isNotEmpty() -> "Pantry & Food"
        else -> "Other"
    }
}

private val receiptFooter = Regex(
    "(?i)^(total|sub\\s*total|grand\\s*total|discount|net\\s*amount|round\\s*off|amou?nt\\s*paid|items?\\s*:|total\\s*saving|payment|thank|gst|cgst|sgst|vat)\\b|free delivery|discount on every"
)
private val receiptNoise = Regex(
    "(?i)^(estimate|tax invoice|cash memo)\\b|\\b(mobile|serial|payment mode|customer|counter|road|date\\s*:|time\\s*:)\\b"
)
private val receiptQtyUnit = "(?:btl|bottles?|pcs|pc|pk|pkt|pack|nos|no|box|tin|jar|bag)"
private val receiptColumnRow = Regex(
    "(?i)^(.*?)\\s*(\\d+(?:\\.\\d+)?)\\s+($receiptQtyUnit)\\s+((?:\\d+(?:\\.\\d+)?\\s*)+)\$"
)
private val receiptSimpleRow = Regex("(?i)^(.+?)\\s+(?:₹|rs\\.?)?\\s*(\\d+\\.\\d{2}|\\d+)\\s*$")
private val receiptQtyOnly = Regex("(?i)^(\\d+(?:\\.\\d+)?)\\s+($receiptQtyUnit)\$")
private val receiptNumberOnly = Regex("^\\d+(?:\\.\\d+)?$")

fun parseReceipt(text: String): List<ReceiptLine> = parseReceiptLines(text.lines())

fun parseReceiptLines(rawLines: List<String>): List<ReceiptLine> {
    val lines = joinSplitReceiptRows(
        rawLines.map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }
    )
    val items = mutableListOf<ReceiptLine>()
    val buffer = mutableListOf<String>()
    var started = false
    for (line in lines) {
        if (isReceiptHeader(line)) {
            started = true
            buffer.clear()
            continue
        }
        if (isReceiptFooter(line)) {
            buffer.clear()
            if (started) break
            continue
        }
        val column = receiptColumn(line)
        if (column != null) {
            started = true
            addReceiptItem(items, buffer, column.prefix, column.quantity, column.price)
            buffer.clear()
            continue
        }
        val simple = receiptSimple(line)
        if (simple != null && !isReceiptNoise(line)) {
            started = true
            addReceiptItem(items, buffer, simple.first, 1, simple.second)
            buffer.clear()
            continue
        }
        if (isReceiptNoise(line)) {
            buffer.clear()
            continue
        }
        if (started && receiptNameFragment(line)) buffer += line
        if (items.size == 40) break
    }
    return items
}

private data class ReceiptColumn(val prefix: String, val quantity: Int, val price: Double)

private fun joinSplitReceiptRows(lines: List<String>): List<String> {
    val joined = mutableListOf<String>()
    var index = 0
    while (index < lines.size) {
        if (receiptQtyOnly.matches(lines[index])) {
            val parts = mutableListOf(lines[index])
            var next = index + 1
            while (next < lines.size && parts.size < 5 && receiptNumberOnly.matches(lines[next])) {
                parts += lines[next]
                next++
            }
            if (parts.size > 1) {
                joined += parts.joinToString(" ")
                index = next
                continue
            }
        }
        joined += lines[index]
        index++
    }
    return joined
}

private fun receiptColumn(line: String): ReceiptColumn? {
    val match = receiptColumnRow.find(line) ?: return null
    val prices = match.groupValues[4].trim().split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
    val amount = prices.lastOrNull { it > 0 } ?: prices.lastOrNull() ?: return null
    val quantity = match.groupValues[2].toDoubleOrNull()?.toInt()?.coerceIn(1, 999) ?: 1
    return ReceiptColumn(match.groupValues[1].trim(), quantity, amount)
}

private fun receiptSimple(line: String): Pair<String, Double>? {
    if (line.count { it.isLetter() } < 2) return null
    val match = receiptSimpleRow.find(line) ?: return null
    val name = match.groupValues[1].trim()
    val price = match.groupValues[2].toDoubleOrNull() ?: return null
    if (name.length < 2 || name.none { it.isLetter() }) return null
    if (name.count { it.isDigit() } > name.count { it.isLetter() }) return null
    return name to price
}

private fun addReceiptItem(
    items: MutableList<ReceiptLine>,
    buffer: List<String>,
    prefix: String,
    quantity: Int,
    price: Double,
) {
    val name = prettyReceiptName((buffer + prefix).filter { it.isNotBlank() }.joinToString(" "))
    if (name.length < 2 || name.none { it.isLetter() }) return
    if (items.any { it.name.equals(name, ignoreCase = true) }) return
    items += ReceiptLine(name.take(80), price, quantity, receiptCategory(name))
}

private fun isReceiptHeader(line: String): Boolean {
    val lower = line.lowercase()
    return lower.contains("item") && (lower.contains("qty") || lower.contains("mrp") || lower.contains("rate"))
}

private fun isReceiptFooter(line: String): Boolean = receiptFooter.containsMatchIn(line)

private fun isReceiptNoise(line: String): Boolean {
    if (receiptNoise.containsMatchIn(line)) return true
    val digits = line.count { it.isDigit() }
    return digits >= 8 && digits > line.count { it.isLetter() }
}

private fun receiptNameFragment(line: String): Boolean {
    if (line.length !in 2..40) return false
    if (line.none { it.isLetter() }) return false
    return line.count { it.isDigit() } <= line.count { it.isLetter() } + 4
}

private fun prettyReceiptName(raw: String): String {
    val stripped = raw
        .replace(Regex("(?i)(?:\\s*\\d+(?:\\.\\d+)?\\s*/-?)+\\s*$"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', ':', '/', '.')
    if (stripped.length < 2 || stripped != stripped.uppercase()) return stripped
    return stripped.split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { char -> if (char.isLetter()) char.titlecase() else char.toString() }
    }
}

private val nutritionCategories = setOf("Pantry & Food", "Dairy", "Beverages", "Fresh Produce")
private val notForNutrition = listOf(
    "dettol", "camphor", "campore", "agarbatti", "incense", "detergent", "phenyl", "soap", "shampoo",
)
private val genericFoodWords = setOf(
    "oil", "dal", "rice", "salt", "atta", "milk", "tea", "ghee", "sugar", "flour", "poha",
    "water", "food", "sun", "flower", "sunflower", "refined", "extra", "pure", "gold",
)

fun nutritionEligible(name: String, category: String): Boolean {
    if (category !in nutritionCategories) return false
    val lower = name.lowercase()
    return notForNutrition.none { lower.contains(it) }
}

fun nutritionQuery(name: String): String = nutritionTokens(name).joinToString(" ")

fun nutritionScore(receiptName: String, candidateName: String, brands: String, india: Boolean): Int {
    val want = nutritionTokens(receiptName).toSet()
    if (want.isEmpty()) return 0
    val have = (nutritionTokens(candidateName) + nutritionTokens(brands)).toSet()
    val shared = want.intersect(have)
    val distinctive = shared.filter { it !in genericFoodWords }
    val wantDistinctive = want.filter { it !in genericFoodWords }
    if (wantDistinctive.isNotEmpty() && distinctive.isEmpty()) return 0
    if (distinctive.isEmpty() && shared.size < 2) return 0
    return distinctive.size * 5 + shared.size + if (india) 2 else 0
}

fun chooseNutrition(receiptName: String, candidates: List<NutritionCandidate>): NutritionFacts? {
    val ranked = candidates
        .map { it to nutritionScore(receiptName, it.name, it.brands, it.india) }
        .filter { (candidate, score) -> score > 0 && candidate.nutrition?.published() == true }
        .sortedWith(compareByDescending<Pair<NutritionCandidate, Int>> { it.second }.thenByDescending { it.first.india })
    return ranked.firstOrNull()?.first?.nutrition?.copy(source = "Open Food Facts")
}

private fun nutritionTokens(name: String): List<String> =
    name.lowercase()
        .replace(Regex("\\d+(?:\\.\\d+)?\\s*(?:kg|gm|g|ml|ltr|l|mg)\\b"), " ")
        .split(Regex("[^a-z]+"))
        .filter { it.length >= 2 && it !in setOf("of", "and", "the", "with", "for", "per") }

fun receiptCategory(name: String): String {
    val lower = name.lowercase()
    val cosmetic = listOf("dettol", "soap", "shampoo", "cream", "gel", "lotion", "skincare", "toothpaste", "face wash")
    val food = listOf(
        "oil", "dal", "poha", "rice", "atta", "sugar", "salt", "spice", "masala",
        "lanka", "chilli", "chili", "everest", "flour", "wheat", "tea", "coffee", "milk", "ghee",
    )
    return when {
        cosmetic.any { lower.contains(it) } -> "Cosmetics & Toiletries"
        food.any { lower.contains(it) } -> "Pantry & Food"
        else -> "Pantry & Food"
    }
}

data class RecipeCard(
    val title: String,
    val detail: String,
    val steps: List<String>,
    val minutes: String,
)

data class PackRead(
    val nutrition: NutritionFacts? = null,
    val ingredients: String? = null,
    val expiry: String? = null,
)

private data class RecipeBook(val title: String, val needs: List<String>, val steps: List<String>, val minutes: String)

fun pantryRecipes(names: List<String>, expiring: List<String> = emptyList()): List<RecipeCard> {
    val have = names.map { it.lowercase() }
    val urgent = expiring.map { it.lowercase() }
    val book = listOf(
        RecipeBook("Tomato onion fry", listOf("tomato", "onion"), listOf("Slice the tomato and onion.", "Warm oil, soften the onion, then add the tomato.", "Salt and cook until the tomato breaks down."), "15 minutes"),
        RecipeBook("Egg scramble", listOf("egg"), listOf("Beat the eggs with a pinch of salt.", "Cook in a little oil, stirring, until just set.", "Onion or tomato can go in if you have them."), "10 minutes"),
        RecipeBook("Jeera rice", listOf("rice"), listOf("Rinse the rice.", "Warm oil, add cumin if you have it, then the rice and water.", "Cover and cook until the water is gone."), "25 minutes"),
        RecipeBook("Potato fry", listOf("potato"), listOf("Cube the potato.", "Fry in oil with salt and turmeric or chili if you have it.", "Cook until the edges brown."), "20 minutes"),
        RecipeBook("Milk chai", listOf("milk"), listOf("Boil water with tea if you have it.", "Add the milk and a little sugar.", "Simmer and strain."), "10 minutes"),
        RecipeBook("Dal", listOf("dal", "lentil", "toor", "moong"), listOf("Rinse the lentils.", "Boil them with salt until soft.", "Serve with rice if you have it."), "30 minutes"),
    )
    return book.mapNotNull { recipe ->
        val hits = recipe.needs.filter { need -> have.any { it.contains(need) } }
        if (hits.isEmpty()) return@mapNotNull null
        val urgentHits = hits.count { need -> urgent.any { it.contains(need) } }
        (urgentHits * 10 + hits.size) to RecipeCard(
            title = recipe.title,
            detail = "Uses what you have: ${hits.joinToString(", ")}. Oil, salt, and a basic spice are assumed.",
            steps = recipe.steps,
            minutes = recipe.minutes,
        )
    }.sortedByDescending { it.first }.take(3).map { it.second }
}

fun readPack(lines: List<String>, today: LocalDate = LocalDate.now()): PackRead {
    val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
    return PackRead(
        nutrition = readNutritionPanel(cleaned),
        ingredients = ingredientText(cleaned),
        expiry = printedExpiry(cleaned, today),
    )
}

fun printedExpiry(lines: List<String>, today: LocalDate = LocalDate.now()): String? {
    data class Dated(val date: LocalDate, val score: Int)
    val found = mutableListOf<Dated>()
    var monthsAhead: Int? = null
    var manufactured: LocalDate? = null
    for (text in lines) {
        val lower = text.lowercase()
        monthsIn(lower)?.let { monthsAhead = it }
        val date = firstPrintedDate(text) ?: monthEndDate(text, lower) ?: continue
        if (listOf("mfg", "pkd", "packed", "manufactur").any { lower.contains(it) }) {
            if (manufactured == null) manufactured = date
            continue
        }
        val score = if (listOf("best", "use by", "exp").any { lower.contains(it) }) 2 else 1
        found += Dated(date, score)
    }
    if (monthsAhead != null) {
        val start = manufactured ?: today
        found += Dated(start.plusMonths(monthsAhead!!.toLong()), if (manufactured == null) 1 else 3)
    }
    val start = today.minusYears(2)
    val end = today.plusYears(8)
    return found.filter { it.date >= start && it.date <= end }
        .maxWithOrNull(compareBy<Dated> { it.score }.thenByDescending { it.date })
        ?.date
        ?.toString()
}

fun readNutritionPanel(lines: List<String>): NutritionFacts? {
    val useful = lines.map { it.trim() }.filter { it.isNotEmpty() && !isPanelNoise(it) }
    if (useful.isEmpty()) return null
    var calories: Int? = null
    var protein: Double? = null
    var carbs: Double? = null
    var sugar: Double? = null
    var fat: Double? = null
    var fiber: Double? = null
    var sodium: Double? = null
    val per100First = columnIsPer100First(useful)
    for (line in useful) {
        val kind = nutrientKind(line) ?: continue
        val values = nutrientValues(line)
        if (values.isEmpty()) continue
        val chosen = if (values.size == 1 || per100First) values.first() else values.getOrNull(1) ?: values.first()
        when (kind) {
            "energy" -> if (calories == null) calories = chosen.kcal
            "protein" -> if (protein == null && chosen.grams != null) protein = chosen.grams
            "carb" -> if (carbs == null && chosen.grams != null) carbs = chosen.grams
            "sugar" -> if (sugar == null && chosen.grams != null) sugar = chosen.grams
            "fat" -> if (fat == null && chosen.grams != null) fat = chosen.grams
            "fiber" -> if (fiber == null && chosen.grams != null) fiber = chosen.grams
            "sodium" -> if (sodium == null) sodium = chosen.sodiumMg
        }
    }
    val count = listOf(calories, protein, carbs, sugar, fat, fiber, sodium).count { it != null }
    if (count < 2) return null
    return NutritionFacts(
        servingSize = "100 g",
        calories = calories,
        proteinGrams = protein,
        carbsGrams = carbs,
        sugarGrams = sugar,
        fatGrams = fat,
        fiberGrams = fiber,
        sodiumMilligrams = sodium,
        source = "Printed on the pack",
    )
}

private data class NutrientValue(val kcal: Int?, val grams: Double?, val sodiumMg: Double?)

private fun nutrientValues(line: String): List<NutrientValue> {
    val matches = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(kcal|kj|cals?|mg|g|%)?").findAll(line).toList()
    val values = mutableListOf<NutrientValue>()
    var pendingKj: Double? = null
    for (match in matches) {
        val number = match.groupValues[1].let { raw ->
            if (raw.contains(",") && raw.substringAfter(",").length <= 2) raw.replace(",", ".") else raw.replace(",", "")
        }.toDoubleOrNull() ?: continue
        when (match.groupValues[2].lowercase()) {
            "%" -> Unit
            "kj" -> pendingKj = number
            "kcal", "cal", "cals" -> {
                values += NutrientValue(number.toInt(), null, null)
                pendingKj = null
            }
            "mg" -> values += NutrientValue(null, null, number)
            else -> {
                pendingKj?.let { values += NutrientValue((it / 4.184).toInt(), null, null) }
                pendingKj = null
                values += NutrientValue(null, number, if (number >= 20 && match.groupValues[2].isEmpty()) number else null)
            }
        }
    }
    return values
}

private fun nutrientKind(line: String): String? {
    val lower = line.lowercase()
    if (listOf("saturat", "trans", "salt", "cholesterol").any { lower.contains(it) }) return null
    return when {
        listOf("sugar", "शर्करा").any { lower.contains(it) } -> "sugar"
        listOf("fibre", "fiber", "फाइबर").any { lower.contains(it) } -> "fiber"
        lower.contains("protein") || lower.contains("प्रोटीन") -> "protein"
        lower.contains("fat") || lower.contains("वसा") -> "fat"
        lower.contains("carb") || lower.contains("कार्बो") -> "carb"
        lower.contains("sodium") || lower.contains("सोडियम") -> "sodium"
        listOf("energy", "calor", "ऊर्जा", "कैलोरी").any { lower.contains(it) } -> "energy"
        else -> null
    }
}

private fun columnIsPer100First(lines: List<String>): Boolean {
    for (line in lines) {
        if (nutrientKind(line) != null) continue
        val lower = line.lowercase()
        val hundred = lower.indexOf("100")
        val serving = lower.indexOf("serv")
        if (hundred >= 0 && serving >= 0) return hundred < serving
    }
    return true
}

private fun isPanelNoise(line: String): Boolean {
    val lower = line.lowercase()
    return listOf("mrp", "fssai", "best before", "use by", "mfg", "pkd", "batch", "customer care", "manufactured", "net wt", "net weight", "net qty").any { lower.contains(it) }
}

private fun ingredientText(lines: List<String>): String? {
    val parts = lines.mapNotNull { line ->
        val lower = line.lowercase()
        if (!lower.startsWith("ingredient") && !lower.startsWith("contains ")) return@mapNotNull null
        line.substringAfter(":", line)
            .replace(Regex("(?i)^(ingredients|contains)\\b"), "")
            .split(",")
            .map { it.trim() }
            .filter { it.length >= 2 && !it.equals("ingredients", true) && !it.equals("contains", true) }
    }.flatten()
    return parts.takeIf { it.size >= 2 }?.joinToString(", ")
}

private fun monthsIn(lower: String): Int? {
    val match = Regex("(?:best before|use by|expiry|exp\\.?)\\s*(\\d{1,2})\\s*months?").find(lower) ?: return null
    return match.groupValues[1].toIntOrNull()?.takeIf { it in 1..60 }
}

private fun firstPrintedDate(text: String): LocalDate? {
    val match = Regex("(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})").find(text) ?: return null
    val first = match.groupValues[1].toInt()
    val second = match.groupValues[2].toInt()
    var year = match.groupValues[3].toInt()
    if (year < 100) year += if (year >= 70) 1900 else 2000
    val (day, month) = when {
        first > 12 -> first to second
        second > 12 -> second to first
        else -> first to second
    }
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

private fun monthEndDate(text: String, lower: String): LocalDate? {
    val expiry = listOf("best", "use by", "exp", "mfg", "pkd", "packed").any { lower.contains(it) }
    if (!expiry) return null
    val named = Regex("(?i)\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\b\\s+(\\d{4})").find(text)
    val month = named?.let { monthNumber(it.groupValues[1]) }
        ?: Regex("\\b(0?[1-9]|1[0-2])[/\\-.](\\d{4})\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()
    val year = named?.groupValues?.get(2)?.toIntOrNull()
        ?: Regex("\\b(0?[1-9]|1[0-2])[/\\-.](\\d{4})\\b").find(text)?.groupValues?.get(2)?.toIntOrNull()
    if (month == null || year == null) return null
    return runCatching { LocalDate.of(year, month, 1).plusMonths(1).minusDays(1) }.getOrNull()
}

private fun monthNumber(name: String): Int? = when (name.lowercase().take(3)) {
    "jan" -> 1
    "feb" -> 2
    "mar" -> 3
    "apr" -> 4
    "may" -> 5
    "jun" -> 6
    "jul" -> 7
    "aug" -> 8
    "sep" -> 9
    "oct" -> 10
    "nov" -> 11
    "dec" -> 12
    else -> null
}

fun suggestNameFromOcr(lines: List<String>): String? {
    val skip = Regex("(?i)(mrp|rs\\b|₹|best before|use by|exp\\b|net\\b|qty|batch|mfg|ingredients|nutrition|energy|kcal)")
    return lines.map { it.trim() }
        .filter { it.length in 3..60 && it.any { char -> char.isLetter() } }
        .filterNot { skip.containsMatchIn(it) }
        .filterNot { it.count { char -> char.isDigit() } > it.length / 2 }
        .maxByOrNull { it.length }
}

fun assistantReply(
    message: String,
    items: List<StashItem>,
    places: List<StashPlace>,
    today: LocalDate,
    warningDays: Int,
): String {
    val lowered = message.lowercase()
    if (listOf("expir", "going bad", "spoil", "use soon", "use up").any { lowered.contains(it) }) {
        val expiring = items.filter { it.isPerishable && (daysUntilExpiry(it.expiry, today) ?: Int.MAX_VALUE) <= warningDays }
            .sortedBy { daysUntilExpiry(it.expiry, today) ?: Int.MAX_VALUE }
        if (expiring.isEmpty()) return "Nothing in your stash is expiring in the next $warningDays days, based on what's recorded."
        val lines = expiring.take(8).joinToString("\n") { item ->
            val days = daysUntilExpiry(item.expiry, today) ?: 0
            val whenText = if (days <= 0) "today or already past" else "in $days day${if (days == 1) "" else "s"}"
            "• ${item.name} — $whenText"
        }
        val more = if (expiring.size > 8) "\n…and ${expiring.size - 8} more." else ""
        return "Here's what's expiring soon:\n$lines$more"
    }
    if (listOf("steps", "workout", "how active").any { lowered.contains(it) }) {
        return "Health data isn't connected on this Android device."
    }
    if (listOf("cook", "recipe", "make with", "eat", "meal", "snack").any { lowered.contains(it) }) {
        return recipeAnswer(items, today)
    }
    if (lowered.contains("in the") || lowered.contains("in my") || lowered.contains("what's in") || lowered.contains("whats in")) {
        val location = places.firstOrNull { lowered.contains(it.name.lowercase()) }
        if (location != null) {
            return summarize(items.filter { it.locationId == location.id }, "In ${location.name}:")
        }
        val category = items.map { it.category }.distinct().firstOrNull { lowered.contains(it.lowercase()) }
        if (category != null) return summarize(items.filter { it.category == category }, "In $category:")
    }
    if (lowered.contains("how many") || lowered.contains("do i have")) {
        val stop = setOf("how", "many", "do", "i", "have", "of", "a", "an", "the", "in", "my", "stash", "left", "any")
        val words = lowered.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it !in stop }
        if (words.isEmpty()) return "I couldn't find anything matching that in your stash."
        val matches = items.filter { item -> words.any { item.name.lowercase().contains(it) } }
        if (matches.isEmpty()) return "I couldn't find anything matching that in your stash."
        return summarize(matches, "Found:")
    }
    return "I can answer a few things directly on this device. Try \"what's expiring soon\", \"what's in the pantry\", \"how many eggs do I have\", or \"what can I cook\"."
}

fun recipeAnswer(items: List<StashItem>, today: LocalDate): String {
    val food = setOf("Fresh Produce", "Dairy", "Pantry & Food")
    val candidates = items.filter { it.category in food && it.quantity > 0 }
        .sortedBy { daysUntilExpiry(it.expiry, today) ?: Int.MAX_VALUE }
    if (candidates.isEmpty()) return "I don't see any food items in your stash to suggest something with right now."
    val names = candidates.take(6).joinToString(", ") { it.name }
    return "Here's what food you have on hand to work with: $names. Anything expiring soonest is listed first, so that's a good place to start."
}

private fun summarize(items: List<StashItem>, headline: String): String {
    if (items.isEmpty()) return "Nothing matches that right now."
    val lines = items.take(10).joinToString("\n") { "• ${it.name} — qty ${it.quantity}" }
    val more = if (items.size > 10) "\n…and ${items.size - 10} more." else ""
    return "$headline\n$lines$more"
}

fun importStatus(inserted: Int, updated: Int): String =
    "Added $inserted new item${if (inserted == 1) "" else "s"}, updated $updated."

fun mergeHousehold(
    existingItems: List<StashItem>,
    existingPlaces: List<StashPlace>,
    incomingItems: List<StashItem>,
    incomingPlaces: List<StashPlace>,
): MergeOutcome {
    val placeById = existingPlaces.associateBy { it.id }.toMutableMap()
    for (incoming in incomingPlaces) {
        val current = placeById[incoming.id]
        placeById[incoming.id] = if (current == null) {
            incoming
        } else {
            current.copy(name = incoming.name, icon = incoming.icon.ifBlank { current.icon }, parentId = incoming.parentId)
        }
    }
    val itemById = existingItems.associateBy { it.id }.toMutableMap()
    var inserted = 0
    var updated = 0
    for (incoming in incomingItems) {
        val current = itemById[incoming.id]
        if (current == null) {
            itemById[incoming.id] = incoming
            inserted++
        } else {
            itemById[incoming.id] = current.copy(
                name = incoming.name,
                category = incoming.category,
                subcategory = incoming.subcategory,
                quantity = incoming.quantity,
                unit = incoming.unit,
                isPerishable = incoming.isPerishable,
                expiry = incoming.expiry,
                expiryUserConfirmed = incoming.expiryUserConfirmed,
                notes = incoming.notes,
                recognizedText = incoming.recognizedText,
                tags = incoming.tags,
                priceAmount = incoming.priceAmount,
                priceCurrency = incoming.priceCurrency,
                priceDetectedAutomatically = incoming.priceDetectedAutomatically,
                barcode = incoming.barcode,
                barcodeSource = incoming.barcodeSource,
                locationId = incoming.locationId,
                photoFiles = if (current.photoFiles.isNotEmpty()) current.photoFiles else incoming.photoFiles,
                nutritionServingSize = incoming.nutritionServingSize,
                nutritionCalories = incoming.nutritionCalories,
                nutritionProteinGrams = incoming.nutritionProteinGrams,
                nutritionCarbsGrams = incoming.nutritionCarbsGrams,
                nutritionSugarGrams = incoming.nutritionSugarGrams,
                nutritionFatGrams = incoming.nutritionFatGrams,
                nutritionFiberGrams = incoming.nutritionFiberGrams,
                nutritionSodiumMilligrams = incoming.nutritionSodiumMilligrams,
                ingredients = incoming.ingredients,
                updatedAt = incoming.updatedAt.takeIf { it > 0 } ?: current.updatedAt,
            )
            updated++
        }
    }
    val itemOrder = existingItems.map { it.id } + incomingItems.map { it.id }.filter { id -> existingItems.none { it.id == id } }
    val placeOrder = existingPlaces.map { it.id } + incomingPlaces.map { it.id }.filter { id -> existingPlaces.none { it.id == id } }
    return MergeOutcome(
        items = itemOrder.map { itemById.getValue(it) },
        places = placeOrder.map { placeById.getValue(it) },
        inserted = inserted,
        updated = updated,
    )
}

fun columnCount(width: Float): Int = when {
    width >= 1100f -> 3
    width >= 700f -> 2
    else -> 1
}

fun horizontalPadding(width: Float): Float = when {
    width >= 1100f -> 36f
    width >= 700f -> 28f
    else -> 16f
}

fun readableWidth(width: Float): Float = min(width, if (width >= 900f) 920f else width)

fun sectionSpacing(width: Float): Float = if (width >= 700f) 32f else 22f

fun attentionCardWidth(width: Float): Float {
    val visible = when {
        width >= 1100f -> 5.2f
        width >= 700f -> 3.6f
        else -> 2.35f
    }
    val available = max(width - horizontalPadding(width) * 2f, 240f)
    return min(168f, max(112f, available / visible))
}

fun locationChipWidth(width: Float): Float = attentionCardWidth(width) * 0.68f

fun statColumns(width: Float): Int = min(3, max(columnCount(width), if (width >= 520f) 3 else 1))

fun springResponseSeconds(refreshRate: Int, reduceMotion: Boolean): Double = when {
    reduceMotion -> 0.01
    refreshRate >= 120 -> 0.36
    else -> 0.42
}

fun frameIntervalSeconds(refreshRate: Int): Double = 1.0 / max(refreshRate, 30).toDouble()
