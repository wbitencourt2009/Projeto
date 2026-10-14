package com.example.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.example.player.data.Kind
import com.example.player.data.Tile
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType

private const val MAX_PER_KIND = 40

@Composable
fun SearchScreen(
    all: Map<Kind, List<Tile>>,
    favourites: List<String>,
    onOpen: (Tile, List<Tile>) -> Unit,
    onToggleFavourite: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    val index = remember(all) { all.mapValues { (_, list) -> list.map { normalise(it.title) to it } } }
    val results = remember(query, index) {
        val q = normalise(query.trim())
        if (q.length < 2) emptyMap() else index.mapValues { (_, list) -> list.filter { it.first.contains(q) }.map { it.second } }
    }
    val favSet = remember(favourites) { favourites.toHashSet() }

    Box(Modifier.fillMaxSize()) {
        SpaceBackground()
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 26.dp)) {
            Text("Busca geral", style = AppType.headline, color = AppColors.text)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Nome do canal, filme ou série") },
                singleLine = true,
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
            Spacer(Modifier.height(16.dp))
            if (query.trim().length < 2) {
                EmptyMessage("Digite pelo menos 2 letras", "A busca procura em canais, filmes e séries ao mesmo tempo.", Modifier.fillMaxWidth())
            } else if (results.values.all { it.isEmpty() }) {
                EmptyMessage("Nada encontrado", "Tente outro nome ou confira a ortografia.", Modifier.fillMaxWidth())
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                    for (kind in listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES)) {
                        val list = results[kind].orEmpty()
                        if (list.isEmpty()) continue
                        item(key = kind.name) {
                            Column {
                                val label = when (kind) {
                                    Kind.LIVE -> "Canais"
                                    Kind.MOVIE -> "Filmes"
                                    Kind.SERIES -> "Séries"
                                }
                                Text("$label (${formatCount(list.size)})", style = AppType.title, color = AppColors.text)
                                Spacer(Modifier.height(8.dp))
                                val shown = list.take(MAX_PER_KIND)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
                                ) {
                                    items(shown, key = { it.key }) { t ->
                                        TileCard(
                                            tile = t,
                                            favourite = t.key in favSet,
                                            modifier = Modifier.width(if (kind == Kind.LIVE) 160.dp else 120.dp),
                                            onClick = { onOpen(t, shown) },
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
    }
}
