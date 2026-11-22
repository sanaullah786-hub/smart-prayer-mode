package com.sanaullah.smartprayermode.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Vector mosque skyline (dome + minarets) drawn on a Canvas, so no image assets are needed. */
@Composable
fun MosqueSilhouette(
    modifier: Modifier = Modifier,
    color: Color = Gold.copy(alpha = 0.55f),
    window: Color = GoldSoft.copy(alpha = 0.9f),
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ground = h * 0.95f

        fun DrawScope.minaret(cx: Float, width: Float, top: Float) {
            drawRect(color, Offset(cx - width / 2, top), Size(width, ground - top))
            val cone = Path().apply {
                moveTo(cx - width * 0.7f, top)
                lineTo(cx, top - width * 2.2f)
                lineTo(cx + width * 0.7f, top)
                close()
            }
            drawPath(cone, color)
            drawCircle(color, width * 0.22f, Offset(cx, top - width * 2.4f))
            drawRect(window, Offset(cx - width * 0.12f, top + width * 0.8f), Size(width * 0.24f, width * 0.9f))
        }

        fun DrawScope.dome(cx: Float, radius: Float, baseY: Float) {
            val path = Path().apply {
                moveTo(cx - radius, baseY)
                cubicTo(cx - radius, baseY - radius * 1.5f, cx - radius * 0.2f, baseY - radius * 1.6f, cx, baseY - radius * 2f)
                cubicTo(cx + radius * 0.2f, baseY - radius * 1.6f, cx + radius, baseY - radius * 1.5f, cx + radius, baseY)
                close()
            }
            drawPath(path, color)
            drawRect(color, Offset(cx - radius * 1.1f, baseY), Size(radius * 2.2f, ground - baseY))
            drawCircle(color, radius * 0.07f, Offset(cx, baseY - radius * 2.15f))
        }

        // main hall
        drawRect(color, Offset(w * 0.2f, h * 0.62f), Size(w * 0.6f, ground - h * 0.62f))
        dome(w * 0.5f, w * 0.16f, h * 0.55f)
        dome(w * 0.3f, w * 0.07f, h * 0.62f)
        dome(w * 0.7f, w * 0.07f, h * 0.62f)
        minaret(w * 0.1f, w * 0.035f, h * 0.30f)
        minaret(w * 0.9f, w * 0.035f, h * 0.30f)
        minaret(w * 0.18f, w * 0.025f, h * 0.45f)
        minaret(w * 0.82f, w * 0.025f, h * 0.45f)

        // glowing arched windows
        val windowTop = h * 0.7f
        for (i in 0 until 5) {
            val x = w * (0.28f + i * 0.11f)
            drawRect(window, Offset(x, windowTop), Size(w * 0.03f, h * 0.14f))
            drawCircle(window, w * 0.015f, Offset(x + w * 0.015f, windowTop))
        }
        drawRect(color, Offset(0f, ground), Size(w, h - ground))
    }
}
