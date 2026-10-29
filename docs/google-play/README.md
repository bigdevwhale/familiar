# Google Play — всё для ручной публикации

Тексты лежат в `listing/<язык>/*.txt` (копируйте как есть), графика — в `graphics/`.
Ниже — что вписать в каждое поле Play Console и в каком порядке.

## 0. Перед стартом

- **Нужен AAB, не APK.** Релизный workflow кладёт в GitHub Release ещё и `Familiar-v<версия>.aab`.
  Первая версия для Play — **v1.4.0**: Actions → Release → Run workflow → `1.4.0`
  (или `git tag v1.4.0 && git push origin v1.4.0`).
  Убедитесь, что в репозитории заданы секреты `SIGNING_*`: без них сборка подписывается debug-ключом,
  а такой файл Play отклонит. `versionCode` — номер запуска workflow, он сам растёт от релиза к релизу.
- **Языки.** AAB собирается без разбиения по языкам (`bundle.language.enableSplit = false`), иначе Play
  ставит только язык телефона и переключение языка в настройках приложения перестаёт работать.
- **Подпись (Play App Signing).** При первой загрузке оставьте вариант «Google создаст ключ подписи».
  Ваш keystore станет ключом загрузки (upload key) — храните его.
  ⚠️ Из-за этого APK из GitHub и версия из Play будут подписаны разными ключами: поверх друг друга они
  не обновятся. Если нужно, чтобы совпадали, выберите «Использовать свой ключ» и загрузите ключ
  из того же keystore через PEPK (инструкция в самом диалоге).
- **Новый личный аккаунт разработчика** (создан после ноября 2023): перед выходом в production нужен
  закрытый тест — минимум 12 тестировщиков, которые не выходят из теста 14 дней подряд.
- **Политика конфиденциальности** — `PRIVACY.md` в корне репозитория. После push ссылка:
  `https://github.com/bigdevwhale/familiar/blob/main/PRIVACY.md`
  (или перенесите текст на сайт Stillcraft).

## 1. Создание приложения

| Поле | Значение |
|---|---|
| App name | `Familiar: Reading Tracker` |
| Default language | English (United States) – en-US |
| App or game | App |
| Free or paid | Free |
| Declarations | обе галочки (Developer Program Policies, US export laws) |

## 2. Store listing → Main store listing

| Поле | en-US | ru-RU (Add translations → Russian) |
|---|---|---|
| App name (≤30) | `listing/en-US/title.txt` | `listing/ru-RU/title.txt` |
| Short description (≤80) | `listing/en-US/short_description.txt` | `listing/ru-RU/short_description.txt` |
| Full description (≤4000) | `listing/en-US/full_description.txt` | `listing/ru-RU/full_description.txt` |
| App icon 512×512 | `graphics/icon-512.png` | та же |
| Feature graphic 1024×500 | `graphics/feature-graphic-en.png` | `graphics/feature-graphic-ru.png` |
| Phone screenshots (2–8) | `graphics/screenshots-en/1…8.png` | `graphics/screenshots-ru/1…8.png` |
| Tablet screenshots | можно не загружать | — |
| Video | пусто | — |

Скриншоты 1080×1920 сняты с телефона и собраны `tools/play.py`. В них нарисованные обложки, а не
обложки издательств: чужие изображения в карточке — частая причина отказа. Как переснять:

```bash
./gradlew installDebug -Pdemo -Pdrawn -Pdemolang=ru   # затем очистить данные app.familiar.demo и снять 8 экранов
./gradlew installDebug -Pdemo -Pdrawn -Pdemolang=en   # то же, язык приложения переключить на English в настройках
python tools/play.py <папка со снимками>              # den, ritual, feast, ledger, bestiary, tree, furnish, shelf (+ .ru)
```

## 3. Store settings

| Поле | Значение |
|---|---|
| App category | Application → **Books & Reference** |
| Tags (до 5) | Reading, Books, Book tracker, Virtual pet, Productivity (берите ближайшие из списка Play) |
| Email address | *ваш публичный email* — обязательно, его увидят все |
| Website | `https://bigdevwhale.github.io/stillcraft/apps/familiar/` |
| Phone | пусто |
| External marketing | можно оставить включённым |

