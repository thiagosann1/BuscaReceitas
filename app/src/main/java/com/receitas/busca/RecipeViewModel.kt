package com.receitas.busca

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecipeViewModel(app: Application) : AndroidViewModel(app) {

    private val store = FavoritesStore(app)

    var query by mutableStateOf("")
    val results = mutableStateListOf<Recipe>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var searched by mutableStateOf(false)
        private set

    val favorites = mutableStateListOf<Recipe>().apply { addAll(store.load()) }
    var selected by mutableStateOf<Recipe?>(null)
    var tab by mutableStateOf(0)

    private var job: Job? = null

    fun search(term: String = query) {
        val t = term.trim()
        if (t.isEmpty()) return
        query = t
        job?.cancel()
        results.clear()
        error = null
        loading = true
        searched = true
        job = viewModelScope.launch {
            try {
                RecipeSearch.search(t).collect { r ->
                    if (results.none { it.url == r.url || it.title.equals(r.title, true) && it.site == r.site }) {
                        results.add(r)
                    }
                }
                if (results.isEmpty()) {
                    error = "Não encontrei receitas para \"$t\". Tente outro termo."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Não consegui buscar agora. Confira a internet e tente de novo."
            } finally {
                if (isActive) loading = false
            }
        }
    }

    fun isFavorite(r: Recipe) = favorites.any { it.url == r.url }

    fun toggleFavorite(r: Recipe) {
        if (isFavorite(r)) favorites.removeAll { it.url == r.url } else favorites.add(0, r)
        store.save(favorites.toList())
    }
}
