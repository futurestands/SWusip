package com.tutpro.baresip

object CustomerMatching {
    fun search(records: List<CustomerRecord>, query: String, scheme: String = ""): List<CustomerRecord> {
        val text = query.trim()
        val phone = CustomerNumbers.normalise(text)
        return records.filter { record ->
            (listOf(record.name,record.phone,record.account,record.schemeCode,record.schemeName)
                .any { it.contains(text,true) } || (phone != null && record.phone == phone)) &&
                (record.schemeCode.contains(scheme.trim(),true) || record.schemeName.contains(scheme.trim(),true))
        }
    }
    fun lookup(records: List<CustomerRecord>, number: String): List<CustomerRecord> {
        val normal = CustomerNumbers.normalise(number) ?: return emptyList()
        return records.filter { it.phone == normal }.distinctBy { Triple(it.id,it.account,it.schemeCode) }
    }
}

object CustomerImportPlanner {
    fun preview(current: List<CustomerRecord>, rows: List<CustomerImportRow>): List<CustomerImportRow> {
        val schemes = current.associate { it.schemeCode.lowercase() to it.schemeName.lowercase() }.toMutableMap()
        val owners = current.associate { (it.schemeCode.lowercase() to it.account) to (it.name.lowercase() to it.category) }.toMutableMap()
        val numbers = current.map { Triple(it.schemeCode.lowercase(),it.account,it.phone) }.toMutableSet()
        return rows.map { row ->
            if(row.error != null) row else {
                val v = row.values; val key = v[3].lowercase() to v[2]
                val owner = v[0].lowercase() to v[5]
                val phone = CustomerNumbers.normalise(v[1])!!
                val number = Triple(key.first,key.second,phone)
                when {
                    (schemes[key.first] != null && schemes[key.first] != v[4].lowercase()) ||
                        (owners[key] != null && owners[key] != owner) -> row.copy(error = "Account ownership, category or scheme conflict",action = "Reject")
                    number in numbers -> row.copy(action = "Skip duplicate")
                    else -> {
                        val action = if(key in owners) "Add telephone to existing account" else "New customer and account"
                        schemes[key.first] = v[4].lowercase(); owners[key] = owner; numbers.add(number)
                        row.copy(action = action)
                    }
                }
            }
        }
    }
}
