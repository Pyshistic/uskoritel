package app.uskoritel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ---------------- Батарея ----------------

@Composable
fun BatteryScreen(tick: Int) {
    val ctx = LocalContext.current
    val power = remember { ctx.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var b by remember { mutableStateOf(SystemInfo.battery(ctx)) }
    var powerSave by remember { mutableStateOf(power.isPowerSaveMode) }

    LaunchedEffect(tick) {
        while (true) {
            b = SystemInfo.battery(ctx)
            powerSave = power.isPowerSaveMode
            delay(2000)
        }
    }

    val tips = buildList {
        if (b.tempC >= 40f) add("Телефон горячий (${fmtTemp(b.tempC)}). Сними чехол, закрой игры и не заряжай под подушкой.")
        if (!powerSave && b.level <= 30 && !b.charging) add("Заряд низкий — включи режим энергосбережения.")
        if (b.charging && b.level >= 95) add("Можно отключать зарядку: постоянные 100% быстрее изнашивают батарею.")
        if (b.health != SystemInfo.HEALTH_GOOD && b.health != SystemInfo.HEALTH_UNKNOWN) {
            add("Состояние батареи: ${b.health.lowercase()}. Возможно, стоит показать телефон мастеру.")
        }
        add("Тёмная тема и пониженная яркость экономят больше всего, особенно на AMOLED-экранах.")
        add("Выключай GPS, Bluetooth и NFC, когда они не нужны.")
        add("Проверь, каким приложениям разрешена работа в фоне, — это главный пожиратель заряда.")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTitle("Батарея", "Состояние и советы по экономии")
        Gauge(
            percent = b.level,
            title = if (b.charging) "Заряжается ⚡" else "Заряд",
            subtitle = b.source,
            spinning = false,
            color = chargeColor(b.level),
        )
        SectionCard {
            InfoRow("Состояние", b.health, if (b.health == SystemInfo.HEALTH_GOOD) Good else Warn)
            InfoRow("Температура", fmtTemp(b.tempC), if (b.tempC >= 40f) Bad else Good)
            InfoRow("Напряжение", "${b.voltageMv} мВ")
            InfoRow("Технология", b.technology)
            b.currentMa?.let { InfoRow("Ток", "$it мА") }
            InfoRow("Энергосбережение", if (powerSave) "Включено" else "Выключено", if (powerSave) Good else Color.Unspecified)
        }
        SectionCard {
            Text("Советы", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            tips.forEach { tip ->
                Text("• $tip", Modifier.padding(vertical = 3.dp))
            }
        }
        ActionButton("Режим энергосбережения", Icons.Rounded.BatterySaver) {
            ctx.openFirst(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }
        ActionButton("Кто тратит батарею", Icons.Rounded.BarChart) {
            ctx.openFirst(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }
        ActionButton("Яркость и тайм-аут экрана", Icons.Rounded.LightMode) {
            ctx.openFirst(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
        ActionButton("Работа приложений в фоне", Icons.Rounded.Tune) {
            ctx.openFirst(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

// ---------------- Приложения ----------------

@Composable
fun AppsScreen(tick: Int) {
    val ctx = LocalContext.current
    var hasUsage by remember { mutableStateOf(Perms.hasUsage(ctx)) }
    var apps by remember { mutableStateOf<List<AppUsage>?>(null) }

    LaunchedEffect(tick) {
        hasUsage = Perms.hasUsage(ctx)
        if (hasUsage) apps = AppUsageRepo.top(ctx)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Приложения", "Кто был активнее всех за последние 24 часа") }

        if (!hasUsage) {
            item {
                SectionCard {
                    Text("Чтобы показать, какие приложения сильнее всего нагружают телефон, нужен доступ к статистике использования.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { ctx.openFirst(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                        Text("Открыть настройки доступа")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Найди в списке «Ускоритель» и включи переключатель, затем вернись сюда.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val list = apps
            if (list == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    Text(
                        (if (canKillOtherApps) "⏹ — остановить фоновые процессы. " else "") +
                            "⚙ — настройки приложения: там есть «Остановить» и «Очистить кэш».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (list.isEmpty()) {
                    item { SectionCard { Text("Пока нет данных за сегодня.") } }
                }
                items(list, key = { it.pkg }) { a -> AppRow(a) }
            }
        }
    }
}

@Composable
private fun AppRow(a: AppUsage) {
    val ctx = LocalContext.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (a.icon != null) {
                Image(
                    a.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Spacer(Modifier.size(44.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Активно: ${fmtDuration(a.foregroundMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canKillOtherApps) {
                IconButton(onClick = {
                    Booster.killApp(ctx, a.pkg)
                    ctx.toast("Фоновые процессы «${a.label}» остановлены")
                }) { Icon(Icons.Rounded.StopCircle, contentDescription = "Остановить") }
            }
            IconButton(onClick = {
                ctx.openFirst(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
            }) { Icon(Icons.Rounded.Settings, contentDescription = "Настройки приложения") }
        }
    }
}
