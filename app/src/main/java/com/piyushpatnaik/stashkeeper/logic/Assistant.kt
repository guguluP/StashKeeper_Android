package com.piyushpatnaik.stashkeeper.logic

import java.time.LocalDate
import java.util.UUID

data class AssistantReply(val speech: String, val lines: List<String> = emptyList())

class AssistantRejected(message: String) : Exception(message)

fun assistantFind(
    items: List<StashItem>,
    places: List<StashPlace>,
    query: String,
    today: LocalDate,
    warningDays: Int,
): AssistantReply {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) throw AssistantRejected("Say what to look for.")
    val intent = keywordFallback(trimmed, itemCategoryOptions, places.map { it.name })
    val matches = applySearch(items, places, trimmed, intent, today, warningDays)
    if (matches.isEmpty()) return AssistantReply("Nothing in the stash matches that.")
    val lines = matches.take(12).map { assistantItemLine(it, places, today) }
    val speech = if (matches.size == 1) "Found ${matches.first().name}." else "Found ${matches.size} items."
    return AssistantReply(speech, lines)
}

fun assistantExpiring(items: List<StashItem>, places: List<StashPlace>, today: LocalDate, warningDays: Int): AssistantReply {
    val attention = attentionItems(items, today, warningDays)
    if (attention.isEmpty()) return AssistantReply("Nothing is expiring soon.")
    val lines = attention.take(12).map { assistantItemLine(it, places, today) }
    val speech = if (attention.size == 1) "${attention.first().name} needs a look." else "${attention.size} items need a look."
    return AssistantReply(speech, lines)
}

fun assistantShopping(items: List<StashItem>, needs: List<PurchaseNeed>): AssistantReply {
    val lines = groceryLines(items, needs)
    if (lines.isEmpty()) return AssistantReply("The shopping list is empty.")
    val text = lines.take(12).map { "Buy ${it.quantity} ${it.name}" }
    val speech = if (lines.size == 1) "${text.first()}." else "${lines.size} things to buy."
    return AssistantReply(speech, text)
}

fun assistantShoppingName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) throw AssistantRejected("Say what to buy.")
    return trimmed
}

fun assistantNewItem(name: String, quantity: Int?, locationId: String?, now: Long, who: String): StashItem {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) throw AssistantRejected("An item needs a name.")
    val count = quantity ?: 1
    if (count !in 1..999) throw AssistantRejected("Quantity needs to be from 1 to 999.")
    return StashItem(
        id = UUID.randomUUID().toString(),
        name = trimmed,
        category = itemCategoryOptions.first(),
        quantity = count,
        locationId = locationId,
        lastChangedBy = who.ifBlank { "Me" },
        lastChangeSummary = "added from an assistant",
        createdAt = now,
        updatedAt = now,
    )
}

private fun assistantItemLine(item: StashItem, places: List<StashPlace>, today: LocalDate): String {
    val place = places.firstOrNull { it.id == item.locationId }?.name ?: "Unsorted"
    val expiry = expiryCaption(item, today).ifBlank { "no expiry" }
    val amount = listOf(item.quantity.toString(), item.unit).filter { !it.isNullOrBlank() }.joinToString(" ")
    return "${item.name}, $amount, $place, $expiry"
}