## 4. App content (Policy → App content)

**Privacy policy** — URL выше.

**App access** — *All functionality is available without any access restrictions.*

**Ads** — *No, my app does not contain ads.*

**Content rating** (анкета IARC)
- Email — ваш; категория — **All Other App Types**.
- Всё — **No**: насилие, кровь, страх, сексуальный контент, ненормативная лексика, наркотики,
  алкоголь и табак, азартные игры, грубый юмор, передача местоположения, цифровые покупки, браузер.
  Жанр «Ужасы» — это только название полки и призрак-простынка с фонарём, пугающих сцен нет.
- **Users interact / share content** — **No**: визиты передаются QR-кодом с телефона на телефон,
  без сервера, чата и профилей; открытка и сага уходят через чужие приложения по выбору пользователя.

**Target audience and content**
- Target age: **13–15, 16–17, 18 and over** (политика: приложение не для детей младше 13).
- *Could your store listing unintentionally appeal to children?* → **No**: это трекер чтения взрослых
  книг, без детских героев. Если Play всё же отнесёт к детской аудитории, понадобится соответствие
  политике Families — тогда пишите, разберём.

**News apps** — No.

**COVID-19 contact tracing and status apps** — My app is not a publicly available COVID-19 app.

**Data safety**
- *Does your app collect or share any of the required user data types?* → **Yes**, ровно один тип:
  - **App activity → In-app search history** — **Collected**, **not shared** (запрос уходит в каталог
    по прямому действию пользователя — это исключение из «sharing»).
  - Processed ephemerally? → **No** (каталоги — внешние сервисы, мы не знаем, что они хранят).
  - Required or optional? → **Optional** (книгу можно вписать вручную).
  - Purpose → **App functionality**.
- *Is all of the user data collected by your app encrypted in transit?* → **Yes** (все запросы по HTTPS).
- *Do you provide a way for users to request that their data is deleted?* → **No** (аккаунтов нет,
  у нас нечего удалять; всё локальное удаляется вместе с приложением).
- Больше ничего не отмечать: библиотека, чтения и фамильяры не покидают телефон; камера только
  читает QR на устройстве; резервная копия — файл, который пользователь сохраняет сам;
  системный бэкап Android (`allowBackup="true"`) в Data safety не декларируется.
- Если хочется карточку «No data collected», поиск придётся считать эфемерным — это спорно,
  честнее оставить один пункт выше.

**Government apps** — No.

**Financial features** — *My app doesn't provide any financial features.*

**Health apps** — No (к здоровью приложение отношения не имеет).

**Advertising ID** — *No* (рекламы и SDK аналитики нет, разрешения `AD_ID` в сборке нет).

**Permissions** — особых деклараций не нужно:
- `CAMERA` — обычное runtime-разрешение, запрашивается только при сканировании визитки друга.
- `INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE` не относятся к чувствительным.
- `android.permission.DUMP` в манифесте сборки — это защита ресивера библиотеки `profileinstaller`,
  а не запрос разрешения; декларировать нечего.
- Точных будильников (exact alarm), foreground-сервисов, доступа к фото и SMS нет: картинки выбираются
  через системный Photo Picker.

## 5. Pricing & distribution

- Countries / regions: все доступные.
- Free, без встроенных покупок.

## 6. Релиз

1. Testing → **Internal testing** (или Closed testing, если аккаунт новый) → Create new release.
2. Загрузить `Familiar-v1.4.0.aab` из GitHub Release.
3. Release name: `1.4.0`. Release notes:

```
<en-US>
(listing/en-US/release_notes.txt)
</en-US>
<ru-RU>
(listing/ru-RU/release_notes.txt)
</ru-RU>
```

4. Review release → Start rollout. Первая проверка Google обычно занимает от нескольких дней до недели.
5. После установки из Play проверьте: Настройки → Язык → Русский/English — экран должен
   переключиться сразу.

## Следующие версии

- `release_notes.txt` переписать под новую версию (для первой там «First release on Google Play»).
- Скриншоты пересобирать, только если поменялись показанные экраны.

## После публикации

Добавьте в README бейдж и ссылку `https://play.google.com/store/apps/details?id=app.familiar`.
