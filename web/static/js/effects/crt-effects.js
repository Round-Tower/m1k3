/**
 * M1K3 CRT Effects
 * Retro terminal display effects including scanlines, phosphor glow, and screen flicker
 */

class CRTEffects {
    constructor(options = {}) {
        this.config = {
            enableScanlines: options.enableScanlines !== false,
            enableFlicker: options.enableFlicker !== false,
            enableNoise: options.enableNoise !== false,
            enableGlow: options.enableGlow !== false,
            scanlineIntensity: options.scanlineIntensity || 0.05,
            flickerIntensity: options.flickerIntensity || 0.02,
            noiseIntensity: options.noiseIntensity || 0.01,
            glowIntensity: options.glowIntensity || 0.3,
            refreshRate: options.refreshRate || 16, // ~60fps
        };
        
        this.initialized = false;
        this.animationFrame = null;
        this.noiseCanvas = null;
        this.noiseContext = null;
        
        this.init();
    }
    
    /**
     * Initialize CRT effects
     */
    init() {
        if (this.initialized) return;
        
        this.createScanlineOverlay();
        this.createNoiseCanvas();
        this.applyPhosphorGlow();
        this.startAnimationLoop();
        
        this.initialized = true;
        console.log('📺 CRT Effects initialized');
    }
    
    /**
     * Create scanline overlay effect
     */
    createScanlineOverlay() {
        if (!this.config.enableScanlines) return;
        
        // Create scanline overlay element
        const scanlineOverlay = document.createElement('div');
        scanlineOverlay.id = 'crt-scanlines';
        scanlineOverlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            pointer-events: none;
            z-index: 1000;
            opacity: ${this.config.scanlineIntensity};
            background: repeating-linear-gradient(
                0deg,
                transparent,
                transparent 2px,
                rgba(0, 255, 0, 0.15) 2px,
                rgba(0, 255, 0, 0.15) 4px
            );
            animation: scanline-roll 8s linear infinite;
        `;
        
        // Add scanline animation
        const style = document.createElement('style');
        style.textContent = `
            @keyframes scanline-roll {
                0% { transform: translateY(-100%); }
                100% { transform: translateY(100vh); }
            }
            
            @keyframes scanline-flicker {
                0%, 100% { opacity: ${this.config.scanlineIntensity}; }
                50% { opacity: ${this.config.scanlineIntensity * 0.7}; }
            }
        `;
        document.head.appendChild(style);
        document.body.appendChild(scanlineOverlay);
        
        this.scanlineOverlay = scanlineOverlay;
    }
    
    /**
     * Create noise canvas for static effect
     */
    createNoiseCanvas() {
        if (!this.config.enableNoise) return;
        
        this.noiseCanvas = document.createElement('canvas');
        this.noiseCanvas.id = 'crt-noise';
        this.noiseCanvas.width = 200;
        this.noiseCanvas.height = 200;
        this.noiseCanvas.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            pointer-events: none;
            z-index: 999;
            opacity: ${this.config.noiseIntensity};
            mix-blend-mode: screen;
        `;
        
        this.noiseContext = this.noiseCanvas.getContext('2d');
        document.body.appendChild(this.noiseCanvas);
        
        this.generateNoise();
    }
    
    /**
     * Generate TV static noise
     */
    generateNoise() {
        if (!this.noiseContext) return;
        
        const imageData = this.noiseContext.createImageData(
            this.noiseCanvas.width, 
            this.noiseCanvas.height
        );
        
        const data = imageData.data;
        
        for (let i = 0; i < data.length; i += 4) {
            const noise = Math.random() * 255;
            data[i] = noise;     // Red
            data[i + 1] = noise; // Green
            data[i + 2] = noise; // Blue
            data[i + 3] = Math.random() * 50; // Alpha (transparency)
        }
        
        this.noiseContext.putImageData(imageData, 0, 0);
    }
    
