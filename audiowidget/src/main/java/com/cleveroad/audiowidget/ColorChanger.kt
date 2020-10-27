package com.cleveroad.audiowidget

import android.graphics.Color

/**
 * Helper class for changing color.
 */
internal class ColorChanger {
    private val fromColorHsv: FloatArray = FloatArray(3)
    private val toColorHsv: FloatArray = FloatArray(3)
    private val resultColorHsv: FloatArray = FloatArray(3)

    fun fromColor(fromColor: Int): ColorChanger {
        Color.colorToHSV(fromColor, fromColorHsv)
        return this
    }

    fun toColor(toColor: Int): ColorChanger {
        Color.colorToHSV(toColor, toColorHsv)
        return this
    }

    fun nextColor(dt: Float): Int {
        for (k in 0..2) {
            resultColorHsv[k] = fromColorHsv[k] + (toColorHsv[k] - fromColorHsv[k]) * dt
        }
        return Color.HSVToColor(resultColorHsv)
    }

}