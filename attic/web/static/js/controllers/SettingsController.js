/**
 * M1K3 Settings Controller
 * Manages application settings, preferences, and configuration
 */
class SettingsController extends EventTarget {
    constructor(stateManager, websocketManager) {
        super();
        
        this.stateManager = stateManager;
        this.websocketManager = websocketManager;
        
        this.elements = {};
        this.settings = {
            // UI Settings
            theme: 'dark',
            autoScroll: true,
            showTimestamps: true,
            animationSpeed: 1.0,
            debugMode: false,
            
            // Audio Settings
            soundEnabled: true,
            volume: 0.7,
            voiceEnabled: true,
            
            // TTS Settings
            ttsEngine: 'auto',
            voiceProfile: 'natural',
            ttsSpeed: 1.0,
            ttsVolume: 0.8,
            
            // Avatar Settings
            avatarStyle: 'robot',
            avatarColor: '#E25303',
            emotionIntensity: 50,
            autoEmotions: true,
            particleEffects: true,
            
            // Performance Settings
            updateInterval: 2000,
            maxMessages: 1000,
            enableMetrics: true,
            enableCharts: true,
            
            // Privacy Settings
            saveHistory: true,
            analytics: false,
            errorReporting: true,
            
            // Advanced Settings
            websocketUrl: 'ws://localhost:8081',
            reconnectAttempts: 10,
            heartbeatInterval: 30000
        };
        
        this.presets = {
            performance: {
                animationSpeed: 0.5,
                particleEffects: false,
                enableCharts: false,
                updateInterval: 5000
            },
            accessibility: {
                animationSpeed: 0.1,
                autoScroll: true,
                showTimestamps: true,
                soundEnabled: false
            },
            mobile: {
                particleEffects: false,
                enableCharts: false,
                updateInterval: 3000,
                maxMessages: 500
            }
        };
        
        console.log('⚙️ SettingsController initialized');
    }
    
    /**
     * Initialize settings controller
     */
    async initialize() {
        this.loadSettings();
        this.findElements();
        this.setupEventListeners();
        this.renderSettings();
        this.applySettings();
        
        console.log('⚙️ Settings controller initialized');
        return this;
    }
    
    /**
     * Find DOM elements
     */
    findElements() {
        this.elements = {
            // Containers
            settingsContainer: document.getElementById('settingsContainer'),
            settingsSections: document.querySelectorAll('.settings-section'),
            
            // UI Settings
            themeSelect: document.getElementById('themeSelect'),
            autoScrollToggle: document.getElementById('autoScrollToggle'),
            showTimestampsToggle: document.getElementById('showTimestampsToggle'),
            animationSpeedSlider: document.getElementById('animationSpeedSlider'),
            debugModeToggle: document.getElementById('debugModeToggle'),
            
            // Audio Settings
            soundEnabledToggle: document.getElementById('soundEnabledToggle'),
            volumeSlider: document.getElementById('volumeSlider'),
            voiceEnabledToggle: document.getElementById('voiceEnabledToggle'),
            
            // TTS Settings
            ttsEngineSelect: document.getElementById('ttsEngineSelect'),
            voiceProfileSelect: document.getElementById('voiceProfileSelect'),
            ttsSpeedSlider: document.getElementById('ttsSpeedSlider'),
            ttsVolumeSlider: document.getElementById('ttsVolumeSlider'),
            
            // Avatar Settings
            avatarStyleSelect: document.getElementById('avatarStyleSelect'),
            avatarColorPicker: document.getElementById('avatarColorPicker'),
            emotionIntensitySlider: document.getElementById('emotionIntensitySlider'),
            autoEmotionsToggle: document.getElementById('autoEmotionsToggle'),
            particleEffectsToggle: document.getElementById('particleEffectsToggle'),
            
            // Performance Settings
            updateIntervalSlider: document.getElementById('updateIntervalSlider'),
            maxMessagesInput: document.getElementById('maxMessagesInput'),
            enableMetricsToggle: document.getElementById('enableMetricsToggle'),
            enableChartsToggle: document.getElementById('enableChartsToggle'),
            
            // Privacy Settings
            saveHistoryToggle: document.getElementById('saveHistoryToggle'),
            analyticsToggle: document.getElementById('analyticsToggle'),
            errorReportingToggle: document.getElementById('errorReportingToggle'),
            
            // Advanced Settings
            websocketUrlInput: document.getElementById('websocketUrlInput'),
            reconnectAttemptsInput: document.getElementById('reconnectAttemptsInput'),
            heartbeatIntervalInput: document.getElementById('heartbeatIntervalInput'),
            
            // Action buttons
            saveButton: document.getElementById('saveSettings'),
            resetButton: document.getElementById('resetSettings'),
            exportButton: document.getElementById('exportSettings'),
            importButton: document.getElementById('importSettings'),
            importFileInput: document.getElementById('importFileInput'),
            
            // Presets
            presetButtons: document.querySelectorAll('.preset-btn'),
            
            // Status
            settingsStatus: document.getElementById('settingsStatus')
        };
    }
    
    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // UI Settings
        this.bindSetting('themeSelect', 'change', 'theme');
        this.bindSetting('autoScrollToggle', 'change', 'autoScroll', 'checked');
        this.bindSetting('showTimestampsToggle', 'change', 'showTimestamps', 'checked');
        this.bindSetting('animationSpeedSlider', 'input', 'animationSpeed', 'valueAsNumber');
        this.bindSetting('debugModeToggle', 'change', 'debugMode', 'checked');
        
