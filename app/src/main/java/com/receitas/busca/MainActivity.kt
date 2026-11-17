@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.receitas.busca

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ReceitasTheme { App() } }
    }
}

// ------------------------------------------------------------------ tema

private val Laranja = Color(0xFFE8590C)

@Composable
fun ReceitasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Laranja,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE8D9),
            onPrimaryContainer = Color(0xFF5A2000),
            secondary = Color(0xFF6B8E23),
            background = Color(0xFFFFFBF8),
            surface = Color(0xFFFFFBF8),
            surfaceVariant = Color(0xFFF6EDE6)
        ),
        content = content
    )
}

// ------------------------------------------------------------------ app

@Composable
fun App(vm: RecipeViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        HomeScreen(vm)
        val sel = vm.selected
        if (sel != null) {
            BackHandler { vm.selected = null }
            RecipeDetail(
                recipe = sel,
                favorite = vm.isFavorite(sel),
                onToggleFavorite = { vm.toggleFavorite(sel) },
                onBack = { vm.selected = null }
            )
        }
    }
}

private val SUGESTOES = listOf(
    "pão doce", "bolo de cenoura", "lasanha", "strogonoff", "pudim",
    "pão de queijo", "feijoada", "brigadeiro", "torta de frango", "escondidinho"
)

@Composable
fun HomeScreen(vm: RecipeViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Busca Receitas", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == 0,
                    onClick = { vm.tab = 0 },
                    icon = { Icon(Icons.Filled.Search, null) },
                    label = { Text("Buscar") }
                )
                NavigationBarItem(
                    selected = vm.tab == 1,
                    onClick = { vm.tab = 1 },
                    icon = { Icon(Icons.Filled.Favorite, null) },
                    label = { Text("Favoritas (${vm.favorites.size})") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.tab == 0) SearchTab(vm) else FavoritesTab(vm)
        }
    }
}

@Composable
fun SearchTab(vm: RecipeViewModel) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("Ex.: pão doce, bolo de fubá…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.query = "" }) { Icon(Icons.Filled.Clear, "Limpar") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.search()
            })
        )

        if (vm.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Text(
                if (vm.results.isEmpty()) "Procurando receitas na internet…"
                else "Encontradas ${vm.results.size} até agora… ainda procurando",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        vm.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }

        if (!vm.searched) {
            Text(
                "Sugestões",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            )
            FlowRow(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SUGESTOES.forEach { s ->
                    SuggestionChip(
                        onClick = { focus.clearFocus(); vm.search(s) },
                        label = { Text(s) }
                    )
                }
            }
        }

        RecipeList(vm.results, onClick = { vm.selected = it })
    }
}

@Composable
fun FavoritesTab(vm: RecipeViewModel) {
    if (vm.favorites.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Nenhuma favorita ainda.\nToque no ♥ dentro de uma receita para guardá-la aqui — ela fica salva no celular e abre mesmo sem internet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        RecipeList(vm.favorites, onClick = { vm.selected = it })
    }
}

@Composable
fun RecipeList(list: List<Recipe>, onClick: (Recipe) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(list, key = { it.url }) { r -> RecipeCard(r) { onClick(r) } }
    }
}

@Composable
fun RecipeCard(r: Recipe, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(r.image, Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(r.site, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        r.time?.let { "⏱ $it" },
                        r.servings?.let { "🍽 $it" },
                        if (r.ingredients.isNotEmpty()) "${r.ingredients.size} ingredientes" else null
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun Thumb(url: String?, modifier: Modifier) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text("🍲", fontSize = 32.sp)
        }
    }
}

// ------------------------------------------------------------------ detalhe

@Composable
fun RecipeDetail(
    recipe: Recipe,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val checked = remember(recipe.url) { mutableStateMapOf<Int, Boolean>() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(recipe.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val texto = buildString {
                                appendLine(recipe.title)
                                appendLine()
                                appendLine("INGREDIENTES")
                                recipe.ingredients.forEach { appendLine("• $it") }
                                appendLine()
                                appendLine("MODO DE PREPARO")
                                var n = 0
                                recipe.steps.forEach { s ->
                                    if (s.startsWith("▸")) appendLine(s) else appendLine("${++n}. $s")
                                }
                                appendLine()
                                append("Fonte: ${recipe.url}")
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, texto)
                            }
                            context.startActivity(Intent.createChooser(send, "Compartilhar receita"))
                        }) { Icon(Icons.Filled.Share, "Compartilhar") }
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                "Favoritar",
                                tint = if (favorite) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        ) { pad ->
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    if (recipe.image != null) {
                        AsyncImage(
                            model = recipe.image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(230.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    Column(Modifier.padding(16.dp)) {
                        Text(recipe.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            recipe.site,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { uri.openUri(recipe.url) }
                        )
                        val meta = listOfNotNull(recipe.time?.let { "⏱ $it" }, recipe.servings?.let { "🍽 $it" })
                        if (meta.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(meta.joinToString("     "), style = MaterialTheme.typography.bodyMedium)
                        }
                        recipe.description?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (recipe.ingredients.isNotEmpty()) {
                    item { SectionTitle("Ingredientes") }
                    itemsIndexed(recipe.ingredients) { i, ing ->
                        val done = checked[i] == true
                        Row(
                            Modifier.fillMaxWidth().clickable { checked[i] = !done }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = done, onCheckedChange = { checked[i] = it })
                            Text(
                                ing,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (done) TextDecoration.LineThrough else null,
                                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (recipe.steps.isNotEmpty()) {
                    item { SectionTitle("Modo de preparo") }
                    val numbered = numberSteps(recipe.steps)
                    items(numbered) { (n, text) ->
                        if (n == null) {
                            Text(
                                text.removePrefix("▸").trim(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                        } else {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$n", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { uri.openUri(recipe.url) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) { Text("Ver receita original em ${recipe.site}") }
                }
            }
        }
    }
}

/** Numera os passos; títulos de seção (▸) ficam sem número. */
private fun numberSteps(steps: List<String>): List<Pair<Int?, String>> {
    var n = 0
    return steps.map { s -> if (s.startsWith("▸")) null to s else (++n) to s }
}

@Composable
fun SectionTitle(text: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp, modifier = Modifier.width(48.dp))
    }
}
