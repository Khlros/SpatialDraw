package com.example.myspatial.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.input.pointer.PointerId
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
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal class PaintSceneState {
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

internal data class BrushPreviewStyle(
    val color: BrushColorState,
    val colorMode: BrushColorMode,
    val textureSelection: TextureCropSelection?,
    val opacity: Float,
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
                    opacity = style.opacity,
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
                opacity = style.opacity,
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
                    opacity = style.opacity,
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
                    opacity = style.opacity,
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
            opacity = DEFAULT_BRUSH_OPACITY,
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
                                opacity = brushConfig.opacity,
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
                                opacity = brushConfig.opacity,
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
                                opacity = brushConfig.opacity,
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
                                opacity = brushConfig.opacity,
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

internal data class BrushConfig(
    val color: Color4,
    val displayColor: BrushColorState,
    val colorMode: BrushColorMode,
    val textureSelection: TextureCropSelection?,
    val opacity: Float,
    val radius: Float,
    val mode: BrushMode,
    val materialPreset: BrushMaterialPreset,
)

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
    return lerpScalar(
        THIN_STROKE_SCALE,
        1f,
        smoothstep(distance / END_TRANSITION_DISTANCE_M),
    )
}

private fun hemisphereBlend(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return sqrt((1f - (1f - t) * (1f - t)).coerceAtLeast(0f))
}

private fun lerpScalar(
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
    opacity: Float,
    materialPreset: BrushMaterialPreset,
): Material {
    val resolvedOpacity = opacity.coerceIn(0.05f, 1f)
    val blendingMode = if (resolvedOpacity < 0.995f) BlendingMode.TRANSPARENT else BlendingMode.OPAQUE
    val baseColor =
        when (materialPreset) {
            BrushMaterialPreset.Metal -> mixColors(color.uiColor, Color(0xFFB8BDC7), 0.18f)
            BrushMaterialPreset.Fabric -> mixColors(color.uiColor, Color(0xFF6C7078), 0.24f)
            BrushMaterialPreset.Plastic -> color.uiColor
            BrushMaterialPreset.Unlit -> color.uiColor
        }.copy(alpha = resolvedOpacity)
    val textureBitmap =
        if (colorMode == BrushColorMode.Texture) {
            textureSelection?.toTextureBitmap()
        } else {
            null
        }
    if (materialPreset == BrushMaterialPreset.Unlit) {
        val material =
            UnlitMaterial.create(blendingMode)
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
        return material
    }
    val material =
        PhysicallyBasedMaterial.create(blendingMode)
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
        BrushMaterialPreset.Unlit -> Unit
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

private fun mixColors(
    a: Color,
    b: Color,
    t: Float,
): Color {
    val clampedT = t.coerceIn(0f, 1f)
    return Color(
        red = lerpScalar(a.red, b.red, clampedT),
        green = lerpScalar(a.green, b.green, clampedT),
        blue = lerpScalar(a.blue, b.blue, clampedT),
        alpha = lerpScalar(a.alpha, b.alpha, clampedT),
    )
}

internal fun Color.toLinearColor4(): Color4 {
    val linearColor = convert(ColorSpaces.LinearSrgb)
    return Color4(linearColor.red, linearColor.green, linearColor.blue, linearColor.alpha)
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

private fun HandPose.thumbMiddleDistance(): Float {
    val thumbTip = this[HandJoint.Index.THUMB_TIP]
    val middleTip = this[HandJoint.Index.MIDDLE_TIP]
    return Vector3.distance(thumbTip.position, middleTip.position)
}

private fun HandPose.pinchDistance(): Float {
    val indexTip = this[HandJoint.Index.INDEX_TIP]
    val thumbTip = this[HandJoint.Index.THUMB_TIP]
    return Vector3.distance(indexTip.position, thumbTip.position)
}

private fun com.pico.spatial.ui.geometry.Offset3D.toVector3(): Vector3 = Vector3(x, y, z)

private const val PINCH_START_DISTANCE_M = 0.018f
private const val PINCH_RELEASE_DISTANCE_M = 0.026f
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
private val BRUSH_PREVIEW_LOCAL_POSITION = Vector3(-0.03f, 0.07f, 0.015f)
private val BRUSH_PREVIEW_POINTS =
    List(BRUSH_PREVIEW_CONTROL_POINT_COUNT) { index ->
        val t = index.toFloat() / (BRUSH_PREVIEW_CONTROL_POINT_COUNT - 1).toFloat()
        val x = lerpScalar(-BRUSH_PREVIEW_HALF_WIDTH_M, BRUSH_PREVIEW_HALF_WIDTH_M, t)
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
private const val UI_POINTER_DRAW_BLOCK_HOLD_MS = 180L
private const val UV_REPEAT_DISTANCE_M = 0.03f
private const val EPSILON_M = 0.0001f
private const val TAU_F = (PI * 2.0).toFloat()
