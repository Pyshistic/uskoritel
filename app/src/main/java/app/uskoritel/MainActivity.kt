package app.uskoritel

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

class MainActivity : ComponentActivity() {

    /** Увеличивается при каждом возврате в приложение — экраны перечитывают разрешения и данные. */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            AppTheme { AppRoot(resumeTick.intValue) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Ускорение", Icons.Rounded.RocketLaunch),
    CLEAN("Очистка", Icons.Rounded.CleaningServices),
    BATTERY("Батарея", Icons.Rounded.BatteryChargingFull),
    APPS("Приложения", Icons.Rounded.Apps),
}

@Composable
fun AppRoot(tick: Int) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (tab) {
                Tab.HOME -> HomeScreen(tick)
                Tab.CLEAN -> CleanScreen(tick)
                Tab.BATTERY -> BatteryScreen(tick)
                Tab.APPS -> AppsScreen(tick)
            }
        }
    }
}
