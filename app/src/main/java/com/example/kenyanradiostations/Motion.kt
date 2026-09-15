package com.example.kenyanradiostations

import android.content.Context
import android.provider.Settings

/**
 * Whether the device has asked for less animation.
 *
 * There is no public "reduce motion" flag on Android. What there is, and what
 * every accessibility guide points at, is the animator duration scale in
 * Developer options / Accessibility: set it to "off" and the platform expects
 * apps to stop animating. View animations honour it automatically; anything
 * driven by a hand-rolled loop - an infinite pulse, a spinner - does not, and
 * has to ask.
 *
 * (An earlier draft of this used android.R.bool.config_reduceMotion. That is an
 * internal framework resource, not public API, and does not compile.)
 */
object Motion {

    fun isReduced(context: Context): Boolean = animatorScale(context) == 0f

    /**
     * Scales a duration by the device's animator setting, so a transition that
     * should be instant is instant rather than merely short.
     */
    fun scaleDuration(context: Context, millis: Long): Long =
        (millis * animatorScale(context)).toLong()

    private fun animatorScale(context: Context): Float = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    }.getOrDefault(1f)
}
