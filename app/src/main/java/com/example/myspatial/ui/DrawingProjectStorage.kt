package com.example.myspatial.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pico.spatial.core.math.Vector3
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal data class DrawingBrushStateSnapshot(
    val selectedColor: BrushColorState,
    val colorMode: BrushColorMode,
    val textureSelection: TextureCropSelection?,
    val opacity: Float,
    val radius: Float,
    val mode: BrushMode,
    val materialPreset: BrushMaterialPreset,
)

internal data class SavedStrokeSnapshot(
    val brush: DrawingBrushStateSnapshot,
    val points: List<Vector3>,
    val growingSphereRadius: Float? = null,
)

internal data class DrawingSceneSnapshot(
    val leftStrokes: List<SavedStrokeSnapshot>,
    val rightStrokes: List<SavedStrokeSnapshot>,
)

internal data class DrawingProjectDocument(
    val leftHandState: DrawingBrushStateSnapshot,
    val rightHandState: DrawingBrushStateSnapshot,
    val leftStrokes: List<SavedStrokeSnapshot>,
    val rightStrokes: List<SavedStrokeSnapshot>,
    val savedAtEpochMs: Long = System.currentTimeMillis(),
)

internal data class SavedProjectFile(
    val file: File,
    val displayName: String,
    val relativePath: String,
    val relativePathWithoutExtension: String,
    val modifiedAtEpochMs: Long,
    val sizeBytes: Long,
)

internal class DrawingProjectStorage(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val drawingsDir =
        File(appContext.filesDir, DRAWING_PROJECT_DIRECTORY_NAME).apply {
            mkdirs()
        }

    fun listProjects(): List<SavedProjectFile> =
        drawingsDir
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == DRAWING_PROJECT_FILE_EXTENSION }
            .toList()
            .sortedByDescending { it.lastModified() }
            .map(::toSavedProjectFile)

    fun buildSuggestedProjectPath(epochMs: Long = System.currentTimeMillis()): String = buildProjectFileStem(epochMs)

    fun normalizeProjectRelativePathInput(relativePath: String): String = normalizeProjectRelativePath(relativePath)

    fun saveProject(
        document: DrawingProjectDocument,
        relativePath: String? = null,
        existingFile: File? = null,
    ): SavedProjectFile {
        val file =
            existingFile?.let(::requireFileInDrawingsDir)
                ?: resolveProjectFile(relativePath ?: buildSuggestedProjectPath(document.savedAtEpochMs))
        file.parentFile?.mkdirs()
        val textureRegistry = TextureAssetRegistry()
        val manifest = document.toJson(textureRegistry)
        ZipOutputStream(FileOutputStream(file).buffered()).use { zipOutput ->
            zipOutput.writeEntry(DRAWING_PROJECT_MANIFEST_PATH, manifest.toString(2).toByteArray(Charsets.UTF_8))
            textureRegistry.entries.forEach { (assetId, pngBytes) ->
                zipOutput.writeEntry("textures/$assetId.png", pngBytes)
            }
        }
        return toSavedProjectFile(file)
    }

    fun loadProject(projectFile: File): DrawingProjectDocument =
        ZipFile(projectFile).use { zipFile ->
            val manifestEntry =
                zipFile.getEntry(DRAWING_PROJECT_MANIFEST_PATH)
                    ?: error("Missing $DRAWING_PROJECT_MANIFEST_PATH in ${projectFile.name}")
            val manifest =
                zipFile.getInputStream(manifestEntry).bufferedReader().use { reader ->
                    JSONObject(reader.readText())
                }
            val textureBytes =
                buildMap {
                    zipFile.entries().asSequence().forEach { entry ->
                        if (entry.name.startsWith("textures/") && entry.name.endsWith(".png")) {
                            put(
                                entry.name.substringAfterLast('/').substringBeforeLast(".png"),
                                zipFile.getInputStream(entry).use { it.readBytes() },
                            )
                        }
                    }
                }
            manifest.toDocument(textureBytes)
        }

    private fun resolveProjectFile(relativePath: String): File =
        File(drawingsDir, normalizeProjectRelativePath(relativePath))

    private fun requireFileInDrawingsDir(file: File): File {
        val canonicalDrawingsDir = drawingsDir.canonicalFile
        val canonicalFile = file.canonicalFile
        require(canonicalFile.path.startsWith("${canonicalDrawingsDir.path}${File.separator}")) {
            "Project file must stay inside ${canonicalDrawingsDir.path}"
        }
        return canonicalFile
    }

    private fun normalizeProjectRelativePath(relativePath: String): String {
        val normalizedSegments =
            relativePath
                .trim()
                .replace('\\', '/')
                .split('/')
                .mapNotNull { segment ->
                    val cleaned =
                        segment
                            .trim()
                            .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_")
                            .trim('.')
                    cleaned.takeIf { it.isNotBlank() && it != "." && it != ".." }
                }
        val joinedPath =
            normalizedSegments
                .ifEmpty { listOf(buildProjectFileStem(System.currentTimeMillis())) }
                .joinToString(separator = "/")
        return if (joinedPath.endsWith(".$DRAWING_PROJECT_FILE_EXTENSION")) {
            joinedPath
        } else {
            "$joinedPath.$DRAWING_PROJECT_FILE_EXTENSION"
        }
    }

    private fun toSavedProjectFile(file: File): SavedProjectFile {
        val relativePath = file.relativeTo(drawingsDir).invariantSeparatorsPath
        return SavedProjectFile(
            file = file,
            displayName = file.nameWithoutExtension,
            relativePath = relativePath,
            relativePathWithoutExtension = relativePath.removeSuffix(".$DRAWING_PROJECT_FILE_EXTENSION"),
            modifiedAtEpochMs = file.lastModified(),
            sizeBytes = file.length(),
        )
    }
}

