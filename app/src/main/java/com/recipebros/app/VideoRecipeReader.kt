package com.recipebros.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class VideoTextMoment(val second: Long, val text: String)
data class VideoRecipeScan(
    val durationSeconds: Long,
    val sampledFrames: Int,
    val readableFrames: Int,
    val ingredients: List<String>,
    val instructions: List<String>,
    val titleHint: String?,
    val possibleIngredientLines: List<String>,
    val moments: List<VideoTextMoment>
)

/** Local video FRAME OCR only: no audio or semantic object recognition. */
object VideoRecipeReader {
    private val measurement = Regex(
        """(?i)(?:^|\s)(?:\d+(?:[.,/]\d+)?|[¼½¾])\s*(?:cups?|tazas?|tbsp|tsp|tablespoons?|teaspoons?|cucharadas?|cucharaditas?|g|gr|grams?|gramos?|kg|ml|liters?|litros?|oz|ounces?|lbs?|pounds?|cloves?|dientes?)\b"""
    )
    private val uiNoise = Regex("""(?i)(follow|subscribe|comment|share|views?|likes?|reposts?|seguidores|comparte|sígueme)""")

    suspend fun scan(context: Context, uri: Uri): VideoRecipeScan = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            retriever.setDataSource(context, uri)
            val durationMs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L).coerceAtLeast(0L)
            val count = when {
                durationMs == 0L -> 1
                durationMs < 15_000L -> 6
                durationMs < 90_000L -> 12
                else -> 16
            }
            val seen = mutableSetOf<String>()
            val moments = mutableListOf<VideoTextMoment>()
            for (index in 0 until count) {
                coroutineContext.ensureActive()
                val positionUs = if (durationMs == 0L) 0L
                    else ((durationMs * 1000.0) * (index + 0.5) / count).toLong()
                val bitmap = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            positionUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 1280, 1280
                        )
                    } else retriever.getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (_: Exception) { null }
                if (bitmap != null) {
                    try {
                        val result = suspendCancellableCoroutine<Text> { continuation ->
                            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                                .addOnSuccessListener {
                                    if (continuation.isActive) continuation.resume(it)
                                }
                                .addOnFailureListener {
                                    if (continuation.isActive) continuation.resumeWithException(it)
                                }
                                .addOnCanceledListener { continuation.cancel() }
                        }
                        val frameText = result.text.trim()
                        val fingerprint = frameText.lowercase().replace(Regex("""\s+"""), " ")
                        if (frameText.isNotBlank() && seen.add(fingerprint)) {
                            moments.add(VideoTextMoment(positionUs / 1_000_000, frameText.take(1600)))
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            val scans = moments.map { ScreenshotRecipeReader.parse(it.text) }
            val fullTextScan = ScreenshotRecipeReader.parse(moments.joinToString("\n") { it.text })
            val ingredients = (scans.flatMap { it.ingredients } + fullTextScan.ingredients)
                .map(String::trim).filter(String::isNotBlank).distinctBy { it.lowercase() }.take(45)
            val instructions = (scans.flatMap { it.steps } + fullTextScan.steps)
                .map(String::trim).filter(String::isNotBlank).distinctBy { it.lowercase() }.take(40)
            val verifiedSet = ingredients.map { it.lowercase() }.toSet()
            val possible = moments.flatMap { it.text.lines() }
                .map { it.trim().trimStart('-', '•', '*', ' ').trim() }
                .filter { it.length in 5..120 && measurement.containsMatchIn(it) && !uiNoise.containsMatchIn(it) }
                .distinctBy { it.lowercase() }.filter { it.lowercase() !in verifiedSet }.take(24)
            VideoRecipeScan(
                durationSeconds = durationMs / 1000L,
                sampledFrames = count,
                readableFrames = moments.size,
                ingredients = ingredients,
                instructions = instructions,
                titleHint = scans.firstNotNullOfOrNull { it.titleHint } ?: fullTextScan.titleHint,
                possibleIngredientLines = possible,
                moments = moments
            )
        } finally {
            recognizer.close()
            retriever.release()
        }
    }
}
