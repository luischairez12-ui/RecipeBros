package com.recipebros.app

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Screenshot-only OCR. It reads visible writing, NOT food objects or speech. */
data class ScreenshotScan(
    val recognizedText: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val titleHint: String?
)

object ScreenshotRecipeReader {
    suspend fun scan(context: Context, uri: Uri): ScreenshotScan = withContext(Dispatchers.IO) {
        val input = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                recognizer.process(input)
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                    .addOnCanceledListener { continuation.cancel() }
            }
            parse(result.text)
        } finally {
            recognizer.close()
        }
    }

    fun parse(text: String): ScreenshotScan {
        val lines = text.lines().map {
            it.trim().trimStart('•', '*', '-', '·', ' ').trim()
        }.filter { it.isNotBlank() }.take(180)
        val ingredientHeader = Regex("""(?i)^(ingredients?|ingredientes|lista de ingredientes|you(?:'|’)ll need)(\s*:.*)?$""")
        val stepsHeader = Regex("""(?i)^(instructions?|steps?|directions?|method|preparaci[oó]n|pasos?|procedimiento|instrucciones)(\s*:.*)?$""")
        val otherHeader = Regex("""(?i)^(notes?|notas?|tips?|consejos|comments?|comentarios|nutrition|calor[ií]as|servings?|porciones|hashtags?)(\s*:.*)?$""")
        val ingredients = mutableListOf<String>()
        val steps = mutableListOf<String>()
        var section = 0
        for (line in lines) {
            when {
                ingredientHeader.matches(line) -> {
                    section = 1
                    val remainder = line.substringAfter(':', "").trim()
                    if (remainder.isNotEmpty()) ingredients.add(remainder)
                }
                stepsHeader.matches(line) -> {
                    section = 2
                    val remainder = line.substringAfter(':', "").trim()
                    if (remainder.isNotEmpty()) steps.add(remainder)
                }
                otherHeader.matches(line) -> section = 0
                section == 1 && ingredients.size < 35 && line.length <= 140 -> ingredients.add(line)
                section == 2 && steps.size < 35 && line.length <= 500 -> steps.add(line)
            }
        }
        // A generic post title is not reliable. Only hint titles explicitly labeled as recipes.
        val hint = lines.firstOrNull {
            it.length in 9..85 && Regex("""(?i)^(receta de|receta:|recipe for|recipe:)\s*.+""").matches(it)
        }?.replace(Regex("""(?i)^(receta de|receta:|recipe for|recipe:)\s*"""), "")?.trim()
        return ScreenshotScan(
            recognizedText = text.take(4500),
            ingredients = ingredients.distinctBy { it.lowercase() },
            steps = steps.distinctBy { it.lowercase() },
            titleHint = hint?.takeIf { it.length in 3..75 }
        )
    }
}