internal fun DrawingBrushStateSnapshot.toBrushConfig(): BrushConfig =
    BrushConfig(
        color = selectedColor.uiColor.copy(alpha = opacity).toLinearColor4(),
        displayColor = selectedColor,
        colorMode = colorMode,
        textureSelection = textureSelection,
        opacity = opacity,
        radius = radius,
        mode = mode,
        materialPreset = materialPreset,
    )

private fun DrawingProjectDocument.toJson(textureRegistry: TextureAssetRegistry): JSONObject =
    JSONObject()
        .put("format", DRAWING_PROJECT_FORMAT)
        .put("version", DRAWING_PROJECT_VERSION)
        .put("savedAtEpochMs", savedAtEpochMs)
        .put("leftHandState", leftHandState.toJson(textureRegistry))
        .put("rightHandState", rightHandState.toJson(textureRegistry))
        .put("leftStrokes", leftStrokes.toJson(textureRegistry))
        .put("rightStrokes", rightStrokes.toJson(textureRegistry))

private fun JSONObject.toDocument(textureBytes: Map<String, ByteArray>): DrawingProjectDocument {
    require(optString("format") == DRAWING_PROJECT_FORMAT) { "Unsupported drawing project format" }
    require(optInt("version") == DRAWING_PROJECT_VERSION) { "Unsupported drawing project version" }
    return DrawingProjectDocument(
        leftHandState = getJSONObject("leftHandState").toBrushStateSnapshot(textureBytes),
        rightHandState = getJSONObject("rightHandState").toBrushStateSnapshot(textureBytes),
        leftStrokes = getJSONArray("leftStrokes").toStrokeSnapshots(textureBytes),
        rightStrokes = getJSONArray("rightStrokes").toStrokeSnapshots(textureBytes),
        savedAtEpochMs = optLong("savedAtEpochMs", System.currentTimeMillis()),
    )
}

private fun List<SavedStrokeSnapshot>.toJson(textureRegistry: TextureAssetRegistry): JSONArray =
    JSONArray().apply {
        forEach { stroke -> put(stroke.toJson(textureRegistry)) }
    }

private fun JSONArray.toStrokeSnapshots(textureBytes: Map<String, ByteArray>): List<SavedStrokeSnapshot> =
    buildList(length()) {
        for (index in 0 until length()) {
            add(getJSONObject(index).toStrokeSnapshot(textureBytes))
        }
    }

private fun SavedStrokeSnapshot.toJson(textureRegistry: TextureAssetRegistry): JSONObject =
    JSONObject()
        .put("brush", brush.toJson(textureRegistry))
        .put(
            "points",
            JSONArray().apply {
                points.forEach { point ->
                    put(
                        JSONArray()
                            .put(point.x.toDouble())
                            .put(point.y.toDouble())
                            .put(point.z.toDouble()),
                    )
                }
            },
        ).apply {
            if (growingSphereRadius != null) {
                put("growingSphereRadius", growingSphereRadius.toDouble())
            }
        }