    /**
     * Apply phosphor glow effect to terminal elements
     */
    applyPhosphorGlow() {
        if (!this.config.enableGlow) return;
        
        const glowStyle = document.createElement('style');
        glowStyle.textContent = `
            /* Terminal text glow effect */
            .terminal-glow,
            .dashboard-header,
            .panel-title,
            .metric-value,
            .tab-button.active,
            .chat-message,
            .btn:hover {
                text-shadow: 
                    0 0 5px currentColor,
                    0 0 10px currentColor,
                    0 0 15px currentColor;
            }
            
            /* Screen glow effect */
            body::before {
                content: '';
                position: fixed;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background: radial-gradient(
                    ellipse at center,
                    rgba(0, 255, 0, 0.1) 0%,
                    rgba(0, 255, 0, 0.05) 50%,
                    transparent 100%
                );
                pointer-events: none;
                z-index: -1;
            }
            
            /* Phosphor persistence effect */
            @keyframes phosphor-fade {
                0% { opacity: 1; text-shadow: 0 0 20px currentColor; }
                100% { opacity: 0.7; text-shadow: 0 0 5px currentColor; }
            }
            
            .phosphor-persist {
                animation: phosphor-fade 0.5s ease-out;
            }
        `;
        document.head.appendChild(glowStyle);
    }
    
    /**
     * Start animation loop for dynamic effects
     */
    startAnimationLoop() {
        const animate = () => {
            // Update noise if enabled
            if (this.config.enableNoise && Math.random() < 0.3) {
                this.generateNoise();
            }
            
            // Screen flicker effect
            if (this.config.enableFlicker && Math.random() < 0.001) {
                this.triggerFlicker();
            }
            
            // Screen curvature simulation
            this.updateCurvature();
            
            this.animationFrame = requestAnimationFrame(animate);
        };
        
        animate();
    }
    
    /**
     * Trigger screen flicker effect
     */
    triggerFlicker() {
        if (!this.config.enableFlicker) return;
        
        document.body.style.filter = `brightness(${0.9 + Math.random() * 0.2})`;
        
        setTimeout(() => {
            document.body.style.filter = '';
        }, 50 + Math.random() * 100);
    }
    
    /**
     * Update screen curvature effect
     */
    updateCurvature() {
        // Subtle screen warp effect
        const time = Date.now() * 0.001;
        const warp = Math.sin(time * 0.1) * 0.5;
        
        document.documentElement.style.setProperty('--screen-warp', `${warp}px`);
    }
    
    /**
     * Add phosphor persistence to element
     */
    addPhosphorPersistence(element) {
        if (!element || !this.config.enableGlow) return;
        
        element.classList.add('phosphor-persist');
        
        setTimeout(() => {
            element.classList.remove('phosphor-persist');
        }, 500);
    }
    
    /**
     * Terminal typing effect
     */
    typewriterEffect(element, text, speed = 50) {
        if (!element) return;
        
        element.textContent = '';
        element.style.borderRight = '2px solid var(--terminal-green)';
        
        let i = 0;
        const timer = setInterval(() => {
            if (i < text.length) {
                element.textContent += text.charAt(i);
                
                // Add phosphor persistence to each character
                this.addPhosphorPersistence(element);
                
                // Random typing speed variation
                const nextDelay = speed + (Math.random() - 0.5) * 20;
                i++;
                
                // Chance for double character (typo effect)
                if (Math.random() < 0.05 && i < text.length) {
                    element.textContent += text.charAt(i);
                    setTimeout(() => {
                        element.textContent = element.textContent.slice(0, -1);
                    }, 100);
                }
            } else {
                clearInterval(timer);
                element.style.borderRight = 'none';
            }
        }, speed);
        
        return timer;
    }
    
