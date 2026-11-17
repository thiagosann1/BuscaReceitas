package com.receitas.busca

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

/**
 * Como a busca funciona:
 * 1. Pesquisa "receita <termo>" no DuckDuckGo (e no Bing, se precisar).
 * 2. Abre cada site encontrado e procura os dados estruturados da receita
 *    (schema.org/Recipe em JSON-LD), que quase todo site de receitas publica
 *    (TudoGostoso, Receitas Nestlé, Panelinha, Ana Maria Braga, Guia da Cozinha...).
 * 3. Devolve as receitas conforme ficam prontas, para a lista ir aparecendo aos poucos.
 */
object RecipeSearch {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val BLOCKED_HOSTS = listOf(
        "youtube.", "youtu.be", "instagram.", "facebook.", "tiktok.", "pinterest.",
        "twitter.", "x.com", "kwai.", "duckduckgo.", "bing.com", "google.", "wikipedia."
    )

    fun search(term: String, maxSites: Int = 14): Flow<Recipe> = channelFlow {
        val links = withContext(Dispatchers.IO) { findLinks(term) }
        if (links.isEmpty()) throw IOException("a busca não retornou sites")
        links.take(maxSites).forEach { url ->
            launch(Dispatchers.IO) {
                val recipe = runCatching { readRecipe(url) }.getOrNull()
                if (recipe != null) send(recipe)
            }
        }
    }

    // ---------------------------------------------------------------- busca de links

    private fun findLinks(term: String): List<String> {
        val query = "receita $term"
        val found = mutableListOf<String>()
        found += runCatching { duckDuckGo(query) }.getOrDefault(emptyList())
        if (found.size < 5) found += runCatching { bing(query) }.getOrDefault(emptyList())
        return found
            .filter { it.startsWith("http") && !it.endsWith(".pdf", ignoreCase = true) }
            .filter { url -> BLOCKED_HOSTS.none { hostOf(url).contains(it) } }
            .distinctBy { it.substringBefore('?').substringBefore('#').trimEnd('/') }
    }

    private fun duckDuckGo(query: String): List<String> {
        val doc = Jsoup.connect("https://html.duckduckgo.com/html/")
            .data("q", query)
            .data("kl", "br-pt")
            .userAgent(UA)
            .timeout(15_000)
            .post()
        return doc.select("a.result__a").mapNotNull { a ->
            val href = a.attr("href")
            when {
                href.contains("uddg=") -> {
                    val enc = href.substringAfter("uddg=").substringBefore('&')
                    URLDecoder.decode(enc, "UTF-8")
                }
                href.startsWith("http") -> href
                else -> null
            }
        }
    }

    private fun bing(query: String): List<String> {
        val doc = Jsoup.connect("https://www.bing.com/search")
            .data("q", query)
            .data("setlang", "pt-BR")
            .data("cc", "BR")
            .userAgent(UA)
            .timeout(15_000)
            .get()
        return doc.select("li.b_algo h2 a").mapNotNull { a -> unwrapBing(a.attr("href")) }
    }

