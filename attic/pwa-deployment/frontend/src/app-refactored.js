/**
 * M1K3 PWA Main Application (Refactored)
 * Orchestrates device detection, model loading, and chat interface with centralized config and error handling
 */

import M1K3Config from './config.js';
import { errorBoundary } from './error-handler.js';

class M1K3App {
    constructor() {
        this.config = M1K3Config;
        this.deviceDetector = new DeviceDetector();
        this.modelLoader = new ModelLoader();
        this.chatInterface = null;
        
        this.state = 'initializing';
        this.retryCount = 0;
        this.maxRetries = 3;
        
        this.setupEventListeners();
        this.setupErrorRecoveryHandlers();
    }

    async initialize() {
        console.log('🚀 M1K3 PWA Starting...');
        
        try {
            // Show loading screen
            this.showScreen('loading');
            this.updateProgress(0, 'Initializing M1K3...');
            
            // Detect device capabilities
            this.updateProgress(20, 'Analyzing device capabilities...');
            const capabilities = await this.deviceDetector.detectCapabilities();
            
            // Display device info
            this.deviceDetector.displayCapabilities();
            this.showDeviceInfo();
            
            // Select optimal model using centralized config
            this.updateProgress(40, 'Selecting optimal AI model...');
            const selectedModel = this.config.getRecommendedModel(capabilities);
            this.deviceDetector.displaySelectedModel();
            
            // Initialize model loader
            this.updateProgress(60, 'Preparing AI engine...');
            await this.modelLoader.initialize();
            
            // Load the selected model with fallbacks
            await this.loadModelWithFallbacks(selectedModel);
            
            // Initialize chat interface
            this.updateProgress(95, 'Setting up interface...');
            this.chatInterface = new ChatInterface(this.modelLoader);
            
            // Setup model loader callbacks
            this.setupModelLoaderCallbacks();
            
            // Show main app
            this.updateProgress(100, 'Ready!');
            setTimeout(() => this.showScreen('app'), 500);
            
            console.log('✅ M1K3 PWA Ready!');
            this.state = 'ready';
            
        } catch (error) {
            console.error('❌ Initialization failed:', error);
            const errorInfo = errorBoundary.handleError(error, 'initialization');
            this.handleInitializationError(errorInfo);
        }
    }

    async loadModelWithFallbacks(selectedModel) {
        const fallbackChain = this.buildFallbackChain(selectedModel);
        
        this.updateProgress(70, `Loading ${selectedModel.displayName} model...`);
        
        // Show loading indicators
        window.loadingIndicators.showLoading(
            `Loading ${selectedModel.displayName}`,
            'Preparing your local AI assistant...'
        );
        
        try {
            const loadedModel = await this.modelLoader.loadWithFallback(
                fallbackChain,
                (progress) => {
                    const baseProgress = 70;
                    const progressRange = 20; // 70-90%
                    const adjustedProgress = baseProgress + (progress.percentage * progressRange / 100);
                    this.updateProgress(adjustedProgress, progress.message);
                    
                    // Update loading overlay
                    window.loadingIndicators.updateProgress(adjustedProgress, progress.message);
                }
            );
            
            console.log(`✅ Successfully loaded: ${loadedModel}`);
            
            // Show success toast
            window.loadingIndicators.showModelLoadingStatus(
                selectedModel.displayName, 
                'success', 
                'Ready for conversations'
            );
            
            // Update UI with loaded model info
            if (this.chatInterface) {
                this.chatInterface.updateModelInfo(this.modelLoader.modelInfo);
            }
            
        } catch (error) {
            console.error('❌ All model loading attempts failed:', error);
            
            // Handle error through error boundary
            errorBoundary.handleError(error, 'model_loading', {
                selectedModel: selectedModel.name,
                fallbackChain: fallbackChain
            });
            
            // Show fallback toast
            window.loadingIndicators.showModelLoadingStatus(
                'AI Assistant', 
                'fallback', 
                'Using demo mode with RAG knowledge base'
            );
            
            // Don't throw error - let fallback mode work
            console.log('🎭 Continuing with demo mode...');
        } finally {
            // Hide loading overlay
            setTimeout(() => {
                window.loadingIndicators.hideLoading();
            }, 500);
        }
    }

    buildFallbackChain(selectedModel) {
        // Build a fallback chain based on the selected model
        const allModelTiers = Object.keys(this.config.MODEL_TIERS);
        const selectedTier = selectedModel.name.toLowerCase().includes('tiny') ? 'tiny' : 
                           selectedModel.name.toLowerCase().includes('small') ? 'small' : 'medium';
        const chain = [selectedTier];
        
        // Add fallbacks in order of increasing compatibility (smaller models first)
        const fallbackOrder = ['tiny', 'small', 'medium'];
        for (const tier of fallbackOrder) {
            if (tier !== selectedTier && !chain.includes(tier)) {
                chain.push(tier);
            }
        }
        
        console.log('🔄 Fallback chain:', chain);
        return chain;
    }

