package app.uskoritel

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** На Android 14+ система разрешает приложениям останавливать только свои процессы. */
val canKillOtherApps: Boolean get() = Build.VERSION.SDK_INT < 34

// ---------------- Ускорение ----------------

object Booster {

    data class Result(val appsProcessed: Int, val ramFreed: Long, val cacheFreed: Long)

    suspend fun boost(ctx: Context): Result = withContext(Dispatchers.Default) {
        val before = SystemInfo.ram(ctx).avail
        var processed = 0

        if (canKillOtherApps) {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val apps = ctx.packageManager.getInstalledApplications(0).filter {
                (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != ctx.packageName
            }
            for (app in apps) {
                try {
                    am.killBackgroundProcesses(app.packageName)
                    processed++
                } catch (_: Exception) {
                }
            }
        }

        val cache = clearOwnCache(ctx)
        System.gc()
        delay(1500)
        val after = SystemInfo.ram(ctx).avail
        Result(processed, (after - before).coerceAtLeast(0), cache)
    }

    fun killApp(ctx: Context, pkg: String) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            am.killBackgroundProcesses(pkg)
        } catch (_: Exception) {
        }
    }

    private fun clearOwnCache(ctx: Context): Long {
        var total = 0L
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).forEach { dir ->
            dir.listFiles()?.forEach { f ->
                total += f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                f.deleteRecursively()
            }
        }
        return total
    }
}

// ---------------- Разрешения ----------------

object Perms {

    fun hasStorage(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    @RequiresApi(Build.VERSION_CODES.R)
    fun storageIntents(ctx: Context): Array<Intent> = arrayOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    )

    @Suppress("DEPRECATION")
    fun hasUsage(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

// ---------------- Поиск мусора ----------------

enum class JunkType(val title: String, val hint: String, val autoSelect: Boolean) {
    APK("Установочные APK", "Файлы установки — само приложение после удаления останется", true),
    TEMP("Временные файлы и логи", ".tmp, .log и прочий служебный мусор", true),
    THUMBS("Кэш миниатюр", "Превью фото и видео — создадутся заново при необходимости", true),
    EMPTY("Пустые папки", "Папки, в которых ничего нет", true),
    LARGE("Большие файлы", "Больше 100 МБ — проверь сам, нужны ли они", false),
}

data class JunkItem(val path: String, val size: Long, val type: JunkType)

object JunkScanner {

    private val TEMP_EXT = setOf("tmp", "temp", "log", "dmp")
    private val TEMP_NAMES = setOf("thumbs.db", ".ds_store", "desktop.ini")
    private val STANDARD_DIRS = setOf(
        "dcim", "download", "downloads", "music", "pictures", "movies", "documents",
        "alarms", "ringtones", "notifications", "podcasts", "audiobooks", "recordings", "android",
    )
    private const val LARGE_SIZE = 100L * 1024 * 1024

    val root: File get() = Environment.getExternalStorageDirectory()

    suspend fun scan(onProgress: (Int) -> Unit): List<JunkItem> = withContext(Dispatchers.IO) {
        val base = root
        val androidDir = File(base, "Android")
        val out = ArrayList<JunkItem>()
        var count = 0

        base.walkTopDown()
            .onEnter { it != androidDir }
            .maxDepth(20)
            .forEach { f ->
                ensureActive()
                if (f.isFile) {
                    count++
                    if (count % 200 == 0) onProgress(count)
                    val name = f.name.lowercase()
                    val ext = f.extension.lowercase()
                    val size = f.length()
                    val type = when {
                        ext == "apk" -> JunkType.APK
                        ext in TEMP_EXT || name in TEMP_NAMES -> JunkType.TEMP
                        f.path.contains("/.thumbnails/", ignoreCase = true) -> JunkType.THUMBS
                        size >= LARGE_SIZE -> JunkType.LARGE
                        else -> null
                    }
                    if (type != null) out += JunkItem(f.path, size, type)
                } else if (f.isDirectory && f != base) {
                    val isStandard = f.parentFile == base && f.name.lowercase() in STANDARD_DIRS
                    if (!isStandard && f.list()?.isEmpty() == true) {
                        out += JunkItem(f.path, 0, JunkType.EMPTY)
                    }
                }
            }
        onProgress(count)
        out.sortedByDescending { it.size }
    }

    suspend fun delete(items: List<JunkItem>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        for (item in items) {
            val f = File(item.path)
            if (!f.exists()) continue
            val ok = if (f.isDirectory) f.list()?.isEmpty() == true && f.delete() else f.delete()
            if (ok) freed += item.size
        }
        freed
    }
}

// ---------------- Активность приложений ----------------

data class AppUsage(val pkg: String, val label: String, val icon: ImageBitmap?, val foregroundMs: Long)

object AppUsageRepo {

    suspend fun top(ctx: Context): List<AppUsage> = withContext(Dispatchers.IO) {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = ctx.packageManager
        val end = System.currentTimeMillis()
        val start = end - 24L * 60 * 60 * 1000

        usm.queryAndAggregateUsageStats(start, end).values
            .filter { it.totalTimeInForeground > 60_000 && it.packageName != ctx.packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedByDescending { it.totalTimeInForeground }
            .take(25)
            .mapNotNull { s ->
                try {
                    val ai = pm.getApplicationInfo(s.packageName, 0)
                    val icon = runCatching { pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    AppUsage(s.packageName, pm.getApplicationLabel(ai).toString(), icon, s.totalTimeInForeground)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }
    }
}
