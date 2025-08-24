/**
 * M1K3 Avatar Controller - Manages avatar rendering and interactions
 */
class AvatarController {
    constructor(stateManager, navigationManager) {
        this.stateManager = stateManager;
        this.navigationManager = navigationManager;
        
        // Canvas elements
        this.canvas = null;
        this.ctx = null;
        this.particleCanvas = null;
        this.particleCtx = null;
        
        // Controls
        this.emotionButtons = null;
        this.styleSelect = null;
        this.intensitySlider = null;
        this.intensityValue = null;
        
        // Particle system
        this.particles = [];
        this.animationFrame = null;
        this.animationState = { breathing: 0, time: 0 };
        
        // Avatar state
        this.pixelEngine = null;
        this.isVisible = false;
        this.needsResize = false;
        
        console.log('🤖 AvatarController initialized');
    }
    
    // Initialize avatar controller
    async initialize() {
        this.bindElements();
        this.setupEventListeners();
        this.initializeCanvas();
        this.setupResizeObserver();
        this.restoreFromState();
        this.startParticleSystem();
        
        console.log('🤖 Avatar interface ready');
    }
    
    // Bind DOM elements
    bindElements() {
        this.canvas = document.getElementById('avatarCanvas');
        this.particleCanvas = document.getElementById('particleCanvas');
        this.emotionButtons = document.querySelectorAll('.emotion-button');
        this.styleSelect = document.getElementById('styleSelect');
        this.intensitySlider = document.getElementById('intensitySlider');
        this.intensityValue = document.getElementById('intensityValue');
        this.avatarDisplay = document.getElementById('avatarDisplay');
        this.stateOverlay = document.getElementById('stateOverlay');
        this.stateIcon = document.getElementById('stateIcon');
        this.progressBar = document.getElementById('progressBar');
        this.progressFill = document.getElementById('progressFill');
    }
    
    // Setup event listeners
    setupEventListeners() {
        // Emotion buttons
        this.emotionButtons?.forEach(btn => {
            btn.addEventListener('click', () => {
                this.emotionButtons.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                const emotion = btn.getAttribute('data-emotion');
                this.updateEmotion(emotion);
            });
        });
        
        // Style selector
        this.styleSelect?.addEventListener('change', (e) => {
            this.updateStyle(e.target.value);
        });
        
        // Intensity slider
        this.intensitySlider?.addEventListener('input', (e) => {
            const intensity = parseInt(e.target.value);
            this.updateIntensity(intensity);
        });
        
        // State listeners
        this.stateManager.subscribe('currentEmotion', (emotion) => {
            this.syncEmotionButton(emotion);
        });
        
        this.stateManager.subscribe('emotionIntensity', (intensity) => {
            this.syncIntensitySlider(intensity);
        });
        
        this.stateManager.subscribe('avatar.style', (style) => {
            this.syncStyleSelect(style);
        });
        
        this.stateManager.subscribe('currentState', (state) => {
            this.updateStateDisplay(state);
            this.updateAvatarTextState(state);
        });
        
        // Listen for AI state updates from WebSocket
        this.stateManager.eventBus.addEventListener('ai.generation', (event) => {
            this.updateAITextState(event.detail);
        });
        
        // Listen for system metrics updates
        this.stateManager.eventBus.addEventListener('websocket.message_received', (event) => {
            const { data } = event.detail;
            if (data.type === 'metrics' || data.type === 'system') {
                this.updateSystemTextState(data.data);
            }
        });
        
        // Handle window resize with debouncing
        window.addEventListener('resize', this.debounce(() => {
            if (this.isVisible) {
                this.resizeCanvas();
                this.generateAvatar();
            } else {
                this.needsResize = true;
            }
        }, 250));
        
        // Handle orientation changes on mobile
        window.addEventListener('orientationchange', () => {
            setTimeout(() => {
                if (this.isVisible) {
                    this.resizeCanvas();
                    this.generateAvatar();
                }
            }, 300);
        });
    }
    
