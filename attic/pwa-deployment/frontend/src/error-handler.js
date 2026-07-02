/**
 * M1K3 PWA Global Error Handler
 * Centralized error handling with user feedback and recovery strategies
 */

import M1K3Config from './config.js';

export class PWAErrorBoundary {
    constructor() {
        this.errorCounts = new Map();
        this.setupGlobalErrorHandling();
    }

    setupGlobalErrorHandling() {
        // Handle unhandled promise rejections
        window.addEventListener('unhandledrejection', (event) => {
            console.error('Unhandled promise rejection:', event.reason);
            this.handleError(event.reason, 'unhandledrejection', { 
                promise: event.promise 
            });
            event.preventDefault();
        });

        // Handle JavaScript errors
        window.addEventListener('error', (event) => {
            console.error('JavaScript error:', event.error);
            this.handleError(event.error, 'javascript', {
                filename: event.filename,
                lineno: event.lineno,
                colno: event.colno
            });
        });

        // Handle WebAssembly errors
        if (typeof WebAssembly !== 'undefined') {
            const originalInstantiate = WebAssembly.instantiate;
            WebAssembly.instantiate = async (...args) => {
                try {
                    return await originalInstantiate.apply(WebAssembly, args);
                } catch (error) {
                    this.handleError(error, 'webassembly', { args });
                    throw error;
                }
            };
        }
    }

    /**
     * Main error handling function
     */
    handleError(error, context, metadata = {}) {
        const errorInfo = this.analyzeError(error, context, metadata);
        
        // Increment error count for this type
        const errorKey = errorInfo.type;
        this.errorCounts.set(errorKey, (this.errorCounts.get(errorKey) || 0) + 1);

        // Log error details if debug enabled
        if (M1K3Config.isDebugEnabled()) {
            console.group(`❌ Error in ${context}`);
            console.error('Error:', error);
            console.log('Error Info:', errorInfo);
            console.log('Metadata:', metadata);
            console.groupEnd();
        }

        // Show user-friendly error message
        this.showUserError(errorInfo);

        // Attempt recovery if possible
        this.attemptRecovery(errorInfo, metadata);

        // Return error info for component handling
        return errorInfo;
    }

    /**
     * Analyze error and categorize it
     */
    analyzeError(error, context, metadata = {}) {
        const errorMessage = error?.message || error?.toString() || 'Unknown error';
        const errorStack = error?.stack || '';

        let errorInfo = {
            type: 'unknown',
            severity: 'medium',
            userMessage: 'An unexpected error occurred',
            technicalMessage: errorMessage,
            recoverable: true,
            recovery: null,
            context,
            timestamp: Date.now(),
            count: 1
        };

        // Categorize based on error message and context
        if (errorMessage.includes('WebAssembly')) {
            errorInfo = {
                ...errorInfo,
                type: 'webassembly',
                severity: 'high',
                userMessage: 'Your browser doesn\'t support WebAssembly, which is required for AI inference.',
                recoverable: false,
                recovery: 'use_fallback_mode'
            };
        } else if (errorMessage.includes('ONNX') || errorMessage.includes('model')) {
            errorInfo = {
                ...errorInfo,
                type: 'model_loading',
                severity: 'high',
                userMessage: 'Failed to load the AI model. This may be due to network issues or device limitations.',
                recoverable: true,
                recovery: 'try_smaller_model'
            };
        } else if (errorMessage.includes('network') || errorMessage.includes('fetch')) {
            errorInfo = {
                ...errorInfo,
                type: 'network',
                severity: 'medium',
                userMessage: 'Network connection error. Some features may not be available.',
                recoverable: true,
                recovery: 'enable_offline_mode'
            };
        } else if (errorMessage.includes('memory') || errorMessage.includes('allocation')) {
            errorInfo = {
                ...errorInfo,
                type: 'memory',
                severity: 'high',
                userMessage: 'Insufficient memory to run the AI model. Try using a smaller model.',
                recoverable: true,
                recovery: 'use_smaller_model'
            };
        } else if (errorMessage.includes('quota') || errorMessage.includes('storage')) {
            errorInfo = {
                ...errorInfo,
                type: 'storage',
                severity: 'medium',
                userMessage: 'Storage quota exceeded. Please clear some space and try again.',
                recoverable: true,
                recovery: 'clear_cache'
            };
        } else if (context === 'initialization') {
            errorInfo = {
                ...errorInfo,
                type: 'initialization',
                severity: 'high',
                userMessage: 'Failed to initialize the application. Please refresh the page.',
                recoverable: true,
                recovery: 'refresh_page'
            };
        } else if (context === 'inference') {
            errorInfo = {
                ...errorInfo,
                type: 'inference',
                severity: 'medium',
                userMessage: 'AI processing error. Please try rephrasing your message.',
                recoverable: true,
                recovery: 'retry_with_fallback'
            };
        }

        // Adjust severity based on error frequency
        const errorCount = this.errorCounts.get(errorInfo.type) || 0;
        if (errorCount > 3) {
            errorInfo.severity = 'critical';
            errorInfo.userMessage += ' (Multiple occurrences detected)';
        }

        return errorInfo;
    }

