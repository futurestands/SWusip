package com.tutpro.baresip

import java.net.URLDecoder

enum class PhoneValidationStatus { VALID, CORRECTED, AMBIGUOUS, INVALID, LEGACY_UNVERIFIED }
enum class TelephoneKind { MOBILE, FIXED, INTERNATIONAL, UNKNOWN }

/** Maintained configuration: UCC communication 13 July 2026, published by ITU.
 * https://www.itu.int/dms_pub/itu-t/oth/02/02/T02020000F10016PDFE.pdf
 * Allocations validate numbering structure, not ownership or reachability. */
data class UgandaNumberingPlan(
    val version: String = "UCC-2026-07-13",
    val nationalLength: Int = 9,
    val mobilePrefixes: Set<String> = setOf("70","71","7210","724","726","727","7280","730","731","7340","7370","7371",
        "74","75","76","77","78","790","791","792","793","794","795"),
    val fixedPrefixes: Set<String> = setOf("200","201","20240","20249","2031","2032","2050","2051","2052","2053","2054",
        "20611","206300","206301","206302","206303","206304","207","3","4")
)

data class PhoneNormalisationPolicy(val accessPrefixes: Set<String> = emptySet(),val plan: UgandaNumberingPlan = UgandaNumberingPlan()) {
    init { require(accessPrefixes.all { it.matches(Regex("[1-9][0-9]{0,3}")) }) { "Use explicit non-zero PBX access prefixes" } }
    val version: String get() = plan.version + "|access=" + accessPrefixes.sorted().joinToString(",")
}

data class PhoneReview(val original: String,val canonical: String?,val status: PhoneValidationStatus,
    val kind: TelephoneKind,val reason: String,val corrections: List<String>,val policyVersion: String) {
    val accepted: Boolean get() = canonical != null && status in setOf(PhoneValidationStatus.VALID,PhoneValidationStatus.CORRECTED)
    val correctionReason: String get() = corrections.joinToString("; ")
}

object PhoneNormaliser {
    fun normalise(original: String, policy: PhoneNormalisationPolicy = PhoneNormalisationPolicy()): PhoneReview {
        fun failure(status: PhoneValidationStatus,reason: String) = PhoneReview(original,null,status,TelephoneKind.UNKNOWN,reason,emptyList(),policy.version)
        if(original.isBlank()) return failure(PhoneValidationStatus.INVALID,"Telephone number is required")
        if(original.length > 2000) return failure(PhoneValidationStatus.INVALID,"Telephone number exceeds 2,000 characters")
        var value = original.trim()
        val changes = mutableListOf<String>()
        if(value != original) changes += "Removed surrounding whitespace"
        if('<' in value || '>' in value) {
            val match = Regex("^[^<>]*<([^<>]+)>\\s*$").matchEntire(value)
                ?: return failure(PhoneValidationStatus.AMBIGUOUS,"Caller identity contains ambiguous address brackets")
            value = match.groupValues[1]; changes += "Extracted the explicit caller address"
        }
        if(Regex("^(sips?|tel):",RegexOption.IGNORE_CASE).containsMatchIn(value)) {
            value = value.substringAfter(':').substringBefore('@').substringBefore(';').substringBefore('?')
            try { value = URLDecoder.decode(value.replace("+","%2B"),"UTF-8") }
            catch (_: IllegalArgumentException) { return failure(PhoneValidationStatus.INVALID,"Invalid encoded SIP caller number") }
            changes += "Extracted the SIP/telephone user number"
        }
        if(value.any { it !in '0'..'9' && it != '+' && it !in "()-." && !it.isWhitespace() && !Character.isSpaceChar(it) })
            return failure(PhoneValidationStatus.INVALID,"Telephone contains unsupported characters")
        val digits = value.filter { it in '0'..'9' || it == '+' }
        if(digits != value) changes += "Removed telephone spacing and punctuation"
        if(digits.isEmpty() || digits.count { it == '+' } > 1 || ('+' in digits && !digits.startsWith('+')))
            return failure(PhoneValidationStatus.INVALID,"Telephone has an invalid country-code marker")

        fun parse(number: String, extra: List<String>): PhoneReview {
            fun bad(status: PhoneValidationStatus,text: String) = failure(status,text)
            fun uganda(nsn: String, corrections: List<String>): PhoneReview {
                if(nsn.length != policy.plan.nationalLength) return bad(PhoneValidationStatus.INVALID,"Uganda numbers require nine national digits; check the original number")
                val kind = when {
                    policy.plan.mobilePrefixes.any { nsn.startsWith(it) } -> TelephoneKind.MOBILE
                    policy.plan.fixedPrefixes.any { nsn.startsWith(it) } -> TelephoneKind.FIXED
                    else -> return bad(PhoneValidationStatus.AMBIGUOUS,"Uganda prefix is not allocated in the configured numbering plan; administrator review required")
                }
                val canonical = "+256$nsn"
                val all = changes + corrections
                return PhoneReview(original,canonical,if(all.isEmpty() && original == canonical) PhoneValidationStatus.VALID else PhoneValidationStatus.CORRECTED,
                    kind,"Recognised Uganda ${kind.name.lowercase()} number (${policy.plan.version})",all,policy.version)
            }
            return when {
                number.startsWith("+256") -> uganda(number.drop(4),extra)
                number.startsWith('+') -> {
                    if(!number.matches(Regex("\\+[1-9][0-9]{7,14}"))) bad(PhoneValidationStatus.INVALID,"International number must have 8–15 digits after +")
                    else PhoneReview(original,number,if(changes.isEmpty() && extra.isEmpty() && original == number) PhoneValidationStatus.VALID else PhoneValidationStatus.CORRECTED,
                        TelephoneKind.INTERNATIONAL,"International format preserved; non-Uganda national allocation is not checked",changes+extra,policy.version)
                }
                number.startsWith("00") -> parseInternational(number,extra,original,changes,policy)
                number.startsWith("256") -> uganda(number.drop(3),extra + "Added the explicit + country-code marker")
                number.startsWith('0') -> uganda(number.drop(1),extra + "Replaced Uganda national trunk 0 with +256")
                number.length == 9 -> uganda(number,extra + "Added +256 to a recognised Uganda national number")
                number.length in 3..4 -> bad(PhoneValidationStatus.AMBIGUOUS,"Short/service or extension number requires administrator review; no country code was guessed")
                number.length in 8..15 -> bad(PhoneValidationStatus.AMBIGUOUS,"Unrecognised number or unconfigured access prefix; no digits were added or removed")
                else -> bad(PhoneValidationStatus.INVALID,"Telephone length is invalid")
            }
        }
        val direct = parse(digits,emptyList())
        val alternatives = if(digits.startsWith('+') || digits.startsWith("00")) emptyList() else policy.accessPrefixes
            .filter { digits.startsWith(it) && digits.length > it.length }
            .map { prefix -> parse(digits.drop(prefix.length),listOf("Removed configured PBX access prefix $prefix")) }
            .filter { it.accepted }
        val candidates = (listOf(direct)+alternatives).filter { it.accepted }.distinctBy { it.canonical }
        return when {
            candidates.size > 1 -> failure(PhoneValidationStatus.AMBIGUOUS,"Configured prefix produces more than one valid number; administrator review required")
            direct.accepted -> direct
            candidates.size == 1 -> candidates.single()
            else -> direct
        }
    }

