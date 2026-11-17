package io.loperlabs.textpup.telephony

import android.content.Context
import android.net.Uri
import android.provider.Telephony

data class RawHistoricalMessage(
    val id: String,
    val threadId: String?,
    val address: String,
    val body: String?,
    val timestamp: Long,
    val direction: String, // "inbound" | "outbound"
    val subId: Int,
    val simSlot: Int,
    val isMms: Boolean
)

class HistoryCrawler(
    private val context: Context,
    private val simManager: SimManager
) {

    /**
     * Crawls Android telephony provider in batches, extracting full SMS history.
     */
    fun crawlSms(limit: Int = 1000, offset: Int = 0): List<RawHistoricalMessage> {
        val list = mutableListOf<RawHistoricalMessage>()
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.SUBSCRIPTION_ID
        )

        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit OFFSET $offset"

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder
            )

            val activeSims = simManager.getActiveSimCards()

            cursor?.use { c ->
                val idCol = c.getColumnIndex(Telephony.Sms._ID)
                val threadIdCol = c.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressCol = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyCol = c.getColumnIndex(Telephony.Sms.BODY)
                val dateCol = c.getColumnIndex(Telephony.Sms.DATE)
                val typeCol = c.getColumnIndex(Telephony.Sms.TYPE)
                val subIdCol = c.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                while (c.moveToNext()) {
                    val id = c.getString(idCol) ?: continue
                    val threadId = c.getString(threadIdCol)
                    val address = c.getString(addressCol) ?: ""
                    val body = c.getString(bodyCol) ?: ""
                    val date = c.getLong(dateCol)
                    val type = c.getInt(typeCol)
                    val subId = if (subIdCol != -1) c.getInt(subIdCol) else -1

                    val direction = if (type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
                        "outbound"
                    } else {
                        "inbound"
                    }

                    val sim = activeSims.find { it.subscriptionId == subId }
                    val simSlot = sim?.simNumber ?: 1

                    list.add(
                        RawHistoricalMessage(
                            id = "sms_$id",
                            threadId = threadId,
                            address = address,
                            body = body,
                            timestamp = date,
                            direction = direction,
                            subId = subId,
                            simSlot = simSlot,
                            isMms = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }
}
