package com.example.myspatial.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween as composeTween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.hover.tween
import kotlin.math.roundToInt

@Composable
internal fun HandBrushPanel(
    state: HandBrushPanelUiState,
    onAction: (HandBrushPanelAction) -> Unit,
) {
    DisposableEffect(Unit) {
        onDispose { onAction(HandBrushPanelAction.SetInteractionActive(false)) }
    }

    DisposableEffect(state.collapsed) {
        if (state.collapsed) {
            onAction(HandBrushPanelAction.SetInteractionActive(false))
        }
        onDispose { }
    }

    val panelWidth by animateDpAsState(
        targetValue = if (state.collapsed) COLLAPSED_PANEL_SIZE_DP else CONTROL_PANEL_WIDTH_DP,
        animationSpec = composeTween(durationMillis = 220),
        label = "panel_width",
    )
    val panelHeight by animateDpAsState(
        targetValue = if (state.collapsed) COLLAPSED_PANEL_SIZE_DP else CONTROL_PANEL_HEIGHT_DP,
        animationSpec = composeTween(durationMillis = 220),
        label = "panel_height",
    )

    Box(
        modifier =
            Modifier
                .width(panelWidth)
                .height(panelHeight),
    ) {
        Crossfade(
            targetState = state.collapsed,
            animationSpec = composeTween(durationMillis = 220),
            label = "panel_mode",
        ) { isCollapsed ->
            if (isCollapsed) {
                CollapsedHandOrb(
                    title = state.title,
                    color = state.selectedColor.uiColor,
                )
            } else {
                ExpandedHandBrushPanel(
                    state = state,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun ExpandedHandBrushPanel(
    state: HandBrushPanelUiState,
    onAction: (HandBrushPanelAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier =
            Modifier
                .width(CONTROL_PANEL_WIDTH_DP)
                .fillMaxHeight()
                .background(Color(0xCC1B1D24))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down =
                            awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed }
                                ?: return@awaitEachGesture
                        onAction(HandBrushPanelAction.SetInteractionActive(true))
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) {
                                onAction(HandBrushPanelAction.SetInteractionActive(false))
                                break
                            }
                        } while (true)
                    }
                }
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Column(
            modifier =
                Modifier
                    .height(CONTROL_PANEL_SCROLL_CONTENT_HEIGHT_DP)
                    .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = Modifier.width(CONTROL_PANEL_WIDTH_DP - 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = state.title, style = PicoTheme.typography.titleLarge, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { onAction(HandBrushPanelAction.Undo) }) {
                        Text(text = "撤销")
                    }
                    Button(onClick = { onAction(HandBrushPanelAction.ClearCanvas) }) {
                        Text(text = "清空")
                    }
                    Button(onClick = { onAction(HandBrushPanelAction.Collapse) }) {
                        Text(text = "收起")
                    }
                }
            }
            Text(
                text = "双侧都缩小时，直接 pinch 即可绘画；面板展开时仍需 long pinch。拇指碰小拇指可展开或收起。",
                style = PicoTheme.typography.bodyMedium,
                color = Color.White,
            )

            Text(text = "笔刷", style = PicoTheme.typography.titleMedium, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrushOptionButton(
                    label = "动态线",
                    selected = state.selectedMode == BrushMode.Tube,
                    onClick = { onAction(HandBrushPanelAction.SelectBrushMode(BrushMode.Tube)) },
                )
                BrushOptionButton(
                    label = "圆球虚线",
                    selected = state.selectedMode == BrushMode.SphereSteps,
                    onClick = { onAction(HandBrushPanelAction.SelectBrushMode(BrushMode.SphereSteps)) },
                )
                BrushOptionButton(
                    label = "圆球",
                    selected = state.selectedMode == BrushMode.GrowingSphere,
                    onClick = { onAction(HandBrushPanelAction.SelectBrushMode(BrushMode.GrowingSphere)) },
                )
                BrushOptionButton(
                    label = "直线",
                    selected = state.selectedMode == BrushMode.StraightLine,
                    onClick = { onAction(HandBrushPanelAction.SelectBrushMode(BrushMode.StraightLine)) },
                )
            }

            Text(text = "示例", style = PicoTheme.typography.titleMedium, color = Color.White)
            Spacer(modifier = Modifier.height(BRUSH_PREVIEW_PANEL_CLEARANCE_DP))
            BrushSizeControl(
                radius = state.brushRadius,
                onRadiusChange = { onAction(HandBrushPanelAction.SetBrushRadius(it)) },
            )

            Text(text = "材质", style = PicoTheme.typography.titleMedium, color = Color.White)
            MaterialPresetPicker(
                selectedMaterial = state.selectedMaterial,
                previewColor = state.selectedColor.uiColor,
                onMaterialSelected = {
                    onAction(HandBrushPanelAction.SelectMaterial(it))
                },
            )
            ColorTrackSlider(
                label = "透明",
                value = state.opacity,
                valueText = "${(state.opacity * 100f).roundToInt()}%",
                knobColor = Color.White,
                gradientColors = OPACITY_TRACK_COLORS,
                trackWidth = BRUSH_SIZE_TRACK_WIDTH_DP,
                onValueChange = { onAction(HandBrushPanelAction.SetOpacity(it)) },
            )

            Text(text = "颜色", style = PicoTheme.typography.titleMedium, color = Color.White)
            ColorSourcePicker(
                selectedMode = state.selectedColorMode,
                textureSelection = state.textureSelection,
                selectedColor = state.selectedColor,
                onColorModeSelected = {
                    onAction(HandBrushPanelAction.SelectColorMode(it))
                },
                onTextureUploadRequest = {
                    onAction(HandBrushPanelAction.RequestTextureUpload)
                },
            )
            if (state.selectedColorMode == BrushColorMode.PureColor) {
                TripleColorTrackPalette(
                    selectedColor = state.selectedColor,
                    onColorSelected = { onAction(HandBrushPanelAction.SelectColor(it)) },
                )
            }
        }
    }
}

