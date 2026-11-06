package com.byteharvest.mdreader.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.byteharvest.mdreader.App
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object RepositoryAutoSync {
    private const val UNIQUE_WORK_PREFIX = "repository-auto-sync:"
    private const val REPOSITORY_ID_KEY = "repository_id"

    fun schedule(context: Context, repoId: String) {
        val request = PeriodicWorkRequestBuilder<RepositoryAutoSyncWorker>(1, TimeUnit.DAYS)
            .setInputData(workDataOf(REPOSITORY_ID_KEY to repoId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueWorkName(repoId),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context, repoId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(repoId))
    }

    private fun uniqueWorkName(repoId: String) = "$UNIQUE_WORK_PREFIX$repoId"

    class RepositoryAutoSyncWorker(
        appContext: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val repoId = inputData.getString(REPOSITORY_ID_KEY)
                ?: return Result.failure()
            val app = applicationContext as? App ?: return Result.failure()
            val container = app.container

            if (repoId !in container.settings.autoSyncRepositoryIds.first()) return Result.success()
            if (container.repository.repo(repoId) == null) {
                container.settings.setAutoSyncEnabled(repoId, false)
                return Result.success()
            }

            return try {
                val sync = container.repository.refresh(repoId)
                if (sync.error != null) {
                    android.util.Log.w(TAG, "Auto-sync refresh failed for $repoId: ${sync.error}")
                    return Result.retry()
                }

                val report = container.repository.downloadForOffline(repoId) { _, _ -> }
                if (report.failed > 0) {
                    android.util.Log.w(TAG, "Auto-sync downloaded with ${report.failed} unavailable files for $repoId")
                    Result.retry()
                } else {
                    Result.success()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Auto-sync failed for $repoId", e)
                Result.retry()
            }
        }

        private companion object {
            const val TAG = "RepositoryAutoSync"
        }
    }
}
