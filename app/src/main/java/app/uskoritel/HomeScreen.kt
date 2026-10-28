package app.uskoritel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(tick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var ram by remember { mutableStateOf(SystemInfo.ram(ctx)) }
    var storage by remember { mutableStateOf(SystemInfo.storage()) }
    var battery by remember { mutableStateOf(SystemInfo.battery(ctx)) }
    var boosting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Booster.Result?>(null) }

    LaunchedEffect(tick) {
        while (true) {
            ram = SystemInfo.ram(ctx)
            storage = SystemInfo.storage()
            battery = SystemInfo.battery(ctx)
            delay(2000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTitle("Ускоритель", "Память, хранилище и батарея в реальном времени")
        Spacer(Modifier.height(20.dp))

        Gauge(
            percent = ram.usedPercent,
            title = "ОЗУ занято",
            subtitle = "${fmtBytes(ram.used)} из ${fmtBytes(ram.total)}",
            spinning = boosting,
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                boosting = true
                result = null
                scope.launch {
                    result = Booster.boost(ctx)
                    ram = SystemInfo.ram(ctx)
                    boosting = false
                }
            },
            enabled = !boosting,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Rounded.RocketLaunch, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(if (boosting) "Оптимизирую…" else "Ускорить", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            SectionCard {
                Text("Готово ✓", fontWeight = FontWeight.Bold, color = Good)
                Spacer(Modifier.height(6.dp))
                if (canKillOtherApps) {
                    Text("Остановлены фоновые процессы у ${r.appsProcessed} приложений.")
                }
                Text(
                    if (r.ramFreed > 10L * 1024 * 1024) "Освобождено ОЗУ: ${fmtBytes(r.ramFreed)}."
                    else "ОЗУ в порядке — Android сам держит память под контролем.",
                )
                Text("Очищен кэш: ${fmtBytes(r.cacheFreed)}.")
                if (!canKillOtherApps) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "На Android 14+ система не даёт закрывать чужие приложения. " +
                            "Тяжёлые приложения можно остановить на вкладке «Приложения», " +
                            "а место освободить на вкладке «Очистка».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionCard {
            CardHeader(Icons.Rounded.Storage, "Хранилище")
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { storage.usedPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = loadColor(storage.usedPercent),
                trackColor = MaterialTheme.colorScheme.background,
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("Свободно", "${fmtBytes(storage.free)} из ${fmtBytes(storage.total)}")
        }

        Spacer(Modifier.height(12.dp))
        SectionCard {
            CardHeader(Icons.Rounded.BatteryChargingFull, "Батарея")
            Spacer(Modifier.height(6.dp))
            InfoRow(
                "Заряд",
                "${battery.level}%" + if (battery.charging) " ⚡" else "",
                chargeColor(battery.level),
            )
            InfoRow("Температура", fmtTemp(battery.tempC), if (battery.tempC >= 40f) Bad else Good)
        }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}
