/**
 * M1K3 Settings Controller - Manages application settings and preferences
 */
class SettingsController {
    constructor(stateManager, navigationManager) {
        this.stateManager = stateManager;
        this.navigationManager = navigationManager;
        
        // Settings elements
        this.toggleSwitches = new Map();
        this.selectors = new Map();
        this.settingsData = {};
        
        console.log('⚙️ SettingsController initialized');
    }
    
    // Initialize settings controller
    async initialize() {
        this.bindElements();
        this.setupEventListeners();
        this.loadSettings();
        this.restoreFromState();
        
        console.log('⚙️ Settings interface ready');
    }
    
    // Bind DOM elements
    bindElements() {
        // Toggle switches
        this.toggleSwitches.set('voice', document.getElementById('voiceToggle'));
        this.toggleSwitches.set('animations', document.getElementById('animToggle'));
        this.toggleSwitches.set('debug', document.getElementById('debugToggle'));
        this.toggleSwitches.set('particles', document.getElementById('particleToggle'));
        
        // Selectors
        this.selectors.set('voiceProfile', document.getElementById('voiceProfile'));
        this.selectors.set('theme', document.getElementById('themeSelect'));
        this.selectors.set('fps', document.getElementById('fpsSelect'));
    }
    
    // Setup event listeners
    setupEventListeners() {
        // Toggle switches
        this.toggleSwitches.forEach((element, key) => {
            if (element) {
                element.addEventListener('click', () => {
                    this.handleToggle(key, element);
                });
            }
        });
        
        // Selectors
        this.selectors.forEach((element, key) => {
            if (element) {
                element.addEventListener('change', (e) => {
                    this.handleSelectorChange(key, e.target.value);
                });
            }
        });
        
        // State listeners
        this.stateManager.subscribe('ui.soundsEnabled', (enabled) => {
            this.syncToggle('voice', enabled);
        });
        
        this.stateManager.subscribe('ui.animationsEnabled', (enabled) => {
            this.syncToggle('animations', enabled);
        });
        
        this.stateManager.subscribe('ui.debugConsoleVisible', (visible) => {
            this.syncToggle('debug', visible);
        });
        
        this.stateManager.subscribe('ui.theme', (theme) => {
            this.syncSelector('theme', theme);
            this.applyTheme(theme);
        });
    }
    
    // Handle toggle switch changes
    handleToggle(key, element) {
        const isActive = element.classList.toggle('active');
        
        switch (key) {
            case 'voice':
                this.stateManager.set('ui.soundsEnabled', isActive);
                break;
                
            case 'animations':
                this.stateManager.set('ui.animationsEnabled', isActive);
                this.applyAnimationSettings(isActive);
                break;
                
            case 'debug':
                this.stateManager.set('ui.debugConsoleVisible', isActive);
                this.toggleDebugConsole(isActive);
                break;
                
            case 'particles':
                this.stateManager.set('ui.particleEffectsEnabled', isActive);
                this.applyParticleSettings(isActive);
                break;
        }
        
        this.saveSettings();
        console.log(`⚙️ Toggle ${key}: ${isActive}`);
    }
    
    // Handle selector changes
    handleSelectorChange(key, value) {
        switch (key) {
            case 'voiceProfile':
                this.stateManager.set('ui.voiceProfile', value);
                this.applyVoiceProfile(value);
                break;
                
            case 'theme':
                this.stateManager.set('ui.theme', value);
                this.applyTheme(value);
                break;
                
            case 'fps':
                this.stateManager.set('ui.avatarFPS', value);
                this.applyFPSSettings(value);
                break;
        }
        
        this.saveSettings();
        console.log(`⚙️ Setting ${key}: ${value}`);
    }
    
    // Apply theme changes
    applyTheme(theme) {
        const validThemes = ['pure-black', 'dark', 'midnight'];
        if (!validThemes.includes(theme)) return;
        
        document.documentElement.className = 'theme-' + theme;
        
        // Notify other components about theme change
        this.stateManager.eventBus.dispatchEvent(new CustomEvent('theme.changed', {
            detail: { theme }
        }));
    }
    