        // Audio Settings
        this.bindSetting('soundEnabledToggle', 'change', 'soundEnabled', 'checked');
        this.bindSetting('volumeSlider', 'input', 'volume', 'valueAsNumber');
        this.bindSetting('voiceEnabledToggle', 'change', 'voiceEnabled', 'checked');
        
        // TTS Settings
        this.bindSetting('ttsEngineSelect', 'change', 'ttsEngine');
        this.bindSetting('voiceProfileSelect', 'change', 'voiceProfile');
        this.bindSetting('ttsSpeedSlider', 'input', 'ttsSpeed', 'valueAsNumber');
        this.bindSetting('ttsVolumeSlider', 'input', 'ttsVolume', 'valueAsNumber');
        
        // Avatar Settings
        this.bindSetting('avatarStyleSelect', 'change', 'avatarStyle');
        this.bindSetting('avatarColorPicker', 'input', 'avatarColor');
        this.bindSetting('emotionIntensitySlider', 'input', 'emotionIntensity', 'valueAsNumber');
        this.bindSetting('autoEmotionsToggle', 'change', 'autoEmotions', 'checked');
        this.bindSetting('particleEffectsToggle', 'change', 'particleEffects', 'checked');
        
        // Performance Settings
        this.bindSetting('updateIntervalSlider', 'input', 'updateInterval', 'valueAsNumber');
        this.bindSetting('maxMessagesInput', 'input', 'maxMessages', 'valueAsNumber');
        this.bindSetting('enableMetricsToggle', 'change', 'enableMetrics', 'checked');
        this.bindSetting('enableChartsToggle', 'change', 'enableCharts', 'checked');
        
        // Privacy Settings
        this.bindSetting('saveHistoryToggle', 'change', 'saveHistory', 'checked');
        this.bindSetting('analyticsToggle', 'change', 'analytics', 'checked');
        this.bindSetting('errorReportingToggle', 'change', 'errorReporting', 'checked');
        
        // Advanced Settings
        this.bindSetting('websocketUrlInput', 'input', 'websocketUrl');
        this.bindSetting('reconnectAttemptsInput', 'input', 'reconnectAttempts', 'valueAsNumber');
        this.bindSetting('heartbeatIntervalInput', 'input', 'heartbeatInterval', 'valueAsNumber');
        
        // Action buttons
        if (this.elements.saveButton) {
            this.elements.saveButton.addEventListener('click', () => {
                this.saveSettings();
            });
        }
        
        if (this.elements.resetButton) {
            this.elements.resetButton.addEventListener('click', () => {
                this.resetSettings();
            });
        }
        
        if (this.elements.exportButton) {
            this.elements.exportButton.addEventListener('click', () => {
                this.exportSettings();
            });
        }
        
