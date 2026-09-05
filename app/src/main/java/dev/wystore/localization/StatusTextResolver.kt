package dev.wystore.localization

import android.content.Context
import dev.wystore.ui.components.StatusCode
import dev.wystore.ui.components.StatusMessage
import java.util.Locale

object StatusTextResolver {

    fun resolve(message: StatusMessage, isRussian: Boolean): String = when (message.code) {
        StatusCode.CHECKING -> if (isRussian) "Проверка обновлений..." else "Checking for updates..."
        StatusCode.QUEUED -> if (isRussian) "В очереди" else "Queued"
        StatusCode.DOWNLOADING -> {
            val percent = message.args["percent"] ?: "0"
            val downloaded = message.args["downloaded"]
            val total = message.args["total"]
            if (downloaded != null && total != null) {
                if (isRussian) "Скачивание $percent% ($downloaded/$total)"
                else "Downloading $percent% ($downloaded/$total)"
            } else {
                if (isRussian) "Скачивание $percent%"
                else "Downloading $percent%"
            }
        }
        StatusCode.VERIFYING -> if (isRussian) "Проверка пакета..." else "Verifying package..."
        StatusCode.READY_TO_INSTALL -> if (isRussian) "Готово к установке" else "Ready to install"
        StatusCode.AWAITING_UNKNOWN_SOURCES_PERMISSION ->
            if (isRussian) "Разрешите установку из этого источника" else "Grant permission to install apps"
        StatusCode.AWAITING_USER_CONFIRMATION ->
            if (isRussian) "Подтвердите установку" else "Confirm installation"
        StatusCode.INSTALLING -> if (isRussian) "Установка..." else "Installing..."
        StatusCode.INSTALLED -> if (isRussian) "Успешно установлено" else "Installed successfully"
        StatusCode.FAILED_NETWORK -> if (isRussian) "Ошибка сети" else "Network error"
        StatusCode.FAILED_STORAGE -> if (isRussian) "Недостаточно места" else "Storage is full"
        StatusCode.FAILED_SIGNATURE -> if (isRussian) "Несовпадение цифровой подписи" else "Signature mismatch"
        StatusCode.FAILED_GENERIC -> {
            val detail = message.args["detail"]
            if (!detail.isNullOrBlank()) detail
            else if (isRussian) "Ошибка установки" else "Installation failed"
        }
        StatusCode.CANCELLED -> if (isRussian) "Отменено" else "Cancelled"
        StatusCode.SKIPPED -> if (isRussian) "Пропущено" else "Skipped"
        StatusCode.OFFER_NEXT -> if (isRussian) "Обновить следующее приложение" else "Update next app in queue"
    }

    fun resolve(context: Context, message: StatusMessage): String {
        val currentLocale = context.resources.configuration.locales.get(0) ?: Locale.getDefault()
        val isRussian = currentLocale.language.startsWith("ru", ignoreCase = true)
        return resolve(message, isRussian)
    }
}
