# Описание для публикации в сторонних магазинах

Готовые тексты и данные для карточки Wy Store 0.2.0. Копировать как есть, при необходимости
подрезая под лимиты конкретной площадки.

## Основное

| Поле | Значение |
|---|---|
| Название | Wy Store |
| Имя пакета | `app.wystore` |
| Версия | 0.2.1 (versionCode 30) |
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
• Библиотека установленного с фильтрами по источнику — обновление, запуск и удаление из одного списка.
• Резервная копия: настройки и списки приложений экспортируются в один файл и переносятся на новый телефон.

ОБНОВЛЕНИЯ, КОТОРЫЕ ПРИХОДЯТ САМИ

Wy Store проверяет обновления в фоне по расписанию и скачивает найденное сам. Установка без окна подтверждения возможна на Android 12 и новее для приложений, которые Wy Store установил сам, — но не гарантирована: на телефонах с сервисами Google окно может показать Play Protect, если он видит этот файл впервые. На Android 9, 10 и 11 такой возможности нет ни у одного магазина. Первая установка подтверждается всегда. С root установка проходит без подтверждений во всех случаях.

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

## Что нового (0.2.1)

```
• Кнопка «Передать обновления Wy Store» наконец работает: раньше файл скачивался и молча отбрасывался на последнем шаге.
• Если поставить поверх нельзя — версия в каталоге ниже установленной или другая подпись — приложение предлагает удалить и поставить заново, а не оставляет в тупике.
• На странице проекта с GitHub открывается весь список релизов, а не только последний.
• Приложения из встроенного каталога GitHub опознаются как установленные, и видно, кто отвечает за их обновления.
• Кнопки на экранах GitHub перестали молчать после нажатия.
```

## Теги

```
магазин приложений, каталог приложений, RuStore, GitHub, APK, обновления приложений, без рекламы, без регистрации, открытый исходный код, российские приложения
```

## Скриншоты

Лежат в `docs/screenshots/store/`. PNG без альфа-канала (24 бита), 1320 × 2616 — отношение
1,98 : 1, то есть укладывается в требование «большая сторона не больше двух меньших». Ширина
добита фоном экрана, содержимое не обрезано.

| Файл | Что на экране |
|---|---|
| `01-home.png` | Главная: поиск, статус обновлений, разделы, подборка с GitHub |
| `02-categories.png` | Все разделы каталога |
| `03-search.png` | Поиск по каталогу RuStore |
| `04-app-page.png` | Карточка приложения: версия, размер, требуемый Android, отпечаток подписи |
| `05-github.png` | Подборка проектов с GitHub |
| `06-updates.png` | Обновления: отчёт проверки и принятые приложения |
| `07-library.png` | Библиотека установленного |
| `08-settings.png` | Настройки |

Экрана с отзывами в наборе нет намеренно: он целиком состоит из оценок и попадает под запрет
показывать в скриншотах информацию о рейтинге. Карточка приложения выбрана такая, у которой в
галерее RuStore обычные снимки интерфейса, а не рекламные баннеры с кешбэком.

## Баннер

`docs/banner-1024x500.png` — 1024 × 500, PNG без альфа-канала. Английский вариант:
`docs/banner-en-1024x500.png`. Исходные баннеры 1280 × 440 остались для README.

## Файлы релиза

APK: `wystore-0.2.1.apk` (30,8 МБ), подписан тем же ключом, что и релизы на GitHub —
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
• A library of installed apps with filters by source — update, open and uninstall from one list.
• Backup: settings and app lists export to a single file and move to a new phone.

UPDATES THAT ARRIVE ON THEIR OWN

Wy Store checks for updates in the background on a schedule and downloads what it finds on its own. Installing without a confirmation dialog is possible on Android 12 and newer for apps Wy Store installed itself — but it is not guaranteed: on phones with Google services, Play Protect may raise a dialog of its own for a file it has not seen before. On Android 9, 10 and 11 no store has that option. A first install is always confirmed. With root, installs go through without any confirmation.

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

## What's new (0.2.1)

```
• "Hand updates to Wy Store" finally works: the file used to download and then be dropped without a word at the last step.
• When nothing can install over the app — an older catalogue version or a different signing key — the app offers to uninstall and install again instead of leaving a dead end.
• A GitHub project page opens its whole release list, not only the latest one.
• Apps from the built-in GitHub catalogue are recognised as installed, and the page says who handles their updates.
• Buttons on the GitHub screens stopped going silent when pressed.
```