    // Initialize canvas and pixel engine
    initializeCanvas() {
        if (!this.canvas) return;
        
        // Initialize particle canvas
        if (this.particleCanvas) {
            this.particleCtx = this.particleCanvas.getContext('2d');
            this.createInitialParticles();
        }
        
        // Initialize GameboyPixelEngine if available
        if (typeof GameboyPixelEngine !== 'undefined') {
            try {
                // Detect layout mode based on container
                const container = document.querySelector('.canvas-container');
                const rect = container ? container.getBoundingClientRect() : this.canvas.getBoundingClientRect();
                const aspectRatio = rect.width / rect.height;
                let layoutMode = 'auto';
                
                // Determine optimal layout mode
                if (rect.width < 200 || rect.height < 200) {
                    layoutMode = 'mini';
                } else if (aspectRatio > 1.8) {
                    layoutMode = 'cinematic';  
                } else if (rect.width > 600 && rect.height > 600) {
                    layoutMode = 'fullscreen';
                }
                
                this.pixelEngine = new GameboyPixelEngine(this.canvas, {
                    basePixelSize: 8, // Changed from fixed pixelSize
                    minPixelSize: 2,
                    maxPixelSize: 16,
                    mode: 'avatar',
                    layoutMode: layoutMode,
                    adaptiveMode: true,
                    aspectRatioMode: 'adaptive',
                    enableCare: true,
                    enableEInk: false,
                    debugMode: false
                });
                
                console.log(`🎨 Adaptive avatar initialized: layout=${layoutMode}, container=${rect.width}x${rect.height}, aspect=${aspectRatio.toFixed(2)}`);
                
                // Set initial palette
                this.pixelEngine.currentPalette = this.pixelEngine.palettes.monochrome;
                
                // Store reference in state
                this.stateManager.set('avatar.pixelEngine', this.pixelEngine, true);
                
                console.log('🤖 GameboyPixelEngine initialized');
                
            } catch (error) {
                console.error('🤖 Failed to initialize GameboyPixelEngine:', error);
                this.pixelEngine = null;
            }
        }
        
        // Add polyfill for roundRect if needed
        this.addCanvasPolyfills();
    }
    
    // Setup resize observer
    setupResizeObserver() {
        if (typeof ResizeObserver !== 'undefined') {
            const container = document.querySelector('.canvas-container');
            if (container) {
                const resizeObserver = new ResizeObserver(() => {
                    if (this.isVisible) {
                        this.debounce(() => {
                            this.resizeCanvas();
                            this.generateAvatar();
                        }, 100)();
                    }
                });
                
                resizeObserver.observe(container);
                console.log('🤖 ResizeObserver attached');
            }
        }
    }
    
    // Restore interface from state
    restoreFromState() {
        const emotion = this.stateManager.get('currentEmotion');
        const intensity = this.stateManager.get('emotionIntensity');
        const style = this.stateManager.get('avatar.style');
        const color = this.stateManager.get('avatar.color');
        
        this.syncEmotionButton(emotion);
        this.syncIntensitySlider(intensity);
        this.syncStyleSelect(style);
        
        // Apply to pixel engine if available
        if (this.pixelEngine) {
            this.pixelEngine.setAvatar({
                emotion,
                style,
                color,
                intensity
            });
        }
    }
    
    // Create initial particles
    createInitialParticles() {
        if (!this.particleCtx) return;
        
        for (let i = 0; i < 20; i++) {
            this.createParticle();
        }
    }
    
    // Create a single particle
    createParticle() {
        if (!this.particleCanvas) return;
        
        const size = Math.random() * 2 + 1;
        this.particles.push({
            x: Math.random() * this.particleCanvas.width,
            y: Math.random() * this.particleCanvas.height,
            size: size,
            speed: Math.random() * 0.5 + 0.2,
            color: `rgba(255, 255, 255, ${Math.random() * 0.5 + 0.2})`,
            life: Math.random() * 2 + 1,
            maxLife: Math.random() * 2 + 1
        });
    }
    
