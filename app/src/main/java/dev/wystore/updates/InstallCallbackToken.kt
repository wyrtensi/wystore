package dev.wystore.updates

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

object InstallCallbackToken {
    fun matches(expected: String?, actual: String?): Boolean {
        if (expected.isNullOrEmpty() || actual.isNullOrEmpty()) return false
        return MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
    }
}

class InstallCallbackStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("install_callbacks", Context.MODE_PRIVATE)

    fun create(packageName: String): String = synchronized(lock) {
        val createdAt = preferences.getLong("${packageName}_at", 0L)
        require(System.currentTimeMillis() - createdAt > ACTIVE_TIMEOUT_MS) { "An install is already awaiting confirmation" }
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val token = bytes.joinToString("") { byte -> "%02x".format(byte) }
        preferences.edit()
            .putString(packageName, token)
            .putLong("${packageName}_at", System.currentTimeMillis())
            .commit()
        token
    }

    fun matches(packageName: String, actual: String?): Boolean =
        InstallCallbackToken.matches(preferences.getString(packageName, null), actual)

    fun clear(packageName: String) {
        preferences.edit().remove(packageName).remove("${packageName}_at").commit()
    }

    companion object {
        private const val ACTIVE_TIMEOUT_MS = 15 * 60 * 1000L
        private val lock = Any()
    }
}
