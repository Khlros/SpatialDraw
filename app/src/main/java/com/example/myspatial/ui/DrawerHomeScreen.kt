package com.example.myspatial.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size as AndroidSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hand.HandJoint
import com.pico.spatial.tracking.hand.HandPose
import com.pico.spatial.tracking.hand.HandTrackingData
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo
import com.pico.spatial.ui.foundation.gesture.data.InteractionKind
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.hover.tween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun DrawerHomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val handTrackingProvider = remember { HandTrackingProvider() }
    val handTrackingData by
        handTrackingProvider.dataFlow.collectAsState(initial = HandTrackingData(null, null, 0L))
    val latestHandTrackingData = rememberUpdatedState(handTrackingData)
    val paintScene = remember { PaintSceneState() }
    val leftPanelState = remember { HandPanelState(HandSide.Left, LEFT_CONTROL_PANEL_POSITION) }
    val rightPanelState = remember { HandPanelState(HandSide.Right, RIGHT_CONTROL_PANEL_POSITION) }
    var leftPanelInteracting by remember { mutableStateOf(false) }
    var rightPanelInteracting by remember { mutableStateOf(false) }
    var leftSelectedColor by remember { mutableStateOf(DEFAULT_LEFT_BRUSH_COLOR) }
    var leftColorMode by remember { mutableStateOf(BrushColorMode.PureColor) }
    var leftTextureSelection by remember { mutableStateOf<TextureCropSelection?>(null) }
    var leftBrushRadius by remember { mutableStateOf(DEFAULT_BRUSH_RADIUS_M) }
    var leftSelectedMode by remember { mutableStateOf(BrushMode.Tube) }
    var leftSelectedMaterial by remember { mutableStateOf(BrushMaterialPreset.Metal) }
    var rightSelectedColor by remember { mutableStateOf(DEFAULT_RIGHT_BRUSH_COLOR) }
    var rightColorMode by remember { mutableStateOf(BrushColorMode.PureColor) }
    var rightTextureSelection by remember { mutableStateOf<TextureCropSelection?>(null) }
    var rightBrushRadius by remember { mutableStateOf(DEFAULT_BRUSH_RADIUS_M) }
    var rightSelectedMode by remember { mutableStateOf(BrushMode.Tube) }
    var rightSelectedMaterial by remember { mutableStateOf(BrushMaterialPreset.Metal) }
    var texturePickerTargetHand by remember { mutableStateOf<HandSide?>(null) }

    paintScene.setBrushConfig(
        handSide = HandSide.Left,
        config =
            BrushConfig(
                color = leftSelectedColor.toColor4(),
                displayColor = leftSelectedColor,
                colorMode = leftColorMode,
                textureSelection = leftTextureSelection,
                radius = leftBrushRadius,
                mode = leftSelectedMode,
                materialPreset = leftSelectedMaterial,
            ),
    )
    paintScene.setBrushPreviewStyle(
        handSide = HandSide.Left,
        style =
            BrushPreviewStyle(
                color = leftSelectedColor,
                colorMode = leftColorMode,
                textureSelection = leftTextureSelection,
                radius = leftBrushRadius,
                mode = leftSelectedMode,
                materialPreset = leftSelectedMaterial,
            ),
    )
    paintScene.setBrushConfig(
        handSide = HandSide.Right,
        config =
            BrushConfig(
                color = rightSelectedColor.toColor4(),
                displayColor = rightSelectedColor,
                colorMode = rightColorMode,
                textureSelection = rightTextureSelection,
                radius = rightBrushRadius,
                mode = rightSelectedMode,
                materialPreset = rightSelectedMaterial,
            ),
    )
    paintScene.setBrushPreviewStyle(
        handSide = HandSide.Right,
        style =
            BrushPreviewStyle(
                color = rightSelectedColor,
                colorMode = rightColorMode,
                textureSelection = rightTextureSelection,
                radius = rightBrushRadius,
                mode = rightSelectedMode,
                materialPreset = rightSelectedMaterial,
            ),
    )
    paintScene.setPanelInteractionActive(leftPanelInteracting || rightPanelInteracting || texturePickerTargetHand != null)

    DisposableEffect(handTrackingProvider) {
        handTrackingProvider.start()
        onDispose { handTrackingProvider.stop() }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0x10000000)),
    ) {
        SpatialView(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectSpatialPointerEvent(context = context) { eventList ->
                            paintScene.handlePointerEvents(
                                eventList = eventList,
                                handTrackingData = latestHandTrackingData.value,
                            )
                            false
                        }
                    },
            initial = { content, attachments ->
                paintScene.attach(content)
                attachments.entity(LEFT_CONTROL_PANEL_ATTACHMENT_ID)?.let { panel ->
                    leftPanelState.applyTransform(panel)
                    content.addEntity(panel)
                }
                attachments.entity(RIGHT_CONTROL_PANEL_ATTACHMENT_ID)?.let { panel ->
                    rightPanelState.applyTransform(panel)
                    content.addEntity(panel)
                }
                attachments.entity(TEXTURE_PICKER_ATTACHMENT_ID)?.let { panel ->
                    applyFixedPanelTransform(panel, TEXTURE_PICKER_PANEL_POSITION)
                    panel.enabled = false
                    content.addEntity(panel)
                }
            },
            attachments = {
                AttachmentPanel(
                    id = LEFT_CONTROL_PANEL_ATTACHMENT_ID,
                ) {
                    HandBrushPanel(
                        title = "左手画笔",
                        collapsed = leftPanelState.collapsed,
                        selectedColor = leftSelectedColor,
                        brushRadius = leftBrushRadius,
                        selectedMode = leftSelectedMode,
                        selectedMaterial = leftSelectedMaterial,
                        selectedColorMode = leftColorMode,
                        textureSelection = leftTextureSelection,
                        onColorSelected = { leftSelectedColor = it },
                        onColorModeSelected = { leftColorMode = it },
                        onTextureSelectionChanged = { leftTextureSelection = it },
                        onTextureUploadRequest = { texturePickerTargetHand = HandSide.Left },
                        onBrushRadiusChange = { leftBrushRadius = it },
                        onModeSelected = { leftSelectedMode = it },
                        onMaterialSelected = { leftSelectedMaterial = it },
                        onCollapseRequest = {
                            leftPanelInteracting = false
                            leftPanelState.collapse()
                        },
                        onInteractionChanged = { leftPanelInteracting = it },
                        onUndo = { paintScene.undoLastStroke(HandSide.Left) },
                        onClear = { paintScene.clear() },
                    )
                }
                AttachmentPanel(
                    id = RIGHT_CONTROL_PANEL_ATTACHMENT_ID,
                ) {
                    HandBrushPanel(
                        title = "右手画笔",
                        collapsed = rightPanelState.collapsed,
                        selectedColor = rightSelectedColor,
                        brushRadius = rightBrushRadius,
                        selectedMode = rightSelectedMode,
                        selectedMaterial = rightSelectedMaterial,
                        selectedColorMode = rightColorMode,
                        textureSelection = rightTextureSelection,
                        onColorSelected = { rightSelectedColor = it },
                        onColorModeSelected = { rightColorMode = it },
                        onTextureSelectionChanged = { rightTextureSelection = it },
                        onTextureUploadRequest = { texturePickerTargetHand = HandSide.Right },
                        onBrushRadiusChange = { rightBrushRadius = it },
                        onModeSelected = { rightSelectedMode = it },
                        onMaterialSelected = { rightSelectedMaterial = it },
                        onCollapseRequest = {
                            rightPanelInteracting = false
                            rightPanelState.collapse()
                        },
                        onInteractionChanged = { rightPanelInteracting = it },
                        onUndo = { paintScene.undoLastStroke(HandSide.Right) },
                        onClear = { paintScene.clear() },
                    )
                }
                AttachmentPanel(
                    id = TEXTURE_PICKER_ATTACHMENT_ID,
                ) {
                    TexturePickerAttachmentPanel(
                        handSide = texturePickerTargetHand,
                        onDismiss = { texturePickerTargetHand = null },
                        onTextureSelected = { targetHand, selection ->
                            when (targetHand) {
                                HandSide.Left -> {
                                    leftTextureSelection = selection
                                    leftColorMode = BrushColorMode.Texture
                                }
                                HandSide.Right -> {
                                    rightTextureSelection = selection
                                    rightColorMode = BrushColorMode.Texture
                                }
                            }
                            texturePickerTargetHand = null
                        },
                    )
                }
            },
            update = { _, attachments ->
                leftPanelState.update(
                    panel = attachments.entity(LEFT_CONTROL_PANEL_ATTACHMENT_ID),
                    handPose = handTrackingData.left,
                    worldToScene = { point -> paintScene.worldToSceneLocal(point) },
                )
                rightPanelState.update(
                    panel = attachments.entity(RIGHT_CONTROL_PANEL_ATTACHMENT_ID),
                    handPose = handTrackingData.right,
                    worldToScene = { point -> paintScene.worldToSceneLocal(point) },
                )
                attachments.entity(TEXTURE_PICKER_ATTACHMENT_ID)?.let { panel ->
                    panel.enabled = texturePickerTargetHand != null
                    if (panel.enabled) {
                        applyFixedPanelTransform(panel, TEXTURE_PICKER_PANEL_POSITION)
                    }
                }
                paintScene.update(
                    handTrackingData = handTrackingData,
                    allowInstantPinchDrawing = leftPanelState.collapsed && rightPanelState.collapsed,
                    leftPanel = attachments.entity(LEFT_CONTROL_PANEL_ATTACHMENT_ID),
                    leftPanelCollapsed = leftPanelState.collapsed,
                    rightPanel = attachments.entity(RIGHT_CONTROL_PANEL_ATTACHMENT_ID),
                    rightPanelCollapsed = rightPanelState.collapsed,
                )
            },
        )
    }
}

@Composable
private fun HandBrushPanel(
    title: String,
    collapsed: Boolean,
    selectedColor: BrushColorState,
    brushRadius: Float,
    selectedMode: BrushMode,
    selectedMaterial: BrushMaterialPreset,
    selectedColorMode: BrushColorMode,
    textureSelection: TextureCropSelection?,
    onColorSelected: (BrushColorState) -> Unit,
    onColorModeSelected: (BrushColorMode) -> Unit,
    onTextureSelectionChanged: (TextureCropSelection?) -> Unit,
    onTextureUploadRequest: () -> Unit,
    onBrushRadiusChange: (Float) -> Unit,
    onModeSelected: (BrushMode) -> Unit,
    onMaterialSelected: (BrushMaterialPreset) -> Unit,
    onCollapseRequest: () -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    DisposableEffect(Unit) {
        onDispose { onInteractionChanged(false) }
    }

    DisposableEffect(collapsed) {
        if (collapsed) {
            onInteractionChanged(false)
        }
        onDispose { }
    }

    val panelWidth by animateDpAsState(
        targetValue = if (collapsed) COLLAPSED_PANEL_SIZE_DP else CONTROL_PANEL_WIDTH_DP,
        animationSpec = composeTween(durationMillis = 220),
        label = "panel_width",
    )

    Box(modifier = Modifier.width(panelWidth)) {
        Crossfade(
            targetState = collapsed,
            animationSpec = composeTween(durationMillis = 220),
            label = "panel_mode",
        ) { isCollapsed ->
            if (isCollapsed) {
                CollapsedHandOrb(
                    title = title,
                    color = selectedColor.uiColor,
                )
            } else {
                ExpandedHandBrushPanel(
                    title = title,
                    selectedColor = selectedColor,
                    brushRadius = brushRadius,
                    selectedMode = selectedMode,
                    selectedMaterial = selectedMaterial,
                    selectedColorMode = selectedColorMode,
                    textureSelection = textureSelection,
                    onColorSelected = onColorSelected,
                    onColorModeSelected = onColorModeSelected,
                    onTextureSelectionChanged = onTextureSelectionChanged,
                    onTextureUploadRequest = onTextureUploadRequest,
                    onBrushRadiusChange = onBrushRadiusChange,
                    onModeSelected = onModeSelected,
                    onMaterialSelected = onMaterialSelected,
                    onCollapseRequest = onCollapseRequest,
                    onInteractionChanged = onInteractionChanged,
                    onUndo = onUndo,
                    onClear = onClear,
                )
            }
        }
    }
}

