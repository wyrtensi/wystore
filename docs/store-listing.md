# Описание для публикации в сторонних магазинах

Готовые тексты и данные для карточки Wy Store 0.5.0. Копировать как есть, при необходимости
подрезая под лимиты конкретной площадки.

## Основное

| Поле | Значение |
|---|---|
| Название | Wy Store |
| Имя пакета | `app.wystore` |
| Версия | 0.5.0 (versionCode 44) |
| Минимальная версия Android | 9.0 (API 28) |
| Целевой API | 36 |
| Категория | Инструменты / Утилиты |
| Разработчик | wyrtensi |
| Сайт | https://github.com/wyrtensi/wystore |
| Политика конфиденциальности | https://github.com/wyrtensi/wystore/blob/main/PRIVACY.md |
| Лицензия | MIT |
| Цена | Бесплатно, без покупок внутри и без рекламы |
| Возрастной рейтинг | 12+ — приложение устанавливает стороннее ПО, содержимое которого не модерируется |

## Краткое описание (до 80 символов)

```
Каталог RuStore и релизы с GitHub. Без аккаунта, рекламы и слежки.
```

Запасные варианты, если лимит меньше:

```
Магазин приложений без аккаунта, рекламы и слежки
```

```
Российские приложения и релизы с GitHub в одном списке
```

## Полное описание

```
Wy Store — магазин приложений для Android, который читает каталог RuStore и релизы открытых проектов с GitHub. Он скачивает APK и ставит их через системный установщик Android. Аккаунт не нужен: ни регистрации, ни входа, ни рекламы, ни аналитики.

ЧТО ВНУТРИ

• Каталог RuStore целиком: банки, госсервисы, маркетплейсы, доставка, транспорт, развлечения — с разделами, поиском, описаниями и скриншотами.
• Релизы с GitHub вторым источником: открытые приложения, которых в магазинах не бывает. Свой репозиторий добавляется ссылкой.
• Отзывы и оценки из RuStore с разбором по звёздам и фильтром: плохие отзывы можно прочитать, не листая все хорошие.
• Вкладка «Приложения»: всё установленное на телефоне, с фильтрами по источнику и значком установщика — обновление, запуск и удаление из одного списка.
• Оформление Wy Hub на телефоне и телевизоре: графитовая тёмная тема, светлая и системная, девять цветов акцента на выбор, у каждого состояния свой цвет.
• Резервная копия: настройки и списки приложений экспортируются в один файл и переносятся на новый телефон.
• Android TV и Google TV: тот же APK, свой интерфейс под пульт в том же оформлении и каталог RuStore для телевизоров.

ОБНОВЛЕНИЯ, КОТОРЫЕ ПРИХОДЯТ САМИ

Wy Store проверяет обновления в фоне по расписанию и скачивает найденное сам. Установка без окна подтверждения возможна на Android 12 и новее для приложений, которые Wy Store установил сам, — но не гарантирована: на телефонах с сервисами Google окно может показать Play Protect, если он видит этот файл впервые. На Android 9, 10 и 11 такой возможности нет ни у одного магазина. Без root первая установка подтверждается всегда. С root первая установка и обновления проходят без окон Android, а удаление по желанию — без подтверждения.

Приложения, установленные другим магазином, можно передать под управление Wy Store одной кнопкой на карточке. Приложения из Google Play не трогаются, пока это не разрешено для конкретного приложения.

ПРОВЕРКА ПЕРЕД УСТАНОВКОЙ

Перед тем как файл уйдёт в установщик, сверяются имя пакета, номер версии, комплектность набора APK и сертификат подписи. Для уже установленного приложения подпись должна совпадать с установленной копией, а версия — расти. Не сошлось — установка отменяется с указанием причины.

БЕЗ СЛЕЖКИ

У проекта нет своего сервера. Запросы идут напрямую к RuStore и GitHub. Нет аккаунтов, аналитики, рекламных SDK и сбора статистики. Всё, что приложение хранит, остаётся на устройстве.

ЧЕГО НЕТ

• Платных приложений и покупок внутри — только бесплатное.
• Каталога Google Play — Wy Store его не читает и не заменяет.

Wy Store — независимый проект с открытым исходным кодом (лицензия MIT). Он не связан с RuStore и не действует от его имени: RuStore является источником данных. Приложение проверяет доставку файлов, но не отвечает за содержимое и работу установленных программ.

Исходный код и релизы: github.com/wyrtensi/wystore
```

## Что нового (0.5.0)

```
• Новое оформление Wy Hub на телефоне и телевизоре: графитовая тёмная тема, шрифт Manrope, стеклянные панели, у каждого состояния свой цвет — готово, пауза, ждёт вас, ошибка.
• Цвет акцента на выбор из девяти в «Внешнем виде».
• Вкладка и экран называются «Приложения» на всех языках, а не «Мои» и «Библиотека».
• Панель «Апдейтов» показывает ход проверки: «Проверено 2 из 7» и кольцо, которое заполняется.
• На телевизоре у плиток разделов появились значки, ряды карточек уходят за края экрана, названия на карточках занимают до трёх строк.
• Кнопка «Что нового» в очереди, у скачанных обновлений и у принятых приложений: описание изменений видно, не уходя со списка. Карточки в очереди открывают страницу приложения.
• Загрузка не застывает, если пропала сеть: она встаёт в очередь и докачивается с того же места. Без сети поиск и разделы говорят «Нет связи с сервером» и предлагают повторить.
• Приложение, которому нужен более новый Android, сразу говорит, какой именно, вместо сбоя при загрузке.
• Страница приложения с приостановленной загрузкой предлагает «Возобновить», а отказ Android из-за копии с другой подписью объяснён словами.
```

