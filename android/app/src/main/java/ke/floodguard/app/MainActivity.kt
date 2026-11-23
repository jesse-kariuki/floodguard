package ke.floodguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ke.floodguard.app.ui.alerts.AlertsScreen
import ke.floodguard.app.ui.alerts.AlertsViewModel
import ke.floodguard.app.ui.map.MapScreen
import ke.floodguard.app.ui.map.MapViewModel
import ke.floodguard.app.ui.sms.SmsScreen
import ke.floodguard.app.ui.sms.SmsViewModel
import ke.floodguard.app.ui.theme.FgColors
import ke.floodguard.app.ui.theme.FloodGuardTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    Map("Flood map", Icons.Default.Map),
    Alerts("Alerts", Icons.Default.Campaign),
    Sms("SMS alerts", Icons.Default.Sms),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FloodGuardApp
        setContent {
            FloodGuardTheme {
                val mapVm: MapViewModel = viewModel(factory = viewModelFactory { initializer { MapViewModel(app.api, java.io.File(app.filesDir, "roads_map.geojson")) } })
                val alertsVm: AlertsViewModel = viewModel(factory = viewModelFactory { initializer { AlertsViewModel(app.api) } })
                val smsVm: SmsViewModel = viewModel(factory = viewModelFactory { initializer { SmsViewModel(app.api, app.tokens) } })
                var tab by rememberSaveable { mutableStateOf(Tab.Map) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t, onClick = { tab = t },
                                    icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
                    // Only the bottom bar's space is consumed, so screens still pad for the status bar themselves.
                    Box(Modifier.fillMaxSize().padding(bottom).consumeWindowInsets(bottom)) {
                        // The map stays composed under the other tabs, so switching back is instant and keeps the view.
                        MapScreen(mapVm, app.locator)
                        if (tab != Tab.Map) {
                            // An opaque layer that takes every touch, so nothing reaches the map underneath.
                            Box(Modifier.fillMaxSize().background(FgColors.Background).pointerInput(Unit) {}) {
                                when (tab) {
                                    Tab.Alerts -> AlertsScreen(alertsVm, onShowRoad = { name ->
                                        mapVm.showRoad(name)
                                        tab = Tab.Map
                                    })
                                    Tab.Sms -> SmsScreen(smsVm, app.locator)
                                    Tab.Map -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