    setupModelLoaderCallbacks() {
        this.modelLoader.onLoadingStateChange((state, data) => {
            switch (state) {
                case 'loading':
                    if (this.chatInterface) {
                        this.chatInterface.setProcessingState(true);
                    }
                    break;
                    
                case 'ready':
                    if (this.chatInterface) {
                        this.chatInterface.setProcessingState(false);
                        this.chatInterface.updateModelInfo(data.info);
                    }
                    break;
                    
                case 'error':
                    if (this.chatInterface) {
                        this.chatInterface.setProcessingState(false);
                        this.chatInterface.showError('Model error: ' + data.error.message);
                    }
                    break;
            }
        });
    }

    setupErrorRecoveryHandlers() {
        // Listen for error recovery events
        window.addEventListener('m1k3-enable-fallback', () => {
            this.useFallbackMode();
        });
        
        window.addEventListener('m1k3-force-smaller-model', () => {
            this.forceSmallerModel();
        });
        
        window.addEventListener('m1k3-offline-mode', (event) => {
            this.enableOfflineMode(event.detail.enabled);
        });
        
        window.addEventListener('m1k3-retry-with-fallback', () => {
            this.retryWithFallback();
        });
    }

    setupEventListeners() {
        // Retry button
        const retryBtn = document.getElementById('retry-btn');
        if (retryBtn) {
            retryBtn.addEventListener('click', () => this.retry());
        }
        
        // Fallback button
        const fallbackBtn = document.getElementById('fallback-btn');
        if (fallbackBtn) {
            fallbackBtn.addEventListener('click', () => this.useFallbackMode());
        }
        
        // Settings modal
        const settingsBtn = document.getElementById('settings-btn');
        const settingsModal = document.getElementById('settings-modal');
        const settingsClose = document.getElementById('settings-close');
        
        if (settingsBtn && settingsModal) {
            settingsBtn.addEventListener('click', () => {
                settingsModal.style.display = 'flex';
            });
        }
        
        if (settingsClose && settingsModal) {
            settingsClose.addEventListener('click', () => {
                settingsModal.style.display = 'none';
            });
        }
        
        // Close modal on background click
        if (settingsModal) {
            settingsModal.addEventListener('click', (e) => {
                if (e.target === settingsModal) {
                    settingsModal.style.display = 'none';
                }
            });
        }
        
        // Info button
        const infoBtn = document.getElementById('info-btn');
        if (infoBtn) {
            infoBtn.addEventListener('click', () => this.showInfo());
        }
        
        // Settings controls
        this.setupSettingsControls();
    }

    setupSettingsControls() {
        // Temperature slider
        const tempSlider = document.getElementById('temperature-slider');
        const tempValue = document.getElementById('temperature-value');
        
        if (tempSlider && tempValue) {
            tempSlider.addEventListener('input', (e) => {
                const value = (e.target.value / 100).toFixed(1);
                tempValue.textContent = value;
                
                // Update config
                this.config.updateConfig('GENERATION_PARAMS', 'temperature', parseFloat(value));
            });
        }
        
        // Max tokens slider
        const tokensSlider = document.getElementById('max-tokens-slider');
        const tokensValue = document.getElementById('max-tokens-value');
        
        if (tokensSlider && tokensValue) {
            tokensSlider.addEventListener('input', (e) => {
                const value = parseInt(e.target.value);
                tokensValue.textContent = `${value} tokens`;
                
                // Update config
                this.config.updateConfig('GENERATION_PARAMS', 'max_new_tokens', value);
            });
        }
    }

    showScreen(screenName) {
        // Hide all screens
        const screens = ['loading-screen', 'app', 'error-screen'];
        screens.forEach(screen => {
            const element = document.getElementById(screen);
            if (element) {
                element.style.display = 'none';
            }
        });
        
        // Show selected screen
        const selectedScreen = document.getElementById(screenName);
        if (selectedScreen) {
            selectedScreen.style.display = 'flex';
        }
    }

    updateProgress(percentage, message) {
        const progressFill = document.getElementById('progress-fill');
        const loadingText = document.getElementById('loading-text');
        
        if (progressFill) {
            progressFill.style.width = `${percentage}%`;
        }
        
        if (loadingText) {
            loadingText.textContent = message;
        }
        
        console.log(`📊 ${percentage}%: ${message}`);
    }

    showDeviceInfo() {
        const deviceInfo = document.getElementById('device-info');
        if (deviceInfo) {
            deviceInfo.style.display = 'block';
        }
    }

