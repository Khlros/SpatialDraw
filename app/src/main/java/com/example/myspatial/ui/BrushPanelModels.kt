package com.example.myspatial.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlin.math.roundToInt

internal data class HandBrushPanelUiState(
    val title: String,
    val collapsed: Boolean,
    val selectedColor: BrushColorState,
    val brushRadius: Float,
    val selectedMode: BrushMode,
    val selectedMaterial: BrushMaterialPreset,
    val opacity: Float,
    val selectedColorMode: BrushColorMode,
    val textureSelection: TextureCropSelection?,
)

internal sealed interface HandBrushPanelAction {
    data class SelectColor(
        val color: BrushColorState,
    ) : HandBrushPanelAction

    data class SelectColorMode(
        val mode: BrushColorMode,
    ) : HandBrushPanelAction

    data class SetBrushRadius(
        val radius: Float,
    ) : HandBrushPanelAction

    data class SelectBrushMode(
        val mode: BrushMode,
    ) : HandBrushPanelAction

    data class SelectMaterial(
        val material: BrushMaterialPreset,
    ) : HandBrushPanelAction

    data class SetOpacity(
        val opacity: Float,
    ) : HandBrushPanelAction

    data class SetInteractionActive(
        val active: Boolean,
    ) : HandBrushPanelAction

    data object RequestTextureUpload : HandBrushPanelAction

    data object SaveProject : HandBrushPanelAction

    data object OpenProjectBrowser : HandBrushPanelAction

    data object Collapse : HandBrushPanelAction

    data object Undo : HandBrushPanelAction

    data object ClearCanvas : HandBrushPanelAction
}

internal data class BrushColorState(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    val uiColor: Color
        get() = Color(AndroidColor.HSVToColor(floatArrayOf(normalizeHue(hue), saturation, value)))

    fun toColor4(): Color4 {
        val linearColor = uiColor.convert(ColorSpaces.LinearSrgb)
        return Color4(linearColor.red, linearColor.green, linearColor.blue, linearColor.alpha)
    }

    companion object {
        fun fromUiColor(color: Color): BrushColorState {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(color.toArgb(), hsv)
            return BrushColorState(
                hue = normalizeHue(hsv[0]),
                saturation = hsv[1].coerceIn(0f, 1f),
                value = hsv[2].coerceIn(0f, 1f),
            )
        }
    }
}

internal data class TextureCropSelection(
    val sourceBitmap: Bitmap,
    val cropLeftPx: Int,
    val cropTopPx: Int,
    val cropSizePx: Int,
) {
    companion object
}

internal data class SquareCropRect(
    val left: Int,
    val top: Int,
    val size: Int,
)

internal fun TextureCropSelection.squareCropRect(): SquareCropRect {
    val minCropSize = sourceBitmap.minCropSizePx()
    val maxCropSize = minOf(sourceBitmap.width, sourceBitmap.height).coerceAtLeast(minCropSize)
    val cropSize = cropSizePx.coerceIn(minCropSize, maxCropSize)
    val maxLeft = (sourceBitmap.width - cropSize).coerceAtLeast(0)
    val maxTop = (sourceBitmap.height - cropSize).coerceAtLeast(0)
    return SquareCropRect(
        left = cropLeftPx.coerceIn(0, maxLeft),
        top = cropTopPx.coerceIn(0, maxTop),
        size = cropSize,
    )
}

internal fun TextureCropSelection.withCropRect(
    left: Int,
    top: Int,
    size: Int,
): TextureCropSelection {
    val minCropSize = sourceBitmap.minCropSizePx()
    val maxCropSize = minOf(sourceBitmap.width, sourceBitmap.height).coerceAtLeast(minCropSize)
    val clampedSize = size.coerceIn(minCropSize, maxCropSize)
    val maxLeft = (sourceBitmap.width - clampedSize).coerceAtLeast(0)
    val maxTop = (sourceBitmap.height - clampedSize).coerceAtLeast(0)
    return copy(
        cropLeftPx = left.coerceIn(0, maxLeft),
        cropTopPx = top.coerceIn(0, maxTop),
        cropSizePx = clampedSize,
    )
}

internal fun Bitmap.minCropSizePx(): Int =
    (minOf(width, height) * MIN_TEXTURE_CROP_SIZE_RATIO).roundToInt().coerceAtLeast(MIN_TEXTURE_CROP_SIZE_PX)

internal fun TextureCropSelection.Companion.createDefault(sourceBitmap: Bitmap): TextureCropSelection {
    val size = minOf(sourceBitmap.width, sourceBitmap.height).coerceAtLeast(sourceBitmap.minCropSizePx())
    return TextureCropSelection(
        sourceBitmap = sourceBitmap,
        cropLeftPx = (sourceBitmap.width - size) / 2,
        cropTopPx = (sourceBitmap.height - size) / 2,
        cropSizePx = size,
    )
}

internal enum class BrushMode {
    Tube,
    SphereSteps,
    GrowingSphere,
    StraightLine,
}

internal enum class BrushColorMode {
    PureColor,
    Texture,
}

internal enum class BrushMaterialPreset {
    Metal,
    Fabric,
    Plastic,
    Unlit,
}

internal enum class HandSide {
    Left,
    Right,
}

internal enum class ProjectPanelMode {
    Save,
    Open,
}

