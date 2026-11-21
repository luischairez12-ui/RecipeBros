package com.recipebros.app

data class Recipe(
    val id: Long = 0,
    val title: String,
    val sourceUrl: String = "",
    val sourceLabel: String = "",
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val prepMinutes: Int = 0,
    val cookMinutes: Int = 0,
    val servings: Int = 2,
    val isMama: Boolean = false,
    val region: String = "",
    val notes: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class GroceryItem(val id: Long = 0, val text: String, val checked: Boolean = false)

data class RecipeSuggestion(
    val title: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val provider: String,
    val referenceUrl: String,
    val similarity: Double
)

data class ImportResult(
    val recipe: Recipe,
    val status: String,
    val suggestions: List<RecipeSuggestion> = emptyList()
)
