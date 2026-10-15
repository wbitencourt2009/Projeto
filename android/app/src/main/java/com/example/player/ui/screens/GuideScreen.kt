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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.player.data.Channel
import com.example.player.data.Programme
import com.example.player.ui.theme.AppColors
import com.example.player.ui.theme.AppType
import com.example.player.ui.theme.FocusBox
import com.example.player.ui.theme.InitialsTile
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

private const val DAYS_BACK = 2
private const val DAYS_AHEAD = 5

/**
 * TV guide: channel list on the left; on the right a day strip and the
 * schedule of the focused channel for that day, with what is on now
 * highlighted. OK on the current programme plays the channel.
 */
@Composable
fun GuideScreen(
    channels: List<Channel>,
    initialChannelId: String?,
    supported: Boolean,
    loadEpg: suspend (String) -> List<Programme>,
    onChannelFocused: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    val now = rememberNow()
    var selectedId by remember { mutableStateOf(initialChannelId ?: channels.firstOrNull()?.id) }
    var programmes by remember { mutableStateOf<List<Programme>?>(null) }
    val days = remember { dayStarts() }
    var day by remember { mutableStateOf(DAYS_BACK) }
    var expanded by remember { mutableStateOf<Long?>(null) }

    val startIndex = remember(channels) { channels.indexOfFirst { it.id == selectedId }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 3).coerceAtLeast(0))
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    // Load the schedule of the focused channel, after a short pause so scrolling stays fast.
    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        programmes = null
        expanded = null
        delay(250)
        programmes = loadEpg(id)
    }

    val dayStart = days[day]
    val dayEnd = dayStart + 24L * 60 * 60 * 1000
    val todays = remember(programmes, day) { programmes.orEmpty().filter { it.end > dayStart && it.start < dayEnd } }
    val nowIndex = todays.indexOfFirst { it.isNow(now) }
    val programmeState = rememberLazyListState()
    LaunchedEffect(todays, nowIndex) {
        if (nowIndex > 0) programmeState.scrollToItem((nowIndex - 1).coerceAtLeast(0))
    }

    Box(Modifier.fillMaxSize()) {
        SpaceBackground()
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text("Guia de programação", style = AppType.headline, color = AppColors.text, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatTime(now), style = AppType.headline, color = AppColors.text)
                    Text(formatDate(now), style = AppType.caption, color = AppColors.textMuted)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppColors.glass.copy(alpha = 0.75f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Channels
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(300.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                        GuideChannelRow(
                            channel = ch,
                            selected = ch.id == selectedId,
                            modifier = if (i == startIndex) Modifier.focusRequester(firstFocus) else Modifier,
                            onFocused = {
                                selectedId = ch.id
                                onChannelFocused(ch.id)
                            },
                            onClick = { onPlay(ch.id) },
                        )
                    }
                }

                Column(Modifier.weight(1f).fillMaxHeight()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                        itemsIndexed(days) { i, start ->
                            DayChip(start, selected = i == day, onFocused = { day = i }, onClick = { day = i })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    when {
                        !supported -> EmptyMessage(
                            "Guia indisponível",
                            "O guia de programação funciona com login Xtream Codes. Listas M3U ainda não têm guia.",
                            Modifier.fillMaxWidth(),
                        )
                        programmes == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AppColors.accent)
                        }
                        todays.isEmpty() -> EmptyMessage(
                            "Sem programação",
                            "O servidor não enviou o guia deste canal para este dia.",
                            Modifier.fillMaxWidth(),
                        )
                        else -> LazyColumn(
                            state = programmeState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(4.dp),
                        ) {
                            items(todays, key = { it.start }) { p ->
                                ProgrammeRow(
                                    programme = p,
                                    now = now,
                                    expanded = expanded == p.start,
                                    onClick = {
                                        if (p.isNow(now)) selectedId?.let(onPlay)
                                        else expanded = if (expanded == p.start) null else p.start
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "▲ ▼ canais  ·  ◀ ▶ dias e programas  ·  OK no programa atual assiste o canal  ·  OK em outro mostra a sinopse",
                style = AppType.caption,
                color = AppColors.textFaint,
            )
        }
    }
}

@Composable
private fun GuideChannelRow(channel: Channel, selected: Boolean, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    FocusBox(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        background = if (selected) AppColors.accent else AppColors.surface.copy(alpha = 0.6f),
        focusScale = 1.03f,
        onFocused = onFocused,
        onClick = onClick,
    ) { _ ->
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(width = 52.dp, height = 34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.bg.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logo != null) {
                    AsyncImage(model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                } else {
                    InitialsTile(channel.name, 34.dp, Modifier.fillMaxSize())
                }
            }
            val fg = if (selected) AppColors.onAccent else AppColors.text
            Text(channel.number?.toString() ?: "", style = AppType.caption, color = if (selected) AppColors.onAccent else AppColors.textFaint)
            Text(channel.name, style = AppType.caption.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DayChip(start: Long, selected: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    FocusBox(
        shape = RoundedCornerShape(10.dp),
        background = if (selected) AppColors.accent else AppColors.surface.copy(alpha = 0.6f),
        focusScale = 1.05f,
        onFocused = onFocused,
        onClick = onClick,
    ) { _ ->
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val fg = if (selected) AppColors.onAccent else AppColors.text
            Text(dayLabel(start), style = AppType.caption.copy(fontWeight = FontWeight.Bold), color = fg)
            Text(SimpleDateFormat("dd/MM", ptBR).format(Date(start)), style = AppType.caption, color = if (selected) AppColors.onAccent else AppColors.textMuted)
        }
    }
}

@Composable
private fun ProgrammeRow(programme: Programme, now: Long, expanded: Boolean, onClick: () -> Unit) {
    val live = programme.isNow(now)
    val past = programme.end <= now
    FocusBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        background = if (live) AppColors.accent else AppColors.surface.copy(alpha = 0.7f),
        focusScale = 1.02f,
        onClick = onClick,
    ) { _ ->
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val time = formatTime(programme.start) + " – " + formatTime(programme.end)
                val fg = when {
                    live -> AppColors.onAccent
                    past -> AppColors.textFaint
                    else -> AppColors.text
                }
                Text(time, style = AppType.caption, color = fg, modifier = Modifier.width(110.dp))
                Text(
                    (if (live) "▶  " else "") + programme.title,
                    style = AppType.caption.copy(fontWeight = if (live) FontWeight.Bold else FontWeight.Medium),
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (live) {
                    val pct = ((now - programme.start).toFloat() / (programme.end - programme.start)).coerceIn(0f, 1f)
                    Text("${(pct * 100).toInt()}%", style = AppType.caption, color = AppColors.onAccent)
                } else if (programme.hasArchive && past) {
                    Text("⟲", style = AppType.caption, color = AppColors.accent)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    programme.description ?: "Sem sinopse.",
                    style = AppType.caption,
                    color = AppColors.textMuted,
                    modifier = Modifier.padding(start = 126.dp),
                )
            }
        }
    }
}

/** Midnight of each day from DAYS_BACK days ago to DAYS_AHEAD days ahead, device time zone. */
private fun dayStarts(): List<Long> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.DAY_OF_MONTH, -DAYS_BACK)
    return (0..DAYS_BACK + DAYS_AHEAD).map {
        val t = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        t
    }
}

private fun dayLabel(start: Long): String {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val diff = Math.round((start - today) / 86_400_000.0).toInt()
    return when (diff) {
        0 -> "Hoje"
        -1 -> "Ontem"
        1 -> "Amanhã"
        else -> SimpleDateFormat("EEE", ptBR).format(Date(start)).replaceFirstChar { it.titlecase(ptBR) }.trimEnd('.')
    }
}
