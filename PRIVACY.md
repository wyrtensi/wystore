# Политика конфиденциальности Wy Store

Обновлено: 9 сентября 2026 г. Приложение: **Wy Store** (`app.wystore`).
Разработчик: wyrtensi, https://github.com/wyrtensi/wystore

*[English version below](#privacy-policy)*

## Коротко

Wy Store не собирает персональные данные, не заводит аккаунтов и не имеет собственного сервера.
Ни одна строка о вас не покидает устройство по инициативе приложения.

## Какие данные собираются

Никакие. В приложении нет ни регистрации, ни входа, ни идентификатора пользователя, ни аналитики,
ни рекламных SDK, ни сборщиков сбоев.

## Какие данные обрабатываются на устройстве

Всё перечисленное хранится только в памяти устройства, в приватном каталоге приложения, и никуда
не передаётся:

- список установленных приложений — нужен, чтобы показывать статус в каталоге (установлено,
  доступно обновление) и искать обновления;
- список приложений, обновления которых вы доверили Wy Store, и добавленные репозитории GitHub;
- настройки приложения;
- очередь загрузок и скачанные APK — удаляются по заданным сроку хранения и лимиту места;
- журнал последних событий для отчёта об ошибке — вы решаете, отправлять его или нет, и видите
  текст до отправки.

Удаление приложения удаляет всё это вместе с ним.

## Куда уходят сетевые запросы

Приложение обращается к трём группам адресов и ни к каким другим:

| Адрес | Зачем |
|---|---|
| `rustore.ru` и поддомены (`www`, `backapi`, `static`) | Каталог, страницы приложений, отзывы, значки, загрузка APK |
| `github.com`, `api.github.com` и хранилища релизов (`objects`, `release-assets`, `github-releases`.`githubusercontent.com`) | Список релизов и загрузка APK |
| `raw.githubusercontent.com`, `avatars.githubusercontent.com` | Значки проектов |

Запросы уходят напрямую этим сервисам. У проекта нет своего сервера и посредников. Как обычно при
любом сетевом запросе, принимающая сторона видит IP-адрес и заголовки запроса; Wy Store
представляется как `WyStore/<версия>` и не передаёт идентификаторов устройства, рекламных ID,
аккаунтов и токенов. `User-Token` RuStore приложение не создаёт и не использует.

Обработка данных на стороне RuStore и GitHub регулируется их собственными правилами; Wy Store
не связан с этими сервисами.

Запросы идут только к публичным адресам, которые отвечают без авторизации. Приложение не создаёт
сессию RuStore, не использует чужие учётные записи, не передаёт логинов и токенов и не обходит
защиту сервиса — подробно об этом в [SECURITY.md](SECURITY.md).

## Разрешения

| Разрешение | Зачем |
|---|---|
| Интернет, состояние сети | Загрузка каталога и APK, режим «только Wi-Fi» |
| Установка и удаление приложений | Установка скачанных APK и удаление из библиотеки |
| Обновление без подтверждения | Обновление того, что установил сам Wy Store |
| Доступ к списку установленных приложений | Статусы в каталоге и поиск обновлений; список не передаётся |
| Уведомления | Сообщения о найденных обновлениях и ходе загрузки |
| Фоновая работа, запуск после перезагрузки | Проверки по расписанию и загрузки, которые не обрываются |
| Исключение из оптимизации батареи | Запрашивается по желанию, чтобы фоновые проверки шли по расписанию |

## Резервные копии

Экспорт создаёт JSON-файл с настройками, списком принятых приложений и добавленными репозиториями.
Файл создаётся только по вашей команде и сохраняется туда, куда вы укажете. Приложение никуда его
не отправляет.

## Дети

Приложение не предназначено для детей и не собирает данные о них — как и ни о ком другом.

## Изменения

Актуальная версия документа лежит в репозитории проекта. Существенные изменения будут отмечены в
[CHANGELOG.md](CHANGELOG.md).

## Контакты

Вопросы — через [Issues](https://github.com/wyrtensi/wystore/issues) или
[Telegram-чат](https://t.me/+_YytpJdDHgQ4OTYy).

---

# Privacy Policy

Updated: 9 September 2026. Application: **Wy Store** (`app.wystore`).
Developer: wyrtensi, https://github.com/wyrtensi/wystore

## Summary

Wy Store collects no personal data, has no accounts and has no backend of its own. Nothing about you
leaves the device on the app's initiative.

## Data collected

None. There is no registration, no sign-in, no user identifier, no analytics, no ad SDKs and no
crash reporters.

## Data processed on the device

All of the following is stored only in the app's private storage on the device and is never
transmitted:

- the list of installed applications — used to show status in the catalogue (installed, update
  available) and to look for updates;
- the list of apps whose updates you handed to Wy Store, and the GitHub repositories you added;
- app settings;
- the download queue and downloaded APKs — removed according to the retention period and size limit
  you set;
- a log of recent events for a bug report — you decide whether to send it and can read it first.

Uninstalling the app removes all of it.

## Network requests

The app contacts three groups of addresses and no others:

| Address | Purpose |
|---|---|
| `rustore.ru` and its subdomains (`www`, `backapi`, `static`) | Catalogue, app pages, reviews, icons, APK downloads |
| `github.com`, `api.github.com` and release storage (`objects`, `release-assets`, `github-releases`.`githubusercontent.com`) | Release listings and APK downloads |
| `raw.githubusercontent.com`, `avatars.githubusercontent.com` | Project icons |

Requests go straight to those services; the project has no server and no intermediaries. As with any
network request, the receiving side sees the IP address and request headers; Wy Store identifies
itself as `WyStore/<version>` and sends no device identifiers, advertising IDs, accounts or tokens.
It never creates or uses a RuStore `User-Token`.

How RuStore and GitHub handle data is governed by their own policies; Wy Store is not affiliated
with either.

Requests go only to public addresses that answer without authentication. The app creates no RuStore
session, uses nobody else's account, sends no logins or tokens, and works around none of the
service's protections - see [SECURITY.md](SECURITY.md) for the detail.

## Permissions

| Permission | Purpose |
|---|---|
| Internet, network state | Fetching the catalogue and APKs, Wi-Fi-only mode |
| Install and uninstall packages | Installing downloaded APKs and removing apps from the library |
| Update without user action | Updating what Wy Store installed itself |
| Query installed packages | Catalogue statuses and update discovery; the list is never transmitted |
| Notifications | Messages about found updates and download progress |
| Background work, start after boot | Scheduled checks and downloads that are not cut short |
| Battery optimisation exemption | Requested optionally so that background checks run on schedule |

## Backups

Export produces a JSON file with settings, adopted apps and added repositories. It is created only
on your command and saved where you point it. The app never uploads it.

## Children

The app is not directed at children and collects no data about them — or about anyone else.

## Changes

The current version of this document lives in the project repository. Material changes will be noted
in [CHANGELOG.md](CHANGELOG.md).

## Contact

Questions via [Issues](https://github.com/wyrtensi/wystore/issues) or the
[Telegram chat](https://t.me/+_YytpJdDHgQ4OTYy).
