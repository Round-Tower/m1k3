package app.m1k3.ai.assistant.ai.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.m1k3.ai.assistant.onboarding.OnboardingDownloadState
import app.m1k3.ai.domain.ai.LlmModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import java.util.UUID

/**
 * ModelDownloadWorker — background GGUF download via WorkManager.
 *
 * Runs as a foreground service: survives screen lock, Doze, and backgrounding.
 * Replaces the viewModelScope.launch that was killed when the phone went to sleep.
 *
 * Progress is reported via WorkManager's setProgress() and observed as a Flow
 * of [OnboardingDownloadState] in [observeAsFlow].
 */
class ModelDownloadWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return ListenableWorker.Result.failure(workDataOf(KEY_ERROR to "Missing model ID"))
        val model = LlmModel.findById(modelId)
            ?: return ListenableWorker.Result.failure(workDataOf(KEY_ERROR to "Unknown model: $modelId"))

        createNotificationChannel(ctx)
        setForeground(buildForegroundInfo(model, 0))

        val httpManager = HttpModelDownloadManager(ctx)

        // Idempotent — skip if already on disk
        if (httpManager.isModelAvailable(modelId)) {
            return ListenableWorker.Result.success(
                workDataOf(KEY_FILE_PATH to httpManager.getModelPath(modelId))
            )
        }

        var downloadFailed = false
        var failureReason = ""

        httpManager.download(model).collect { progress ->
            when (progress) {
                is DownloadProgress.Starting ->
                    setProgress(workDataOf(KEY_STATE to STATE_STARTING))

                is DownloadProgress.InProgress -> {
                    val pct = progress.progressPercent
                    setProgress(workDataOf(
                        KEY_STATE to STATE_DOWNLOADING,
                        KEY_PROGRESS_PERCENT to pct,
                        KEY_DOWNLOADED_MB to (progress.bytesDownloaded / 1_000_000).toInt(),
                        KEY_TOTAL_MB to (progress.totalBytes / 1_000_000).toInt()
                    ))
                    setForeground(buildForegroundInfo(model, pct))
                }

                is DownloadProgress.Complete ->
                    setProgress(workDataOf(KEY_STATE to STATE_COMPLETE))

                is DownloadProgress.Failed -> {
                    downloadFailed = true
                    failureReason = progress.error
                }
            }
        }

        return if (!downloadFailed && httpManager.isModelAvailable(modelId)) {
            ListenableWorker.Result.success(
                workDataOf(KEY_FILE_PATH to httpManager.getModelPath(modelId))
            )
        } else {
            ListenableWorker.Result.failure(
                workDataOf(KEY_ERROR to failureReason.ifEmpty { "Download incomplete" })
            )
        }
    }

    private fun buildForegroundInfo(model: LlmModel, percent: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Installing ${model.displayName}")
            .setContentText(if (percent > 0) "$percent% complete" else "Starting download…")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_PROGRESS_PERCENT = "progress_pct"
        const val KEY_DOWNLOADED_MB = "downloaded_mb"
        const val KEY_TOTAL_MB = "total_mb"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_ERROR = "error"
        const val KEY_STATE = "state"

        const val STATE_STARTING = "starting"
        const val STATE_DOWNLOADING = "downloading"
        const val STATE_COMPLETE = "complete"

        private const val CHANNEL_ID = "m1k3_model_install"
        private const val NOTIFICATION_ID = 4200

        /**
         * Enqueue a background model download.
         * Uses [ExistingWorkPolicy.KEEP] — calling again during active download is safe.
         *
         * @return WorkRequest UUID to pass to [observeAsFlow]
         */
        fun enqueue(context: Context, model: LlmModel): UUID {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL_ID to model.id))
                .build()

            val wm = WorkManager.getInstance(context)

            // Cancel downloads for all OTHER models — only one active download
            // at a time. Without this, WorkManager re-queues previous jobs on
            // app restart, splitting bandwidth between tiers.
            LlmModel.all()
                .filter { it.id != model.id }
                .forEach { other -> wm.cancelUniqueWork("download_${other.id}") }

            // REPLACE: safe because download resumes from .tmp (append mode).
            wm.enqueueUniqueWork("download_${model.id}", ExistingWorkPolicy.REPLACE, request)
            return request.id
        }

        /**
         * Observe a running download as a [OnboardingDownloadState] Flow.
         *
         * Observes by **unique work name** (not UUID) so that re-enqueueing
         * with [ExistingWorkPolicy.KEEP] still returns progress from the
         * original running job, not the abandoned new request.
         */
        fun observeAsFlow(context: Context, model: LlmModel): Flow<OnboardingDownloadState> =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow("download_${model.id}")
                .mapNotNull { infoList ->
                    // Take the most recent work item (last in list)
                    val info = infoList.lastOrNull() ?: return@mapNotNull null
                    when (info.state) {
                        WorkInfo.State.ENQUEUED ->
                            OnboardingDownloadState.Starting

                        WorkInfo.State.RUNNING -> {
                            val pct = info.progress.getInt(KEY_PROGRESS_PERCENT, 0)
                            val dlMb = info.progress.getInt(KEY_DOWNLOADED_MB, 0)
                            val totalMb = info.progress.getInt(KEY_TOTAL_MB, 0)
                            if (totalMb > 0) {
                                OnboardingDownloadState.Downloading(pct, dlMb, totalMb)
                            } else {
                                OnboardingDownloadState.Starting
                            }
                        }

                        WorkInfo.State.SUCCEEDED ->
                            OnboardingDownloadState.Complete

                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED ->
                            OnboardingDownloadState.Failed(
                                info.outputData.getString(KEY_ERROR) ?: "Download failed"
                            )

                        else -> null
                    }
                }

        fun createNotificationChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Model Installation",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Progress while M1K3 installs its intelligence engine"
                    setShowBadge(false)
                }
                ctx.getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(channel)
            }
        }
    }
}
