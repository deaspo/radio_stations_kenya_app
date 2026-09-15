package com.example.kenyanradiostations

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

/**
 * Generated station artwork.
 *
 * The catalogue does have real logos, and they are still what gets shown. This
 * is what stands in while one loads and what replaces one that 404s - in place
 * of the single generic radio glyph every station used to fall back to, which
 * made a slow or broken row look like a list of duplicates.
 *
 * Deterministic on purpose: the same station gets the same art on every device
 * and every launch, so the placeholder does not flicker to a different colour
 * on each bind, and two people looking at the same screen see the same thing.
 * Theme-independent for the same reason - the gradients are not overridden in
 * values-night.
 */
class StationArtDrawable(
    @ColorInt private val startColor: Int,
    @ColorInt private val endColor: Int,
    private val initials: String
) : Drawable() {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INITIALS_COLOR
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        backgroundPaint.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(),
            b.right.toFloat(), b.bottom.toFloat(),
            startColor, endColor, Shader.TileMode.CLAMP
        )
        canvas.drawRect(b, backgroundPaint)

        textPaint.textSize = b.height() * INITIALS_HEIGHT_FRACTION
        val metrics = textPaint.fontMetrics
        val baseline = b.exactCenterY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(initials, b.exactCenterX(), baseline, textPaint)
    }

    // Decorative and fully opaque; there is nothing useful to vary.
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    private companion object {
        /** White at 92%. */
        const val INITIALS_COLOR = 0xEBFFFFFF.toInt()
        const val INITIALS_HEIGHT_FRACTION = 0.34f
    }
}

object StationArt {

    /** Gradient pairs, in the order they appear in colors.xml. */
    private val PALETTES = arrayOf(
        R.color.art_01_a to R.color.art_01_b,
        R.color.art_02_a to R.color.art_02_b,
        R.color.art_03_a to R.color.art_03_b,
        R.color.art_04_a to R.color.art_04_b,
        R.color.art_05_a to R.color.art_05_b,
        R.color.art_06_a to R.color.art_06_b
    )

    val paletteCount: Int get() = PALETTES.size

    fun forStation(context: Context, id: String, name: String): StationArtDrawable {
        val (startRes, endRes) = PALETTES[paletteIndexOf(id, PALETTES.size)]
        return StationArtDrawable(
            ContextCompat.getColor(context, startRes),
            ContextCompat.getColor(context, endRes),
            initialsOf(name)
        )
    }

    /**
     * Stable index for [id].
     *
     * String.hashCode is specified by the language, not left to the runtime, so
     * this is the same number on every device and every release - which is the
     * whole point.
     *
     * Math.floorMod, not abs(): abs(Int.MIN_VALUE) is still Int.MIN_VALUE, so
     * the obvious `abs(hash) % size` returns a negative index for one hash in
     * four billion and crashes on an array lookup. Rare is not never when the
     * ids come from a scraped page.
     */
    fun paletteIndexOf(id: String, size: Int = PALETTES.size): Int =
        Math.floorMod(id.hashCode(), size)

    /**
     * First two letters of the station's first word: "Citizen FM" reads "Ci",
     * "KBC" reads "Kb".
     *
     * Word initials were the other option and are wrong here - most names in
     * this catalogue are "<Name> FM <frequency>", so that yields a grid of
     * tiles all ending in F.
     */
    fun initialsOf(name: String): String {
        val firstWord = name.trim()
            .split(' ', '\t', '\n')
            .firstOrNull { it.any(Char::isLetterOrDigit) }
            ?.filter(Char::isLetterOrDigit)
            .orEmpty()

        return when {
            firstWord.isEmpty() -> "RD"
            firstWord.length == 1 -> firstWord.uppercase()
            else -> firstWord[0].uppercaseChar().toString() + firstWord[1].lowercaseChar()
        }
    }
}
