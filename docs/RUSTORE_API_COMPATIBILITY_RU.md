# Совместимость с неофициальным API RuStore

Проверено 10 сентября 2026 года. Описанные маршруты являются внутренними публично доступными web-endpoints RuStore, а не документированным стабильным API. WyStore не получает и не подделывает пользовательскую сессию RuStore.

## Используемые endpoints

1. Поиск: `GET https://www.rustore.ru/catalog/search?query={query}` — HTML публичного каталога.
2. Карточка и `appId`: `GET https://backapi.rustore.ru/applicationData/overallInfo/{packageName}`.
3. Файлы для конкретного устройства: `POST https://backapi.rustore.ru/applicationData/v2/download-link`.
4. Endpoint v1 `POST /applicationData/download-link` существует, но WyStore его не использует, потому что v2 возвращает полный набор split APK с учётом SDK, DPI и ABI устройства.

Для двух JSON-endpoints передаётся числовой заголовок `ruStoreVerCode`. Для POST также передаются `Content-Type: application/json; charset=utf-8`, `appId`, `firstInstall`, `screenDensity`, `sdkVersion`, `withoutSplits` и `supportedAbis`.

## Причина HTTP 419

Manifest `versionCode` официального APK и значение `ruStoreVerCode` — разные форматы:

- APK RuStore `1.108.0.2`: manifest `versionCode = 1108002`;
- API-код из той же версии: `1.108.0.2 -> 110802`.

Прежняя реализация записывала `1108002` прямо в HTTP-заголовок. На текущем backend это значение восемь раз из восьми вернуло пустой HTTP 419. Без `User-Token` значения `110802`, `1000000` и `247` успешно прошли как `overallInfo`, так и `v2/download-link`.

Независимый GitHub-фикс подтверждает диапазон: меньше `247` возвращает HTTP 417, а слишком большое значение — HTTP 419; разные backend-инстансы могут иметь немного разные верхние границы. Поэтому один вечный hardcode ненадёжен.

## Реализация WyStore

- Код зашит в приложение: `110910`, из версии официального клиента `1.109.1.0`. Меняется вместе с релизом Wy Store, а не в работающем приложении.
- Каждый реальный GET/POST при 417/419 повторяет текущий код до трёх раз, затем пробует `1000000` и `247`. Это единственное, что осталось от подбора: запас на случай, если backend перестанет принимать зашитое значение раньше, чем выйдет новая версия приложения.
- Сетевая ошибка, HTTP 4xx/5xx другого типа или изменившийся JSON-формат не выдаются за проблему совместимости.
- Семизначное старое значение из настроек 0.1.8 мигрирует на зашитый код. Старое поле резервной копии `usedVersionCode` читается как `apiVersionCode`.
- Числовой `minSdkVersion` из `overallInfo` проверяется до загрузки. Если последняя версия приложения требует более новый Android, WyStore показывает обе версии SDK и не скачивает заведомо неустанавливаемый APK. Пустой `downloadUrls` для совместимого SDK диагностируется отдельно.
- Endpoint v1 не используется как обход этой проверки: он может вернуть ZIP последней версии без учёта SDK, но Android всё равно отвергнет несовместимый APK.

## Почему нет monkeypatch `User-Token`

Текущий официальный клиент действительно использует сессионный `User-Token` на другом внутреннем host, но проверенные endpoints `backapi.rustore.ru` не требуют его. Синтез или копирование токена привязало бы WyStore к аккаунту, сроку жизни сессии и внутренней авторизации, не устраняя настоящую причину 419. Разрешённый compatibility monkeypatch ограничен заголовком версии и не содержит пользовательских данных.

## Источники

- [Описание маршрутов RuStore API](https://gist.github.com/oldnomad/5d38a9ea9b1daf9d82fa4f655b9aebe8)
- [GitHub-фикс HTTP 419 с ограниченным fallback](https://github.com/Dynamic-Mobile-Security/mdast-cli/commit/5f4787d0)
- [Рабочий userscript с API-кодом 110802 и endpoint v2](https://gist.github.com/smi-falcon/f75edfb5c0143f81ff6ea25e315f0964)