@Composable
private fun CollapsedHandOrb(
    title: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .size(COLLAPSED_PANEL_SIZE_DP)
                .background(Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = color.copy(alpha = 0.22f),
                radius = size.minDimension * 0.48f,
                center = center,
            )
            drawCircle(
                color = color.copy(alpha = 0.40f),
                radius = size.minDimension * 0.34f,
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                radius = size.minDimension * 0.18f,
                center = center,
            )
        }
        Text(
            text = title.take(1),
            style = PicoTheme.typography.bodyMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun TripleColorTrackPalette(
    selectedColor: BrushColorState,
    onColorSelected: (BrushColorState) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        ColorTrackSlider(
            label = "色相",
            value = selectedColor.hue / 360f,
            valueText = selectedColor.hue.roundToInt().toString(),
            knobColor = selectedColor.uiColor,
            gradientColors = HUE_TRACK_COLORS,
            onValueChange = { trackValue ->
                onColorSelected(selectedColor.copy(hue = trackValue * 360f))
            },
        )
        ColorTrackSlider(
            label = "饱和",
            value = selectedColor.saturation,
            valueText = (selectedColor.saturation * 100f).roundToInt().toString(),
            knobColor = selectedColor.uiColor,
            gradientColors =
                listOf(
                    BrushColorState(
                        hue = selectedColor.hue,
                        saturation = 0f,
                        value = selectedColor.value,
                    ).uiColor,
                    BrushColorState(
                        hue = selectedColor.hue,
                        saturation = 1f,
                        value = selectedColor.value,
                    ).uiColor,
                ),
            onValueChange = { trackValue ->
                onColorSelected(selectedColor.copy(saturation = trackValue))
            },
        )
        ColorTrackSlider(
            label = "明度",
            value = selectedColor.value,
            valueText = (selectedColor.value * 100f).roundToInt().toString(),
            knobColor = selectedColor.uiColor,
            gradientColors =
                listOf(
                    Color.Black,
                    BrushColorState(
                        hue = selectedColor.hue,
                        saturation = selectedColor.saturation,
                        value = 1f,
                    ).uiColor,
                ),
            onValueChange = { trackValue ->
                onColorSelected(selectedColor.copy(value = trackValue))
            },
        )
    }
}