@Composable
private fun ExpandedHandBrushPanel(
    title: String,
    selectedColor: BrushColorState,
    brushRadius: Float,
    selectedMode: BrushMode,
    selectedMaterial: BrushMaterialPreset,
    selectedColorMode: BrushColorMode,
    textureSelection: TextureCropSelection?,
    onColorSelected: (BrushColorState) -> Unit,
    onColorModeSelected: (BrushColorMode) -> Unit,
    onTextureSelectionChanged: (TextureCropSelection?) -> Unit,
    onTextureUploadRequest: () -> Unit,
    onBrushRadiusChange: (Float) -> Unit,
    onModeSelected: (BrushMode) -> Unit,
    onMaterialSelected: (BrushMaterialPreset) -> Unit,
    onCollapseRequest: () -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .width(CONTROL_PANEL_WIDTH_DP)
                .background(Color(0xCC1B1D24))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down =
                            awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed }
                                ?: return@awaitEachGesture
                        onInteractionChanged(true)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) {
                                onInteractionChanged(false)
                                break
                            }
                        } while (true)
                    }
                }
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            modifier = Modifier.width(CONTROL_PANEL_WIDTH_DP - 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = PicoTheme.typography.titleLarge, color = Color.White)
            Button(onClick = onCollapseRequest) {
                Text(text = "收起")
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
                selected = selectedMode == BrushMode.Tube,
                onClick = { onModeSelected(BrushMode.Tube) },
            )
            BrushOptionButton(
                label = "圆球虚线",
                selected = selectedMode == BrushMode.SphereSteps,
                onClick = { onModeSelected(BrushMode.SphereSteps) },
            )
            BrushOptionButton(
                label = "圆球",
                selected = selectedMode == BrushMode.GrowingSphere,
                onClick = { onModeSelected(BrushMode.GrowingSphere) },
            )
            BrushOptionButton(
                label = "直线",
                selected = selectedMode == BrushMode.StraightLine,
                onClick = { onModeSelected(BrushMode.StraightLine) },
            )
        }

        Text(text = "示例", style = PicoTheme.typography.titleMedium, color = Color.White)
        Spacer(modifier = Modifier.height(BRUSH_PREVIEW_PANEL_CLEARANCE_DP))
        BrushSizeControl(
            radius = brushRadius,
            onRadiusChange = onBrushRadiusChange,
        )

        Text(text = "材质", style = PicoTheme.typography.titleMedium, color = Color.White)
        MaterialPresetPicker(
            selectedMaterial = selectedMaterial,
            previewColor = selectedColor.uiColor,
            onMaterialSelected = onMaterialSelected,
        )

        Text(text = "颜色", style = PicoTheme.typography.titleMedium, color = Color.White)
        ColorSourcePicker(
            selectedMode = selectedColorMode,
            textureSelection = textureSelection,
            selectedColor = selectedColor,
            onColorModeSelected = onColorModeSelected,
            onTextureSelectionChanged = onTextureSelectionChanged,
            onTextureUploadRequest = onTextureUploadRequest,
        )
        if (selectedColorMode == BrushColorMode.PureColor) {
            TripleColorTrackPalette(
                selectedColor = selectedColor,
                onColorSelected = onColorSelected,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onUndo) {
                Text(text = "撤销上一步")
            }
            onClear?.let { clearAction ->
                Button(onClick = clearAction) {
                    Text(text = "清除画布")
                }
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
                scale = 1f,
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    modifier: Modifier = Modifier,
    color: Color,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .spatialHoverEffect {
                    val isActive = it.isActive
                    animation(tween(durationMillis = 160)) {
                        scale(if (isActive) 1.16f else 1f)
                    }
                    alpha(if (isActive || selected) 1f else 0.94f)
                }
                .size(if (selected) 34.dp else 28.dp)
                .border(
                    width = if (selected) 3.dp else 1.5.dp,
                    color = if (selected) Color.White else Color(0x88FFFFFF),
                    shape = shape,
                )
                .background(color, shape)
                .clickable(onClick = onClick),
    )
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
    onTextureSelectionChanged: (TextureCropSelection?) -> Unit,
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
            if (selectedMode == BrushColorMode.Texture) {
                Button(onClick = onTextureUploadRequest) {
                    Text(text = if (textureSelection == null) "上传纹理" else "更换图片")
                }
            }
        }

        if (selectedMode == BrushColorMode.Texture) {
            TextureCropEditor(
                selection = textureSelection,
                fallbackColor = selectedColor.uiColor,
                onTextureSelectionChanged = onTextureSelectionChanged,
            )
        }
    }
}

@Composable
private fun TextureCropEditor(
    selection: TextureCropSelection?,
    fallbackColor: Color,
    onTextureSelectionChanged: (TextureCropSelection?) -> Unit,
) {
    if (selection == null) {
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
private fun TexturePickerAttachmentPanel(
    handSide: HandSide?,
    onDismiss: () -> Unit,
    onTextureSelected: (HandSide, TextureCropSelection) -> Unit,
) {
    if (handSide == null) {
        Spacer(modifier = Modifier.size(1.dp))
        return
    }

    val context = LocalContext.current
    val permission = remember { textureReadPermission() }
    var permissionGranted by remember { mutableStateOf(context.hasPermission(permission)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var draftSelection by remember(handSide) { mutableStateOf<TextureCropSelection?>(null) }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            permissionGranted = granted
            permissionDenied = !granted
        }

    DisposableEffect(handSide) {
        if (!permissionGranted) {
            permissionLauncher.launch(permission)
        }
        onDispose { }
    }

    val imageItems by produceState<List<TextureImageItem>?>(initialValue = null, permissionGranted) {
        value =
            if (permissionGranted) {
                withContext(Dispatchers.IO) { queryRecentImages(context) }
            } else {
                emptyList()
            }
    }

    Column(
        modifier =
            Modifier
                .width(TEXTURE_PICKER_PANEL_WIDTH_DP)
                .height(TEXTURE_PICKER_PANEL_HEIGHT_DP)
                .background(Color(0xFF12141A), RoundedCornerShape(24.dp))
                .border(1.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    when {
                        draftSelection != null && handSide == HandSide.Left -> "左手纹理裁切"
                        draftSelection != null && handSide == HandSide.Right -> "右手纹理裁切"
                        handSide == HandSide.Left -> "左手纹理图片"
                        else -> "右手纹理图片"
                    },
                style = PicoTheme.typography.titleLarge,
                color = Color.White,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draftSelection != null) {
                    Button(onClick = { draftSelection = null }) {
                        Text(text = "返回")
                    }
                }
                Button(onClick = onDismiss) {
                    Text(text = "关闭")
                }
            }
        }

        when {
            !permissionGranted && permissionDenied -> {
                TextureOverlayState(
                    text = "需要读取照片权限",
                    actionText = "重新授权",
                    onAction = {
                        permissionDenied = false
                        permissionLauncher.launch(permission)
                    },
                )
            }
            !permissionGranted -> {
                TextureOverlayState(
                    text = "正在请求照片权限",
                    actionText = "关闭",
                    onAction = onDismiss,
                )
            }
            imageItems == null -> {
                TextureOverlayState(
                    text = "正在加载照片",
                    actionText = "关闭",
                    onAction = onDismiss,
                )
            }
            imageItems.isNullOrEmpty() -> {
                TextureOverlayState(
                    text = "没有读取到照片",
                    actionText = "关闭",
                    onAction = onDismiss,
                )
            }
            draftSelection != null -> {
                TextureCropAttachmentEditor(
                    selection = draftSelection!!,
                    onSelectionChanged = { draftSelection = it },
                    onConfirm = {
                        onTextureSelected(handSide, draftSelection!!)
                        draftSelection = null
                    },
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = imageItems.orEmpty(),
                        key = { it.uri.toString() },
                    ) { item ->
                        TextureImageCard(
                            item = item,
                            onClick = {
                                val bitmap = loadTextureBitmap(context, item.uri) ?: return@TextureImageCard
                                draftSelection = TextureCropSelection.createDefault(bitmap)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextureCropAttachmentEditor(
    selection: TextureCropSelection,
    onSelectionChanged: (TextureCropSelection) -> Unit,
    onConfirm: () -> Unit,
) {
    val cropRect = selection.squareCropRect()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextureInteractiveCropPreview(
            selection = selection,
            onSelectionChanged = onSelectionChanged,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "拖动左上和右下角调整裁切",
                    style = PicoTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Text(
                    text = "当前 ${cropRect.size}px，最小 ${selection.sourceBitmap.minCropSizePx()}px",
                    style = PicoTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.62f),
                )
            }
            Button(onClick = onConfirm) {
                Text(text = "使用裁切")
            }
        }
    }
}

@Composable
private fun TextureInteractiveCropPreview(
    selection: TextureCropSelection,
    onSelectionChanged: (TextureCropSelection) -> Unit,
) {
    val imageBitmap = remember(selection.sourceBitmap) { selection.sourceBitmap.asImageBitmap() }
    val cropRect = selection.squareCropRect()
    val density = LocalDensity.current
    Box(
        modifier =
            Modifier
                .size(TEXTURE_CROP_EDITOR_PREVIEW_SIZE_DP)
                .border(2.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val metrics = calculateTexturePreviewMetrics(size, selection.sourceBitmap)
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(metrics.imageLeftPx.roundToInt(), metrics.imageTopPx.roundToInt()),
                dstSize = IntSize(metrics.imageWidthPx.roundToInt(), metrics.imageHeightPx.roundToInt()),
            )

            val cropLeft = metrics.imageLeftPx + cropRect.left * metrics.scale
            val cropTop = metrics.imageTopPx + cropRect.top * metrics.scale
            val cropSize = cropRect.size * metrics.scale
            val cropRight = cropLeft + cropSize
            val cropBottom = cropTop + cropSize

            drawRect(Color.Black.copy(alpha = 0.48f), topLeft = Offset.Zero, size = Size(size.width, cropTop))
            drawRect(
                Color.Black.copy(alpha = 0.48f),
                topLeft = Offset.Zero,
                size = Size(cropLeft, size.height),
            )
            drawRect(
                Color.Black.copy(alpha = 0.48f),
                topLeft = Offset(cropRight, 0f),
                size = Size(size.width - cropRight, size.height),
            )
            drawRect(
                Color.Black.copy(alpha = 0.48f),
                topLeft = Offset(0f, cropBottom),
                size = Size(size.width, size.height - cropBottom),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropSize, cropSize),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
            )
        }

        val metrics = remember(selection.sourceBitmap) {
            TexturePreviewMetrics.fromBitmap(selection.sourceBitmap, with(density) { TEXTURE_CROP_EDITOR_PREVIEW_SIZE_DP.toPx() })
        }
        val cropLeftPx = (metrics.imageLeftPx + cropRect.left * metrics.scale).roundToInt()
        val cropTopPx = (metrics.imageTopPx + cropRect.top * metrics.scale).roundToInt()
        val cropSizePx = (cropRect.size * metrics.scale).roundToInt()
        TextureCropMoveRegion(
            leftPx = cropLeftPx,
            topPx = cropTopPx,
            sizePx = cropSizePx,
            onDrag = { dragX, dragY ->
                onSelectionChanged(selection.dragWholeCrop(dragX / metrics.scale, dragY / metrics.scale))
            },
        )
        TextureCropHandle(
            centerX = cropLeftPx,
            centerY = cropTopPx,
            onDrag = { dragX, dragY ->
                onSelectionChanged(selection.dragTopLeft(dragX / metrics.scale, dragY / metrics.scale))
            },
        )
        TextureCropHandle(
            centerX = cropLeftPx + cropSizePx,
            centerY = cropTopPx + cropSizePx,
            onDrag = { dragX, dragY ->
                onSelectionChanged(selection.dragBottomRight(dragX / metrics.scale, dragY / metrics.scale))
            },
        )
    }
}

@Composable
private fun TextureCropMoveRegion(
    leftPx: Int,
    topPx: Int,
    sizePx: Int,
    onDrag: (Float, Float) -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .offset { IntOffset(leftPx, topPx) }
                .size(with(LocalDensity.current) { sizePx.toDp() })
                .spatialHoverEffect {
                    val isActive = it.isActive
                    animation(tween(durationMillis = 120)) {
                        scale(if (isActive || isDragging) 1.01f else 1f)
                    }
                    alpha(1f)
                }
                .pointerInput(leftPx, topPx, sizePx) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            val pressed = event.changes.any { it.pressed }
                            isDragging = pressed
                            if (pressed) {
                                val delta = change.position - change.previousPosition
                                if (delta != Offset.Zero) {
                                    onDrag(delta.x, delta.y)
                                }
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
    )
}

@Composable
private fun TextureCropHandle(
    centerX: Int,
    centerY: Int,
    onDrag: (Float, Float) -> Unit,
) {
    val hitSizePx = with(LocalDensity.current) { TEXTURE_CROP_HANDLE_HIT_SIZE_DP.roundToPx() }
    var isDragging by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .offset {
                    IntOffset(
                        x = centerX - hitSizePx / 2,
                        y = centerY - hitSizePx / 2,
                    )
                }
                .size(TEXTURE_CROP_HANDLE_HIT_SIZE_DP)
                .spatialHoverEffect {
                    val isActive = it.isActive
                    animation(tween(durationMillis = 140)) {
                        scale(if (isActive || isDragging) TEXTURE_CROP_HANDLE_HOVER_SCALE else 1f)
                    }
                    alpha(1f)
                }
                .pointerInput(centerX, centerY) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            val pressed = event.changes.any { it.pressed }
                            isDragging = pressed
                            if (pressed) {
                                val delta = change.position - change.previousPosition
                                if (delta != Offset.Zero) {
                                    onDrag(delta.x, delta.y)
                                }
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
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(TEXTURE_CROP_HANDLE_SIZE_DP)
                    .background(Color.White, CircleShape)
                    .border(2.dp, Color.Black.copy(alpha = 0.24f), CircleShape),
        )
    }
}

private data class TexturePreviewMetrics(
    val imageLeftPx: Float,
    val imageTopPx: Float,
    val imageWidthPx: Float,
    val imageHeightPx: Float,
    val scale: Float,
) {
    companion object {
        fun fromBitmap(
            bitmap: Bitmap,
            previewSizePx: Float,
        ): TexturePreviewMetrics {
            val width = bitmap.width.toFloat().coerceAtLeast(1f)
            val height = bitmap.height.toFloat().coerceAtLeast(1f)
            val scale = minOf(previewSizePx / width, previewSizePx / height)
            val imageWidth = width * scale
            val imageHeight = height * scale
            return TexturePreviewMetrics(
                imageLeftPx = (previewSizePx - imageWidth) * 0.5f,
                imageTopPx = (previewSizePx - imageHeight) * 0.5f,
                imageWidthPx = imageWidth,
                imageHeightPx = imageHeight,
                scale = scale,
            )
        }
    }
}

private fun calculateTexturePreviewMetrics(
    previewSize: Size,
    bitmap: Bitmap,
): TexturePreviewMetrics {
    val side = minOf(previewSize.width, previewSize.height)
    return TexturePreviewMetrics.fromBitmap(bitmap, side)
}

private fun TextureCropSelection.dragTopLeft(
    deltaX: Float,
    deltaY: Float,
): TextureCropSelection {
    val current = squareCropRect()
    val anchorRight = current.left + current.size
    val anchorBottom = current.top + current.size
    val candidateLeft = current.left + deltaX.roundToInt()
    val candidateTop = current.top + deltaY.roundToInt()
    val maxSize = minOf(anchorRight, anchorBottom)
    val newSize =
        minOf(anchorRight - candidateLeft, anchorBottom - candidateTop)
            .coerceIn(sourceBitmap.minCropSizePx(), maxSize.coerceAtLeast(sourceBitmap.minCropSizePx()))
    return withCropRect(
        left = anchorRight - newSize,
        top = anchorBottom - newSize,
        size = newSize,
    )
}

private fun TextureCropSelection.dragBottomRight(
    deltaX: Float,
    deltaY: Float,
): TextureCropSelection {
    val current = squareCropRect()
    val candidateSize =
        current.size + minOf(deltaX.roundToInt(), deltaY.roundToInt())
    val maxSize = minOf(sourceBitmap.width - current.left, sourceBitmap.height - current.top)
    return withCropRect(
        left = current.left,
        top = current.top,
        size = candidateSize.coerceIn(sourceBitmap.minCropSizePx(), maxSize.coerceAtLeast(sourceBitmap.minCropSizePx())),
    )
}

private fun TextureCropSelection.dragWholeCrop(
    deltaX: Float,
    deltaY: Float,
): TextureCropSelection {
    val current = squareCropRect()
    return withCropRect(
        left = current.left + deltaX.roundToInt(),
        top = current.top + deltaY.roundToInt(),
        size = current.size,
    )
}

@Composable
private fun TextureOverlayState(
    text: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = text, color = Color.White.copy(alpha = 0.82f))
            Button(onClick = onAction) {
                Text(text = actionText)
            }
        }
    }
}

