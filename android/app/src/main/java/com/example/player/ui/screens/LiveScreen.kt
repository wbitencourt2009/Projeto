package com.example.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.example.player.data.Channel
import com.example.player.data.Content
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType

@Composable
fun LiveScreen(
    content: Content,
    initialCategoryId: String?,
    onCategoryChange: (String) -> Unit,
    onPlay: (List<Channel>, Int) -> Unit,
) {
    val categories = remember(content) { content.nonEmptyCategories.map { it.id to it.name } }
    var selected by remember(content) { mutableStateOf(initialCategoryId ?: categories.firstOrNull()?.first) }
    val channels = remember(selected, content) { content.channelsByCategory[selected].orEmpty() }
    val firstChannel = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstChannel.requestFocus() }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg)
            .padding(horizontal = 48.dp, vertical = 27.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.width(320.dp).fillMaxHeight()) {
            Text("Ao vivo", style = AppType.headline, color = AppColors.text)
            Text("${content.channels.size} canais", style = AppType.caption, color = AppColors.textMuted)
            Spacer(Modifier.height(16.dp))
            CategoryList(categories, selected) {
                selected = it
                onCategoryChange(it)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                categories.firstOrNull { it.first == selected }?.second.orEmpty(),
                style = AppType.title,
                color = AppColors.accent,
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                    ChannelRow(
                        channel = ch,
                        modifier = if (i == 0) Modifier.focusRequester(firstChannel) else Modifier,
                        onClick = { onPlay(channels, i) },
                    )
                }
            }
        }
    }
}
