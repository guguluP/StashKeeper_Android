package com.piyushpatnaik.stashkeeper.data

import com.piyushpatnaik.stashkeeper.logic.NutritionCandidate
import com.piyushpatnaik.stashkeeper.logic.NutritionFacts
import com.piyushpatnaik.stashkeeper.logic.barcodeCandidates
import com.piyushpatnaik.stashkeeper.logic.chooseNutrition
import com.piyushpatnaik.stashkeeper.logic.nutritionQuery
import com.piyushpatnaik.stashkeeper.logic.nutritionScore
import com.piyushpatnaik.stashkeeper.logic.openFactsName
import com.piyushpatnaik.stashkeeper.logic.stashCategory
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LookedUpProduct(
    val name: String,
    val category: String,
    val source: String,
    val barcode: String,
    val nutritionServingSize: String? = null,
    val nutritionCalories: Int? = null,
    val nutritionProteinGrams: Double? = null,
    val nutritionCarbsGrams: Double? = null,
    val nutritionSugarGrams: Double? = null,
    val nutritionFatGrams: Double? = null,
    val nutritionFiberGrams: Double? = null,
    val nutritionSodiumMilligrams: Double? = null,
)

enum class LookupOutcome { Hit, Miss, NetworkFailure }

data class LookupRecord(
    val barcode: String,
    val outcome: LookupOutcome,
    val source: String?,
    val at: Long,
)

object ProductLookup {
    private const val userAgent =
        "StashKeeper/1.0 (https://github.com/guguluP/StashKeeper; inventory-barcode-lookup)"

    private val catalogs = listOf(
        "Open Food Facts" to "world.openfoodfacts.org",
        "Open Beauty Facts" to "world.openbeautyfacts.org",
        "Open Products Facts" to "world.openproductsfacts.org",
        "Open Pet Food Facts" to "world.openpetfoodfacts.org",
    )

    fun nutritionForName(receiptName: String): NutritionFacts? {
        val query = nutritionQuery(receiptName)
        if (query.isBlank()) return null
        val hits = searchFoods(query)
        val ranked = hits.sortedByDescending { nutritionScore(receiptName, it.name, it.brands, it.india) }
        val filled = ranked.take(3).map { hit ->
            val score = nutritionScore(receiptName, hit.name, hit.brands, hit.india)
            if (score <= 0) hit
            else if (hit.nutrition?.published() == true) hit
            else hit.copy(nutrition = nutritionFromCode(hit.code))
        }
        return chooseNutrition(receiptName, filled)
    }

    fun lookup(raw: String, learned: Map<String, LookedUpProduct>): Pair<LookedUpProduct?, LookupRecord> {
        val codes = barcodeCandidates(raw)
        val stamp = System.currentTimeMillis()
        if (codes.isEmpty()) {
            return null to LookupRecord(raw.trim(), LookupOutcome.Miss, null, stamp)
        }
        for (code in codes) {
            learned[code]?.let { known ->
                return known.copy(barcode = code) to LookupRecord(code, LookupOutcome.Hit, known.source, stamp)
            }
        }
        var sawNetworkFailure = false
        for (code in codes) {
            var catalogFailed = false
            for ((source, host) in catalogs) {
                when (val attempt = fetchOpenFacts(code, host, source)) {
                    is Attempt.Found -> return attempt.product to LookupRecord(code, LookupOutcome.Hit, source, stamp)
                    Attempt.Failed -> catalogFailed = true
                    Attempt.Missing -> Unit
                }
            }
            if (catalogFailed) sawNetworkFailure = true
        }
        when (val upc = fetchUpc(codes.first())) {
            is Attempt.Found -> return upc.product to LookupRecord(codes.first(), LookupOutcome.Hit, "UPCitemdb", stamp)
            Attempt.Failed -> sawNetworkFailure = true
            Attempt.Missing -> Unit
        }
        val outcome = if (sawNetworkFailure) LookupOutcome.NetworkFailure else LookupOutcome.Miss
        return null to LookupRecord(codes.first(), outcome, null, stamp)
    }

    private sealed class Attempt {
        data class Found(val product: LookedUpProduct) : Attempt()
        data object Missing : Attempt()
        data object Failed : Attempt()
    }