@Composable
private fun TextureImageCard(
    item: TextureImageItem,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onClick)
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (item.thumbnail != null) {
            androidx.compose.foundation.Image(
                bitmap = item.thumbnail,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .size(132.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .size(132.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "图片", color = Color.White.copy(alpha = 0.75f))
            }
        }
        Text(
            text = item.label,
            color = Color.White,
            style = PicoTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

private data class TextureImageItem(
    val uri: Uri,
    val label: String,
    val thumbnail: androidx.compose.ui.graphics.ImageBitmap?,
)

private fun textureReadPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun queryRecentImages(context: Context): List<TextureImageItem> {
    val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    val projection =
        arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
        )

    val items = mutableListOf<TextureImageItem>()
    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        while (cursor.moveToNext() && items.size < 80) {
            val id = cursor.getLong(idIndex)
            val name = cursor.getString(nameIndex) ?: "未命名图片"
            val uri = ContentUris.withAppendedId(collection, id)
            val thumbnail =
                runCatching {
                    context.contentResolver.loadThumbnail(uri, AndroidSize(320, 320), null).asImageBitmap()
                }.getOrNull()
            items += TextureImageItem(uri = uri, label = name, thumbnail = thumbnail)
        }
    }
    return items
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
    return lerp(MIN_BRUSH_RADIUS_M, MAX_BRUSH_RADIUS_M, value.coerceIn(0f, 1f))
}

private fun formatBrushRadiusLabel(radius: Float): String {
    return "${(radius * 1000f).roundToInt()}mm"
}

private class PaintSceneState {
    private val sceneRoot = Entity().apply { setName("PaintSceneRoot") }
    private val leftInput = HandDrawInputState()
    private val rightInput = HandDrawInputState()
    private val activeUiPointers = mutableSetOf<PointerId>()
    private val leftStroke =
        DynamicBrushHand(
            handName = "Left",
            cursorColor = Color4.fromLinearHex("0x82b0ffff"),
        )
    private val rightStroke =
        DynamicBrushHand(
            handName = "Right",
            cursorColor = Color4.fromLinearHex("0xffd166ff"),
        )
    private var attached = false
    private var panelInteractionActive = false
    private var uiBlockUntilUptimeMillis = 0L
    private var leftUndoGestureLatched = false
    private var rightUndoGestureLatched = false
    private val leftPreview = BrushPreview3D("Left")
    private val rightPreview = BrushPreview3D("Right")

    fun attach(content: SpatialViewContent) {
        if (attached) return
        leftStroke.attach(sceneRoot)
        rightStroke.attach(sceneRoot)
        content.addEntity(sceneRoot)
        attached = true
    }

    fun handlePointerEvents(
        eventList: List<SpatialPointerInfo>,
        handTrackingData: HandTrackingData,
    ) {
        eventList.forEach { event ->
            if (event.targetedEntity == null) return@forEach
            trackUiPointer(event)

            when (resolveHandSide(event, handTrackingData)) {
                HandSide.Left -> leftInput.onUiPointerEvent(event)
                HandSide.Right -> rightInput.onUiPointerEvent(event)
                null -> {
                    leftInput.onUiPointerEvent(event)
                    rightInput.onUiPointerEvent(event)
                }
            }
        }
    }

    fun update(
        handTrackingData: HandTrackingData,
        allowInstantPinchDrawing: Boolean,
        leftPanel: Entity?,
        leftPanelCollapsed: Boolean,
        rightPanel: Entity?,
        rightPanelCollapsed: Boolean,
    ) {
        val now = SystemClock.uptimeMillis()
        val uiInteractionActive = panelInteractionActive || activeUiPointers.isNotEmpty() || now < uiBlockUntilUptimeMillis

        if (handTrackingData.left == null) {
            leftInput.reset()
        }
        if (handTrackingData.right == null) {
            rightInput.reset()
        }

        leftInput.updateLongPinchState(
            handPose = handTrackingData.left,
            nowUptimeMillis = now,
            uiInteractionActive = uiInteractionActive,
            allowInstantPinchDrawing = allowInstantPinchDrawing,
        )
        rightInput.updateLongPinchState(
            handPose = handTrackingData.right,
            nowUptimeMillis = now,
            uiInteractionActive = uiInteractionActive,
            allowInstantPinchDrawing = allowInstantPinchDrawing,
        )
        leftUndoGestureLatched =
            updateUndoGesture(
                handPose = handTrackingData.left,
                inputState = leftInput,
                currentLatch = leftUndoGestureLatched,
                onUndo = { leftStroke.undoLastStroke() },
            )
        rightUndoGestureLatched =
            updateUndoGesture(
                handPose = handTrackingData.right,
                inputState = rightInput,
                currentLatch = rightUndoGestureLatched,
                onUndo = { rightStroke.undoLastStroke() },
            )

        leftPreview.updateAnchor(panel = leftPanel, collapsed = leftPanelCollapsed)
        rightPreview.updateAnchor(panel = rightPanel, collapsed = rightPanelCollapsed)
        leftStroke.update(handTrackingData.left, sceneRoot, leftInput.shouldDraw)
        rightStroke.update(handTrackingData.right, sceneRoot, rightInput.shouldDraw)
    }

    fun worldToSceneLocal(point: Vector3): Vector3 = sceneRoot.convertPositionFrom(point, null)

    fun setBrushConfig(
        handSide: HandSide,
        config: BrushConfig,
    ) {
        when (handSide) {
            HandSide.Left -> leftStroke.setBrushConfig(config)
            HandSide.Right -> rightStroke.setBrushConfig(config)
        }
    }

    fun setBrushPreviewStyle(
        handSide: HandSide,
        style: BrushPreviewStyle,
    ) {
        when (handSide) {
            HandSide.Left -> leftPreview.setStyle(style)
            HandSide.Right -> rightPreview.setStyle(style)
        }
    }

    fun clear() {
        leftStroke.clear()
        rightStroke.clear()
    }

    fun undoLastStroke(handSide: HandSide) {
        when (handSide) {
            HandSide.Left -> leftStroke.undoLastStroke()
            HandSide.Right -> rightStroke.undoLastStroke()
        }
    }

    fun setPanelInteractionActive(active: Boolean) {
        panelInteractionActive = active
        if (active) {
            uiBlockUntilUptimeMillis = SystemClock.uptimeMillis() + UI_POINTER_DRAW_BLOCK_HOLD_MS
            leftInput.cancelDrawingForUi()
            rightInput.cancelDrawingForUi()
        }
    }

    private fun trackUiPointer(event: SpatialPointerInfo) {
        uiBlockUntilUptimeMillis = SystemClock.uptimeMillis() + UI_POINTER_DRAW_BLOCK_HOLD_MS
        when {
            event.isUpEvent() || !event.pressed -> activeUiPointers.remove(event.pointerId)
            else -> activeUiPointers.add(event.pointerId)
        }
    }

    private fun updateUndoGesture(
        handPose: HandPose?,
        inputState: HandDrawInputState,
        currentLatch: Boolean,
        onUndo: () -> Unit,
    ): Boolean {
        val undoGestureActive =
            handPose?.thumbMiddleDistance()?.let { it <= UNDO_GESTURE_DISTANCE_M } == true
        if (undoGestureActive && !currentLatch && !inputState.shouldDraw) {
            onUndo()
        }
        return undoGestureActive
    }
}

private data class BrushPreviewStyle(
    val color: BrushColorState,
    val colorMode: BrushColorMode,
    val textureSelection: TextureCropSelection?,
    val radius: Float,
    val mode: BrushMode,
    val materialPreset: BrushMaterialPreset,
)

private class BrushPreview3D(
    private val handName: String,
) {
    private val previewRoot = Entity().apply { setName("${handName}BrushPreviewRoot") }
    private var parentPanel: Entity? = null
    private var tubeResource: MeshResource? = null
    private var tubeEntity: ModelEntity? = null
    private var sphereMesh: MeshResource? = null
    private val sphereEntities = mutableListOf<ModelEntity>()
    private var currentStyle: BrushPreviewStyle? = null

    fun setStyle(style: BrushPreviewStyle) {
        if (style == currentStyle) return
        currentStyle = style
        rebuild()
    }

    fun updateAnchor(
        panel: Entity?,
        collapsed: Boolean,
    ) {
        if (panel == null) {
            previewRoot.enabled = false
            return
        }
        if (panel !== parentPanel) {
            parentPanel?.removeChild(previewRoot)
            panel.addChild(previewRoot)
            parentPanel = panel
        }
        previewRoot.enabled = !collapsed
        previewRoot.components.set(
            TransformComponent()
                .setPosition(BRUSH_PREVIEW_LOCAL_POSITION)
                .setScaleVector(Vector3(BRUSH_PREVIEW_LOCAL_SCALE)),
        )
    }

    private fun rebuild() {
        clearGeometry()
        val style = currentStyle ?: return
        when (style.mode) {
            BrushMode.Tube -> rebuildTube(style)
            BrushMode.SphereSteps -> rebuildSphereSteps(style)
            BrushMode.GrowingSphere -> rebuildGrowingSphere(style)
            BrushMode.StraightLine -> rebuildStraightLine(style)
        }
    }

    private fun rebuildTube(style: BrushPreviewStyle) {
        val profile =
            buildStrokeProfile(
                points = BRUSH_PREVIEW_POINTS,
                startCap = true,
                endCap = true,
            )
        val rings = buildTubeRingsFromProfile(profile)
        val meshData = buildTubeMeshDataFromRings(rings, style.radius)
        val resource = MeshResource.createWithMeshModel(meshData.model, meshData.bounds, "${handName}_brush_preview")
        val entity =
            ModelEntity(
                resource,
                createBrushMaterial(
                    color = style.color,
                    colorMode = style.colorMode,
                    textureSelection = style.textureSelection,
                    materialPreset = style.materialPreset,
                ),
            ).apply {
                setName("${handName}BrushPreviewTube")
            }
        previewRoot.addChild(entity)
        tubeResource = resource
        tubeEntity = entity
    }

    private fun rebuildSphereSteps(style: BrushPreviewStyle) {
        val resource = MeshResource.createSphere(style.radius)
        val material =
            createBrushMaterial(
                color = style.color,
                colorMode = style.colorMode,
                textureSelection = style.textureSelection,
                materialPreset = style.materialPreset,
            )
        samplePointsForSpherePreview(
            pathPoints = BRUSH_PREVIEW_POINTS,
            spacing = SPHERE_STEP_DISTANCE_M,
        ).forEachIndexed { index, point ->
            val sphere =
                ModelEntity(resource, material).apply {
                    setName("${handName}BrushPreviewSphere_$index")
                    components.set(
                        TransformComponent().setPosition(point),
                    )
                }
            previewRoot.addChild(sphere)
            sphereEntities.add(sphere)
        }
        sphereMesh = resource
    }

    private fun rebuildGrowingSphere(style: BrushPreviewStyle) {
        val previewRadius =
            maxOf(
                style.radius * GROWING_SPHERE_PREVIEW_SCALE,
                BRUSH_PREVIEW_MIN_GROWING_SPHERE_RADIUS_M,
            ).coerceAtMost(MAX_GROWING_SPHERE_RADIUS_M)
        val resource = MeshResource.createSphere(previewRadius)
        val entity =
            ModelEntity(
                resource,
                createBrushMaterial(
                    color = style.color,
                    colorMode = style.colorMode,
                    textureSelection = style.textureSelection,
                    materialPreset = style.materialPreset,
                ),
            ).apply {
                setName("${handName}BrushPreviewGrowingSphere")
                components.set(
                    TransformComponent().setPosition(Vector3.ZERO),
                )
            }
        previewRoot.addChild(entity)
        sphereMesh = resource
        sphereEntities.add(entity)
    }

    private fun rebuildStraightLine(style: BrushPreviewStyle) {
        val profile =
            buildUniformCapsuleProfile(
                points = BRUSH_PREVIEW_LINE_POINTS,
                radius = style.radius,
            )
        val rings = buildTubeRingsFromProfile(profile)
        val meshData = buildTubeMeshDataFromRings(rings, style.radius)
        val resource = MeshResource.createWithMeshModel(meshData.model, meshData.bounds, "${handName}_brush_preview_line")
        val entity =
            ModelEntity(
                resource,
                createBrushMaterial(
                    color = style.color,
                    colorMode = style.colorMode,
                    textureSelection = style.textureSelection,
                    materialPreset = style.materialPreset,
                ),
            ).apply {
                setName("${handName}BrushPreviewStraightLine")
            }
        previewRoot.addChild(entity)
        tubeResource = resource
        tubeEntity = entity
    }

    private fun clearGeometry() {
        tubeEntity?.let { previewRoot.removeChild(it) }
        tubeEntity = null
        tubeResource?.close()
        tubeResource = null
        sphereEntities.forEach { previewRoot.removeChild(it) }
        sphereEntities.clear()
        sphereMesh?.close()
        sphereMesh = null
    }
}

