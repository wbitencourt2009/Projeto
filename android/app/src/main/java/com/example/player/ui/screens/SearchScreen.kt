package com.example.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.player.AppViewModel.SearchResult
import com.example.player.data.Kind
import com.example.player.data.Tile
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType
import kotlinx.coroutines.delay

/**
 * Global search. Matching runs in the background and only after typing pauses,
 * so the remote keyboard never lags and big lists can't freeze the app.
 */
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    search: suspend (String) -> List<SearchResult>,
    favourites: List<String>,
    onOpen: (Tile, List<Tile>) -> Unit,
    onToggleFavourite: (String) -> Unit,
) {
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchedFor by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val favSet = remember(favourites) { favourites.toHashSet() }

    // Coming back from the player with results: focus the first result, not the keyboard.
    LaunchedEffect(Unit) {
        if (query.trim().length < 2) runCatching { field.requestFocus() }
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            searching = false
            searchedFor = ""
            return@LaunchedEffect
        }
        searching = true
        if (searchedFor.isNotEmpty()) delay(350) // wait for the user to stop typing
        results = search(q)
        searchedFor = q
        searching = false
    }

    Box(Modifier.fillMaxSize()) {
        SpaceBackground()
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 26.dp)) {
            Text("Busca geral", style = AppType.headline, color = AppColors.text)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Nome do canal, filme ou série") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    runCatching { firstResult.requestFocus() }
                }),
                trailingIcon = {
                    if (searching) CircularProgressIndicator(color = AppColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                },
                modifier = Modifier.fillMaxWidth().focusRequester(field),
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
            Spacer(Modifier.height(6.dp))
            Text(
                "Digite e aperte o botão de buscar (ou OK) no teclado para ir aos resultados.",
                style = AppType.caption,
                color = AppColors.textFaint,
            )
            Spacer(Modifier.height(12.dp))
            val q = query.trim()
            when {
                q.length < 2 -> EmptyMessage("Digite pelo menos 2 letras", "A busca procura em canais, filmes e séries ao mesmo tempo.", Modifier.fillMaxWidth())
                results.isEmpty() && searching -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AppColors.accent)
                    Spacer(Modifier.width(16.dp))
                    Text("Buscando…", style = AppType.title, color = AppColors.text)
                }
                results.isEmpty() -> EmptyMessage("Nada encontrado", "Tente outro nome ou confira a ortografia.", Modifier.fillMaxWidth())
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                    itemsIndexed(results, key = { _, r -> r.kind.name }) { rowIndex, r ->
                        Column {
                            val label = when (r.kind) {
                                Kind.LIVE -> "Canais"
                                Kind.MOVIE -> "Filmes"
                                Kind.SERIES -> "Séries"
                            }
                            val more = if (r.total > r.items.size) " · mostrando ${r.items.size}" else ""
                            Text("$label (${formatCount(r.total)})$more", style = AppType.title, color = AppColors.text)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
                            ) {
                                // Keyed by position: providers sometimes repeat the same id.
                                itemsIndexed(r.items, key = { i, t -> "$i|${t.key}" }) { i, t ->
                                    TileCard(
                                        tile = t,
                                        favourite = t.key in favSet,
                                        modifier = Modifier.width(if (r.kind == Kind.LIVE) 160.dp else 120.dp),
                                        focusModifier = if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstResult) else Modifier,
                                        onClick = { onOpen(t, r.items) },
                                        onLongClick = { onToggleFavourite(t.key) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Returning from the player with results already there: put focus on them.
    LaunchedEffect(Unit) {
        if (query.trim().length >= 2) {
            delay(400)
            runCatching { firstResult.requestFocus() }
        }
    }
}
