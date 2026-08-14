package com.example.myspatial.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text

internal data class ProjectOperationNotice(
    val title: String,
    val detail: String,
    val busy: Boolean,
)

@Composable
internal fun ProjectBrowserAttachmentPanel(
    mode: ProjectPanelMode,
    projects: List<SavedProjectFile>,
    currentProject: SavedProjectFile?,
    savePathInput: String,
    existingSaveTarget: SavedProjectFile?,
    onSavePathInputChange: (String) -> Unit,
    onSaveProject: () -> Unit,
    onRefresh: () -> Unit,
    onOpenProject: (SavedProjectFile) -> Unit,
    onUseProjectPath: (SavedProjectFile) -> Unit,
    onDismiss: () -> Unit,
) {
    val panelShape = RoundedCornerShape(28.dp)
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .width(PROJECT_BROWSER_PANEL_WIDTH_DP)
                .height(PROJECT_BROWSER_PANEL_HEIGHT_DP)
                .background(Color(0xE61B1D24), panelShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.12f), panelShape)
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.width(PROJECT_BROWSER_PANEL_WIDTH_DP - 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (mode == ProjectPanelMode.Save) "保存作品" else "打开作品",
                style = PicoTheme.typography.titleLarge,
                color = Color.White,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onRefresh) {
                    Text(text = "刷新")
                }
                Button(onClick = onDismiss) {
                    Text(text = "关闭")
                }
            }
        }

        if (mode == ProjectPanelMode.Save) {
            SaveProjectSection(
                savePathInput = savePathInput,
                currentProject = currentProject,
                existingSaveTarget = existingSaveTarget,
                onSavePathInputChange = onSavePathInputChange,
                onSaveProject = onSaveProject,
            )
        } else {
            Text(
                text = "作品保存在应用私有目录里。打开后，之前保存的每一笔都还能继续撤销。",
                style = PicoTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (projects.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .width(PROJECT_BROWSER_PANEL_WIDTH_DP - 36.dp)
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                            .padding(16.dp),
                ) {
                    Text(
                        text =
                            if (mode == ProjectPanelMode.Save) {
                                "还没有存档。直接输入路径后点一次“保存”。"
                            } else {
                                "还没有存档。先在画笔面板里点一次“保存”。"
                            },
                        style = PicoTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }
            } else {
                projects.forEach { project ->
                    ProjectFileRow(
                        project = project,
                        currentProject = currentProject,
                        actionLabel = if (mode == ProjectPanelMode.Save) "使用" else "打开",
                        onAction = {
                            if (mode == ProjectPanelMode.Save) {
                                onUseProjectPath(project)
                            } else {
                                onOpenProject(project)
                            }
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SaveProjectSection(
    savePathInput: String,
    currentProject: SavedProjectFile?,
    existingSaveTarget: SavedProjectFile?,
    onSavePathInputChange: (String) -> Unit,
    onSaveProject: () -> Unit,
) {
    val sectionShape = RoundedCornerShape(18.dp)
    Column(
        modifier =
            Modifier
                .width(PROJECT_BROWSER_PANEL_WIDTH_DP - 36.dp)
                .background(Color.White.copy(alpha = 0.05f), sectionShape)
                .border(1.dp, Color.White.copy(alpha = 0.10f), sectionShape)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "保存路径（相对 drawings 目录，可写子目录）",
            style = PicoTheme.typography.titleMedium,
            color = Color.White,
        )
        ProjectPathInput(
            value = savePathInput,
            onValueChange = onSavePathInputChange,
        )
        Text(
            text = "例如：room/demo01 或 sketches/0814/test",
            style = PicoTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.70f),
        )
        currentProject?.let { project ->
            Text(
                text = "当前打开：${project.relativePathWithoutExtension}",
                style = PicoTheme.typography.bodyMedium,
                color = Color(0xFF8CC4FF),
            )
        }
        existingSaveTarget?.let { project ->
            Text(
                text = "将覆盖：${project.relativePath}",
                style = PicoTheme.typography.bodyMedium,
                color = Color(0xFFFFD58F),
            )
        }
        Button(onClick = onSaveProject) {
            Text(
                text =
                    if (existingSaveTarget != null) {
                        "覆盖保存"
                    } else {
                        "保存到此路径"
                    },
            )
        }
    }
}

@Composable
private fun ProjectPathInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(Color.White),
        textStyle = TextStyle(color = Color.White),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0x33171A21), shape)
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isBlank()) {
                    Text(
                        text = "输入文件名或子目录/文件名",
                        style = PicoTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.42f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun ProjectFileRow(
    project: SavedProjectFile,
    currentProject: SavedProjectFile?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val rowShape = RoundedCornerShape(18.dp)
    val isCurrent = currentProject?.file == project.file
    Row(
        modifier =
            Modifier
                .width(PROJECT_BROWSER_PANEL_WIDTH_DP - 36.dp)
                .background(
                    if (isCurrent) Color(0x1F4E8CFF) else Color.White.copy(alpha = 0.06f),
                    rowShape,
                ).border(
                    1.dp,
                    if (isCurrent) Color(0xFF8CC4FF) else Color.White.copy(alpha = 0.10f),
                    rowShape,
                ).clickable(onClick = onAction)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(290.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = project.displayName,
                style = PicoTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = project.relativePath,
                style = PicoTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
            )
            Text(
                text = project.formattedSubtitle(),
                style = PicoTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.60f),
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isCurrent) {
                Text(
                    text = "当前",
                    style = PicoTheme.typography.bodyMedium,
                    color = Color(0xFF8CC4FF),
                )
            }
            Button(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
internal fun ProjectOperationOverlay(
    notice: ProjectOperationNotice?,
) {
    if (notice == null) {
        return
    }
    val alpha =
        animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180),
            label = "project_notice_alpha",
        )
    val panelShape = RoundedCornerShape(24.dp)
    Box(
        modifier =
            Modifier
                .width(300.dp)
                .background(Color(0xEE11131A).copy(alpha = alpha.value), panelShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.16f * alpha.value), panelShape)
                .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (notice?.busy == true) {
                LoadingDots()
            }
            Text(
                text = notice?.title.orEmpty(),
                style = PicoTheme.typography.titleMedium,
                color = Color.White.copy(alpha = alpha.value),
            )
            Text(
                text = notice?.detail.orEmpty(),
                style = PicoTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f * alpha.value),
            )
        }
    }
}

@Composable
private fun LoadingDots() {
    val transition = rememberInfiniteTransition(label = "project_loading")
    val progress =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 780, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "project_loading_progress",
        )
    Canvas(modifier = Modifier.size(width = 56.dp, height = 12.dp)) {
        val radius = size.height * 0.28f
        val spacing = size.width / 3f
        repeat(3) { index ->
            val phase = ((progress.value + index * 0.18f) % 1f)
            drawCircle(
                color = Color.White.copy(alpha = 0.30f + (1f - phase) * 0.55f),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(spacing * (index + 0.5f), size.height / 2f),
            )
        }
    }
}
