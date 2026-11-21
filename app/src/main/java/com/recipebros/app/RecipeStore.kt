package com.recipebros.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

class RecipeStore(context: Context) : SQLiteOpenHelper(context, "recipe_bros.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE recipes (
            id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, source_url TEXT,
            source_label TEXT, ingredients TEXT NOT NULL, steps TEXT NOT NULL,
            prep_minutes INTEGER NOT NULL, cook_minutes INTEGER NOT NULL,
            servings INTEGER NOT NULL, is_mama INTEGER NOT NULL, region TEXT,
            notes TEXT, favorite INTEGER NOT NULL, created_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE grocery (
            id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL,
            checked INTEGER NOT NULL DEFAULT 0
        )""")
        // Clearly labeled starter for testing Cook Mode; not a claimed family recipe.
        val starter = ContentValues().apply {
            put("title", "Starter: Easy Cheese Quesadillas")
            put("source_url", "")
            put("source_label", "Recipe Bros starter")
            put("ingredients", JSONArray(listOf("4 flour or corn tortillas", "1 cup shredded melting cheese", "1 tsp oil or butter (optional)")).toString())
            put("steps", JSONArray(listOf(
                "Heat a skillet over medium heat. Use a little oil or butter if desired.",
                "Place one tortilla in the pan. Sprinkle with cheese and cover with another tortilla.",
                "Cook for 2 to 3 minutes until the underside is lightly golden, then flip carefully.",
                "Cook for another 1 to 2 minutes until the cheese melts and the other side is golden.",
                "Move to a plate, let cool slightly, cut into wedges, and serve."
            )).toString())
            put("prep_minutes", 5)
            put("cook_minutes", 5)
            put("servings", 2)
            put("is_mama", 0)
            put("region", "")
            put("notes", "General beginner example, not a family recipe. Adjust filling and time to your stove.")
            put("favorite", 0)
            put("created_at", System.currentTimeMillis())
        }
        db.insert("recipes", null, starter)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun recipes(): List<Recipe> = readableDatabase.rawQuery(
        "SELECT * FROM recipes ORDER BY created_at DESC, id DESC", null
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) add(cursor.toRecipe())
    } }

    fun save(recipe: Recipe): Long {
        val content = ContentValues().apply {
            put("title", recipe.title)
            put("source_url", recipe.sourceUrl)
            put("source_label", recipe.sourceLabel)
            put("ingredients", JSONArray(recipe.ingredients).toString())
            put("steps", JSONArray(recipe.steps).toString())
            put("prep_minutes", recipe.prepMinutes)
            put("cook_minutes", recipe.cookMinutes)
            put("servings", recipe.servings)
            put("is_mama", if (recipe.isMama) 1 else 0)
            put("region", recipe.region)
            put("notes", recipe.notes)
            put("favorite", if (recipe.favorite) 1 else 0)
            put("created_at", recipe.createdAt)
        }
        return if (recipe.id == 0L) writableDatabase.insertOrThrow("recipes", null, content)
        else {
            writableDatabase.update("recipes", content, "id=?", arrayOf(recipe.id.toString()))
            recipe.id
        }
    }

    fun deleteRecipe(id: Long) {
        writableDatabase.delete("recipes", "id=?", arrayOf(id.toString()))
    }

    fun groceries(): List<GroceryItem> = readableDatabase.rawQuery(
        "SELECT * FROM grocery ORDER BY checked ASC, id DESC", null
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) add(GroceryItem(
            cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            cursor.getString(cursor.getColumnIndexOrThrow("text")),
            cursor.getInt(cursor.getColumnIndexOrThrow("checked")) != 0
        ))
    } }

    fun addGrocery(value: String) {
        val normalized = value.trim()
        if (normalized.isEmpty()) return
        val content = ContentValues().apply { put("text", normalized); put("checked", 0) }
        writableDatabase.insert("grocery", null, content)
    }

    fun addMissingIngredients(items: List<String>) {
        val existing = groceries().map { it.text.trim().lowercase() }.toSet()
        val seen = existing.toMutableSet()
        items.map { it.trim() }.filter { it.isNotBlank() }.forEach {
            if (seen.add(it.lowercase())) addGrocery(it)
        }
    }

    fun setGroceryChecked(id: Long, checked: Boolean) {
        writableDatabase.update("grocery", ContentValues().apply { put("checked", if (checked) 1 else 0) }, "id=?", arrayOf(id.toString()))
    }

    fun deleteGrocery(id: Long) { writableDatabase.delete("grocery", "id=?", arrayOf(id.toString())) }
    fun removeChecked() { writableDatabase.delete("grocery", "checked=1", null) }

    private fun Cursor.toRecipe(): Recipe = Recipe(
        id = getLong(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        sourceUrl = getString(getColumnIndexOrThrow("source_url")) ?: "",
        sourceLabel = getString(getColumnIndexOrThrow("source_label")) ?: "",
        ingredients = jsonList(getString(getColumnIndexOrThrow("ingredients"))),
        steps = jsonList(getString(getColumnIndexOrThrow("steps"))),
        prepMinutes = getInt(getColumnIndexOrThrow("prep_minutes")),
        cookMinutes = getInt(getColumnIndexOrThrow("cook_minutes")),
        servings = getInt(getColumnIndexOrThrow("servings")),
        isMama = getInt(getColumnIndexOrThrow("is_mama")) != 0,
        region = getString(getColumnIndexOrThrow("region")) ?: "",
        notes = getString(getColumnIndexOrThrow("notes")) ?: "",
        favorite = getInt(getColumnIndexOrThrow("favorite")) != 0,
        createdAt = getLong(getColumnIndexOrThrow("created_at"))
    )

    private fun jsonList(value: String?): List<String> = try {
        val array = JSONArray(value ?: "[]")
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    } catch (_: Exception) { emptyList() }
}
