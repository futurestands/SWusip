package com.tutpro.baresip

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream
import java.util.zip.ZipInputStream

data class CustomerImportRow(val row: Int, val values: List<String>, val error: String? = null, val action: String = "New record",
    val phone: PhoneReview? = null,val scheme: SchemeResolution? = null)
object CustomerImport {
    // Stable internal field order and legacy export schema; new templates use requiredHeaders.
    val headers = listOf("customer_name", "phone_number", "account_number", "scheme_code", "scheme_name", "connection_type", "notes")
    val requiredHeaders = listOf("customer_name","phone_number","account_number","scheme_name")
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_ROWS = 10000
    fun parse(input: InputStream, xlsx: Boolean, policy: PhoneNormalisationPolicy = PhoneNormalisationPolicy()): List<CustomerImportRow> = parseWithParser(input,xlsx,policy) { Xml.newPullParser() }
    internal fun parseWithParser(input: InputStream, xlsx: Boolean, policy: PhoneNormalisationPolicy = PhoneNormalisationPolicy(),newParser: () -> XmlPullParser): List<CustomerImportRow> {
        val bytes = input.use { readBounded(it, MAX_BYTES + 1) }
        require(bytes.size <= MAX_BYTES) { "Import exceeds 5 MB" }
        val rows = if (xlsx) spreadsheet(bytes,newParser) else csv(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"))
        require(rows.size <= MAX_ROWS + 1) { "Import exceeds 10,000 rows" }
        val aliases = mapOf("name" to "customer_name","customername" to "customer_name","phone" to "phone_number","phonenumber" to "phone_number",
            "telephone" to "phone_number","contact" to "phone_number","contactnumber" to "phone_number","account" to "account_number",
            "accountno" to "account_number","accountnumber" to "account_number","scheme" to "scheme_name","schemename" to "scheme_name",
            "waterscheme" to "scheme_name","schemecode" to "scheme_code","connectiontype" to "connection_type","notes" to "notes")
        val sourceHeaders = rows.firstOrNull().orEmpty().dropLastWhile { it.isBlank() }.map { name ->
            aliases[name.trim().lowercase(java.util.Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }]
                ?: throw IllegalArgumentException("Unrecognised import column; use the four-column or legacy seven-column template")
        }
        require(sourceHeaders.distinct().size == sourceHeaders.size && sourceHeaders.containsAll(requiredHeaders)) {
            "Required columns: customer_name, phone_number, account_number, scheme_name; no duplicate columns"
        }
        return rows.drop(1).mapIndexedNotNull { i, row ->
            if(row.all { it.isBlank() }) return@mapIndexedNotNull null
            val values = MutableList(headers.size) { "" }
            sourceHeaders.forEachIndexed { index, name ->
                val raw = row.getOrElse(index) { "" }
                values[headers.indexOf(name)] = if(name == "account_number") raw.trim()
                    else if(name == "phone_number") raw.removePrefix("#NUMERIC#") else raw.removePrefix("#NUMERIC#").trim()
            }
            val phone = PhoneNormaliser.normalise(values[1],policy)
            val error = if(row.drop(sourceHeaders.size).any { it.isNotBlank() }) "Unexpected data beyond the declared columns" else validate(values,policy)
            CustomerImportRow(i + 2, values, error,phone=phone)
        }
    }
    internal fun validate(values: List<String>,policy: PhoneNormalisationPolicy = PhoneNormalisationPolicy()): String? = when {
                values.size != headers.size -> "Expected seven columns"
                listOf(0,1,2,4).any { values[it].isBlank() } -> "Customer name, phone, account and scheme name are required"
                values.any { it.length > 2000 } -> "Cell exceeds 2,000 characters"
                values[2].startsWith("#NUMERIC#") -> "Format account numbers as Text in Excel, then re-enter the original account number"
                values.any { it.startsWith("#FORMULA#") } -> "Formula cells are not supported; use literal text"
                values.any { value -> value.any { it.isISOControl() && it !in "\r\n\t" } } -> "Unsupported control character in a cell"
                !PhoneNormaliser.normalise(values[1],policy).accepted -> PhoneNormaliser.normalise(values[1],policy).reason
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
                    type == "n" && rows.isNotEmpty() && value.isNotEmpty() -> "#NUMERIC#$value"
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
