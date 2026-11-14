package app.familiar.content

import app.familiar.domain.Mood
import app.familiar.domain.Season
import app.familiar.domain.Species
import app.familiar.domain.Trait
import java.util.Locale

/**
 * What the familiar is up to, told like a line from a storybook. Russian lines stay in the
 * present tense so they fit any name, whatever its grammatical gender.
 */
object Lines {
    enum class State { EGG, STONE, ASH, HUNGRY, FED, READING, SLEEP, WOKEN, PURR }

    fun of(locale: Locale): Map<Species, Map<State, List<String>>> = if (locale.language == "ru") RU else EN

    /**
     * A line for [state], picked by [seed] so it holds still for a while. A line marked {tails} speaks of the
     * kitsune's tails, so it is only said once she has two or more ([tails], one for every genre read), and one
     * marked {genre} of a tail in a book's colour, once she has one.
     */
    fun line(locale: Locale, sp: Species, state: State, name: String, seed: Int, tails: Int = Int.MAX_VALUE): String {
        val st = if (state == State.ASH && sp != Species.PHOENIX) State.STONE else state
        val list = of(locale)[sp]?.get(st).orEmpty().ifEmpty { common(locale)[st].orEmpty() }
            .filter { (!it.startsWith("{tails}") || tails >= 2) && (!it.startsWith("{genre}") || tails >= 1) }
        if (list.isEmpty()) return ""
        return list[Math.floorMod(seed, list.size)].removePrefix("{tails}").removePrefix("{genre}").replace("{n}", name)
    }

    fun mood(locale: Locale, mood: Mood, name: String): String? {
        val ru = locale.language == "ru"
        return when (mood) {
            Mood.SCARED -> if (ru) "{n} прячется за книгу. Было страшно, но интересно." else "{n} is hiding behind the book. Scary, but good."
            Mood.HEARTS -> if (ru) "{n} вздыхает и смотрит в окно. Какая история!" else "{n} sighs and gazes out of the window. What a story!"
            Mood.SUSPICIOUS -> if (ru) "{n} щурится: кажется, убийца — садовник." else "{n} squints: the gardener did it, surely."
            Mood.SPARKLE -> if (ru) "{n} размахивает воображаемым мечом." else "{n} swings an imaginary sword."
            Mood.STARS -> if (ru) "{n} смотрит на звёзды и что-то считает." else "{n} looks at the stars, counting something."
            Mood.GLASSES -> if (ru) "{n} поправляет очки и выглядит очень учёно." else "{n} adjusts the spectacles, looking very learned."
            Mood.NONE -> null
        }?.replace("{n}", name)
    }

    /** A line in the familiar's character, now and then instead of the usual one. */
    fun trait(locale: Locale, t: Trait, name: String, seed: Int): String {
        val ru = locale.language == "ru"
        val list = when (t) {
            Trait.SLEEPY -> if (ru) listOf("{n} зевает так, что видно все зубки.", "{n} дремлет прямо на раскрытой книге.") else listOf("{n} yawns so wide you can see every tooth.", "{n} dozes right on the open book.")
            Trait.GLUTTON -> if (ru) listOf("{n} облизывается: а будет ещё глава?", "{n} проверяет, не осталось ли страниц на добавку.") else listOf("{n} licks its lips: is there another chapter?", "{n} checks for any pages left for seconds.")
            Trait.CURIOUS -> if (ru) listOf("{n} суёт нос между страниц: что там спрятано?", "{n} разглядывает корешки на полке и что-то замышляет.") else listOf("{n} pokes its nose between the pages: what's hidden there?", "{n} studies the spines on the shelf, plotting something.")
            Trait.GRUMPY -> if (ru) listOf("{n} ворчит, но тихонько придвигается поближе.", "{n} делает вид, что ему всё равно. Ему не всё равно.") else listOf("{n} grumbles, but quietly shuffles closer.", "{n} pretends not to care. It cares.")
            Trait.PLAYFUL -> if (ru) listOf("{n} гоняется за закладкой по всему логову.", "{n} прыгает вокруг: ну давай почитаем!") else listOf("{n} chases a bookmark all around the den.", "{n} bounces around: come on, let's read!")
            Trait.BRAVE -> if (ru) listOf("{n} не боится ни одной истории, даже самой страшной.", "{n} охраняет логово с картонным мечом.") else listOf("{n} is ready for any story, even the scariest.", "{n} guards the den with a cardboard sword.")
        }
        return list[Math.floorMod(seed, list.size)].replace("{n}", name)
    }

