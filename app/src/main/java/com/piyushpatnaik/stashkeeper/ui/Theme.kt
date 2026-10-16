package com.piyushpatnaik.stashkeeper.ui

import android.app.Activity
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.piyushpatnaik.stashkeeper.logic.ExpiryStatus
import com.piyushpatnaik.stashkeeper.logic.MilestoneTier
import com.piyushpatnaik.stashkeeper.logic.attentionCardWidth
import com.piyushpatnaik.stashkeeper.logic.columnCount
import com.piyushpatnaik.stashkeeper.logic.frameIntervalSeconds
import com.piyushpatnaik.stashkeeper.logic.horizontalPadding
import com.piyushpatnaik.stashkeeper.logic.locationChipWidth
import com.piyushpatnaik.stashkeeper.logic.readableWidth
import com.piyushpatnaik.stashkeeper.logic.sectionSpacing
import com.piyushpatnaik.stashkeeper.logic.springResponseSeconds
import com.piyushpatnaik.stashkeeper.logic.statColumns
import kotlin.math.PI
import kotlin.math.roundToInt

private val Accent = Color(0xFF31C398)
private val AccentDark = Color(0xFF4CCDA9)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0C6B52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F3E2),
    secondary = Color(0xFF6A4C93),
    background = Color(0xFFF7F4EF),
    surface = Color(0xFFFBF9F6),
    surfaceContainer = Color(0xFFF3EFE8),
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF003328),
    primaryContainer = Color(0xFF0C6B52),
    secondary = Color(0xFFD2BCF5),
    background = Color(0xFF121411),
    surface = Color(0xFF1A1C19),
    surfaceContainer = Color(0xFF232622),
)

@Immutable
data class DisplayMetrics(
    val width: Float,
    val height: Float,
    val refreshRate: Int,
    val reduceMotion: Boolean,
) {
    val columnCount: Int = columnCount(width)
    val horizontalPadding: Dp = horizontalPadding(width).dp
    val readableWidth: Dp = readableWidth(width).dp
    val sectionSpacing: Dp = sectionSpacing(width).dp
    val attentionCardWidth: Dp = attentionCardWidth(width).dp
    val locationChipWidth: Dp = locationChipWidth(width).dp
    val statColumns: Int = statColumns(width)
    val wide: Boolean = columnCount >= 2
    val frameIntervalSeconds: Double = frameIntervalSeconds(refreshRate)

    fun springStiffness(): Float {
        val response = springResponseSeconds(refreshRate, reduceMotion).toFloat()
        val omega = (2.0 * PI / response).toFloat()
        return omega * omega
    }
}

val LocalDisplayMetrics = staticCompositionLocalOf {
    DisplayMetrics(390f, 844f, 60, false)
}

@Composable
fun StashKeeperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

@Composable
fun ProvideDisplay(refreshRate: Int, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        scale == 0f
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val metrics = remember(maxWidth, maxHeight, refreshRate, reduceMotion) {
            DisplayMetrics(
                width = maxWidth.value,
                height = maxHeight.value,
                refreshRate = refreshRate.coerceAtLeast(30),
                reduceMotion = reduceMotion,
            )
        }
        CompositionLocalProvider(LocalDisplayMetrics provides metrics, content = content)
    }
}

fun Activity.requestNativeRefreshRate(): Int {
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay
    } ?: return 60
    val current = display.mode
    val sameSize = display.supportedModes.filter {
        it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
    }
    val best = sameSize.maxByOrNull { it.refreshRate }
        ?: display.supportedModes.maxByOrNull { it.refreshRate }
    val rate = (best?.refreshRate ?: display.refreshRate).coerceAtLeast(30f)
    val params = window.attributes
    if (best != null) params.preferredDisplayModeId = best.modeId
    @Suppress("DEPRECATION")
    params.preferredRefreshRate = rate
    window.attributes = params
    window.applyNativeFrameRate()
    return rate.roundToInt().coerceAtLeast(30)
}

private fun Window.applyNativeFrameRate() {
    if (Build.VERSION.SDK_INT >= 35) {
        setFrameRatePowerSavingsBalanced(false)
        setFrameRateBoostOnTouchEnabled(true)
    }
    decorView.post { decorView.requestHighFrameRate() }
}

private fun View.requestHighFrameRate() {
    if (Build.VERSION.SDK_INT >= 35) {
        setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)
        if (this is ViewGroup) {
            for (index in 0 until childCount) getChildAt(index).requestHighFrameRate()
        }
    }
}

@Composable
fun expiryColor(status: ExpiryStatus): Color {
    val dark = isSystemInDarkTheme()
    return when (status) {
        ExpiryStatus.Fresh -> if (dark) Color(0xFF8CF29E) else Color(0xFF00662E)
        ExpiryStatus.ExpiringSoon -> if (dark) Color(0xFFFFB847) else Color(0xFF8C4700)
        ExpiryStatus.Expired -> if (dark) Color(0xFFFF7A73) else Color(0xFF9E141A)
        ExpiryStatus.NotPerishable -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun expiryInk(): Color = if (isSystemInDarkTheme()) Color.Black else Color.White

@Composable
fun tierColor(tier: MilestoneTier): Color {
    val dark = isSystemInDarkTheme()
    return when (tier) {
        MilestoneTier.Bronze -> if (dark) Color(0xFFE69E52) else Color(0xFF73420D)
        MilestoneTier.Silver -> if (dark) Color(0xFFD1D6E0) else Color(0xFF525761)
        MilestoneTier.Gold -> if (dark) Color(0xFFFFDB59) else Color(0xFF7A5700)
        MilestoneTier.Platinum -> if (dark) Color(0xFFA6E6FA) else Color(0xFF1F6185)
    }
}

