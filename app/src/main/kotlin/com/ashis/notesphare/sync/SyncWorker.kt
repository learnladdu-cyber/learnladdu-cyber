package com.ashis.notesphare.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ashis.notesphare.NoteSphereApp

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NoteSphereApp ?: return Result.success()
        val report = runCatching { app.container.syncCoordinator.syncNow() }.getOrElse {
            return Result.retry()
        }
        if (!report.signedIn) return Result.success()
        return if (report.failed > 0) Result.retry() else Result.success()
    }
}