    /** A line about the familiar and the one living beside it, [mate]. */
    fun duo(locale: Locale, name: String, mate: String, seed: Int): String {
        val list = if (locale.language == "ru") listOf(
            "{n} и {m} делят одну подушку и спорят, чья очередь слушать.",
            "{n} пересказывает главу, {m} слушает и кивает.",
            "{m} приносит закладку, {n} делает вид, что так и было задумано.",
            "{n} и {m} греются у свечи, бок о бок.",
        ) else listOf(
            "{n} and {m} share one cushion and argue about whose turn it is to listen.",
            "{n} retells the chapter; {m} listens and nods.",
            "{m} fetches the bookmark; {n} pretends that was the plan.",
            "{n} and {m} warm up by the candle, side by side.",
        )
        return list[Math.floorMod(seed, list.size)].replace("{n}", name).replace("{m}", mate)
    }

    /** A line for the days of a season. */
    fun season(locale: Locale, s: Season, name: String): String? {
        val ru = locale.language == "ru"
        return when (s) {
            Season.SAMHAIN -> if (ru) "{n} рычит на тыкву. Для порядка." else "{n} growls at the pumpkin. Just in case."
            Season.WINTER -> if (ru) "{n} смотрит, как за окном идёт снег." else "{n} watches the snow fall outside."
            Season.VALENTINE -> if (ru) "{n} прячет за спиной валентинку. Для тебя." else "{n} hides a valentine behind its back. It's for you."
            Season.BOOKDAY -> if (ru) "{n} празднует: сегодня праздник всех книг!" else "{n} is celebrating: today is the feast of all books!"
            Season.MIDSUMMER -> if (ru) "{n} ловит светлячков в самую короткую ночь." else "{n} catches fireflies on the shortest night."
            Season.NONE -> null
        }?.replace("{n}", name)
    }

    private fun common(locale: Locale): Map<State, List<String>> = if (locale.language == "ru") mapOf(
        State.EGG to listOf("Яйцо тёплое. Кажется, оно слушает, как ты читаешь.", "Внутри что-то шевелится. Ещё пара страниц…", "Тук-тук. Яйцо ждёт историю."),
        State.STONE to listOf("{n} каменеет: это чары забвения. Пара страниц снимет их.", "{n} почти статуя. Открой книгу, и камень треснет."),
        State.FED to listOf("{n} сыто мурлычет и греется у свечи.", "{n} дремлет у камина. Сегодня был хороший ужин."),
        State.READING to listOf("{n} заглядывает через плечо: что там дальше?", "{n} тихо ждёт следующую страницу."),
        State.SLEEP to listOf("{n} спит и сопит. Тсс…", "{n} видит сны о недочитанных главах."),
        State.WOKEN to listOf("{n} сонно приоткрывает один глаз…", "{n} зевает: «Уже утро?»"),
        State.PURR to listOf("{n} жмурится и мурлычет.", "{n} подставляет макушку: ещё, ещё!"),
    ) else mapOf(
        State.EGG to listOf("The egg is warm. It seems to be listening to you read.", "Something stirs inside. A few more pages…", "Tap, tap. The egg is waiting for a story."),
        State.STONE to listOf("{n} is turning to stone: the spell of forgetting. A page or two will break it.", "{n} is almost a statue. Open a book and the stone will crack."),
        State.FED to listOf("{n} purrs, full, warming up by the candle.", "{n} dozes by the fire. Dinner was good today."),
        State.READING to listOf("{n} peeks over your shoulder: what happens next?", "{n} waits quietly for the next page."),
        State.SLEEP to listOf("{n} is asleep and snoring softly. Shh…", "{n} dreams of unfinished chapters."),
        State.WOKEN to listOf("{n} opens one sleepy eye…", "{n} yawns: “Is it morning already?”"),
        State.PURR to listOf("{n} squeezes its eyes shut and purrs.", "{n} pushes its head into your hand: more, more!"),
    )

