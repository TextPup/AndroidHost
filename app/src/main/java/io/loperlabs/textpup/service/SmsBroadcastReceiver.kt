package io.loperlabs.textpup.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages: Array<SmsMessage> = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) return

            val firstMsg = messages[0]
            val sender = firstMsg.originatingAddress ?: "Unknown"
            val timestamp = firstMsg.timestampMillis

            // Reconstruct multi-part SMS body
            val fullBody = StringBuilder()
            for (msg in messages) {
                fullBody.append(msg.messageBody)
            }

            // Extract SIM Subscription ID from intent extras
            val subId = intent.getIntExtra("subscription", -1).takeIf { it != -1 }
                ?: intent.getIntExtra("sub_id", -1).takeIf { it != -1 }
                ?: intent.getIntExtra(android.telephony.SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)

            val service = RadioGatewayService.instance
            val simNumber = service?.simManager?.getActiveSimCards()
                ?.find { it.subscriptionId == subId }?.simNumber ?: 1

            Log.d("TextPupSMS", "Incoming SMS on SIM $simNumber from $sender")
            RadioGatewayService.logActivity("Received SMS from $sender (SIM $simNumber)", isIncoming = true)
            service?.wsClient?.forwardInboundSms(
                sender = sender,
                body = fullBody.toString(),
                simNumber = simNumber,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            Log.e("TextPupSMS", "Error processing incoming SMS broadcast", e)
        }
    }
}
