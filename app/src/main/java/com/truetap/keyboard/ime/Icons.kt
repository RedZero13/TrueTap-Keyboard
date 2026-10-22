package com.truetap.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View

/** Line icons drawn on a 24-unit grid, so the keyboard needs no image assets. */
enum class Icon {
    SHIFT, SHIFT_ON, SHIFT_LOCK, DELETE, ENTER, SEARCH, SEND, ARROW_RIGHT, DONE, GLOBE, EMOJI, MIC,
    SETTINGS, GIF, STICKER, CLIPBOARD, ONE_HANDED, FLOATING, BOOK, THESAURUS, KEYBOARD, CHEVRON_LEFT,
    CHEVRON_RIGHT, EXPAND, CLOSE, GRID, TARGET, PALETTE, PIN, TRASH, BACK
}

object Icons {
    private val path = Path()
    private val rect = RectF()

    fun draw(canvas: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, color: Int, paint: Paint) {
        val s = size / 24f
        canvas.save()
        canvas.translate(cx - size / 2, cy - size / 2)
        canvas.scale(s, s)
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.isAntiAlias = true
        val p = path
        p.reset()
        when (icon) {
            Icon.SHIFT, Icon.SHIFT_ON, Icon.SHIFT_LOCK -> {
                p.moveTo(12f, 4f); p.lineTo(20f, 12.5f); p.lineTo(15.5f, 12.5f); p.lineTo(15.5f, 18f)
                p.lineTo(8.5f, 18f); p.lineTo(8.5f, 12.5f); p.lineTo(4f, 12.5f); p.close()
                if (icon != Icon.SHIFT) { paint.style = Paint.Style.FILL_AND_STROKE }
                canvas.drawPath(p, paint)
                if (icon == Icon.SHIFT_LOCK) {
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(8.5f, 21f, 15.5f, 21f, paint)
                }
            }
            Icon.DELETE -> {
                p.moveTo(8f, 5f); p.lineTo(21f, 5f); p.lineTo(21f, 19f); p.lineTo(8f, 19f); p.lineTo(2f, 12f); p.close()
                canvas.drawPath(p, paint)
                canvas.drawLine(11f, 9f, 17f, 15f, paint)
                canvas.drawLine(17f, 9f, 11f, 15f, paint)
            }
            Icon.ENTER -> {
                p.moveTo(19f, 5f); p.lineTo(19f, 13f); p.lineTo(5f, 13f)
                p.moveTo(9f, 9f); p.lineTo(5f, 13f); p.lineTo(9f, 17f)
                canvas.drawPath(p, paint)
            }
            Icon.SEARCH -> {
                canvas.drawCircle(10f, 10f, 6f, paint)
                canvas.drawLine(14.5f, 14.5f, 20f, 20f, paint)
            }
            Icon.SEND -> {
                p.moveTo(3f, 20f); p.lineTo(21f, 12f); p.lineTo(3f, 4f); p.lineTo(5.5f, 12f); p.close()
                canvas.drawPath(p, paint)
                canvas.drawLine(5.5f, 12f, 12f, 12f, paint)
            }
            Icon.ARROW_RIGHT -> {
                canvas.drawLine(4f, 12f, 20f, 12f, paint)
                p.moveTo(14f, 6f); p.lineTo(20f, 12f); p.lineTo(14f, 18f)
                canvas.drawPath(p, paint)
            }
            Icon.BACK -> {
                canvas.drawLine(4f, 12f, 20f, 12f, paint)
                p.moveTo(10f, 6f); p.lineTo(4f, 12f); p.lineTo(10f, 18f)
                canvas.drawPath(p, paint)
            }
            Icon.DONE -> {
                p.moveTo(4.5f, 12.5f); p.lineTo(9.5f, 17.5f); p.lineTo(19.5f, 6.5f)
                canvas.drawPath(p, paint)
            }
            Icon.GLOBE -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                rect.set(8f, 3f, 16f, 21f); canvas.drawOval(rect, paint)
                canvas.drawLine(3f, 12f, 21f, 12f, paint)
                canvas.drawLine(12f, 3f, 12f, 21f, paint)
            }
            Icon.EMOJI -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(9f, 10f, 1.2f, paint)
                canvas.drawCircle(15f, 10f, 1.2f, paint)
                paint.style = Paint.Style.STROKE
                rect.set(7.5f, 9.5f, 16.5f, 17f); canvas.drawArc(rect, 20f, 140f, false, paint)
            }
            Icon.MIC -> {
                rect.set(9f, 3f, 15f, 15f); canvas.drawRoundRect(rect, 3f, 3f, paint)
                rect.set(5.5f, 6f, 18.5f, 18f); canvas.drawArc(rect, 10f, 160f, false, paint)
                canvas.drawLine(12f, 18f, 12f, 21.5f, paint)
            }
            Icon.SETTINGS -> {
                canvas.drawCircle(12f, 12f, 3f, paint)
                for (i in 0 until 8) {
                    canvas.save(); canvas.rotate(i * 45f, 12f, 12f)
                    canvas.drawLine(12f, 3f, 12f, 5.5f, paint)
                    canvas.restore()
                }
                canvas.drawCircle(12f, 12f, 6.5f, paint)
            }
            Icon.GIF -> {
                rect.set(2.5f, 6f, 21.5f, 18f); canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = 7.5f
                paint.textAlign = Paint.Align.CENTER
                paint.isFakeBoldText = true
                canvas.drawText("GIF", 12f, 14.6f, paint)
                paint.isFakeBoldText = false
            }
            Icon.STICKER -> {
                p.moveTo(4f, 4f); p.lineTo(20f, 4f); p.lineTo(20f, 13f); p.lineTo(13f, 20f); p.lineTo(4f, 20f); p.close()
                canvas.drawPath(p, paint)
                p.reset(); p.moveTo(20f, 13f); p.lineTo(13f, 13f); p.lineTo(13f, 20f)
                canvas.drawPath(p, paint)
            }
            Icon.CLIPBOARD -> {
                rect.set(5f, 4.5f, 19f, 21f); canvas.drawRoundRect(rect, 2f, 2f, paint)
                rect.set(9f, 2.5f, 15f, 6.5f); canvas.drawRoundRect(rect, 1f, 1f, paint)
                canvas.drawLine(8.5f, 11f, 15.5f, 11f, paint)
                canvas.drawLine(8.5f, 15f, 13.5f, 15f, paint)
            }
            Icon.ONE_HANDED -> {
                rect.set(7f, 2.5f, 17f, 21.5f); canvas.drawRoundRect(rect, 2f, 2f, paint)
                rect.set(9.5f, 13f, 14.5f, 18f); canvas.drawRect(rect, paint)
            }
            Icon.FLOATING -> {
                rect.set(3f, 5f, 21f, 19f); canvas.drawRoundRect(rect, 2f, 2f, paint)
                rect.set(9f, 10f, 18f, 16f); paint.style = Paint.Style.FILL; canvas.drawRoundRect(rect, 1f, 1f, paint)
            }
            Icon.BOOK -> {
                p.moveTo(12f, 6f); p.cubicTo(9f, 4f, 5f, 4f, 3f, 5f); p.lineTo(3f, 19f); p.cubicTo(5f, 18f, 9f, 18f, 12f, 20f)
                p.cubicTo(15f, 18f, 19f, 18f, 21f, 19f); p.lineTo(21f, 5f); p.cubicTo(19f, 4f, 15f, 4f, 12f, 6f); p.lineTo(12f, 20f)
                canvas.drawPath(p, paint)
            }
            Icon.THESAURUS -> {
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 11f
                paint.isFakeBoldText = true
                canvas.drawText("Aa", 10f, 13f, paint)
                paint.isFakeBoldText = false
                paint.style = Paint.Style.STROKE
                p.moveTo(6f, 17f); p.lineTo(20f, 17f); p.moveTo(17f, 14.5f); p.lineTo(20f, 17f); p.lineTo(17f, 19.5f)
                canvas.drawPath(p, paint)
            }
            Icon.KEYBOARD -> {
                rect.set(2.5f, 6f, 21.5f, 18f); canvas.drawRoundRect(rect, 2f, 2f, paint)
                paint.style = Paint.Style.FILL
                for (r in 0..1) for (c in 0..4) canvas.drawCircle(6f + c * 3f, 9.5f + r * 3f, 0.9f, paint)
                paint.style = Paint.Style.STROKE
                canvas.drawLine(8f, 15.5f, 16f, 15.5f, paint)
            }
            Icon.CHEVRON_LEFT -> { p.moveTo(15f, 5f); p.lineTo(8f, 12f); p.lineTo(15f, 19f); canvas.drawPath(p, paint) }
            Icon.CHEVRON_RIGHT -> { p.moveTo(9f, 5f); p.lineTo(16f, 12f); p.lineTo(9f, 19f); canvas.drawPath(p, paint) }
            Icon.EXPAND -> {
                p.moveTo(4f, 9f); p.lineTo(4f, 4f); p.lineTo(9f, 4f)
                p.moveTo(15f, 4f); p.lineTo(20f, 4f); p.lineTo(20f, 9f)
                p.moveTo(20f, 15f); p.lineTo(20f, 20f); p.lineTo(15f, 20f)
                p.moveTo(9f, 20f); p.lineTo(4f, 20f); p.lineTo(4f, 15f)
                canvas.drawPath(p, paint)
            }
            Icon.CLOSE -> { canvas.drawLine(6f, 6f, 18f, 18f, paint); canvas.drawLine(18f, 6f, 6f, 18f, paint) }
            Icon.GRID -> {
                for (r in 0..1) for (c in 0..1) { rect.set(4f + c * 9f, 4f + r * 9f, 11f + c * 9f, 11f + r * 9f); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint) }
            }
            Icon.TARGET -> {
                canvas.drawCircle(12f, 12f, 8f, paint)
                canvas.drawCircle(12f, 12f, 3.5f, paint)
                canvas.drawLine(12f, 1.5f, 12f, 5f, paint); canvas.drawLine(12f, 19f, 12f, 22.5f, paint)
                canvas.drawLine(1.5f, 12f, 5f, 12f, paint); canvas.drawLine(19f, 12f, 22.5f, 12f, paint)
            }
            Icon.PALETTE -> {
                p.moveTo(12f, 3f); p.cubicTo(6f, 3f, 3f, 7.5f, 3f, 12f); p.cubicTo(3f, 17f, 7f, 21f, 12f, 21f)
                p.cubicTo(13.5f, 21f, 14f, 20f, 13.5f, 18.5f); p.cubicTo(13f, 17f, 14f, 15.5f, 15.5f, 15.5f)
                p.lineTo(17.5f, 15.5f); p.cubicTo(19.5f, 15.5f, 21f, 14f, 21f, 12f); p.cubicTo(21f, 7f, 17f, 3f, 12f, 3f); p.close()
                canvas.drawPath(p, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(7.5f, 11.5f, 1.3f, paint); canvas.drawCircle(10f, 7.5f, 1.3f, paint); canvas.drawCircle(14.5f, 7.5f, 1.3f, paint)
            }
            Icon.PIN -> {
                p.moveTo(9f, 3f); p.lineTo(15f, 3f); p.lineTo(14f, 9f); p.lineTo(17f, 13f); p.lineTo(7f, 13f); p.lineTo(10f, 9f); p.close()
                canvas.drawPath(p, paint)
                canvas.drawLine(12f, 13f, 12f, 21f, paint)
            }
            Icon.TRASH -> {
                canvas.drawLine(4f, 6f, 20f, 6f, paint)
                rect.set(9f, 3f, 15f, 6f); canvas.drawRect(rect, paint)
                p.moveTo(6f, 6f); p.lineTo(7f, 21f); p.lineTo(17f, 21f); p.lineTo(18f, 6f)
                canvas.drawPath(p, paint)
            }
        }
        paint.style = Paint.Style.FILL
        canvas.restore()
    }
}

/** A Drawable wrapper for an [Icon], for use in ordinary views. */
class IconDrawable(private val icon: Icon, private var color: Int, private val sizeFraction: Float = 0.6f) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setColor(c: Int) { color = c; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val size = minOf(b.width(), b.height()) * sizeFraction
        Icons.draw(canvas, icon, b.exactCenterX(), b.exactCenterY(), size, color, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A square tappable icon. */
class IconButton(context: Context, icon: Icon, color: Int, sizeFraction: Float = 0.5f) : View(context) {
    val drawable = IconDrawable(icon, color, sizeFraction)

    init {
        background = drawable
        isClickable = true
        isFocusable = false
    }
}
