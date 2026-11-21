package com.recipebros.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * A no-server **prototype** lookup for a second-source recipe when a shared post
 * has no measurable ingredient list. TheMealDB's test key is for development;
 * public app-store distribution requires a supporter/production key.
 *
 * Never silently combine another cook's recipe with the Facebook video.
 */
object RecipeFinder {
    private val genericWords = setOf(
        "recipe", "recipes", "easy", "best", "delicious", "homemade", "how", "to", "make", "cook", "cooking", "video",
        "facebook", "instagram", "tiktok", "youtube", "share", "watch", "online", "food", "from", "with", "the",
        "a", "an", "on", "in", "and", "for", "of", "my", "your", "viral", "shorts", "reel", "reels", "original",
        "receta", "recetas", "facil", "faciles", "casera", "casero", "como", "hacer", "cocinar", "video", "para",
        "con", "del", "de", "en", "el", "la", "los", "las", "una", "uno", "muy", "ricas", "rico"
    )

    fun usableDishName(input: String): String? {
        val cleaned = input
            .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(?i)\\b(?:facebook|instagram|tiktok|youtube)\\b.*$"), " ")
            .substringBefore(" | ")
            .substringBefore(" — ")
            .substringBefore(" - Recipe")
            .replace(Regex("[#@][\\p{L}\\p{N}_]+"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s'-]"), " ")
            .trim()
        val meaningful = tokens(cleaned)
        if (meaningful.isEmpty() || cleaned.length !in 3..95) return null
        return cleaned
    }

    private fun tokens(s: String): List<String> = s.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(Regex("\\s+"))
        .filter { it.length > 1 && it !in genericWords }
        .distinct()

    private fun similarity(a: String, b: String): Double {
        val aWords = tokens(a).toSet()
        val bWords = tokens(b).toSet()
        if (aWords.isEmpty() || bWords.isEmpty()) return 0.0
        if (aWords == bWords) return 1.0
        val common = (aWords intersect bWords).size.toDouble()
        return (common / aWords.size * 0.6 + common / bWords.size * 0.4).coerceIn(0.0, 1.0)
    }

    suspend fun search(dishName: String): List<RecipeSuggestion> = withContext(Dispatchers.IO) {
        val query = usableDishName(dishName) ?: return@withContext emptyList()
        try {
            val endpoint = "https://www.themealdb.com/api/json/v1/1/search.php?s=" +
                URLEncoder.encode(query, "UTF-8")
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "RecipeBrosDev/1.2")
                instanceFollowRedirects = true
            }
            val payload = try {
                if (conn.responseCode !in 200..299) return@withContext emptyList()
                conn.inputStream.bufferedReader().use { it.readText().take(700_000) }
            } finally { conn.disconnect() }
            val meals = JSONObject(payload).optJSONArray("meals") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until meals.length()) {
                    val item = meals.optJSONObject(i) ?: continue
                    val title = item.optString("strMeal").trim()
                    val confidence = similarity(query, title)
                    if (confidence < 0.52) continue // Don't propose unrelated dishes.
                    val ingredients = buildList {
                        for (n in 1..20) {
                            val name = item.optString("strIngredient$n").trim()
                            if (name.isEmpty() || name.equals("null", ignoreCase = true)) continue
                            val measure = item.optString("strMeasure$n").trim()
                            add(listOf(measure, name).filter(String::isNotBlank).joinToString(" "))
                        }
                    }
                    val rawSteps = item.optString("strInstructions").trim()
                    val steps = rawSteps.split(Regex("\\r?\\n+|(?<=\\.)\\s+(?=\\d+[.)]\\s)"))
                        .map { it.trim() }.filter(String::isNotBlank)
                    if (ingredients.isEmpty()) continue
                    val id = item.optString("idMeal")
                    val nativeLink = item.optString("strSource").trim().takeIf { it.startsWith("https://") }
                    add(RecipeSuggestion(
                        title = title,
                        ingredients = ingredients,
                        steps = steps,
                        provider = "TheMealDB",
                        referenceUrl = nativeLink ?: "https://www.themealdb.com/meal/$id",
                        similarity = confidence
                    ))
                }
            }.sortedByDescending { it.similarity }.take(5)
        } catch (_: Exception) { emptyList() }
    }
}
