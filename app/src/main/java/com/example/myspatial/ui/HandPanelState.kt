package com.example.myspatial.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hand.HandJoint
import com.pico.spatial.tracking.hand.HandPose
import kotlin.math.atan2

internal class HandPanelState(
    private val handSide: HandSide,
    initialExpandedPosition: Vector3,
) {
    var collapsed by mutableStateOf(false)
        private set

    private var currentPosition = initialExpandedPosition
    private var expandedPosition = initialExpandedPosition
    private var currentRotation = Quat(Vector3.UP, 0f)
    private var expandedRotation = Quat(Vector3.UP, 0f)
    private var currentScale = CONTROL_PANEL_SCALE
    private var expandGestureLatched = false
    private var initializedFacingHead = false

    fun collapse() {
        collapsed = true
    }

    fun initializeFacingHead(headWorldPosition: Vector3?) {
        if (initializedFacingHead || headWorldPosition == null) return
        expandedRotation = faceHeadYawQuaternion(expandedPosition, headWorldPosition)
        currentRotation = expandedRotation
        initializedFacingHead = true
    }

    fun applyTransform(panel: Entity) {
        panel.components.set(
            TransformComponent()
                .setPosition(currentPosition)
                .setQuaternion(currentRotation)
                .setScaleVector(Vector3(currentScale)),
        )
    }

    fun update(
        panel: Entity?,
        handPose: HandPose?,
        headWorldPosition: Vector3?,
        worldToScene: (Vector3) -> Vector3,
    ) {
        panel ?: return
        initializeFacingHead(headWorldPosition)

        val toggleGestureActive =
            handPose?.thumbLittleDistance()?.let { it <= PANEL_EXPAND_GESTURE_DISTANCE_M } == true
        if (toggleGestureActive && !expandGestureLatched) {
            if (collapsed) {
                handPose?.let { expandedHandPose ->
                    val expandedWorldPosition = expandedHandPose.expandedPanelWorldPosition()
                    expandedPosition = worldToScene(expandedWorldPosition)
                    expandedRotation = faceHeadYawQuaternion(expandedWorldPosition, headWorldPosition)
                    currentRotation = expandedRotation
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
        currentRotation = expandedRotation
        currentScale = lerp(currentScale, targetScale, PANEL_TRANSFORM_LERP)

        panel.enabled = !collapsed || handPose != null
        applyTransform(panel)
    }
}

internal fun applyFixedPanelTransform(
    panel: Entity,
    position: Vector3,
) {
    panel.components.set(
        TransformComponent()
            .setPosition(position)
            .setScaleVector(Vector3(CONTROL_PANEL_SCALE)),
    )
}

internal fun HandPose.thumbLittleDistance(): Float {
    val thumbTip = this[HandJoint.Index.THUMB_TIP]
    val littleTip = this[HandJoint.Index.LITTLE_TIP]
    return Vector3.distance(thumbTip.position, littleTip.position)
}

private fun HandPose.panelForwardDirection(): Vector3 {
    val wrist = this[HandJoint.Index.WRIST].position
    val palm = this[HandJoint.Index.PALM].position
    val middle = this[HandJoint.Index.MIDDLE_PROXIMAL].position
    return safeNormalizeOrFallback((palm - wrist) + (middle - palm), Vector3.FORWARD)
}

private fun HandPose.collapsedOrbWorldPosition(): Vector3 {
    val palm = this[HandJoint.Index.PALM].position
    return palm + panelForwardDirection() * PANEL_ORB_FORWARD_OFFSET_M
}

private fun HandPose.expandedPanelWorldPosition(): Vector3 {
    val palm = this[HandJoint.Index.PALM].position
    return palm + panelForwardDirection() * PANEL_EXPANDED_FORWARD_OFFSET_M + Vector3.UP * PANEL_EXPANDED_VERTICAL_OFFSET_M
}

private fun safeNormalizeOrFallback(
    vector: Vector3,
    fallback: Vector3,
): Vector3 {
    val length = vector.length()
    return if (length > 0.0001f) vector / length else fallback
}

private fun lerp(
    start: Float,
    end: Float,
    fraction: Float,
): Float = start + (end - start) * fraction.coerceIn(0f, 1f)

private fun faceHeadYawQuaternion(
    panelWorldPosition: Vector3,
    headWorldPosition: Vector3?,
): Quat {
    val lookDirection =
        headWorldPosition?.let { head ->
            val horizontal = Vector3(head.x - panelWorldPosition.x, 0f, head.z - panelWorldPosition.z)
            safeNormalizeOrFallback(horizontal, Vector3(0f, 0f, 1f))
        } ?: Vector3(0f, 0f, 1f)
    val yawRadians = atan2(lookDirection.x, lookDirection.z)
    return Quat(Vector3.UP, yawRadians)
}
