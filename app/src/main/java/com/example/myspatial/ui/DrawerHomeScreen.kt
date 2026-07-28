package com.example.myspatial.ui

import android.graphics.Color as AndroidColor
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
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
    var leftPanelInteracting by remember { mutableStateOf(false) }
    var rightPanelInteracting by remember { mutableStateOf(false) }
    var leftSelectedColor by remember { mutableStateOf(DEFAULT_LEFT_BRUSH_COLOR) }
    var leftSelectedSize by remember { mutableStateOf(BrushSizePreset.Medium) }
    var leftSelectedMode by remember { mutableStateOf(BrushMode.Tube) }
    var rightSelectedColor by remember { mutableStateOf(DEFAULT_RIGHT_BRUSH_COLOR) }
    var rightSelectedSize by remember { mutableStateOf(BrushSizePreset.Medium) }
    var rightSelectedMode by remember { mutableStateOf(BrushMode.Tube) }

    paintScene.setBrushConfig(
        handSide = HandSide.Left,
        config =
            BrushConfig(
                color = leftSelectedColor.toColor4(),
                radius = leftSelectedSize.radius,
                mode = leftSelectedMode,
            ),
    )
    paintScene.setBrushConfig(
        handSide = HandSide.Right,
        config =
            BrushConfig(
                color = rightSelectedColor.toColor4(),
                radius = rightSelectedSize.radius,
                mode = rightSelectedMode,
            ),
    )
    paintScene.setPanelInteractionActive(leftPanelInteracting || rightPanelInteracting)

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
                    panel.components.set(
                        TransformComponent()
                            .setPosition(LEFT_CONTROL_PANEL_POSITION)
                            .setScaleVector(Vector3(CONTROL_PANEL_SCALE)),
                    )
                    content.addEntity(panel)
                }
                attachments.entity(RIGHT_CONTROL_PANEL_ATTACHMENT_ID)?.let { panel ->
                    panel.components.set(
                        TransformComponent()
                            .setPosition(RIGHT_CONTROL_PANEL_POSITION)
                            .setScaleVector(Vector3(CONTROL_PANEL_SCALE)),
                    )
                    content.addEntity(panel)
                }
            },
            attachments = {
                AttachmentPanel(
                    id = LEFT_CONTROL_PANEL_ATTACHMENT_ID,
                ) {
                    HandBrushPanel(
                        title = "左手画笔",
                        selectedColor = leftSelectedColor,
                        selectedSize = leftSelectedSize,
                        selectedMode = leftSelectedMode,
                        onColorSelected = { leftSelectedColor = it },
                        onSizeSelected = { leftSelectedSize = it },
                        onModeSelected = { leftSelectedMode = it },
                        onInteractionChanged = { leftPanelInteracting = it },
                    )
                }
                AttachmentPanel(
                    id = RIGHT_CONTROL_PANEL_ATTACHMENT_ID,
                ) {
                    HandBrushPanel(
                        title = "右手画笔",
                        selectedColor = rightSelectedColor,
                        selectedSize = rightSelectedSize,
                        selectedMode = rightSelectedMode,
                        onColorSelected = { rightSelectedColor = it },
                        onSizeSelected = { rightSelectedSize = it },
                        onModeSelected = { rightSelectedMode = it },
                        onInteractionChanged = { rightPanelInteracting = it },
                        onClear = { paintScene.clear() },
                    )
                }
            },
            update = { _, _ ->
                paintScene.update(handTrackingData)
            },
        )
    }
}

