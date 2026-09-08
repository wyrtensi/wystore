package dev.wystore.updates

import android.app.ActivityManager.RunningAppProcessInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingUserActionPolicyTest {

    @Test
    fun anAppOnScreenCanRaiseTheDialog() {
        assertTrue(PendingUserActionPolicy.canConfirmNow(RunningAppProcessInfo.IMPORTANCE_FOREGROUND))
    }

    @Test
    fun aForegroundServiceCannot() {
        // The download worker runs as one, and since Android 12 that is not enough to start an
        // activity. Trying anyway loses the install with no error anywhere.
        assertFalse(
            PendingUserActionPolicy.canConfirmNow(RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE)
        )
    }

    @Test
    fun aBackgroundOrCachedProcessCannot() {
        assertFalse(PendingUserActionPolicy.canConfirmNow(RunningAppProcessInfo.IMPORTANCE_VISIBLE))
        assertFalse(PendingUserActionPolicy.canConfirmNow(RunningAppProcessInfo.IMPORTANCE_SERVICE))
        assertFalse(PendingUserActionPolicy.canConfirmNow(RunningAppProcessInfo.IMPORTANCE_CACHED))
    }
}