internal const val DEFAULT_BRUSH_OPACITY = 1f
internal const val DEFAULT_BRUSH_RADIUS_M = 0.008f
internal const val MIN_BRUSH_RADIUS_M = 0.0035f
internal const val MAX_BRUSH_RADIUS_M = 0.018f
internal const val TEXTURE_OUTPUT_SIZE_PX = 512
internal const val MAX_TEXTURE_IMPORT_EDGE_PX = 2048
internal const val MIN_TEXTURE_CROP_SIZE_PX = 192
internal const val MIN_TEXTURE_CROP_SIZE_RATIO = 0.22f
internal const val LEFT_CONTROL_PANEL_ATTACHMENT_ID = "left_brush_control_panel"
internal const val RIGHT_CONTROL_PANEL_ATTACHMENT_ID = "right_brush_control_panel"
internal const val TEXTURE_PICKER_ATTACHMENT_ID = "texture_picker_panel"
internal const val PROJECT_BROWSER_ATTACHMENT_ID = "project_browser_panel"
internal val LEFT_CONTROL_PANEL_POSITION = Vector3(-0.38f, 1.20f, -0.58f)
internal val RIGHT_CONTROL_PANEL_POSITION = Vector3(0.38f, 1.20f, -0.58f)
internal val TEXTURE_PICKER_PANEL_POSITION = Vector3(0f, 1.12f, -0.50f)
internal val PROJECT_BROWSER_PANEL_POSITION = Vector3(0f, 1.10f, -0.52f)
internal const val CONTROL_PANEL_SCALE = 1.0f
internal val CONTROL_PANEL_WIDTH_DP = 450.dp
internal val CONTROL_PANEL_HEIGHT_DP = 1120.dp
internal val CONTROL_PANEL_SCROLL_CONTENT_HEIGHT_DP = 940.dp
internal val TEXTURE_PICKER_PANEL_WIDTH_DP = 440.dp
internal val TEXTURE_PICKER_PANEL_HEIGHT_DP = 560.dp
internal val PROJECT_BROWSER_PANEL_WIDTH_DP = 460.dp
internal val PROJECT_BROWSER_PANEL_HEIGHT_DP = 620.dp
internal val COLLAPSED_PANEL_SIZE_DP = 72.dp
internal const val COLLAPSED_PANEL_WORLD_SCALE = 0.32f
internal const val PANEL_TRANSFORM_LERP = 0.22f
internal const val PANEL_ORB_FORWARD_OFFSET_M = 0.05f
internal const val PANEL_EXPANDED_FORWARD_OFFSET_M = 0.15f
internal const val PANEL_EXPANDED_VERTICAL_OFFSET_M = 0.08f
internal const val PANEL_EXPAND_GESTURE_DISTANCE_M = 0.028f
internal val DEFAULT_LEFT_BRUSH_COLOR = BrushColorState.fromUiColor(Color(0xFF4E8CFF))
internal val DEFAULT_RIGHT_BRUSH_COLOR = BrushColorState.fromUiColor(Color(0xFFFF7A59))
internal val HUE_TRACK_COLORS =
    listOf(
        Color.Red,
        Color.Yellow,
        Color.Green,
        Color.Cyan,
        Color.Blue,
        Color.Magenta,
        Color.Red,
    )
internal val COLOR_TRACK_WIDTH_DP = 236.dp
internal val COLOR_TRACK_LABEL_WIDTH_DP = 40.dp
internal val COLOR_TRACK_VALUE_WIDTH_DP = 44.dp
internal val BRUSH_SIZE_TRACK_WIDTH_DP = 212.dp
internal val MATERIAL_SWATCH_SIZE_DP = 44.dp
internal val TEXTURE_CROP_PREVIEW_SIZE_DP = 132.dp
internal val TEXTURE_CROP_EDITOR_PREVIEW_SIZE_DP = 320.dp
internal val TEXTURE_CROP_HANDLE_SIZE_DP = 28.dp
internal val TEXTURE_CROP_HANDLE_HIT_SIZE_DP = 56.dp
internal val BRUSH_PREVIEW_PANEL_CLEARANCE_DP = 72.dp
internal val COLOR_TRACK_TOUCH_HEIGHT_DP = 34.dp
internal val COLOR_TRACK_HEIGHT_DP = 14.dp
internal val COLOR_TRACK_KNOB_DIAMETER_DP = 28.dp
internal val COLOR_TRACK_KNOB_HIT_SIZE_DP = 52.dp
internal val COLOR_TRACK_HORIZONTAL_PADDING_DP = 14.dp
internal const val COLOR_TRACK_KNOB_HOVER_SCALE = 1.78f
internal const val TRUE_COLOR_WHEEL_MARKER_OUTER_RADIUS_PX = 11.5f
internal const val TRUE_COLOR_WHEEL_MARKER_INNER_RADIUS_PX = 7f
internal const val TRUE_COLOR_WHEEL_MARKER_HOVER_HALO_PX = 10f
internal const val TRUE_COLOR_WHEEL_MARKER_HOVER_RING_PX = 6f
internal const val TEXTURE_CROP_HANDLE_HOVER_SCALE = 1.45f
internal val OPACITY_TRACK_COLORS =
    listOf(
        Color.White.copy(alpha = 0.16f),
        Color.White.copy(alpha = 0.45f),
        Color.White.copy(alpha = 1f),
    )
internal val BRUSH_MATERIAL_PRESETS =
    listOf(
        BrushMaterialPreset.Metal,
        BrushMaterialPreset.Fabric,
        BrushMaterialPreset.Plastic,
        BrushMaterialPreset.Unlit,
    )

private fun normalizeHue(hue: Float): Float {
    val normalized = hue % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}