@Composable
private fun ColorTrackSlider(
    label: String,
    value: Float,
    valueText: String,
    knobColor: Color,
    gradientColors: List<Color>,
    trackWidth: Dp = COLOR_TRACK_WIDTH_DP,
    onValueChange: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val trackHeightPx = with(density) { COLOR_TRACK_HEIGHT_DP.toPx() }
    val trackHorizontalPaddingPx = with(density) { COLOR_TRACK_HORIZONTAL_PADDING_DP.toPx() }
    val trackDrawWidthDp = trackWidth - COLOR_TRACK_HORIZONTAL_PADDING_DP * 2f
    val trackDrawWidthPx = with(density) { trackDrawWidthDp.toPx() }
    var isDragging by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            isDragging = false
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(COLOR_TRACK_LABEL_WIDTH_DP),
            style = PicoTheme.typography.bodyMedium,
            color = Color.White,
        )
        Text(
            text = valueText,
            modifier = Modifier.width(COLOR_TRACK_VALUE_WIDTH_DP),
            style = PicoTheme.typography.bodyMedium,
            color = Color.White,
        )

        Box(
            modifier =
                Modifier
                    .size(width = trackWidth, height = COLOR_TRACK_TOUCH_HEIGHT_DP)
                    .spatialHoverEffect {
                        val isActive = it.isActive
                        animation(tween(durationMillis = 160)) {
                            scale(if (isActive) 1.01f else 1f)
                        }
                        alpha(if (isActive) 1f else 0.98f)
                    }
                    .pointerInput(value) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: continue
                                val pressed = event.changes.any { it.pressed }
                                isDragging = pressed
                                if (pressed) {
                                    val normalized =
                                        ((change.position.x - trackHorizontalPaddingPx) / trackDrawWidthPx)
                                            .coerceIn(0f, 1f)
                                    onValueChange(normalized)
                                    event.changes.forEach { pointerChange ->
                                        if (pointerChange.pressed) {
                                            pointerChange.consume()
                                        }
                                    }
                                } else {
                                    isDragging = false
                                }
                            }
                        }
                    },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawColorTrack(
                    value = value,
                    trackHeightPx = trackHeightPx,
                    gradientColors = gradientColors,
                    trackHorizontalPaddingPx = trackHorizontalPaddingPx,
                )
            }
            ColorTrackKnob(
                value = value,
                color = knobColor,
                dragging = isDragging,
                trackWidth = trackWidth,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawColorTrack(
    value: Float,
    trackHeightPx: Float,
    gradientColors: List<Color>,
    trackHorizontalPaddingPx: Float,
) {
    val trackTop = (size.height - trackHeightPx) * 0.5f
    val trackLeft = trackHorizontalPaddingPx
    val trackWidth = size.width - trackHorizontalPaddingPx * 2f
    val trackCorner = CornerRadius(trackHeightPx * 0.5f)

    drawRoundRect(
        brush = Brush.horizontalGradient(gradientColors),
        topLeft = Offset(trackLeft, trackTop),
        size = Size(trackWidth, trackHeightPx),
        cornerRadius = trackCorner,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.16f),
        topLeft = Offset(trackLeft, trackTop),
        size = Size(trackWidth, trackHeightPx),
        cornerRadius = trackCorner,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
    )
}

