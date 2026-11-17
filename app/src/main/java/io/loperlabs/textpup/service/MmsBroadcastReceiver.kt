package io.loperlabs.textpup.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MmsBroadcastReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION) return

        val contentType = intent.type ?: ""
        if (!contentType.equals("application/vnd.wap.mms-message", ignoreCase = true)) return

        // Extract subId
        val subId = intent.getIntExtra("subscription", -1).takeIf { it != -1 }
            ?: intent.getIntExtra("sub_id", -1).takeIf { it != -1 }
            ?: intent.getIntExtra(android.telephony.SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)

        val service = RadioGatewayService.instance
        val simNumber = service?.simManager?.getActiveSimCards()
            ?.find { it.subscriptionId == subId }?.simNumber ?: 1

        Log.d("TextPupMMS", "WAP Push MMS trigger on SIM $simNumber. Extracting downloaded media parts...")

        // Android writes MMS to Telephony database asynchronously; wait 2.5s for PDU assembly
        scope.launch {
            delay(2500)
            extractAndForwardLatestMms(context, simNumber)
        }
    }

    private fun extractAndForwardLatestMms(context: Context, simNumber: Int) {
        val service = RadioGatewayService.instance ?: return
        val uri = Uri.parse("content://mms/inbox")
        val projection = arrayOf("_id", "date", "sub", "m_type")
        val sortOrder = "date DESC LIMIT 1"

        try {
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, sortOrder)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val mmsId = c.getString(c.getColumnIndexOrThrow("_id"))
                    val dateSec = c.getLong(c.getColumnIndexOrThrow("date"))
                    val subject = c.getString(c.getColumnIndexOrThrow("sub"))
                    val timestamp = if (dateSec < 100000000000L) dateSec * 1000 else dateSec

                    val sender = resolveMmsSender(context, mmsId)
                    val parts = service.mmsExtractor.getPartsForMms(mmsId)

                    Log.d("TextPupMMS", "Extracted MMS $mmsId with ${parts.size} media parts from $sender")
                    RadioGatewayService.logActivity("Received MMS from $sender (${parts.size} parts, SIM $simNumber)", isIncoming = true)

                    service.wsClient.forwardInboundMms(
                        sender = sender,
                        subject = subject,
                        simNumber = simNumber,
                        timestamp = timestamp,
                        parts = parts
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("TextPupMMS", "Error reading downloaded MMS", e)
        }
    }

    private fun resolveMmsSender(context: Context, mmsId: String): String {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        val projection = arrayOf("address", "type")
        val selection = "type = 137" // 137 in MMS PDU headers is PduHeaders.FROM

        try {
            val cursor = context.contentResolver.query(uri, projection, selection, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    return c.getString(c.getColumnIndexOrThrow("address")) ?: "Unknown"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unknown"
    }
}