    private val EN: Map<Species, Map<State, List<String>>> = mapOf(
        Species.MIMIC to mapOf(
            State.HUNGRY to listOf("{n} gnaws on the bookmark out of boredom.", "{n} opens and shuts its lid hopefully. Book?", "{n} has eaten a receipt. Please, a real book."),
            State.FED to listOf("{n} burps up a comma, very pleased.", "{n} snores with its lid half open."),
        ),
        Species.KITSUNE to mapOf(
            State.HUNGRY to listOf("{n} sniffs the cover of your book.", "{tails}{n} counts her tails and wonders which one comes next.", "{n} flicks a tail at the bookshelf."),
            State.FED to listOf("{tails}{n} curls up in her tails like a blanket.", "{genre}{n} grooms a tail the colour of your last book.", "{n} curls up with her tail round her paws."),
        ),
        Species.SLIME to mapOf(
            State.HUNGRY to listOf("{n} wobbles sadly. Nothing new inside.", "{n} tries to swallow the TV remote. Not that."),
            State.FED to listOf("{n} jiggles happily, showing off what it swallowed.", "{n} glows softly, full of stories."),
        ),
        Species.DRAGON to mapOf(
            State.HUNGRY to listOf("{n} counts the hoard. One more book would be nice.", "{n} breathes a sad little puff of smoke."),
            State.FED to listOf("{n} sleeps on the hoard, one eye open.", "{n} polishes a book spine with its tail."),
        ),
        Species.PHOENIX to mapOf(
            State.HUNGRY to listOf("{n} flickers low, waiting for a story.", "{n} preens, a little dimmer than yesterday."),
            State.ASH to listOf("{n} has burned down to ash. One page, and it will rise again.", "Only warm ash is left of {n}. Read, and it rises brighter."),
            State.FED to listOf("{n} burns bright and warm.", "{n} sings softly; sparks fly up."),
        ),
        Species.GRIFFIN to mapOf(
            State.HUNGRY to listOf("{n} paces by the window, wings restless.", "{n} looks at you sternly. Reading today?"),
            State.FED to listOf("{n} stretches its wings, proud of the streak.", "{n} dozes with its beak tucked under a wing."),
        ),
        Species.OWLBEAR to mapOf(
            State.HUNGRY to listOf("{n} has read its book three times already.", "{n} hoots softly: a new chapter, please?"),
            State.FED to listOf("{n} underlines something important with a claw.", "{n} hugs the book and dozes."),
        ),
        Species.MOTH to mapOf(
            State.HUNGRY to listOf("{n} circles the lamp, waiting for the night pages.", "{n} folds its wings and waits for dark."),
            State.FED to listOf("{n} rests on the lampshade, wings full of stars.", "{n} dusts the page with a little moonlight."),
        ),
        Species.TANUKI to mapOf(
            State.HUNGRY to listOf("{n} drums on its belly: tell me a story!", "{n} turns into a teapot out of boredom. Then back."),
            State.FED to listOf("{n} hums the narrator's voice and pats its full belly.", "{n} naps with the headphones still on."),
        ),
        Species.SPROUT to mapOf(
            State.HUNGRY to listOf("{n} droops its leaves a little. Some morning pages?", "{n} turns to the window, waiting for the sun and a story."),
            State.FED to listOf("{n} rustles happily and opens a petal.", "{n} soaks up the story like morning sun."),
        ),
        Species.GHOST to mapOf(
            State.HUNGRY to listOf("{n} rattles a little chain: boo? a book?", "{n} floats through the shelf looking for a scary one."),
            State.FED to listOf("{n} glows softly and hums an eerie lullaby.", "{n} hides under its sheet, pleasantly spooked."),
        ),
        Species.WYRM to mapOf(
            State.HUNGRY to listOf("{n} nibbles the corner of a page. Just a little.", "{n} pushes its spectacles up its nose and waits."),
            State.FED to listOf("{n} curls up between two chapters, very pleased.", "{n} has read every footnote and wants more."),
        ),
        Species.CHIMERA to mapOf(
            State.HUNGRY to listOf("{n} roars, bleats and hisses at once: a book, please.", "{n} counts its patches and finds room for one more."),
            State.FED to listOf("{n} purrs with the lion, dozes with the goat; the snake keeps watch.", "{n} admires a new stitch. It suits it."),
        ),
        Species.MAGPIE to mapOf(
            State.HUNGRY to listOf("{n} rummages in the nest: nothing new to look at.", "{n} eyes your bookmark. It's very shiny."),
            State.FED to listOf("{n} sorts its treasures by shade, very carefully.", "{n} chatters about the book like it's gossip."),
        ),
        Species.HYDRA to mapOf(
            State.HUNGRY to listOf("{n}'s heads argue about which volume comes next.", "One of {n}'s heads is asleep. The others want a chapter."),
            State.FED to listOf("{n}'s heads retell the chapter to each other, all at once.", "{n} nods off one head at a time."),
        ),
        Species.BAYUN to mapOf(
            State.HUNGRY to listOf("{n} walks round the bookshelf, humming an old tale.", "{n} paws at a book it knows by heart. Again?"),
            State.FED to listOf("{n} purrs the story back to itself, word for word.", "{n} curls up on a book it has heard before."),
        ),
        Species.SNAIL to mapOf(
            State.HUNGRY to listOf("{n} peeks out of the shell: is it quiet enough to read?", "{n} waits. It's good at waiting."),
            State.FED to listOf("{n} draws back into its little house, content.", "{n} leaves a slow, shining trail across the page."),
        ),
        Species.SALAMANDER to mapOf(
            State.HUNGRY to listOf("{n} glows a little dimmer. A page by the fire?", "{n} pokes at the cold embers with its tail."),
            State.FED to listOf("{n} basks in the hearth, crackling softly.", "{n} warms the page for you."),
        ),
        Species.AUTOMATON to mapOf(
            State.HUNGRY to listOf("{n} ticks slowly. It needs winding with a chapter.", "{n} blinks its lamp: story not found."),
            State.FED to listOf("{n} whirrs happily, gears full of stars.", "{n} files the chapter away under “wonderful”."),
        ),
        Species.PIXIE to mapOf(
            State.HUNGRY to listOf("{n} sits on the bookmark, swinging its legs.", "{n} looks for a book small enough to carry."),
            State.FED to listOf("{n} hums a verse and scatters a little dust.", "{n} sleeps between two pages, like a pressed flower."),
        ),
        Species.RAVEN to mapOf(
            State.HUNGRY to listOf("{n} taps the inkwell with its beak. A line to keep?", "{n} recites an old quote, hoping for a new one."),
            State.FED to listOf("{n} repeats the best line under its breath.", "{n} tucks a new line under its wing."),
        ),
        Species.SPHINX to mapOf(
            State.HUNGRY to listOf("{n} asks: what has a spine but no bones? A book, please.", "{n} gazes into the distance, waiting for a classic."),
            State.FED to listOf("{n} smiles like it knows how the book ends.", "{n} folds its paws, pleased with your answer."),
        ),
        Species.OWLET to mapOf(
            State.HUNGRY to listOf("{n} checks the letterbox. No stories today?", "{n} sorts stamps and waits for a page."),
            State.FED to listOf("{n} hoots softly and tucks a letter under its wing.", "{n} dozes on the letterbox, one eye open for guests."),
        ),
    )

