package dev.wystore.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap

/**
 * The app's own icon, for the notification that talks about it.
 *
 * A notification carrying nothing but a monochrome glyph looks like a system message; the icon is
 * how the user recognises which app the line is about without reading it. Only installed packages
 * can be asked for one - a first install has nothing on the device yet - so the caller has to cope
 * with null rather than assume an icon is always there.
 */
object NotificationArt {
    /** Large icons are displayed small; anything bigger is memory spent on nothing. */
    private const val MAX_SIZE_PX = 192

    fun iconFor(context: Context, packageName: String): Bitmap? = runCatching {
        toBitmap(context.packageManager.getApplicationIcon(packageName))
    }.getOrNull()

    private fun toBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap ?: return null
            return scaled(bitmap)
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: MAX_SIZE_PX
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: MAX_SIZE_PX
        val bitmap = createBitmap(width.coerceAtMost(MAX_SIZE_PX), height.coerceAtMost(MAX_SIZE_PX))
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun scaled(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_SIZE_PX) return bitmap
        val ratio = MAX_SIZE_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