@Composable
private fun ColorTrackKnob(
    value: Float,
    color: Color,
    dragging: Boolean,
    trackWidth: Dp = COLOR_TRACK_WIDTH_DP,
) {
    val knobDiameter = COLOR_TRACK_KNOB_DIAMETER_DP
    val knobHitSize = COLOR_TRACK_KNOB_HIT_SIZE_DP
    val knobCenterX =
        COLOR_TRACK_HORIZONTAL_PADDING_DP + (trackWidth - COLOR_TRACK_HORIZONTAL_PADDING_DP * 2f) * value.coerceIn(0f, 1f)
    val knobOffsetX = knobCenterX - knobHitSize / 2f
    val knobOffsetY = COLOR_TRACK_TOUCH_HEIGHT_DP / 2f - knobHitSize / 2f

    Box(
        modifier =
            Modifier
                .offset(x = knobOffsetX, y = knobOffsetY)
                .size(knobHitSize)
                .spatialHoverEffect {
                    val isActive = it.isActive || dragging
                    animation(tween(durationMillis = 160)) {
                        scale(if (isActive) COLOR_TRACK_KNOB_HOVER_SCALE else 1f)
                    }
                    alpha(1f)
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(knobDiameter)) {
            drawSelectionMarker(
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                color = color,
            )
        }
    }
}

@Composable
private fun BrushOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick) {
        Text(
            text = if (selected) "[$label]" else label,
            style = PicoTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MaterialPresetPicker(
    selectedMaterial: BrushMaterialPreset,
    previewColor: Color,
    onMaterialSelected: (BrushMaterialPreset) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BRUSH_MATERIAL_PRESETS.forEach { preset ->
            MaterialSphereButton(
                previewColor = previewColor,
                preset = preset,
                selected = preset == selectedMaterial,
                onClick = { onMaterialSelected(preset) },
            )
        }
    }
}

@Composable
private fun MaterialSphereButton(
    previewColor: Color,
    preset: BrushMaterialPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .spatialHoverEffect {
                    val isActive = it.isActive
                    animation(tween(durationMillis = 160)) {
                        scale(if (isActive) 1.08f else 1f)
                    }
                    alpha(1f)
                }
                .size(MATERIAL_SWATCH_SIZE_DP)
                .border(
                    width = if (selected) 3.dp else 1.5.dp,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.22f),
                    shape = CircleShape,
                )
                .background(Color.Transparent, CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMaterialSphere(
                preset = preset,
                previewColor = previewColor,
                selected = selected,
            )
        }
    }
}

@Composable
private fun ColorSourcePicker(
    selectedMode: BrushColorMode,
    textureSelection: TextureCropSelection?,
    selectedColor: BrushColorState,
    onColorModeSelected: (BrushColorMode) -> Unit,
    onTextureUploadRequest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrushOptionButton(
                label = "纯色",
                selected = selectedMode == BrushColorMode.PureColor,
                onClick = { onColorModeSelected(BrushColorMode.PureColor) },
            )
            BrushOptionButton(
                label = "纹理",
                selected = selectedMode == BrushColorMode.Texture,
                onClick = { onColorModeSelected(BrushColorMode.Texture) },
            )
        }

        if (selectedMode == BrushColorMode.Texture) {
            TextureCropEditor(
                selection = textureSelection,
                fallbackColor = selectedColor.uiColor,
                onTextureUploadRequest = onTextureUploadRequest,
            )
        }
    }
}

