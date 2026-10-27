package com.example.myspatial.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.res.stringResource
import com.example.myspatial.R
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size as AndroidSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.hover.tween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun TexturePickerAttachmentPanel(
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
                        draftSelection != null && handSide == HandSide.Left -> stringResource(R.string.left_texture_crop)
                        draftSelection != null && handSide == HandSide.Right -> stringResource(R.string.right_texture_crop)
                        handSide == HandSide.Left -> stringResource(R.string.left_texture_images)
                        else -> stringResource(R.string.right_texture_images)
                    },
                style = PicoTheme.typography.titleLarge,
                color = Color.White,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draftSelection != null) {
                    Button(onClick = { draftSelection = null }) {
                        Text(text = stringResource(R.string.back))
                    }
                }
                Button(onClick = onDismiss) {
                    Text(text = stringResource(R.string.close))
                }
            }
        }

        when {
            !permissionGranted && permissionDenied -> {
                TextureOverlayState(
                    text = stringResource(R.string.photo_permission_required),
                    actionText = stringResource(R.string.grant_permission),
                    onAction = {
                        permissionDenied = false
                        permissionLauncher.launch(permission)
                    },
                )
            }
            !permissionGranted -> {
                TextureOverlayState(
                    text = stringResource(R.string.requesting_photo_permission),
                    actionText = stringResource(R.string.close),
                    onAction = onDismiss,
                )
            }
            imageItems == null -> {
                TextureOverlayState(
                    text = stringResource(R.string.loading_photos),
                    actionText = stringResource(R.string.close),
                    onAction = onDismiss,
                )
            }
            imageItems.isNullOrEmpty() -> {
                TextureOverlayState(
                    text = stringResource(R.string.no_photos),
                    actionText = stringResource(R.string.close),
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
                text = stringResource(R.string.crop_instructions),
                    style = PicoTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Text(
                text = stringResource(R.string.crop_size, cropRect.size, selection.sourceBitmap.minCropSizePx()),
                    style = PicoTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.62f),
                )
            }
            Button(onClick = onConfirm) {
            Text(text = stringResource(R.string.use_crop))
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
            Image(
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
            Text(text = stringResource(R.string.image), color = Color.White.copy(alpha = 0.75f))
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
            val name = cursor.getString(nameIndex) ?: context.getString(R.string.unnamed_image)
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

private fun loadTextureBitmap(
    context: Context,
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
