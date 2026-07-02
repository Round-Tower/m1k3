/**
 * M1K3 Loading Indicators
 * Visual feedback components for model loading and processing
 */

class LoadingIndicators {
    constructor() {
        this.createLoadingOverlay();
    }

    createLoadingOverlay() {
        // Create loading overlay if it doesn't exist
        if (!document.getElementById('loading-overlay')) {
            const overlay = document.createElement('div');
            overlay.id = 'loading-overlay';
            overlay.className = 'loading-overlay hidden';
            overlay.innerHTML = `
                <div class="loading-content">
                    <div class="loading-spinner">
                        <div class="spinner-ring"></div>
                        <div class="spinner-ring"></div>
                        <div class="spinner-ring"></div>
                    </div>
                    <h3 class="loading-title">Loading AI Model</h3>
                    <p class="loading-description">Preparing your local AI assistant...</p>
                    <div class="loading-progress-container">
                        <div class="loading-progress-bar">
                            <div class="progress-fill" id="loading-progress-fill"></div>
                        </div>
                        <div class="loading-percentage" id="loading-percentage">0%</div>
                    </div>
                    <div class="loading-details" id="loading-details">Initializing...</div>
                </div>
            `;
            document.body.appendChild(overlay);
        }
    }

    showLoading(title = 'Loading AI Model', description = 'Preparing your local AI assistant...') {
        const overlay = document.getElementById('loading-overlay');
        const titleEl = overlay.querySelector('.loading-title');
        const descEl = overlay.querySelector('.loading-description');
        
        titleEl.textContent = title;
        descEl.textContent = description;
        
        overlay.classList.remove('hidden');
        this.updateProgress(0, 'Initializing...');
    }

    hideLoading() {
        const overlay = document.getElementById('loading-overlay');
        overlay.classList.add('hidden');
    }

    updateProgress(percentage, details = '') {
        const progressFill = document.getElementById('loading-progress-fill');
        const percentageEl = document.getElementById('loading-percentage');
        const detailsEl = document.getElementById('loading-details');
        
        if (progressFill) {
            progressFill.style.width = `${percentage}%`;
        }
        
        if (percentageEl) {
            percentageEl.textContent = `${Math.round(percentage)}%`;
        }
        
        if (detailsEl && details) {
            detailsEl.textContent = details;
        }
    }

    createInlineSpinner(containerId) {
        const container = document.getElementById(containerId);
        if (!container) return null;

        const spinner = document.createElement('div');
        spinner.className = 'inline-spinner';
        spinner.innerHTML = `
            <div class="spinner-dots">
                <div class="dot"></div>
                <div class="dot"></div>
                <div class="dot"></div>
            </div>
            <span class="spinner-text">Loading...</span>
        `;
        
        container.appendChild(spinner);
        return spinner;
    }

    removeInlineSpinner(containerId) {
        const container = document.getElementById(containerId);
        if (!container) return;
        
        const spinner = container.querySelector('.inline-spinner');
        if (spinner) {
            spinner.remove();
        }
    }

    createToast(message, type = 'info', duration = 3000) {
        const toastContainer = this.getToastContainer();
        
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.innerHTML = `
            <div class="toast-icon">${this.getToastIcon(type)}</div>
            <div class="toast-content">
                <div class="toast-message">${message}</div>
            </div>
            <button class="toast-close">✕</button>
        `;
        
        // Add close functionality
        toast.querySelector('.toast-close').addEventListener('click', () => {
            this.removeToast(toast);
        });
        
        toastContainer.appendChild(toast);
        
        // Animate in
        requestAnimationFrame(() => {
            toast.classList.add('toast-show');
        });
        
        // Auto remove
        if (duration > 0) {
            setTimeout(() => {
                this.removeToast(toast);
            }, duration);
        }
        
        return toast;
    }

    getToastContainer() {
        let container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            container.className = 'toast-container';
            document.body.appendChild(container);
        }
        return container;
    }

    getToastIcon(type) {
        const icons = {
            info: 'ℹ️',
            success: '✅',
            warning: '⚠️',
            error: '❌',
            loading: '⏳'
        };
        return icons[type] || icons.info;
    }

    removeToast(toast) {
        toast.classList.add('toast-hide');
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 300);
    }

    showModelLoadingStatus(modelName, status, details = '') {
        const statusMap = {
            'loading': { type: 'loading', message: `Loading ${modelName}...` },
            'success': { type: 'success', message: `${modelName} loaded successfully` },
            'error': { type: 'error', message: `Failed to load ${modelName}` },
            'fallback': { type: 'warning', message: `Using fallback mode for ${modelName}` }
        };
        
        const config = statusMap[status] || statusMap.loading;
        const message = details ? `${config.message}: ${details}` : config.message;
        
        return this.createToast(message, config.type, status === 'loading' ? 0 : 3000);
    }
}

// Create and export singleton instance
window.LoadingIndicators = LoadingIndicators;
window.loadingIndicators = new LoadingIndicators();