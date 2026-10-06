package ru.tsvetaeva.daily.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.tsvetaeva.daily.MainActivity
import ru.tsvetaeva.daily.R
import ru.tsvetaeva.daily.core.Author
import ru.tsvetaeva.daily.data.PoemRepository
import ru.tsvetaeva.daily.data.Prefs
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object DailyNotifications {
    private const val CHANNEL_ID = "poem_of_the_day"
    private const val WORK_DAILY = "daily_poem"
    private const val WORK_REFRESH = "weekly_refresh"
    private const val NOTIFICATION_ID = 1

    /** Запланировать ближайшее уведомление. [replace] — при смене времени в настройках. */
    fun schedule(context: Context, replace: Boolean = false) {
        val prefs = Prefs(context)
        val wm = WorkManager.getInstance(context)
        if (!prefs.notifyEnabled) {
            wm.cancelUniqueWork(WORK_DAILY)
            return
        }
        val request = OneTimeWorkRequestBuilder<DailyPoemWorker>()
            .setInitialDelay(delayToNext(prefs.notifyHour, prefs.notifyMinute).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK_DAILY, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    /** Следующее срабатывание из самого воркера: дописываем в цепочку, чтобы не отменить текущий. */
    internal fun scheduleNextFromWorker(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.notifyEnabled) return
        val request = OneTimeWorkRequestBuilder<DailyPoemWorker>()
            .setInitialDelay(delayToNext(prefs.notifyHour, prefs.notifyMinute).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_DAILY, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Раз в неделю тихо обновлять базу стихов с Викитеки. */
    fun scheduleWeeklyRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_REFRESH, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun delayToNext(hour: Int, minute: Int): Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now.plusSeconds(30))) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Стих дня", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ежедневное стихотворение основного автора"
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission") // проверяется в canPost()
    fun show(context: Context, author: Author, title: String, text: String, footer: String?) {
        if (!canPost(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val preview = text.lines().filter { it.isNotBlank() }.take(2).joinToString(" / ") { it.trim() }
        val big = buildString {
            append(text.take(1200))
            if (text.length > 1200) append("\n…")
            if (!footer.isNullOrBlank()) append("\n\n").append(footer)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setSubText(author.fullName)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
            // разрешение отозвали между проверкой и показом
        }
    }
}

class DailyPoemWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val author = Prefs(applicationContext).mainAuthor
        val repo = PoemRepository.get(applicationContext, author)
        try {
            // Стихи скачиваются только по выбору пользователя: если основной автор не загружен — молчим.
            repo.today()?.let { t ->
                DailyNotifications.show(applicationContext, author, t.poem.title, t.poem.text, t.poem.dateText)
            }
        } finally {
            DailyNotifications.scheduleNextFromWorker(applicationContext)
        }
        return Result.success()
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Обновляем только уже скачанных авторов.
        var failed = false
        for (author in Author.entries) {
            val repo = PoemRepository.get(applicationContext, author)
            if (!repo.hasData()) continue
            if (!repo.needsRefresh()) continue
            try {
                repo.refresh()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
        }
        return when {
            !failed -> Result.success()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }
}
