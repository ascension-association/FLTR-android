package org.ascensionassociation.fltr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.ascensionassociation.fltr.ui.FltrApp
import org.ascensionassociation.fltr.ui.FltrViewModel
import org.ascensionassociation.fltr.ui.theme.FltrTheme

class MainActivity : ComponentActivity() {

    private val vm: FltrViewModel by viewModels { FltrViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh start: after rotation the link was already handled.
        if (savedInstanceState == null) handlePairingLink(intent)
        setContent {
            FltrTheme {
                FltrApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePairingLink(intent)
    }

    /** gkfltr://pair?… from the system camera or another app. */
    private fun handlePairingLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme.equals("gkfltr", ignoreCase = true)) {
            vm.pairFromLink(data.toString())
        }
    }
}