## Теги

```
магазин приложений, каталог приложений, RuStore, GitHub, APK, обновления приложений, без рекламы, без регистрации, открытый исходный код, российские приложения
```

## Скриншоты

Лежат в `docs/screenshots/store/`. PNG без альфа-канала (24 бита), 1320 × 2616 — отношение
1,98 : 1, то есть укладывается в требование «большая сторона не больше двух меньших». Ширина
добита фоном экрана, содержимое не обрезано. Тот же набор на английском — в `docs/screenshots/store/en/`, с теми же именами файлов.

| Файл | Что на экране |
|---|---|
| `01-home.png` | Главная: поиск, статус обновлений, разделы, подборка с GitHub |
| `02-categories.png` | Все разделы каталога |
| `03-search.png` | Поиск по каталогу RuStore |
| `04-app-page.png` | Карточка приложения: версия, размер, требуемый Android, отпечаток подписи |
| `05-github.png` | Подборка проектов с GitHub |
| `06-updates.png` | Апдейты: ход проверки, готовые к установке и принятые приложения |
| `07-library.png` | Приложения: установленное на телефоне, принятые и не принятые |
| `08-settings.png` | Настройки |

Экрана с отзывами в наборе нет намеренно: он целиком состоит из оценок и попадает под запрет
показывать в скриншотах информацию о рейтинге. Карточка приложения выбрана такая, у которой в
галерее RuStore обычные снимки интерфейса, а не рекламные баннеры с кешбэком.

## Баннер

`docs/banner-1024x500.png` — 1024 × 500, PNG без альфа-канала. Английский вариант:
`docs/banner-en-1024x500.png`. Исходные баннеры 1280 × 440 остались для README.

## Файлы релиза

APK: `wystore-0.5.0.apk`, подписан тем же ключом, что и релизы на GitHub —
отпечаток сертификата SHA-256 `33dcb50061a08190f68bbee2d743be38ad4f7307d98dc18dd7e76719c815a258`.

---

# Store listing (English)

## Short description

```
The RuStore catalogue and GitHub releases. No account, no ads, no tracking.
```

## Full description

```
Wy Store is an Android app store that reads the RuStore catalogue and the releases of open-source projects on GitHub. It downloads APKs and installs them through Android's own package installer. No account is needed: no registration, no sign-in, no ads, no analytics.

WHAT IS INSIDE

• The whole RuStore catalogue: banks, government services, marketplaces, delivery, transport, entertainment — with sections, search, descriptions and screenshots.
• GitHub releases as a second source: open-source apps that stores do not carry. Any public repository can be added by URL.
• RuStore reviews and ratings with a breakdown by star count and a filter, so the bad reviews can be read without scrolling past the good ones.
• An Apps tab with everything installed on the phone, with filters by source and a badge saying who installed each app — update, open and uninstall from one list.
• The Wy Hub look on phone and TV: a graphite dark theme, a light and a system one, nine accent colours to choose from, and a colour for every state.
• Backup: settings and app lists export to a single file and move to a new phone.
• Android TV and Google TV: the same APK, with its own remote-friendly interface in the same look and the RuStore TV catalogue.

UPDATES THAT ARRIVE ON THEIR OWN

Wy Store checks for updates in the background on a schedule and downloads what it finds on its own. Installing without a confirmation dialog is possible on Android 12 and newer for apps Wy Store installed itself — but it is not guaranteed: on phones with Google services, Play Protect may raise a dialog of its own for a file it has not seen before. On Android 9, 10 and 11 no store has that option. Without root, a first install is always confirmed. With root, first installs and updates go through with no Android dialogs, and uninstalling can skip confirmation if you choose.

Apps installed by another store can be handed over to Wy Store with one button on the app page. Apps from Google Play are left alone until that is enabled for a specific app.

CHECKS BEFORE INSTALLING

Before a file reaches the installer, the package name, version code, completeness of the APK set and the signing certificate are verified. For an already installed app the signature must match the installed copy and the version must increase. A mismatch cancels the install with a stated reason.

NO TRACKING

The project has no backend. Requests go straight to RuStore and GitHub. No accounts, no analytics, no ad SDKs, no statistics. Everything the app stores stays on the device.

WHAT IT DOES NOT DO

• No paid apps or in-app purchases — free apps only.
• No Google Play catalogue — Wy Store neither reads nor replaces it.

Wy Store is an independent open-source project (MIT licence). It is not affiliated with RuStore and does not act on its behalf: RuStore is a data source. The app verifies file delivery but is not responsible for the content or behaviour of the programs it installs.

Source code and releases: github.com/wyrtensi/wystore
```

## What's new (0.5.0)

```
• The new Wy Hub look on phone and TV: a graphite dark theme, the Manrope typeface, glass panels, and a colour for every state — done, paused, waiting on you, error.
• Accent colour: a choice of nine in Appearance.
• The tab and the screen are called "Apps" in every language, instead of "My apps" and "Library".
• The Updates panel shows how the check is going: "Checked 2 of 7" and a ring that fills up.
• On the TV, section tiles have glyphs, rows of cards run off the edges of the screen, and card names take up to three lines.
• A "What is new" button in the queue, on downloaded updates and on accepted apps: the changes are readable without leaving the list. Cards in the queue open the app's page.
• A download no longer freezes when the network drops: it joins the queue and resumes from the same place. Without a network, search and sections say "No connection to the server" and offer a retry.
• An app that needs a newer Android says which one straight away, instead of failing at the download.
• The page of an app with a paused download offers "Resume", and Android's refusal over a copy with a different signature is explained in words.
```