    // Start particle animation system
    startParticleSystem() {
        const animateParticles = () => {
            this.animationState.time += 0.016;
            this.animationState.breathing = Math.sin(this.animationState.time * 2) * 0.5 + 0.5;
            
            if (this.isVisible) {
                this.updateParticles();
            }
            
            this.animationFrame = requestAnimationFrame(animateParticles);
        };
        
        animateParticles();
    }
    
    // Update particles
    updateParticles() {
        if (!this.particleCtx || !this.particleCanvas) return;
        
        const particlesEnabled = this.stateManager.get('ui.particleEffectsEnabled');
        if (!particlesEnabled) return;
        
        this.particleCtx.clearRect(0, 0, this.particleCanvas.width, this.particleCanvas.height);
        
        this.particles = this.particles.filter(particle => {
            particle.y -= particle.speed;
            particle.life -= 0.01;
            
            if (particle.life > 0) {
                const opacity = Math.min(particle.life / particle.maxLife, 1);
                this.particleCtx.globalAlpha = opacity;
                this.particleCtx.fillStyle = particle.color;
                this.particleCtx.fillRect(particle.x, particle.y, particle.size, particle.size);
                return true;
            }
            return false;
        });
        
        // Add new particles occasionally
        if (Math.random() > 0.95) {
            this.createParticle();
        }
    }
    
    // Resize canvas with high-DPI support
    resizeCanvas() {
        const container = document.querySelector('.canvas-container');
        if (!container || !this.canvas) return;
        
        const devicePixelRatio = window.devicePixelRatio || 1;
        const isMobile = window.innerWidth <= 767;
        
        // Get actual available dimensions
        const rect = container.getBoundingClientRect();
        let availableWidth = rect.width;
        let availableHeight = rect.height;
        
        // Account for padding
        const padding = isMobile ? 0 : 16;
        availableWidth -= padding;
        availableHeight -= padding;
        
        // Enhanced canvas sizing with full container utilization
        let canvasWidth = Math.floor(availableWidth * 0.95);
        let canvasHeight = Math.floor(availableHeight * 0.95);
        
        // No longer force square aspect ratio - use full available space!
        if (isMobile) {
            const maxMobileWidth = Math.floor(window.innerWidth * 0.95);
            const maxMobileHeight = Math.floor(window.innerHeight * 0.65);
            
            canvasWidth = Math.min(canvasWidth, maxMobileWidth);
            canvasHeight = Math.min(canvasHeight, maxMobileHeight);
        }
        
        // Determine layout mode based on new dimensions
        const aspectRatio = canvasWidth / canvasHeight;
        let layoutMode = 'auto';
        
        if (canvasWidth < 200 || canvasHeight < 200) {
            layoutMode = 'mini';
        } else if (aspectRatio > 1.8) {
            layoutMode = 'cinematic';
        } else if (canvasWidth > 600 && canvasHeight > 600) {
            layoutMode = 'fullscreen';
        }
        
        // Ensure minimum sizes
        const minSize = isMobile ? 150 : 200;
        canvasWidth = Math.max(canvasWidth, minSize);
        canvasHeight = Math.max(canvasHeight, minSize);
        
        // Update canvas dimensions
        if (this.canvas) {
            this.canvas.style.width = canvasWidth + 'px';
            this.canvas.style.height = canvasHeight + 'px';
            
            const internalWidth = isMobile ? canvasWidth : Math.floor(canvasWidth * Math.min(devicePixelRatio, 2));
            const internalHeight = isMobile ? canvasHeight : Math.floor(canvasHeight * Math.min(devicePixelRatio, 2));
            
            this.canvas.width = internalWidth;
            this.canvas.height = internalHeight;
        }
        
        if (this.particleCanvas) {
            this.particleCanvas.style.width = canvasWidth + 'px';
            this.particleCanvas.style.height = canvasHeight + 'px';
            
            const internalWidth = isMobile ? canvasWidth : Math.floor(canvasWidth * Math.min(devicePixelRatio, 2));
            const internalHeight = isMobile ? canvasHeight : Math.floor(canvasHeight * Math.min(devicePixelRatio, 2));
            
            this.particleCanvas.width = internalWidth;
            this.particleCanvas.height = internalHeight;
        }
        
        // Update pixel engine dimensions with layout mode
        if (this.pixelEngine && this.pixelEngine.updateDimensions) {
            this.pixelEngine.updateDimensions(canvasWidth, canvasHeight, { layoutMode });
        }
        
        console.log(`🤖 Adaptive canvas resized to ${canvasWidth}x${canvasHeight} (${aspectRatio.toFixed(2)} aspect, ${layoutMode} mode)`);
        this.needsResize = false;
    }
    