    handleInitializationError(errorInfo) {
        this.state = 'error';
        
        // Show error screen
        this.showScreen('error-screen');
        
        // Update error message using centralized error info
        const errorMessage = document.getElementById('error-message');
        if (errorMessage) {
            errorMessage.textContent = errorInfo.userMessage || 'An unexpected error occurred.';
        }
        
        // Show technical details if debug mode
        if (this.config.isDebugEnabled()) {
            const debugInfo = document.getElementById('debug-info');
            if (debugInfo) {
                debugInfo.style.display = 'block';
                debugInfo.textContent = `Debug: ${errorInfo.technicalMessage}`;
            }
        }
    }

    async retry() {
        if (this.retryCount >= this.maxRetries) {
            alert('Maximum retry attempts reached. Please refresh the page.');
            return;
        }
        
        this.retryCount++;
        console.log(`🔄 Retry attempt ${this.retryCount}/${this.maxRetries}`);
        
        // Reset state
        this.modelLoader.dispose();
        this.chatInterface = null;
        
        // Try again
        await this.initialize();
    }

    useFallbackMode() {
        console.log('🔄 Using fallback mode...');
        
        // Create a minimal chat interface without AI model
        this.showScreen('app');
        this.chatInterface = new ChatInterface({
            getModelStatus: () => ({ hasModel: true }),
            runInference: async (text) => {
                // Simple fallback responses using RAG engine if available
                if (window.ragEngine) {
                    return await window.ragEngine.search(text) || 
                           "I'm running in fallback mode with knowledge base search.";
                }
                return "I'm running in fallback mode. While I can't provide AI responses, I can confirm that your message was received. For full functionality, please try refreshing the page.";
            }
        });
        
        // Update model info
        this.chatInterface.updateModelInfo({
            name: 'Fallback Mode',
            tier: 'minimal',
            displayName: 'RAG Knowledge Base',
            features: ['keyword-search', 'knowledge-base']
        });
    }

    showInfo() {
        const capabilities = this.deviceDetector.capabilities;
        const modelInfo = this.modelLoader.modelInfo;
        
        let info = '🤖 M1K3 - Local AI Assistant\\n\\n';
        info += '🔒 Privacy: All processing happens locally in your browser\\n';
        info += '📱 No data is sent to external servers\\n\\n';
        
        if (capabilities) {
            info += `💾 Memory: ${capabilities.memory}GB\\n`;
            info += `⚡ WebGPU: ${capabilities.webgpu.supported ? 'Available' : 'Not Available'}\\n`;
            info += `📱 Platform: ${capabilities.platform.mobile ? 'Mobile' : 'Desktop'}\\n\\n`;
        }
        
        if (modelInfo) {
            info += `🧠 Model: ${modelInfo.displayName || modelInfo.name}\\n`;
            info += `📊 Size: ${modelInfo.size_mb}MB\\n`;
            info += `🎯 Tier: ${modelInfo.tier}\\n`;
        }
        
        // Add debug info if enabled
        if (this.config.isDebugEnabled()) {
            const errorStats = errorBoundary.getErrorStats();
            info += `\\n🛠️ Debug Info:\\n`;
            info += `Total Errors: ${errorStats.totalErrors}\\n`;
            info += `Config Version: ${this.config.MODEL_TIERS.tiny.name}\\n`;
            info += `Version: PWA v2.0\\n`;
        }
        
        alert(info);
    }

    // New recovery methods
    forceSmallerModel() {
        const currentTier = this.modelLoader.modelInfo?.tier || 'medium';
        const tiers = ['tiny', 'small', 'medium'];
        const currentIndex = tiers.indexOf(currentTier);
        
        if (currentIndex > 0) {
            const smallerTier = tiers[currentIndex - 1];
            const smallerModel = this.config.getModelConfig(smallerTier);
            
            console.log(`🔄 Forcing smaller model: ${smallerModel.displayName}`);
            this.loadModelWithFallbacks(smallerModel);
        }
    }
    
    enableOfflineMode(enabled) {
        console.log(`📴 Offline mode: ${enabled ? 'enabled' : 'disabled'}`);
        // Update UI to show offline status
        const offlineIndicator = document.getElementById('offline-indicator');
        if (offlineIndicator) {
            offlineIndicator.style.display = enabled ? 'block' : 'none';
        }
        
        // Update config
        this.config.updateConfig('PWA_CONFIG', 'offline', { enabled });
    }
    
    retryWithFallback() {
        console.log('🔄 Retrying with fallback strategy...');
        this.retryCount = 0; // Reset retry count
        this.useFallbackMode();
    }
}

// Initialize the app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    const app = new M1K3App();
    
    // Enhanced error handling for initialization
    app.initialize().catch(error => {
        console.error('App initialization failed:', error);
        errorBoundary.handleError(error, 'app_initialization');
    });
    
    // Make app globally available
    window.app = app;
});

// Export for debugging and module use
export default M1K3App;
window.M1K3App = M1K3App;