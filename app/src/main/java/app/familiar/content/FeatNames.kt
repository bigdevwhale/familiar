package app.familiar.content

import app.familiar.domain.Achievement
import app.familiar.domain.Feat
import java.util.Locale

/**
 * The name of every achievement (one per step of its measure) and what it is given for; "{n}" stands for
 * the step's number. Russian lines are phrased so the number needs no agreement ("Дочитано книг: 25").
 */
object FeatNames {
    private class Text(val what: String, vararg val names: String)

    fun name(locale: Locale, a: Achievement): String = of(locale)[a.feat]?.names?.getOrNull(a.index) ?: a.id

    fun what(locale: Locale, a: Achievement): String = of(locale)[a.feat]?.what.orEmpty().replace("{n}", String.format(locale, "%,d", a.target))

    private fun of(locale: Locale) = if (locale.language == "ru") RU else EN

    /** Every measure has a name for each of its steps, in both languages: checked by a test. */
    fun complete(): Boolean = listOf(EN, RU).all { m -> Feat.entries.all { f -> m[f]?.names?.size == f.steps.size && m[f]!!.what.isNotBlank() } }

    private val EN: Map<Feat, Text> = mapOf(
        Feat.BOOKS to Text("Books finished: {n}", "The first one", "A handful of stories", "Double digits", "Quarter of a hundred", "Half a hundred", "Centurion", "Walking library", "Legend of the shelves"),
        Feat.PAGES to Text("Pages read: {n}", "A hundred pages", "A thousand pages", "Paper trail", "Ten thousand leaves", "Page turner", "Paper mountain", "A hundred thousand pages"),
        Feat.HOURS to Text("Hours by the candle or the gramophone: {n}", "An hour by the candle", "Ten quiet hours", "Fifty hours", "A hundred hours of candlelight", "Keeper of the flame"),
        Feat.DAY_PAGES to Text("Pages read in a single day: {n}", "A good evening", "A hundred in a day", "Couldn't put it down", "Devoured"),
        Feat.BRICK to Text("Finish a book of {n} pages or more", "Brick", "Doorstop", "Monument"),
        Feat.LITTLE to Text("Poems and books under 150 pages finished: {n}", "Pocket-sized", "Slim volumes", "Small is beautiful"),
        Feat.MONTH_BOOKS to Text("Books finished in one month: {n}", "A good month", "A book a week", "Reading spree"),
        Feat.ONE_DAY to Text("Books of 100 pages or more read in a single day: {n}", "In one sitting", "Gone by sunset"),
        Feat.LISTEN to Text("Times listened: {n}", "Good listener", "All ears", "A voice in the headphones", "Audiophile"),
        Feat.REREAD to Text("Books read again to the end: {n}", "Once more", "Old friends", "By heart"),

        Feat.STREAK to Text("Days of reading in a row: {n}", "Three in a row", "A whole week", "Fortnight", "A month without a miss", "Two months strong", "A hundred days", "A year, every day"),
        Feat.DAYS to Text("Days with reading: {n}", "Ten days of reading", "Fifty days", "A hundred days with a book", "A year's worth of days", "A thousand and one nights"),
        Feat.NIGHTS to Text("Times read after ten in the evening: {n}", "Night owl", "Past bedtime", "Midnight oil", "Reader by moonlight"),
        Feat.MORNINGS to Text("Times read before ten in the morning: {n}", "Early bird", "Coffee and a chapter", "Dawn chorus", "Lark"),
        Feat.MIDNIGHT to Text("Times read between midnight and four: {n}", "Witching hour", "One more chapter"),
        Feat.RITUALS to Text("Rituals: {n}", "First candle", "Ten candles", "Candle maker", "A hundred rituals", "Grand ritual"),
        Feat.LONG_SIT to Text("A ritual {n} minutes long", "Half an hour", "A full hour", "Double feature", "Marathon"),
        Feat.QUIET to Text("Rituals with ten quiet minutes face down: {n}", "Face down", "Do not disturb", "Deep quiet"),
        Feat.FIRES to Text("Rituals by the fire: {n}", "By the fire", "Hearth and home", "Fire keeper"),
        Feat.RAIN to Text("Rituals in the rain: {n}", "Rainy day", "Drizzle and a book", "Rainmaker"),
        Feat.WEEKENDS to Text("Weekends with reading on both days: {n}", "Weekend reader", "Ten good weekends", "Every weekend"),
        Feat.FULL_MONTHS to Text("Calendar months with reading every day: {n}", "Not a day without a line", "A season of pages"),
        Feat.COMEBACK to Text("Come back to reading after five quiet days", "Out of the ashes"),
        Feat.CANDLES to Text("Days a guardian candle kept the streak: {n}", "Guarded", "Guardian flame"),
        Feat.YEAR_GOAL to Text("Reach your goal for the year", "A promise kept"),

        Feat.GENRES to Text("Genres with a finished book: {n}", "Taster", "Wide reader", "Omnivore"),
        Feat.FANTASY to Text("Fantasy finished: {n}", "Through the wardrobe", "Sword and sorcery", "Archmage"),
        Feat.SCIFI to Text("Science fiction finished: {n}", "Lift-off", "Star charts", "Captain of the fleet"),
        Feat.MYSTERY to Text("Mysteries finished: {n}", "First clue", "Private eye", "Great detective"),
        Feat.HORROR to Text("Horror finished: {n}", "Lights on", "Things in the dark", "Fearless"),
        Feat.ROMANCE to Text("Romance finished: {n}", "First blush", "Heart on the sleeve", "Hopeless romantic"),
        Feat.ADVENTURE to Text("Adventure finished: {n}", "Setting sail", "Treasure map", "Seasoned traveller"),
        Feat.CLASSIC to Text("Classics finished: {n}", "Dusty spine", "Well read", "Keeper of the canon"),
        Feat.HISTORY to Text("History finished: {n}", "Once upon a century", "Chronicler", "Time traveller"),
        Feat.NONFICTION to Text("Non-fiction finished: {n}", "Curious mind", "Know-it-all", "Polymath"),
        Feat.POETRY to Text("Poetry finished: {n}", "First verse", "Rhyme and reason", "Poet at heart"),
        Feat.SERIES to Text("Cycles read to the last volume: {n}", "The whole story", "Three cycles", "Saga hunter"),
        Feat.AUTHOR to Text("Books by one author: {n}", "A familiar voice", "Devoted reader", "Complete works"),
        Feat.AUTHORS to Text("Authors read: {n}", "Ten voices", "A chorus of fifty"),
        Feat.RATED to Text("Books rated: {n}", "Critic", "Reviewer", "Jury of one"),
        Feat.FIVE_STARS to Text("Books given five stars: {n}", "Five stars", "Constellation", "Hall of fame"),
        Feat.QUOTES to Text("Quotes copied out: {n}", "The first line kept", "Commonplace book", "Collector of lines", "Quotable"),
        Feat.NOTES to Text("Books with a note: {n}", "In the margins", "Marginalia"),
        Feat.WANT to Text("Books on the want-to-read shelf: {n}", "So many books", "So little time"),
        Feat.ABANDONED to Text("Books put down for good: {n}", "Life is short", "The right to stop"),
        Feat.FORMATS to Text("Finish a book on paper, an e-book and an audiobook", "Paper, screen and voice"),
        Feat.OWN to Text("Shelves and genres of your own: {n}", "A shelf of one's own", "Cataloguer"),
        Feat.LIBRARY to Text("Books in the library: {n}", "Home library", "Reading room", "The great library"),

        Feat.PETS to Text("Familiars in the den: {n}", "Good company", "Lucky seven", "Menagerie", "The whole bestiary"),
        Feat.GROWN to Text("Grown familiars: {n}", "All grown up", "Five grown", "Ten grown"),
        Feat.ANCIENT to Text("Ancient familiars: {n}", "The ancient one", "Council of elders", "Seven ancients"),
        Feat.FORMS to Text("Different forms among your familiars: {n}", "True form", "Three forms", "Every form"),
        Feat.TRAITS to Text("Different characters in the den: {n}", "Characters", "Every temper"),
        Feat.MANA to Text("Mana eaten by all your familiars: {n}", "A thousand mana", "Well of mana", "River of mana", "Ocean of mana"),
        Feat.STARTERS to Text("Bring the mimic, the kitsune and the slime together", "The first three"),
        Feat.COMPANION to Text("Settle a second familiar in the den", "Two's company"),
        Feat.GUESTS to Text("Guests welcomed: {n}", "Open door", "Host of the den"),
        Feat.SHINY to Text("Hatch a shining familiar", "One in sixteen"),

        Feat.FINDS to Text("Different finds in the casket: {n}", "Odds and ends", "Cabinet of curiosities", "The full casket"),
        Feat.SEASONAL to Text("Seasonal finds: {n}", "A find of the season", "All year round"),
        Feat.TREASURE to Text("Finds brought back in all: {n}", "Magpie's nest", "Dragon's hoard"),
        Feat.DREAMS to Text("Dreams in the album: {n}", "Sweet dream", "Dreamer", "Album of dreams"),
        Feat.FURNITURE to Text("Pieces bought for the den: {n}", "The first chair", "Cosy", "Fully furnished", "Collector's den"),
        Feat.QUESTS to Text("Guild errands done: {n}", "Errand runner", "Guild member", "Guild master"),
        Feat.ITEMS to Text("Things the slime swallowed: {n}", "Swallowed whole", "Jelly museum"),
    )

