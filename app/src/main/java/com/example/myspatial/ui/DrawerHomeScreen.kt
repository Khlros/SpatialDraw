package com.example.myspatial.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo
import com.pico.spatial.ui.foundation.gesture.data.InteractionKind
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val hmdTrackingProvider = remember { HMDTrackingProvider() }
    val projectStorage = remember(context.applicationContext) { DrawingProjectStorage(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
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
    var leftOpacity by remember { mutableStateOf(DEFAULT_BRUSH_OPACITY) }
    var leftBrushRadius by remember { mutableStateOf(DEFAULT_BRUSH_RADIUS_M) }
    var leftSelectedMode by remember { mutableStateOf(BrushMode.Tube) }
    var leftSelectedMaterial by remember { mutableStateOf(BrushMaterialPreset.Metal) }
    var rightSelectedColor by remember { mutableStateOf(DEFAULT_RIGHT_BRUSH_COLOR) }
    var rightColorMode by remember { mutableStateOf(BrushColorMode.PureColor) }
    var rightTextureSelection by remember { mutableStateOf<TextureCropSelection?>(null) }
    var rightOpacity by remember { mutableStateOf(DEFAULT_BRUSH_OPACITY) }
    var rightBrushRadius by remember { mutableStateOf(DEFAULT_BRUSH_RADIUS_M) }
    var rightSelectedMode by remember { mutableStateOf(BrushMode.Tube) }
    var rightSelectedMaterial by remember { mutableStateOf(BrushMaterialPreset.Metal) }
    var texturePickerTargetHand by remember { mutableStateOf<HandSide?>(null) }
    var projectBrowserVisible by remember { mutableStateOf(false) }
    var projectPanelMode by remember { mutableStateOf(ProjectPanelMode.Open) }
    var currentProjectFile by remember { mutableStateOf<SavedProjectFile?>(null) }
    var savePathInput by remember { mutableStateOf(projectStorage.buildSuggestedProjectPath()) }
    var projectNotice by remember { mutableStateOf<ProjectOperationNotice?>(null) }
    var savedProjects by remember { mutableStateOf(projectStorage.listProjects()) }
    val effectiveSavePathInput =
        remember(savePathInput) {
            savePathInput.ifBlank { projectStorage.buildSuggestedProjectPath() }
        }
    val normalizedSavePath =
        remember(effectiveSavePathInput) {
            projectStorage.normalizeProjectRelativePathInput(effectiveSavePathInput)
        }
    val existingSaveTarget =
        remember(savedProjects, normalizedSavePath) {
            savedProjects.firstOrNull { it.relativePath == normalizedSavePath }
        }
    val leftPanelUiState =
        HandBrushPanelUiState(
            title = "左手画笔",
            collapsed = leftPanelState.collapsed,
            selectedColor = leftSelectedColor,
            brushRadius = leftBrushRadius,
            selectedMode = leftSelectedMode,
            selectedMaterial = leftSelectedMaterial,
            opacity = leftOpacity,
            selectedColorMode = leftColorMode,
            textureSelection = leftTextureSelection,
        )
    val rightPanelUiState =
        HandBrushPanelUiState(
            title = "右手画笔",
            collapsed = rightPanelState.collapsed,
            selectedColor = rightSelectedColor,
            brushRadius = rightBrushRadius,
            selectedMode = rightSelectedMode,
            selectedMaterial = rightSelectedMaterial,
            opacity = rightOpacity,
            selectedColorMode = rightColorMode,
            textureSelection = rightTextureSelection,
        )

    paintScene.setBrushConfig(
        handSide = HandSide.Left,
        config =
            BrushConfig(
                color = leftSelectedColor.uiColor.copy(alpha = leftOpacity).toLinearColor4(),
                displayColor = leftSelectedColor,
                colorMode = leftColorMode,
                textureSelection = leftTextureSelection,
                opacity = leftOpacity,
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
                opacity = leftOpacity,
                radius = leftBrushRadius,
                mode = leftSelectedMode,
                materialPreset = leftSelectedMaterial,
            ),
    )
    paintScene.setBrushConfig(
        handSide = HandSide.Right,
        config =
            BrushConfig(
                color = rightSelectedColor.uiColor.copy(alpha = rightOpacity).toLinearColor4(),
                displayColor = rightSelectedColor,
                colorMode = rightColorMode,
                textureSelection = rightTextureSelection,
                opacity = rightOpacity,
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
                opacity = rightOpacity,
                radius = rightBrushRadius,
                mode = rightSelectedMode,
                materialPreset = rightSelectedMaterial,
            ),
    )
    val refreshProjectFiles = {
        savedProjects = projectStorage.listProjects()
    }
    val startProjectNotice: (ProjectOperationNotice, Long?) -> Unit = { notice, autoDismissMs ->
        coroutineScope.launch {
            projectNotice = notice
            autoDismissMs?.let {
                delay(it)
                if (projectNotice == notice) {
                    projectNotice = null
                }
            }
        }
    }
    val openSavePanel = {
        refreshProjectFiles()
        projectPanelMode = ProjectPanelMode.Save
        savePathInput = currentProjectFile?.relativePathWithoutExtension ?: projectStorage.buildSuggestedProjectPath()
        texturePickerTargetHand = null
        projectBrowserVisible = true
    }
    val openProjectBrowser = {
        refreshProjectFiles()
        projectPanelMode = ProjectPanelMode.Open
        texturePickerTargetHand = null
        projectBrowserVisible = true
    }
    val saveCurrentProject = {
        val sceneSnapshot = paintScene.exportDrawingSnapshot()
        val document =
            DrawingProjectDocument(
                leftHandState =
                    snapshotBrushState(
                        color = leftSelectedColor,
                        colorMode = leftColorMode,
                        textureSelection = leftTextureSelection,
                        opacity = leftOpacity,
                        radius = leftBrushRadius,
                        mode = leftSelectedMode,
                        materialPreset = leftSelectedMaterial,
                    ),
                rightHandState =
                    snapshotBrushState(
                        color = rightSelectedColor,
                        colorMode = rightColorMode,
                        textureSelection = rightTextureSelection,
                        opacity = rightOpacity,
                        radius = rightBrushRadius,
                        mode = rightSelectedMode,
                        materialPreset = rightSelectedMaterial,
                    ),
                leftStrokes = sceneSnapshot.leftStrokes,
                rightStrokes = sceneSnapshot.rightStrokes,
            )
        coroutineScope.launch {
            projectNotice =
                ProjectOperationNotice(
                    title = "正在保存",
                    detail = normalizedSavePath,
                    busy = true,
                )
            delay(120)
            runCatching {
                withContext(Dispatchers.IO) {
                    projectStorage.saveProject(
                        document = document,
                        relativePath = effectiveSavePathInput,
                        existingFile = existingSaveTarget?.file,
                    )
                }
            }.onSuccess { savedProject ->
                currentProjectFile = savedProject
                refreshProjectFiles()
                projectBrowserVisible = false
                startProjectNotice(
                    ProjectOperationNotice(
                        title = "保存完成",
                        detail = savedProject.relativePath,
                        busy = false,
                    ),
                    1600L,
                )
            }.onFailure {
                startProjectNotice(
                    ProjectOperationNotice(
                        title = "保存失败",
                        detail = it.message ?: "请检查路径后重试",
                        busy = false,
                    ),
                    2200L,
                )
            }
        }
    }
    val loadProjectDocument: (SavedProjectFile) -> Unit = { savedProject ->
        coroutineScope.launch {
            projectNotice =
                ProjectOperationNotice(
                    title = "正在打开",
                    detail = savedProject.relativePath,
                    busy = true,
                )
            delay(180)
            runCatching {
                withContext(Dispatchers.IO) {
                    projectStorage.loadProject(savedProject.file)
                }
            }.onSuccess { document ->
                leftSelectedColor = document.leftHandState.selectedColor
                leftColorMode = document.leftHandState.colorMode
                leftTextureSelection = document.leftHandState.textureSelection
                leftOpacity = document.leftHandState.opacity
                leftBrushRadius = document.leftHandState.radius
                leftSelectedMode = document.leftHandState.mode
                leftSelectedMaterial = document.leftHandState.materialPreset

                rightSelectedColor = document.rightHandState.selectedColor
                rightColorMode = document.rightHandState.colorMode
                rightTextureSelection = document.rightHandState.textureSelection
                rightOpacity = document.rightHandState.opacity
                rightBrushRadius = document.rightHandState.radius
                rightSelectedMode = document.rightHandState.mode
                rightSelectedMaterial = document.rightHandState.materialPreset

                paintScene.loadDrawingSnapshot(
                    DrawingSceneSnapshot(
                        leftStrokes = document.leftStrokes,
                        rightStrokes = document.rightStrokes,
                    ),
                )
                currentProjectFile = savedProject
                savePathInput = savedProject.relativePathWithoutExtension
                texturePickerTargetHand = null
                projectBrowserVisible = false
                leftPanelInteracting = false
                rightPanelInteracting = false
                refreshProjectFiles()
                startProjectNotice(
                    ProjectOperationNotice(
                        title = "打开完成",
                        detail = savedProject.relativePath,
                        busy = false,
                    ),
                    1600L,
                )
            }.onFailure {
                startProjectNotice(
                    ProjectOperationNotice(
                        title = "打开失败",
                        detail = it.message ?: "请稍后重试",
                        busy = false,
                    ),
                    2200L,
                )
            }
        }
    }

    paintScene.setPanelInteractionActive(
        leftPanelInteracting || rightPanelInteracting || texturePickerTargetHand != null || projectBrowserVisible,
    )

    DisposableEffect(handTrackingProvider, hmdTrackingProvider) {
        handTrackingProvider.start()
        hmdTrackingProvider.start()
        onDispose {
            handTrackingProvider.stop()
            hmdTrackingProvider.stop()
        }
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
                    leftPanelState.initializeFacingHead(hmdTrackingProvider.latestData.hmdPose.position)
                    leftPanelState.applyTransform(panel)
                    content.addEntity(panel)
                }
                attachments.entity(RIGHT_CONTROL_PANEL_ATTACHMENT_ID)?.let { panel ->
                    rightPanelState.initializeFacingHead(hmdTrackingProvider.latestData.hmdPose.position)
                    rightPanelState.applyTransform(panel)
                    content.addEntity(panel)
                }
                attachments.entity(TEXTURE_PICKER_ATTACHMENT_ID)?.let { panel ->
                    applyFixedPanelTransform(panel, TEXTURE_PICKER_PANEL_POSITION)
                    panel.enabled = false
                    content.addEntity(panel)
                }
                attachments.entity(PROJECT_BROWSER_ATTACHMENT_ID)?.let { panel ->
                    applyFixedPanelTransform(panel, PROJECT_BROWSER_PANEL_POSITION)
                    panel.enabled = false
                    content.addEntity(panel)
                }
            },
            attachments = {
                AttachmentPanel(
                    id = LEFT_CONTROL_PANEL_ATTACHMENT_ID,
                ) {
                    HandBrushPanel(
                        state = leftPanelUiState,
                        onAction = { action ->
                            when (action) {
                                is HandBrushPanelAction.SelectBrushMode -> leftSelectedMode = action.mode
                                is HandBrushPanelAction.SelectColor -> leftSelectedColor = action.color
                                is HandBrushPanelAction.SelectColorMode -> leftColorMode = action.mode
                                is HandBrushPanelAction.SelectMaterial -> leftSelectedMaterial = action.material
                                is HandBrushPanelAction.SetBrushRadius -> leftBrushRadius = action.radius
                                is HandBrushPanelAction.SetInteractionActive -> leftPanelInteracting = action.active
                                is HandBrushPanelAction.SetOpacity -> leftOpacity = action.opacity
                                HandBrushPanelAction.ClearCanvas -> paintScene.clear()
                                HandBrushPanelAction.SaveProject -> openSavePanel()
                                HandBrushPanelAction.OpenProjectBrowser -> {
                                    openProjectBrowser()
                                }
                                HandBrushPanelAction.Collapse -> {
                                    leftPanelInteracting = false
                                    leftPanelState.collapse()
                                }
                                HandBrushPanelAction.RequestTextureUpload -> texturePickerTargetHand = HandSide.Left
                                HandBrushPanelAction.Undo -> paintScene.undoLastStroke(HandSide.Left)
                            }
                        },
                    )
                }
                AttachmentPanel(
                    id = RIGHT_CONTROL_PANEL_ATTACHMENT_ID,
                ) {
                    HandBrushPanel(
                        state = rightPanelUiState,
                        onAction = { action ->
                            when (action) {
                                is HandBrushPanelAction.SelectBrushMode -> rightSelectedMode = action.mode
                                is HandBrushPanelAction.SelectColor -> rightSelectedColor = action.color
                                is HandBrushPanelAction.SelectColorMode -> rightColorMode = action.mode
                                is HandBrushPanelAction.SelectMaterial -> rightSelectedMaterial = action.material
                                is HandBrushPanelAction.SetBrushRadius -> rightBrushRadius = action.radius
                                is HandBrushPanelAction.SetInteractionActive -> rightPanelInteracting = action.active
                                is HandBrushPanelAction.SetOpacity -> rightOpacity = action.opacity
                                HandBrushPanelAction.ClearCanvas -> paintScene.clear()
                                HandBrushPanelAction.SaveProject -> openSavePanel()
                                HandBrushPanelAction.OpenProjectBrowser -> {
                                    openProjectBrowser()
                                }
                                HandBrushPanelAction.Collapse -> {
                                    rightPanelInteracting = false
                                    rightPanelState.collapse()
                                }
                                HandBrushPanelAction.RequestTextureUpload -> texturePickerTargetHand = HandSide.Right
                                HandBrushPanelAction.Undo -> paintScene.undoLastStroke(HandSide.Right)
                            }
                        },
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
                AttachmentPanel(
                    id = PROJECT_BROWSER_ATTACHMENT_ID,
                ) {
                    ProjectBrowserAttachmentPanel(
                        mode = projectPanelMode,
                        projects = savedProjects,
                        currentProject = currentProjectFile,
                        savePathInput = savePathInput,
                        existingSaveTarget = existingSaveTarget,
                        onSavePathInputChange = { savePathInput = it },
                        onSaveProject = { saveCurrentProject() },
                        onRefresh = refreshProjectFiles,
                        onOpenProject = loadProjectDocument,
                        onUseProjectPath = { project -> savePathInput = project.relativePathWithoutExtension },
                        onDismiss = { projectBrowserVisible = false },
                    )
                }
            },
            update = { _, attachments ->
                leftPanelState.update(
                    panel = attachments.entity(LEFT_CONTROL_PANEL_ATTACHMENT_ID),
                    handPose = handTrackingData.left,
                    headWorldPosition = hmdTrackingProvider.latestData.hmdPose.position,
                    worldToScene = { point -> paintScene.worldToSceneLocal(point) },
                )
                rightPanelState.update(
                    panel = attachments.entity(RIGHT_CONTROL_PANEL_ATTACHMENT_ID),
                    handPose = handTrackingData.right,
                    headWorldPosition = hmdTrackingProvider.latestData.hmdPose.position,
                    worldToScene = { point -> paintScene.worldToSceneLocal(point) },
                )
                attachments.entity(TEXTURE_PICKER_ATTACHMENT_ID)?.let { panel ->
                    panel.enabled = texturePickerTargetHand != null
                    if (panel.enabled) {
                        applyFixedPanelTransform(panel, TEXTURE_PICKER_PANEL_POSITION)
                    }
                }
                attachments.entity(PROJECT_BROWSER_ATTACHMENT_ID)?.let { panel ->
                    panel.enabled = projectBrowserVisible
                    if (panel.enabled) {
                        applyFixedPanelTransform(panel, PROJECT_BROWSER_PANEL_POSITION)
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ProjectOperationOverlay(notice = projectNotice)
        }
    }
}

private fun snapshotBrushState(
    color: BrushColorState,
    colorMode: BrushColorMode,
    textureSelection: TextureCropSelection?,
    opacity: Float,
    radius: Float,
    mode: BrushMode,
    materialPreset: BrushMaterialPreset,
): DrawingBrushStateSnapshot =
    DrawingBrushStateSnapshot(
        selectedColor = color,
        colorMode = colorMode,
        textureSelection = textureSelection,
        opacity = opacity,
        radius = radius,
        mode = mode,
        materialPreset = materialPreset,
    )