@Composable
private fun HandBrushPanel(
    title: String,
    selectedColor: BrushColorState,
    selectedSize: BrushSizePreset,
    selectedMode: BrushMode,
    onColorSelected: (BrushColorState) -> Unit,
    onSizeSelected: (BrushSizePreset) -> Unit,
    onModeSelected: (BrushMode) -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    DisposableEffect(Unit) {
        onDispose { onInteractionChanged(false) }
    }

    Column(
        modifier =
            Modifier
                .width(320.dp)
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
        Text(text = title, style = PicoTheme.typography.titleLarge)
        Text(
            text = "短 pinch 点面板，长 pinch 开始绘画。",
            style = PicoTheme.typography.bodyMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrushOptionButton(
                label = "动态线",
                selected = selectedMode == BrushMode.Tube,
                onClick = { onModeSelected(BrushMode.Tube) },
            )
            BrushOptionButton(
                label = "圆球点阵",
                selected = selectedMode == BrushMode.SphereSteps,
                onClick = { onModeSelected(BrushMode.SphereSteps) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrushSizePreset.entries.forEach { size ->
                BrushSizeOptionButton(
                    label = size.label,
                    dotSize = size.dotSize,
                    selected = selectedSize == size,
                    onClick = { onSizeSelected(size) },
                )
            }
        }

        Text(text = "颜色", style = PicoTheme.typography.titleMedium)
        TripleColorTrackPalette(
            selectedColor = selectedColor,
            onColorSelected = onColorSelected,
        )

        Text(
            text = "圆球点阵模式下，轨迹至少移动 10cm 才会再落下一颗球。",
            style = PicoTheme.typography.bodyMedium,
        )

        onClear?.let { clearAction ->
            Button(onClick = clearAction) {
                Text(text = "Clear Stroke")
            }
        }
    }
}

@Composable
private fun TripleColorTrackPalette(
    selectedColor: BrushColorState,
    onColorSelected: (BrushColorState) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .border(2.dp, Color.White, CircleShape)
                        .background(selectedColor.uiColor, CircleShape),
            )
            Text(
                text =
                    "H ${selectedColor.hue.roundToInt()}  S ${(selectedColor.saturation * 100f).roundToInt()}  V ${(selectedColor.value * 100f).roundToInt()}",
                style = PicoTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ColorTrackSlider(
    label: String,
    value: Float,
    valueText: String,
    knobColor: Color,
    gradientColors: List<Color>,
    onValueChange: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val trackHeightPx = with(density) { COLOR_TRACK_HEIGHT_DP.toPx() }
    var isDragging by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            isDragging = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            modifier = Modifier.width(COLOR_TRACK_WIDTH_DP),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = PicoTheme.typography.bodyMedium)
            Text(text = valueText, style = PicoTheme.typography.bodyMedium)
        }

        Box(
            modifier =
                Modifier
                    .size(width = COLOR_TRACK_WIDTH_DP, height = COLOR_TRACK_TOUCH_HEIGHT_DP)
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
                                        ((change.position.x - COLOR_TRACK_HORIZONTAL_PADDING_PX) / COLOR_TRACK_DRAW_WIDTH_PX)
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
                )
            }
            ColorTrackKnob(
                value = value,
                color = knobColor,
                dragging = isDragging,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawColorTrack(
    value: Float,
    trackHeightPx: Float,
    gradientColors: List<Color>,
) {
    val trackTop = (size.height - trackHeightPx) * 0.5f
    val trackLeft = COLOR_TRACK_HORIZONTAL_PADDING_PX
    val trackWidth = size.width - COLOR_TRACK_HORIZONTAL_PADDING_PX * 2f
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
) {
    val knobDiameter = COLOR_TRACK_KNOB_DIAMETER_DP
    val knobHitSize = COLOR_TRACK_KNOB_HIT_SIZE_DP
    val knobCenterX = COLOR_TRACK_HORIZONTAL_PADDING_DP + COLOR_TRACK_DRAW_WIDTH_DP * value.coerceIn(0f, 1f)
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
        Text(text = if (selected) "[$label]" else label)
    }
}

@Composable
private fun BrushSizeOptionButton(
    label: String,
    dotSize: Dp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .border(1.dp, Color(0x55FFFFFF), CircleShape)
                        .background(Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(dotSize)
                            .background(Color.White, CircleShape),
                )
            }
            Text(text = if (selected) "[$label]" else label)
        }
    }
}

private class PaintSceneState {
    private val sceneRoot = Entity().apply { setName("PaintSceneRoot") }
    private val leftInput = HandDrawInputState(HandSide.Left)
    private val rightInput = HandDrawInputState(HandSide.Right)
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

    fun update(handTrackingData: HandTrackingData) {
        val now = SystemClock.uptimeMillis()
        val uiInteractionActive = panelInteractionActive || activeUiPointers.isNotEmpty() || now < uiBlockUntilUptimeMillis

        if (handTrackingData.left == null) {
            leftInput.reset()
        }
        if (handTrackingData.right == null) {
            rightInput.reset()
        }

        leftInput.updateLongPinchState(handTrackingData.left, now, uiInteractionActive)
        rightInput.updateLongPinchState(handTrackingData.right, now, uiInteractionActive)

        leftStroke.update(handTrackingData.left, sceneRoot, leftInput.shouldDraw)
        rightStroke.update(handTrackingData.right, sceneRoot, rightInput.shouldDraw)
    }

    fun setBrushConfig(
        handSide: HandSide,
        config: BrushConfig,
    ) {
        when (handSide) {
            HandSide.Left -> leftStroke.setBrushConfig(config)
            HandSide.Right -> rightStroke.setBrushConfig(config)
        }
    }