private fun samplePointsForSpherePreview(
    pathPoints: List<Vector3>,
    spacing: Float,
): List<Vector3> {
    if (pathPoints.isEmpty()) return emptyList()
    val sampled = mutableListOf(pathPoints.first())
    var lastPlacedPoint = pathPoints.first()
    var distanceSinceLastPlacement = 0f

    pathPoints.zipWithNext().forEach { (start, end) ->
        var fromPoint = start
        var remainingSegmentLength = Vector3.distance(start, end)
        while (true) {
            val distanceToNextPlacement = spacing - distanceSinceLastPlacement
            if (remainingSegmentLength < distanceToNextPlacement) {
                break
            }
            val segment = end - fromPoint
            val segmentLength = segment.length()
            if (segmentLength < EPSILON_M) {
                break
            }
            val nextPoint = fromPoint + segment / segmentLength * distanceToNextPlacement
            if (Vector3.distance(lastPlacedPoint, nextPoint) > EPSILON_M) {
                sampled.add(nextPoint)
                lastPlacedPoint = nextPoint
            }
            fromPoint = nextPoint
            remainingSegmentLength -= distanceToNextPlacement
            distanceSinceLastPlacement = 0f
        }
        distanceSinceLastPlacement += remainingSegmentLength
    }

    if (Vector3.distance(sampled.last(), pathPoints.last()) > EPSILON_M) {
        sampled.add(pathPoints.last())
    }
    return sampled
}

private class DynamicBrushHand(
    handName: String,
    cursorColor: Color4,
) {
    private val strokesRoot = Entity().apply { setName("${handName}StrokeRoot") }
    private val cursorMaterial =
        UnlitMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(cursorColor)
        }
    private val cursorEntity =
        createSphereEntity(
            name = "${handName}BrushCursor",
            material = cursorMaterial,
            radius = 0.012f,
            scale = 1f,
        )
    private var brushConfig =
        BrushConfig(
            color = DEFAULT_RIGHT_BRUSH_COLOR.toColor4(),
            displayColor = DEFAULT_RIGHT_BRUSH_COLOR,
            colorMode = BrushColorMode.PureColor,
            textureSelection = null,
            radius = DEFAULT_BRUSH_RADIUS_M,
            mode = BrushMode.Tube,
            materialPreset = BrushMaterialPreset.Metal,
        )

    private var drawActive = false
    private var activeStroke: BrushStroke? = null
    private val finishedStrokes = mutableListOf<BrushStroke>()
    private var strokeIndex = 0

    fun attach(parent: Entity) {
        parent.addChild(strokesRoot)
        parent.addChild(cursorEntity)
        cursorEntity.enabled = false
        setBrushConfig(brushConfig)
    }

    fun setBrushConfig(config: BrushConfig) {
        brushConfig = config
        cursorMaterial.setBaseColor(config.color)
        cursorEntity.components[TransformComponent::class.java]?.setScaleVector(
            Vector3((config.radius / CURSOR_BASE_RADIUS_M).coerceAtLeast(0.5f)),
        )
    }

    fun update(
        handPose: HandPose?,
        root: Entity,
        shouldDraw: Boolean,
    ) {
        if (handPose == null) {
            endStroke()
            cursorEntity.enabled = false
            return
        }

        val indexTip = handPose[HandJoint.Index.INDEX_TIP]
        val thumbTip = handPose[HandJoint.Index.THUMB_TIP]
        val worldDrawPoint = (indexTip.position + thumbTip.position) / 2f
        val localDrawPoint = root.convertPositionFrom(worldDrawPoint, null)

        cursorEntity.enabled = true
        cursorEntity.components[TransformComponent::class.java]?.setPosition(localDrawPoint)

        if (!shouldDraw) {
            endStroke()
            return
        }

        if (!drawActive) {
            drawActive = true
            beginStroke(localDrawPoint)
            return
        }

        val stroke = activeStroke ?: beginStroke(localDrawPoint)
        val previousPoint = stroke.lastPoint ?: localDrawPoint
        val distance = Vector3.distance(previousPoint, localDrawPoint)
        if (distance > MAX_SEGMENT_JUMP_M) {
            finishAndStore(stroke)
            beginStroke(localDrawPoint)
            return
        }
        if (distance < MIN_POINT_SPACING_M && !stroke.requiresContinuousUpdates) {
            return
        }
        if (stroke.vertexCount >= MAX_STROKE_VERTICES) {
            finishAndStore(stroke)
            beginStroke(localDrawPoint)
            return
        }
        stroke.appendPoint(localDrawPoint)
    }

    fun clear() {
        activeStroke?.dispose()
        activeStroke = null
        finishedStrokes.forEach { it.dispose() }
        finishedStrokes.clear()
        strokesRoot.removeAllChildren()
        drawActive = false
    }

    fun undoLastStroke() {
        if (activeStroke != null) {
            activeStroke?.dispose()
            activeStroke = null
            drawActive = false
            return
        }

        finishedStrokes.removeLastOrNull()?.dispose()
    }

    private fun beginStroke(startPoint: Vector3): BrushStroke {
        val strokeName = "${strokesRoot.getName()}_${strokeIndex++}"
        val stroke =
            when (brushConfig.mode) {
                BrushMode.Tube ->
                    DynamicTubeStroke(
                        strokeName = strokeName,
                        material =
                            createBrushMaterial(
                                color = brushConfig.displayColor,
                                colorMode = brushConfig.colorMode,
                                textureSelection = brushConfig.textureSelection,
                                materialPreset = brushConfig.materialPreset,
                            ),
                        parent = strokesRoot,
                        radius = brushConfig.radius,
                    )
                BrushMode.SphereSteps ->
                    SphereStepStroke(
                        strokeName = strokeName,
                        material =
                            createBrushMaterial(
                                color = brushConfig.displayColor,
                                colorMode = brushConfig.colorMode,
                                textureSelection = brushConfig.textureSelection,
                                materialPreset = brushConfig.materialPreset,
                            ),
                        parent = strokesRoot,
                        radius = brushConfig.radius,
                    )
                BrushMode.GrowingSphere ->
                    GrowingSphereStroke(
                        strokeName = strokeName,
                        material =
                            createBrushMaterial(
                                color = brushConfig.displayColor,
                                colorMode = brushConfig.colorMode,
                                textureSelection = brushConfig.textureSelection,
                                materialPreset = brushConfig.materialPreset,
                            ),
                        parent = strokesRoot,
                        baseRadius = brushConfig.radius,
                    )
                BrushMode.StraightLine ->
                    StraightLineStroke(
                        strokeName = strokeName,
                        material =
                            createBrushMaterial(
                                color = brushConfig.displayColor,
                                colorMode = brushConfig.colorMode,
                                textureSelection = brushConfig.textureSelection,
                                materialPreset = brushConfig.materialPreset,
                            ),
                        parent = strokesRoot,
                        radius = brushConfig.radius,
                    )
            }
        stroke.appendPoint(startPoint)
        activeStroke = stroke
        return stroke
    }

    private fun endStroke() {
        drawActive = false
        activeStroke?.let { finishAndStore(it) }
        activeStroke = null
    }

    private fun finishAndStore(stroke: BrushStroke) {
        stroke.finish()
        if (stroke.hasRenderableMesh) {
            finishedStrokes.add(stroke)
        } else {
            stroke.dispose()
        }
    }
}

private data class BrushConfig(
    val color: Color4,
    val displayColor: BrushColorState,
    val colorMode: BrushColorMode,
    val textureSelection: TextureCropSelection?,
    val radius: Float,
    val mode: BrushMode,
    val materialPreset: BrushMaterialPreset,
)

private enum class BrushMode {
    Tube,
    SphereSteps,
    GrowingSphere,
    StraightLine,
}

private enum class BrushColorMode {
    PureColor,
    Texture,
}

private enum class BrushMaterialPreset {
    Metal,
    Fabric,
    Plastic,
    Translucent,
}

private enum class HandSide {
    Left,
    Right,
}

private class HandPanelState(
    private val handSide: HandSide,
    initialExpandedPosition: Vector3,
) {
    var collapsed by mutableStateOf(false)
        private set

    private var currentPosition = initialExpandedPosition
    private var expandedPosition = initialExpandedPosition
    private var currentScale = CONTROL_PANEL_SCALE
    private var expandGestureLatched = false

    fun collapse() {
        collapsed = true
    }

    fun applyTransform(panel: Entity) {
        panel.components.set(
            TransformComponent()
                .setPosition(currentPosition)
                .setScaleVector(Vector3(currentScale)),
        )
    }

    fun update(
        panel: Entity?,
        handPose: HandPose?,
        worldToScene: (Vector3) -> Vector3,
    ) {
        panel ?: return

        val toggleGestureActive =
            handPose?.thumbLittleDistance()?.let { it <= PANEL_EXPAND_GESTURE_DISTANCE_M } == true
        if (toggleGestureActive && !expandGestureLatched) {
            if (collapsed) {
                handPose?.let { expandedHandPose ->
                    expandedPosition = worldToScene(expandedHandPose.expandedPanelWorldPosition())
                    collapsed = false
                }
            } else {
                collapsed = true
            }
        }
        expandGestureLatched = toggleGestureActive

        val targetPosition =
            when {
                collapsed && handPose != null -> worldToScene(handPose.collapsedOrbWorldPosition())
                else -> expandedPosition
            }
        val targetScale = if (collapsed) COLLAPSED_PANEL_WORLD_SCALE else CONTROL_PANEL_SCALE

        currentPosition = Vector3.lerp(currentPosition, targetPosition, PANEL_TRANSFORM_LERP)
        currentScale = lerp(currentScale, targetScale, PANEL_TRANSFORM_LERP)

        panel.enabled = !collapsed || handPose != null
        applyTransform(panel)
    }
}

private fun applyFixedPanelTransform(
    panel: Entity,
    position: Vector3,
) {
    panel.components.set(
        TransformComponent()
            .setPosition(position)
            .setScaleVector(Vector3(CONTROL_PANEL_SCALE)),
    )
}

private class HandDrawInputState {
    private var activeUiPointerId: PointerId? = null
    private var uiPressed = false
    private var pinchHoldStartUptimeMillis: Long? = null
    var shouldDraw = false
        private set

    fun onUiPointerEvent(event: SpatialPointerInfo) {
        val pointerId = event.pointerId

        when {
            event.isDownEvent() -> {
                activeUiPointerId = pointerId
                uiPressed = true
                shouldDraw = false
                pinchHoldStartUptimeMillis = null
            }

            activeUiPointerId == pointerId && (event.isUpEvent() || !event.pressed) -> {
                activeUiPointerId = null
                uiPressed = false
            }
        }
    }

    fun updateLongPinchState(
        handPose: HandPose?,
        nowUptimeMillis: Long,
        uiInteractionActive: Boolean,
        allowInstantPinchDrawing: Boolean,
    ) {
        if (uiPressed || uiInteractionActive || handPose == null) {
            pinchHoldStartUptimeMillis = null
            shouldDraw = false
            return
        }

        val pinchDistance = handPose.pinchDistance()
        val withinPinchRange =
            if (shouldDraw) {
                pinchDistance < PINCH_RELEASE_DISTANCE_M
            } else {
                pinchDistance < PINCH_START_DISTANCE_M
            }

        if (!withinPinchRange) {
            pinchHoldStartUptimeMillis = null
            shouldDraw = false
            return
        }

        if (shouldDraw) {
            return
        }

        if (allowInstantPinchDrawing) {
            shouldDraw = true
            return
        }

        if (pinchHoldStartUptimeMillis == null) {
            pinchHoldStartUptimeMillis = nowUptimeMillis
        }

        shouldDraw =
            nowUptimeMillis - (pinchHoldStartUptimeMillis ?: nowUptimeMillis) >=
                LONG_PINCH_ACTIVATION_MS
    }

