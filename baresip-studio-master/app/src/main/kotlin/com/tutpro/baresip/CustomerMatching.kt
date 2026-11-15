package com.tutpro.baresip

object CustomerMatching {
    fun search(records: List<CustomerRecord>, query: String, scheme: String = ""): List<CustomerRecord> {
        val text = query.trim()
        val phone = CustomerNumbers.normalise(text)
        val digits = text.takeIf { it.matches(Regex("[+0-9 ()\\-.]+")) }
            ?.filter { it.isDigit() || it == '+' }.orEmpty()
        val phoneFragment = when {
            digits.matches(Regex("0[37][0-9]{1,8}")) -> "+256" + digits.drop(1)
            digits.startsWith("00") -> "+" + digits.drop(2)
            else -> digits
        }
        return records.filter { record ->
            (listOf(record.name,record.phone,record.account,record.schemeCode,record.schemeName)
                .any { it.contains(text,true) } || (phone != null && record.phone == phone) ||
                (phoneFragment.length >= 3 && record.phone.contains(phoneFragment))) &&
                (record.schemeCode.contains(scheme.trim(),true) || record.schemeName.contains(scheme.trim(),true))
        }
    }
    fun lookup(records: List<CustomerRecord>, number: String,policy: PhoneNormalisationPolicy = PhoneNormalisationPolicy()): List<CustomerRecord> {
        val normal = PhoneNormaliser.normalise(number,policy).takeIf { it.accepted }?.canonical ?: return emptyList()
        return records.filter { it.phone == normal }.distinctBy { Triple(it.id,it.account,it.schemeCode) }
    }
    fun identify(records: List<CustomerRecord>, number: String,policy: PhoneNormalisationPolicy = PhoneNormalisationPolicy()): CallerIdentity {
        val matches = lookup(records,number,policy)
        return when(matches.size) {
            0 -> CallerIdentity.Unknown
            1 -> CallerIdentity.Single(matches.single())
            else -> CallerIdentity.Multiple(matches)
        }
    }
}

/** Multiple matches deliberately have no selected customer or default account. */
sealed interface CallerIdentity {
    data object Unknown : CallerIdentity
    data class Single(val record: CustomerRecord) : CallerIdentity
    data class Multiple(val matches: List<CustomerRecord>) : CallerIdentity
}

object CustomerImportPlanner {
    fun preview(current: List<CustomerRecord>, rows: List<CustomerImportRow>,
        catalog: List<SchemeEntry> = current.map { SchemeEntry(it.schemeCode,it.schemeName) },
        policy: PhoneNormalisationPolicy = PhoneNormalisationPolicy()): List<CustomerImportRow> {
        fun String.lower() = lowercase(java.util.Locale.ROOT)
        val schemes = SchemeIndex(catalog)
        val names = SchemeIndex(catalog + rows.filter { it.values.size == 7 && it.values[3].isNotBlank() && it.values[4].isNotBlank() && it.error == null }
            .map { SchemeEntry(it.values[3],it.values[4]) })
        val owners = current.groupBy { it.schemeCode.lower() to it.account }
            .mapValues { (_, entries) -> entries.map { it.name.lower() to it.category }.toSet() }.toMutableMap()
        val numbers = current.map { Triple(it.schemeCode.lower(),it.account,it.phone) }.toMutableSet()
        return rows.map { row ->
            val review = PhoneNormaliser.normalise(row.values.getOrElse(1) { "" },policy)
            val changedPolicy = row.phone?.let { it.policyVersion != policy.version } == true
            val validation = if(changedPolicy) "Telephone policy changed; reopen the import preview" else row.error ?: CustomerImport.validate(row.values,policy)
            if(validation != null) row.copy(error = validation, action = "Administrator review",phone=review) else {
                val v = row.values
                val proposed = if(v[3].isBlank()) names.resolve(v[3],v[4]) else schemes.resolve(v[3],v[4])
                val resolved = if(proposed.error == null) schemes.resolve(proposed.code,proposed.name).copy(generated=proposed.generated) else proposed
                val key = resolved.code.lower() to v[2]
                val owner = v[0].lower() to v[5]
                val phone = requireNotNull(review.canonical)
                val number = Triple(key.first,key.second,phone)
                val fillCategory=v[5].isNotBlank() && owners[key]?.let { it.isNotEmpty() && it.all { entry -> entry.second.isBlank() } } == true
                when {
                    resolved.error != null -> row.copy(error=resolved.error,action="Administrator review",phone=review,scheme=resolved)
                    owners[key]?.any { it.first != owner.first || (v[5].isNotBlank() && it.second.isNotBlank() && it.second != v[5]) } == true ->
                        row.copy(error = "Account ownership or category conflict",action = "Reject",phone=review,scheme=resolved)
                    number in numbers -> {
                        if(fillCategory) owners[key]=owners.getValue(key).map { it.first to v[5] }.toSet()
                        row.copy(action = if(fillCategory) "Fill missing connection category" else "Skip duplicate",phone=review,scheme=resolved)
                    }
                    else -> {
                        val action = if(fillCategory) "Add telephone and fill missing category" else if(key in owners) "Add telephone to existing account" else "New customer and account"
                        schemes.add(SchemeEntry(resolved.code,resolved.name)); names.add(SchemeEntry(resolved.code,resolved.name))
                        if(fillCategory) owners[key]=owners.getValue(key).map { it.first to v[5] }.toSet()
                        owners[key] = owners[key].orEmpty()+owner; numbers.add(number)
                        row.copy(action = action,phone=review,scheme=resolved)
                    }
                }
            }
        }
    }
}
