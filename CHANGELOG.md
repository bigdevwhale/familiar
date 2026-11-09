# Changelog

<!--
Telegram: end each version's section with a hidden block for the StillCraft chat. Start it with a
line "<!-- tg", then 2-4 short lines in Russian for users (what is new, what is fixed), no markdown,
then close it like any HTML comment. The release notes carry it, and the chat bot posts those lines.
-->

## [1.13.2] - 2026-11-09

### Fixed
- **The calendar paints only days of reading:** a book still being read filled every day from its start to today, so a day with nothing logged showed every book on the "Reading" shelf, and a book shelved but never opened painted days it wasn't read. Now an open book fills its days only up to its last sitting, and one never opened fills none; finished books still fill from start to finish (#30).

<!-- tg
Исправили календарь: в день, когда вы не читали, больше не появляются все книги из «Читаю».
Книга, которую добавили в «Читаю», но ещё не открывали, не закрашивает дни.
Прочитанные и перенесённые книги по-прежнему закрашивают все дни от начала до конца.
-->

## [1.13.1] - 2026-11-08

### Added
- Support Familiar: Settings → StillCraft has a fifth line, «Поддержать Фамильяра · на свечи и угощения для фамильяров», which opens the StillCraft support page in the browser.

<!-- tg
В настройках, в блоке StillCraft, появилась строчка «Поддержать Фамильяра» – на свечи и угощения для фамильяров.
-->

## [1.13.0] - 2026-11-08
- **When you began a book:** a book's page shows the day it was started, and "Edit" sets any day; a book added as already read takes its start day too (#30).
- **The calendar fills a book's days:** a day with nothing logged shows the books open then, from the day each was started to the day it was finished, so books brought over from another app paint their weeks as well (#30).
- **Two books on one day share the cell:** two covers side by side, and "+N" when there were more.
<!-- tg
Дату начала книги теперь можно поменять, как и дату окончания.
Календарь закрашивает все дни от начала книги до конца – и перенесённые книги видны целиком.
Если в один день читалось две книги, клетка делится пополам.
-->

## [1.12.2] - 2026-11-08
- **Buttons fit:** "Light the candle" and "Log" share the row by their words and go one under the other when both don't fit, so neither is cut short on a narrow screen or with large text (#31). In Russian the fed meter is called simply «Сытость», so it isn't cut either.
<!-- tg
Кнопки «Зажечь свечу» и «Отметить» больше не обрезаются: если не помещаются рядом, встают одна под другой.
-->

