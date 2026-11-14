package app.familiar.ui.books

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Book
import app.familiar.data.Quote
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.BookCover
import app.familiar.ui.components.SoftButton
import app.familiar.ui.components.TopBar
import app.familiar.ui.components.UCard
import app.familiar.ui.components.UTextField
import app.familiar.ui.hm
import app.familiar.ui.share.Cards
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType

const val QUOTE_MAX = 1_000

/** Where a quote stands in its book: "p. 42", or the time for an audiobook; empty when not said. */
@Composable
fun quotePlace(q: Quote, book: Book?): String = when {
    q.page <= 0 -> ""
    book?.audio == true -> hm(q.page)
    else -> stringResource(R.string.quote_page, q.page)
}

/** The album of quotes: every line copied out, newest first, or only those of the book [bookId]. */
@Composable
fun QuotesScreen(bookId: Long, data: FamiliarData, go: Go) {
    val context = LocalContext.current
    val container = context.container
    val only = data.book(bookId)
    val quotes = if (only != null) data.quotes.filter { it.bookId == only.id } else data.quotes
    var editing by remember { mutableStateOf<Quote?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(U.screen).statusBarsPadding()) {
        TopBar(stringResource(R.string.quotes), { go.back() }) {
            if (only != null) IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.quote_add), tint = U.c.muted) }
        }
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp)) {
            item {
                Column(Modifier.padding(bottom = 12.dp)) {
                    Text(if (only != null) only.title else stringResource(R.string.quotes_text), style = UType.Sub)
                    if (quotes.isEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Icon(Icons.Rounded.FormatQuote, null, tint = U.c.faint, modifier = Modifier.size(40.dp))
                        Text(stringResource(R.string.quotes_empty), style = UType.Muted, modifier = Modifier.padding(top = 6.dp))
                        if (only != null) SoftButton(stringResource(R.string.quote_add), { adding = true }, Modifier.fillMaxWidth().padding(top = 14.dp), icon = Icons.Rounded.Add)
                    }
                }
            }
            items(quotes, key = { it.id }) { q ->
                val book = data.book(q.bookId)
                val place = quotePlace(q, book)
                UCard(Modifier.padding(bottom = 10.dp)) {
                    Text(stringResource(R.string.quote_marks, q.text), style = UType.Quote)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (book != null && only == null) {
                            BookCover(book, Modifier.width(26.dp).height(38.dp), small = true, bare = true)
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            if (book != null && only == null) Text(book.title, style = UType.Small.copy(color = U.c.ink), maxLines = 1)
                            Text(
                                listOfNotNull(book?.author?.takeIf { it.isNotBlank() && only == null }, place.takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = UType.Small.copy(color = U.c.muted), maxLines = 1,
                            )
                        }
                        IconButton(onClick = { go.share(Cards.Kind.QUOTE, q.id) }) { Icon(Icons.Rounded.Image, stringResource(R.string.share_title), tint = U.c.muted, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = {
                            val by = listOfNotNull(book?.author?.takeIf { it.isNotBlank() }, book?.title).joinToString(", ")
                            val text = context.getString(R.string.quote_marks, q.text) + if (by.isNotBlank()) "\n— $by" else ""
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                        }) { Icon(Icons.Rounded.Share, stringResource(R.string.share_as_text), tint = U.c.muted, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { editing = q }) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit), tint = U.c.muted, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
    if (adding && only != null) QuoteDialog(only, null, { adding = false }) { text, page ->
        container.write { dao().insertQuote(Quote(bookId = only.id, text = text, page = page, at = System.currentTimeMillis())) }
    }
    editing?.let { q ->
        QuoteDialog(
            data.book(q.bookId), q, { editing = null },
            onDelete = { container.write { dao().deleteQuote(q) } },
        ) { text, page -> container.write { dao().updateQuote(q.copy(text = text, page = page)) } }
    }
}

/**
 * Copies a line out of [book] (or mends [quote]): the words, and the page they are on if you care to say;
 * [page] is offered for a new one (the page the ritual is on).
 */
@Composable
fun QuoteDialog(book: Book?, quote: Quote?, onDismiss: () -> Unit, page: Int = 0, onDelete: (() -> Unit)? = null, onSave: (String, Int) -> Unit) {
    var text by remember { mutableStateOf(quote?.text.orEmpty()) }
    var at by remember { mutableStateOf((quote?.page ?: page).takeIf { it > 0 }?.toString().orEmpty()) }
    val ok = text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = U.c.bg,
        title = { Text(stringResource(if (quote == null) R.string.quote_add else R.string.quote_edit), style = UType.Strong) },
        text = {
            Column {
                if (book != null) Text(book.title, style = UType.Small.copy(color = U.c.muted), maxLines = 1, modifier = Modifier.padding(bottom = 8.dp))
                UTextField(text, { text = it.take(QUOTE_MAX) }, placeholder = stringResource(R.string.quote_hint), minLines = 4)
                // An audiobook has no pages to point at.
                if (book?.audio != true) {
                    Spacer(Modifier.height(8.dp))
                    UTextField(at, { at = it.filter(Char::isDigit).take(5) }, placeholder = stringResource(R.string.page_short), number = true)
                }
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text(stringResource(R.string.delete), color = U.c.danger, style = UType.Button) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (ok) { onSave(text.trim(), at.toIntOrNull() ?: 0); onDismiss() } }, enabled = ok) {
                Text(stringResource(R.string.save), color = if (ok) U.c.accent else U.c.faint, style = UType.Button)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = U.c.muted, style = UType.Button) } },
    )
}
