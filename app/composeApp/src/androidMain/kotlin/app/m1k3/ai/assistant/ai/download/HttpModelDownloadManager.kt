package app.m1k3.ai.assistant.ai.download

import android.content.Context
import app.m1k3.ai.domain.ai.LlmModel
import app.m1k3.ai.domain.ai.ModelDownloadManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP-based ModelDownloadManager for downloading GGUF models.
 *
 * Downloads model files from HuggingFace to internal storage.
 * Supports progress tracking and resumable downloads.
 *
 * @param context Android context for internal storage access
 */
class HttpModelDownloadManager(
    private val context: Context
) : ModelDownloadManager {

    private val logger = Logger.withTag("HttpModelDownloadManager")
    private val modelsDir: File get() = File(context.filesDir, "models").also { it.mkdirs() }

    override fun isModelAvailable(modelId: String): Boolean {
        val model = LlmModel.findById(modelId) ?: return false
        val file = File(modelsDir, model.filename)
        return file.exists() && file.length() > 0
    }

    override fun getModelPath(modelId: String): String? {
        val model = LlmModel.findById(modelId) ?: return null
        val file = File(modelsDir, model.filename)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    override fun deleteModel(modelId: String): Boolean {
        val model = LlmModel.findById(modelId) ?: return false
        val file = File(modelsDir, model.filename)
        return if (file.exists()) {
            logger.i { "Deleting model $modelId (${file.length() / 1_000_000}MB)" }
            file.delete()
        } else false
    }

    /**
     * Download a model from HuggingFace with progress reporting.
     *
     * @param model The LlmModel to download
     * @return Flow emitting DownloadProgress updates
     */
    fun download(model: LlmModel): Flow<DownloadProgress> = flow {
        val url = getDownloadUrl(model)
        val targetFile = File(modelsDir, model.filename)
        val tempFile = File(modelsDir, "${model.filename}.tmp")

        logger.i { "Downloading ${model.displayName} from $url" }
        emit(DownloadProgress.Starting(model.id))

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000

            // Support resume
            if (tempFile.exists()) {
                connection.setRequestProperty("Range", "bytes=${tempFile.length()}-")
                logger.i { "Resuming download from ${tempFile.length()} bytes" }
            }

            connection.connect()

            val totalBytes = connection.contentLengthLong + (if (tempFile.exists()) tempFile.length() else 0L)
            var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L

            connection.inputStream.use { input ->
                tempFile.outputStream().let { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes * 100).toInt()
                        } else 0

                        emit(DownloadProgress.InProgress(
                            modelId = model.id,
                            bytesDownloaded = downloadedBytes,
                            totalBytes = totalBytes,
                            progressPercent = progress
                        ))
                    }
                }
            }

            // Move temp file to final location
            tempFile.renameTo(targetFile)
            logger.i { "Download complete: ${targetFile.length() / 1_000_000}MB" }
            emit(DownloadProgress.Complete(model.id, targetFile.absolutePath))

        } catch (e: Exception) {
            logger.e(e) { "Download failed: ${e.message}" }
            emit(DownloadProgress.Failed(model.id, e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get the HuggingFace download URL for a model.
     */
    private fun getDownloadUrl(model: LlmModel): String {
        return when (model) {
            is LlmModel.Gemma4_E2B ->
                "https://huggingface.co/ggml-org/gemma-4-E2B-it-GGUF/resolve/main/${model.filename}"
            else ->
                "https://huggingface.co/models/${model.filename}"
        }
    }
}

/**
 * Download progress state.
 */
sealed class DownloadProgress {
    abstract val modelId: String

    data class Starting(override val modelId: String) : DownloadProgress()

    data class InProgress(
        override val modelId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : DownloadProgress()

    data class Complete(
        override val modelId: String,
        val filePath: String
    ) : DownloadProgress()

    data class Failed(
        override val modelId: String,
        val error: String
    ) : DownloadProgress()
}
