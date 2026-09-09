# Описание для публикации в сторонних магазинах

Готовые тексты и данные для карточки Wy Store 0.2.0. Копировать как есть, при необходимости
подрезая под лимиты конкретной площадки.

## Основное

| Поле | Значение |
|---|---|
| Название | Wy Store |
| Имя пакета | `app.wystore` |
| Версия | 0.2.0 (versionCode 29) |
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

Wy Store проверяет обновления в фоне по расписанию, скачивает найденное и устанавливает. Root для этого не нужен: Android разрешает обновлять приложение без диалога тому, кто его установил. Первая установка подтверждается всегда — это правило системы, обойти его нельзя.

Приложения, установленные другим магазином, можно передать под управление Wy Store одной кнопкой на карточке. Приложения из Google Play не трогаются, пока это не разрешено для конкретного приложения.

ПРОВЕРКА ПЕРЕД УСТАНОВКОЙ

Перед тем как файл уйдёт в установщик, сверяются имя пакета, номер версии, комплектность набора APK и сертификат подписи. Для уже установленного приложения подпись должна совпадать с установленной копией, а версия — расти. Не сошлось — установка отменяется с указанием причины.

БЕЗ СЛЕЖКИ

У проекта нет своего сервера. Запросы идут напрямую к RuStore и GitHub. Нет аккаунтов, аналитики, рекламных SDK и сбора статистики. Всё, что приложение хранит, остаётся на устройстве.

ЧЕГО НЕТ

• Платных приложений и покупок внутри — только бесплатное.
• Каталога Google Play — Wy Store его не читает и не заменяет.

Wy Store — независимый проект с открытым исходным кодом (лицензия MIT). Он не связан с RuStore, не одобрен им и не действует от его имени: RuStore является источником данных. Приложение проверяет доставку файлов, но не отвечает за содержимое и работу установленных программ.

Исходный код и релизы: github.com/wyrtensi/wystore
```

## Что нового (0.2.0)

```
• Сменился идентификатор приложения — обновление с версий 0.1.x нужно поставить вручную, а настройки перенести через экспорт и импорт в разделе «Резервная копия и перенос».
• Полноценные отзывы для приложений RuStore: разбор по количеству звёзд и фильтр по оценке.
• Карточки заметнее отделены от фона — интерфейс лучше читается на дешёвых экранах и на солнце.
• Минимальная версия Android — 9.0. На Android 8 проверка подписи обновлений технически не работала.
```

## Теги

```
магазин приложений, каталог приложений, RuStore, GitHub, APK, обновления приложений, без рекламы, без регистрации, открытый исходный код, российские приложения
```

## Скриншоты

Лежат в `docs/screenshots/store/`, PNG 1220 × 2616 (соотношение 20:9, обычный телефон, тёмная
тема). Порядок для карточки:

| Файл | Что на экране |
|---|---|
| `01-home.png` | Главная: поиск, статус обновлений, разделы, подборка с GitHub |
| `02-categories.png` | Все разделы каталога |
| `03-app-page.png` | Карточка приложения: версия, размер, требуемый Android, отпечаток подписи |
| `04-reviews.png` | Отзывы с разбором по звёздам и фильтром |
| `05-updates.png` | Обновления: отчёт проверки и принятые приложения |
| `06-github.png` | Подборка проектов с GitHub |
| `07-settings.png` | Настройки |
| `08-library.png` | Библиотека установленного |

Если площадка требует иное соотношение или ограничивает вес, скриншоты можно уменьшить по ширине
без обрезки — вертикальные пропорции у всех одинаковые.

## Файлы релиза

APK: `wystore-0.2.0.apk` (30,8 МБ), подписан тем же ключом, что и релизы на GitHub —
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

Wy Store checks for updates in the background on a schedule, downloads what it finds and installs it. No root required: Android lets whoever installed an app update it without a dialog. A first install is always confirmed — that is a system rule and cannot be bypassed.

Apps installed by another store can be handed over to Wy Store with one button on the app page. Apps from Google Play are left alone until that is enabled for a specific app.

CHECKS BEFORE INSTALLING

Before a file reaches the installer, the package name, version code, completeness of the APK set and the signing certificate are verified. For an already installed app the signature must match the installed copy and the version must increase. A mismatch cancels the install with a stated reason.

NO TRACKING

The project has no backend. Requests go straight to RuStore and GitHub. No accounts, no analytics, no ad SDKs, no statistics. Everything the app stores stays on the device.

WHAT IT DOES NOT DO

• No paid apps or in-app purchases — free apps only.
• No Google Play catalogue — Wy Store neither reads nor replaces it.

Wy Store is an independent open-source project (MIT licence). It is not affiliated with RuStore, not endorsed by it and does not act on its behalf: RuStore is a data source. The app verifies file delivery but is not responsible for the content or behaviour of the programs it installs.

Source code and releases: github.com/wyrtensi/wystore
```

## What's new (0.2.0)

```
• The application id changed — updating from 0.1.x has to be installed by hand, and settings move over through Export and Import in "Backup and transfer".
• Full reviews for RuStore apps: a breakdown by star count and a filter by score.
• Cards stand out more against the background — the interface reads better on cheap screens and in daylight.
• The minimum Android version is now 9.0. On Android 8 the update signature check technically did nothing.
```