    // Apply animation settings
    applyAnimationSettings(enabled) {
        document.body.classList.toggle('no-animations', !enabled);
        
        if (enabled) {
            document.documentElement.style.setProperty('--duration-fast', '150ms');
            document.documentElement.style.setProperty('--duration-normal', '300ms');
            document.documentElement.style.setProperty('--duration-slow', '500ms');
        } else {
            document.documentElement.style.setProperty('--duration-fast', '0ms');
            document.documentElement.style.setProperty('--duration-normal', '0ms');
            document.documentElement.style.setProperty('--duration-slow', '0ms');
        }
    }
    
    // Apply particle settings
    applyParticleSettings(enabled) {
        const particleCanvases = document.querySelectorAll('#particleCanvas');
        particleCanvases.forEach(canvas => {
            canvas.style.display = enabled ? 'block' : 'none';
        });
    }
    
    // Apply voice profile settings
    applyVoiceProfile(profile) {
        // This would integrate with TTS system
        console.log(`⚙️ Voice profile set to: ${profile}`);
        
        // Notify other components
        this.stateManager.eventBus.dispatchEvent(new CustomEvent('voice.profile_changed', {
            detail: { profile }
        }));
    }
    
    // Apply FPS settings
    applyFPSSettings(fps) {
        const pixelEngine = this.stateManager.get('avatar.pixelEngine');
        if (pixelEngine && pixelEngine.setFPS) {
            if (fps === 'auto') {
                // Use device-appropriate FPS
                const targetFPS = window.innerWidth <= 767 ? 30 : 60;
                pixelEngine.setFPS(targetFPS);
            } else {
                pixelEngine.setFPS(parseInt(fps));
            }
        }
        
        console.log(`⚙️ Avatar FPS set to: ${fps}`);
    }
    
    // Toggle debug console
    toggleDebugConsole(visible) {
        const debugConsole = document.getElementById('debugConsole');
        if (debugConsole) {
            debugConsole.style.display = visible ? 'flex' : 'none';
        }
        
        // Notify debug console if it exists
        if (window.debugConsole && typeof window.debugConsole.toggle === 'function') {
            window.debugConsole.visible = visible;
        }
    }
    
    // Sync toggle switch state
    syncToggle(key, active) {
        const element = this.toggleSwitches.get(key);
        if (element) {
            element.classList.toggle('active', active);
        }
    }
    
    // Sync selector state
    syncSelector(key, value) {
        const element = this.selectors.get(key);
        if (element && element.value !== value) {
            element.value = value;
        }
    }
    
    // Load settings from localStorage
    loadSettings() {
        try {
            const saved = localStorage.getItem('m1k3-settings');
            if (saved) {
                this.settingsData = JSON.parse(saved);
                this.applyLoadedSettings();
            } else {
                this.setDefaultSettings();
            }
        } catch (error) {
            console.error('⚙️ Error loading settings:', error);
            this.setDefaultSettings();
        }
    }
    
    // Set default settings
    setDefaultSettings() {
        this.settingsData = {
            soundsEnabled: true,
            animationsEnabled: true,
            debugConsoleVisible: false,
            particleEffectsEnabled: true,
            voiceProfile: 'natural',
            theme: 'pure-black',
            avatarFPS: 'auto'
        };
        
        this.applyLoadedSettings();
        this.saveSettings();
    }
    
    // Apply loaded settings to state and UI
    applyLoadedSettings() {
        // Update state
        this.stateManager.update({
            'ui.soundsEnabled': this.settingsData.soundsEnabled,
            'ui.animationsEnabled': this.settingsData.animationsEnabled,
            'ui.debugConsoleVisible': this.settingsData.debugConsoleVisible,
            'ui.particleEffectsEnabled': this.settingsData.particleEffectsEnabled,
            'ui.voiceProfile': this.settingsData.voiceProfile,
            'ui.theme': this.settingsData.theme,
            'ui.avatarFPS': this.settingsData.avatarFPS
        }, true);
        
        // Apply settings
        this.applyTheme(this.settingsData.theme);
        this.applyAnimationSettings(this.settingsData.animationsEnabled);
        this.applyParticleSettings(this.settingsData.particleEffectsEnabled);
        this.applyVoiceProfile(this.settingsData.voiceProfile);
        this.applyFPSSettings(this.settingsData.avatarFPS);
        
        // Don't auto-show debug console on load
        if (this.settingsData.debugConsoleVisible) {
            this.settingsData.debugConsoleVisible = false;
            this.stateManager.set('ui.debugConsoleVisible', false, true);
        }
    }
    
