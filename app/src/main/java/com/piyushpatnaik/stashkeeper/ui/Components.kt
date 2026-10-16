package com.piyushpatnaik.stashkeeper.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.piyushpatnaik.stashkeeper.data.StashStore
import com.piyushpatnaik.stashkeeper.logic.StashItem
import com.piyushpatnaik.stashkeeper.logic.expiryCaption
import com.piyushpatnaik.stashkeeper.logic.expiryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StashHaptics(private val view: View) {
    fun impact() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    fun success() = pulse(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
    fun alignment() = pulse(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK)

    private fun pulse(constant: Int) = view.performHapticFeedback(constant)
}

@Composable
fun rememberHaptics(): StashHaptics {
    val view = LocalView.current
    return remember(view) { StashHaptics(view) }
}

@Composable
fun Modifier.glass(corner: Dp = 20.dp): Modifier {
    val shape = RoundedCornerShape(corner)
    val fill = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val stroke = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    return this
        .shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.06f))
        .clip(shape)
        .background(fill)
        .border(0.5.dp, stroke, shape)
}

@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    val metrics = LocalDisplayMetrics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = metrics.springStiffness()),
        label = "press",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.85f else 1f
            }
            .clickable(interactionSource = interaction, indication = null) {
                if (haptic) haptics.impact()
                onClick()
            },
    ) { content() }
}

@Composable
fun Page(content: @Composable () -> Unit) {
    val metrics = LocalDisplayMetrics.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = metrics.readableWidth).fillMaxWidth()) { content() }
    }
}

fun categoryIcon(category: String): ImageVector = when (category) {
    "Pantry & Food" -> Icons.Filled.ShoppingBasket
    "Fresh Produce" -> Icons.Filled.Eco
    "Dairy" -> Icons.Filled.WaterDrop
    "Beverages" -> Icons.Filled.LocalDrink
    "Medicine & Health" -> Icons.Filled.MedicalServices
    "Cosmetics & Toiletries" -> Icons.Filled.WaterDrop
    "Electronics" -> Icons.Filled.Build
    "Watches & Jewelry" -> Icons.Filled.Watch
    "Documents" -> Icons.Filled.Description
    "Clothing" -> Icons.Filled.Checkroom
    "Tools & Hardware" -> Icons.Filled.Build
    "Kitchenware" -> Icons.Filled.Restaurant
    "Cleaning Supplies" -> Icons.Filled.CleaningServices
    "Stationery" -> Icons.Filled.Edit
    "Toys & Games" -> Icons.Filled.SportsEsports
    "Sports & Outdoors" -> Icons.Filled.Hiking
    "Automotive" -> Icons.Filled.DirectionsCar
    else -> Icons.Filled.Inventory2
}

fun locationIcon(name: String): ImageVector = when (name) {
    "refrigerator" -> Icons.Filled.Kitchen
    "cross.case" -> Icons.Filled.MedicalServices
    "car" -> Icons.Filled.DirectionsCar
    "bed.double" -> Icons.Filled.Bed
    "table.furniture" -> Icons.Filled.TableRestaurant
    "shippingbox" -> Icons.Filled.LocalShipping
    "cabinet" -> Icons.Filled.Kitchen
    else -> Icons.Filled.Inventory2
}

@Composable
fun ItemThumbnail(store: StashStore, item: StashItem, size: Dp) {
    val pixels = with(LocalDensity.current) { size.roundToPx() } * 2
    var bitmap by remember(item.photoFiles.firstOrNull(), pixels) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(item.photoFiles.firstOrNull(), pixels) {
        val name = item.photoFiles.firstOrNull() ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) { store.thumbnail(name, pixels) }
    }
    val shape = RoundedCornerShape(size * 0.2f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image.asImageBitmap(), item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            androidx.compose.material3.Icon(
                categoryIcon(item.category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ItemRow(store: StashStore, item: StashItem, modifier: Modifier = Modifier) {
    val status = expiryStatus(item, store.today(), store.warningDays)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ItemThumbnail(store, item, 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(item.category, store.placeName(item.locationId), "×${item.quantity}").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.isPerishable) ExpiryCapsule(expiryCaption(item, store.today()), status)
        }
    }
}

@Composable
fun ExpiryCapsule(label: String, status: com.piyushpatnaik.stashkeeper.logic.ExpiryStatus) {
    if (label.isBlank()) return
    val short = when {
        label.startsWith("Expires today") -> "Today"
        label.startsWith("Expires tomorrow") -> "Tomorrow"
        label.startsWith("Expired") -> "Expired"
        else -> label
    }
    Text(
        short,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(expiryColor(status).copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = expiryColor(status),
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
fun QuantityStepper(value: Int, onChange: (Int) -> Unit, range: IntRange = 0..999) {
    val haptics = rememberHaptics()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalIconButton(onClick = {
            haptics.alignment()
            onChange((value - 1).coerceIn(range))
        }) { androidx.compose.material3.Icon(Icons.Filled.Remove, "Less") }
        Text(
            "$value",
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = {
            haptics.alignment()
            onChange((value + 1).coerceIn(range))
        }) { androidx.compose.material3.Icon(Icons.Filled.Add, "More") }
    }
}

@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = LocalDisplayMetrics.current.horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun SectionTitle(text: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = tint)
}

@Composable
fun EmptyCopy(title: String, detail: String) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