    /**
     * Show user-friendly error message
     */
    showUserError(errorInfo) {
        const { severity, userMessage, recoverable, recovery } = errorInfo;
        
        // Determine notification style based on severity
        const notificationConfig = {
            low: { type: 'info', duration: 3000 },
            medium: { type: 'warning', duration: 5000 },
            high: { type: 'error', duration: 8000 },
            critical: { type: 'error', duration: 0 } // Persistent
        };

        const config = notificationConfig[severity] || notificationConfig.medium;
        
        // Show error notification
        this.showErrorNotification(userMessage, config, recoverable ? recovery : null);
    }

    /**
     * Display error notification to user
     */
    showErrorNotification(message, config, recovery) {
        // Create error notification element
        const notification = document.createElement('div');
        notification.className = `error-notification error-${config.type}`;
        notification.innerHTML = `
            <div class="error-content">
                <div class="error-icon">⚠️</div>
                <div class="error-message">${message}</div>
                ${recovery ? `<button class="error-action-btn" data-recovery="${recovery}">Try Fix</button>` : ''}
                <button class="error-close-btn">×</button>
            </div>
        `;

        // Add to page
        document.body.appendChild(notification);

        // Setup event listeners
        const closeBtn = notification.querySelector('.error-close-btn');
        const actionBtn = notification.querySelector('.error-action-btn');

        closeBtn.addEventListener('click', () => {
            this.hideErrorNotification(notification);
        });

        if (actionBtn && recovery) {
            actionBtn.addEventListener('click', () => {
                this.executeRecovery(recovery);
                this.hideErrorNotification(notification);
            });
        }

        // Auto-hide after duration (unless persistent)
        if (config.duration > 0) {
            setTimeout(() => {
                if (document.body.contains(notification)) {
                    this.hideErrorNotification(notification);
                }
            }, config.duration);
        }

        // Add CSS if not already present
        this.ensureErrorStyles();
    }

    /**
     * Hide error notification
     */
    hideErrorNotification(notification) {
        notification.style.opacity = '0';
        notification.style.transform = 'translateX(100%)';
        setTimeout(() => {
            if (document.body.contains(notification)) {
                document.body.removeChild(notification);
            }
        }, 300);
    }

    /**
     * Attempt automatic error recovery
     */
    attemptRecovery(errorInfo, metadata) {
        if (!errorInfo.recoverable || !errorInfo.recovery) {
            return false;
        }

        console.log(`🔄 Attempting recovery: ${errorInfo.recovery}`);
        
        // Delay recovery to avoid immediate re-triggering
        setTimeout(() => {
            this.executeRecovery(errorInfo.recovery, metadata);
        }, 1000);

        return true;
    }

    /**
     * Execute specific recovery strategy
     */
    executeRecovery(strategy, metadata = {}) {
        switch (strategy) {
            case 'use_fallback_mode':
                this.enableFallbackMode();
                break;
                
            case 'try_smaller_model':
                this.suggestSmallerModel();
                break;
                
            case 'use_smaller_model':
                this.forceSmallerModel();
                break;
                
            case 'enable_offline_mode':
                this.enableOfflineMode();
                break;
                
            case 'clear_cache':
                this.clearApplicationCache();
                break;
                
            case 'refresh_page':
                this.offerPageRefresh();
                break;
                
            case 'retry_with_fallback':
                this.retryWithFallback(metadata);
                break;
                
            default:
                console.warn(`Unknown recovery strategy: ${strategy}`);
        }
    }

    enableFallbackMode() {
        console.log('🎭 Enabling fallback mode...');
        // Signal to app to use fallback mode
        window.dispatchEvent(new CustomEvent('m1k3-enable-fallback', {
            detail: { reason: 'error_recovery' }
        }));
    }

