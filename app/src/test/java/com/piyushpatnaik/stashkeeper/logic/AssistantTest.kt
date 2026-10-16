package com.piyushpatnaik.stashkeeper.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AssistantTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val fridge = listOf(StashPlace("fridge", "Fridge"))

    @Test
    fun findKeepsANamedItemAndAZeroQuantity() {
        val items = listOf(
            item("milk", "Toned milk", "Dairy", location = "fridge", expiry = "2026-10-10"),
            item("soap", "Dettol", "Cosmetics & Toiletries"),
            item("jar", "Empty jar of ghee", "Pantry & Food", quantity = 0),
        )
        val milk = assistantFind(items, fridge, "milk in the fridge", today, 5)
        assertEquals("Found Toned milk.", milk.speech)
        assertTrue(milk.lines.single().startsWith("Toned milk"))
        val ghee = assistantFind(items, fridge, "ghee", today, 5)
        assertEquals("Found Empty jar of ghee.", ghee.speech)
    }

    @Test
    fun expiringSkipsAFreshItem() {
        val items = listOf(
            item("milk", "Toned milk", "Dairy", expiry = "2026-10-10"),
            item("poha", "JK Poha", "Pantry & Food", expiry = "2026-10-16"),
            item("rice", "Rice", "Pantry & Food", expiry = "2026-12-01"),
        )
        val reply = assistantExpiring(items, fridge, today, 5)
        assertEquals("2 items need a look.", reply.speech)
        assertEquals(2, reply.lines.size)
        assertTrue(reply.lines[0].startsWith("Toned milk"))
        assertTrue(reply.lines[1].startsWith("JK Poha"))
    }

    @Test
    fun shoppingListsAShortfallAndAHandwrittenNeed() {
        val items = listOf(item("dal", "Moong dal", "Pantry & Food", quantity = 0, par = 2))
        val needs = listOf(
            PurchaseNeed("n1", "Salt"),
            PurchaseNeed("n2", "Checked", isChecked = true),
        )
        val reply = assistantShopping(items, needs)
        assertEquals("2 things to buy.", reply.speech)
        assertEquals(listOf("Buy 2 Moong dal", "Buy 1 Salt"), reply.lines)
    }

    @Test
    fun addUsesTheSameDefaultCategoryAsTheSheet() {
        val added = assistantNewItem("Eggs", null, null, 10L, "Me")
        assertEquals("Eggs", added.name)
        assertEquals(1, added.quantity)
        assertEquals("Pantry & Food", added.category)
        assertEquals("added from an assistant", added.lastChangeSummary)
        try {
            assistantNewItem("  ", 1, null, 10L, "Me")
            error("blank name should be rejected")
        } catch (error: AssistantRejected) {
            assertEquals("An item needs a name.", error.message)
        }
        try {
            assistantNewItem("Eggs", 0, null, 10L, "Me")
            error("zero quantity should be rejected")
        } catch (error: AssistantRejected) {
            assertEquals("Quantity needs to be from 1 to 999.", error.message)
        }
        try {
            assistantShoppingName("  ")
            error("blank shopping name should be rejected")
        } catch (error: AssistantRejected) {
            assertEquals("Say what to buy.", error.message)
        }
    }

    private fun item(
        id: String,
        name: String,
        category: String,
        quantity: Int = 1,
        par: Int = 0,
        expiry: String? = null,
        location: String? = null,
    ) = StashItem(
        id = id,
        name = name,
        category = category,
        quantity = quantity,
        parQuantity = par,
        isPerishable = expiry != null,
        expiry = expiry,
        locationId = location,
    )
}