    fun clear() {
        leftStroke.clear()
        rightStroke.clear()
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
            radius = BrushSizePreset.Medium.radius,
            mode = BrushMode.Tube,
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
        if (distance < MIN_POINT_SPACING_M) {
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

    private fun beginStroke(startPoint: Vector3): BrushStroke {
        val strokeName = "${strokesRoot.getName()}_${strokeIndex++}"
        val stroke =
            when (brushConfig.mode) {
                BrushMode.Tube ->
                    DynamicTubeStroke(
                        strokeName = strokeName,
                        material = createBrushMaterial(brushConfig.color),
                        parent = strokesRoot,
                        radius = brushConfig.radius,
                    )
                BrushMode.SphereSteps ->
                    SphereStepStroke(
                        strokeName = strokeName,
                        material = createBrushMaterial(brushConfig.color),
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
    val radius: Float,
    val mode: BrushMode,
)

private enum class BrushMode {
    Tube,
    SphereSteps,
}

private enum class HandSide {
    Left,
    Right,
}

private class HandDrawInputState(
    private val handSide: HandSide,
) {
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

private enum class BrushSizePreset(
    val label: String,
    val radius: Float,
    val dotSize: Dp,
) {
    Small("细", 0.0055f, 5.dp),
    Medium("中", 0.008f, 8.dp),
    Large("粗", 0.012f, 11.dp),
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

private interface BrushStroke {
    val lastPoint: Vector3?
    val vertexCount: Int
    val hasRenderableMesh: Boolean

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
    private val sampledPoints = mutableListOf<Vector3>()
    private var meshResource: MeshResource? = null
    private var meshEntity: ModelEntity? = null

    override val lastPoint: Vector3?
        get() = sampledPoints.lastOrNull()

    override val vertexCount: Int
        get() = sampledPoints.size * STROKE_RING_SIDES

    override val hasRenderableMesh: Boolean
        get() = meshEntity != null

    override fun appendPoint(point: Vector3) {
        val previousPoint = sampledPoints.lastOrNull()
        if (previousPoint == null) {
            sampledPoints.add(point)
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
                changed = true
            }
        }

        if (changed && sampledPoints.size >= 2) {
            rebuildMesh()
        }
    }

    override fun finish() {
        if (sampledPoints.size >= 2) {
            rebuildMesh()
        }
    }

    override fun dispose() {
        meshEntity?.let { parent.removeChild(it) }
        meshEntity = null
        meshResource?.close()
        meshResource = null
    }

    private fun rebuildMesh() {
        val meshData = buildTubeMesh(sampledPoints, radius)
        val resource = meshResource
        if (resource == null) {
            val newMesh = MeshResource.createWithMeshModel(meshData.model, meshData.bounds, strokeName)
            meshResource = newMesh
            meshEntity =
                ModelEntity(newMesh, material).apply {
                    setName(strokeName)
                }
            parent.addChild(meshEntity!!)
        } else {
            resource.replaceWithMeshModel(meshData.model, meshData.bounds)
        }
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

private data class TubeMeshData(
    val model: MeshModel,
    val bounds: BoundingBox,
)

private data class StrokeProfile(
    val positions: List<Vector3>,
    val radiusScales: List<Float>,
)

private fun buildTubeMesh(
    points: List<Vector3>,
    radius: Float,
): TubeMeshData {
    val profile = buildStrokeProfile(points)
    val tangents = computeTangents(profile.positions)
    val frames = computeFrames(tangents)
    val cumulativeLengths = computeCumulativeLengths(profile.positions)

    val positions = mutableListOf<Vector3>()
    val normals = mutableListOf<Vector3>()
    val uv0 = mutableListOf<Vector2>()
    val indices = mutableListOf<Int>()

    for (pointIndex in profile.positions.indices) {
        val point = profile.positions[pointIndex]
        val frame = frames[pointIndex]
        val v = cumulativeLengths[pointIndex] / UV_REPEAT_DISTANCE_M
        val ringRadius = radius * profile.radiusScales[pointIndex]

        for (side in 0 until STROKE_RING_SIDES) {
            val angle = TAU_F * side.toFloat() / STROKE_RING_SIDES.toFloat()
            val radial =
                (frame.normal * cos(angle) + frame.binormal * sin(angle)).normalize()
            positions.add(point + radial * ringRadius)
            normals.add(radial)
            uv0.add(Vector2(side.toFloat() / STROKE_RING_SIDES.toFloat(), v))
        }
    }

    for (ring in 0 until profile.positions.lastIndex) {
        val ringStart = ring * STROKE_RING_SIDES
        val nextRingStart = (ring + 1) * STROKE_RING_SIDES
        for (side in 0 until STROKE_RING_SIDES) {
            val nextSide = (side + 1) % STROKE_RING_SIDES
            val a = ringStart + side
            val b = ringStart + nextSide
            val c = nextRingStart + side
            val d = nextRingStart + nextSide

            indices.add(a)
            indices.add(c)
            indices.add(b)
            indices.add(b)
            indices.add(c)
            indices.add(d)
        }
    }

    val model = MeshModel(positions, indices, normals, uv0 = uv0)
    val bounds = computeBoundingBox(positions)
    return TubeMeshData(model = model, bounds = bounds)
}

private data class TubeFrame(
    val normal: Vector3,
    val binormal: Vector3,
)

private fun computeTangents(points: List<Vector3>): List<Vector3> =
    points.indices.map { index ->
        val previous = if (index == 0) points[index] else points[index - 1]
        val next = if (index == points.lastIndex) points[index] else points[index + 1]
        safeNormalize(next - previous, Vector3.FORWARD)
    }

private fun computeFrames(tangents: List<Vector3>): List<TubeFrame> {
    val frames = mutableListOf<TubeFrame>()
    var normal = createReferenceNormal(tangents.first())
    var binormal = safeNormalize(Vector3.cross(tangents.first(), normal), Vector3.RIGHT)
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

private fun buildStrokeProfile(points: List<Vector3>): StrokeProfile {
    if (points.size < 2) {
        return StrokeProfile(
            positions = points,
            radiusScales = List(points.size) { 1f },
        )
    }

    val corePoints =
        points
            .let { smoothStrokePoints(it, SMOOTHING_PASSES) }
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

    for (step in 0 until END_CAP_SAMPLE_COUNT) {
        val u = step.toFloat() / END_CAP_SAMPLE_COUNT.toFloat()
        positions.add(corePoints.first() - startTangent * (END_CAP_LENGTH_M * (1f - u)))
        radiusScales.add(THIN_STROKE_SCALE * hemisphereBlend(u))
    }

    corePoints.forEachIndexed { index, point ->
        val fromStart = bodyLengths[index]
        val fromEnd = totalLength - fromStart
        val startScale = bodyRadiusScale(fromStart)
        val endScale = bodyRadiusScale(fromEnd)
        positions.add(point)
        radiusScales.add(minOf(startScale, endScale))
    }

    for (step in 1..END_CAP_SAMPLE_COUNT) {
        val u = step.toFloat() / END_CAP_SAMPLE_COUNT.toFloat()
        positions.add(corePoints.last() + endTangent * (END_CAP_LENGTH_M * u))
        radiusScales.add(THIN_STROKE_SCALE * hemisphereBlend(1f - u))
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

private fun computeBoundingBox(positions: List<Vector3>): BoundingBox {
    var minX = positions.first().x
    var minY = positions.first().y
    var minZ = positions.first().z
    var maxX = minX
    var maxY = minY
    var maxZ = minZ

    for (position in positions.drop(1)) {
        minX = minOf(minX, position.x)
        minY = minOf(minY, position.y)
        minZ = minOf(minZ, position.z)
        maxX = maxOf(maxX, position.x)
        maxY = maxOf(maxY, position.y)
        maxZ = maxOf(maxZ, position.z)
    }

    return BoundingBox(
        Vector3(minX, minY, minZ),
        Vector3(maxX, maxY, maxZ),
    )
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

private fun createBrushMaterial(color: Color4): PhysicallyBasedMaterial =
    PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
        setBaseColor(color)
        setRoughness(0.28f)
        setMetallic(0.02f)
        setAmbientOcclusion(1f)
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
private const val BRUSH_RADIUS_M = 0.008f
private const val CURSOR_BASE_RADIUS_M = 0.012f
private const val MIN_POINT_SPACING_M = 0.004f
private const val TARGET_POINT_SPACING_M = 0.008f
private const val MAX_SEGMENT_JUMP_M = 0.08f
private const val MAX_STROKE_VERTICES = 12000
private const val SPHERE_STEP_DISTANCE_M = 0.10f
private const val STROKE_RING_SIDES = 12
private const val SMOOTHING_PASSES = 2
private const val THIN_STROKE_SCALE = 0.42f
private const val END_TRANSITION_DISTANCE_M = 0.10f
private const val END_CAP_LENGTH_M = 0.018f
private const val END_CAP_SAMPLE_COUNT = 5
private const val LONG_PINCH_ACTIVATION_MS = 450L
private const val LEFT_CONTROL_PANEL_ATTACHMENT_ID = "left_brush_control_panel"
private const val RIGHT_CONTROL_PANEL_ATTACHMENT_ID = "right_brush_control_panel"
private val LEFT_CONTROL_PANEL_POSITION = Vector3(-0.38f, 1.12f, -0.58f)
private val RIGHT_CONTROL_PANEL_POSITION = Vector3(0.38f, 1.12f, -0.58f)
private const val CONTROL_PANEL_SCALE = 1.0f
private val DEFAULT_LEFT_BRUSH_COLOR = BrushColorState.fromUiColor(Color(0xFF4E8CFF))
private val DEFAULT_RIGHT_BRUSH_COLOR = BrushColorState.fromUiColor(Color(0xFFFF7A59))
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
private val COLOR_TRACK_WIDTH_DP = 248.dp
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
private const val TAU_F = (PI * 2.0).toFloat()