    private val RU: Map<Feat, Text> = mapOf(
        Feat.BOOKS to Text("Дочитано книг: {n}", "Первая", "Пригоршня историй", "Десяточка", "Четверть сотни", "Полсотни", "Сотня", "Ходячая библиотека", "Легенда полок"),
        Feat.PAGES to Text("Прочитано страниц: {n}", "Сто страниц", "Тысяча страниц", "Бумажный след", "Десять тысяч листов", "Листатель", "Бумажная гора", "Сто тысяч страниц"),
        Feat.HOURS to Text("Часов со свечой или граммофоном: {n}", "Час при свече", "Десять тихих часов", "Полсотни часов", "Сто часов при свече", "Хранитель огня"),
        Feat.DAY_PAGES to Text("Страниц за один день: {n}", "Хороший вечер", "Сотня за день", "Не оторваться", "Проглочено"),
        Feat.BRICK to Text("Дочитай книгу от {n} страниц", "Кирпич", "Томище", "Монумент"),
        Feat.LITTLE to Text("Дочитано стихов и книг до 150 страниц: {n}", "Карманный формат", "Тонкие книжки", "Мал золотник"),
        Feat.MONTH_BOOKS to Text("Книг за один месяц: {n}", "Хороший месяц", "Книга в неделю", "Книжный запой"),
        Feat.ONE_DAY to Text("Книг от 100 страниц, прочитанных за один день: {n}", "За один день", "До заката"),
        Feat.LISTEN to Text("Прослушиваний: {n}", "Хороший слушатель", "Ушки на макушке", "Голос в наушниках", "Аудиофил"),
        Feat.REREAD to Text("Перечитано книг: {n}", "Ещё разок", "Старые друзья", "Наизусть"),

        Feat.STREAK to Text("Дней чтения подряд: {n}", "Три дня подряд", "Целая неделя", "Две недели", "Месяц без пропусков", "Два месяца", "Сто дней", "Год каждый день"),
        Feat.DAYS to Text("Дней с чтением: {n}", "Десять дней чтения", "Полсотни дней", "Сто дней с книгой", "Год в днях", "Тысяча и одна ночь"),
        Feat.NIGHTS to Text("Чтений после десяти вечера: {n}", "Сова", "После отбоя", "Ночные бдения", "Читатель при луне"),
        Feat.MORNINGS to Text("Чтений до десяти утра: {n}", "Ранняя пташка", "Кофе и глава", "Утренний хор", "Жаворонок"),
        Feat.MIDNIGHT to Text("Чтений между полуночью и четырьмя утра: {n}", "Ведьмин час", "Ещё одну главу"),
        Feat.RITUALS to Text("Ритуалов: {n}", "Первая свеча", "Десять свечей", "Свечных дел мастер", "Сто ритуалов", "Великий ритуал"),
        Feat.LONG_SIT to Text("Ритуал длиной {n} минут", "Полчаса", "Целый час", "Двойной сеанс", "Марафон"),
        Feat.QUIET to Text("Ритуалов с десятью минутами экраном вниз: {n}", "Экраном вниз", "Не беспокоить", "Глубокая тишина"),
        Feat.FIRES to Text("Ритуалов под камин: {n}", "У камина", "Домашний очаг", "Хранитель очага"),
        Feat.RAIN to Text("Ритуалов под дождь: {n}", "Дождливый день", "Дождь и книга", "Заклинатель дождя"),
        Feat.WEEKENDS to Text("Выходных с чтением в оба дня: {n}", "Читатель выходного дня", "Десять хороших выходных", "Каждые выходные"),
        Feat.FULL_MONTHS to Text("Месяцев с чтением каждый день: {n}", "Ни дня без строчки", "Сезон страниц"),
        Feat.COMEBACK to Text("Вернись к чтению после пяти дней тишины", "Из пепла"),
        Feat.CANDLES to Text("Дней, когда серию спасла свеча-хранитель: {n}", "Под охраной", "Пламя-хранитель"),
        Feat.YEAR_GOAL to Text("Выполни цель на год", "Слово сдержано"),

        Feat.GENRES to Text("Жанров с дочитанной книгой: {n}", "Дегустатор", "Широкий круг", "Всеядный"),
        Feat.FANTASY to Text("Дочитано фэнтези: {n}", "Сквозь шкаф", "Меч и магия", "Архимаг"),
        Feat.SCIFI to Text("Дочитано фантастики: {n}", "Взлёт", "Звёздные карты", "Капитан флота"),
        Feat.MYSTERY to Text("Дочитано детективов: {n}", "Первая улика", "Частный сыщик", "Великий детектив"),
        Feat.HORROR to Text("Дочитано ужасов: {n}", "Со светом", "Кто там в темноте", "Бесстрашный"),
        Feat.ROMANCE to Text("Дочитано романов о любви: {n}", "Первый румянец", "Сердце нараспашку", "Неисправимый романтик"),
        Feat.ADVENTURE to Text("Дочитано приключений: {n}", "Поднять паруса", "Карта сокровищ", "Бывалый путешественник"),
        Feat.CLASSIC to Text("Дочитано классики: {n}", "Пыльный корешок", "Начитанность", "Хранитель канона"),
        Feat.HISTORY to Text("Дочитано книг по истории: {n}", "Давным-давно", "Летописец", "Путешественник во времени"),
        Feat.NONFICTION to Text("Дочитано нон-фикшна: {n}", "Пытливый ум", "Всезнайка", "Энциклопедист"),
        Feat.POETRY to Text("Дочитано поэзии: {n}", "Первая строфа", "В рифму", "Поэт в душе"),
        Feat.SERIES to Text("Циклов, дочитанных до последнего тома: {n}", "Вся история", "Три цикла", "Охотник за сагами"),
        Feat.AUTHOR to Text("Книг одного автора: {n}", "Знакомый голос", "Верный читатель", "Собрание сочинений"),
        Feat.AUTHORS to Text("Прочитано авторов: {n}", "Десять голосов", "Хор из пятидесяти"),
        Feat.RATED to Text("Оценено книг: {n}", "Критик", "Рецензент", "Жюри из одного"),
        Feat.FIVE_STARS to Text("Книг на пять звёзд: {n}", "Пять звёзд", "Созвездие", "Зал славы"),
        Feat.QUOTES to Text("Выписано цитат: {n}", "Первая строчка", "Тетрадь цитат", "Собиратель строк", "Цитатник"),
        Feat.NOTES to Text("Книг с заметкой: {n}", "На полях", "Маргиналии"),
        Feat.WANT to Text("Книг на полке «Хочу прочитать»: {n}", "Столько книг", "Так мало времени"),
        Feat.ABANDONED to Text("Отложено насовсем: {n}", "Жизнь коротка", "Право не дочитывать"),
        Feat.FORMATS to Text("Дочитай бумажную, электронную и аудиокнигу", "Бумага, экран и голос"),
        Feat.OWN to Text("Своих полок и жанров: {n}", "Своя полка", "Каталогизатор"),
        Feat.LIBRARY to Text("Книг в библиотеке: {n}", "Домашняя библиотека", "Читальный зал", "Великая библиотека"),

        Feat.PETS to Text("Фамильяров в логове: {n}", "Хорошая компания", "Счастливая семёрка", "Зверинец", "Весь бестиарий"),
        Feat.GROWN to Text("Взрослых фамильяров: {n}", "Совсем большой", "Пятеро взрослых", "Десять взрослых"),
        Feat.ANCIENT to Text("Древних фамильяров: {n}", "Древний", "Совет старейшин", "Семеро древних"),
        Feat.FORMS to Text("Разных форм у фамильяров: {n}", "Истинная форма", "Три формы", "Все формы"),
        Feat.TRAITS to Text("Разных характеров в логове: {n}", "Характеры", "Все нравы"),
        Feat.MANA to Text("Маны съедено всеми фамильярами: {n}", "Тысяча маны", "Колодец маны", "Река маны", "Океан маны"),
        Feat.STARTERS to Text("Собери мимика, кицунэ и слайма", "Первая троица"),
        Feat.COMPANION to Text("Посели в логове второго фамильяра", "Вдвоём веселее"),
        Feat.GUESTS to Text("Принято гостей: {n}", "Открытая дверь", "Радушный хозяин"),
        Feat.SHINY to Text("Вылупи сияющего фамильяра", "Один из шестнадцати"),

        Feat.FINDS to Text("Разных находок в шкатулке: {n}", "Всякая всячина", "Кабинет редкостей", "Полная шкатулка"),
        Feat.SEASONAL to Text("Сезонных находок: {n}", "Находка сезона", "Круглый год"),
        Feat.TREASURE to Text("Всего принесено находок: {n}", "Сорочье гнездо", "Драконий клад"),
        Feat.DREAMS to Text("Снов в альбоме: {n}", "Сладкий сон", "Мечтатель", "Альбом снов"),
        Feat.FURNITURE to Text("Куплено вещей для логова: {n}", "Первое кресло", "Уютно", "Обставлено", "Логово коллекционера"),
        Feat.QUESTS to Text("Выполнено поручений гильдии: {n}", "На побегушках", "Член гильдии", "Мастер гильдии"),
        Feat.ITEMS to Text("Вещей, проглоченных слаймом: {n}", "Проглочено целиком", "Желейный музей"),
    )
}