    // Generate avatar
    generateAvatar() {
        if (!this.isVisible) return;
        
        if (this.pixelEngine) {
            // Update system state
            const systemMetrics = this.stateManager.get('systemMetrics');
            if (systemMetrics) {
                this.pixelEngine.updateSystemState(systemMetrics);
            }
            
            // Update avatar state
            const emotion = this.stateManager.get('currentEmotion');
            const intensity = this.stateManager.get('emotionIntensity');
            
            if (this.pixelEngine.avatarState) {
                this.pixelEngine.avatarState.mood = emotion;
                this.pixelEngine.avatarState.health = intensity;
                this.pixelEngine.avatarState.lastInteraction = Date.now();
            }
            
            // Render avatar
            this.pixelEngine.renderAvatar();
            
            // Log adaptive info for debugging (occasionally)
            if (Math.random() < 0.05) { // 5% chance to avoid spam
                const layoutInfo = this.pixelEngine.getLayoutInfo();
                console.log('🎨 Avatar adaptive info:', layoutInfo);
            }
        } else {
            // Fallback rendering
            this.renderFallbackAvatar();
        }
    }
    
    // Fallback avatar rendering
    renderFallbackAvatar() {
        if (!this.canvas) return;
        
        const ctx = this.canvas.getContext('2d');
        if (!ctx) return;
        
        ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        
        const centerX = this.canvas.width / 2;
        const centerY = this.canvas.height / 2;
        const size = Math.min(this.canvas.width, this.canvas.height) * 0.3;
        
        const color = this.stateManager.get('avatar.color') || '#E25303';
        
        // Draw basic avatar shape
        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.arc(centerX, centerY, size / 2, 0, Math.PI * 2);
        ctx.fill();
        
        // Draw eyes
        ctx.fillStyle = '#FFFFFF';
        ctx.beginPath();
        ctx.arc(centerX - size * 0.2, centerY - size * 0.15, size * 0.08, 0, Math.PI * 2);
        ctx.arc(centerX + size * 0.2, centerY - size * 0.15, size * 0.08, 0, Math.PI * 2);
        ctx.fill();
        
        // Draw pupils
        ctx.fillStyle = '#000000';
        ctx.beginPath();
        ctx.arc(centerX - size * 0.2, centerY - size * 0.15, size * 0.04, 0, Math.PI * 2);
        ctx.arc(centerX + size * 0.2, centerY - size * 0.15, size * 0.04, 0, Math.PI * 2);
        ctx.fill();
    }
    
    // Update emotion
    updateEmotion(emotion, intensity = null) {
        this.stateManager.update({
            'currentEmotion': emotion,
            'emotionIntensity': intensity || this.stateManager.get('emotionIntensity')
        });
        
        if (this.pixelEngine && this.pixelEngine.setEmotion) {
            this.pixelEngine.setEmotion(emotion, intensity || this.stateManager.get('emotionIntensity'));
        }
        
        this.generateAvatar();
        console.log(`🤖 Emotion updated: ${emotion}`);
    }
    
    // Update style
    updateStyle(style) {
        const color = this.stateManager.get('avatar.color');
        
        this.stateManager.set('avatar.style', style);
        
        if (this.pixelEngine && this.pixelEngine.setStyle) {
            this.pixelEngine.setStyle(style, color);
        } else if (this.pixelEngine) {
            // Fallback style handling
            const stylePaletteMap = {
                'robot': 'monochrome',
                'organic': 'forest',
                'crystal': 'crystal',
                'ghost': 'ocean',
                'energy': 'sunset',
                'cute': 'classic'
            };
            
            const paletteKey = stylePaletteMap[style] || 'monochrome';
            if (this.pixelEngine.palettes && this.pixelEngine.palettes[paletteKey]) {
                this.pixelEngine.currentPalette = this.pixelEngine.palettes[paletteKey];
            }
        }
        
        this.generateAvatar();
        console.log(`🤖 Style updated: ${style}`);
    }
    
