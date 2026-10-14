package com.ourindia.app.ui.civic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * Opens the device camera for a fresh photo (no gallery: evidence should be taken on the
 * spot). Returns the launcher; [onCaptured] gets the file the camera wrote, or null if the
 * user backed out.
 *
 * The manifest must NOT declare CAMERA: when an app declares it but hasn't been granted it,
 * ACTION_IMAGE_CAPTURE throws SecurityException. The camera app holds the permission instead.
 */
@Composable
fun rememberPhotoCapture(onCaptured: (File?) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPath?.let(::File)
        onCaptured(if (ok && file != null && file.length() > 0) file else null)
    }
    return remember(launcher) {
        {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(dir, "issue_${System.currentTimeMillis()}.jpg")
            pendingPath = file.absolutePath
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            launcher.launch(uri)
        }
    }
}

/**
 * Turns a camera file into a data: URL small enough for the server's 5 MB JSON body limit
 * (target under ~350 KB). Applies the EXIF rotation, which the camera app records instead of
 * rotating pixels, so portrait shots don't arrive sideways.
 */
suspend fun encodePhotoForUpload(context: Context, file: File, maxEdge: Int = 1280): String? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null

        val rotation = when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = maxEdge.toFloat() / max(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val bitmap = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        var quality = 78
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= 10
        } while (bytes.size > 350_000 && quality >= 40)

        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()
}
