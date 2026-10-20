package com.supernova.networkswitch.widget

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.supernova.networkswitch.domain.usecase.ToggleNetworkModeUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Invisible activity behind the home-screen widget tap.
 *
 * Android does not allow a normal app to change the preferred network type
 * (MODIFY_PHONE_STATE is a system/signature permission), so a direct toggle
 * only works through this app's Shizuku/root controllers. When neither is
 * available we open the hidden "Phone info" screen - the same screen the
 * popular "5G only" apps open - falling back to the system network settings.
 */
@AndroidEntryPoint
class WidgetToggleActivity : ComponentActivity() {

    @Inject
    lateinit var toggleNetworkModeUseCase: ToggleNetworkModeUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            if (!tryToggle()) {
                Toast.makeText(
                    this@WidgetToggleActivity,
                    "Direct toggle needs Shizuku/root - opening Phone info",
                    Toast.LENGTH_SHORT,
                ).show()
                openPhoneInfoOrSettings()
            }
            finish()
        }
    }

    private suspend fun tryToggle(): Boolean {
        return try {
            val subId = SubscriptionManager.getDefaultDataSubscriptionId()
            withTimeout(TOGGLE_TIMEOUT_MS) {
                toggleNetworkModeUseCase(subId)
                    .onSuccess { mode ->
                        Toast.makeText(
                            this@WidgetToggleActivity,
                            "Switched to ${mode.displayName}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
            }.isSuccess
        } catch (e: Exception) {
            false
        }
    }

    private fun openPhoneInfoOrSettings() {
        val candidates = listOf(
            // Hidden "Phone info" screen (the *#*#4636#*#* screen).
            Intent(Intent.ACTION_MAIN).setComponent(
                ComponentName("com.android.settings", "com.android.settings.RadioInfo")
            ),
            Intent(Intent.ACTION_MAIN).setComponent(
                ComponentName("com.android.phone", "com.android.phone.settings.RadioInfo")
            ),
            // Public settings fallbacks.
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next candidate.
            } catch (e: SecurityException) {
                // Activity not exported on this device; try the next candidate.
            }
        }
        Toast.makeText(
            this,
            "Could not open network settings on this device",
            Toast.LENGTH_LONG,
        ).show()
    }

    private companion object {
        const val TOGGLE_TIMEOUT_MS = 8_000L
    }
}