    fun reset() {
        activeUiPointerId = null
        uiPressed = false
        pinchHoldStartUptimeMillis = null
        shouldDraw = false
    }

    fun cancelDrawingForUi() {
        pinchHoldStartUptimeMillis = null
        shouldDraw = false
    }
}

private data class BrushColorState(
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

private data class TextureCropSelection(
    val sourceBitmap: Bitmap,
    val cropLeftPx: Int,
    val cropTopPx: Int,
    val cropSizePx: Int,
) {
    companion object
}

private data class SquareCropRect(
    val left: Int,
    val top: Int,
    val size: Int,
)

private fun TextureCropSelection.squareCropRect(): SquareCropRect {
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

private fun TextureCropSelection.withCropRect(
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

private fun Bitmap.minCropSizePx(): Int =
    (minOf(width, height) * MIN_TEXTURE_CROP_SIZE_RATIO).roundToInt().coerceAtLeast(MIN_TEXTURE_CROP_SIZE_PX)

private fun TextureCropSelection.Companion.createDefault(sourceBitmap: Bitmap): TextureCropSelection {
    val size = minOf(sourceBitmap.width, sourceBitmap.height).coerceAtLeast(sourceBitmap.minCropSizePx())
    return TextureCropSelection(
        sourceBitmap = sourceBitmap,
        cropLeftPx = (sourceBitmap.width - size) / 2,
        cropTopPx = (sourceBitmap.height - size) / 2,
        cropSizePx = size,
    )
}

private interface BrushStroke {
    val lastPoint: Vector3?
    val vertexCount: Int
    val hasRenderableMesh: Boolean
    val requiresContinuousUpdates: Boolean

    fun appendPoint(point: Vector3)

    fun finish()

    fun dispose()
}

private class DynamicTubeStroke(
    private val strokeName: String,
    private val material: Material,
    private val parent: Entity,
    private val radius: Float,
) : BrushStroke {
    private val strokeRoot = Entity().apply { setName(strokeName) }
    private val sampledPoints = mutableListOf<Vector3>()
    private val cumulativeLengths = mutableListOf<Float>()
    private var dynamicStartIndex = 0
    private var nextFreezeTriggerLengthM = INITIAL_FREEZE_TRIGGER_LENGTH_M
    private val frozenChunks = mutableListOf<FrozenTubeChunk>()
    private var lastFrozenRing: TubeRing? = null
    private var dynamicAnchorProfile: StrokeProfile? = null
    private var dynamicMeshResource: MeshResource? = null
    private var dynamicMeshEntity: ModelEntity? = null
    private var lastMeshUpdateRealtimeMs = 0L

    override val lastPoint: Vector3?
        get() = sampledPoints.lastOrNull()

    override val vertexCount: Int
        get() = sampledPoints.size * STROKE_RING_SIDES

    override val hasRenderableMesh: Boolean
        get() = dynamicMeshEntity != null || frozenChunks.isNotEmpty()

    override val requiresContinuousUpdates: Boolean
        get() = false

    init {
        parent.addChild(strokeRoot)
    }

    override fun appendPoint(point: Vector3) {
        val previousPoint = sampledPoints.lastOrNull()
        if (previousPoint == null) {
            sampledPoints.add(point)
            cumulativeLengths.add(0f)
            return
        }

        val distance = Vector3.distance(previousPoint, point)
        if (distance < MIN_POINT_SPACING_M) {
            return
        }

        val steps = maxOf(1, (distance / TARGET_POINT_SPACING_M).toInt())
        var changed = false
        for (step in 1..steps) {
            val t = step.toFloat() / steps.toFloat()
            val interpolated = Vector3.lerp(previousPoint, point, t)
            val currentLast = sampledPoints.last()
            if (Vector3.distance(currentLast, interpolated) >= MIN_POINT_SPACING_M) {
                sampledPoints.add(interpolated)
                cumulativeLengths.add(cumulativeLengths.last() + Vector3.distance(currentLast, interpolated))
                changed = true
            }
        }

        if (changed && sampledPoints.size >= 2) {
            requestMeshUpdate()
        }
    }

    override fun finish() {
        if (sampledPoints.size >= 2) {
            requestMeshUpdate(force = true)
        }
    }

    override fun dispose() {
        dynamicMeshEntity?.let { strokeRoot.removeChild(it) }
        dynamicMeshEntity = null
        dynamicMeshResource?.close()
        dynamicMeshResource = null
        frozenChunks.forEach { chunk ->
            strokeRoot.removeChild(chunk.entity)
            chunk.resource.close()
        }
        frozenChunks.clear()
        lastFrozenRing = null
        dynamicAnchorProfile = null
        parent.removeChild(strokeRoot)
    }

    private fun requestMeshUpdate(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val shouldUpdateNow =
            force ||
                dynamicMeshEntity == null ||
                now - lastMeshUpdateRealtimeMs >= MESH_UPDATE_THROTTLE_MS
        if (!shouldUpdateNow) {
            return
        }

        updateMeshes()
        lastMeshUpdateRealtimeMs = now
    }

    private fun updateMeshes() {
        var dynamicWindowProfile = buildDynamicWindowProfile()
        var dynamicRings = buildDynamicRings(dynamicWindowProfile)
        if (freezeStablePrefix(dynamicWindowProfile, dynamicRings)) {
            dynamicWindowProfile = buildDynamicWindowProfile()
            dynamicRings = buildDynamicRings(dynamicWindowProfile)
        }
        val meshData =
            dynamicRings?.let { buildTubeMeshDataFromRings(it, radius) }
        if (meshData == null) {
            dynamicMeshEntity?.let { strokeRoot.removeChild(it) }
            dynamicMeshEntity = null
            dynamicMeshResource?.close()
            dynamicMeshResource = null
            return
        }
        val resource = dynamicMeshResource
        if (resource == null) {
            val newMesh = MeshResource.createWithMeshModel(meshData.model, meshData.bounds, strokeName)
            dynamicMeshResource = newMesh
            dynamicMeshEntity =
                ModelEntity(newMesh, material).apply {
                    setName(strokeName)
                }
            strokeRoot.addChild(dynamicMeshEntity!!)
        } else {
            resource.replaceWithMeshModel(meshData.model, meshData.bounds)
        }
    }

    private fun buildDynamicRings(profile: StrokeProfile?): List<TubeRing>? {
        if (profile == null || profile.positions.size < 2) {
            return null
        }

        return if (lastFrozenRing == null) {
            buildTubeRingsFromProfile(profile)
        } else {
            buildTubeRingsFromProfile(
                profile = profile,
                initialRing = lastFrozenRing,
            )
        }
    }

    private fun buildDynamicWindowProfile(): StrokeProfile? {
        val dynamicPoints = sampledPoints.subList(dynamicStartIndex, sampledPoints.size).toList()
        val generatedProfile =
            if (dynamicPoints.size >= 2) {
                buildStrokeProfile(
                    points = dynamicPoints,
                    startCap = dynamicStartIndex == 0 && dynamicAnchorProfile == null,
                    endCap = true,
                    previousSmoothingPoints =
                        sampledPoints.subList(
                            maxOf(0, dynamicStartIndex - SMOOTHING_CONTEXT_POINT_COUNT),
                            dynamicStartIndex,
                        ),
                )
            } else {
                null
            }

        return when {
            dynamicAnchorProfile != null && generatedProfile != null ->
                mergeStrokeProfilesByOverlap(dynamicAnchorProfile!!, generatedProfile)
            dynamicAnchorProfile != null -> dynamicAnchorProfile
            else -> generatedProfile
        }
    }

    private fun freezeStablePrefix(
        profile: StrokeProfile?,
        dynamicRings: List<TubeRing>?,
    ): Boolean {
        val totalStrokeLength = cumulativeLengths.lastOrNull() ?: return false
        if (totalStrokeLength < nextFreezeTriggerLengthM) {
            return false
        }

        val latestStableIndex = findLatestStableIndex()
        if (latestStableIndex <= dynamicStartIndex) {
            return false
        }

        val nonNullProfile = profile ?: return false
        val nonNullDynamicRings = dynamicRings ?: return false
        if (nonNullProfile.positions.size < MIN_PROFILE_POINTS_PER_CHUNK) {
            return false
        }

        val profileLengths = computeCumulativeLengths(nonNullProfile.positions)
        val totalProfileLength = profileLengths.lastOrNull() ?: return false
        val freezeLength = totalProfileLength - DYNAMIC_TAIL_KEEP_LENGTH_M
        if (freezeLength < MIN_FROZEN_PREFIX_LENGTH_M) {
            return false
        }

        val maxSplitIndex = nonNullProfile.positions.lastIndex - MIN_PROFILE_POINTS_AFTER_SPLIT
        var splitIndex = 0
        while (splitIndex < maxSplitIndex && profileLengths[splitIndex + 1] <= freezeLength) {
            splitIndex++
        }
        if (splitIndex < 1) {
            return false
        }

        var anchorEndIndex = splitIndex
        val anchorTargetLength = profileLengths[splitIndex] + DYNAMIC_ANCHOR_LENGTH_M
        while (anchorEndIndex < nonNullProfile.positions.lastIndex && profileLengths[anchorEndIndex] < anchorTargetLength) {
            anchorEndIndex++
        }
        anchorEndIndex = maxOf(anchorEndIndex, splitIndex + 1)

        val ringsToFreeze =
            if (lastFrozenRing == null) {
                nonNullDynamicRings.take(splitIndex + 1)
            } else {
                nonNullDynamicRings.drop(1).take(splitIndex)
            }
        if (ringsToFreeze.isEmpty()) {
            return false
        }

        appendFrozenChunk(
            if (lastFrozenRing == null) {
                ringsToFreeze
            } else {
                listOf(lastFrozenRing!!) + ringsToFreeze
            },
        )
        lastFrozenRing = ringsToFreeze.last()
        dynamicAnchorProfile = sliceStrokeProfile(nonNullProfile, splitIndex, anchorEndIndex)
        dynamicStartIndex = latestStableIndex
        nextFreezeTriggerLengthM += FREEZE_TRIGGER_INTERVAL_M
        return true
    }

    private fun appendFrozenChunk(chunkRings: List<TubeRing>) {
        if (chunkRings.size < 2) {
            return
        }

        val chunkIndex = frozenChunks.size
        val chunkName = "${strokeName}_chunk_$chunkIndex"
        val meshData = buildTubeMeshDataFromRings(chunkRings, radius)
        val resource = MeshResource.createWithMeshModel(meshData.model, meshData.bounds, chunkName)
        val entity =
            ModelEntity(resource, material).apply {
                setName(chunkName)
            }
        strokeRoot.addChild(entity)
        frozenChunks.add(FrozenTubeChunk(resource = resource, entity = entity))
    }

    private fun findLatestStableIndex(): Int {
        if (sampledPoints.size < 3) {
            return dynamicStartIndex
        }

        val stableCutoffLength = cumulativeLengths.last() - DYNAMIC_TAIL_KEEP_LENGTH_M
        if (stableCutoffLength <= cumulativeLengths[dynamicStartIndex]) {
            return dynamicStartIndex
        }

        var stableIndex = dynamicStartIndex
        val lastAllowedIndex = sampledPoints.lastIndex - 1
        while (
            stableIndex < lastAllowedIndex &&
            cumulativeLengths[stableIndex + 1] <= stableCutoffLength
        ) {
            stableIndex++
        }
        return stableIndex
    }
}

private class SphereStepStroke(
    private val strokeName: String,
    private val material: Material,
    private val parent: Entity,
    private val radius: Float,
) : BrushStroke {
    private val strokeRoot = Entity().apply { setName(strokeName) }
    private val sphereMesh = MeshResource.createSphere(radius)
    private val spheres = mutableListOf<ModelEntity>()
    private var lastSampledPoint: Vector3? = null
    private var distanceSinceLastPlacement = 0f

    override val lastPoint: Vector3?
        get() = lastSampledPoint

    override val vertexCount: Int
        get() = spheres.size

    override val hasRenderableMesh: Boolean
        get() = spheres.isNotEmpty()

    override val requiresContinuousUpdates: Boolean
        get() = false

    init {
        parent.addChild(strokeRoot)
    }

    override fun appendPoint(point: Vector3) {
        val previousPoint = lastSampledPoint
        lastSampledPoint = point

        if (spheres.isEmpty()) {
            placeSphere(point)
            return
        }

        val nonNullPreviousPoint = previousPoint ?: return
        var fromPoint = nonNullPreviousPoint
        var remainingSegmentLength = Vector3.distance(nonNullPreviousPoint, point)

        while (true) {
            val distanceToNextPlacement = SPHERE_STEP_DISTANCE_M - distanceSinceLastPlacement
            if (remainingSegmentLength < distanceToNextPlacement) {
                break
            }
            val segment = point - fromPoint
            val segmentLength = segment.length()
            val direction = segment / segmentLength
            val nextPoint = fromPoint + direction * distanceToNextPlacement
            placeSphere(nextPoint)
            fromPoint = nextPoint
            remainingSegmentLength -= distanceToNextPlacement
            distanceSinceLastPlacement = 0f
        }

        distanceSinceLastPlacement += remainingSegmentLength
    }

    override fun finish() = Unit

    override fun dispose() {
        parent.removeChild(strokeRoot)
        sphereMesh.close()
    }

    private fun placeSphere(point: Vector3) {
        val sphere =
            ModelEntity(sphereMesh, material).apply {
                setName("${strokeName}_${spheres.size}")
                components.set(
                    TransformComponent().setPosition(point),
                )
            }
        strokeRoot.addChild(sphere)
        spheres.add(sphere)
        distanceSinceLastPlacement = 0f
    }
}

private class GrowingSphereStroke(
    private val strokeName: String,
    private val material: Material,
    private val parent: Entity,
    private val baseRadius: Float,
) : BrushStroke {
    private val strokeRoot = Entity().apply { setName(strokeName) }
    private val sphereMesh = MeshResource.createSphere(1f)
    private var sphereEntity: ModelEntity? = null
    private var centerPoint: Vector3? = null
    private var drawStartRealtimeMs = 0L
    private var currentRadius = baseRadius

    override val lastPoint: Vector3?
        get() = centerPoint

    override val vertexCount: Int
        get() = if (sphereEntity != null) 1 else 0

    override val hasRenderableMesh: Boolean
        get() = sphereEntity != null

    override val requiresContinuousUpdates: Boolean
        get() = true

    init {
        parent.addChild(strokeRoot)
    }

    override fun appendPoint(point: Vector3) {
        if (centerPoint == null) {
            centerPoint = point
            drawStartRealtimeMs = SystemClock.elapsedRealtime()
            currentRadius = baseRadius
            val entity =
                ModelEntity(sphereMesh, material).apply {
                    setName(strokeName)
                }
            strokeRoot.addChild(entity)
            sphereEntity = entity
        }
        updateSphereScale()
    }

    override fun finish() {
        updateSphereScale()
    }

    override fun dispose() {
        parent.removeChild(strokeRoot)
        sphereMesh.close()
    }

    private fun updateSphereScale() {
        val center = centerPoint ?: return
        val elapsedSeconds = (SystemClock.elapsedRealtime() - drawStartRealtimeMs).coerceAtLeast(0L) / 1000f
        currentRadius =
            (baseRadius + elapsedSeconds * GROWING_SPHERE_GROWTH_SPEED_M_PER_S)
                .coerceAtMost(MAX_GROWING_SPHERE_RADIUS_M)
        sphereEntity?.components?.set(
            TransformComponent()
                .setPosition(center)
                .setScaleVector(Vector3(currentRadius)),
        )
    }
}

private class StraightLineStroke(
    private val strokeName: String,
    private val material: Material,
    private val parent: Entity,
    private val radius: Float,
) : BrushStroke {
    private val strokeRoot = Entity().apply { setName(strokeName) }
    private var startPoint: Vector3? = null
    private var endPoint: Vector3? = null
    private var meshResource: MeshResource? = null
    private var meshEntity: ModelEntity? = null

    override val lastPoint: Vector3?
        get() = endPoint ?: startPoint

    override val vertexCount: Int
        get() = if (meshEntity != null) STROKE_RING_SIDES * 2 else 0

    override val hasRenderableMesh: Boolean
        get() = meshEntity != null

    override val requiresContinuousUpdates: Boolean
        get() = false

    init {
        parent.addChild(strokeRoot)
    }

    override fun appendPoint(point: Vector3) {
        if (startPoint == null) {
            startPoint = point
            endPoint = point
            return
        }
        endPoint = point
        rebuildLineMesh()
    }

    override fun finish() {
        rebuildLineMesh()
    }

    override fun dispose() {
        meshEntity?.let { strokeRoot.removeChild(it) }
        meshEntity = null
        meshResource?.close()
        meshResource = null
        parent.removeChild(strokeRoot)
    }

    private fun rebuildLineMesh() {
        val start = startPoint ?: return
        val end = endPoint ?: return
        if (Vector3.distance(start, end) < MIN_POINT_SPACING_M) {
            return
        }
        val profile =
            buildUniformCapsuleProfile(
                points = listOf(start, end),
                radius = radius,
            )
        val rings = buildTubeRingsFromProfile(profile)
        val meshData = buildTubeMeshDataFromRings(rings, radius)
        val existing = meshResource
        if (existing == null) {
            val resource = MeshResource.createWithMeshModel(meshData.model, meshData.bounds, strokeName)
            meshResource = resource
            meshEntity =
                ModelEntity(resource, material).apply {
                    setName(strokeName)
                }
            strokeRoot.addChild(meshEntity!!)
        } else {
            existing.replaceWithMeshModel(meshData.model, meshData.bounds)
        }
    }
}

private data class TubeMeshData(
    val model: MeshModel,
    val bounds: BoundingBox,
)

private data class FrozenTubeChunk(
    val resource: MeshResource,
    val entity: ModelEntity,
)

private data class TubeRing(
    val center: Vector3,
    val radiusScale: Float,
    val normal: Vector3,
    val binormal: Vector3,
    val v: Float,
)

private data class TubeMeshSection(
    val positions: List<Vector3>,
    val normals: List<Vector3>,
    val uv0: List<Vector2>,
    val indices: List<Int>,
    val bounds: BoundingBox,
)

private data class StrokeProfile(
    val positions: List<Vector3>,
    val radiusScales: List<Float>,
)

private fun buildTubeRingsFromProfile(
    profile: StrokeProfile,
    previousContextPoint: Vector3? = null,
    nextContextPoint: Vector3? = null,
    initialRing: TubeRing? = null,
): List<TubeRing> {
    val positions =
        if (initialRing == null) {
            profile.positions
        } else {
            buildList(profile.positions.size) {
                add(initialRing.center)
                addAll(profile.positions.drop(1))
            }
        }
    val radiusScales =
        if (initialRing == null) {
            profile.radiusScales
        } else {
            buildList(profile.radiusScales.size) {
                add(initialRing.radiusScale)
                addAll(profile.radiusScales.drop(1))
            }
        }
    val tangents =
        computeTangents(
            points = positions,
            previousContextPoint = previousContextPoint,
            nextContextPoint = nextContextPoint,
        )
    val frames =
        computeFrames(
            tangents = tangents,
            initialFrame = initialRing?.let { TubeFrame(normal = it.normal, binormal = it.binormal) },
        )
    val cumulativeLengths = computeCumulativeLengths(positions)
    val vOffset = initialRing?.v ?: 0f

    return positions.indices.map { pointIndex ->
        TubeRing(
            center = positions[pointIndex],
            radiusScale = radiusScales[pointIndex],
            normal = frames[pointIndex].normal,
            binormal = frames[pointIndex].binormal,
            v = cumulativeLengths[pointIndex] / UV_REPEAT_DISTANCE_M + vOffset,
        )
    }
}

private fun buildTubeMeshDataFromRings(
    rings: List<TubeRing>,
    radius: Float,
): TubeMeshData {
    val section = buildTubeMeshSectionFromRings(rings, radius)
    return buildTubeMeshDataFromSection(section)
}

private fun buildTubeMeshSectionFromRings(
    rings: List<TubeRing>,
    radius: Float,
): TubeMeshSection {
    val positions = mutableListOf<Vector3>()
    val normals = mutableListOf<Vector3>()
    val uv0 = mutableListOf<Vector2>()
    val indices = mutableListOf<Int>()
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY

    for (pointIndex in rings.indices) {
        val ring = rings[pointIndex]
        val point = ring.center
        val ringRadius = radius * ring.radiusScale

        for (side in 0 until STROKE_RING_SIDES) {
            val angle = TAU_F * side.toFloat() / STROKE_RING_SIDES.toFloat()
            val radial =
                (ring.normal * cos(angle) + ring.binormal * sin(angle)).normalize()
            val vertex = point + radial * ringRadius
            positions.add(vertex)
            normals.add(radial)
            uv0.add(Vector2(side.toFloat() / STROKE_RING_SIDES.toFloat(), ring.v))
            minX = minOf(minX, vertex.x)
            minY = minOf(minY, vertex.y)
            minZ = minOf(minZ, vertex.z)
            maxX = maxOf(maxX, vertex.x)
            maxY = maxOf(maxY, vertex.y)
            maxZ = maxOf(maxZ, vertex.z)
        }
    }

    for (ring in 0 until rings.lastIndex) {
        val ringStart = ring * STROKE_RING_SIDES
        val nextRingStart = (ring + 1) * STROKE_RING_SIDES
        for (side in 0 until STROKE_RING_SIDES) {
            val nextSide = (side + 1) % STROKE_RING_SIDES
            val a = ringStart + side
            val b = ringStart + nextSide
            val c = nextRingStart + side
            val d = nextRingStart + nextSide

            // Keep the side quads wound outward so the tube front faces remain visible.
            indices.add(a)
            indices.add(b)
            indices.add(c)
            indices.add(b)
            indices.add(d)
            indices.add(c)
        }
    }

    return TubeMeshSection(
        positions = positions,
        normals = normals,
        uv0 = uv0,
        indices = indices,
        bounds =
            BoundingBox(
                Vector3(minX, minY, minZ),
                Vector3(maxX, maxY, maxZ),
            ),
    )
}

private fun buildTubeMeshDataFromSection(section: TubeMeshSection): TubeMeshData {
    val model = MeshModel(section.positions, section.indices, section.normals, uv0 = section.uv0)
    return TubeMeshData(model = model, bounds = section.bounds)
}

private fun sliceStrokeProfile(
    profile: StrokeProfile,
    startIndex: Int,
    endIndexInclusive: Int,
): StrokeProfile =
    StrokeProfile(
        positions = profile.positions.subList(startIndex, endIndexInclusive + 1).toList(),
        radiusScales = profile.radiusScales.subList(startIndex, endIndexInclusive + 1).toList(),
    )

private fun mergeStrokeProfilesByOverlap(
    prefixProfile: StrokeProfile,
    suffixProfile: StrokeProfile,
): StrokeProfile {
    if (prefixProfile.positions.isEmpty()) return suffixProfile
    if (suffixProfile.positions.isEmpty()) return prefixProfile

    val dropIndex = (findProfileOverlapIndex(prefixProfile, suffixProfile) ?: -1) + 1

    return StrokeProfile(
        positions = prefixProfile.positions + suffixProfile.positions.drop(dropIndex),
        radiusScales = prefixProfile.radiusScales + suffixProfile.radiusScales.drop(dropIndex),
    )
}

private fun findProfileOverlapIndex(
    prefixProfile: StrokeProfile,
    suffixProfile: StrokeProfile,
): Int? {
    if (prefixProfile.positions.isEmpty() || suffixProfile.positions.isEmpty()) {
        return null
    }

    val prefixEnd = prefixProfile.positions.last()
    val searchLimit = minOf(suffixProfile.positions.size, PROFILE_OVERLAP_SEARCH_POINT_COUNT)
    var bestIndex = 0
    var bestDistance = Float.MAX_VALUE

    for (index in 0 until searchLimit) {
        val distance = Vector3.distance(prefixEnd, suffixProfile.positions[index])
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }

    return if (bestDistance <= PROFILE_OVERLAP_DISTANCE_THRESHOLD_M) {
        bestIndex
    } else {
        null
    }
}

private data class TubeFrame(
    val normal: Vector3,
    val binormal: Vector3,
)

private fun computeTangents(
    points: List<Vector3>,
    previousContextPoint: Vector3? = null,
    nextContextPoint: Vector3? = null,
): List<Vector3> =
    points.indices.map { index ->
        val previous =
            when {
                index > 0 -> points[index - 1]
                previousContextPoint != null -> previousContextPoint
                else -> points[index]
            }
        val next =
            when {
                index < points.lastIndex -> points[index + 1]
                nextContextPoint != null -> nextContextPoint
                else -> points[index]
            }
        safeNormalize(next - previous, Vector3.FORWARD)
    }

private fun computeFrames(
    tangents: List<Vector3>,
    initialFrame: TubeFrame? = null,
): List<TubeFrame> {
    val frames = mutableListOf<TubeFrame>()
    var normal = initialFrame?.normal ?: createReferenceNormal(tangents.first())
    var binormal = initialFrame?.binormal ?: safeNormalize(Vector3.cross(tangents.first(), normal), Vector3.RIGHT)
    var previousTangent = tangents.first()

    tangents.forEachIndexed { index, tangent ->
        if (index > 0) {
            val axis = Vector3.cross(previousTangent, tangent)
            val axisLength = axis.length()
            if (axisLength > EPSILON_M) {
                val angle = acos(Vector3.dot(previousTangent, tangent).coerceIn(-1f, 1f))
                normal = Quat(axis / axisLength, angle).rotateVector(normal)
            }
            val projected = normal - tangent * Vector3.dot(normal, tangent)
            normal = safeNormalize(projected, createReferenceNormal(tangent))
            binormal = safeNormalize(Vector3.cross(tangent, normal), Vector3.RIGHT)
            normal = safeNormalize(Vector3.cross(binormal, tangent), Vector3.UP)
        }
        frames.add(TubeFrame(normal = normal, binormal = binormal))
        previousTangent = tangent
    }
    return frames
}

private fun createReferenceNormal(tangent: Vector3): Vector3 {
    val reference =
        if (abs(Vector3.dot(tangent, Vector3.UP)) > 0.9f) {
            Vector3.RIGHT
        } else {
            Vector3.UP
        }
    return safeNormalize(Vector3.cross(reference, tangent), Vector3.RIGHT)
}

private fun computeCumulativeLengths(points: List<Vector3>): List<Float> {
    val lengths = MutableList(points.size) { 0f }
    for (index in 1 until points.size) {
        lengths[index] = lengths[index - 1] + Vector3.distance(points[index - 1], points[index])
    }
    return lengths
}

private fun buildStrokeProfile(
    points: List<Vector3>,
    startCap: Boolean,
    endCap: Boolean,
    previousSmoothingPoints: List<Vector3> = emptyList(),
    nextSmoothingPoints: List<Vector3> = emptyList(),
): StrokeProfile {
    if (points.size < 2) {
        return StrokeProfile(
            positions = points,
            radiusScales = List(points.size) { 1f },
        )
    }

    val smoothedInput =
        smoothStrokePointsWithContext(
            points = points,
            passes = SMOOTHING_PASSES,
            previousContextPoints = previousSmoothingPoints,
            nextContextPoints = nextSmoothingPoints,
        )
    val corePoints =
        smoothedInput
            .let { resampleStrokePoints(it, TARGET_POINT_SPACING_M) }

    if (corePoints.size < 2) {
        return StrokeProfile(
            positions = corePoints,
            radiusScales = List(corePoints.size) { 1f },
        )
    }

    val startTangent = safeNormalize(corePoints[1] - corePoints[0], Vector3.FORWARD)
    val endTangent =
        safeNormalize(
            corePoints.last() - corePoints[corePoints.lastIndex - 1],
            Vector3.FORWARD,
        )
    val bodyLengths = computeCumulativeLengths(corePoints)
    val totalLength = bodyLengths.last()

    val positions = mutableListOf<Vector3>()
    val radiusScales = mutableListOf<Float>()

    if (startCap) {
        for (step in 0 until END_CAP_SAMPLE_COUNT) {
            val u = step.toFloat() / END_CAP_SAMPLE_COUNT.toFloat()
            positions.add(corePoints.first() - startTangent * (END_CAP_LENGTH_M * (1f - u)))
            radiusScales.add(THIN_STROKE_SCALE * hemisphereBlend(u))
        }
    }

    corePoints.forEachIndexed { index, point ->
        val fromStart = bodyLengths[index]
        val fromEnd = totalLength - fromStart
        val startScale = if (startCap) bodyRadiusScale(fromStart) else 1f
        val endScale = if (endCap) bodyRadiusScale(fromEnd) else 1f
        positions.add(point)
        radiusScales.add(minOf(startScale, endScale))
    }

    if (endCap) {
        for (step in 1..END_CAP_SAMPLE_COUNT) {
            val u = step.toFloat() / END_CAP_SAMPLE_COUNT.toFloat()
            positions.add(corePoints.last() + endTangent * (END_CAP_LENGTH_M * u))
            radiusScales.add(THIN_STROKE_SCALE * hemisphereBlend(1f - u))
        }
    }

    return StrokeProfile(
        positions = positions,
        radiusScales = radiusScales,
    )
}

private fun buildUniformCapsuleProfile(
    points: List<Vector3>,
    radius: Float,
): StrokeProfile {
    if (points.size < 2) {
        return StrokeProfile(
            positions = points,
            radiusScales = List(points.size) { 1f },
        )
    }

    val corePoints = resampleStrokePoints(points, TARGET_POINT_SPACING_M)
    if (corePoints.size < 2) {
        return StrokeProfile(
            positions = corePoints,
            radiusScales = List(corePoints.size) { 1f },
        )
    }

    val startTangent = safeNormalize(corePoints[1] - corePoints[0], Vector3.FORWARD)
    val endTangent =
        safeNormalize(
            corePoints.last() - corePoints[corePoints.lastIndex - 1],
            Vector3.FORWARD,
        )
    val capLength = maxOf(radius, MIN_POINT_SPACING_M)
    val positions = mutableListOf<Vector3>()
    val radiusScales = mutableListOf<Float>()

    for (step in 0 until END_CAP_SAMPLE_COUNT) {
        val u = step.toFloat() / END_CAP_SAMPLE_COUNT.toFloat()
        positions.add(corePoints.first() - startTangent * (capLength * (1f - u)))
        radiusScales.add(hemisphereBlend(u))
    }

    corePoints.forEach { point ->
        positions.add(point)
        radiusScales.add(1f)
    }

    for (step in 1..END_CAP_SAMPLE_COUNT) {
        val u = step.toFloat() / END_CAP_SAMPLE_COUNT.toFloat()
        positions.add(corePoints.last() + endTangent * (capLength * u))
        radiusScales.add(hemisphereBlend(1f - u))
    }

    return StrokeProfile(
        positions = positions,
        radiusScales = radiusScales,
    )
}

private fun smoothStrokePoints(
    points: List<Vector3>,
    passes: Int,
): List<Vector3> {
    if (points.size < 3) return points

    var smoothed = points
    repeat(passes) {
        smoothed =
            buildList(smoothed.size) {
                add(smoothed.first())
                for (index in 1 until smoothed.lastIndex) {
                    val previous = smoothed[index - 1]
                    val current = smoothed[index]
                    val next = smoothed[index + 1]
                    add(previous * 0.2f + current * 0.6f + next * 0.2f)
                }
                add(smoothed.last())
            }
    }
    return smoothed
}

private fun smoothStrokePointsWithContext(
    points: List<Vector3>,
    passes: Int,
    previousContextPoints: List<Vector3>,
    nextContextPoints: List<Vector3>,
): List<Vector3> {
    if (points.size < 3 || (previousContextPoints.isEmpty() && nextContextPoints.isEmpty())) {
        return smoothStrokePoints(points, passes)
    }

    val prefixSize = previousContextPoints.size
    var smoothed = previousContextPoints + points + nextContextPoints
    repeat(passes) {
        smoothed =
            buildList(smoothed.size) {
                add(smoothed.first())
                for (index in 1 until smoothed.lastIndex) {
                    val previous = smoothed[index - 1]
                    val current = smoothed[index]
                    val next = smoothed[index + 1]
                    add(previous * 0.2f + current * 0.6f + next * 0.2f)
                }
                add(smoothed.last())
            }
    }
    return smoothed.subList(prefixSize, prefixSize + points.size)
}

private fun resampleStrokePoints(
    points: List<Vector3>,
    spacing: Float,
): List<Vector3> {
    if (points.size < 2) return points

    val result = mutableListOf(points.first())
    var segmentStart = points.first()
    var pointIndex = 1
    var remainingDistance = spacing

    while (pointIndex < points.size) {
        val segmentEnd = points[pointIndex]
        val segment = segmentEnd - segmentStart
        val segmentLength = segment.length()

        if (segmentLength < EPSILON_M) {
            segmentStart = segmentEnd
            pointIndex++
            continue
        }

        if (segmentLength >= remainingDistance) {
            val direction = segment / segmentLength
            val sample = segmentStart + direction * remainingDistance
            result.add(sample)
            segmentStart = sample
            remainingDistance = spacing
        } else {
            remainingDistance -= segmentLength
            segmentStart = segmentEnd
            pointIndex++
        }
    }

    if (Vector3.distance(result.last(), points.last()) > EPSILON_M) {
        result.add(points.last())
    }
    return result
}

private fun bodyRadiusScale(distance: Float): Float {
    if (distance <= 0f) return THIN_STROKE_SCALE
    if (distance >= END_TRANSITION_DISTANCE_M) return 1f
    return lerp(
        THIN_STROKE_SCALE,
        1f,
        smoothstep(distance / END_TRANSITION_DISTANCE_M),
    )
}

private fun hemisphereBlend(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return sqrt((1f - (1f - t) * (1f - t)).coerceAtLeast(0f))
}

private fun lerp(
    start: Float,
    end: Float,
    t: Float,
): Float {
    return start + (end - start) * t
}

private fun smoothstep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun safeNormalize(
    vector: Vector3,
    fallback: Vector3,
): Vector3 {
    return if (vector.length() > EPSILON_M) {
        vector.normalize()
    } else {
        fallback
    }
}

private fun createSphereEntity(
    name: String,
    material: Material,
    position: Vector3 = Vector3.ZERO,
    radius: Float,
    scale: Float,
): ModelEntity {
    val sphereMesh = MeshResource.createSphere(radius)
    return ModelEntity(sphereMesh, material).apply {
        setName(name)
        components.set(
            TransformComponent()
                .setPosition(position)
                .setScaleVector(Vector3(scale)),
        )
    }
}

private fun createBrushMaterial(
    color: BrushColorState,
    colorMode: BrushColorMode,
    textureSelection: TextureCropSelection?,
    materialPreset: BrushMaterialPreset,
): PhysicallyBasedMaterial {
    val material =
        PhysicallyBasedMaterial.create(
            if (materialPreset == BrushMaterialPreset.Translucent) BlendingMode.TRANSPARENT else BlendingMode.OPAQUE,
        )
    val baseColor =
        when (materialPreset) {
            BrushMaterialPreset.Metal -> mixColors(color.uiColor, Color(0xFFB8BDC7), 0.18f)
            BrushMaterialPreset.Fabric -> mixColors(color.uiColor, Color(0xFF6C7078), 0.24f)
            BrushMaterialPreset.Plastic -> color.uiColor
            BrushMaterialPreset.Translucent -> color.uiColor.copy(alpha = 0.4f)
        }
    val textureBitmap =
        if (colorMode == BrushColorMode.Texture) {
            textureSelection?.toTextureBitmap()
        } else {
            null
        }
    if (textureBitmap != null) {
        material.setBaseColor(Color.White.copy(alpha = baseColor.alpha).toLinearColor4())
        runCatching { TextureResource.create(textureBitmap) }
            .onSuccess { material.setBaseColorTexture(it) }
            .onFailure {
                material.setBaseColor(baseColor.toLinearColor4())
            }
    } else {
        material.setBaseColor(baseColor.toLinearColor4())
    }
    when (materialPreset) {
        BrushMaterialPreset.Metal -> {
            material.setMetallic(0.92f)
            material.setRoughness(0.18f)
        }
        BrushMaterialPreset.Fabric -> {
            material.setMetallic(0f)
            material.setRoughness(0.92f)
        }
        BrushMaterialPreset.Plastic -> {
            material.setMetallic(0.04f)
            material.setRoughness(0.34f)
        }
        BrushMaterialPreset.Translucent -> {
            material.setMetallic(0f)
            material.setRoughness(0.08f)
        }
    }
    material.setAmbientOcclusion(1f)
    return material
}

private fun TextureCropSelection.toTextureBitmap(): Bitmap? {
    val cropRect = squareCropRect()
    val cropped =
        Bitmap.createBitmap(
            sourceBitmap,
            cropRect.left,
            cropRect.top,
            cropRect.size,
            cropRect.size,
        )
    if (cropRect.size == TEXTURE_OUTPUT_SIZE_PX) {
        return cropped
    }
    val scaled = Bitmap.createScaledBitmap(cropped, TEXTURE_OUTPUT_SIZE_PX, TEXTURE_OUTPUT_SIZE_PX, true)
    if (scaled !== cropped) {
        cropped.recycle()
    }
    return scaled
}

private fun loadTextureBitmap(
    context: android.content.Context,
    uri: Uri,
): Bitmap? =
    runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maxDimension = maxOf(info.size.width, info.size.height)
            if (maxDimension > MAX_TEXTURE_IMPORT_EDGE_PX) {
                val scale = MAX_TEXTURE_IMPORT_EDGE_PX.toFloat() / maxDimension.toFloat()
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }.getOrNull()

private fun mixColors(
    a: Color,
    b: Color,
    t: Float,
): Color {
    val clampedT = t.coerceIn(0f, 1f)
    return Color(
        red = lerp(a.red, b.red, clampedT),
        green = lerp(a.green, b.green, clampedT),
        blue = lerp(a.blue, b.blue, clampedT),
        alpha = lerp(a.alpha, b.alpha, clampedT),
    )
}

private fun Color.toLinearColor4(): Color4 {
    val linearColor = convert(ColorSpaces.LinearSrgb)
    return Color4(linearColor.red, linearColor.green, linearColor.blue, linearColor.alpha)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMaterialSphere(
    preset: BrushMaterialPreset,
    previewColor: Color,
    selected: Boolean,
) {
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val radius = size.minDimension * 0.40f
    val highlightCenter = Offset(size.width * 0.34f, size.height * 0.28f)
    val shadowCenter = Offset(size.width * 0.66f, size.height * 0.72f)
    val baseTint =
        when (preset) {
            BrushMaterialPreset.Metal -> mixColors(previewColor, Color(0xFFB8BDC7), 0.18f)
            BrushMaterialPreset.Fabric -> mixColors(previewColor, Color(0xFF6C7078), 0.24f)
            BrushMaterialPreset.Plastic -> previewColor.copy(alpha = 1f)
            BrushMaterialPreset.Translucent -> previewColor.copy(alpha = 0.55f)
        }

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    when (preset) {
                        BrushMaterialPreset.Metal ->
                            listOf(
                                Color.White.copy(alpha = 0.95f),
                                baseTint,
                                Color(0xFF5A606C),
                            )
                        BrushMaterialPreset.Fabric ->
                            listOf(
                                baseTint.copy(alpha = 0.95f),
                                baseTint,
                                baseTint.copy(alpha = 0.82f),
                            )
                        BrushMaterialPreset.Plastic ->
                            listOf(
                                Color.White.copy(alpha = 0.85f),
                                baseTint,
                                baseTint.copy(alpha = 0.88f),
                            )
                        BrushMaterialPreset.Translucent ->
                            listOf(
                                Color.White.copy(alpha = 0.65f),
                                baseTint,
                                baseTint.copy(alpha = 0.22f),
                            )
                    },
                center = highlightCenter,
                radius = size.minDimension * 0.78f,
            ),
        radius = radius,
        center = center,
    )
    if (preset == BrushMaterialPreset.Fabric) {
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)),
                    center = shadowCenter,
                    radius = size.minDimension * 0.65f,
                ),
            radius = radius,
            center = center,
        )
    } else {
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                    center = shadowCenter,
                    radius = size.minDimension * 0.7f,
                ),
            radius = radius,
            center = center,
        )
    }
    drawCircle(
        color =
            when (preset) {
                BrushMaterialPreset.Metal -> Color.White.copy(alpha = 0.55f)
                BrushMaterialPreset.Fabric -> Color.White.copy(alpha = 0.14f)
                BrushMaterialPreset.Plastic -> Color.White.copy(alpha = 0.48f)
                BrushMaterialPreset.Translucent -> Color.White.copy(alpha = 0.34f)
            },
        radius = radius * 0.28f,
        center = highlightCenter,
    )
    if (preset == BrushMaterialPreset.Translucent) {
        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = radius * 0.68f,
            center = center,
        )
    }
    drawCircle(
        color = if (selected) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.22f),
        radius = radius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (selected) 3f else 2f),
    )
}

