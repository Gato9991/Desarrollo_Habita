package cl.habita.core

import java.io.BufferedReader
import java.io.Reader

/** CSV con comillas, comas escapadas, BOM y campos multilínea; sin cargar el archivo en memoria. */
internal object Csv {
    fun rows(source: Reader, consume: (List<String>) -> Unit) {
        val reader = if (source is BufferedReader) source else source.buffered(65536)
        var pending = StringBuilder()
        var quoted = false
        while (true) {
            val line = reader.readLine() ?: break
            if (pending.isNotEmpty()) pending.append('\n')
            pending.append(line)
            var index = 0
            while (index < line.length) {
                if (line[index] == '"') {
                    if (quoted && index + 1 < line.length && line[index + 1] == '"') index++
                    else quoted = !quoted
                }
                index++
            }
            require(pending.length <= 2_000_000) { "Fila CSV demasiado grande." }
            if (!quoted) {
                if (pending.isNotEmpty()) consume(parse(pending.toString()))
                pending = StringBuilder()
            }
        }
        require(!quoted) { "CSV incompleto: comillas sin cerrar." }
    }

    internal fun parse(line: String): List<String> {
        val fields = ArrayList<String>(12)
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                    field.append('"'); i++
                } else quoted = !quoted
                c == ',' && !quoted -> { fields.add(field.toString()); field.setLength(0) }
                else -> field.append(c)
            }
            i++
        }
        fields.add(field.toString())
        return fields
    }
}
