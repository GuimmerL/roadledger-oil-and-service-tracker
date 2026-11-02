package ca.gllavoie.vehicletracker.data.repository

import java.io.BufferedReader
import java.io.PushbackReader
import java.io.Reader

/** Minimal RFC 4180 reader for the bundled catalog, including escaped quotes and quoted newlines. */
internal object CsvRecordReader {
    fun read(reader: Reader, onRecord: (List<String>) -> Unit): Int {
        val input = PushbackReader(BufferedReader(reader), 1)
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var afterQuote = false
        var sawContent = false
        var recordCount = 0

        fun emitRecord() {
            fields += field.toString()
            if (recordCount == 0 && fields.isNotEmpty()) {
                fields[0] = fields[0].removePrefix("\uFEFF")
            }
            if (fields.any(String::isNotEmpty)) {
                onRecord(fields.toList())
                recordCount++
            }
            fields = mutableListOf()
            field.setLength(0)
            afterQuote = false
            sawContent = false
        }

        while (true) {
            val code = input.read()
            if (code < 0) break
            val char = code.toChar()
            sawContent = true
            if (inQuotes) {
                if (char == '"') {
                    val next = input.read()
                    if (next == '"'.code) field.append('"')
                    else {
                        inQuotes = false
                        afterQuote = true
                        if (next >= 0) input.unread(next)
                    }
                } else {
                    field.append(char)
                }
                continue
            }

            when (char) {
                '"' -> {
                    require(field.isEmpty() && !afterQuote) { "Unexpected quote in catalog CSV field." }
                    inQuotes = true
                }
                ',' -> {
                    fields += field.toString()
                    field.setLength(0)
                    afterQuote = false
                }
                '\r' -> {
                    val next = input.read()
                    if (next >= 0 && next != '\n'.code) input.unread(next)
                    emitRecord()
                }
                '\n' -> emitRecord()
                else -> {
                    if (afterQuote && char != ' ' && char != '\t') {
                        throw IllegalArgumentException("Unexpected text after a quoted catalog CSV field.")
                    }
                    if (!afterQuote) field.append(char)
                }
            }
        }
        require(!inQuotes) { "Catalog CSV ends inside a quoted field." }
        if (sawContent || field.isNotEmpty() || fields.isNotEmpty() || afterQuote) emitRecord()
        return recordCount
    }
}