    // Update intensity
    updateIntensity(intensity) {
        this.stateManager.set('emotionIntensity', intensity);
        
        if (this.intensityValue) {
            this.intensityValue.textContent = intensity + '%';
        }
        
        if (this.pixelEngine && this.pixelEngine.avatarState) {
            this.pixelEngine.avatarState.health = intensity;
        }
        
        this.generateAvatar();
        console.log(`🤖 Intensity updated: ${intensity}%`);
    }
    
    // Sync emotion button state
    syncEmotionButton(emotion) {
        this.emotionButtons?.forEach(btn => {
            const isActive = btn.getAttribute('data-emotion') === emotion;
            btn.classList.toggle('active', isActive);
        });
    }
    
    // Sync intensity slider
    syncIntensitySlider(intensity) {
        if (this.intensitySlider) {
            this.intensitySlider.value = intensity;
        }
        if (this.intensityValue) {
            this.intensityValue.textContent = intensity + '%';
        }
    }
    
    // Sync style select
    syncStyleSelect(style) {
        if (this.styleSelect) {
            this.styleSelect.value = style;
        }
    }
    
    // Update state overlay display
    updateStateDisplay(state) {
        if (!this.stateOverlay || !this.stateIcon) return;
        
        const stateIcons = {
            'idle': '💤',
            'thinking': '🤔',
            'generating': '⚡',
            'speaking': '🔊',
            'error': '❌'
        };
        
        this.stateIcon.textContent = stateIcons[state] || '💤';
        
        if (this.avatarDisplay) {
            this.avatarDisplay.className = 'avatar-container ' + state;
        }
    }
    
    // Add canvas polyfills
    addCanvasPolyfills() {
        if (!CanvasRenderingContext2D.prototype.roundRect) {
            CanvasRenderingContext2D.prototype.roundRect = function(x, y, width, height, radius = 0) {
                if (radius === 0) {
                    this.rect(x, y, width, height);
                    return;
                }
                
                this.moveTo(x + radius, y);
                this.lineTo(x + width - radius, y);
                this.arcTo(x + width, y, x + width, y + radius, radius);
                this.lineTo(x + width, y + height - radius);
                this.arcTo(x + width, y + height, x + width - radius, y + height, radius);
                this.lineTo(x + radius, y + height);
                this.arcTo(x, y + height, x, y + height - radius, radius);
                this.lineTo(x, y + radius);
                this.arcTo(x, y, x + radius, y, radius);
            };
        }
    }
    
