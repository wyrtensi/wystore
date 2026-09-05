package dev.wystore.background

import java.util.concurrent.TimeUnit

/**
 * When Wy Store is allowed to spend the user's battery.
 *
 * A store that nobody opens for a week should cost close to nothing. The rules here decide whether
 * a periodic check runs at all and how loosely it may be scheduled; keeping them free of Android
 * types makes each one testable.
 */
object BackgroundPolicy {

    /**
     * Share of the period the system may shift the run by.
     *
     * A periodic job with a flex window can be batched with whatever else the device is already
     * waking up for, instead of forcing a radio wake of its own. Android clamps flex to at least
     * five minutes, so short intervals fall back to that.
     */
    private const val FLEX_FRACTION = 0.5

    /** Android refuses a flex shorter than this. */
    val MIN_FLEX_MINUTES: Long = TimeUnit.MINUTES.toMinutes(5)

    /**
     * Whether a periodic check should do any work this time round.
     *
     * A manual check always runs: the user is standing there waiting for it. A background one
     * stands down while the device is saving power or has nothing to check, because the next
     * period will come round anyway.
     */
    fun shouldRunCheck(
        manual: Boolean,
        powerSaveMode: Boolean,
        respectBatterySaver: Boolean,
        managedAppCount: Int
    ): Boolean {
        if (manual) return true
        if (managedAppCount == 0) return false
        return !(powerSaveMode && respectBatterySaver)
    }

    /** Flex window for a periodic check of [intervalHours], in minutes. */
    fun flexMinutesFor(intervalHours: Long): Long {
        val interval = intervalHours.coerceAtLeast(1)
        val flex = (TimeUnit.HOURS.toMinutes(interval) * FLEX_FRACTION).toLong()
        return flex.coerceAtLeast(MIN_FLEX_MINUTES)
    }

    /**
     * Whether the periodic worker is worth having scheduled at all.
     *
     * With nothing adopted there is nothing to check, and an empty periodic job still costs a wake
     * every interval.
     */
    fun shouldSchedulePeriodicWork(managedAppCount: Int): Boolean = managedAppCount > 0
}
