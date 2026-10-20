package cl.habita.mobile

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/** PDF nativo con paginación y ajuste de líneas; no depende de WebView ni de un servidor. */
object ReportPdf {
    fun create(text: String): ByteArray {
        val document = PdfDocument()
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 43, 68); textSize = 11f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val title = Paint(ink).apply { textSize = 20f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        var number = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
        var y = 84f
        fun header() {
            page.canvas.drawText("HABITA / INFORME DEL ENTORNO", 40f, 43f, title)
            page.canvas.drawLine(40f, 57f, 555f, 57f, ink)
            page.canvas.drawText("Prototipo · Página $number", 40f, 810f, ink)
        }
        fun line(value: String) {
            if (y > 775f) { document.finishPage(page); number++; page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create()); y = 84f; header() }
            page.canvas.drawText(value, 40f, y, ink); y += 17f
        }
        try {
            header()
            text.lines().forEach { paragraph ->
                var pending = paragraph
                if (pending.isEmpty()) line("")
                while (pending.isNotEmpty()) {
                    val count = ink.breakText(pending, true, 510f, null).coerceAtLeast(1)
                    val breakAt = if (count < pending.length) pending.lastIndexOf(' ', count).takeIf { it > 0 } ?: count else count
                    line(pending.substring(0, breakAt)); pending = pending.substring(breakAt).trimStart()
                }
            }
            document.finishPage(page)
            return ByteArrayOutputStream().use { document.writeTo(it); it.toByteArray() }
        } finally { document.close() }
    }
}