        if (this.elements.importButton) {
            this.elements.importButton.addEventListener('click', () => {
                this.elements.importFileInput?.click();
            });
        }
        
        if (this.elements.importFileInput) {
            this.elements.importFileInput.addEventListener('change', (e) => {
                this.importSettings(e.target.files[0]);
            });
        }
        
        // Preset buttons
        this.elements.presetButtons.forEach(button => {
            button.addEventListener('click', () => {
                const preset = button.dataset.preset;
                this.applyPreset(preset);
            });
        });
    }
    
    /**
     * Bind setting element to setting property
     */
    bindSetting(elementKey, event, settingKey, property = 'value') {
        const element = this.elements[elementKey];
        if (!element) return;
        
        element.addEventListener(event, (e) => {
            const value = property === 'checked' ? e.target.checked :
                         property === 'valueAsNumber' ? e.target.valueAsNumber :
                         e.target.value;
            
            this.updateSetting(settingKey, value);
        });
    }
    
    /**
     * Update a single setting
     */
    updateSetting(key, value) {
        const oldValue = this.settings[key];
        this.settings[key] = value;
        
        this.applySetting(key, value, oldValue);
        this.showSettingStatus(`${key} updated`);
        
        this.emit('setting.changed', { key, value, oldValue });
    }
    
    /**
     * Apply a single setting
     */
    applySetting(key, value, oldValue) {
        switch (key) {
            case 'theme':
                this.applyTheme(value);
                break;
                
            case 'animationSpeed':
                this.applyAnimationSpeed(value);
                break;
                
            case 'debugMode':
                this.applyDebugMode(value);
                break;
                
            case 'particleEffects':
                this.applyParticleEffects(value);
                break;
                
            case 'ttsEngine':
            case 'voiceProfile':
            case 'ttsSpeed':
            case 'ttsVolume':
                this.applyTTSSettings();
                break;
                
            case 'avatarStyle':
            case 'avatarColor':
            case 'emotionIntensity':
                this.applyAvatarSettings();
                break;
                
            case 'updateInterval':
                this.applyUpdateInterval(value);
                break;
                
            case 'websocketUrl':
                this.applyWebSocketSettings();
                break;
        }
    }
    
    /**
     * Apply all settings
     */
    applySettings() {
        for (const [key, value] of Object.entries(this.settings)) {
            this.applySetting(key, value);
        }
        
        this.emit('settings.applied', { settings: this.settings });
    }
    
    /**
     * Apply theme
     */
    applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        this.stateManager.updateState('ui.theme', theme);
    }
    
    /**
     * Apply animation speed
     */
    applyAnimationSpeed(speed) {
        document.documentElement.style.setProperty('--animation-speed', speed);
        
        // Update controllers
        if (window.m1k3App && window.m1k3App.avatarController) {
            window.m1k3App.avatarController.setAnimationSpeed(speed);
        }
    }
    
    /**
     * Apply debug mode
     */
    applyDebugMode(enabled) {
        document.documentElement.classList.toggle('debug-mode', enabled);
        this.stateManager.updateState('ui.debugVisible', enabled);
        
        if (enabled) {
            console.log('🔍 Debug mode enabled');
        }
    }
    
    /**
     * Apply particle effects
     */
    applyParticleEffects(enabled) {
        document.documentElement.classList.toggle('no-particles', !enabled);
        
        // Update avatar controller
        if (window.m1k3App && window.m1k3App.avatarController) {
            // Could disable particle system
        }
    }
    
    /**
     * Apply TTS settings
     */
    applyTTSSettings() {
        const ttsSettings = {
            engine: this.settings.ttsEngine,
            profile: this.settings.voiceProfile,
            speed: this.settings.ttsSpeed,
            volume: this.settings.ttsVolume
        };
        
        this.stateManager.updateVoiceState(ttsSettings);
        
        // Send to server if connected
        if (this.websocketManager && this.websocketManager.isConnected) {
            this.websocketManager.send({
                type: 'update_tts_settings',
                settings: ttsSettings
            });
        }
    }
    
    /**
     * Apply avatar settings
     */
    applyAvatarSettings() {
        const avatarSettings = {
            style: this.settings.avatarStyle,
            color: this.settings.avatarColor,
            intensity: this.settings.emotionIntensity
        };
        
        this.stateManager.updateState('avatar.style', avatarSettings.style);
        this.stateManager.updateState('avatar.color', avatarSettings.color);
        this.stateManager.updateState('avatar.intensity', avatarSettings.intensity);
        
        // Update avatar controller
        if (window.m1k3App && window.m1k3App.avatarController) {
            window.m1k3App.avatarController.setStyle(avatarSettings.style, avatarSettings.color);
            window.m1k3App.avatarController.setIntensity(avatarSettings.intensity);
        }
    }
    
    /**
     * Apply update interval
     */
    applyUpdateInterval(interval) {
        // Update status controller
        if (window.m1k3App && window.m1k3App.statusController) {
            // Would need to restart update timer
        }
    }
    
    /**
     * Apply WebSocket settings
     */
    applyWebSocketSettings() {
        // Would need to reconnect WebSocket with new settings
        if (this.websocketManager) {
            this.websocketManager.config.url = this.settings.websocketUrl;
            this.websocketManager.config.maxReconnectAttempts = this.settings.reconnectAttempts;
            this.websocketManager.config.heartbeatInterval = this.settings.heartbeatInterval;
        }
    }
    
    /**
     * Render settings to UI
     */
    renderSettings() {
        for (const [key, value] of Object.entries(this.settings)) {
            this.renderSetting(key, value);
        }
    }
    
    /**
     * Render a single setting to UI
     */
    renderSetting(key, value) {
        const elementMap = {
            theme: 'themeSelect',
            autoScroll: 'autoScrollToggle',
            showTimestamps: 'showTimestampsToggle',
            animationSpeed: 'animationSpeedSlider',
            debugMode: 'debugModeToggle',
            soundEnabled: 'soundEnabledToggle',
            volume: 'volumeSlider',
            voiceEnabled: 'voiceEnabledToggle',
            ttsEngine: 'ttsEngineSelect',
            voiceProfile: 'voiceProfileSelect',
            ttsSpeed: 'ttsSpeedSlider',
            ttsVolume: 'ttsVolumeSlider',
            avatarStyle: 'avatarStyleSelect',
            avatarColor: 'avatarColorPicker',
            emotionIntensity: 'emotionIntensitySlider',
            autoEmotions: 'autoEmotionsToggle',
            particleEffects: 'particleEffectsToggle',
            updateInterval: 'updateIntervalSlider',
            maxMessages: 'maxMessagesInput',
            enableMetrics: 'enableMetricsToggle',
            enableCharts: 'enableChartsToggle',
            saveHistory: 'saveHistoryToggle',
            analytics: 'analyticsToggle',
            errorReporting: 'errorReportingToggle',
            websocketUrl: 'websocketUrlInput',
            reconnectAttempts: 'reconnectAttemptsInput',
            heartbeatInterval: 'heartbeatIntervalInput'
        };
        
        const elementKey = elementMap[key];
        const element = this.elements[elementKey];
        
        if (!element) return;
        
        if (element.type === 'checkbox') {
            element.checked = value;
        } else if (element.type === 'range' || element.type === 'number') {
            element.value = value;
            
            // Update display if there's a connected display element
            const displayElement = document.getElementById(elementKey + 'Value');
            if (displayElement) {
                displayElement.textContent = value;
            }
        } else {
            element.value = value;
        }
    }
    
    /**
     * Apply preset configuration
     */
    applyPreset(presetName) {
        const preset = this.presets[presetName];
        if (!preset) return;
        
        // Apply preset settings
        for (const [key, value] of Object.entries(preset)) {
            this.updateSetting(key, value);
            this.renderSetting(key, value);
        }
        
        this.showSettingStatus(`Applied ${presetName} preset`);
        this.emit('preset.applied', { preset: presetName, settings: preset });
    }
    
    /**
     * Load settings from storage
     */
    loadSettings() {
        try {
            const saved = localStorage.getItem('m1k3_settings');
            if (saved) {
                const parsedSettings = JSON.parse(saved);
                this.settings = { ...this.settings, ...parsedSettings };
                console.log('⚙️ Settings loaded from storage');
            }
        } catch (error) {
            console.error('⚙️ Failed to load settings:', error);
        }
    }
    
    /**
     * Save settings to storage
     */
    saveSettings() {
        try {
            localStorage.setItem('m1k3_settings', JSON.stringify(this.settings));
            this.showSettingStatus('Settings saved successfully');
            this.emit('settings.saved', { settings: this.settings });
            console.log('⚙️ Settings saved to storage');
        } catch (error) {
            console.error('⚙️ Failed to save settings:', error);
            this.showSettingStatus('Failed to save settings', 'error');
        }
    }
    
    /**
     * Reset settings to defaults
     */
    resetSettings() {
        if (confirm('Reset all settings to defaults?')) {
            // Store original settings
            const originalSettings = { ...this.settings };
            
            // Reset to defaults (reinitialize)
            this.settings = new SettingsController().settings;
            
            this.renderSettings();
            this.applySettings();
            this.saveSettings();
            
            this.showSettingStatus('Settings reset to defaults');
            this.emit('settings.reset', { 
                original: originalSettings, 
                current: this.settings 
            });
        }
    }
    
    /**
     * Export settings to file
     */
    exportSettings() {
        const exportData = {
            version: '1.0.0',
            timestamp: Date.now(),
            settings: this.settings,
            metadata: {
                userAgent: navigator.userAgent,
                url: window.location.href
            }
        };
        
        const blob = new Blob([JSON.stringify(exportData, null, 2)], { 
            type: 'application/json' 
        });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `m1k3-settings-${Date.now()}.json`;
        a.click();
        URL.revokeObjectURL(url);
        
        this.showSettingStatus('Settings exported');
        this.emit('settings.exported', { data: exportData });
    }
    
    /**
     * Import settings from file
     */
    async importSettings(file) {
        if (!file) return;
        
        try {
            const text = await file.text();
            const data = JSON.parse(text);
            
            if (data.settings && typeof data.settings === 'object') {
                const originalSettings = { ...this.settings };
                this.settings = { ...this.settings, ...data.settings };
                
                this.renderSettings();
                this.applySettings();
                this.saveSettings();
                
                this.showSettingStatus('Settings imported successfully');
                this.emit('settings.imported', { 
                    original: originalSettings,
                    imported: data.settings,
                    current: this.settings 
                });
            } else {
                throw new Error('Invalid settings file format');
            }
            
        } catch (error) {
            console.error('⚙️ Failed to import settings:', error);
            this.showSettingStatus('Failed to import settings', 'error');
        }
        
        // Clear file input
        if (this.elements.importFileInput) {
            this.elements.importFileInput.value = '';
        }
    }
    
    /**
     * Show setting status message
     */
    showSettingStatus(message, type = 'success') {
        if (this.elements.settingsStatus) {
            this.elements.settingsStatus.textContent = message;
            this.elements.settingsStatus.className = `settings-status ${type}`;
            
            // Auto-hide after 3 seconds
            setTimeout(() => {
                if (this.elements.settingsStatus) {
                    this.elements.settingsStatus.textContent = '';
                    this.elements.settingsStatus.className = 'settings-status';
                }
            }, 3000);
        }
    }
    
    /**
     * Get current settings
     */
    getSettings() {
        return { ...this.settings };
    }
    
    /**
     * Get setting value
     */
    getSetting(key) {
        return this.settings[key];
    }
    
    /**
     * Update settings object
     */
    updateSettings(newSettings) {
        const originalSettings = { ...this.settings };
        this.settings = { ...this.settings, ...newSettings };
        
        this.renderSettings();
        this.applySettings();
        
        this.emit('settings.updated', {
            original: originalSettings,
            current: this.settings,
            updated: newSettings
        });
    }
    
    /**
     * Emit custom event
     */
    emit(eventName, data) {
        const event = new CustomEvent(eventName, { detail: data });
        this.dispatchEvent(event);
    }
    
    /**
     * Cleanup
     */
    destroy() {
        this.saveSettings();
        console.log('⚙️ SettingsController destroyed');
    }
}

// Export for global use
window.SettingsController = SettingsController;