private fun resolveHandSide(
    event: SpatialPointerInfo,
    handTrackingData: HandTrackingData,
): HandSide? {
    val eventPosition = event.position3D.toVector3()
    val leftPoint = handTrackingData.left?.pinchCenter()
    val rightPoint = handTrackingData.right?.pinchCenter()

    return when {
        leftPoint == null && rightPoint == null -> null
        leftPoint != null && rightPoint == null -> HandSide.Left
        leftPoint == null && rightPoint != null -> HandSide.Right
        else -> {
            val leftDistance = Vector3.distance(leftPoint!!, eventPosition)
            val rightDistance = Vector3.distance(rightPoint!!, eventPosition)
            if (leftDistance <= rightDistance) HandSide.Left else HandSide.Right
        }
    }
}

private fun HandPose.pinchCenter(): Vector3 {
    val indexTip = this[HandJoint.Index.INDEX_TIP]
    val thumbTip = this[HandJoint.Index.THUMB_TIP]
    return (indexTip.position + thumbTip.position) / 2f
}

private fun HandPose.thumbLittleDistance(): Float {
    val thumbTip = this[HandJoint.Index.THUMB_TIP]
    val littleTip = this[HandJoint.Index.LITTLE_TIP]
    return Vector3.distance(thumbTip.position, littleTip.position)
}