    // Save settings to localStorage
    saveSettings() {
        try {
            this.settingsData = {
                soundsEnabled: this.stateManager.get('ui.soundsEnabled'),
                animationsEnabled: this.stateManager.get('ui.animationsEnabled'),
                debugConsoleVisible: this.stateManager.get('ui.debugConsoleVisible'),
                particleEffectsEnabled: this.stateManager.get('ui.particleEffectsEnabled'),
                voiceProfile: this.stateManager.get('ui.voiceProfile'),
                theme: this.stateManager.get('ui.theme'),
                avatarFPS: this.stateManager.get('ui.avatarFPS')
            };
            
            localStorage.setItem('m1k3-settings', JSON.stringify(this.settingsData));
            console.log('⚙️ Settings saved');
        } catch (error) {
            console.error('⚙️ Error saving settings:', error);
        }
    }
    
    // Restore interface from state
    restoreFromState() {
        // Sync all toggles
        this.syncToggle('voice', this.stateManager.get('ui.soundsEnabled'));
        this.syncToggle('animations', this.stateManager.get('ui.animationsEnabled'));
        this.syncToggle('debug', this.stateManager.get('ui.debugConsoleVisible'));
        this.syncToggle('particles', this.stateManager.get('ui.particleEffectsEnabled'));
        
        // Sync all selectors
        this.syncSelector('voiceProfile', this.stateManager.get('ui.voiceProfile'));
        this.syncSelector('theme', this.stateManager.get('ui.theme'));
        this.syncSelector('fps', this.stateManager.get('ui.avatarFPS'));
    }
    
    // Reset settings to defaults
    resetSettings() {
        if (confirm('Reset all settings to defaults? This cannot be undone.')) {
            this.setDefaultSettings();
            this.restoreFromState();
            console.log('⚙️ Settings reset to defaults');
        }
    }
    
    // Export settings
    exportSettings() {
        const exportData = {
            version: '1.0',
            timestamp: new Date().toISOString(),
            settings: this.settingsData,
            state: {
                theme: this.stateManager.get('ui.theme'),
                soundsEnabled: this.stateManager.get('ui.soundsEnabled'),
                animationsEnabled: this.stateManager.get('ui.animationsEnabled')
            }
        };
        
        const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `m1k3-settings-${Date.now()}.json`;
        a.click();
        URL.revokeObjectURL(url);
        
        console.log('⚙️ Settings exported');
    }
    
    // Import settings
    importSettings(file) {
        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const data = JSON.parse(e.target.result);
                if (data.settings && data.version) {
                    this.settingsData = data.settings;
                    this.applyLoadedSettings();
                    this.restoreFromState();
                    this.saveSettings();
                    console.log('⚙️ Settings imported successfully');
                } else {
                    throw new Error('Invalid settings file format');
                }
            } catch (error) {
                console.error('⚙️ Error importing settings:', error);
                alert('Error importing settings: Invalid file format');
            }
        };
        reader.readAsText(file);
    }
    
    // Get current settings summary
    getSettingsSummary() {
        return {
            voice: {
                enabled: this.stateManager.get('ui.soundsEnabled'),
                profile: this.stateManager.get('ui.voiceProfile')
            },
            interface: {
                theme: this.stateManager.get('ui.theme'),
                animations: this.stateManager.get('ui.animationsEnabled'),
                debugConsole: this.stateManager.get('ui.debugConsoleVisible')
            },
            performance: {
                particles: this.stateManager.get('ui.particleEffectsEnabled'),
                avatarFPS: this.stateManager.get('ui.avatarFPS')
            }
        };
    }
    
    // Called when view is shown
    async onShow() {
        // Refresh settings display
        this.restoreFromState();
    }
    
    // Called when view is hidden
    async onHide() {
        // Save current settings
        this.saveSettings();
    }
    
    // Cleanup resources
    cleanup() {
        // Save settings on cleanup
        this.saveSettings();
    }
}

// Export as global
if (typeof window !== 'undefined') {
    window.SettingsController = SettingsController;
}

// Module exports
if (typeof module !== 'undefined' && module.exports) {
    module.exports = SettingsController;
}