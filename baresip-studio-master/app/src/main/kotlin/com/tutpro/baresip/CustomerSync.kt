package com.tutpro.baresip

/** Reserved contract for an institution-approved backend. There is deliberately
 * no HTTP implementation, endpoint, background upload or central server claim. */
interface CustomerSync {
    suspend fun synchronise(cursor: String?): SyncReceipt
    data class SyncReceipt(val cursor: String?, val downloaded: Int, val uploaded: Int)
}

class OfflineCustomerSync : CustomerSync {
    override suspend fun synchronise(cursor: String?): CustomerSync.SyncReceipt =
        throw UnsupportedOperationException("Central synchronisation has not been configured by the institution")
}