    /**
     * Boot sequence effect
     */
    bootSequence() {
        const bootMessages = [
            'INITIALIZING M1K3 TERMINAL...',
            'LOADING SYSTEM MODULES...',
            'AI CORE: ONLINE',
            'VOICE ENGINE: STANDBY',
            'WEBSOCKET: CONNECTING...',
            'AVATAR SYSTEM: READY',
            'TERMINAL READY'
        ];
        
        const bootContainer = document.createElement('div');
        bootContainer.id = 'boot-sequence';
        bootContainer.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: var(--bg-terminal);
            color: var(--terminal-green);
            font-family: var(--font-terminal);
            font-size: 1.2rem;
            padding: 50px;
            z-index: 10000;
            display: flex;
            flex-direction: column;
            justify-content: center;
        `;
        
        document.body.appendChild(bootContainer);
        
        let messageIndex = 0;
        const showNextMessage = () => {
            if (messageIndex < bootMessages.length) {
                const messageEl = document.createElement('div');
                messageEl.style.cssText = 'margin: 10px 0; opacity: 0.7;';
                bootContainer.appendChild(messageEl);
                
                this.typewriterEffect(messageEl, bootMessages[messageIndex], 30);
                
                setTimeout(() => {
                    messageIndex++;
                    showNextMessage();
                }, 800);
            } else {
                // Boot complete - fade out
                setTimeout(() => {
                    bootContainer.style.transition = 'opacity 1s';
                    bootContainer.style.opacity = '0';
                    
                    setTimeout(() => {
                        document.body.removeChild(bootContainer);
                    }, 1000);
                }, 1000);
            }
        };
        
        showNextMessage();
    }
    
    /**
     * Matrix-style code rain effect
     */
    matrixRain(duration = 5000) {
        const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#$%^&*(){}[]|\\:;"<>?,./';
        const drops = [];
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        
        canvas.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            pointer-events: none;
            z-index: 1001;
            opacity: 0.3;
        `;
        
        document.body.appendChild(canvas);
        
        const resize = () => {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
            
            const columns = Math.floor(canvas.width / 20);
            drops.length = 0;
            
            for (let i = 0; i < columns; i++) {
                drops[i] = Math.random() * canvas.height;
            }
        };
        
        resize();
        window.addEventListener('resize', resize);
        
        const draw = () => {
            ctx.fillStyle = 'rgba(0, 0, 0, 0.05)';
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            
            ctx.fillStyle = 'var(--terminal-green)';
            ctx.font = '15px VT323, monospace';
            
            for (let i = 0; i < drops.length; i++) {
                const text = chars[Math.floor(Math.random() * chars.length)];
                ctx.fillText(text, i * 20, drops[i]);
                
                if (drops[i] > canvas.height && Math.random() > 0.975) {
                    drops[i] = 0;
                }
                
                drops[i] += 20;
            }
        };
        
        const interval = setInterval(draw, 50);
        
        setTimeout(() => {
            clearInterval(interval);
            document.body.removeChild(canvas);
            window.removeEventListener('resize', resize);
        }, duration);
    }
    
    /**
     * Destroy CRT effects
     */
    destroy() {
        if (this.animationFrame) {
            cancelAnimationFrame(this.animationFrame);
        }
        
        // Remove DOM elements
        const elements = [
            'crt-scanlines',
            'crt-noise',
            'boot-sequence'
        ];
        
        elements.forEach(id => {
            const el = document.getElementById(id);
            if (el) el.remove();
        });
        
        // Reset body styles
        document.body.style.filter = '';
        document.documentElement.style.removeProperty('--screen-warp');
        
        this.initialized = false;
        console.log('📺 CRT Effects destroyed');
    }
}

// Auto-initialize CRT effects when DOM is ready
if (typeof window !== 'undefined') {
    window.CRTEffects = CRTEffects;
    
    // Initialize with default settings
    document.addEventListener('DOMContentLoaded', () => {
        if (window.location.pathname.includes('realtime_dashboard')) {
            window.crtEffects = new CRTEffects({
                enableScanlines: true,
                enableFlicker: true,
                enableNoise: false, // Disable by default for performance
                enableGlow: true,
                scanlineIntensity: 0.03,
                flickerIntensity: 0.01
            });
            
            // Show boot sequence on load
            setTimeout(() => {
                window.crtEffects.bootSequence();
            }, 500);
        }
    });
    
    console.log('📺 CRT Effects loaded');
}

// Export for module use
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CRTEffects;
}