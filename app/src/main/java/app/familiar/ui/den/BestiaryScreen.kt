package app.familiar.ui.den

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Library
import app.familiar.data.Pet
import app.familiar.domain.Form
import app.familiar.domain.Species
import app.familiar.domain.Trait
import app.familiar.ui.formGenres
import app.familiar.ui.formColor
import app.familiar.ui.formName
import app.familiar.ui.traitName
import app.familiar.ui.traitText
import androidx.compose.ui.graphics.Color
import app.familiar.domain.Stage
import app.familiar.domain.Unlocks
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.Bar
import app.familiar.ui.components.FamiliarArt
import app.familiar.ui.components.SoftButton
import app.familiar.ui.components.TopBar
import app.familiar.ui.components.UCard
import app.familiar.ui.components.UTextField
import app.familiar.ui.lookOf
import app.familiar.ui.speciesHow
import app.familiar.ui.speciesLore
import app.familiar.ui.speciesLoves
import app.familiar.ui.speciesName
import app.familiar.ui.stageName
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.launch

/** Every familiar: the ones in the den, eggs waiting to be taken, and the ones still to find. */
@Composable
fun BestiaryScreen(data: FamiliarData, go: Go) {
    val container = LocalContext.current.container
    var renaming by remember { mutableStateOf<Pet?>(null) }
    Column(Modifier.fillMaxSize().background(U.screen).statusBarsPadding()) {
        TopBar(stringResource(R.string.bestiary), { go.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(stringResource(R.string.bestiary_text), style = UType.Sub)
            Spacer(Modifier.height(12.dp))
            val s = data.stats
            // In the den first, then eggs, then the hidden ones.
            val order = Species.entries.sortedBy { sp ->
                when {
                    data.pets.any { it.species == sp.name } -> 0
                    sp in s.unlocked -> 1
                    else -> 2
                }
            }
            order.forEach { sp ->
                val pet = data.pets.firstOrNull { it.species == sp.name }
                val open = pet != null || sp in s.unlocked
                UCard(Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(96.dp).clip(U.RMd).background(U.c.surface2), contentAlignment = Alignment.Center) {
                            if (open) FamiliarArt(pet?.let { data.lookOfPet(it) } ?: lookOf(sp, 0, s, false, season = data.season), Modifier.fillMaxSize().padding(4.dp), fps = 4f)
                            else {
                                // A shadow of what's to come.
                                FamiliarArt(lookOf(sp, Stage.ADULT.from, s, false), Modifier.fillMaxSize().padding(4.dp), fps = 0f, tint = ColorFilter.tint(U.c.line))
                                Icon(Icons.Rounded.Lock, null, tint = U.c.faint, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (pet != null) pet.name else speciesName(sp), style = UType.Strong)
                            Text(
                                when {
                                    pet != null -> (if (pet.shiny) "✦ " + stringResource(R.string.shiny) + " · " else "") + "${speciesName(sp)} · ${stageName(Stage.of(pet.mana))}" + if (pet.active) " · " + stringResource(R.string.with_you) else if (data.settings.companion == pet.id) " · " + stringResource(R.string.companion_tag) else ""
                                    open -> stringResource(R.string.egg_waiting)
                                    else -> stringResource(R.string.not_found)
                                },
                                style = UType.Small.copy(color = if (pet?.active == true) U.c.accent else U.c.muted),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(if (open) speciesLore(sp) else speciesHow(sp), style = UType.Muted)
                        }
                    }
                    if (pet != null) {
                        val trait = Trait.of(pet.trait, pet.joined)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.trait_line, traitName(trait), traitText(trait)), style = UType.Small.copy(color = U.c.muted))
                        val form = Form.of(pet.form)
                        if (Stage.of(pet.mana) >= Stage.ADULT && form != Form.NONE) {
                            Text(stringResource(R.string.form_line, formName(form)), style = UType.Small.copy(color = formColor(form)), modifier = Modifier.padding(top = 2.dp))
                        } else if (Stage.of(pet.mana) < Stage.ADULT) {
                            // Which way it leans so far, from what it ate.
                            val byId = remember(data.books) { data.books.associateBy { it.id } }
                            val lean = remember(data.readings, pet.id) { Form.leaning(Library.meals(pet.id, data.readings, byId)) }
                            lean.maxByOrNull { it.value }?.let { (f, share) ->
                                Text(stringResource(R.string.form_leaning, formName(f), (share * 100).toInt()), style = UType.Small.copy(color = formColor(f)), modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.loves, speciesLoves(sp)), style = UType.Small.copy(color = U.c.gold))
                    if (!open) {
                        val (have, need) = Unlocks.progress(sp, s)
                        Spacer(Modifier.height(8.dp))
                        Bar(have / need.toFloat(), color = U.c.gold, track = U.c.goldSoft)
                        Text("$have / $need", style = UType.Small, modifier = Modifier.padding(top = 3.dp))
                    }
                    val name = speciesName(sp)
                    when {
                        pet != null && !pet.active -> {
                            val beside = data.settings.companion == pet.id
                            Spacer(Modifier.height(10.dp))
                            SoftButton(stringResource(R.string.make_active), {
                                // The one keeping company steps forward: the one you read with takes its place beside.
                                val was = data.pet?.id ?: 0L
                                container.scope.launch {
                                    if (beside) container.settings.update { it.copy(companion = was) }
                                    container.db.dao().activate(pet.id)
                                    container.changed()
                                }
                            }, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            SoftButton(
                                stringResource(if (beside) R.string.companion_unset else R.string.companion_set),
                                { container.scope.launch { container.settings.update { it.copy(companion = if (beside) 0L else pet.id) } } },
                                Modifier.fillMaxWidth(), color = if (beside) U.c.muted else U.c.mana, soft = if (beside) U.c.surface2 else U.c.manaSoft,
                            )
                        }
                        pet != null -> {
                            Spacer(Modifier.height(10.dp))
                            SoftButton(stringResource(R.string.rename), { renaming = pet }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Edit)
                        }
                        open -> {
                            Spacer(Modifier.height(10.dp))
                            SoftButton(stringResource(R.string.take_egg), { container.scope.launch { Library.adopt(container.db.dao(), sp, name); container.changed() } }, Modifier.fillMaxWidth(), color = U.c.gold, soft = U.c.goldSoft)
                        }
                    }
                }
            }
            UCard(Modifier.padding(bottom = 10.dp)) {
                Text(stringResource(R.string.forms_title), style = UType.Strong)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.forms_text), style = UType.Muted)
                Form.entries.filter { it != Form.NONE }.forEach { f ->
                    Text(stringResource(R.string.form_of, formName(f), formGenres(f)), style = UType.Small.copy(color = formColor(f)), modifier = Modifier.padding(top = 4.dp))
                }
            }
            Text(stringResource(R.string.bestiary_note) + " " + stringResource(R.string.companion_note), style = UType.Small.copy(color = U.c.muted))
        }
    }
    renaming?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = U.c.bg,
            title = { Text(stringResource(R.string.rename), style = UType.Strong) },
            text = { UTextField(name, { name = it.take(24) }) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) container.write { dao().updatePet(p.copy(name = n)) }
                    renaming = null
                }) { Text(stringResource(R.string.save), color = U.c.accent, style = UType.Button) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel), color = U.c.muted, style = UType.Button) } },
        )
    }
}
