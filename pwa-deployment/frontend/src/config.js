/**
 * M1K3 PWA Centralized Configuration System
 * Provides consistent configuration across all components
 */

export class M1K3Config {
    // Model Configuration
    static MODEL_TIERS = {
        tiny: {
            name: 'TinyLlama-270M',
            displayName: 'TinyLlama (Mobile)',
            minMemory: 2,  // GB
            maxMemory: 4,
            size_mb: 270,
            description: 'Optimized for mobile devices and low-power hardware',
            modelPath: './models/m1k3-tiny/',
            features: ['basic-chat', 'simple-qa'],
            performance: 'fast',
            quality: 'good'
        },
        small: {
            name: 'M1K3-Small',
            displayName: 'M1K3 Small (Balanced)',
            minMemory: 4,
            maxMemory: 8,
            size_mb: 800,
            description: 'Balanced model for general conversations and reasoning',
            modelPath: './models/m1k3-small/',
            features: ['conversation', 'reasoning', 'qa', 'summarization'],
            performance: 'balanced',
            quality: 'high'
        },
        medium: {
            name: 'M1K3-Medium',
            displayName: 'M1K3 Medium (Advanced)',
            minMemory: 8,
            maxMemory: 16,
            size_mb: 1600,
            description: 'Advanced model for complex reasoning and code generation',
            modelPath: './models/m1k3-medium/',
            features: ['advanced-reasoning', 'code-generation', 'analysis', 'creative-writing'],
            performance: 'slower',
            quality: 'excellent'
        }
    };

    // Generation Parameters by Model Tier
    static GENERATION_PARAMS = {
        tiny: {
            max_new_tokens: 50,
            temperature: 0.7,
            top_p: 0.9,
            repetition_penalty: 1.1,
            do_sample: true,
            pad_token_id: 0
        },
        small: {
            max_new_tokens: 150,
            temperature: 0.8,
            top_p: 0.9,
            repetition_penalty: 1.1,
            do_sample: true,
            pad_token_id: 0
        },
        medium: {
            max_new_tokens: 300,
            temperature: 0.8,
            top_p: 0.9,
            repetition_penalty: 1.05,
            do_sample: true,
            pad_token_id: 0
        }
    };

    // Device Detection Configuration
    static DEVICE_CONFIG = {
        memoryThresholds: {
            low: 2,      // GB - Use tiny model
            medium: 4,   // GB - Use small model
            high: 8      // GB - Use medium model
        },
        platformDetection: {
            mobile: {
                userAgents: ['Mobile', 'Android', 'iPhone', 'iPad'],
                maxRecommendedModel: 'small'
            },
            desktop: {
                userAgents: ['Windows', 'Macintosh', 'Linux'],
                maxRecommendedModel: 'medium'
            }
        },
        performanceBenchmarks: {
            minimumScore: 100,  // Baseline performance score
            modelSelectionThreshold: 0.8  // Model selection confidence threshold
        }
    };

    // UI Configuration
    static UI_CONFIG = {
        theme: {
            primaryColor: '#2563eb',
            secondaryColor: '#64748b',
            successColor: '#10b981',
            warningColor: '#f59e0b',
            errorColor: '#ef4444'
        },
        animations: {
            fadeInDuration: 300,
            slideInDuration: 200,
            progressUpdateInterval: 50,
            typewriterSpeed: 30
        },
        chat: {
            maxMessageLength: 1000,
            historyLimit: 50,
            autoSaveInterval: 5000,  // ms
            typingIndicatorDelay: 200
        },
        loading: {
            progressUpdateInterval: 100,
            minimumLoadingTime: 1000,  // Show loading for at least 1s
            timeoutDuration: 30000     // 30s timeout
        }
    };

    // PWA Configuration
    static PWA_CONFIG = {
        caching: {
            modelCacheDuration: 30 * 24 * 60 * 60, // 30 days
            apiCacheDuration: 60 * 60,              // 1 hour
            staticCacheDuration: 7 * 24 * 60 * 60,  // 7 days
            maxCacheSize: 2 * 1024 * 1024 * 1024    // 2GB
        },
        notifications: {
            enabled: false,  // Can be enabled later
            permission: 'default'
        },
        offline: {
            fallbackMode: true,
            ragFallback: true,
            offlineMessage: 'You are currently offline. Using cached models and knowledge base.'
        }
    };

    // RAG Configuration
    static RAG_CONFIG = {
        knowledgeBase: {
            maxChunks: 5,
            chunkSize: 500,
            similarity_threshold: 0.7,
            useSemanticSearch: true
        },
        embeddings: {
            model: 'sentence-transformers/all-MiniLM-L6-v2',
            dimensions: 384,
            batchSize: 8
        },
        fallback: {
            keywordSearchEnabled: true,
            maxKeywords: 10,
            minMatchScore: 0.3
        }
    };