@Composable
private fun TextureCropEditor(
    selection: TextureCropSelection?,
    fallbackColor: Color,
    onTextureUploadRequest: () -> Unit,
) {
    if (selection == null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(TEXTURE_CROP_PREVIEW_SIZE_DP)
                        .border(1.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .background(fallbackColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "贴图", style = PicoTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.84f))
            }
            Column(
                modifier = Modifier.width(CONTROL_PANEL_WIDTH_DP - TEXTURE_CROP_PREVIEW_SIZE_DP - 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onTextureUploadRequest) {
                    Text(text = "上传纹理")
                }
                Text(
                    text = "上传后可在弹出面板中裁切",
                    style = PicoTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
        }
        return
    }

    val cropRect = selection.squareCropRect()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TextureCropPreview(
            selection = selection,
            cropRect = cropRect,
        )
        Column(
            modifier = Modifier.width(CONTROL_PANEL_WIDTH_DP - TEXTURE_CROP_PREVIEW_SIZE_DP - 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onTextureUploadRequest) {
                Text(text = "更换图片")
            }
            Text(
                text = "裁切请在上传面板中调整",
                style = PicoTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.76f),
            )
            Text(
                text = "${cropRect.size}px",
                style = PicoTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun TextureCropPreview(
    selection: TextureCropSelection,
    cropRect: SquareCropRect,
) {
    val imageBitmap = remember(selection.sourceBitmap) { selection.sourceBitmap.asImageBitmap() }
    Box(
        modifier =
            Modifier
                .size(TEXTURE_CROP_PREVIEW_SIZE_DP)
                .border(2.dp, Color.White.copy(alpha = 0.28f)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset(cropRect.left, cropRect.top),
                srcSize = IntSize(cropRect.size, cropRect.size),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )
            val third = size.width / 3f
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(third, 0f),
                end = Offset(third, size.height),
                strokeWidth = 1.5f,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(third * 2f, 0f),
                end = Offset(third * 2f, size.height),
                strokeWidth = 1.5f,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(0f, third),
                end = Offset(size.width, third),
                strokeWidth = 1.5f,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(0f, third * 2f),
                end = Offset(size.width, third * 2f),
                strokeWidth = 1.5f,
            )
        }
    }
}

@Composable
private fun BrushSizeControl(
    radius: Float,
    onRadiusChange: (Float) -> Unit,
) {
    val sliderValue = radiusToSliderValue(radius)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        ColorTrackSlider(
            label = "粗细",
            value = sliderValue,
            valueText = formatBrushRadiusLabel(radius),
            knobColor = Color.White,
            gradientColors =
                listOf(
                    Color.White.copy(alpha = 0.30f),
                    Color.White.copy(alpha = 0.92f),
                ),
            trackWidth = BRUSH_SIZE_TRACK_WIDTH_DP,
            onValueChange = { onRadiusChange(sliderValueToBrushRadius(it)) },
        )
    }
}

private fun radiusToSliderValue(radius: Float): Float {
    return ((radius - MIN_BRUSH_RADIUS_M) / (MAX_BRUSH_RADIUS_M - MIN_BRUSH_RADIUS_M)).coerceIn(0f, 1f)
}

private fun sliderValueToBrushRadius(value: Float): Float {
    return lerpFloat(MIN_BRUSH_RADIUS_M, MAX_BRUSH_RADIUS_M, value.coerceIn(0f, 1f))
}

private fun formatBrushRadiusLabel(radius: Float): String {
    return "${(radius * 1000f).roundToInt()}mm"
}

private fun mixColors(
    a: Color,
    b: Color,
    t: Float,
): Color {
    val clampedT = t.coerceIn(0f, 1f)
    return Color(
        red = lerpFloat(a.red, b.red, clampedT),
        green = lerpFloat(a.green, b.green, clampedT),
        blue = lerpFloat(a.blue, b.blue, clampedT),
        alpha = lerpFloat(a.alpha, b.alpha, clampedT),
    )
}

