package com.tutpro.baresip

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

data class SchemeEntry(val code: String,val name: String)
data class SchemeResolution(val code: String = "",val name: String = "",val generated: Boolean = false,val error: String? = null)
object SchemeNames {
    fun key(name: String): String = Normalizer.normalize(name,Normalizer.Form.NFC)
        .map { if(it.isWhitespace() || Character.isSpaceChar(it)) ' ' else it }.joinToString("")
        .trim().replace(Regex(" +")," ").lowercase(Locale.ROOT)
    fun generatedCode(name: String): String = "AUTO-"+MessageDigest.getInstance("SHA-256").digest(("SWUSIP-SCHEME-V1:"+key(name)).toByteArray())
        .take(10).joinToString("") { "%02x".format(it) }
    fun resolve(code: String,name: String,catalog: List<SchemeEntry>): SchemeResolution = SchemeIndex(catalog).resolve(code,name)
}

/** Index once per batch; never scan/renormalise the whole catalog per row. */
class SchemeIndex(entries: List<SchemeEntry>) {
    private val codes = linkedMapOf<String,LinkedHashMap<String,SchemeEntry>>()
    private val names = linkedMapOf<String,LinkedHashMap<String,SchemeEntry>>()
    init { entries.forEach(::add) }
    fun add(entry: SchemeEntry) {
        val code = entry.code.trim().lowercase(Locale.ROOT); val name = SchemeNames.key(entry.name)
        codes.getOrPut(code) { linkedMapOf() }.putIfAbsent(name,entry)
        names.getOrPut(name) { linkedMapOf() }.putIfAbsent(code,entry)
    }
    fun resolve(code: String,name: String): SchemeResolution {
        val nameKey = SchemeNames.key(name)
        if(nameKey.isEmpty()) return SchemeResolution(error="Scheme name is required")
        val codeMatches = codes[code.trim().lowercase(Locale.ROOT)].orEmpty().values
        if(code.isNotBlank()) {
            if(codeMatches.any { SchemeNames.key(it.name) != nameKey }) return SchemeResolution(error="Scheme code identifies a different scheme name")
            return SchemeResolution(codeMatches.firstOrNull()?.code ?: code.trim(),codeMatches.firstOrNull()?.name ?: name.trim())
        }
        val matches = names[nameKey].orEmpty().values
        if(matches.size > 1) return SchemeResolution(error="Ambiguous scheme name: more than one existing scheme uses this name. Administrator review or an explicit scheme code is required")
        matches.singleOrNull()?.let { return SchemeResolution(it.code,it.name) }
        val generated = SchemeNames.generatedCode(name)
        if(codes[generated.lowercase(Locale.ROOT)].orEmpty().keys.any { it != nameKey }) return SchemeResolution(error="Internal scheme identifier collision; administrator review required")
        return SchemeResolution(generated,name.trim(),true)
    }
}
