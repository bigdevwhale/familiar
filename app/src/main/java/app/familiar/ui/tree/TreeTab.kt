package app.familiar.ui.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Library
import app.familiar.domain.Genre
import app.familiar.domain.Mana
import app.familiar.domain.Quests
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.books
import app.familiar.ui.components.Bar
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.SoftButton
import app.familiar.ui.components.UCard
import app.familiar.ui.components.pixels
import app.familiar.ui.components.rememberTime
import app.familiar.ui.locale
import app.familiar.ui.pix.Scenes
import app.familiar.ui.questText
import app.familiar.ui.questTitle
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import kotlin.math.hypot

@Composable
fun TreeTab(data: FamiliarData, go: Go) {
    val container = LocalContext.current.container
    val s = data.stats
    val finished = s.finished
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        Text(stringResource(R.string.tab_tree), style = UType.Display, modifier = Modifier.padding(top = 14.dp))
        Text(if (finished.isEmpty()) stringResource(R.string.tree_empty) else stringResource(R.string.tree_count, books(finished.size)), style = UType.Sub)
        Spacer(Modifier.height(12.dp))
        val colors = finished.map { Genre.of(it.genre).argb.toInt() }
        val dark = U.c.dark
        val frames by produceState<List<Pair<app.familiar.ui.pix.Px, List<Scenes.Fruit>>>?>(null, colors, dark) {
            value = withContext(Dispatchers.Default) { List(12) { Scenes.tree(colors, it, dark) } }
        }
        val bitmaps = remember(frames) { frames?.map { it.first.bitmap() } }
        val t by rememberTime()
        // Pale green-gold in the dark; deeper amber so they still show on the light ground.
        val fireflyCore = if (dark) Color(0xFFF6F7B0) else Color(0xFFD08A1E)
        val fireflyHalo = if (dark) Color(0xFFC9EE6A) else Color(0xFFF2C14E)
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(128f / 112f).clip(U.RXl).background(U.c.surface2)
                .pointerInput(frames) {
                    val fr = frames ?: return@pointerInput
                    detectTapGestures { o ->
                        val img = fr[0].first
                        val k = maxOf(1, minOf(size.width / img.w, size.height / img.h))
                        val ox = (size.width - img.w * k) / 2
                        val oy = (size.height - img.h * k) / 2
                        val px = (o.x - ox) / k
                        val py = (o.y - oy) / k
                        fr[0].second.minByOrNull { hypot(it.x - px, it.y - py) }
                            ?.takeIf { hypot(it.x - px, it.y - py) < 6f }
                            ?.let { go.book(finished[it.index].id) }
                    }
                }
        ) {
            val bm = bitmaps ?: return@Canvas
            val img = bm[(t * 4).toInt() % bm.size]
            pixels(img)
            // Fireflies over the tree, as big as its pixels, drifting smoothly between them.
            val k = maxOf(1, minOf(size.width / img.width, size.height / img.height).toInt()).toFloat()
            val ox = ((size.width - img.width * k) / 2).toInt()
            val oy = (size.height - img.height * k).toInt().coerceAtLeast(0) / 2
            Scenes.fireflies(finished.size, t).forEach { fly ->
                if (fly.light < 0.05f) return@forEach
                val x = ox + fly.x * k
                val y = oy + fly.y * k
                for ((dx, dy, a) in listOf(Triple(-1, 0, 0.5f), Triple(1, 0, 0.5f), Triple(0, -1, 0.5f), Triple(0, 1, 0.5f), Triple(-1, -1, 0.2f), Triple(1, -1, 0.2f), Triple(-1, 1, 0.2f), Triple(1, 1, 0.2f))) {
                    drawRect(fireflyHalo.copy(alpha = a * fly.light), Offset(x + dx * k, y + dy * k), Size(k, k))
                }
                drawRect(fireflyCore.copy(alpha = fly.light), Offset(x, y), Size(k, k))
            }
        }
        Text(stringResource(R.string.tree_hint), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(top = 6.dp))

        YearGoal(data)

        val month = java.time.YearMonth.from(s.today)
        SectionTitle(stringResource(R.string.guild, month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale())))
        UCard {
            Text(stringResource(R.string.guild_text, Mana.QUEST), style = UType.Muted)
            Quests.state(s).forEach { q ->
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = U.c.line)
                val claimed = q.key in data.settings.claimed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(questTitle(q.quest), style = UType.Strong)
                        Text(questText(q.quest), style = UType.Muted)
                    }
                    if (claimed) Icon(Icons.Rounded.CheckCircle, null, tint = U.c.leaf)
                }
                Spacer(Modifier.height(8.dp))
                if (q.done && !claimed) SoftButton(stringResource(R.string.claim, Mana.QUEST), {
                    container.scope.launch {
                        Library.reward(container.db.dao(), Mana.QUEST)
                        container.settings.update { it.copy(claimed = it.claimed + q.key) }
                        container.changed()
                    }
                }, Modifier.fillMaxWidth(), color = U.c.gold, soft = U.c.goldSoft)
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Bar(q.progress / q.quest.target.toFloat(), Modifier.weight(1f), color = U.c.gold, track = U.c.goldSoft)
                    Spacer(Modifier.width(10.dp))
                    Text("${q.progress} / ${q.quest.target}", style = UType.Small)
                }
            }
        }

        SectionTitle(stringResource(R.string.saga))
        UCard(onClick = { go.saga() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HistoryEdu, null, tint = U.c.accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.saga_title, s.today.year), style = UType.Strong)
                    Text(stringResource(R.string.saga_sub), style = UType.Muted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        UCard(onClick = { go.stats() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Insights, null, tint = U.c.mana)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.stats_title), style = UType.Strong)
                    Text(stringResource(R.string.stats_sub), style = UType.Muted)
                }
            }
        }
    }
}