    private val RU: Map<Species, Map<State, List<String>>> = mapOf(
        Species.MIMIC to mapOf(
            State.HUNGRY to listOf("{n} грызёт закладку от скуки.", "{n} с надеждой хлопает крышкой. Книга?", "{n} съедает чек из магазина. Нужна настоящая книга."),
            State.FED to listOf("{n} довольно отрыгивает запятую.", "{n} храпит, приоткрыв крышку."),
        ),
        Species.KITSUNE to mapOf(
            State.HUNGRY to listOf("{n} принюхивается к обложке твоей книги.", "{tails}{n} пересчитывает хвосты и гадает, какой будет следующим.", "{n} машет хвостом в сторону книжной полки."),
            State.FED to listOf("{tails}{n} сворачивается в хвостах, как в одеяле.", "{genre}{n} вылизывает хвост цвета последней книги.", "{n} сворачивается клубочком, укрыв лапки хвостом."),
        ),
        Species.SLIME to mapOf(
            State.HUNGRY to listOf("{n} грустно колышется. Внутри ничего нового.", "{n} пытается проглотить пульт от телевизора. Не то."),
            State.FED to listOf("{n} радостно трясётся и хвастается проглоченным.", "{n} мягко светится изнутри: полон историй."),
        ),
        Species.DRAGON to mapOf(
            State.HUNGRY to listOf("{n} пересчитывает сокровища. Ещё бы одну книгу.", "{n} грустно выпускает колечко дыма."),
            State.FED to listOf("{n} спит на книгах, приоткрыв один глаз.", "{n} полирует хвостом корешок книги."),
        ),
        Species.PHOENIX to mapOf(
            State.HUNGRY to listOf("{n} горит вполсилы и ждёт историю.", "{n} чистит перья, чуть тусклее, чем вчера."),
            State.ASH to listOf("От огня остаётся только пепел. Одна страница, и {n} восстанет.", "{n} — горстка тёплого пепла. Почитай, и огонь вернётся ярче."),
            State.FED to listOf("{n} горит ярко и тепло.", "{n} тихо поёт, и вверх летят искры."),
        ),
        Species.GRIFFIN to mapOf(
            State.HUNGRY to listOf("{n} ходит у окна и беспокойно шевелит крыльями.", "{n} строго смотрит на тебя. Сегодня читаем?"),
            State.FED to listOf("{n} расправляет крылья: гордится серией.", "{n} дремлет, спрятав клюв под крыло."),
        ),
        Species.OWLBEAR to mapOf(
            State.HUNGRY to listOf("{n} перечитывает свою книгу в третий раз.", "{n} тихо ухает: можно новую главу?"),
            State.FED to listOf("{n} подчёркивает когтем что-то важное.", "{n} обнимает книгу и дремлет."),
        ),
        Species.MOTH to mapOf(
            State.HUNGRY to listOf("{n} кружит у лампы и ждёт ночных страниц.", "{n} складывает крылья и ждёт темноты."),
            State.FED to listOf("{n} сидит на абажуре, крылья полны звёзд.", "{n} посыпает страницу лунной пыльцой."),
        ),
        Species.TANUKI to mapOf(
            State.HUNGRY to listOf("{n} барабанит по пузику: расскажи историю!", "{n} от скуки превращается в чайник. И обратно."),
            State.FED to listOf("{n} напевает голосом чтеца и гладит сытое пузико.", "{n} дремлет, не снимая наушников."),
        ),
        Species.SPROUT to mapOf(
            State.HUNGRY to listOf("{n} чуть опускает листики. Почитаем утром?", "{n} тянется к окну: ждёт солнца и историю."),
            State.FED to listOf("{n} радостно шелестит и раскрывает лепесток.", "{n} впитывает историю, как утреннее солнце."),
        ),
        Species.GHOST to mapOf(
            State.HUNGRY to listOf("{n} звенит цепочкой: бу? книгу?", "{n} проплывает сквозь полку в поисках страшилки."),
            State.FED to listOf("{n} мягко светится и напевает жуткую колыбельную.", "{n} прячется под простынкой: приятно страшно."),
        ),
        Species.WYRM to mapOf(
            State.HUNGRY to listOf("{n} покусывает уголок страницы. Совсем чуть-чуть.", "{n} поправляет очки и ждёт."),
            State.FED to listOf("{n} сворачивается между двумя главами, очень довольный.", "{n} прочитывает все сноски и хочет ещё."),
        ),
        Species.CHIMERA to mapOf(
            State.HUNGRY to listOf("{n} рычит, блеет и шипит разом: книгу, пожалуйста.", "{n} пересчитывает заплатки: найдётся место ещё для одной."),
            State.FED to listOf("{n} мурлычет львом, дремлет козой, а змейка сторожит.", "{n} любуется новым стежком. Ему идёт."),
        ),
        Species.MAGPIE to mapOf(
            State.HUNGRY to listOf("{n} роется в гнезде: ничего нового.", "{n} косится на твою закладку. Она такая блестящая."),
            State.FED to listOf("{n} раскладывает сокровища по оттенкам, очень тщательно.", "{n} стрекочет о книге, как о свежей сплетне."),
        ),
        Species.HYDRA to mapOf(
            State.HUNGRY to listOf("Головы спорят, какой том следующий. {n} ждёт.", "Одна голова спит, остальные просят главу. {n} скучает."),
            State.FED to listOf("Головы пересказывают друг другу главу, все разом. {n} сияет.", "{n} засыпает по одной голове."),
        ),
        Species.BAYUN to mapOf(
            State.HUNGRY to listOf("{n} ходит вокруг полки и мурлычет старую сказку.", "{n} трогает лапой знакомую книгу. Ещё раз?"),
            State.FED to listOf("{n} мурлычет историю слово в слово.", "{n} сворачивается на книге, которую знает наизусть."),
        ),
        Species.SNAIL to mapOf(
            State.HUNGRY to listOf("{n} выглядывает из раковины: уже тихо, можно читать?", "{n} ждёт. Ждать {n} умеет."),
            State.FED to listOf("{n} прячется в домик, всё хорошо.", "{n} оставляет на странице медленный блестящий след."),
        ),
        Species.SALAMANDER to mapOf(
            State.HUNGRY to listOf("{n} светится чуть тусклее. Страничку у камина?", "{n} ворошит хвостом остывшие угольки."),
            State.FED to listOf("{n} греется в очаге и тихо потрескивает.", "{n} согревает для тебя страницу."),
        ),
        Species.AUTOMATON to mapOf(
            State.HUNGRY to listOf("{n} тикает медленно: пора завести главой.", "{n} мигает лампочкой: история не найдена."),
            State.FED to listOf("{n} довольно жужжит, шестерёнки полны звёзд.", "{n} раскладывает главу по полочкам: «чудесно»."),
        ),
        Species.PIXIE to mapOf(
            State.HUNGRY to listOf("{n} сидит на закладке и болтает ножками.", "{n} ищет книжку, которую можно унести."),
            State.FED to listOf("{n} напевает строфу и сыплет пыльцой.", "{n} спит между страниц, как засушенный цветок."),
        ),
        Species.RAVEN to mapOf(
            State.HUNGRY to listOf("{n} стучит клювом по чернильнице. Выпишем строчку?", "{n} повторяет старую цитату и надеется на новую."),
            State.FED to listOf("{n} вполголоса повторяет лучшую строчку.", "{n} прячет под крыло новую цитату."),
        ),
        Species.SPHINX to mapOf(
            State.HUNGRY to listOf("{n} спрашивает: у кого есть корешок, но нет корней? Книгу, пожалуйста.", "{n} смотрит вдаль и ждёт классику."),
            State.FED to listOf("{n} улыбается так, будто знает, чем кончится книга.", "{n} складывает лапы: ответ верный."),
        ),
        Species.OWLET to mapOf(
            State.HUNGRY to listOf("{n} заглядывает в почтовый ящик. Историй сегодня нет?", "{n} перебирает марки и ждёт страничку."),
            State.FED to listOf("{n} тихо ухает и прячет письмо под крыло.", "{n} дремлет на почтовом ящике, одним глазом высматривая гостей."),
        ),
    )
}