private fun JSONObject.toStrokeSnapshot(textureBytes: Map<String, ByteArray>): SavedStrokeSnapshot =
    SavedStrokeSnapshot(
        brush = getJSONObject("brush").toBrushStateSnapshot(textureBytes),
        points =
            getJSONArray("points").let { pointArray ->
                buildList(pointArray.length()) {
                    for (index in 0 until pointArray.length()) {
                        val point = pointArray.getJSONArray(index)
                        add(
                            Vector3(
                                point.getDouble(0).toFloat(),
                                point.getDouble(1).toFloat(),
                                point.getDouble(2).toFloat(),
                            ),
                        )
                    }
                }
            },
        growingSphereRadius =
            if (has("growingSphereRadius")) {
                getDouble("growingSphereRadius").toFloat()
            } else {
                null
            },
    )

private fun DrawingBrushStateSnapshot.toJson(textureRegistry: TextureAssetRegistry): JSONObject =
    JSONObject()
        .put(
            "color",
            JSONObject()
                .put("h", selectedColor.hue.toDouble())
                .put("s", selectedColor.saturation.toDouble())
                .put("v", selectedColor.value.toDouble()),
        ).put("colorMode", colorMode.name)
        .put("opacity", opacity.toDouble())
        .put("radius", radius.toDouble())
        .put("mode", mode.name)
        .put("materialPreset", materialPreset.name)
        .apply {
            textureRegistry.register(textureSelection)?.let { assetId ->
                put("textureAssetId", assetId)
            }
        }

private fun JSONObject.toBrushStateSnapshot(textureBytes: Map<String, ByteArray>): DrawingBrushStateSnapshot {
    val color = getJSONObject("color")
    val textureSelection =
        optString("textureAssetId").takeIf { it.isNotBlank() }?.let { assetId ->
            textureBytes[assetId]?.toTextureSelection()
        }
    return DrawingBrushStateSnapshot(
        selectedColor =
            BrushColorState(
                hue = color.getDouble("h").toFloat(),
                saturation = color.getDouble("s").toFloat(),
                value = color.getDouble("v").toFloat(),
            ),
        colorMode = BrushColorMode.valueOf(getString("colorMode")),
        textureSelection = textureSelection,
        opacity = getDouble("opacity").toFloat(),
        radius = getDouble("radius").toFloat(),
        mode = BrushMode.valueOf(getString("mode")),
        materialPreset = BrushMaterialPreset.valueOf(getString("materialPreset")),
    )
}

private fun buildProjectFileStem(epochMs: Long): String =
    "PicoDraw_${
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(epochMs))
    }"

private fun TextureCropSelection.toPersistedBitmap(): Bitmap? {
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

private fun ByteArray.toTextureSelection(): TextureCropSelection? {
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size) ?: return null
    return TextureCropSelection.createDefault(bitmap)
}

private class TextureAssetRegistry {
    val entries = linkedMapOf<String, ByteArray>()

    fun register(selection: TextureCropSelection?): String? {
        val bitmap = selection?.toPersistedBitmap() ?: return null
        val pngBytes =
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                bitmap.recycle()
                stream.toByteArray()
            }
        val assetId = pngBytes.sha256Hex()
        entries.putIfAbsent(assetId, pngBytes)
        return assetId
    }
}

private fun ByteArray.sha256Hex(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun ZipOutputStream.writeEntry(
    path: String,
    bytes: ByteArray,
) {
    putNextEntry(ZipEntry(path))
    write(bytes)
    closeEntry()
}

internal fun SavedProjectFile.formattedSubtitle(): String {
    val dateText =
        SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(modifiedAtEpochMs))
    val sizeText =
        DecimalFormat("#0.0").format(sizeBytes / 1024f)
    return "$dateText  ${sizeText}KB"
}

private const val DRAWING_PROJECT_DIRECTORY_NAME = "drawings"
private const val DRAWING_PROJECT_FILE_EXTENSION = "picodraw"
private const val DRAWING_PROJECT_MANIFEST_PATH = "document.json"
private const val DRAWING_PROJECT_FORMAT = "picodraw_document"
private const val DRAWING_PROJECT_VERSION = 1