/** Books to finish this year: progress, and whether you're ahead or behind the pace. */
@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
private fun YearGoalPicker(current: Int, onPick: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        listOf(0, 6, 12, 24, 36, 52).forEach { n ->
            app.familiar.ui.components.Chip(if (n == 0) stringResource(R.string.off) else n.toString(), current == n, { onPick(n) })
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun YearGoal(data: FamiliarData) {
    val container = LocalContext.current.container
    val s = data.stats
    val goal = data.settings.yearGoal
    var editing by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val year = s.today.year
    val done = app.familiar.domain.Year(s, year).books.size
    SectionTitle(stringResource(R.string.goal_title, year))
    UCard(onClick = { editing = !editing }) {
        if (goal <= 0) {
            Text(stringResource(R.string.goal_none), style = UType.Strong)
            Text(stringResource(R.string.goal_none_text), style = UType.Muted)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.goal_of, done, goal), style = UType.Title, modifier = Modifier.weight(1f))
                if (done >= goal) Icon(Icons.Rounded.CheckCircle, null, tint = U.c.leaf)
            }
            Spacer(Modifier.height(8.dp))
            Bar(done / goal.toFloat(), color = U.c.leaf, track = U.c.leafSoft)
            Spacer(Modifier.height(6.dp))
            // Where you should be by today if the books were spread evenly over the year.
            val due = goal * s.today.dayOfYear / s.today.lengthOfYear().toFloat()
            val diff = done - due
            Text(
                when {
                    done >= goal -> stringResource(R.string.goal_reached)
                    diff >= 0.5f -> stringResource(R.string.goal_ahead, books(kotlin.math.floor(diff).toInt().coerceAtLeast(1)))
                    diff > -1f -> stringResource(R.string.goal_on_pace)
                    else -> stringResource(R.string.goal_behind, books(kotlin.math.ceil(-diff).toInt()))
                },
                style = UType.Small.copy(color = U.c.muted),
            )
        }
        if (editing) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.goal_pick), style = UType.Muted)
            Spacer(Modifier.height(6.dp))
            YearGoalPicker(goal) { n -> container.scope.launch { container.settings.update { it.copy(yearGoal = n) } }; editing = false }
        }
    }
}