    // Development and Debug Configuration
    static DEBUG_CONFIG = {
        enabled: window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1',
        logLevel: 'info', // 'debug', 'info', 'warn', 'error'
        showPerformanceMetrics: true,
        enableDevTools: true,
        mockMode: false
    };

    // API Configuration
    static API_CONFIG = {
        baseUrl: '',  // Same origin
        endpoints: {
            models: '/api/models',
            health: '/api/health',
            device: '/api/device',
            embeddings: '/api/embeddings'
        },
        timeout: 10000,  // 10s
        retries: 3,
        retryDelay: 1000  // 1s
    };

    // Security Configuration
    static SECURITY_CONFIG = {
        csp: {
            enabled: true,
            allowInline: false,
            allowEval: false
        },
        sanitization: {
            enabled: true,
            allowedTags: ['p', 'br', 'strong', 'em', 'code', 'pre'],
            maxInputLength: 10000
        },
        rateLimiting: {
            maxRequestsPerMinute: 60,
            maxRequestsPerHour: 1000
        }
    };

    /**
     * Get model configuration by tier name
     */
    static getModelConfig(tierName) {
        return this.MODEL_TIERS[tierName] || this.MODEL_TIERS.tiny;
    }

    /**
     * Get generation parameters for model tier
     */
    static getGenerationParams(tierName) {
        return { ...this.GENERATION_PARAMS[tierName] } || { ...this.GENERATION_PARAMS.tiny };
    }

    /**
     * Get device-appropriate model recommendation
     */
    static getRecommendedModel(capabilities) {
        const { memory, platform, webgpu } = capabilities;
        
        // Start with memory-based recommendation
        let recommendedTier = 'tiny';
        
        if (memory >= this.DEVICE_CONFIG.memoryThresholds.high) {
            recommendedTier = 'medium';
        } else if (memory >= this.DEVICE_CONFIG.memoryThresholds.medium) {
            recommendedTier = 'small';
        }
        
        // Platform-based adjustments
        if (platform.mobile) {
            const maxMobile = this.DEVICE_CONFIG.platformDetection.mobile.maxRecommendedModel;
            const tiers = Object.keys(this.MODEL_TIERS);
            const currentIndex = tiers.indexOf(recommendedTier);
            const maxIndex = tiers.indexOf(maxMobile);
            
            if (currentIndex > maxIndex) {
                recommendedTier = maxMobile;
            }
        }
        
        return this.getModelConfig(recommendedTier);
    }

    /**
     * Get UI theme configuration
     */
    static getTheme() {
        return { ...this.UI_CONFIG.theme };
    }

    /**
     * Get animation configuration
     */
    static getAnimationConfig() {
        return { ...this.UI_CONFIG.animations };
    }

    /**
     * Get PWA cache configuration
     */
    static getCacheConfig() {
        return { ...this.PWA_CONFIG.caching };
    }

    /**
     * Check if debugging is enabled
     */
    static isDebugEnabled() {
        return this.DEBUG_CONFIG.enabled;
    }

    /**
     * Get log level
     */
    static getLogLevel() {
        return this.DEBUG_CONFIG.logLevel;
    }

    /**
     * Get API endpoint URL
     */
    static getApiUrl(endpoint) {
        return this.API_CONFIG.baseUrl + this.API_CONFIG.endpoints[endpoint];
    }

    /**
     * Update configuration at runtime (for settings)
     */
    static updateConfig(section, key, value) {
        if (this[section] && this[section][key] !== undefined) {
            this[section][key] = value;
            
            // Persist to localStorage for user preferences
            const userPrefs = JSON.parse(localStorage.getItem('m1k3-preferences') || '{}');
            userPrefs[`${section}.${key}`] = value;
            localStorage.setItem('m1k3-preferences', JSON.stringify(userPrefs));
            
            console.log(`🔧 Configuration updated: ${section}.${key} = ${value}`);
        }
    }

    /**
     * Load user preferences from localStorage
     */
    static loadUserPreferences() {
        try {
            const userPrefs = JSON.parse(localStorage.getItem('m1k3-preferences') || '{}');
            
            for (const [key, value] of Object.entries(userPrefs)) {
                const [section, configKey] = key.split('.');
                if (this[section] && this[section][configKey] !== undefined) {
                    this[section][configKey] = value;
                }
            }
            
            console.log('✅ User preferences loaded');
        } catch (error) {
            console.warn('⚠️ Failed to load user preferences:', error);
        }
    }

    /**
     * Initialize configuration system
     */
    static initialize() {
        this.loadUserPreferences();
        
        // Set debug mode based on environment
        if (this.DEBUG_CONFIG.enabled) {
            console.log('🛠️ M1K3 Debug mode enabled');
            window.M1K3Config = this; // Expose for debugging
        }
        
        console.log('⚙️ M1K3 Configuration initialized');
    }
}

// Auto-initialize when module loads
M1K3Config.initialize();

// Default export
export default M1K3Config;