private fun HandPose.thumbMiddleDistance(): Float {
    val thumbTip = this[HandJoint.Index.THUMB_TIP]
    val middleTip = this[HandJoint.Index.MIDDLE_TIP]
    return Vector3.distance(thumbTip.position, middleTip.position)
}

private fun HandPose.panelForwardDirection(): Vector3 {
    val wrist = this[HandJoint.Index.WRIST].position
    val palm = this[HandJoint.Index.PALM].position
    val middle = this[HandJoint.Index.MIDDLE_PROXIMAL].position
    return safeNormalize((palm - wrist) + (middle - palm), Vector3.FORWARD)
}

private fun HandPose.collapsedOrbWorldPosition(): Vector3 {
    val palm = this[HandJoint.Index.PALM].position
    return palm + panelForwardDirection() * PANEL_ORB_FORWARD_OFFSET_M
}

private fun HandPose.expandedPanelWorldPosition(): Vector3 {
    val palm = this[HandJoint.Index.PALM].position
    return palm + panelForwardDirection() * PANEL_EXPANDED_FORWARD_OFFSET_M
}

private fun HandPose.pinchDistance(): Float {
    val indexTip = this[HandJoint.Index.INDEX_TIP]
    val thumbTip = this[HandJoint.Index.THUMB_TIP]
    return Vector3.distance(indexTip.position, thumbTip.position)
}

