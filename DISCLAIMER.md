# Заявление о проекте

[English below](#about-this-project)

Этот документ описывает, чем Wy Store является и чем не является. Он написан авторами проекта и
**не является юридической консультацией**. Если вы распространяете Wy Store или строите на нём
что-то, оцените применимое к вам законодательство самостоятельно или со специалистом.

## Независимость

Wy Store — независимый клиент с открытым исходным кодом. Он **не связан с RuStore, VK, Google или
GitHub**, не одобрен ими, не сертифицирован и не действует от их имени. Названия RuStore, VK,
Google Play, Android и GitHub принадлежат их владельцам и используются здесь исключительно для того,
чтобы назвать соответствующий сервис.

Приложение не использует логотипы, фирменный стиль или иные визуальные обозначения RuStore. Имя
«Wy Store», иконка и оформление созданы для этого проекта.

## Что приложение делает технически

- **Ничего не хостит и не перераспространяет.** Wy Store не содержит в себе APK сторонних
  приложений и не размещает их на своих серверах — своих серверов у проекта нет вовсе. Файлы
  скачиваются напрямую с `rustore.ru` и `github.com` по ссылкам, которые эти сервисы публикуют сами.
- **Представляется честно.** Каждый запрос уходит с `User-Agent: WyStore/<версия>`. Приложение не
  маскируется под официальный клиент RuStore и не подделывает его идентификаторы.
- **Не создаёт и не копирует учётные данные.** Wy Store не заводит аккаунт, не логинится и не
  создаёт и не копирует `User-Token` RuStore. Всё, что он читает, доступно без авторизации.
- **Не обходит оплату и защиту.** Поддерживаются только бесплатные приложения в текущей версии.
  В коде нет обработки покупок, лицензий и DRM, и не предпринимается попыток их обойти.
- **Не изменяет чужие приложения.** Скачанный APK устанавливается ровно в том виде, в котором его
  опубликовал источник; Wy Store только проверяет его подпись и версию перед установкой.
- **Использует публичные веб-эндпоинты.** Это не поддерживаемое клиентское API, и источник вправе
  изменить или закрыть его в любой момент. Используемые адреса описаны в
  [docs/RUSTORE_API_COMPATIBILITY_RU.md](docs/RUSTORE_API_COMPATIBILITY_RU.md).
- **Щадит источник.** Ответы кешируются, фоновые проверки идут с гибким окном и не чаще выбранного
  интервала. Приложение не выкачивает каталог целиком и не создаёт нагрузки сверх той, что создал бы
  человек, листающий сайт.

## Ответственность

Wy Store проверяет доставку: имя пакета, версию, целостность комплекта APK и совпадение подписи.
Он **не проверяет и не модерирует сами приложения** и не отвечает за их содержимое, поведение и
последствия использования. Устанавливая приложение, вы делаете это на свой риск и отвечаете за
соблюдение законодательства своей юрисдикции.

Программа поставляется «как есть», без каких-либо гарантий — см. [LICENSE](LICENSE).

## Претензии и обращения

Если вы правообладатель и считаете, что проект нарушает ваши права, откройте issue в этом
репозитории или свяжитесь с владельцем аккаунта [@wyrtensi](https://github.com/wyrtensi). Проект не
размещает чужих бинарных файлов, поэтому в большинстве случаев речь будет идти о коде или тексте в
этом репозитории — и то и другое может быть изменено или удалено.

---

# About this project

This document states what Wy Store is and is not. It is written by the project's authors and is
**not legal advice**. If you distribute Wy Store or build on it, assess the law that applies to you
yourself or with a professional.

## Independence

Wy Store is an independent, open-source client. It is **not affiliated with RuStore, VK, Google or
GitHub**, not endorsed or certified by them, and does not act on their behalf. RuStore, VK, Google
Play, Android and GitHub are trademarks of their respective owners and are used here only to name
the corresponding service.

The app uses no RuStore logo, brand styling or other visual identifier. The name "Wy Store", the
icon and the design were made for this project.

## What the app actually does

- **Hosts and redistributes nothing.** Wy Store bundles no third-party APK and serves none from its
  own servers — the project has no servers at all. Files are downloaded directly from `rustore.ru`
  and `github.com` using links those services publish themselves.
- **Identifies itself honestly.** Every request carries `User-Agent: WyStore/<version>`. The app
  does not disguise itself as the official RuStore client or forge its identifiers.
- **Creates and copies no credentials.** Wy Store has no account, never logs in, and never creates
  or copies a RuStore `User-Token`. Everything it reads is available without authentication.
- **Circumvents no payment or protection.** Only free applications in their current version are
  supported. There is no purchase, licence or DRM handling in the code, and no attempt to bypass any.
- **Modifies nobody's application.** A downloaded APK is installed exactly as the source published
  it; Wy Store only verifies its signature and version beforehand.
- **Uses public web endpoints.** These are not a supported consumer API, and the source may change
  or withdraw them at any time. The addresses used are documented in
  [docs/RUSTORE_API_COMPATIBILITY_RU.md](docs/RUSTORE_API_COMPATIBILITY_RU.md) (in Russian).
- **Is gentle with the source.** Responses are cached, background checks run in a flex window and no
  more often than the chosen interval. The app does not scrape the whole catalogue or generate load
  beyond what a person browsing the site would.

## Responsibility

Wy Store verifies delivery: package name, version, APK-set integrity and signature match. It does
**not review or moderate the applications themselves** and is not responsible for their content,
behaviour or the consequences of using them. You install at your own risk and are responsible for
complying with the law in your jurisdiction.

The software is provided "as is", without warranty of any kind — see [LICENSE](LICENSE).

## Claims and contact

If you are a rights holder and believe this project infringes your rights, open an issue in this
repository or contact the account owner, [@wyrtensi](https://github.com/wyrtensi). The project hosts
no third-party binaries, so in most cases the subject will be code or text in this repository, and
either can be changed or removed.