private fun lerpFloat(
    start: Float,
    end: Float,
    fraction: Float,
): Float = start + (end - start) * fraction.coerceIn(0f, 1f)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMaterialSphere(
    preset: BrushMaterialPreset,
    previewColor: Color,
    selected: Boolean,
) {
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val radius = size.minDimension * 0.37f
    val topLight = Offset(size.width * 0.34f, size.height * 0.27f)
    val bottomShade = Offset(size.width * 0.68f, size.height * 0.74f)
    val rimCenter = Offset(size.width * 0.54f, size.height * 0.45f)
    val baseTint =
        when (preset) {
            BrushMaterialPreset.Metal -> mixColors(previewColor, Color(0xFFC0C6D0), 0.24f)
            BrushMaterialPreset.Fabric -> mixColors(previewColor, Color(0xFF727884), 0.26f)
            BrushMaterialPreset.Plastic -> previewColor
            BrushMaterialPreset.Unlit -> previewColor
        }

    if (preset == BrushMaterialPreset.Unlit) {
        drawCircle(
            color = baseTint,
            radius = radius,
            center = center,
        )
        drawCircle(
            color = if (selected) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.22f),
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (selected) 3f else 2f),
        )
        return
    }

    drawCheckerSphereBackground(center = center, radius = radius * 1.02f)
    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    when (preset) {
                        BrushMaterialPreset.Metal ->
                            listOf(Color.White.copy(alpha = 0.96f), baseTint, Color(0xFF505766))
                        BrushMaterialPreset.Fabric ->
                            listOf(
                                mixColors(baseTint, Color.White, 0.12f),
                                baseTint,
                                mixColors(baseTint, Color.Black, 0.18f),
                            )
                        BrushMaterialPreset.Plastic ->
                            listOf(Color.White.copy(alpha = 0.84f), baseTint, mixColors(baseTint, Color.Black, 0.10f))
                        BrushMaterialPreset.Unlit ->
                            listOf(baseTint, baseTint, baseTint)
                    },
                center = topLight,
                radius = size.minDimension * 0.82f,
            ),
        radius = radius,
        center = center,
    )
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (preset == BrushMaterialPreset.Fabric) 0.14f else 0.22f)),
                center = bottomShade,
                radius = size.minDimension * 0.74f,
            ),
        radius = radius,
        center = center,
    )
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = if (preset == BrushMaterialPreset.Fabric) 0.10f else 0.22f), Color.Transparent),
                center = rimCenter,
                radius = radius,
            ),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = Color.White.copy(
            alpha =
                when (preset) {
                    BrushMaterialPreset.Metal -> 0.60f
                    BrushMaterialPreset.Fabric -> 0.10f
                    BrushMaterialPreset.Plastic -> 0.46f
                    BrushMaterialPreset.Unlit -> 0f
                },
        ),
        radius = radius * 0.26f,
        center = topLight,
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.22f),
        radius = radius,
        center = center + Offset(0f, radius * 0.10f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
    )
    drawCircle(
        color = if (selected) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.22f),
        radius = radius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (selected) 3f else 2f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerSphereBackground(
    center: Offset,
    radius: Float,
) {
    val cell = radius * 0.42f
    for (row in -2..2) {
        for (column in -2..2) {
            val left = center.x - radius + column * cell
            val top = center.y - radius + row * cell
            drawRect(
                color = if ((row + column) % 2 == 0) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f),
                topLeft = Offset(left, top),
                size = Size(cell, cell),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionMarker(
    center: Offset,
    color: Color,
    scale: Float = 1f,
) {
    val outerRadius = TRUE_COLOR_WHEEL_MARKER_OUTER_RADIUS_PX * scale
    val innerRadius = TRUE_COLOR_WHEEL_MARKER_INNER_RADIUS_PX * scale
    if (scale > 1.01f) {
        drawCircle(
            color = Color.White.copy(alpha = 0.34f),
            radius = outerRadius + TRUE_COLOR_WHEEL_MARKER_HOVER_HALO_PX,
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.82f),
            radius = outerRadius + TRUE_COLOR_WHEEL_MARKER_HOVER_RING_PX,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
        )
    }
    drawCircle(
        color = Color.Black.copy(alpha = 0.42f),
        radius = outerRadius + 2f * scale,
        center = center,
    )
    drawCircle(
        color = Color.White,
        radius = outerRadius,
        center = center,
    )
    drawCircle(
        color = color,
        radius = innerRadius,
        center = center,
    )
}
