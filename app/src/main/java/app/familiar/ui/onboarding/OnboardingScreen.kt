package app.familiar.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Book
import app.familiar.data.Library
import app.familiar.domain.Genre
import app.familiar.domain.Species
import app.familiar.domain.Stage
import app.familiar.ui.components.Chip
import app.familiar.ui.components.Den
import app.familiar.ui.components.FamiliarArt
import app.familiar.ui.components.PrimaryButton
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.SelectCard
import app.familiar.ui.components.SwitchRow
import app.familiar.ui.components.UTextField
import app.familiar.ui.genreColor
import app.familiar.ui.genreName
import app.familiar.ui.pix.Look
import app.familiar.ui.speciesLore
import app.familiar.ui.speciesLoves
import app.familiar.ui.speciesName
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val container = LocalContext.current.container
    var step by remember { mutableIntStateOf(0) }
    var species by remember { mutableStateOf(Species.KITSUNE) }
    val speciesNames = Species.entries.associateWith { speciesName(it) }
    var petName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf(Genre.FANTASY) }
    var cover by remember { mutableStateOf("") }
    var remind by remember { mutableStateOf(true) }
    BackHandler(step > 0) { step-- }
    Column(Modifier.fillMaxSize().background(U.screen).statusBarsPadding().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(18.dp))
            when (step) {
                0 -> {
                    Den(Modifier.fillMaxWidth().height(260.dp)) {
                        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Species.entries.filter { it.starter }.forEach { sp ->
                                FamiliarArt(Look(sp, Stage.EGG, hatch = 0.4f), Modifier.size(100.dp), fps = 4f)
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(stringResource(R.string.app_name), style = UType.Display)
                    Text(stringResource(R.string.ob_tagline), style = UType.Strong.copy(color = U.c.accent))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.ob_intro), style = UType.Sub)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.ob_intro2), style = UType.Sub)
                }
                1 -> {
                    Text(stringResource(R.string.ob_choose), style = UType.Title)
                    Text(stringResource(R.string.ob_choose_text), style = UType.Sub)
                    Spacer(Modifier.height(14.dp))
                    Species.entries.filter { it.starter }.forEach { sp ->
                        SelectCard(species == sp, { species = sp }, U.c.accent, Modifier.padding(bottom = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FamiliarArt(Look(sp, Stage.BABY), Modifier.size(84.dp), fps = 4f)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(speciesName(sp), style = UType.Strong)
                                    Text(speciesLore(sp), style = UType.Muted)
                                    Spacer(Modifier.height(4.dp))
                                    Text(stringResource(R.string.loves, speciesLoves(sp)), style = UType.Small.copy(color = U.c.gold))
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.ob_others), style = UType.Small.copy(color = U.c.muted))
                }
                2 -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        FamiliarArt(Look(species, Stage.EGG, hatch = 0.7f), Modifier.size(150.dp))
                    }
                    Text(stringResource(R.string.ob_names), style = UType.Title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    SectionTitle(stringResource(R.string.ob_pet_name))
                    UTextField(petName, { petName = it.take(24) }, placeholder = speciesNames[species] ?: "")
                    SectionTitle(stringResource(R.string.ob_your_name))
                    UTextField(name, { name = it.take(30) }, placeholder = stringResource(R.string.ob_name_hint))
                }
                else -> {
                    Text(stringResource(R.string.ob_book), style = UType.Title)
                    Text(stringResource(R.string.ob_book_text), style = UType.Sub)
                    SectionTitle(stringResource(R.string.search_title))
                    app.familiar.ui.books.BookSearch { f ->
                        title = f.title
                        author = f.author
                        f.pages?.let { pages = it.toString() }
                        cover = f.cover
                    }
                    SectionTitle(stringResource(R.string.f_cover))
                    app.familiar.ui.books.CoverPicker(title, genre, cover) { cover = it }
                    SectionTitle(stringResource(R.string.f_title))
                    UTextField(title, { title = it.take(120) }, placeholder = stringResource(R.string.f_title_hint))
                    SectionTitle(stringResource(R.string.f_author))
                    UTextField(author, { author = it.take(80) }, placeholder = stringResource(R.string.f_author_hint))
                    SectionTitle(stringResource(R.string.f_pages))
                    UTextField(pages, { pages = it.filter(Char::isDigit).take(5) }, number = true, placeholder = stringResource(R.string.f_pages_optional))
                    SectionTitle(stringResource(R.string.f_genre))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Genre.entries.forEach { g -> Chip(genreName(g), genre == g, { genre = g }, genreColor(g)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(stringResource(R.string.ob_remind), stringResource(R.string.ob_remind_sub), remind, { remind = it })
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        val bookOk = title.isNotBlank()
        PrimaryButton(
            stringResource(
                when {
                    step < 3 -> R.string.next
                    bookOk -> R.string.ob_start
                    else -> R.string.ob_skip_book
                }
            ),
            {
                if (step < 3) step++
                else {
                    val pet = petName.trim().ifEmpty { speciesNames[species] ?: species.name }
                    container.scope.launch {
                        val dao = container.db.dao()
                        val now = System.currentTimeMillis()
                        Library.adopt(dao, species, pet, now)
                        if (bookOk) dao.insertBook(Book(title = title.trim(), author = author.trim(), pages = pages.toIntOrNull() ?: 0, genre = genre.name, added = now, started = now, cover = cover))
                        container.settings.update {
                            it.copy(onboarded = true, name = name.trim(), remindAt = if (remind) 21 * 60 else -1, seenUnlocks = it.seenUnlocks + species.name)
                        }
                        container.changed()
                    }
                    onDone()
                }
            },
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp).navigationBarsPadding(),
        )
    }
}
