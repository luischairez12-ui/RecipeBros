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
        try {
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
                val status = if (ingredients.isNotEmpty() && steps.isNotEmpty())
                    "Recipe extracted from structured website data. Please check quantities and instructions before cooking."
                else "Only part of this recipe was available. Review and complete it before saving."
                ImportResult(recipe, status)
            } else {
                val ingredients = document.select("[itemprop=recipeIngredient]").map { it.text().trim() }.filter { it.isNotBlank() }
                val steps = document.select("[itemprop=recipeInstructions]").map { it.text().trim() }.filter { it.isNotBlank() }
                val recipe = Recipe(
                    title = metaTitle(document, host), sourceUrl = url, sourceLabel = host,
                    ingredients = ingredients, steps = steps
                )
                ImportResult(recipe, if (ingredients.isEmpty() || steps.isEmpty())
                    "Saved the link and available page title. This page may block recipe access or contain only video; add ingredients and steps manually."
                else "Some recipe fields were found. Check them before saving.")
            }
        } catch (_: Exception) {
            ImportResult(
                Recipe(title = "Recipe from $host", sourceUrl = url, sourceLabel = host),
                "The website did not allow extraction or needs internet access. Your link is ready to save; add instructions manually."
            )
        }
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