    // Utility: debounce function
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }
    
    // Called when view is shown
    async onShow() {
        this.isVisible = true;
        
        // Handle pending resize
        if (this.needsResize) {
            this.resizeCanvas();
        }
        
        // Generate avatar
        setTimeout(() => {
            this.generateAvatar();
            
            // Start pixel engine animation if available
            if (this.pixelEngine && !this.pixelEngine.isAnimating) {
                this.pixelEngine.startAnimationLoop();
            }
        }, 100);
    }
    
    // Called when view is hidden
    async onHide() {
        this.isVisible = false;
        
        // Stop pixel engine animation to save resources
        if (this.pixelEngine && this.pixelEngine.isAnimating) {
            this.pixelEngine.stopAnimationLoop();
        }
    }
    
    // Cleanup resources
    cleanup() {
        this.isVisible = false;
        
        // Cancel animation frame
        if (this.animationFrame) {
            cancelAnimationFrame(this.animationFrame);
            this.animationFrame = null;
        }
        
        // Stop pixel engine
        if (this.pixelEngine && this.pixelEngine.stopAnimationLoop) {
            this.pixelEngine.stopAnimationLoop();
        }
    }
    
    // === ADVANCED LAYOUT MODE CONTROLS ===
    
    // Switch layout mode dynamically
    switchLayoutMode(newMode, options = {}) {
        if (this.pixelEngine && this.pixelEngine.setLayoutMode) {
            this.pixelEngine.setLayoutMode(newMode, options);
            console.log(`🎨 Avatar layout mode switched to: ${newMode}`);
        }
    }
    
    // Get current adaptive information
    getAdaptiveInfo() {
        if (this.pixelEngine && this.pixelEngine.getLayoutInfo) {
            return this.pixelEngine.getLayoutInfo();
        }
        return null;
    }
    
    // Cycle through layout modes for testing
    cycleLayoutModes() {
        const modes = ['auto', 'mini', 'fullscreen', 'cinematic'];
        const currentMode = this.pixelEngine ? this.pixelEngine.layoutMode : 'auto';
        const currentIndex = modes.indexOf(currentMode);
        const nextIndex = (currentIndex + 1) % modes.length;
        const nextMode = modes[nextIndex];
        
        this.switchLayoutMode(nextMode);
        return nextMode;
    }
    
    // Force container refresh for testing
    refreshContainer() {
        this.resizeCanvas();
        this.generateAvatar();
    }
    
    // === TEXT STATE INTEGRATION ===
    
    // Update avatar text state from WebSocket state changes
    updateAvatarTextState(state) {
        if (!this.pixelEngine) return;
        
        // Update avatar internal state for text rendering
        if (this.pixelEngine.avatarState) {
            // Map AI state to avatar text display
            const stateMapping = {
                'idle': { mood: 'happy', displayText: 'Ready' },
                'thinking': { mood: 'thinking', displayText: 'Processing' },
                'generating': { mood: 'excited', displayText: 'Creating' },
                'speaking': { mood: 'happy', displayText: 'Speaking' },
                'error': { mood: 'sad', displayText: 'Error' }
            };
            
            const mappedState = stateMapping[state] || stateMapping['idle'];
            this.pixelEngine.avatarState.mood = mappedState.mood;
            this.pixelEngine.avatarState.displayText = mappedState.displayText;
            this.pixelEngine.avatarState.lastStateUpdate = Date.now();
        }
    }
    
    // Update AI text state from WebSocket AI generation events
    updateAITextState(data) {
        if (!this.pixelEngine || !this.pixelEngine.systemState) return;
        
        // Update system state with latest AI metrics for text display
        const aiUpdates = {};
        
        if (data.type === 'thinking_phase' && data.phase) {
            aiUpdates.aiPhase = data.phase;
        }
        
        if (data.type === 'generation_stream' && data.tokens_per_second) {
            aiUpdates.aiSpeed = data.tokens_per_second;
        }
        
        if (data.type === 'progress' && data.percentage !== undefined) {
            aiUpdates.aiProgress = data.percentage;
        }
        
        // Apply updates to pixel engine system state for text rendering
        Object.assign(this.pixelEngine.systemState, aiUpdates);
    }
    
    // Update system text state from WebSocket metrics
    updateSystemTextState(data) {
        if (!this.pixelEngine || !this.pixelEngine.systemState) return;
        
        // Map WebSocket metrics to pixel engine system state
        const systemUpdates = {};
        
        if (data.cpu !== undefined) {
            systemUpdates.cpu = data.cpu;
        }
        
        if (data.memory !== undefined) {
            systemUpdates.memory = data.memory;
        }
        
        if (data.battery !== undefined) {
            systemUpdates.battery = data.battery;
        }
        
        if (data.temperature !== undefined) {
            systemUpdates.temperature = data.temperature;
        }
        
        if (data.network !== undefined) {
            systemUpdates.network = data.network;
            systemUpdates.networkStrength = data.networkStrength || 75;
        }
        
        // Apply updates to pixel engine system state for text rendering
        Object.assign(this.pixelEngine.systemState, systemUpdates);
        
        console.log('📊 System text state updated:', systemUpdates);
    }
}

// Export as global
if (typeof window !== 'undefined') {
    window.AvatarController = AvatarController;
}

// Module exports
if (typeof module !== 'undefined' && module.exports) {
    module.exports = AvatarController;
}