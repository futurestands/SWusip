package com.tutpro.baresip

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream
import java.util.zip.ZipInputStream

data class CustomerImportRow(val row: Int, val values: List<String>, val error: String? = null, val action: String = "New record")
object CustomerImport {
    val headers = listOf("customer_name", "phone_number", "account_number", "scheme_code", "scheme_name", "connection_type", "notes")
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_ROWS = 10000
    fun parse(input: InputStream, xlsx: Boolean): List<CustomerImportRow> = parseWithParser(input,xlsx) { Xml.newPullParser() }
    internal fun parseWithParser(input: InputStream, xlsx: Boolean, newParser: () -> XmlPullParser): List<CustomerImportRow> {
        val bytes = input.use { readBounded(it, MAX_BYTES + 1) }
        require(bytes.size <= MAX_BYTES) { "Import exceeds 5 MB" }
        val rows = if (xlsx) spreadsheet(bytes,newParser) else csv(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"))
        require(rows.size <= MAX_ROWS + 1) { "Import exceeds 10,000 rows" }
        require(rows.firstOrNull()?.map { it.trim() } == headers) { "Use the SWUSIP template column names and order" }
        return rows.drop(1).mapIndexedNotNull { i, row ->
            if(row.all { it.isBlank() }) return@mapIndexedNotNull null
            val values = row.map { it.trim() }
            val error = validate(values)
            CustomerImportRow(i + 2, values, error)
        }
    }
    internal fun validate(values: List<String>): String? = when {
                values.size != headers.size -> "Expected seven columns"
                values.take(5).any { it.isEmpty() } -> "Name, phone, account and scheme code/name are required"
                values.any { it.length > 2000 } -> "Cell exceeds 2,000 characters"
                CustomerNumbers.normalise(values[1]) == null -> "Invalid telephone; use Uganda or international format"
                values[2].startsWith("#NUMERIC#") -> "Format account numbers as Text in Excel, then re-enter the original account number"
                values.any { it.startsWith("#FORMULA#") } -> "Formula cells are not supported; use literal text"
                else -> null
            }
    internal fun csv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>()
        val cell = StringBuilder(); var quoted = false; var closed = false; var i = 0
        fun field() { row.add(cell.toString()); cell.setLength(0); closed = false }
        while(i < text.length) {
            val ch = text[i++]
            if (quoted) {
                if (ch == '"') {
                    if (i < text.length && text[i] == '"') { cell.append('"'); i++ }
                    else { quoted = false; closed = true }
                } else cell.append(ch)
            } else when(ch) {
                '"' -> { require(cell.isEmpty() && !closed) { "Invalid CSV quote" }; quoted = true }
                ',' -> field()
                '\r', '\n' -> {
                    if(ch == '\r' && i < text.length && text[i] == '\n') i++
                    field(); rows.add(row); row = mutableListOf()
                    require(rows.size <= MAX_ROWS + 1) { "Too many rows" }
                }
                else -> { require(!closed) { "Unexpected characters after CSV quote" }; cell.append(ch) }
            }
        }
        require(!quoted) { "Unclosed CSV quote" }
        if(cell.isNotEmpty() || row.isNotEmpty() || closed) { field(); rows.add(row) }
        return rows
    }
    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while(out.size() < limit) {
            val n = input.read(buffer,0,minOf(buffer.size,limit-out.size()))
            if(n < 0) break
            out.write(buffer,0,n)
        }
        return out.toByteArray()
    }
    private fun spreadsheet(bytes: ByteArray, newParser: () -> XmlPullParser): List<List<String>> {
        val entries = mutableMapOf<String, ByteArray>(); var total = 0
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while(entry != null) {
                val data = readBounded(zip, 20 * 1024 * 1024 - total + 1); total += data.size
                require(total <= 20 * 1024 * 1024) { "Expanded XLSX exceeds 20 MB" }
                if(entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml") entries[entry.name] = data
                entry = zip.nextEntry
            }
        }
        fun parser(data: ByteArray): XmlPullParser {
            if(String(data,Charsets.UTF_8).contains("<!DOCTYPE",ignoreCase = true))
                throw XmlPullParserException("DTD declarations are not supported")
            return newParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                // Android Xml.newPullParser enables document declarations,
                // while some JVM parsers leave the feature disabled and reject
                // attempts to set it. Disable it where supported, then verify
                // the effective state on both implementations.
                try { setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false) }
                catch (_: XmlPullParserException) { /* Effective state checked below. */ }
                if(getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL))
                    throw XmlPullParserException("DTD processing must remain disabled")
                setInput(data.inputStream(), "UTF-8")
            }
        }
        val strings = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { data ->
            val xml = parser(data); var value = StringBuilder()
            while(xml.eventType != XmlPullParser.END_DOCUMENT) {
                require(xml.eventType != XmlPullParser.DOCDECL) { "DTD is not supported" }
                if(xml.eventType == XmlPullParser.START_TAG && xml.name == "si") value = StringBuilder()
                if(xml.eventType == XmlPullParser.START_TAG && xml.name == "t") value.append(xml.nextText())
                if(xml.eventType == XmlPullParser.END_TAG && xml.name == "si") strings.add(value.toString())
                xml.next()
            }
        }
        val xml = parser(entries["xl/worksheets/sheet1.xml"] ?: error("XLSX first worksheet missing"))
        val rows = mutableListOf<List<String>>(); var row = MutableList(7) { "" }
        var column = 0; var type = ""; var value = ""; var formula = false; var sourceRow = 1
        while(xml.eventType != XmlPullParser.END_DOCUMENT) {
            require(xml.eventType != XmlPullParser.DOCDECL) { "DTD is not supported" }
            if(xml.eventType == XmlPullParser.START_TAG) when(xml.name) {
                "row" -> {
                    sourceRow = xml.getAttributeValue(null,"r")?.toIntOrNull() ?: (rows.size+1)
                    require(sourceRow in (rows.size+1)..(MAX_ROWS+1)) { "Invalid or excessive worksheet row number" }
                    row = MutableList(7) { "" }
                }
                "c" -> {
                    val address = xml.getAttributeValue(null,"r") ?: error("Cell address missing")
                    val letters = address.takeWhile { it.isLetter() }
                    column = letters.fold(0) { acc,c -> acc * 26 + c.code - 'A'.code + 1 } - 1
                    require(column in 0..6) { "Use only the seven template columns" }
                    type = xml.getAttributeValue(null,"t") ?: "n"; value = ""; formula = false
                }
                "f" -> formula = true
                "v", "t" -> value += xml.nextText()
            }
            if(xml.eventType == XmlPullParser.END_TAG) when(xml.name) {
                "c" -> row[column] = when {
                    formula -> "#FORMULA#"
                    type == "s" -> strings.getOrNull(value.toIntOrNull() ?: -1) ?: error("Invalid shared string")
                    type == "n" && column == 2 && rows.isNotEmpty() && value.isNotEmpty() -> "#NUMERIC#$value"
                    else -> value
                }
                "row" -> {
                    while(rows.size < sourceRow-1) rows.add(List(7) { "" })
                    rows.add(row); require(rows.size <= MAX_ROWS + 1) { "Too many rows" }
                }
            }
            xml.next()
        }
        return rows
    }
}
