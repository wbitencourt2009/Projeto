package com.example.player.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.player.data.AccountInfo
import com.example.player.data.Channel
import com.example.player.data.Content
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppLayout
import com.example.player.ui.theme.AppType
import com.example.player.ui.theme.FocusBox
import com.example.player.ui.theme.InitialsTile
import com.example.player.ui.theme.LiveBadge
import com.example.player.ui.theme.PillButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ROWS_ON_HOME = 12
private const val CARDS_PER_ROW = 30

@Composable
fun HomeScreen(
    content: Content,
    profileName: String,
    message: String?,
    onPlay: (List<Channel>, Int) -> Unit,
    onOpenLive: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    val rows = remember(content) {
        content.nonEmptyCategories.take(ROWS_ON_HOME).map { cat ->
            cat to content.channelsByCategory[cat.id].orEmpty()
        }
    }
    var hero by remember(content) { mutableStateOf(rows.firstOrNull()?.second?.firstOrNull()) }
    var heroCategory by remember(content) { mutableStateOf(rows.firstOrNull()?.first?.name.orEmpty()) }
    val firstCard = remember { FocusRequester() }

    LaunchedEffect(content) {
        runCatching { firstCard.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg)
            .drawBehind { drawSpaceGlow() },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item {
                TopBar(profileName, content.account, onOpenLive, onRefresh, onLogout)
            }
            item {
                Crossfade(targetState = hero, animationSpec = tween(400), label = "hero") { ch ->
                    Hero(ch, heroCategory) { ch?.let { c -> rowOf(rows, c)?.let { (list, i) -> onPlay(list, i) } } }
                }
            }
            if (message != null) {
                item {
                    Text(message, style = AppType.body, color = AppColors.warning, modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp))
                }
            }
            itemsIndexed(rows, key = { _, r -> r.first.id }) { rowIndex, (cat, channels) ->
                Column(Modifier.padding(top = 20.dp)) {
                    Row(Modifier.padding(start = 48.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                        Text(cat.name, style = AppType.title, color = AppColors.text)
                        Spacer(Modifier.width(12.dp))
                        Text("${channels.size} canais", style = AppType.caption, color = AppColors.textFaint)
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        itemsIndexed(channels.take(CARDS_PER_ROW), key = { _, c -> c.id }) { i, ch ->
                            ChannelCard(
                                channel = ch,
                                modifier = if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstCard) else Modifier,
                                onFocused = { hero = ch; heroCategory = cat.name },
                                onClick = { onPlay(channels, i) },
                            )
                        }
                        if (channels.size > CARDS_PER_ROW) {
                            item {
                                FocusBox(
                                    modifier = Modifier.width(AppLayout.cardW).height(AppLayout.cardH),
                                    onClick = onOpenLive,
                                ) {
                                    Text(
                                        "Ver todos os ${channels.size}",
                                        style = AppType.label,
                                        color = AppColors.accent,
                                        modifier = Modifier.align(Alignment.Center),
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

private fun rowOf(rows: List<Pair<com.example.player.data.Category, List<Channel>>>, ch: Channel): Pair<List<Channel>, Int>? {
    for ((_, list) in rows) {
        val i = list.indexOf(ch)
        if (i >= 0) return list to i
    }
    return null
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpaceGlow() {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(AppColors.accent.copy(alpha = 0.30f), Color.Transparent),
            center = Offset(size.width * 0.78f, size.height * 0.22f),
            radius = size.width * 0.35f,
        ),
        radius = size.width * 0.35f,
        center = Offset(size.width * 0.78f, size.height * 0.22f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(AppColors.accent2.copy(alpha = 0.28f), Color.Transparent),
            center = Offset(size.width * 0.60f, size.height * 0.45f),
            radius = size.width * 0.30f,
        ),
        radius = size.width * 0.30f,
        center = Offset(size.width * 0.60f, size.height * 0.45f),
    )
}

@Composable
private fun TopBar(
    profileName: String,
    account: AccountInfo?,
    onOpenLive: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("◉", style = AppType.headline, color = AppColors.accent)
        Text(profileName, style = AppType.title, color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        account?.expDate?.let {
            val date = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(it * 1000))
            Text("Expira em $date", style = AppType.caption, color = AppColors.textMuted)
        }
        PillButton("Ao vivo", onClick = onOpenLive)
        PillButton("Atualizar", onClick = onRefresh)
        PillButton("Sair", onClick = onLogout)
    }
}

@Composable
private fun Hero(channel: Channel?, categoryName: String, onWatch: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(AppLayout.heroHeight)
            .padding(horizontal = 48.dp),
    ) {
        if (channel == null) return@Box
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 120.dp)
                .size(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(220.dp),
                )
            } else {
                InitialsTile(channel.name, 200.dp, Modifier.size(200.dp))
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .width(760.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveBadge()
                Text(
                    (channel.number?.let { "Canal $it · " } ?: "") + categoryName.uppercase(),
                    style = AppType.caption,
                    color = AppColors.accent,
                )
            }
            Text(channel.name, style = AppType.display, color = AppColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "Use o controle para navegar pelas fileiras. OK para assistir; no player, ▲ ▼ trocam de canal.",
                style = AppType.body,
                color = AppColors.textMuted,
            )
            Spacer(Modifier.height(6.dp))
            PillButton("▶  Assistir", primary = true, onClick = onWatch)
        }
    }
}

@Composable
fun ChannelCard(
    channel: Channel,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    FocusBox(
        modifier = modifier
            .width(AppLayout.cardW)
            .height(AppLayout.cardH),
        onFocused = onFocused,
        onClick = onClick,
    ) { _ ->
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (channel.logo != null) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(56.dp),
                    )
                } else {
                    InitialsTile(channel.name, 56.dp, Modifier.size(56.dp))
                }
                Spacer(Modifier.weight(1f))
                channel.number?.let { Text(it.toString(), style = AppType.caption, color = AppColors.textFaint) }
            }
            Spacer(Modifier.weight(1f))
            Text(channel.name, style = AppType.label, color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    FocusBox(
        modifier = modifier
            .fillMaxWidth()
            .height(AppLayout.channelRowH),
        focusScale = 1.02f,
        onFocused = onFocused,
        onClick = onClick,
    ) { _ ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                channel.number?.toString() ?: "",
                style = AppType.caption,
                color = AppColors.textFaint,
                modifier = Modifier.width(48.dp),
            )
            if (channel.logo != null) {
                AsyncImage(model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(48.dp))
            } else {
                InitialsTile(channel.name, 48.dp, Modifier.size(48.dp))
            }
            Text(channel.name, style = AppType.label, color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CategoryItem(name: String, selected: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    FocusBox(
        modifier = Modifier.fillMaxWidth(),
        background = if (selected) AppColors.surfaceRaised else AppColors.surface,
        focusScale = 1.03f,
        onFocused = onFocused,
        onClick = onClick,
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(if (selected) AppColors.accent else Color.Transparent),
            )
            Text(
                name,
                style = AppType.label,
                color = if (selected) AppColors.accent else AppColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun CategoryList(names: List<Pair<String, String>>, selectedId: String?, onSelect: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(names, key = { it.first }) { (id, name) ->
            CategoryItem(name, id == selectedId, onFocused = { onSelect(id) }, onClick = { onSelect(id) })
        }
    }
}
