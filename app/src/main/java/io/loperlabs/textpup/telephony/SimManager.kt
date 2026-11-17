package io.loperlabs.textpup.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

data class SimCardInfo(
    val slotIndex: Int,          // 0 or 1
    val simNumber: Int,          // 1 or 2 (User-facing)
    val subscriptionId: Int,     // Android internal subId
    val carrierName: String,     // e.g. "Helium", "Dark Star", "T-Mobile"
    val displayName: String,
    val phoneNumber: String?,
    val countryIso: String,
    val isDefault: Boolean
)

class SimManager(private val context: Context) {

    private val subscriptionManager: SubscriptionManager? =
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

    @SuppressLint("MissingPermission")
    fun getActiveSimCards(): List<SimCardInfo> {
        val list = mutableListOf<SimCardInfo>()
        if (subscriptionManager == null) return list

        try {
            val subs: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList
            if (subs.isNullOrEmpty()) return list

            val defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId()

            for (sub in subs) {
                val slot = sub.simSlotIndex
                val carrier = sub.carrierName?.toString() ?: "Unknown Carrier"
                val display = sub.displayName?.toString() ?: carrier
                val number = sub.number?.takeIf { it.isNotBlank() }

                list.add(
                    SimCardInfo(
                        slotIndex = slot,
                        simNumber = slot + 1,
                        subscriptionId = sub.subscriptionId,
                        carrierName = carrier,
                        displayName = display,
                        phoneNumber = number,
                        countryIso = sub.countryIso ?: "",
                        isDefault = sub.subscriptionId == defaultSubId
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list.sortedBy { it.slotIndex }
    }

    fun getSubIdForSimNumber(simNumber: Int): Int? {
        val sims = getActiveSimCards()
        return sims.find { it.simNumber == simNumber }?.subscriptionId
            ?: sims.firstOrNull()?.subscriptionId
    }
}
