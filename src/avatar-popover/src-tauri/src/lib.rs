use tauri::{
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    Manager,
};
use tauri_plugin_global_shortcut::{Code, GlobalShortcutExt, Modifiers, Shortcut};

/// Toggle main window visibility
fn toggle_window(app: &tauri::AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        if window.is_visible().unwrap_or(false) {
            let _ = window.hide();
        } else {
            let _ = window.show();
            let _ = window.set_focus();
        }
    }
}

/// Position window near system tray (top-right on macOS)
fn position_window_near_tray(app: &tauri::AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        // Get primary monitor dimensions
        if let Some(monitor) = window.primary_monitor().ok().flatten() {
            let screen_size = monitor.size();
            let window_size = window.outer_size().unwrap_or_default();

            // Position near top-right (where menu bar is on macOS)
            let x = screen_size.width as i32 - window_size.width as i32 - 20;
            let y = 40; // Below menu bar

            let _ = window.set_position(tauri::Position::Physical(
                tauri::PhysicalPosition { x, y },
            ));
        }
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .setup(|app| {
            // Setup logging in debug mode
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }

            // Build system tray
            let _tray = TrayIconBuilder::new()
                .tooltip("M1K3 Avatar - Click to toggle")
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        let app = tray.app_handle();
                        position_window_near_tray(app);
                        toggle_window(app);
                    }
                })
                .build(app)?;

            // Register global shortcut: Cmd+Shift+M (macOS) / Ctrl+Shift+M (others)
            let shortcut = Shortcut::new(
                Some(Modifiers::SUPER | Modifiers::SHIFT),
                Code::KeyM,
            );

            let app_handle = app.handle().clone();
            app.handle().plugin(
                tauri_plugin_global_shortcut::Builder::new()
                    .with_handler(move |_app, _shortcut, event| {
                        if event.state() == tauri_plugin_global_shortcut::ShortcutState::Pressed {
                            position_window_near_tray(&app_handle);
                            toggle_window(&app_handle);
                        }
                    })
                    .build(),
            )?;

            // Register the shortcut
            app.global_shortcut().register(shortcut)?;

            log::info!("M1K3 Avatar popover initialized");
            log::info!("Global shortcut: Cmd+Shift+M to toggle");

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
