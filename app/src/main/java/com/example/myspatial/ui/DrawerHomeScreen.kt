package com.example.myspatial.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.cos
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
    var selectedColor by remember { mutableStateOf(BrushColorPreset.Coral) }
    var selectedSize by remember { mutableStateOf(BrushSizePreset.Medium) }
    var selectedMode by remember { mutableStateOf(BrushMode.Tube) }

    paintScene.setBrushConfig(
        BrushConfig(
            color = selectedColor.color,
            radius = selectedSize.radius,
            mode = selectedMode,
        ),
    )

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
                attachments.entity(CONTROL_PANEL_ATTACHMENT_ID)?.let { panel ->
                    panel.components.set(
                        TransformComponent()
                            .setPosition(CONTROL_PANEL_POSITION)
                            .setScaleVector(Vector3(CONTROL_PANEL_SCALE)),
                    )
                    content.addEntity(panel)
                }
            },
            attachments = {
                AttachmentPanel(
                    id = CONTROL_PANEL_ATTACHMENT_ID,
                ) {
                    BrushControlPanel(
                        selectedColor = selectedColor,
                        selectedSize = selectedSize,
                        selectedMode = selectedMode,
                        onColorSelected = { selectedColor = it },
                        onSizeSelected = { selectedSize = it },
                        onModeSelected = { selectedMode = it },
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
private fun BrushControlPanel(
    selectedColor: BrushColorPreset,
    selectedSize: BrushSizePreset,
    selectedMode: BrushMode,
    onColorSelected: (BrushColorPreset) -> Unit,
    onSizeSelected: (BrushSizePreset) -> Unit,
    onModeSelected: (BrushMode) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(320.dp)
                .background(Color(0xCC1B1D24))
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(text = "Brush Panel", style = PicoTheme.typography.titleLarge)
        Text(
            text = "短 pinch 用来点面板，长 pinch 才会开始绘画。",
            style = PicoTheme.typography.bodyMedium,
        )

        Text(text = "模式", style = PicoTheme.typography.titleMedium)
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

        Text(text = "粗细", style = PicoTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrushSizePreset.entries.forEach { size ->
                BrushOptionButton(
                    label = size.label,
                    selected = selectedSize == size,
                    onClick = { onSizeSelected(size) },
                )
            }
        }

        Text(text = "颜色", style = PicoTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrushColorPreset.entries.take(3).forEach { color ->
                BrushOptionButton(
                    label = color.label,
                    selected = selectedColor == color,
                    onClick = { onColorSelected(color) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrushColorPreset.entries.drop(3).forEach { color ->
                BrushOptionButton(
                    label = color.label,
                    selected = selectedColor == color,
                    onClick = { onColorSelected(color) },
                )
            }
        }

        Text(
            text = "圆球点阵模式下，轨迹至少移动 10cm 才会再落下一颗球。",
            style = PicoTheme.typography.bodyMedium,
        )

        Button(onClick = onClear) {
            Text(text = "Clear Stroke")
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
        Text(text = if (selected) "[$label]" else label)
    }
}

private class PaintSceneState {
    private val sceneRoot = Entity().apply { setName("PaintSceneRoot") }
    private val leftInput = HandDrawInputState(HandSide.Left)
    private val rightInput = HandDrawInputState(HandSide.Right)
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
    private var currentBrushConfig =
        BrushConfig(
            color = BrushColorPreset.Coral.color,
            radius = BrushSizePreset.Medium.radius,
            mode = BrushMode.Tube,
        )

    private var attached = false

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

        if (handTrackingData.left == null) {
            leftInput.reset()
        }
        if (handTrackingData.right == null) {
            rightInput.reset()
        }

        leftInput.updateLongPinchState(handTrackingData.left, now)
        rightInput.updateLongPinchState(handTrackingData.right, now)

        leftStroke.update(handTrackingData.left, sceneRoot, leftInput.shouldDraw)
        rightStroke.update(handTrackingData.right, sceneRoot, rightInput.shouldDraw)
    }

    fun setBrushConfig(config: BrushConfig) {
        currentBrushConfig = config
        leftStroke.setBrushConfig(config)
        rightStroke.setBrushConfig(config)
    }

    fun clear() {
        leftStroke.clear()
        rightStroke.clear()
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
            color = BrushColorPreset.Coral.color,
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
    ) {
        if (uiPressed || handPose == null) {
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
}

private enum class BrushSizePreset(
    val label: String,
    val radius: Float,
) {
    Small("细", 0.0055f),
    Medium("中", 0.008f),
    Large("粗", 0.012f),
}

private enum class BrushColorPreset(
    val label: String,
    val color: Color4,
) {
    Coral("珊瑚", Color4.fromLinearHex("0xff6b57ff")),
    Sky("天蓝", Color4.fromLinearHex("0x4e8cffFF")),
    Mint("薄荷", Color4.fromLinearHex("0x47d7acff")),
    Gold("金色", Color4.fromLinearHex("0xf6c445ff")),
    Violet("紫色", Color4.fromLinearHex("0xa779ffff")),
    White("白色", Color4.fromLinearHex("0xf5f7ffff")),
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

private const val PINCH_START_DISTANCE_M = 0.02f
private const val PINCH_RELEASE_DISTANCE_M = 0.03f
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
private const val CONTROL_PANEL_ATTACHMENT_ID = "brush_control_panel"
private val CONTROL_PANEL_POSITION = Vector3(0.34f, 1.30f, -0.58f)
private const val CONTROL_PANEL_SCALE = 1.0f
private const val UV_REPEAT_DISTANCE_M = 0.03f
private const val EPSILON_M = 0.0001f
private const val TAU_F = (PI * 2.0).toFloat()
