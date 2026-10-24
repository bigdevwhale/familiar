package app.familiar.ui.ritual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.data.Book
import app.familiar.ui.components.HoursMinutesField
import app.familiar.ui.components.PrimaryButton
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.Stepper
import app.familiar.ui.components.SwitchRow
import app.familiar.ui.components.UCard
import app.familiar.ui.components.UTextField
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType

class SittingInput(val toPage: Int, val item: String, val finish: Boolean)

/**
 * Where you stopped and (for a slime) what it should swallow. After a [timed] ritual the page can
 * be left out: then the familiar eats the minutes by the candle.
 */
@Composable
fun SittingForm(book: Book, fromPage: Int, startPage: Int, slime: Boolean, button: String, timed: Boolean = false, listen: Boolean = false, skipPage: Boolean = listen, onSave: (SittingInput) -> Unit) {
    var page by remember { mutableIntStateOf(startPage.coerceIn(fromPage, book.cap)) }
    // What was typed: a page, or for an audiobook the time in hours and minutes; or a percent.
    var typed by remember { mutableStateOf("") }
    var typedTime by remember { mutableStateOf<Int?>(null) }
    var percent by remember { mutableStateOf("") }
    var item by remember { mutableStateOf("") }
    var finish by remember { mutableStateOf(false) }
    // Where the page was before "finished" sent it to the end, to go back there when it's undone.
    var before by remember { mutableStateOf<Int?>(null) }
    // After listening the page in the text is often unknown: skipping it is the default then.
    var noPage by remember { mutableStateOf(skipPage) }
    val known = book.pages > 0
    fun moved(to: Int) { page = to.coerceIn(fromPage, book.cap) }
    Column {
        SectionTitle(stringResource(R.string.stopped_at))
        UCard {
            if (timed && !book.audio) SwitchRow(stringResource(R.string.no_page), stringResource(if (listen) R.string.no_page_listen else R.string.no_page_sub), noPage, { noPage = it })
            if (!noPage) {
                Stepper(
                    if (book.audio) stringResource(R.string.minute_at, app.familiar.ui.hm(page)) else stringResource(R.string.page_n, page), "+${page - fromPage}",
                    { moved(page - 1); typed = ""; typedTime = null; percent = "" }, { moved(page + 1); typed = ""; typedTime = null; percent = "" }, color = U.c.accent,
                )
                Text(
                    when {
                        book.audio && known -> stringResource(R.string.time_type, app.familiar.ui.hm(book.pages))
                        book.audio -> stringResource(R.string.time_type_any)
                        known -> stringResource(R.string.page_type, book.pages)
                        else -> stringResource(R.string.page_type_any)
                    },
                    style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (book.audio) HoursMinutesField(typedTime, { v -> typedTime = v; percent = ""; v?.let(::moved) }, Modifier.weight(2f))
                    else UTextField(typed, {
                        typed = it.filter(Char::isDigit).take(5)
                        percent = ""
                        typed.toIntOrNull()?.let(::moved)
                    }, Modifier.weight(2f), number = true, placeholder = stringResource(R.string.page_short))
                    // Readers and players that only show how far you are: a percent works too.
                    if (known) UTextField(percent, {
                        percent = it.filter(Char::isDigit).take(3).let { p -> if ((p.toIntOrNull() ?: 0) > 100) "100" else p }
                        typed = ""; typedTime = null
                        percent.toIntOrNull()?.let { p -> moved(Book.atPercent(book.pages, p)) }
                    }, Modifier.weight(1f), number = true, placeholder = Book.percentOf(book.pages, page).toString(), suffix = "%")
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(if (book.audio) R.string.listened_now else R.string.read_now, app.familiar.ui.amount(page - fromPage, book.audio)), style = UType.Small.copy(color = U.c.muted))
            }
            // Finished when said so or when the last page is reached. Undoing it goes back to where
            // the page was before, else the last page would keep the switch on.
            val atEnd = known && page >= book.pages && !noPage
            SwitchRow(stringResource(R.string.finished_book), null, finish || atEnd, { on ->
                finish = on
                if (on && known && !noPage && !atEnd) { before = page; page = book.pages }
                if (!on) {
                    if (atEnd && fromPage < book.pages) page = (before ?: (page - 1)).coerceIn(fromPage, book.pages - 1)
                    before = null
                }
                typed = ""; typedTime = null; percent = ""
            })
        }
        if (slime) {
            SectionTitle(stringResource(R.string.slime_item))
            UTextField(item, { item = it.take(30) }, placeholder = stringResource(R.string.slime_item_hint))
            Text(stringResource(R.string.slime_item_note), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(button, {
            val to = if (noPage) fromPage else page
            onSave(SittingInput(to, item, finish || (known && to >= book.pages)))
        }, Modifier.fillMaxWidth())
    }
}
