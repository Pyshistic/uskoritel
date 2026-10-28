package app.uskoritel

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

data class RamInfo(val total: Long, val avail: Long) {
    val used: Long get() = total - avail
    val usedPercent: Int get() = if (total <= 0) 0 else (used * 100 / total).toInt()
}

data class StorageInfo(val total: Long, val free: Long) {
    val used: Long get() = total - free
    val usedPercent: Int get() = if (total <= 0) 0 else (used * 100 / total).toInt()
}

data class BatteryInfo(
    val level: Int,
    val charging: Boolean,
    val source: String,
    val health: String,
    val tempC: Float,
    val voltageMv: Int,
    val technology: String,
    val currentMa: Int?,
)

object SystemInfo {

    fun ram(ctx: Context): RamInfo {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return RamInfo(mi.totalMem, mi.availMem)
    }

    fun storage(): StorageInfo {
        val s = StatFs(Environment.getDataDirectory().path)
        return StorageInfo(s.totalBytes, s.availableBytes)
    }

    fun battery(ctx: Context): BatteryInfo {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0

        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val source = when (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_AC -> "От сети"
            BatteryManager.BATTERY_PLUGGED_USB -> "От USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Беспроводная зарядка"
            else -> "Не на зарядке"
        }

        val health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0) {
            BatteryManager.BATTERY_HEALTH_GOOD -> HEALTH_GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Перегрев"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Изношена"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Перенапряжение"
            BatteryManager.BATTERY_HEALTH_COLD -> "Переохлаждение"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Неисправность"
            else -> HEALTH_UNKNOWN
        }

        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltage = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tech = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—"

        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Одни телефоны отдают микроамперы, другие — миллиамперы
        val current = when {
            raw == Int.MIN_VALUE || raw == 0 -> null
            abs(raw) > 20_000 -> raw / 1000
            else -> raw
        }

        return BatteryInfo(percent, charging, source, health, temp, voltage, tech, current)
    }

    const val HEALTH_GOOD = "Хорошее"
    const val HEALTH_UNKNOWN = "Неизвестно"
}

private val RU = Locale("ru")

fun fmtBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(RU, "%.1f ГБ", bytes / gb)
        bytes >= mb -> String.format(RU, "%.0f МБ", bytes / mb)
        bytes >= kb -> String.format(RU, "%.0f КБ", bytes / kb)
        else -> "$bytes Б"
    }
}

fun fmtDuration(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    return if (hours > 0) "$hours ч ${minutes % 60} мин" else "$minutes мин"
}

fun fmtTemp(c: Float): String = String.format(RU, "%.1f °C", c)

/** Открывает первый экран настроек, который есть на этом телефоне. */
fun Context.openFirst(vararg intents: Intent) {
    for (intent in intents) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
    try {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
