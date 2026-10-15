package com.example.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.player.BrowseIds
import com.example.player.data.Category
import com.example.player.data.Content
import com.example.player.data.Kind
import com.example.player.data.Tile
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType
import com.example.player.ui.theme.FocusBox
import com.example.player.ui.theme.PillButton

private data class Entry(val id: String, val icon: String, val name: String, val count: Int)

/**
 * The list screen: a glass panel with categories and counts on the left
 * (All, Recently watched, Favourites, then the provider's categories) and a
 * poster or channel grid on the right.
 */
@Composable
fun BrowseScreen(
    kind: Kind,
    selectedId: String,
    content: Content,
    tiles: List<Tile>,
    favourites: List<String>,
    recent: List<String>,
    onSelect: (String) -> Unit,
    onOpen: (Tile, List<Tile>) -> Unit,
    onToggleFavourite: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onGuide: (() -> Unit)?,
    onBack: () -> Unit,
) {
    var menuFor by remember { mutableStateOf<Tile?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val byKey = remember(tiles) { tiles.associateBy { it.key } }
    val byCategory = remember(tiles) { tiles.groupBy { it.categoryId ?: Content.UNCATEGORISED } }
    val categories: List<Category> = remember(content, kind) {
        when (kind) {
            Kind.LIVE -> content.nonEmptyCategories
            Kind.MOVIE -> content.nonEmptyMovieCategories
            Kind.SERIES -> content.nonEmptySeriesCategories
        }
    }
    val prefix = kind.name.lowercase() + ":"
    val recentTiles = remember(recent, byKey) { recent.filter { it.startsWith(prefix) }.mapNotNull { byKey[it] } }
    val favTiles = remember(favourites, byKey) { favourites.filter { it.startsWith(prefix) }.mapNotNull { byKey[it] } }

    val entries = remember(categories, tiles, recentTiles, favTiles) {
        listOf(
            Entry(BrowseIds.ALL, "▦", "Todos", tiles.size),
            Entry(BrowseIds.RECENT, "◷", "Assistidos recentemente", recentTiles.size),
            Entry(BrowseIds.FAVOURITES, "♥", "Favoritos", favTiles.size),
        ) + categories.map { Entry(it.id, "›", it.name, byCategory[it.id]?.size ?: 0) }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }

    val visible = remember(selectedId, tiles, recentTiles, favTiles, query) {
        val base = when (selectedId) {
            BrowseIds.ALL -> tiles
            BrowseIds.RECENT -> recentTiles
            BrowseIds.FAVOURITES -> favTiles
            else -> byCategory[selectedId].orEmpty()
        }
        val q = normalise(query.trim())
        if (q.isEmpty()) base else base.filter { normalise(it.title).contains(q) }
    }
    val favSet = remember(favourites) { favourites.toHashSet() }
    val title = when (kind) {
        Kind.LIVE -> "Ao vivo"
        Kind.MOVIE -> "Filmes"
        Kind.SERIES -> "Séries"
    }
    val selectedName = entries.firstOrNull { it.id == selectedId }?.name ?: "Todos"
    val firstCategory = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstCategory.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        SpaceBackground()
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 26.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(AppColors.glass.copy(alpha = 0.72f)),
        ) {
            // Categories
            Column(
                Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(AppColors.surface.copy(alpha = 0.85f))
                    .padding(horizontal = 14.dp, vertical = 18.dp),
            ) {
                FocusBox(
                    shape = RoundedCornerShape(10.dp),
                    background = AppColors.surface.copy(alpha = 0f),
                    focusScale = 1.03f,
                    onClick = onBack,
                ) { _ ->
                    Text(
                        "‹  $title",
                        style = AppType.title,
                        color = AppColors.text,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(entries, key = { it.id }) { e ->
                        CategoryEntry(
                            entry = e,
                            selected = e.id == selectedId,
                            modifier = if (e.id == BrowseIds.ALL) Modifier.focusRequester(firstCategory) else Modifier,
                            onSelect = { onSelect(e.id) },
                        )
                    }
                }
            }

            // Grid
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 22.dp, end = 18.dp, top = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(selectedName, style = AppType.title, color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatCount(visible.size)} itens · segure OK para mais opções", style = AppType.caption, color = AppColors.textMuted)
                    }
                    if (selectedId == BrowseIds.RECENT && recentTiles.isNotEmpty()) {
                        PillButton("🗑  Limpar histórico", onClick = { confirmClear = true })
                        Spacer(Modifier.width(10.dp))
                    }
                    if (onGuide != null) {
                        PillButton("☰  Guia", onClick = onGuide)
                        Spacer(Modifier.width(10.dp))
                    }
                    PillButton(if (searching) "✕" else "⌕", onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    })
                }
                if (searching) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar em $title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.text,
                            unfocusedTextColor = AppColors.text,
                            focusedBorderColor = AppColors.focus,
                            unfocusedBorderColor = AppColors.borderInput,
                            cursorColor = AppColors.accent,
                            focusedContainerColor = AppColors.surface,
                            unfocusedContainerColor = AppColors.surface,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (visible.isEmpty()) {
                    EmptyMessage(
                        title = when (selectedId) {
                            BrowseIds.FAVOURITES -> "Nada nos favoritos ainda"
                            BrowseIds.RECENT -> "Nada assistido ainda"
                            else -> "Nenhum item aqui"
                        },
                        line = when (selectedId) {
                            BrowseIds.FAVOURITES -> "Segure OK em qualquer item para adicionar."
                            BrowseIds.RECENT -> "O que você assistir aparece aqui."
                            else -> "Escolha outra categoria."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (kind == Kind.LIVE) 150.dp else 118.dp),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(visible, key = { _, t -> t.key }) { _, t ->
                            TileCard(
                                tile = t,
                                favourite = t.key in favSet,
                                onClick = { onOpen(t, visible) },
                                onLongClick = { menuFor = t },
                            )
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { t ->
        val inRecent = t.key in recent
        OptionsDialog(
            title = t.title,
            options = buildList {
                add((if (t.key in favSet) "♥  Remover dos favoritos" else "♡  Adicionar aos favoritos") to { onToggleFavourite(t.key) })
                if (inRecent) add("🗑  Remover de assistidos recentemente" to { onRemoveRecent(t.key) })
            },
            onDismiss = { menuFor = null },
        )
    }
    if (confirmClear) {
        OptionsDialog(
            title = "Limpar todo o histórico de $title?",
            options = listOf("🗑  Sim, limpar" to onClearRecent),
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun CategoryEntry(entry: Entry, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    FocusBox(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        background = if (selected) AppColors.accent else AppColors.surface.copy(alpha = 0f),
        focusScale = 1.03f,
        onFocused = onSelect,
        onClick = onSelect,
    ) { _ ->
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val fg = if (selected) AppColors.onAccent else AppColors.text
            Text(entry.icon, style = AppType.caption, color = if (selected) AppColors.onAccent else AppColors.textMuted)
            Text(
                entry.name,
                style = AppType.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatCount(entry.count),
                style = AppType.caption,
                color = if (selected) AppColors.onAccent else AppColors.textMuted,
            )
        }
    }
}