    private fun parseInternational(number: String, extra: List<String>, original: String, changes: List<String>,policy: PhoneNormalisationPolicy): PhoneReview {
        val nested = normalise("+"+number.drop(2),policy.copy(accessPrefixes=emptySet()))
        if(!nested.accepted) return nested.copy(original=original,policyVersion=policy.version)
        return nested.copy(original=original,status=PhoneValidationStatus.CORRECTED,
            corrections=changes+extra+"Converted explicit 00 international prefix"+nested.corrections,policyVersion=policy.version)
    }
}

/** Compatibility facade used by pure search/tests; contextual callers pass policy. */
object CustomerNumbers {
    fun normalise(input: String): String? = PhoneNormaliser.normalise(input).takeIf { it.accepted }?.canonical
}

enum class OutboundNumberStyle { UGANDA_NATIONAL, UGANDA_COUNTRY_CODE, E164_PLUS, PBX_ACCESS_NATIONAL }
data class OutboundDiallingProfile(val style: OutboundNumberStyle = OutboundNumberStyle.UGANDA_NATIONAL,
    val accessPrefix: String = "",val verified: Boolean = false,val verificationReference: String = "")
sealed interface OutboundDestination {
    data class Ready(val number: String): OutboundDestination
    data class Blocked(val reason: String): OutboundDestination
}
object OutboundDiallingFormatter {
    fun format(canonical: String,profile: OutboundDiallingProfile): OutboundDestination {
        val review = PhoneNormaliser.normalise(canonical)
        if(!review.accepted || !canonical.startsWith('+') || review.canonical != canonical)
            return OutboundDestination.Blocked("Customer number is invalid or not canonical. Ask an administrator to review it.")
        if(!profile.verified || profile.verificationReference.isBlank())
            return OutboundDestination.Blocked("No verified office dialling profile is enabled. Contact your administrator; the manual dialler is available.")
        if(profile.style != OutboundNumberStyle.E164_PLUS && !canonical.startsWith("+256"))
            return OutboundDestination.Blocked("This verified profile supports Uganda numbers only.")
        val national = "0"+canonical.removePrefix("+256")
        return when(profile.style) {
            OutboundNumberStyle.UGANDA_NATIONAL -> OutboundDestination.Ready(national)
            OutboundNumberStyle.UGANDA_COUNTRY_CODE -> OutboundDestination.Ready(canonical.drop(1))
            OutboundNumberStyle.E164_PLUS -> OutboundDestination.Ready(canonical)
            OutboundNumberStyle.PBX_ACCESS_NATIONAL -> if(profile.accessPrefix.matches(Regex("[1-9]"))) OutboundDestination.Ready(profile.accessPrefix+national)
                else OutboundDestination.Blocked("Configure one approved PBX access digit; repeated or empty prefixes are not allowed.")
        }
    }
}
