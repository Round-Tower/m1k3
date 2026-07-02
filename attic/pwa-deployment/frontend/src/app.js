/**
 * M1K3 PWA Main Application
 * Orchestrates device detection, model loading, and chat interface
 */

class M1K3App {
    constructor() {
        this.deviceDetector = new DeviceDetector();
        this.modelLoader = new ModelLoader();
        this.chatInterface = null;
        
        this.state = 'initializing';
        this.retryCount = 0;
        this.maxRetries = 3;
        
        this.setupEventListeners();
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
            
            // Select optimal model
            this.updateProgress(40, 'Selecting optimal AI model...');
            const selectedModel = this.deviceDetector.selectOptimalModel(capabilities);
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
            this.handleInitializationError(error);
        }
    }

    async loadModelWithFallbacks(selectedModel) {
        const fallbackChain = this.buildFallbackChain(selectedModel);
        
        this.updateProgress(70, `Loading ${selectedModel.name} model...`);
        
        // Show loading indicators
        window.loadingIndicators.showLoading(
            `Loading ${selectedModel.name}`,
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
                selectedModel.name, 
                'success', 
                'Ready for conversations'
            );
            
            // Update UI with loaded model info
            if (this.chatInterface) {
                this.chatInterface.updateModelInfo(this.modelLoader.modelInfo);
            }
            
        } catch (error) {
            console.error('❌ All model loading attempts failed:', error);
            
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
        const allModels = ['tiny', 'small', 'medium'];
        const chain = [selectedModel.name];
        
        // Add fallbacks in order of increasing compatibility
        for (const model of allModels) {
            if (model !== selectedModel.name && !chain.includes(model)) {
                chain.push(model);
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
            });
        }
        
        // Max tokens slider
        const tokensSlider = document.getElementById('max-tokens-slider');
        const tokensValue = document.getElementById('max-tokens-value');
        
        if (tokensSlider && tokensValue) {
            tokensSlider.addEventListener('input', (e) => {
                tokensValue.textContent = `${e.target.value} tokens`;
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

    handleInitializationError(error) {
        this.state = 'error';
        
        // Show error screen
        this.showScreen('error-screen');
        
        // Update error message
        const errorMessage = document.getElementById('error-message');
        if (errorMessage) {
            let message = 'An unexpected error occurred.';
            
            if (error.message.includes('WebAssembly')) {
                message = 'Your browser doesn\'t support WebAssembly, which is required for AI inference.';
            } else if (error.message.includes('model')) {
                message = 'Failed to load the AI model. This may be due to insufficient device resources or network issues.';
            } else if (error.message.includes('ONNX')) {
                message = 'Failed to initialize the AI runtime. Please check your internet connection and try again.';
            }
            
            errorMessage.textContent = message;
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
                // Simple fallback responses
                return "I'm running in fallback mode. While I can't provide AI responses, I can confirm that your message was received. For full functionality, please try refreshing the page.";
            }
        });
        
        // Update model info
        this.chatInterface.updateModelInfo({
            name: 'Fallback Mode',
            tier: 'minimal'
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
            info += `🧠 Model: ${modelInfo.name}\\n`;
            info += `📊 Size: ${modelInfo.size_mb}MB\\n`;
        }
        
        alert(info);
    }
}

// Initialize the app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    const app = new M1K3App();
    app.initialize().catch(error => {
        console.error('App initialization failed:', error);
    });
});

// Export for debugging
window.M1K3App = M1K3App;