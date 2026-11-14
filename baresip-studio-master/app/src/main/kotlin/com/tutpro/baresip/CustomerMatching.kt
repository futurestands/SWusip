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
    fun lookup(records: List<CustomerRecord>, number: String): List<CustomerRecord> {
        val normal = CustomerNumbers.normalise(number) ?: return emptyList()
        return records.filter { it.phone == normal }.distinctBy { Triple(it.id,it.account,it.schemeCode) }
    }
    fun identify(records: List<CustomerRecord>, number: String): CallerIdentity {
        val matches = lookup(records,number)
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
    fun preview(current: List<CustomerRecord>, rows: List<CustomerImportRow>): List<CustomerImportRow> {
        val schemes = current.associate { it.schemeCode.lowercase() to it.schemeName.lowercase() }.toMutableMap()
        val owners = current.groupBy { it.schemeCode.lowercase() to it.account }
            .mapValues { (_, entries) -> entries.map { it.name.lowercase() to it.category }.toSet() }.toMutableMap()
        val numbers = current.map { Triple(it.schemeCode.lowercase(),it.account,it.phone) }.toMutableSet()
        return rows.map { row ->
            val validation = row.error ?: CustomerImport.validate(row.values)
            if(validation != null) row.copy(error = validation, action = "Reject") else {
                val v = row.values; val key = v[3].lowercase() to v[2]
                val owner = v[0].lowercase() to v[5]
                val phone = CustomerNumbers.normalise(v[1])!!
                val number = Triple(key.first,key.second,phone)
                when {
                    (schemes[key.first] != null && schemes[key.first] != v[4].lowercase()) ||
                        (owners[key]?.any { it != owner } == true) -> row.copy(error = "Account ownership, category or scheme conflict",action = "Reject")
                    number in numbers -> row.copy(action = "Skip duplicate")
                    else -> {
                        val action = if(key in owners) "Add telephone to existing account" else "New customer and account"
                        schemes[key.first] = v[4].lowercase(); owners[key] = setOf(owner); numbers.add(number)
                        row.copy(action = action)
                    }
                }
            }
        }
    }
}
