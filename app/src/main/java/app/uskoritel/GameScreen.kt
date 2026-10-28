package app.uskoritel

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val BRAWL_STARS_PKG = "com.supercell.brawlstars"

data class ScreenHz(val current: Int, val max: Int)

object GameMode {

    fun screenHz(ctx: Context): ScreenHz {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return ScreenHz(60, 60)
        val max = d.supportedModes.maxOfOrNull { it.refreshRate } ?: d.refreshRate
        return ScreenHz(d.refreshRate.roundToInt(), max.roundToInt())
    }

    fun hasDndAccess(ctx: Context): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun setDnd(ctx: Context, on: Boolean) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    fun launch(ctx: Context, pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}

@Composable
fun GameScreen(tick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val power = remember { ctx.getSystemService(Context.POWER_SERVICE) as PowerManager }

    var hz by remember { mutableStateOf(GameMode.screenHz(ctx)) }
    var battery by remember { mutableStateOf(SystemInfo.battery(ctx)) }
    var ram by remember { mutableStateOf(SystemInfo.ram(ctx)) }
    var powerSave by remember { mutableStateOf(power.isPowerSaveMode) }
    var dndAccess by remember { mutableStateOf(GameMode.hasDndAccess(ctx)) }
    var useDnd by remember { mutableStateOf(true) }
    var installed by remember { mutableStateOf(GameMode.isInstalled(ctx, BRAWL_STARS_PKG)) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(tick) {
        dndAccess = GameMode.hasDndAccess(ctx)
        installed = GameMode.isInstalled(ctx, BRAWL_STARS_PKG)
        while (true) {
            hz = GameMode.screenHz(ctx)
            battery = SystemInfo.battery(ctx)
            ram = SystemInfo.ram(ctx)
            powerSave = power.isPowerSaveMode
            delay(2000)
        }
    }

    val supports120 = hz.max >= 119
    val hzAtMax = hz.current >= hz.max - 1
    val cool = battery.tempC < 40f
    val enoughCharge = battery.level > 20 || battery.charging
    val ramOk = ram.usedPercent < 85
    val readyCount = listOf(!powerSave, hzAtMax, cool, enoughCharge, ramOk).count { it }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Игровой режим", "Максимум FPS в Brawl Stars")

        SectionCard {
            Text("Экран телефона", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${hz.max}", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = if (supports120) Good else Warn)
                Text(" Гц максимум", Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (supports120) {
                    "Экран умеет 120 Гц — значит, 120 FPS в Brawl Stars возможны, если игра поддерживает твой телефон."
                } else {
                    "Экран обновляется максимум ${hz.max} раз в секунду, поэтому больше ${hz.max} FPS ты не увидишь " +
                        "ни в одной игре. Но можно добиться стабильных ${hz.max} без просадок."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Text("Готовность к игре: $readyCount из 5", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CheckRow(
                ok = !powerSave,
                text = if (powerSave) "Энергосбережение включено — оно режет FPS" else "Энергосбережение выключено",
                action = "Выключить",
            ) { ctx.openFirst(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
            CheckRow(
                ok = hzAtMax,
                text = if (hzAtMax) "Частота экрана на максимуме (${hz.current} Гц)" else "Экран сейчас ${hz.current} Гц из ${hz.max}",
                action = "Настроить",
            ) { ctx.openFirst(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
            CheckRow(
                ok = cool,
                text = if (cool) "Телефон не перегрет (${fmtTemp(battery.tempC)})" else "Перегрев ${fmtTemp(battery.tempC)} — FPS будет падать",
                action = null,
            ) {}
            CheckRow(
                ok = enoughCharge,
                text = if (enoughCharge) "Заряда достаточно (${battery.level}%)" else "Мало заряда (${battery.level}%) — система замедлит телефон",
                action = null,
            ) {}
            CheckRow(
                ok = ramOk,
                text = "Свободно ОЗУ: ${fmtBytes(ram.avail)}",
                action = null,
            ) {}
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Не беспокоить во время игры", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Уведомления не будут вылезать посреди боя",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (dndAccess) {
                    Switch(checked = useDnd, onCheckedChange = { useDnd = it })
                } else {
                    TextButton(onClick = {
                        ctx.openFirst(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }) { Text("Разрешить") }
                }
            }
            if (dndAccess) {
                TextButton(onClick = {
                    GameMode.setDnd(ctx, false)
                    ctx.toast("«Не беспокоить» выключено")
                }) { Text("Выключить «Не беспокоить» после игры") }
            }
        }

        Button(
            onClick = {
                working = true
                scope.launch {
                    Booster.boost(ctx)
                    if (dndAccess && useDnd) GameMode.setDnd(ctx, true)
                    working = false
                    if (!GameMode.launch(ctx, BRAWL_STARS_PKG)) {
                        ctx.toast("Brawl Stars не установлена")
                    }
                }
            },
            enabled = !working && installed,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Rounded.SportsEsports, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    !installed -> "Brawl Stars не найдена"
                    working -> "Готовлю телефон…"
                    else -> "Ускорить и запустить Brawl Stars"
                },
                fontWeight = FontWeight.Bold,
            )
        }

        SectionCard {
            Text("Как включить 120 FPS в самой игре", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            listOf(
                "Открой Brawl Stars → значок меню справа вверху → «Настройки».",
                "Найди переключатель частоты кадров (120 FPS) и включи его.",
                "Если переключателя нет — игра пока не поддерживает 120 FPS на твоём телефоне, и включить их нельзя.",
                "Играй без чехла и не на зарядке: перегрев — главная причина просадок.",
            ).forEachIndexed { i, s ->
                Text("${i + 1}. $s", Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun CheckRow(ok: Boolean, text: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
            contentDescription = null,
            tint = if (ok) Good else Bad,
        )
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), color = if (ok) Color.Unspecified else Bad)
        if (!ok && action != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