    /** O Bing às vezes embrulha o link em bing.com/ck/a?...&u=a1<base64>. */
    private fun unwrapBing(href: String): String? {
        if (!href.startsWith("http")) return null
        if (!hostOf(href).contains("bing.com")) return href
        val u = href.substringAfter("&u=", "").substringBefore('&')
        if (!u.startsWith("a1")) return null
        return runCatching {
            String(Base64.decode(u.drop(2), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        }.getOrNull()
    }

    // ---------------------------------------------------------------- leitura da receita

    fun readRecipe(url: String): Recipe? {
        val doc = Jsoup.connect(url)
            .userAgent(UA)
            .timeout(12_000)
            .followRedirects(true)
            .maxBodySize(4_000_000)
            .get()

        for (script in doc.select("script[type=application/ld+json]")) {
            val raw = script.data().trim()
            if (raw.isEmpty()) continue
            val json = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: continue
            val node = findRecipeNode(json, 0) ?: continue
            val recipe = buildRecipe(node, doc, doc.location().ifBlank { url })
            if (recipe != null) return recipe
        }
        return null
    }

    private fun findRecipeNode(any: Any?, depth: Int): JSONObject? {
        if (depth > 6) return null
        return when (any) {
            is JSONObject -> {
                if (isRecipeType(any.opt("@type"))) return any
                findRecipeNode(any.opt("@graph"), depth + 1)
                    ?: findRecipeNode(any.opt("mainEntity"), depth + 1)
            }
            is JSONArray -> {
                for (i in 0 until any.length()) {
                    findRecipeNode(any.opt(i), depth + 1)?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    private fun isRecipeType(type: Any?): Boolean = when (type) {
        is String -> type.equals("Recipe", ignoreCase = true)
        is JSONArray -> (0 until type.length()).any { type.optString(it).equals("Recipe", true) }
        else -> false
    }

    private fun buildRecipe(node: JSONObject, doc: Document, url: String): Recipe? {
        val ingredients = stringList(node.opt("recipeIngredient") ?: node.opt("ingredients"))
            .map { clean(it) }
            .filter { it.isNotBlank() }
        val steps = mutableListOf<String>().also { collectSteps(node.opt("recipeInstructions"), it, 0) }
        if (ingredients.isEmpty() && steps.isEmpty()) return null

        val title = clean(node.optString("name")).ifBlank { doc.title() }
        val image = imageOf(node.opt("image"))
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }

        val time = duration(node.optString("totalTime"))
            ?: listOfNotNull(
                duration(node.optString("prepTime"))?.let { "preparo $it" },
                duration(node.optString("cookTime"))?.let { "cozimento $it" }
            ).joinToString(" + ").ifBlank { null }

        return Recipe(
            title = title,
            url = url,
            site = hostOf(url).removePrefix("www."),
            image = image?.let { if (it.startsWith("//")) "https:$it" else it },
            description = clean(node.optString("description")).ifBlank { null }?.take(400),
            ingredients = ingredients,
            steps = steps,
            time = time,
            servings = servingsOf(node.opt("recipeYield"))
        )
    }

    private fun collectSteps(any: Any?, out: MutableList<String>, depth: Int) {
        if (any == null || any == JSONObject.NULL || depth > 5) return
        when (any) {
            is String -> {
                if (any.contains("<li", ignoreCase = true) || any.contains("<p", ignoreCase = true)) {
                    val html = Jsoup.parse(any)
                    val parts = html.select("li").map { it.text() }.ifEmpty { html.select("p").map { it.text() } }
                    out += parts.map { it.trim() }.filter { it.isNotBlank() }
                } else {
                    // separa por quebra de linha (antes de limpar, pois a limpeza junta as linhas)
                    out += any.split(Regex("\\r?\\n+"))
                        .map { clean(it) }
                        .filter { it.isNotBlank() }
                }
            }
            is JSONArray -> for (i in 0 until any.length()) collectSteps(any.opt(i), out, depth + 1)
            is JSONObject -> {
                val type = any.optString("@type")
                if (type.equals("HowToSection", true) || any.has("itemListElement")) {
                    val name = clean(any.optString("name"))
                    if (name.isNotBlank()) out += "▸ $name"
                    collectSteps(any.opt("itemListElement"), out, depth + 1)
                } else {
                    val text = clean(any.optString("text")).ifBlank { clean(any.optString("name")) }
                    if (text.isNotBlank()) out += text
                }
            }
        }
    }

    // ---------------------------------------------------------------- utilitários

    private fun stringList(any: Any?): List<String> = when (any) {
        is JSONArray -> (0 until any.length()).mapNotNull { i ->
            when (val v = any.opt(i)) {
                is String -> v
                is JSONObject -> v.optString("name").ifBlank { v.optString("text") }
                else -> null
            }
        }
        is String -> any.split('\n')
        else -> emptyList()
    }

    private fun imageOf(any: Any?): String? = when (any) {
        is String -> any.takeIf { it.isNotBlank() }
        is JSONArray -> if (any.length() > 0) imageOf(any.opt(0)) else null
        is JSONObject -> any.optString("url").ifBlank { any.optString("contentUrl") }.ifBlank { null }
        else -> null
    }

    private fun servingsOf(any: Any?): String? {
        val v = when (any) {
            is JSONArray -> if (any.length() > 0) any.opt(0)?.toString() else null
            null, JSONObject.NULL -> null
            else -> any.toString()
        } ?: return null
        val text = clean(v)
        if (text.isBlank()) return null
        return if (text.all { it.isDigit() }) "$text porções" else text
    }

    /** Converte duração ISO 8601 (PT1H30M) em texto ("1 h 30 min"). */
    private fun duration(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val m = Regex("P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?", RegexOption.IGNORE_CASE)
            .find(iso.trim()) ?: return null
        val d = m.groupValues[1].toIntOrNull() ?: 0
        var h = m.groupValues[2].toIntOrNull() ?: 0
        var min = m.groupValues[3].toIntOrNull() ?: 0
        h += d * 24 + min / 60
        min %= 60
        if (h == 0 && min == 0) return null
        return listOfNotNull(
            if (h > 0) "$h h" else null,
            if (min > 0) "$min min" else null
        ).joinToString(" ")
    }

    private fun clean(s: String?): String =
        if (s.isNullOrBlank() || s == "null") "" else Jsoup.parse(s).text().trim()

    private fun hostOf(url: String): String =
        runCatching { URI(url).host ?: "" }.getOrDefault("").lowercase()
}
