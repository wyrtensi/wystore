package dev.wystore.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import dev.wystore.settings.DeviceType

/** Which interface and which RuStore catalogue this device gets. */
enum class DeviceProfile { PHONE, TV }

/** What the system says about itself, read once per question and nothing more. */
data class DeviceTraits(
    val televisionUiMode: Boolean,
    val hasLeanback: Boolean
) {
    val looksLikeTv: Boolean get() = televisionUiMode || hasLeanback

    companion object {
        fun read(context: Context): DeviceTraits {
            val uiMode = context.getSystemService(UiModeManager::class.java)?.currentModeType
            return DeviceTraits(
                televisionUiMode = uiMode == Configuration.UI_MODE_TYPE_TELEVISION,
                hasLeanback = runCatching {
                    context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                }.getOrDefault(false)
            )
        }
    }
}

/**
 * Turns the user's choice and the device's own signals into one answer.
 *
 * Only two signals count: the television UI mode, which Android TV, Google TV and Fire TV set, and
 * the leanback feature every certified Android TV declares. A missing touchscreen is deliberately
 * not one of them - it is what a cheap box on tablet firmware looks like, and also what plenty of
 * other things look like. Those boxes are what the manual choice is for.
 */
object DeviceProfilePolicy {
    fun resolve(choice: DeviceType, traits: DeviceTraits): DeviceProfile = when (choice) {
        DeviceType.PHONE -> DeviceProfile.PHONE
        DeviceType.TV -> DeviceProfile.TV
        DeviceType.AUTO -> if (traits.looksLikeTv) DeviceProfile.TV else DeviceProfile.PHONE
    }
}
