package com.glasslauncher.tv.ui

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint as NativePaint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasslauncher.tv.R
import kotlin.math.max

/** Couch-TV-inspired clock: the scene is clipped to the glyphs, with a restrained sheen and rim. */
@Composable
internal fun GlassClockCanvas(
    text: String,
    backdrop: ImageBitmap?,
    scale: Float,
    fontChoice: String,
    showDate: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fontPx = with(density) { (232f * scale).sp.toPx() }
    val topPx = with(density) { (42f * scale + if (showDate) 21f * scale else 0f).dp.toPx() }
    val trackingPx = with(density) { (-10f * scale).sp.toPx() }
    val typeface = androidx.compose.runtime.remember(fontChoice) {
        if (fontChoice == "system") Typeface.create("sans-serif-light", Typeface.NORMAL)
        else runCatching { context.resources.getFont(R.font.inter_light) }.getOrElse {
            Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
    }
    val bitmap: Bitmap? = androidx.compose.runtime.remember(backdrop) {
        runCatching { backdrop?.asAndroidBitmap() }.getOrNull()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas
        val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = fontPx
            style = NativePaint.Style.FILL
            textAlign = NativePaint.Align.LEFT
            isSubpixelText = true
        }
        val glyphPath = Path()
        val advances = text.map { paint.measureText(it.toString()) + trackingPx }
        val textWidth = (advances.sum() - trackingPx).coerceAtLeast(0f)
        var x = (size.width - textWidth) / 2f
        val baseline = topPx + fontPx * 1.10f
        text.forEachIndexed { index, char ->
            val charPath = Path()
            paint.getTextPath(char.toString(), 0, 1, x, baseline, charPath)
            glyphPath.addPath(charPath)
            x += advances[index]
        }

        val save = canvas.save()
        canvas.clipPath(glyphPath)
        bitmap?.let { image ->
            val scaleToFill = max(size.width / image.width.toFloat(), size.height / image.height.toFloat())
            val drawW = image.width * scaleToFill
            val drawH = image.height * scaleToFill
            val dst = RectF((size.width - drawW) / 2f, (size.height - drawH) / 2f,
                (size.width + drawW) / 2f, (size.height + drawH) / 2f)
            paint.shader = null
            paint.alpha = 255
            canvas.drawBitmap(image, null, dst, paint)
        }
        // Frosted-glass milkiness is subtle so the wallpaper remains visible in each digit.
        paint.shader = null
        paint.style = NativePaint.Style.FILL
        paint.color = android.graphics.Color.argb(34, 235, 246, 255)
        canvas.drawRect(0f, 0f, size.width, size.height, paint)
        paint.color = android.graphics.Color.WHITE
        paint.shader = LinearGradient(0f, topPx, size.width, baseline + fontPx * 0.25f,
            intArrayOf(0x66FFFFFF, 0x08FFFFFF, 0x00FFFFFF, 0x42FFFFFF),
            floatArrayOf(0f, 0.34f, 0.68f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.width, size.height, paint)
        canvas.restoreToCount(save)

        // Fine luminous edge, not a solid gray outline.
        paint.shader = null
        paint.color = android.graphics.Color.argb(150, 248, 252, 255)
        paint.style = NativePaint.Style.STROKE
        paint.strokeWidth = with(density) { (0.8f * scale).dp.toPx() }
        paint.strokeJoin = NativePaint.Join.ROUND
        canvas.drawPath(glyphPath, paint)
        paint.style = NativePaint.Style.FILL
        paint.color = android.graphics.Color.argb(24, 255, 255, 255)
        canvas.drawPath(glyphPath, paint)
    }
}
