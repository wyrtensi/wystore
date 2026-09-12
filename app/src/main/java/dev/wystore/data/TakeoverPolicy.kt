package dev.wystore.data

/** How Wy Store can become the installer of record for an app it did not install. */
enum class TakeoverPath {
    /** Nothing to hand over: the app is not installed, or Wy Store already owns its updates. */
    NONE,

    /** Installing the catalogue's copy over the top works, and the app's data survives it. */
    IN_PLACE,

    /** Android would refuse the install; the app has to be removed first, losing its data. */
    REPLACE
}

/** Why an in-place handover is impossible, so the screen can say which of the two it is. */
enum class TakeoverObstacle {
    NONE,

    /** The source signs with a different certificate - nothing else may update the app in place. */
    SIGNATURE,

    /** The catalogue carries an older build, and Android never installs backwards over an app. */
    OLDER_IN_CATALOGUE
}

data class TakeoverDecision(
    val path: TakeoverPath,
    val obstacle: TakeoverObstacle = TakeoverObstacle.NONE
)

/**
 * Decides what "hand updates to Wy Store" has to do for one app.
 *
 * Only the installer of record may update an app without a dialog, and the way to become it is to
 * install the app. Usually that is an install over the top: same certificate, and a build that is
 * not older than the one on the phone. Two things make it impossible, and until now the store knew
 * about one of them, in one place, by the wrong evidence - it asked whether Google Play was the
 * installer instead of asking whether the install could succeed.
 *
 * The certificate is the first question, because no version ever fixes it: an app Google signed
 * carries Google's key, and an APK from anywhere else cannot install over it. The version is the
 * second: a catalogue that is behind the phone offers a downgrade, which Android refuses and which
 * this store used to download in full before discarding in silence.
 *
 * Either way the answer is the same shape - remove it, then install the catalogue's copy - and it
 * is offered rather than performed, because it costs the app's data.
 */
object TakeoverPolicy {

    /**
     * @param installedVersionCode null when the app is not on the phone, which is an install and
     * not a handover.
     * @param ownedByStore whether Wy Store is already the installer of record.
     * @param acceptedSourceDigest a signature the user accepted from this source in place of the
     * one it advertises: the phone then holds the file the source serves, and removing the app to
     * install that same file again would cost its data for nothing.
     * @param catalogVersionCode null when the source states nothing comparable, in which case the
     * version cannot rule anything out and the archive decides at install time.
     * @param storeCanBecomeInstaller false on a device whose firmware refuses installer sessions.
     * Every install there goes through the system installer, which stays the installer of record,
     * so a handover would download and reinstall the app and change nothing - and the offer would
     * never go away. Only the signature case is still worth offering: until the app carries the
     * source's certificate, no update from here can install at all.
     */
    fun decide(
        installedVersionCode: Long?,
        installedDigests: Set<String>,
        ownedByStore: Boolean,
        catalogVersionCode: Long?,
        catalogSignatureHint: String?,
        acceptedSourceDigest: String? = null,
        storeCanBecomeInstaller: Boolean = true
    ): TakeoverDecision {
        if (installedVersionCode == null || ownedByStore) return TakeoverDecision(TakeoverPath.NONE)
        val compatibility = SignatureCompatibilityPolicy.evaluate(
            installedDigests = installedDigests,
            declaredFingerprint = catalogSignatureHint,
            acceptedFingerprint = acceptedSourceDigest
        )
        if (compatibility == SignatureCompatibility.MISMATCH) {
            return TakeoverDecision(TakeoverPath.REPLACE, TakeoverObstacle.SIGNATURE)
        }
        if (!storeCanBecomeInstaller) return TakeoverDecision(TakeoverPath.NONE)
        if (catalogVersionCode != null && catalogVersionCode < installedVersionCode) {
            return TakeoverDecision(TakeoverPath.REPLACE, TakeoverObstacle.OLDER_IN_CATALOGUE)
        }
        return TakeoverDecision(TakeoverPath.IN_PLACE)
    }
}
