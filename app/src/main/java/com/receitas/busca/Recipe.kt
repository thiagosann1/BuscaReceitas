package com.receitas.busca

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Recipe(
    val title: String,
    val url: String,
    val site: String,
    val image: String?,
    val description: String?,
    val ingredients: List<String>,
    val steps: List<String>,
    val time: String?,
    val servings: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("url", url)
        put("site", site)
        put("image", image ?: "")
        put("description", description ?: "")
        put("ingredients", JSONArray(ingredients))
        put("steps", JSONArray(steps))
        put("time", time ?: "")
        put("servings", servings ?: "")
    }

    companion object {
        fun fromJson(o: JSONObject): Recipe {
            fun list(key: String): List<String> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { arr.optString(it) }
            }
            return Recipe(
                title = o.optString("title"),
                url = o.optString("url"),
                site = o.optString("site"),
                image = o.optString("image").ifBlank { null },
                description = o.optString("description").ifBlank { null },
                ingredients = list("ingredients"),
                steps = list("steps"),
                time = o.optString("time").ifBlank { null },
                servings = o.optString("servings").ifBlank { null }
            )
        }
    }
}

/** Guarda as receitas favoritas no próprio celular (funciona sem internet). */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favoritos", Context.MODE_PRIVATE)

    fun load(): List<Recipe> = runCatching {
        val arr = JSONArray(prefs.getString("lista", "[]"))
        (0 until arr.length()).map { Recipe.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun save(list: List<Recipe>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("lista", arr.toString()).apply()
    }
}