private fun com.pico.spatial.ui.geometry.Offset3D.toVector3(): Vector3 = Vector3(x, y, z)

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

private fun normalizeHue(hue: Float): Float {
    val normalized = hue % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}

private const val PINCH_START_DISTANCE_M = 0.018f
private const val PINCH_RELEASE_DISTANCE_M = 0.026f
private const val DEFAULT_BRUSH_RADIUS_M = 0.008f
private const val MIN_BRUSH_RADIUS_M = 0.0055f
private const val MAX_BRUSH_RADIUS_M = 0.012f
private const val MAX_GROWING_SPHERE_RADIUS_M = 0.3f
private const val GROWING_SPHERE_GROWTH_SPEED_M_PER_S = 0.22f
private const val CURSOR_BASE_RADIUS_M = 0.012f
private const val MIN_POINT_SPACING_M = 0.004f
private const val TARGET_POINT_SPACING_M = 0.008f
private const val MAX_SEGMENT_JUMP_M = 0.08f
private const val MAX_STROKE_VERTICES = 12000
private const val SPHERE_STEP_DISTANCE_M = 0.05f
private const val STROKE_RING_SIDES = 12
private const val SMOOTHING_PASSES = 2
private const val SMOOTHING_CONTEXT_POINT_COUNT = 2
private const val DYNAMIC_TAIL_KEEP_LENGTH_M = 0.20f
private const val INITIAL_FREEZE_TRIGGER_LENGTH_M = 0.50f
private const val FREEZE_TRIGGER_INTERVAL_M = 0.50f
private const val DYNAMIC_ANCHOR_LENGTH_M = 0.028f
private const val MIN_FROZEN_PREFIX_LENGTH_M = 0.30f
private const val MIN_PROFILE_POINTS_PER_CHUNK = 6
private const val MIN_PROFILE_POINTS_AFTER_SPLIT = 4
private const val PROFILE_OVERLAP_SEARCH_POINT_COUNT = 12
private const val PROFILE_OVERLAP_DISTANCE_THRESHOLD_M = 0.03f
private const val MESH_UPDATE_THROTTLE_MS = 33L
private const val THIN_STROKE_SCALE = 0.42f
private const val END_TRANSITION_DISTANCE_M = 0.07f
private const val END_CAP_LENGTH_M = 0.018f
private const val END_CAP_SAMPLE_COUNT = 5
private const val LONG_PINCH_ACTIVATION_MS = 450L
private const val LEFT_CONTROL_PANEL_ATTACHMENT_ID = "left_brush_control_panel"
private const val RIGHT_CONTROL_PANEL_ATTACHMENT_ID = "right_brush_control_panel"
private const val TEXTURE_PICKER_ATTACHMENT_ID = "texture_picker_panel"
private val LEFT_CONTROL_PANEL_POSITION = Vector3(-0.38f, 1.12f, -0.58f)
private val RIGHT_CONTROL_PANEL_POSITION = Vector3(0.38f, 1.12f, -0.58f)
private val TEXTURE_PICKER_PANEL_POSITION = Vector3(0f, 1.12f, -0.50f)
private const val CONTROL_PANEL_SCALE = 1.0f
private val CONTROL_PANEL_WIDTH_DP = 380.dp
private val TEXTURE_PICKER_PANEL_WIDTH_DP = 440.dp
private val TEXTURE_PICKER_PANEL_HEIGHT_DP = 560.dp
private val COLLAPSED_PANEL_SIZE_DP = 72.dp
private const val COLLAPSED_PANEL_WORLD_SCALE = 0.32f
private const val PANEL_TRANSFORM_LERP = 0.22f
private const val PANEL_ORB_FORWARD_OFFSET_M = 0.05f
private const val PANEL_EXPANDED_FORWARD_OFFSET_M = 0.15f
private const val PANEL_EXPAND_GESTURE_DISTANCE_M = 0.028f
private const val UNDO_GESTURE_DISTANCE_M = 0.028f
private const val BRUSH_PREVIEW_LOCAL_SCALE = 1.0f
private const val GROWING_SPHERE_PREVIEW_SCALE = 2.6f
private const val BRUSH_PREVIEW_MIN_GROWING_SPHERE_RADIUS_M = 0.025f
private const val BRUSH_PREVIEW_CONTROL_POINT_COUNT = 90
private const val BRUSH_PREVIEW_HELIX_TURNS = 1.5f
private const val BRUSH_PREVIEW_HALF_WIDTH_M = 0.058f
private const val BRUSH_PREVIEW_WAVE_AMPLITUDE_M = 0.03f
private const val BRUSH_PREVIEW_Z_AMPLITUDE_M = 0.014f
private const val BRUSH_PREVIEW_LINE_HALF_LENGTH_M = 0.09f
private val DEFAULT_LEFT_BRUSH_COLOR = BrushColorState.fromUiColor(Color(0xFF4E8CFF))
private val DEFAULT_RIGHT_BRUSH_COLOR = BrushColorState.fromUiColor(Color(0xFFFF7A59))
private val BRUSH_PREVIEW_LOCAL_POSITION = Vector3(-0.03f, 0.07f, 0.015f)
private val BRUSH_PREVIEW_POINTS =
    List(BRUSH_PREVIEW_CONTROL_POINT_COUNT) { index ->
        val t = index.toFloat() / (BRUSH_PREVIEW_CONTROL_POINT_COUNT - 1).toFloat()
        val x = lerp(-BRUSH_PREVIEW_HALF_WIDTH_M, BRUSH_PREVIEW_HALF_WIDTH_M, t)
        val angle = t * BRUSH_PREVIEW_HELIX_TURNS * TAU_F
        val y = cos(angle) * BRUSH_PREVIEW_WAVE_AMPLITUDE_M
        val z = sin(angle) * BRUSH_PREVIEW_Z_AMPLITUDE_M
        Vector3(x, y, z)
    }
private val BRUSH_PREVIEW_LINE_POINTS =
    listOf(
        Vector3(-BRUSH_PREVIEW_LINE_HALF_LENGTH_M, 0f, 0f),
        Vector3(BRUSH_PREVIEW_LINE_HALF_LENGTH_M, 0f, 0f),
    )
private val HUE_TRACK_COLORS =
    listOf(
        Color.Red,
        Color.Yellow,
        Color.Green,
        Color.Cyan,
        Color.Blue,
        Color.Magenta,
        Color.Red,
    )
private val COLOR_TRACK_WIDTH_DP = 236.dp
private val COLOR_TRACK_LABEL_WIDTH_DP = 40.dp
private val COLOR_TRACK_VALUE_WIDTH_DP = 44.dp
private val BRUSH_SIZE_TRACK_WIDTH_DP = 212.dp
private val MATERIAL_SWATCH_SIZE_DP = 44.dp
private val TEXTURE_CROP_PREVIEW_SIZE_DP = 132.dp
private val TEXTURE_CROP_EDITOR_PREVIEW_SIZE_DP = 320.dp
private val TEXTURE_CROP_HANDLE_SIZE_DP = 28.dp
private val TEXTURE_CROP_HANDLE_HIT_SIZE_DP = 56.dp
private val BRUSH_PREVIEW_PANEL_CLEARANCE_DP = 72.dp
private val COLOR_TRACK_TOUCH_HEIGHT_DP = 34.dp
private val COLOR_TRACK_HEIGHT_DP = 14.dp
private val COLOR_TRACK_KNOB_DIAMETER_DP = 28.dp
private val COLOR_TRACK_KNOB_HIT_SIZE_DP = 52.dp
private val COLOR_TRACK_HORIZONTAL_PADDING_DP = 14.dp
private val COLOR_TRACK_DRAW_WIDTH_DP = 220.dp
private const val COLOR_TRACK_HORIZONTAL_PADDING_PX = 14f
private const val COLOR_TRACK_DRAW_WIDTH_PX = 220f
private const val COLOR_TRACK_KNOB_HOVER_SCALE = 1.78f
private const val TRUE_COLOR_WHEEL_MARKER_OUTER_RADIUS_PX = 11.5f
private const val TRUE_COLOR_WHEEL_MARKER_INNER_RADIUS_PX = 7f
private const val TRUE_COLOR_WHEEL_MARKER_HOVER_HALO_PX = 10f
private const val TRUE_COLOR_WHEEL_MARKER_HOVER_RING_PX = 6f
private const val UI_POINTER_DRAW_BLOCK_HOLD_MS = 180L
private const val UV_REPEAT_DISTANCE_M = 0.03f
private const val EPSILON_M = 0.0001f
private const val TEXTURE_OUTPUT_SIZE_PX = 512
private const val MAX_TEXTURE_IMPORT_EDGE_PX = 2048
private const val MIN_TEXTURE_CROP_SIZE_PX = 192
private const val MIN_TEXTURE_CROP_SIZE_RATIO = 0.22f
private const val TEXTURE_CROP_HANDLE_HOVER_SCALE = 1.45f
private const val TAU_F = (PI * 2.0).toFloat()
private val TEXTURE_CROP_TRACK_COLORS =
    listOf(
        Color(0xFF3E4656),
        Color(0xFF8D98AD),
        Color(0xFFE3E7F0),
    )
private val BRUSH_MATERIAL_PRESETS =
    listOf(
        BrushMaterialPreset.Metal,
        BrushMaterialPreset.Fabric,
        BrushMaterialPreset.Plastic,
        BrushMaterialPreset.Translucent,
    )
