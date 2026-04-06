package app.m1k3.ai.assistant.context

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController

/**
 * Manages runtime permission requests for user context providers.
 *
 * Each permission type has its own request flow:
 * - Location: standard runtime permission (ACCESS_COARSE_LOCATION)
 * - Health: HealthConnectClient.requestPermissionsActivityContract()
 * - Screen time: deep-link to Settings (PACKAGE_USAGE_STATS is special)
 * - Notifications: deep-link to notification listener settings
 *
 * Usage:
 * ```kotlin
 * val requester = rememberContextPermissionRequester { updatedState ->
 *     // Permission state changed
 * }
 * requester.requestLocation()
 * requester.requestHealth()
 * ```
 */
@Composable
fun rememberContextPermissionRequester(
    onStateChanged: (ContextPermissionState) -> Unit
): ContextPermissionRequester {
    val context = LocalContext.current
    val manager = remember { UserContextManager(context) }

    // Location permission launcher
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onStateChanged(manager.getPermissionStatus().toCommonState())
    }

    // Health Connect permission launcher
    val healthPermissions = HealthContextProvider.REQUIRED_PERMISSIONS
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { _ ->
        onStateChanged(manager.getPermissionStatus().toCommonState())
    }

    return remember {
        ContextPermissionRequester(
            context = context,
            onRequestLocation = {
                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onRequestHealth = {
                healthLauncher.launch(healthPermissions)
            },
            onRequestScreenTime = {
                // Special permission — must send to Settings
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            },
            onRequestNotifications = {
                context.startActivity(
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        )
    }
}

/**
 * Holds the permission request actions — passed into ContextualWelcomeCard.
 */
data class ContextPermissionRequester(
    val context: Context,
    val onRequestLocation: () -> Unit,
    val onRequestHealth: () -> Unit,
    val onRequestScreenTime: () -> Unit,
    val onRequestNotifications: () -> Unit
)

/** Convert Android-specific status to common state */
fun ContextPermissionStatus.toCommonState() = ContextPermissionState(
    hasLocation = hasLocation,
    hasHealth = hasHealth,
    hasScreenTime = hasScreenTime,
    hasNotifications = hasNotifications
)
