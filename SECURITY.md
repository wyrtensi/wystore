# Безопасность

[English below](#security-policy)

## Поддерживаемые версии

Исправления выходят только для последнего релиза. Обновиться до него — часть решения любой
проблемы.

| Версия | Поддержка |
|---|---|
| 0.2.x | Да |
| 0.1.x | Нет |

## Как сообщить об уязвимости

**Не открывайте публичную задачу для того, чем можно воспользоваться.** Пока исправления нет,
описание в открытом доступе помогает не пользователям.

Напишите приватно через GitHub: вкладка **Security → Report a vulnerability** в этом репозитории.
Если приватное сообщение по какой-то причине недоступно, напишите в
[Telegram-чат](https://t.me/+_YytpJdDHgQ4OTYy) без подробностей — попросите связь и передайте
детали лично.

Что помогает в отчёте:

- версия Wy Store, версия Android и модель устройства;
- что происходит и чего вы ожидали;
- шаги для воспроизведения — по возможности минимальные;
- если это про установку APK — какой файл, из какого источника, с какой подписью;
- отчёт из **Настройки → О приложении → Отчёт для сообщения об ошибке**, если он относится к делу.

Проект ведёт один человек, поэтому сроков вроде «ответ за сутки» здесь не будет. На письма отвечаю,
как только вижу; о ходе работы сообщаю в той же переписке. Раскрытие — согласованное: сначала
исправление и релиз, потом публикация. Авторство находки указывается в CHANGELOG.md, если вы не
против.

## Как Wy Store обращается к RuStore

Приложение пользуется только тем, что RuStore отдаёт публично, и не обходит его защиту.

- Адреса `backapi.rustore.ru` и `www.rustore.ru` отвечают по интернету **без пользовательской
  авторизации**. Именно на них Wy Store и ходит — теми же запросами, что и открытая страница
  каталога.
- Приложение **не крадёт логины и токены**, не создаёт и не подделывает сессию RuStore, не
  пользуется чужой учётной записью и ничего не подбирает к паролю. `User-Token` официального
  клиента не запрашивается и не используется.
- Приложение **не эксплуатирует уязвимости**: ни SQL-инъекций, ни выполнения кода, ни обхода
  проверок на стороне сервиса. Ничего закрытого — персональных данных, платного контента,
  внутренних панелей — оно не получает и не пытается получить.
- Бесплатный APK в итоге выдаёт **сама инфраструктура RuStore** по её собственной ссылке. Wy Store
  не раздаёт файлы со своей стороны и не хранит их копий: своего сервера у проекта нет.
- Единственное, что приложение подставляет от себя, — числовой заголовок `ruStoreVerCode`, который
  сервис ждёт от любого клиента. Он **зашит в сборку** и виден в исходниках; никаких пользовательских
  данных в нём нет. Раньше приложение вычисляло его само, скачивая официальный клиент, — этот
  механизм убран.
- Wy Store читает публичные страницы, а не документированный стабильный API. Разбор маршрутов — в
  [docs/RUSTORE_API_COMPATIBILITY_RU.md](docs/RUSTORE_API_COMPATIBILITY_RU.md).

Если RuStore закроет эти адреса или потребует авторизацию, Wy Store перестанет их читать. Обходить
такое ограничение проект не будет.

## Что относится к этой политике

Всё, что касается доставки и установки:

- проверка APK перед установкой — имя пакета, versionCode, комплектность набора split-APK,
  сертификат подписи и его сверка с установленной копией;
- обход этих проверок любым способом, включая подмену файла между проверкой и установкой;
- установка без системного диалога: получение такой возможности для приложения, которому она не
  положена, или для APK, не прошедшего проверку;
- загрузка: выход за список разрешённых хостов, обработка перенаправлений, работа с TLS;
- обновление самого Wy Store;
- разбор данных источника — падения и порча данных на подготовленном ответе RuStore или GitHub;
- файл резервной копии: его импорт не должен приводить к выполнению кода или к тихой смене
  чувствительных настроек;
- утечка чего-либо за пределы устройства — приложение не должно отправлять данные никуда, кроме
  описанного в [PRIVACY.md](PRIVACY.md).

## Что к ней не относится

- **Приложения, установленные через Wy Store.** Он проверяет доставку файла, а не то, что этот файл
  делает. Вопросы по стороннему приложению — его разработчику или источнику.
- **RuStore и GitHub.** Уязвимости в самих сервисах — к ним.
- **Возможность ставить APK из интернета.** Это назначение приложения, а не дефект. То же про
  установку без диалога на Android 12 и новее для приложений, которые установил сам Wy Store: так
  работает Android.
- **Режим с root.** Он выключен по умолчанию и включается сознательно. Последствия выдачи root
  любому приложению — известный размен, а не находка.
- **Отсутствие защиты, требующей сервера.** У проекта его нет и не будет: нет ни репутации
  приложений, ни сканирования, ни отзыва установок.
- Отчёты автоматических сканеров без сценария, в котором из этого что-то следует.

## Подпись релизов

Все релизы подписаны одним ключом. Отпечаток сертификата SHA-256:

```
33:dc:b5:00:61:a0:81:90:f6:8b:be:e2:d7:43:be:38:ad:4f:73:07:d9:8d:c1:8d:d7:e7:67:19:c8:15:a2:58
```

Проверить скачанный файл:

```bash
apksigner verify --print-certs wystore-<версия>.apk
```

В релизе два файла, `wystore-<версия>.apk` и `wystore.apk`, — это один и тот же APK с одной
подписью. Если отпечаток не совпал, файл получен не отсюда: не устанавливайте его и сообщите.

Смена ключа подписи будет объявлена в CHANGELOG.md и в описании релиза заранее. Android не даст
обновить приложение сборкой с другой подписью — это защита, а не сбой.

---

# Security Policy

## Supported versions

Fixes ship for the latest release only. Updating to it is part of the answer to any problem.

| Version | Supported |
|---|---|
| 0.2.x | Yes |
| 0.1.x | No |

## Reporting a vulnerability

**Do not open a public issue for anything exploitable.** Until a fix exists, a public description
helps the wrong people.

Report privately through GitHub: the **Security → Report a vulnerability** tab on this repository.
If private reporting is unavailable to you, say so in the
[Telegram chat](https://t.me/+_YytpJdDHgQ4OTYy) without details and ask for a way to send them
directly.

What helps in a report:

- the Wy Store version, the Android version and the device model;
- what happens and what you expected instead;
- steps to reproduce, as small as you can make them;
- for anything about APK installation: which file, from which source, with which signature;
- the report from **Settings → About → Diagnostics report**, when it is relevant.

This is a one-person project, so there is no "response within 24 hours" here. Reports are answered
as soon as they are seen, and progress is shared in the same thread. Disclosure is coordinated: fix
and release first, publication after. Credit goes in CHANGELOG.md unless you would rather it did
not.

## How Wy Store talks to RuStore

The app uses only what RuStore serves publicly, and works around none of its protections.

- `backapi.rustore.ru` and `www.rustore.ru` answer over the internet **without user
  authentication**. Those are the addresses Wy Store calls, with the same requests the open
  catalogue page makes.
- The app **steals no logins and no tokens**, creates or forges no RuStore session, uses nobody
  else's account and guesses at no password. The official client's `User-Token` is neither
  requested nor used.
- The app **exploits nothing**: no SQL injection, no code execution, no bypassing of server-side
  checks. It obtains no private data - personal records, paid content, internal consoles - and does
  not try to.
- A free APK is served in the end by **RuStore's own infrastructure**, from its own link. Wy Store
  serves no files itself and keeps no copies of them: the project has no server.
- The one thing the app supplies of its own is the numeric `ruStoreVerCode` header the service
  expects from any client. It is **compiled into the build** and visible in the source; it carries
  no user data. The app used to work the value out by downloading the official client - that
  mechanism has been removed.
- Wy Store reads public pages, not a documented stable API. The routes are described in
  [docs/RUSTORE_API_COMPATIBILITY_RU.md](docs/RUSTORE_API_COMPATIBILITY_RU.md) (Russian).

If RuStore closes those addresses or starts requiring authentication, Wy Store will stop reading
them. The project will not work around such a restriction.

## In scope

Everything about delivery and installation:

- the pre-install APK checks — package name, versionCode, completeness of the split-APK set, the
  signing certificate and its comparison against the installed copy;
- bypassing any of those checks, including swapping the file between verification and install;
- installation without the system dialog: obtaining it for an app not entitled to it, or for an APK
  that failed verification;
- downloads: escaping the host allowlist, redirect handling, TLS behaviour;
- Wy Store's own update path;
- source parsing — crashes or data corruption on a crafted RuStore or GitHub response;
- the backup file: importing one must not execute code or silently change sensitive settings;
- anything leaving the device: the app must send data nowhere beyond what
  [PRIVACY.md](PRIVACY.md) describes.

## Out of scope

- **Apps installed through Wy Store.** It verifies the delivery of a file, not what the file does.
  Take those to the app's developer or its source.
- **RuStore and GitHub themselves.** Vulnerabilities in those services belong to them.
- **The ability to install APKs from the internet.** That is what the app is for, not a defect. The
  same goes for dialog-free updates, on Android 12 and newer, of apps Wy Store installed: that is
  how Android works.
- **Root mode.** It is off by default and turned on deliberately. The consequences of granting root
  to any app are a known trade-off, not a finding.
- **The absence of protections that need a server.** There is no backend and there will not be one:
  no app reputation, no scanning, no remote revocation.
- Automated scanner output with no scenario in which anything follows from it.

## Release signing

Every release is signed with the same key. Certificate SHA-256 fingerprint:

```
33:dc:b5:00:61:a0:81:90:f6:8b:be:e2:d7:43:be:38:ad:4f:73:07:d9:8d:c1:8d:d7:e7:67:19:c8:15:a2:58
```

To check a downloaded file:

```bash
apksigner verify --print-certs wystore-<version>.apk
```

A release carries two files, `wystore-<version>.apk` and `wystore.apk` — the same APK with the same
signature. If the fingerprint does not match, the file did not come from here: do not install it,
and report it.

A change of signing key would be announced in CHANGELOG.md and in the release notes ahead of time.
Android refuses to update an app with a differently signed build — that is the protection working,
not a failure.
