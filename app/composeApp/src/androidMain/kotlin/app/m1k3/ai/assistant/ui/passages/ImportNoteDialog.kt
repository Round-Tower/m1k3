package app.m1k3.ai.assistant.ui.passages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.m1k3.ai.domain.passages.SourceKind
import app.m1k3.ai.domain.passages.usecases.ImportTextUseCase
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * ImportNoteDialog — minimal paste-a-note import UX.
 *
 * Lets the user paste text, choose a kind (plain text / markdown), and
 * commit it to the passages store via [ImportTextUseCase]. Success and
 * failure both surface through [onFinished] so the caller can toast /
 * update status as they see fit.
 *
 * Scope is deliberately narrow for the day-one slice — no file picker,
 * no rich editing, no tag/category metadata. Those earn their way in
 * once the flow has a real consumer.
 */
@Composable
fun ImportNoteDialog(
    onDismiss: () -> Unit,
    onFinished: (Result<String>) -> Unit,
) {
    val importTextUseCase: ImportTextUseCase = koinInject()
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SourceKind.MARKDOWN) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Import a note") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting,
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    enabled = !submitting,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourceKind.values().forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { if (!submitting) kind = k },
                            label = { Text(k.name.lowercase().replaceFirstChar { it.titlecase() }) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting && content.isNotBlank(),
                onClick = {
                    submitting = true
                    scope.launch {
                        val effectiveTitle =
                            title.trim().ifBlank {
                                content.lineSequence().firstOrNull { it.isNotBlank() }?.take(60) ?: "Untitled note"
                            }
                        val uri = "note:${System.currentTimeMillis()}"
                        val result =
                            importTextUseCase.execute(
                                title = effectiveTitle,
                                content = content,
                                kind = kind,
                                uri = uri,
                            )
                        submitting = false
                        onFinished(
                            result.fold(
                                onSuccess = { Result.success(it.title) },
                                onFailure = { Result.failure(it) },
                            ),
                        )
                        onDismiss()
                    }
                },
            ) {
                Text(if (submitting) "Importing…" else "Import")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = onDismiss,
            ) { Text("Cancel") }
        },
        modifier = Modifier.padding(16.dp),
    )
}