    suggestSmallerModel() {
        const currentModel = window.app?.modelLoader?.modelInfo?.tier || 'unknown';
        const modelTiers = Object.keys(M1K3Config.MODEL_TIERS);
        const currentIndex = modelTiers.indexOf(currentModel);
        
        if (currentIndex > 0) {
            const smallerTier = modelTiers[currentIndex - 1];
            const smallerModel = M1K3Config.getModelConfig(smallerTier);
            
            this.showErrorNotification(
                `Consider using ${smallerModel.displayName} (${smallerModel.size_mb}MB) for better performance.`,
                { type: 'info', duration: 8000 },
                'use_smaller_model'
            );
        }
    }

    forceSmallerModel() {
        console.log('📉 Forcing smaller model...');
        window.dispatchEvent(new CustomEvent('m1k3-force-smaller-model', {
            detail: { reason: 'error_recovery' }
        }));
    }

    enableOfflineMode() {
        console.log('📴 Enabling offline mode...');
        window.dispatchEvent(new CustomEvent('m1k3-offline-mode', {
            detail: { enabled: true }
        }));
    }

    async clearApplicationCache() {
        console.log('🧹 Clearing application cache...');
        
        try {
            // Clear localStorage
            localStorage.clear();
            
            // Clear service worker cache
            if ('caches' in window) {
                const cacheNames = await caches.keys();
                await Promise.all(
                    cacheNames.map(cacheName => caches.delete(cacheName))
                );
            }
            
            this.showErrorNotification(
                'Application cache cleared. Please refresh the page.',
                { type: 'success', duration: 3000 }
            );
        } catch (error) {
            console.error('Failed to clear cache:', error);
        }
    }

    offerPageRefresh() {
        const shouldRefresh = confirm('The application encountered an error. Refresh the page to try again?');
        if (shouldRefresh) {
            window.location.reload();
        }
    }

    retryWithFallback(metadata) {
        console.log('🔄 Retrying with fallback...');
        // This would trigger a retry in the component that encountered the error
        window.dispatchEvent(new CustomEvent('m1k3-retry-with-fallback', {
            detail: { metadata }
        }));
    }

    /**
     * Get error statistics for debugging
     */
    getErrorStats() {
        return {
            errors: Object.fromEntries(this.errorCounts),
            totalErrors: Array.from(this.errorCounts.values()).reduce((a, b) => a + b, 0),
            timestamp: Date.now()
        };
    }

    /**
     * Ensure error notification styles are loaded
     */
    ensureErrorStyles() {
        if (document.getElementById('error-notification-styles')) {
            return;
        }

        const styles = document.createElement('style');
        styles.id = 'error-notification-styles';
        styles.textContent = `
            .error-notification {
                position: fixed;
                top: 20px;
                right: 20px;
                z-index: 10000;
                max-width: 400px;
                padding: 16px;
                border-radius: 8px;
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
                transform: translateX(100%);
                transition: all 0.3s ease;
                animation: slideIn 0.3s ease forwards;
            }

            .error-notification.error-info {
                background: #f0f9ff;
                border-left: 4px solid #0ea5e9;
                color: #0c4a6e;
            }

            .error-notification.error-warning {
                background: #fffbeb;
                border-left: 4px solid #f59e0b;
                color: #92400e;
            }

            .error-notification.error-error {
                background: #fef2f2;
                border-left: 4px solid #ef4444;
                color: #991b1b;
            }

            .error-notification.error-success {
                background: #f0fdf4;
                border-left: 4px solid #10b981;
                color: #166534;
            }

            .error-content {
                display: flex;
                align-items: center;
                gap: 12px;
            }

            .error-icon {
                font-size: 20px;
                flex-shrink: 0;
            }

            .error-message {
                flex: 1;
                font-size: 14px;
                line-height: 1.4;
            }

            .error-action-btn, .error-close-btn {
                background: none;
                border: 1px solid currentColor;
                color: inherit;
                padding: 4px 8px;
                border-radius: 4px;
                cursor: pointer;
                font-size: 12px;
                opacity: 0.8;
                transition: opacity 0.2s;
            }

            .error-action-btn:hover, .error-close-btn:hover {
                opacity: 1;
            }

            .error-close-btn {
                width: 24px;
                height: 24px;
                display: flex;
                align-items: center;
                justify-content: center;
                border-radius: 50%;
                padding: 0;
            }

            @keyframes slideIn {
                to { transform: translateX(0); }
            }
        `;
        
        document.head.appendChild(styles);
    }
}

// Create and export global error boundary instance
export const errorBoundary = new PWAErrorBoundary();

// Make available globally for debugging
if (M1K3Config.isDebugEnabled()) {
    window.PWAErrorBoundary = errorBoundary;
}

export default PWAErrorBoundary;