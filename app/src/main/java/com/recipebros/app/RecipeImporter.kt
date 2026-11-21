package com.recipebros.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

object RecipeImporter {
    private val urlPattern = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun findUrl(text: String): String? = urlPattern.find(text)?.value?.trimEnd('.', ',', ')', ']', ';')

    suspend fun import(linkOrSharedText: String): ImportResult = withContext(Dispatchers.IO) {
        val url = findUrl(linkOrSharedText) ?: linkOrSharedText.trim()
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in listOf("https", "http") || uri.host.isNullOrBlank()) {
            return@withContext ImportResult(Recipe(title = "New recipe", sourceUrl = url), "Add a valid http(s) recipe link, or fill the recipe manually.")
        }
        val host = uri.host.removePrefix("www.")
        val sharedCaption = linkOrSharedText.replace(url, " ").trim()
        val result = try {
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36")
                .followRedirects(true).timeout(13_000).maxBodySize(1_500_000).get()
            val structured = extractJsonLdRecipe(document)
            if (structured != null) {
                val servings = structured.opt("recipeYield")?.let { parseServings(it) } ?: 2
                val ingredients = jsonStrings(structured.opt("recipeIngredient"))
                val steps = instructions(structured.opt("recipeInstructions"))
                val prep = durationMinutes(structured.optString("prepTime"))
                val cook = durationMinutes(structured.optString("cookTime"))
                val recipe = Recipe(
                    title = structured.optString("name").ifBlank { metaTitle(document, host) },
                    sourceUrl = url, sourceLabel = host, ingredients = ingredients,
                    steps = steps, prepMinutes = prep, cookMinutes = cook, servings = servings
                )
                ImportResult(recipe, if (ingredients.isNotEmpty() && steps.isNotEmpty())
                    "Recipe extracted from its original website. Check quantities and cooking safety."
                else "The original page has incomplete ingredients or directions.")
            } else {
                val htmlIngredients = document.select("[itemprop=recipeIngredient]").map { it.text().trim() }
                val htmlSteps = document.select("[itemprop=recipeInstructions]").map { it.text().trim() }
                // Public posts sometimes put explicit ingredients in OG descriptions.
                // Only read lines after an explicit Ingredients/Ingredientes label; don't invent missing amounts.
                val caption = document.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
                val captionIngredients = if (htmlIngredients.isEmpty()) explicitCaptionIngredients(caption) else emptyList()
                val rawTitle = metaTitle(document, host)
                val safeTitle = RecipeFinder.usableDishName(rawTitle)
                    ?: RecipeFinder.usableDishName(sharedCaption)
                    ?: "Recipe from $host"
                val recipe = Recipe(
                    title = safeTitle, sourceUrl = url, sourceLabel = host,
                    ingredients = htmlIngredients.ifEmpty { captionIngredients }, steps = htmlSteps
                )
                ImportResult(recipe, if (recipe.ingredients.isNotEmpty() && recipe.steps.isNotEmpty())
                    "Extracted available information from the page. Check it before cooking."
                else "Original post does not provide a complete recipe.")
            }
        } catch (_: Exception) {
            val title = RecipeFinder.usableDishName(sharedCaption) ?: "Recipe from $host"
            ImportResult(Recipe(title = title, sourceUrl = url, sourceLabel = host),
                "This site blocked video details or could not be reached. Original link preserved.")
        }
        if (result.recipe.ingredients.isNotEmpty() && result.recipe.steps.isNotEmpty()) {
            return@withContext result
        }
        val titleForLookup = RecipeFinder.usableDishName(result.recipe.title)
            ?: RecipeFinder.usableDishName(sharedCaption)
        val alternatives = titleForLookup?.let { RecipeFinder.search(it) }.orEmpty()
        if (alternatives.isEmpty()) {
            result.copy(status = result.status +
                " Enter the dish name below and tap 'Find similar recipes' to search another source.")
        } else {
            result.copy(suggestions = alternatives, status = result.status +
                " Found ${alternatives.size} similar recipe option(s) from a separate source — compare and choose before using.")
        }
    }

    private fun explicitCaptionIngredients(caption: String): List<String> {
        val lines = caption.replace(" • ", "\n").replace("; ", "\n")
            .lines().map { it.trim().trimStart('-', '*', '•').trim() }
        val start = lines.indexOfFirst { it.matches(Regex("(?i)^(ingredients|ingredientes)\\s*:?\\s*$")) }
        if (start < 0) return emptyList()
        return lines.drop(start + 1).takeWhile {
            it.isNotBlank() && !it.matches(Regex("(?i)^(steps|instructions|directions|preparaci[oó]n|instrucciones).*"))
        }.take(30).filter { it.length in 2..160 }
    }

    private fun metaTitle(doc: Document, host: String): String =
        doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.title().takeIf { it.isNotBlank() } ?: "Recipe from $host"

    private fun extractJsonLdRecipe(doc: Document): JSONObject? {
        for (element in doc.select("script[type=application/ld+json]")) {
            val raw = element.data().ifBlank { element.html() }.trim()
            try {
                val parsed: Any = if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw)
                findRecipe(parsed)?.let { return it }
            } catch (_: Exception) { /* malformed JSON-LD is common */ }
        }
        return null
    }

    private fun findRecipe(node: Any?, depth: Int = 0): JSONObject? {
        if (depth > 8) return null
        if (node is JSONArray) {
            for (i in 0 until node.length()) findRecipe(node.opt(i), depth + 1)?.let { return it }
        } else if (node is JSONObject) {
            val type = node.opt("@type")
            val isRecipe = when (type) {
                is String -> type.substringAfterLast('/').equals("Recipe", true)
                is JSONArray -> (0 until type.length()).any { type.optString(it).substringAfterLast('/').equals("Recipe", true) }
                else -> false
            }
            if (isRecipe) return node
            for (key in listOf("@graph", "mainEntity", "itemListElement", "about")) {
                findRecipe(node.opt(key), depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun jsonStrings(node: Any?): List<String> = when (node) {
        is JSONArray -> (0 until node.length()).mapNotNull { index ->
            val item = node.opt(index)
            when (item) { is String -> item; is JSONObject -> item.optString("text").ifBlank { item.optString("name") }; else -> null }
        }.map { Jsoup.parse(it).text().trim() }.filter(String::isNotBlank)
        is String -> listOf(Jsoup.parse(node).text().trim()).filter(String::isNotBlank)
        else -> emptyList()
    }

    private fun instructions(node: Any?): List<String> = when (node) {
        is JSONArray -> (0 until node.length()).flatMap { instructions(node.opt(it)) }
        is JSONObject -> when {
            node.has("text") -> jsonStrings(node.opt("text"))
            node.has("itemListElement") -> instructions(node.opt("itemListElement"))
            else -> jsonStrings(node.opt("name"))
        }
        is String -> listOf(Jsoup.parse(node).text().trim()).filter(String::isNotBlank)
        else -> emptyList()
    }

    private fun parseServings(node: Any): Int {
        val string = when (node) { is JSONArray -> node.optString(0); else -> node.toString() }
        return Regex("\\d+").find(string)?.value?.toIntOrNull()?.coerceIn(1, 50) ?: 2
    }

    private fun durationMinutes(raw: String): Int {
        val minutes = Regex("(\\d+)M", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val hours = Regex("(\\d+)H", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return (hours * 60 + minutes).coerceIn(0, 1440)
    }
}
