package com.dynamicisland.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K
import java.util.concurrent.TimeUnit

/** يعيد تشغيل الجزيرة بعد إقلاع الجهاز أو تحديث التطبيق. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WatchdogWorker.schedule(context)
        val s = IslandSettings.get(context)
        if (s.bool(K.ENABLED) && Perms.canDraw(context)) IslandService.start(context)
    }
}

/** حارس دوري (كل 15 دقيقة، أقل مدة يسمح بها WorkManager): يعيد الخدمة إن قُتلت. */
class WatchdogWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val s = IslandSettings.get(c)
        if (s.bool(K.ENABLED) && Perms.canDraw(c) && !IslandService.running) IslandService.start(c)
        return Result.success()
    }

    companion object {
        fun schedule(c: Context) {
            val req = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("island_watchdog", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
