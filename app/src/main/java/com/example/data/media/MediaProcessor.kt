package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

data class ExtractedVideoInfo(
    val durationSeconds: Long,
    val sizeBytes: Long,
    val autoThumbnailDataUri: String,
    val width: Int,
    val height: Int
)

object MediaProcessor {

    private const val RAW_CHUNK_SIZE_BYTES = 420_000 // ~560 KB base64, safely under 1 MiB Firestore limit

    suspend fun extractVideoInfo(context: Context, uri: Uri): ExtractedVideoInfo = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val sizeBytes = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0 }
            ?: resolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L

        val retriever = MediaMetadataRetriever()
        var durationSec = 0L
        var thumbUri = ""
        var width = 0
        var height = 0
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            durationSec = ((durationMs + 500L) / 1000L).coerceAtLeast(1L)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (frame != null) {
                thumbUri = bitmapToDataUri(frame, maxDim = 540, quality = 72)
                frame.recycle()
            }
        } finally {
            runCatching { retriever.release() }
        }
        ExtractedVideoInfo(
            durationSeconds = durationSec,
            sizeBytes = sizeBytes,
            autoThumbnailDataUri = thumbUri,
            width = width,
            height = height
        )
    }

    suspend fun compressImageUriToDataUri(
        context: Context,
        uri: Uri,
        maxDim: Int = 540,
        quality: Int = 75
    ): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext ""
        val decoded = input.use { BitmapFactory.decodeStream(it) } ?: return@withContext ""
        val result = bitmapToDataUri(decoded, maxDim = maxDim, quality = quality)
        decoded.recycle()
        result
    }

    private fun bitmapToDataUri(bitmap: Bitmap, maxDim: Int, quality: Int): String {
        val scale = min(1f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1))
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, targetW, targetH, true) else bitmap

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    suspend fun readVideoIntoBase64Chunks(
        context: Context,
        uri: Uri,
        maxBytes: Long
    ): List<String> = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("ویڈیو فائل نہیں کھل سکی")
        val chunks = mutableListOf<String>()
        var totalRead = 0L
        stream.use { input ->
            val buffer = ByteArray(RAW_CHUNK_SIZE_BYTES)
            while (true) {
                var offset = 0
                while (offset < buffer.size) {
                    val read = input.read(buffer, offset, buffer.size - offset)
                    if (read == -1) break
                    offset += read
                }
                if (offset == 0) break
                totalRead += offset
                if (totalRead > maxBytes) {
                    throw IllegalArgumentException("فائل کا سائز مقررہ حد سے زیادہ ہے")
                }
                val slice = if (offset == buffer.size) buffer else buffer.copyOf(offset)
                chunks.add(Base64.encodeToString(slice, Base64.NO_WRAP))
            }
        }
        if (chunks.isEmpty()) {
            throw IllegalArgumentException("منتخب کردہ ویڈیو خالی ہے")
        }
        chunks
    }

    fun getCachedVideoFile(context: Context, videoId: String): File {
        val dir = File(context.cacheDir, "paktube_videos")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$videoId.mp4")
    }

    suspend fun assembleChunksToCache(
        context: Context,
        videoId: String,
        orderedBase64Chunks: List<String>
    ): Uri = withContext(Dispatchers.IO) {
        val outFile = getCachedVideoFile(context, videoId)
        FileOutputStream(outFile).use { fos ->
            for (chunkB64 in orderedBase64Chunks) {
                val bytes = Base64.decode(chunkB64, Base64.DEFAULT)
                fos.write(bytes)
            }
            fos.flush()
        }
        Uri.fromFile(outFile)
    }

    fun decodeDataUriToBytes(dataUri: String): ByteArray? {
        if (!dataUri.startsWith("data:image", ignoreCase = true)) return null
        val comma = dataUri.indexOf(',')
        if (comma <= 0) return null
        return runCatching { Base64.decode(dataUri.substring(comma + 1), Base64.DEFAULT) }.getOrNull()
    }
}