## [1.12.1] - 2026-11-08
- **A percent stays what you typed:** 52% on an e-book or an audiobook no longer comes back as 51% after the sitting (#29).
<!-- tg
Исправлено: прогресс в процентах больше не уменьшается на единицу – введённые 52% так и остаются 52%.
-->

## [1.12.0] - 2026-11-07
- **The classic familiars, if you miss them:** Settings → Look → Familiars → "Classic" brings back the familiars as they were before 1.10, drawn in code, with their seasonal hats and form crowns (#27). "Hand-drawn" stays the default.
<!-- tg
Вернули классических фамильяров — тех, что были до перерисовки, со шляпами и коронами.
Включаются в Настройки → Вид → Фамильяры → «Классические».
-->

## [1.11.0] - 2026-11-06

- **A one-cell widget:** "Familiar · small" shows just the familiar you read with, with a thin line of how fed it is today (#23). Both widgets now show its grown form and rare colouring too.
- **The candle for as long as you like:** Settings → Reading → "Other…" takes any length from 1 to 600 minutes (#25).

<!-- tg
Новый маленький виджет 1×1 — только твой фамильяр и тонкая полоска сытости.
Свечу теперь можно поставить на любое время: Настройки → Чтение → «Своё…».
-->

## [1.10.0] - 2026-11-06

- **Hand-drawn familiars:** all fourteen are redrawn as cute chibi pixel art, with big glossy eyes and pink cheeks, at every stage from hatchling to ancient. They still breathe, blink, fall asleep, squint with pleasure when stroked, float and turn to stone; the slime still shows what it swallowed, the moth gains stars and the ghost its wisps.
- **Forms and the rare colouring, recoloured:** each form has its own shade (starry indigo, dusky violet, rose, old gold, leaf green) and each familiar its own rare palette; the eyes, cheeks and mouth stay as drawn.
- **No more hats:** the seasonal hats and the form crowns are gone; the bloom wreath and the astral moon stay.
- **Postcards** show the familiar with its eyes open. The wisps and sparks circling a familiar pass in front of it as well as behind.
- **A new app icon** with the new kitsune.

<!-- tg
Фамильяры перерисованы: все четырнадцать теперь милые, с большими блестящими глазами — на каждой стадии.
Формы и редкий окрас получили свои красивые цвета, сезонные шляпы убраны.
На открытках фамильяр с открытыми глазами, и у приложения новая иконка.
-->

## [1.9.1] - 2026-11-03

- **Settings in three tabs:** Reading (name, daily goal, the ritual, the reminder), Look (theme, background, language) and Data (backup, books from another app, community, about). The backgrounds are a grid, all in sight.

<!-- tg
Настройки разложены по вкладкам: «Чтение», «Вид» и «Данные» — без длинной простыни. Фоны теперь видны сеткой.
-->

## [1.9.0] - 2026-11-03

- **A background picture:** behind every screen, softened so the text stays clear. A public-domain photo for each genre (Openverse, CC0; credits in `assets/backdrops/CREDITS.txt`), the genre of the book being read, or a picture of your own from the gallery. Settings → Background.
- **The day, when a book is finished:** logging a sitting with "Book finished" shows the day it was finished, and it can be set to another day.

<!-- tg
Новое: фон приложения — красивые фото по жанрам, «по книге» или своя картинка из галереи (Настройки → Фон).
Когда отмечаешь книгу дочитанной, можно сразу выбрать дату прочтения.
-->

## [1.8.0] - 2026-11-03

- **One subgenre in several genres:** "Mysticism" can sit both in horror and in mystery; a name is taken only within its own genre (#15).
- **The day a book was finished can be moved:** on a finished or abandoned book, tap Edit beside the date. Imported books that came dated the day of the import (Yandex Books shows nobody the dates) can get their real month and year back (#19).
- **A year goal of your own:** any number of books, not only the ones offered (#17).
- **Paper or e-book:** a book is on paper, an e-book or an audiobook, and the shelf filters by each. Books from Yandex Books and Bookmory come as e-books when they are (#20).
- **Russian lines fit any name:** "Призрак готово…" and two other lines no longer give the familiar a grammatical gender (#21).
- **A bell for the candle:** when the ritual candle burns out, a chime and a buzz, or a notification when the app isn't in front; it can be turned off in Settings (#22).

<!-- tg
Один и тот же поджанр теперь можно добавить к разным жанрам: «мистика» и у ужасов, и у детективов.
Дату прочтения можно поменять в карточке книги — для книг из импорта, которые получили сегодняшнюю дату.
Цель на год — любое своё число; у книги появился формат «бумажная / электронная / аудио».
Когда свеча догорает, звучит сигнал и вибрация, чтобы не зачитаться (выключается в настройках).
-->

## [1.7.0] - 2026-11-01

- **The shelf as spines:** besides covers and a list, the shelf can stand as spines on wooden planks. Thick books are thick, each keeps its own height, the spine takes the colours of its cover (or its genre's, with gold bands), the title runs down it, a book being read has its ribbon sticking out and an abandoned one fades. Cycles stand on planks of their own, volumes in order.
- **The month in covers:** a calendar where each day shows the book finished that day (with a star) or the one read most; go back month by month and tap a cover to open its book. Beside it, the year in covers: a row of finished books for every month.
- **Books finished, in numbers:** the ledger now counts this year's books or all of them: how many, pages, the average length, stars and days a book, authors; month by month or year by year; genres, your stars, how long the books were, and the authors you come back to.
- **The tree in three pages:** Overview (the tree, the goal, the saga and the guild), Calendar and the ledger, a tap away at the top instead of at the bottom of a long page.
- **A clearer den:** mana and today's feeding sit right under the familiar; with several books open, pick the one the candle is for; the den, the casket, the bestiary and visits are one row of tiles.
- **The next volume:** a cycle shows which book comes next.

<!-- tg
Полку можно показать корешками на деревянных полках: толщина по страницам, цвет от обложки, у читаемой — закладка.
Новый календарь: каждый день с обложкой книги, которую ты читал или дочитал, и год в обложках по месяцам.
Счётная книга считает дочитанное за год или за всё время: жанры, оценки, длину книг, любимых авторов.
Древо и логово стали удобнее: вкладки «Обзор / Календарь / Счётная книга», мана и сытость сразу под фамильяром.
-->

## [1.6.0] - 2026-11-01

- **Import from Yandex Books:** `tools/yandex_books.js` (paste it in the browser console on a profile page) or `tools/yandex_books.py` saves a reader's books as a CSV Familiar imports: finished, reading and wanted, with covers, genres, audiobooks and comics.
- **Import from Livelib Backup:** its CSV, JSON and Goodreads files are read as they are; the shelf comes from the file's name.
- **Import from Bookmory:** its backup file (.bookmory) is read as it is: shelves, dates, ratings, pages, covers, audiobooks and your collections as shelves.
- **A book you read before:** a new book can go straight to Read, with the day you finished it and your stars. It counts on the tree and in the stats; your familiar still eats only what you read here.
- **Every cycle when adding a book:** the cycles that match what you type, and "All cycles" when there are more than six.
- **Not going to finish it:** a book can be put down for good, with a few words on why. It goes to the new Abandoned shelf, a quiet graveyard of faded covers with their epitaphs, and can always have another chance. Finished and abandoned books keep a note of your own. "Didn't finish" from Livelib Backup and Bookmory lands there too.
- **Shelves by cycle:** the filter can show one cycle, its volumes in order, and the shelf can be sorted by cycle.
- **A clearer shelf:** shelves are tabs with how many books each holds, the order and the filter are small buttons beside the count, and covers show the author, the percent read and a mark for audiobooks. The filter sheet no longer jumps when dragged.
- **Hold + or −** where you stopped to go by ten minutes or ten pages at a time.
- The import is now Settings → Books from another app, and takes JSON too.

<!-- tg
Импорт из Яндекс Книг, Livelib Backup и Bookmory: полки переедут вместе с датами, оценками и обложками.
Кнопка «Не буду дочитывать»: книга уходит на полку «Брошено» с парой слов о том, почему.
Уже прочитанную книгу можно добавить сразу с датой и оценкой, а полку — отфильтровать по циклу, тома встанут по порядку.
Полка стала понятнее: вкладки с числом книг, автор и процент на обложках; удержание плюса листает по десять.
-->

## [1.5.0] - 2026-10-31

- **Share cards:** pictures of your reading for stories and posts, in a candlelit night or warm parchment, 9:16 or 4:5. The book you're on (its real cover, progress and the day you'll finish it), or every book on the go at once; your month (books, pages, hours, the covers you finished and a square for every day); your year (the numbers, the goal, six months in columns, your favourite genre and pace); and the den. Each card carries a code that opens Familiar on Google Play. Share them from the den, a book, the feast after a sitting, the tree, the ledger and the saga, or save them to the gallery.
- **Import from LiveLib or Goodreads** (Settings → Books from LiveLib or Goodreads): shelves, dates, ratings, pages, genres and cycles; covers are found for a Goodreads export and keep loading after the app is closed. A LiveLib export script is on the website.
- **The tree, this year or all:** the tree can show this year's books or every book you have finished.
- **A clearer den:** the book you're reading and the candle sit right under the familiar, the streak shows beside its name, and notices are slim strips you can close.
- **A lighter guild:** a reward waiting is a small button beside its errand.

<!-- tg
Новое: красивые карточки для сторис и постов — что ты читаешь, твой месяц и год в книгах, с обложками и статистикой.
Импорт книг из LiveLib и Goodreads: полки, оценки, даты и циклы переедут сами, к Goodreads подберутся обложки.
Древо может показывать книги за этот год или все прочитанные.
Логово стало проще: книга и свеча сразу под фамильяром, серия дней — рядом с его именем.
-->

## [1.4.1] - 2026-10-30

- **Fix:** covers come back with a backup. The file now carries the pictures themselves, so covers picked from the gallery survive a reinstall or a move to another phone. Backups saved before this keep only the addresses: covers from the catalogues download again, gallery ones can't be recovered from them.
- **Smoother pixels:** familiars sway, flap and float in twice as many steps, breathe in a slow rise and fall instead of a one-pixel jump, and hop smoothly when tapped in the den. Bats circle and beat their wings, the mandrake's leaves sway, the banner stirs and the cauldron's bubbles rise and wobble. Mana sparks and the tree's fireflies drift without stepping from pixel to pixel.

<!-- tg
Исправлено: обложки теперь восстанавливаются из бэкапа — картинки сохраняются прямо в файле, и обложки из галереи переживут переустановку и переезд на другой телефон.
Анимация стала плавнее: фамильяры мягко дышат и покачиваются, летучие мыши кружат, мандрагора качает листьями, пузыри в котле поднимаются.
-->

## [1.4.0] - 2026-10-28

- **The ledger:** a new page under the saga with the plain numbers: pages a day, minutes a sitting, hours by the candle; your pace in pages an hour, overall and in each genre; when you read most (morning, day, evening or night) and your best day of the week; the last six months as columns, with the books finished in each and how this month compares with the last.
- **When you'll finish:** a book you're reading shows the day you'll finish it if you go on as in the last two weeks.

<!-- tg
Новое: «Счётная книга» под сагой — скорость чтения по жанрам, когда ты читаешь чаще всего и полгода по месяцам.
На странице книги теперь видно, когда ты её дочитаешь, если читать в том же темпе.
-->

## [1.3.4] - 2026-10-25

### Added
- Community links in settings: report a bug, suggest an idea or follow updates through the StillCraft bot on Telegram, and the StillCraft chat.

<!-- tg skip -->

## [1.3.3] - 2026-10-24

- **Fix:** turning “finished the book” off again takes you back to the page you were on, not one page before the end.
- **Fix:** a percent above 100 is kept at 100.

## [1.3.2] - 2026-10-24

- **Progress in percent:** when you log a sitting or add a book, type how far you are in percent instead of the page or minute, as e-readers and audiobook apps show it. The shelf shows the percent next to the page.
- **Audiobooks in hours and minutes:** the length and where you are are typed as hours and minutes, the way shops and players show them.
- **The tree of stories:** the fireflies drift smoothly and blink each at its own pace, with a soft glow instead of a dark outline; more of them come as the tree bears fruit. The crown is even on both sides, and the fruits glow steadily.
- **Fix:** the “finished the book” switch could not be turned off again once tapped while logging a sitting.

## [1.3.1] - 2026-10-23

- **Search finds genres:** typing a genre (“mystery”, “детектив”) in the shelf search finds its books, not only your own genres and subgenres.

## [1.3.0] - 2026-10-23

- **Your own genres and subgenres:** add a subgenre to any genre (Fantasy · Dark fantasy) or a genre of your own (Manga) right in the book editor. The built-in genre still sets the mood, tails, forms and finds; your own colours the cover and sorts the shelf. Rename, move or delete them from the shelf filter.
- **Two more familiars:** the chimera, a patch on its coat for every genre of your own you finish a book in, and the magpie, a shiny thing in its nest for every subgenre, who loves staying in one genre. Fourteen in all.
- **The shelf:** an All view, search by title, author, cycle, genre or shelf, a filter by genre, subgenre, paper or audio and stars, sorting by recent, date added, title, author (by surname), rating, progress or length (tap again to reverse), and a list view with author, genre and progress. The order and the view are remembered.
- Shelves and genres that only books remember (after restoring a backup) show up again.
- The update keeps everything: books, sittings and familiars move to the new database as they are.

## [1.2.0] - 2026-10-20

- **Four more familiars:** the tanuki (loves audiobooks, grows big headphones), the sprout (a flower for every morning of reading), the ghost (a wisp for every horror or mystery, a lantern, a top hat when ancient) and the bookworm (a segment in a finished book's colour at every stage). Twelve in all.
- **Forms:** a grown familiar takes one of five forms for good, after the books it was fed on: astral, shadow, bloom, sage or wild, each with its own colours and crown. The bestiary shows which way it leans. Familiars already grown take theirs on the first start.
- **Character:** every new egg hatches sleepy, gluttonous, curious, grumpy, playful or brave, and it shows in what it says and does.
- **Finds and the casket:** after a sitting the familiar sometimes brings back a little thing from the story, one of 25, some only from one genre or one season.
- **Dreams:** in the morning after an evening of reading, a bubble shows what the familiar dreamt; dreams fill an album.
- **The familiar's draw:** a book drawn from Want to read, with a prophecy.
- **Only N more pages:** the nearest goal under the den, and live in the ritual.
- **Guardian candles:** bought with sparks, they burn by themselves on a quiet day and keep the streak (and the stone) away.
- **Seasons:** Samhain, the winter holidays, Valentine's days, World Book Day and midsummer dress up the den and the familiar, each with its own find.
- **Visits by QR:** a familiar visits a friend's den for a day, with a book to recommend and a gift. Scan with the camera or read the code from a picture.
- **Postcard:** a picture of the familiar in its den with the book and the streak, to share.
- **Search finds Russian books:** Fantlab first for anything typed in Cyrillic (Russian titles, translations, covers and page counts), Open Library for the rest. Google Books, often over its free quota, is the last resort.
- The update keeps everything: books, sittings and familiars move to the new database as they are.

## [1.1.1] - 2026-10-19

- **Fixed: 1.1.0 didn't start after updating from 1.0.0.** The database changed without a new version. Updating now starts with a fresh database and a new egg; the books, sittings and familiars from 1.0.0 are not carried over.
- A book with no page count shows an empty field in the editor instead of 0, so it can be saved.
- The first book in onboarding can get its own cover from the gallery.

## [1.1.0] - 2026-10-19

- **Read or listen.** Any book can be switched between reading and its audio version, sitting by sitting: a gramophone turns instead of the candle, the minutes count by themselves, and the page in the text is optional. Listened sittings are marked with 🎧 in the book's chronicle.
- **Log reading** without the app by the page you reached, or just by minutes of reading or listening.
- **Minutes count like pages:** they feed the daily fill, the streak, nights, guild errands and the heatmap, so a day of listening keeps the chain.
- **Pages are optional:** a book's length can be left out, and a ritual can end without a page; the familiar then eats the minutes.
- **Your own shelves**, with books on as many as you like; rename or delete them any time.
- **Your own cover** from the gallery.
- The first book in onboarding gets its own author field.
- The yarn game is gone; stroking the familiar is sideways only, so the page scrolls over the den.
- A just-made shelf opens at once; a listening ritual has its own wording.

## [1.0.0] - 2026-10-17

The first release of Familiar: a reading tracker with a pixel-art familiar that feeds on stories.

- **Eight familiars** in rich pixel art painted on the phone: the mimic, kitsune and slime (the three you can hatch first), and the dragon, phoenix, griffin, owlbear and moon moth (found by reading). Five stages from egg to ancient, each familiar with its own favourite kind of reading. About one egg in sixteen hatches shining, in a rare colouring.
- **They show what you read:** the kitsune grows a tail for every genre, the dragon sits on a hoard of finished books, the moth's wings fill with a star for every night, the slime keeps what you named inside. Moods follow the last genre read.
- **The spell of forgetting:** two quiet days and the familiar starts turning to stone from the paws up; the phoenix burns to ash instead. A few pages break the spell.
- **The den:** a pixel room furnished with sparks (one for every mana ever eaten), sixteen pieces from a moonlit window to a bubbling cauldron. Stroke the familiar to make it purr, toss it yarn once a day; it sleeps after midnight.
- **The ritual:** a candle that burns while you read, a page counter with a rustle or the page at the end, a synthesised fireplace or rain, and a stillness bonus while the phone lies face down. Audiobooks count minutes with a gramophone. Or log pages read elsewhere.
- **Find a book** in Open Library (Google Books as a fallback): title, author, pages and the real cover, kept on the phone. Or a cover drawn in the genre's colour.
- **Cycles** of books with a reward for reading them all, the tree of stories, a goal for the year with pace, three monthly guild errands and the saga of your year with a square for every day.
- An evening reminder told by the familiar, a home-screen widget, JSON backup, English and Russian, parchment and candlelight themes.
