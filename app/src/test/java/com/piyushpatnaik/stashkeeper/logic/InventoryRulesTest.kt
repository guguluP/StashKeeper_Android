package com.piyushpatnaik.stashkeeper.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InventoryRulesTest {
    private val today = LocalDate.of(2026, 10, 14)

    @Test
    fun barcodeCandidatesKeepTheIndianGtin() {
        val link = barcodeCandidates("https://id.gs1.org/01/08901058000290")
        assertEquals("08901058000290", link.first())
        assertTrue(link.contains("8901058000290"))
        assertEquals(listOf("8901058000290"), barcodeCandidates("890 1058 000290"))
        assertEquals(listOf("012345678905", "0012345678905"), barcodeCandidates("012345678905"))
    }

    @Test
    fun openFactsNameUsesBrandWhenTheTitleIsOnlyTheProduct() {
        assertEquals(
            "Nestlé Maggi",
            openFactsName("maggi", null, null, "noodles", null, "Nestlé", null),
        )
    }

    @Test
    fun chocolateStaysPantryBeforeTheMilkRule() {
        assertEquals("Pantry & Food", stashCategory("Dairies, Milks", "Cadbury Dairy Milk"))
        assertEquals("Pantry & Food", stashCategory(null, "Bournville"))
        assertEquals("Dairy", stashCategory("Milks", "Toned milk"))
    }

    @Test
    fun differentExpiryStaysASeparateLot() {
        val items = listOf(sample(barcode = "890", expiry = "2026-10-20"))
        assertEquals("1", findSameLot(items, "890", "2026-10-20T00:00:00Z")?.id)
        assertNull(findSameLot(items, "890", "2026-11-01"))
        assertEquals("1", findSameLot(listOf(sample(barcode = "890", expiry = null)), "890", null)?.id)
    }

    @Test
    fun expiryUsesTheCalendarDayAndDropsEmptyRows() {
        val soon = sample(expiry = "2026-10-16", perishable = true)
        val todayItem = sample(id = "today", expiry = today.toString(), perishable = true)
        val gone = sample(id = "gone", expiry = "2026-10-01", perishable = true, quantity = 0)
        assertEquals(ExpiryStatus.ExpiringSoon, expiryStatus(soon, today, 5))
        assertEquals(ExpiryStatus.ExpiringSoon, expiryStatus(todayItem, today, 5))
        assertEquals("Expires today", expiryCaption(todayItem, today))
        assertEquals(listOf("today", "1"), attentionItems(listOf(soon, todayItem, gone), today, 5).map { it.id })
    }

    @Test
    fun groceryListIsParShortfallsPlusOpenNeeds() {
        val stocked = sample(name = "Rice", quantity = 1, par = 3)
        val open = PurchaseNeed("n1", "Eggs", 2, isChecked = false)
        val bought = PurchaseNeed("n2", "Soap", isChecked = true)
        val lines = groceryLines(listOf(stocked), listOf(open, bought))
        assertEquals(listOf("Eggs", "Rice"), lines.map { it.name })
        assertEquals(2, lines.first { it.name == "Rice" }.quantity)
    }

    @Test
    fun searchFallbackFindsExpiredFoodInAPlace() {
        val milk = sample(name = "Milk", category = "Dairy", expiry = "2026-10-01", perishable = true, location = "fridge")
        val passport = sample(id = "p", name = "Passport", category = "Documents", location = "desk")
        val places = listOf(StashPlace("fridge", "Fridge"), StashPlace("desk", "Desk"))
        val intent = keywordFallback("expired in the fridge", listOf("Dairy"), listOf("Fridge"))
        assertTrue(intent.expiryFocused)
        assertEquals("Fridge", intent.location)
        val hits = applySearch(listOf(milk, passport), places, "expired in the fridge", intent, today, 5)
        assertEquals(listOf("1"), hits.map { it.id })
    }

    @Test
    fun importKeepsLocalPhotosAndPar() {
        val existing = sample(name = "Old", par = 4, photos = listOf("local.jpg"))
        val incoming = sample(name = "New", par = 0, photos = listOf("remote.jpg"), quantity = 2)
        val merged = mergeHousehold(listOf(existing), emptyList(), listOf(incoming), emptyList())
        assertEquals(0, merged.inserted)
        assertEquals(1, merged.updated)
        assertEquals("New", merged.items.single().name)
        assertEquals(4, merged.items.single().parQuantity)
        assertEquals(listOf("local.jpg"), merged.items.single().photoFiles)
        assertEquals("Added 1 new item, updated 0.", importStatus(1, 0))
    }

    @Test
    fun milestonesAndLayoutFollowTheAppleThresholds() {
        val items = List(5) { sample(id = "c$it", category = "Dairy") }
        val bronze = allMilestones(items).first { it.tier == MilestoneTier.Bronze }
        assertTrue(bronze.isUnlocked)
        assertFalse(allMilestones(items).first { it.tier == MilestoneTier.Silver }.isUnlocked)
        assertEquals(1, columnCount(390f))
        assertEquals(2, columnCount(800f))
        assertEquals(3, columnCount(1200f))
        assertEquals(16f, horizontalPadding(390f))
        assertEquals(0.36, springResponseSeconds(120, false), 0.001)
        assertEquals(0.42, springResponseSeconds(60, false), 0.001)
        assertEquals(0.01, springResponseSeconds(120, true), 0.001)
        assertTrue(attentionCardWidth(390f) in 112f..168f)
    }

    @Test
    fun receiptSkipsTotalsAndQuantityChangesReadLikeTheAppleApp() {
        val lines = parseReceipt("Amul Butter 62.00\nTOTAL 62.00\nThank you")
        assertEquals(listOf("Amul Butter"), lines.map { it.name })
        assertEquals(62.0, lines.single().price!!, 0.001)
        val used = adjustQuantity(sample(quantity = 1), -1, "Piyush", 10L)
        assertEquals(0, used.quantity)
        assertEquals("used the last one", used.lastChangeSummary)
        assertEquals("Piyush", used.lastChangedBy)
    }

    @Test
    fun megaMartEstimateKeepsWrappedRowsAndTheAmountPaid() {
        val text = """
            ESTIMATE
            Mega Mart
            Dak Bunglow Road 72/a Dak Bunglow Road
            Mobile No: 8239463572
            Serial No.: 28023
            Date : 12/10/2026
            Payment Mode: Cash
            Customer Name:Walk-in Customer
            Item Qty MRP Rate Amount
            DETTOL
            SKINCARE
            LIQUID
            HW(675ML) 1 BTL 109 105 105
            JK POHA 500G 1 PCS 64 55 55
            FORTUNE
            SUN FLOWER
            OIL 1LTR 1 PK 240 185 185
            PCH
            CYCLE OM 1 PCS 140 130 130
            SHANTI
            CAMPORE
            50G
            SOUNDARYA 1 PCS 55 50 50
            ALOE GEL
            60ML
            EVEREST
            KASHMIRILAL-
            (50g) 1 PK 82 58 58
            ARHAR DAL
            500G 75/- 1 PK 0 75 75
            ECO MOONG
            DAL 500G
            65/- 1 PK 0 65 65
            GOTA
            LANKA
            100G 1 PK 0 35 35
            Total 9 758
            Items: 9
            Total saving:87.00
            Sub Total : 758.00
            Grand Total: 758.00
        """.trimIndent()
        val lines = parseReceipt(text)
        assertEquals(9, lines.size)
        assertEquals(105.0, lines[0].price!!, 0.001)
        assertTrue(lines[0].name.contains("Dettol"))
        assertEquals("Cosmetics & Toiletries", lines[0].category)
        assertEquals(
            listOf(105, 55, 185, 130, 50, 58, 75, 65, 35),
            lines.map { it.price?.toInt() },
        )
        assertEquals(1, lines[1].quantity)
        assertTrue(lines[1].name.contains("Poha"))
        assertTrue(lines.any { it.name.contains("Fortune") && it.price == 185.0 })
        assertTrue(lines.none { it.name.contains("Mega") || it.name.contains("Total") })
    }

    @Test
    fun receiptNutritionUsesABrandedIndiaMatchAndSkipsNonFood() {
        assertFalse(nutritionEligible("Dettol Skincare Liquid", "Cosmetics & Toiletries"))
        assertFalse(nutritionEligible("Shanti Campore 50g", "Pantry & Food"))
        assertTrue(nutritionEligible("Fortune Sun Flower Oil 1ltr", "Pantry & Food"))
        val fortune = NutritionCandidate(
            name = "Fortune Refined Sunflower Oil",
            brands = "Fortune",
            code = "8906007280280",
            india = true,
            nutrition = NutritionFacts(calories = 900, fatGrams = 100.0),
        )
        val generic = NutritionCandidate(
            name = "Sunflower oil",
            brands = "Sunflower oil",
            india = false,
            nutrition = NutritionFacts(calories = 828, fatGrams = 92.0),
        )
        val chosen = chooseNutrition("Fortune Sun Flower Oil 1ltr", listOf(generic, fortune))
        assertEquals(900, chosen?.calories)
        assertEquals("Open Food Facts", chosen?.source)
        assertNull(chooseNutrition("Jk Poha 500g", listOf(generic)))
        val poha = NutritionCandidate(
            name = "JK Poha",
            brands = "JK",
            india = true,
            nutrition = NutritionFacts(calories = 350, carbsGrams = 77.0, proteinGrams = 6.0),
        )
        assertEquals(350, chooseNutrition("Jk Poha 500g", listOf(poha))?.calories)
    }

    @Test
    fun aPackPhotoKeepsThePrintedPanelAndTheExpiryDate() {
        val lines = listOf(
            "FORTUNE SUNFLOWER OIL",
            "Best before 12/10/2027",
            "Mfg 01/01/2026",
            "Per 100 g",
            "Energy 900 kcal",
            "Protein 0 g",
            "Total fat 100 g",
            "Saturated fat 17 g",
            "Ingredients: sunflower oil, antioxidant",
        )
        val read = readPack(lines, LocalDate.of(2026, 10, 14))
        assertEquals("2027-10-12", read.expiry)
        assertEquals(900, read.nutrition?.calories)
        assertEquals(100.0, read.nutrition?.fatGrams!!, 0.01)
        assertNull(read.nutrition?.sugarGrams)
        assertEquals("Printed on the pack", read.nutrition?.source)
        assertTrue(read.ingredients!!.contains("sunflower oil"))
        assertEquals("Dal", pantryRecipes(listOf("Moong dal 500g")).first().title)
    }

    @Test
    fun aDescendantCannotBecomeItsOwnAncestor() {
        val places = listOf(
            StashPlace("garage", "Garage"),
            StashPlace("shelf", "Shelf", parentId = "garage"),
        )
        assertTrue(isUnder("shelf", "garage", places))
        assertTrue(parentChoices("garage", places).isEmpty())
        assertEquals(listOf("garage"), parentChoices("shelf", places).map { it.id })
    }

    private fun sample(
        id: String = "1",
        name: String = "Milk",
        category: String = "Dairy",
        quantity: Int = 1,
        par: Int = 0,
        expiry: String? = null,
        perishable: Boolean = expiry != null,
        barcode: String? = null,
        location: String? = null,
        photos: List<String> = emptyList(),
    ) = StashItem(
        id = id,
        name = name,
        category = category,
        quantity = quantity,
        parQuantity = par,
        isPerishable = perishable,
        expiry = expiry,
        barcode = barcode,
        locationId = location,
        photoFiles = photos,
    )
}