    private fun fetchOpenFacts(code: String, host: String, source: String): Attempt {
        val connection = open("https://$host/api/v2/product/$code.json") ?: return Attempt.Failed
        return try {
            when (connection.responseCode) {
                404 -> Attempt.Missing
                200 -> decodeOpenFacts(connection.inputStream.bufferedReader().readText(), code, source)
                else -> Attempt.Failed
            }
        } catch (_: Exception) {
            Attempt.Failed
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeOpenFacts(body: String, code: String, source: String): Attempt {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return Attempt.Failed
        if (root.optInt("status") != 1) return Attempt.Missing
        val product = root.optJSONObject("product") ?: return Attempt.Missing
        val name = openFactsName(
            productName = product.optString("product_name").ifBlank { null },
            productNameEn = product.optString("product_name_en").ifBlank { null },
            productNameHi = product.optString("product_name_hi").ifBlank { null },
            genericName = product.optString("generic_name").ifBlank { null },
            abbreviatedName = product.optString("abbreviated_product_name").ifBlank { null },
            brands = product.optString("brands").ifBlank { null },
            quantity = flexible(product, "quantity"),
        ) ?: return Attempt.Missing
        val catalog = product.optString("categories_en").ifBlank { product.optString("categories") }
            .substringBefore(",")
            .trim()
            .ifBlank { null }
        val nutrition = runCatching { nutritionOf(product.optJSONObject("nutriments")) }.getOrNull()
        return Attempt.Found(
            LookedUpProduct(
                name = name,
                category = stashCategory(catalog, name),
                source = source,
                barcode = code,
                nutritionServingSize = nutrition?.serving,
                nutritionCalories = nutrition?.calories,
                nutritionProteinGrams = nutrition?.protein,
                nutritionCarbsGrams = nutrition?.carbs,
                nutritionSugarGrams = nutrition?.sugar,
                nutritionFatGrams = nutrition?.fat,
                nutritionFiberGrams = nutrition?.fiber,
                nutritionSodiumMilligrams = nutrition?.sodium,
            )
        )
    }

    private data class Nutrition(
        val serving: String?,
        val calories: Int?,
        val protein: Double?,
        val carbs: Double?,
        val sugar: Double?,
        val fat: Double?,
        val fiber: Double?,
        val sodium: Double?,
    )

    private fun nutritionOf(nutriments: JSONObject?): Nutrition? {
        if (nutriments == null) return null
        fun number(key: String): Double? = when {
            nutriments.has(key) && !nutriments.isNull(key) -> nutriments.optDouble(key).takeIf { !it.isNaN() }
            else -> null
        }
        val calories = number("energy-kcal_100g")?.toInt()
        val protein = number("proteins_100g")
        val carbs = number("carbohydrates_100g")
        val sugar = number("sugars_100g")
        val fat = number("fat_100g")
        val fiber = number("fiber_100g")
        val sodium = number("sodium_100g")?.times(1000.0)
        if (calories == null && protein == null && carbs == null) return null
        return Nutrition("100 g", calories, protein, carbs, sugar, fat, fiber, sodium)
    }

    private fun searchFoods(query: String): List<NutritionCandidate> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val connection = open("https://search.openfoodfacts.org/search?q=$encoded&page_size=8") ?: return emptyList()
        return try {
            if (connection.responseCode != 200) return emptyList()
            val root = JSONObject(connection.inputStream.bufferedReader().readText())
            val hits = root.optJSONArray("hits") ?: return emptyList()
            val found = mutableListOf<NutritionCandidate>()
            for (index in 0 until hits.length()) {
                val hit = hits.optJSONObject(index) ?: continue
                val name = hit.optString("product_name")
                if (name.isBlank()) continue
                found += NutritionCandidate(
                    name = name,
                    brands = textList(hit.opt("brands")),
                    code = hit.optString("code"),
                    india = textList(hit.opt("countries")).contains("india", ignoreCase = true) ||
                        textList(hit.opt("countries_tags")).contains("india", ignoreCase = true),
                    nutrition = nutritionOf(hit.optJSONObject("nutriments"))?.toFacts(),
                )
            }
            found
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun nutritionFromCode(code: String): NutritionFacts? {
        if (code.isBlank()) return null
        val connection = open("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=nutriments") ?: return null
        return try {
            if (connection.responseCode != 200) return null
            val product = JSONObject(connection.inputStream.bufferedReader().readText()).optJSONObject("product")
            nutritionOf(product?.optJSONObject("nutriments"))?.toFacts()
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun Nutrition.toFacts() = NutritionFacts(
        servingSize = serving ?: "100 g",
        calories = calories,
        proteinGrams = protein,
        carbsGrams = carbs,
        sugarGrams = sugar,
        fatGrams = fat,
        fiberGrams = fiber,
        sodiumMilligrams = sodium,
    )

    private fun textList(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> buildList {
            for (index in 0 until value.length()) add(value.optString(index))
        }.joinToString(" ")
        else -> ""
    }

    private fun fetchUpc(code: String): Attempt {
        val connection = open("https://api.upcitemdb.com/prod/trial/lookup?upc=$code") ?: return Attempt.Failed
        return try {
            when (connection.responseCode) {
                404 -> Attempt.Missing
                200 -> {
                    val root = JSONObject(connection.inputStream.bufferedReader().readText())
                    val item = root.optJSONArray("items")?.optJSONObject(0) ?: return Attempt.Missing
                    val title = item.optString("title").ifBlank { return Attempt.Missing }
                    Attempt.Found(
                        LookedUpProduct(
                            name = title,
                            category = stashCategory(item.optString("category").ifBlank { null }, title),
                            source = "UPCitemdb",
                            barcode = code,
                        )
                    )
                }
                else -> Attempt.Failed
            }
        } catch (_: Exception) {
            Attempt.Failed
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", userAgent)
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
        }
    }.getOrNull()

    private fun flexible(product: JSONObject, key: String): String? {
        if (!product.has(key) || product.isNull(key)) return null
        val value = product.opt(key)
        return when (value) {
            is String -> value.ifBlank { null }
            is Number -> value.toString()
            else -> null
        }
    }
}
