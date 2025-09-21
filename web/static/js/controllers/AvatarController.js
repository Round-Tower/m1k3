/**
 * M1K3 Avatar Controller
 * Manages avatar display, emotions, styles, and interactions
 */
class AvatarController extends EventTarget {
    constructor(stateManager, websocketManager) {
        super();
        
        this.stateManager = stateManager;
        this.websocketManager = websocketManager;
        
        this.elements = {};
        this.pixelEngine = null;
        this.animationFrame = null;
        this.particles = [];
        this.lastUpdate = Date.now();
        
        this.emotions = [
            'happy', 'sad', 'angry', 'surprised', 
            'love', 'thinking', 'sleepy', 'excited'
        ];
        
        this.styles = [
            'robot', 'organic', 'crystal', 
            'ghost', 'energy', 'cute'
        ];
        
        this.colors = [
            '#E25303', '#00FF85', '#FF4500', '#00BFFF',
            '#9A4BFF', '#FFD700', '#FF69B4', '#32CD32'
        ];
        
        console.log('🤖 AvatarController initialized');
    }
    
    /**
     * Initialize avatar controller
     */
    async initialize() {
        this.findElements();
        this.setupEventListeners();
        this.initializeCanvas();
        this.initializeControls();
        
        // Subscribe to state changes
        this.subscribeToStateChanges();
        
        console.log('🤖 Avatar controller initialized');
        return this;
    }
    
    /**
     * Find DOM elements
     */
    findElements() {
        this.elements = {
            // Canvas elements
            avatarCanvas: document.getElementById('avatarCanvas'),
            particleCanvas: document.getElementById('particleCanvas'),
            loadingCanvas: document.getElementById('loadingAvatarCanvas'),
            
            // Control elements
            emotionButtons: document.querySelectorAll('.emotion-btn'),
            styleButtons: document.querySelectorAll('.style-btn'),
            colorButtons: document.querySelectorAll('.color-btn'),
            
            // Sliders and inputs
            emotionSlider: document.getElementById('emotionIntensity'),
            emotionValue: document.getElementById('emotionIntensityValue'),
            animationSpeed: document.getElementById('animationSpeed'),
            
            // Display elements
            currentEmotion: document.getElementById('currentEmotion'),
            currentStyle: document.getElementById('currentStyle'),
            currentState: document.getElementById('currentState'),
            
            // Action buttons
            testEmotionsBtn: document.getElementById('testEmotions'),
            randomizeBtn: document.getElementById('randomizeAvatar'),
            resetBtn: document.getElementById('resetAvatar'),
            exportBtn: document.getElementById('exportAvatar'),
            
            // Avatar container
            avatarContainer: document.querySelector('.avatar-container, .screensaver-avatar-container'),
            canvasContainer: document.querySelector('.canvas-container, .screensaver-avatar-container')
        };
    }
    
    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Emotion buttons
        this.elements.emotionButtons.forEach(button => {
            button.addEventListener('click', () => {
                const emotion = button.dataset.emotion;
                if (emotion) {
                    this.setEmotion(emotion);
                }
            });
        });
        
        // Style buttons
        this.elements.styleButtons.forEach(button => {
            button.addEventListener('click', () => {
                const style = button.dataset.style;
                if (style) {
                    this.setStyle(style);
                }
            });
        });
        
        // Color buttons
        this.elements.colorButtons.forEach(button => {
            button.addEventListener('click', () => {
                const color = button.dataset.color || button.style.backgroundColor;
                if (color) {
                    this.setColor(color);
                }
            });
        });
        
        // Emotion intensity slider
        if (this.elements.emotionSlider) {
            this.elements.emotionSlider.addEventListener('input', (e) => {
                const intensity = parseInt(e.target.value);
                this.setIntensity(intensity);
                
                if (this.elements.emotionValue) {
                    this.elements.emotionValue.textContent = intensity + '%';
                }
            });
        }
        
        // Animation speed
        if (this.elements.animationSpeed) {
            this.elements.animationSpeed.addEventListener('input', (e) => {
                const speed = parseFloat(e.target.value);
                this.setAnimationSpeed(speed);
            });
        }
        
        // Action buttons
        if (this.elements.testEmotionsBtn) {
            this.elements.testEmotionsBtn.addEventListener('click', () => {
                this.testAllEmotions();
            });
        }
        
        if (this.elements.randomizeBtn) {
            this.elements.randomizeBtn.addEventListener('click', () => {
                this.randomizeAvatar();
            });
        }
        
        if (this.elements.resetBtn) {
            this.elements.resetBtn.addEventListener('click', () => {
                this.resetAvatar();
            });
        }
        
        if (this.elements.exportBtn) {
            this.elements.exportBtn.addEventListener('click', () => {
                this.exportAvatar();
            });
        }
        
        // Canvas resize handling
        this.setupResizeHandler();
        
        // WebSocket events for avatar updates
        if (this.websocketManager) {
            this.websocketManager.addEventListener('message.emotion', (e) => {
                this.handleEmotionUpdate(e.detail.data);
            });
            
            this.websocketManager.addEventListener('message.avatar_emotion', (e) => {
                this.handleEmotionUpdate(e.detail.data);
            });
            
            this.websocketManager.addEventListener('message.state', (e) => {
                this.handleStateUpdate(e.detail.data);
            });
        }
    }
    
    /**
     * Subscribe to state changes
     */
    subscribeToStateChanges() {
        // Avatar emotion changes
        this.stateManager.subscribe('avatar.emotion', (event) => {
            this.updateAvatarEmotion(event.detail.value);
        });
        
        this.stateManager.subscribe('avatar.intensity', (event) => {
            this.updateIntensityDisplay(event.detail.value);
        });
        
        this.stateManager.subscribe('avatar.state', (event) => {
            this.updateAvatarState(event.detail.value);
        });
        
        this.stateManager.subscribe('avatar.style', (event) => {
            this.updateAvatarStyle(event.detail.value);
        });
        
        // System metrics for avatar reactions
        this.stateManager.subscribe('system.metrics', (event) => {
            this.handleSystemMetrics(event.detail.metrics);
        });
    }
    
    /**
     * Initialize canvas and pixel engine
     */
    initializeCanvas() {
        const canvas = this.elements.avatarCanvas || this.elements.loadingCanvas;
        const particleCanvas = this.elements.particleCanvas;
        
        if (!canvas) {
            console.warn('🤖 No avatar canvas found');
            return;
        }
        
        // Initialize GameboyPixelEngine if available
        if (typeof GameboyPixelEngine !== 'undefined') {
            try {
                this.pixelEngine = new GameboyPixelEngine(canvas, {
                    pixelSize: 12,
                    mode: 'avatar',
                    enableCare: true,
                    enableEInk: false,
                    debugMode: false
                });
                
                // Set initial state
                const avatarState = this.stateManager.getState('avatar');
                this.pixelEngine.setAvatar({
                    emotion: avatarState.emotion || 'happy',
                    style: avatarState.style || 'robot',
                    color: avatarState.color || '#E25303',
                    intensity: avatarState.intensity || 50
                });
                
                // Start animation loop
                this.pixelEngine.startAnimationLoop();
                
                console.log('🤖 GameboyPixelEngine initialized');
                
            } catch (error) {
                console.error('🤖 Failed to initialize GameboyPixelEngine:', error);
                this.initializeFallbackRenderer(canvas);
            }
        } else {
            console.warn('🤖 GameboyPixelEngine not available, using fallback');
            this.initializeFallbackRenderer(canvas);
        }
        
        // Initialize particle system
        if (particleCanvas) {
            this.initializeParticleSystem(particleCanvas);
        }
        
        // Setup canvas resize
        this.resizeCanvas();
    }
    
    /**
     * Initialize fallback avatar renderer
     */
    initializeFallbackRenderer(canvas) {
        this.ctx = canvas.getContext('2d');
        this.startFallbackAnimation();
    }
    
    /**
     * Initialize particle system
     */
    initializeParticleSystem(canvas) {
        this.particleCtx = canvas.getContext('2d');
        this.particles = [];
        
        // Create initial particles
        for (let i = 0; i < 20; i++) {
            this.createParticle();
        }
        
        this.startParticleAnimation();
    }
    
    /**
     * Setup resize handler
     */
    setupResizeHandler() {
        const resizeObserver = new ResizeObserver(() => {
            clearTimeout(this.resizeTimeout);
            this.resizeTimeout = setTimeout(() => {
                this.resizeCanvas();
            }, 100);
        });
        
        if (this.elements.canvasContainer) {
            resizeObserver.observe(this.elements.canvasContainer);
        }
        
        window.addEventListener('orientationchange', () => {
            setTimeout(() => this.resizeCanvas(), 300);
        });
    }
    
    /**
     * Initialize avatar controls
     */
    initializeControls() {
        // Update button states
        this.updateControlStates();
        
        // Set initial values
        const avatarState = this.stateManager.getState('avatar');
        
        if (this.elements.emotionSlider) {
            this.elements.emotionSlider.value = avatarState.intensity || 50;
        }
        
        if (this.elements.emotionValue) {
            this.elements.emotionValue.textContent = (avatarState.intensity || 50) + '%';
        }
        
        // Update displays
        this.updateDisplays();
    }
    
    /**
     * Set avatar emotion
     */
    setEmotion(emotion, intensity = null) {
        if (!this.emotions.includes(emotion)) {
            console.warn(`🤖 Invalid emotion: ${emotion}`);
            return;
        }
        
        const currentIntensity = intensity || this.stateManager.getState('avatar.intensity') || 50;
        
        this.stateManager.updateAvatarEmotion(emotion, currentIntensity);
        
        // Send to server if connected
        if (this.websocketManager && this.websocketManager.isConnected) {
            this.websocketManager.send({
                type: 'set_emotion',
                emotion: emotion,
                intensity: currentIntensity
            });
        }
        
        this.updateControlStates();
        this.emit('emotion.changed', { emotion, intensity: currentIntensity });
    }
    
    /**
     * Set avatar style
     */
    setStyle(style, color = null) {
        if (!this.styles.includes(style)) {
            console.warn(`🤖 Invalid style: ${style}`);
            return;
        }
        
        this.stateManager.updateState('avatar.style', style);
        
        if (color) {
            this.stateManager.updateState('avatar.color', color);
        }
        
        // Update pixel engine
        if (this.pixelEngine && this.pixelEngine.setStyle) {
            this.pixelEngine.setStyle(style, color);
        }
        
        // Send to server
        if (this.websocketManager && this.websocketManager.isConnected) {
            this.websocketManager.send({
                type: 'set_style',
                style: style,
                color: color
            });
        }
        
        this.updateControlStates();
        this.emit('style.changed', { style, color });
    }
    
    /**
     * Set avatar color
     */
    setColor(color) {
        this.stateManager.updateState('avatar.color', color);
        
        // Update pixel engine
        if (this.pixelEngine && this.pixelEngine.setColor) {
            this.pixelEngine.setColor(color);
        }
        
        this.updateControlStates();
        this.emit('color.changed', { color });
    }
    
    /**
     * Set emotion intensity
     */
    setIntensity(intensity) {
        const emotion = this.stateManager.getState('avatar.emotion') || 'happy';
        this.stateManager.updateAvatarEmotion(emotion, intensity);
        
        // Update pixel engine
        if (this.pixelEngine && this.pixelEngine.setEmotion) {
            this.pixelEngine.setEmotion(emotion, intensity);
        }
        
        this.emit('intensity.changed', { intensity });
    }
    
    /**
     * Set animation speed
     */
    setAnimationSpeed(speed) {
        if (this.pixelEngine && this.pixelEngine.setAnimationSpeed) {
            this.pixelEngine.setAnimationSpeed(speed);
        }
        
        this.emit('animation.speed.changed', { speed });
    }
    
    /**
     * Handle emotion update from server
     */
    handleEmotionUpdate(data) {
        const emotion = data.emotion || data.emo;
        const intensity = data.intensity || data.int || 50;
        
        if (emotion) {
            this.stateManager.updateAvatarEmotion(emotion, intensity, data);
            this.updateAvatarEmotion(emotion, intensity);
        }
    }
    
    /**
     * Handle state update from server
     */
    handleStateUpdate(data) {
        const state = data.state || data;
        this.stateManager.updateAvatarState(state);
        this.updateAvatarState(state);
    }
    
    /**
     * Update avatar emotion display
     */
    updateAvatarEmotion(emotion, intensity = 50) {
        // Update pixel engine
        if (this.pixelEngine) {
            if (this.pixelEngine.setEmotion) {
                this.pixelEngine.setEmotion(emotion, intensity);
            } else if (this.pixelEngine.avatarState) {
                this.pixelEngine.avatarState.mood = emotion;
                this.pixelEngine.avatarState.health = intensity;
                this.pixelEngine.renderAvatar();
            }
        }
        
        this.updateControlStates();
        this.updateDisplays();
    }
    
    /**
     * Update avatar state
     */
    updateAvatarState(state) {
        // Update pixel engine
        if (this.pixelEngine && this.pixelEngine.setState) {
            this.pixelEngine.setState(state);
        }
        
        this.updateDisplays();
    }
    
    /**
     * Update avatar style
     */
    updateAvatarStyle(style) {
        // Update pixel engine  
        if (this.pixelEngine && this.pixelEngine.setStyle) {
            this.pixelEngine.setStyle(style);
        }
        
        this.updateControlStates();
        this.updateDisplays();
    }
    
    /**
     * Handle system metrics for avatar reactions
     */
    handleSystemMetrics(metrics) {
        // Update pixel engine with system state
        if (this.pixelEngine && this.pixelEngine.updateSystemState) {
            this.pixelEngine.updateSystemState({
                battery: metrics.battery,
                cpu: metrics.cpu,
                memory: metrics.memory,
                temperature: metrics.temperature,
                network: metrics.network,
                networkStrength: metrics.networkStrength,
                ecoSavings: metrics.ecoSavings
            });
        }
        
        // Trigger automatic emotion changes based on system state
        this.handleAutomaticReactions(metrics);
    }
    
    /**
     * Handle automatic avatar reactions to system state
     */
    handleAutomaticReactions(metrics) {
        const currentEmotion = this.stateManager.getState('avatar.emotion');
        
        // React to extreme conditions
        if (metrics.temperature > 80) {
            if (currentEmotion !== 'angry') {
                this.setEmotion('angry', 80);
                setTimeout(() => this.setEmotion('happy'), 3000);
            }
        } else if (metrics.battery < 20) {
            if (currentEmotion !== 'sleepy') {
                this.setEmotion('sleepy', 60);
            }
        } else if (metrics.cpu > 90) {
            if (currentEmotion !== 'thinking') {
                this.setEmotion('thinking', 75);
                setTimeout(() => this.setEmotion('happy'), 2000);
            }
        }
    }
    
    /**
     * Update control button states
     */
    updateControlStates() {
        const avatarState = this.stateManager.getState('avatar');
        
        // Update emotion buttons
        this.elements.emotionButtons.forEach(button => {
            button.classList.toggle('active', button.dataset.emotion === avatarState.emotion);
        });
        
        // Update style buttons
        this.elements.styleButtons.forEach(button => {
            button.classList.toggle('active', button.dataset.style === avatarState.style);
        });
        
        // Update color buttons
        this.elements.colorButtons.forEach(button => {
            const buttonColor = button.dataset.color || button.style.backgroundColor;
            button.classList.toggle('active', buttonColor === avatarState.color);
        });
    }
    
    /**
     * Update display elements
     */
    updateDisplays() {
        const avatarState = this.stateManager.getState('avatar');
        
        if (this.elements.currentEmotion) {
            this.elements.currentEmotion.textContent = `${this.getEmotionEmoji(avatarState.emotion)} ${avatarState.emotion}`;
        }
        
        if (this.elements.currentStyle) {
            this.elements.currentStyle.textContent = avatarState.style || 'robot';
        }
        
        if (this.elements.currentState) {
            this.elements.currentState.textContent = avatarState.state || 'idle';
        }
    }
    
    /**
     * Update intensity display
     */
    updateIntensityDisplay(intensity) {
        if (this.elements.emotionSlider) {
            this.elements.emotionSlider.value = intensity;
        }
        
        if (this.elements.emotionValue) {
            this.elements.emotionValue.textContent = intensity + '%';
        }
    }
    
    /**
     * Test all emotions
     */
    async testAllEmotions() {
        for (let i = 0; i < this.emotions.length; i++) {
            const emotion = this.emotions[i];
            this.setEmotion(emotion, 75);
            await new Promise(resolve => setTimeout(resolve, 1500));
        }
        
        // Return to happy
        this.setEmotion('happy', 50);
    }
    
    /**
     * Randomize avatar appearance
     */
    randomizeAvatar() {
        const randomEmotion = this.emotions[Math.floor(Math.random() * this.emotions.length)];
        const randomStyle = this.styles[Math.floor(Math.random() * this.styles.length)];
        const randomColor = this.colors[Math.floor(Math.random() * this.colors.length)];
        const randomIntensity = Math.floor(Math.random() * 70) + 30; // 30-100
        
        this.setEmotion(randomEmotion, randomIntensity);
        this.setStyle(randomStyle, randomColor);
        
        this.emit('avatar.randomized', {
            emotion: randomEmotion,
            style: randomStyle,
            color: randomColor,
            intensity: randomIntensity
        });
    }
    
    /**
     * Reset avatar to defaults
     */
    resetAvatar() {
        this.setEmotion('happy', 50);
        this.setStyle('robot', '#E25303');
        
        this.emit('avatar.reset');
    }
    
    /**
     * Export avatar as image
     */
    exportAvatar() {
        const canvas = this.elements.avatarCanvas;
        if (!canvas) return;
        
        canvas.toBlob(blob => {
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `m1k3-avatar-${Date.now()}.png`;
            a.click();
            URL.revokeObjectURL(url);
        });
        
        this.emit('avatar.exported');
    }
    
    /**
     * Resize canvas
     */
    resizeCanvas() {
        const canvas = this.elements.avatarCanvas;
        const particleCanvas = this.elements.particleCanvas;
        const container = this.elements.canvasContainer;
        
        if (!canvas || !container) return;
        
        const rect = container.getBoundingClientRect();
        const devicePixelRatio = window.devicePixelRatio || 1;
        const isMobile = window.innerWidth <= 768;
        
        // Calculate canvas size
        let canvasWidth = Math.floor(rect.width * 0.95);
        let canvasHeight = Math.floor(rect.height * 0.95);
        
        // Maintain aspect ratio for avatar
        const aspectRatio = 4/3;
        if (canvasWidth / canvasHeight > aspectRatio) {
            canvasWidth = Math.floor(canvasHeight * aspectRatio);
        } else {
            canvasHeight = Math.floor(canvasWidth / aspectRatio);
        }
        
        // Set CSS size
        canvas.style.width = canvasWidth + 'px';
        canvas.style.height = canvasHeight + 'px';
        
        // Set internal resolution
        const internalWidth = canvasWidth * (isMobile ? 1 : Math.min(devicePixelRatio, 2));
        const internalHeight = canvasHeight * (isMobile ? 1 : Math.min(devicePixelRatio, 2));
        
        canvas.width = internalWidth;
        canvas.height = internalHeight;
        
        // Update pixel engine dimensions
        if (this.pixelEngine && this.pixelEngine.updateDimensions) {
            this.pixelEngine.updateDimensions(canvasWidth, canvasHeight);
        }
        
        // Resize particle canvas
        if (particleCanvas) {
            particleCanvas.style.width = canvasWidth + 'px';
            particleCanvas.style.height = canvasHeight + 'px';
            particleCanvas.width = internalWidth;
            particleCanvas.height = internalHeight;
        }
        
        console.log(`🤖 Canvas resized to ${canvasWidth}x${canvasHeight} (internal: ${internalWidth}x${internalHeight})`);
    }
    
    /**
     * Create particle
     */
    createParticle() {
        const canvas = this.elements.particleCanvas;
        if (!canvas) return;
        
        this.particles.push({
            x: Math.random() * canvas.width,
            y: canvas.height + 10,
            size: Math.random() * 3 + 2,
            speed: Math.random() * 0.8 + 0.3,
            color: `rgba(255, 255, 255, ${Math.random() * 0.6 + 0.4})`,
            life: Math.random() * 3 + 2,
            drift: Math.random() * 0.4 - 0.2
        });
    }
    
    /**
     * Start fallback animation
     */
    startFallbackAnimation() {
        const animate = () => {
            this.renderFallbackAvatar();
            this.animationFrame = requestAnimationFrame(animate);
        };
        animate();
    }
    
    /**
     * Start particle animation
     */
    startParticleAnimation() {
        const animate = () => {
            this.updateParticles();
            requestAnimationFrame(animate);
        };
        animate();
    }
    
    /**
     * Render fallback avatar
     */
    renderFallbackAvatar() {
        if (!this.ctx) return;
        
        const canvas = this.elements.avatarCanvas;
        const avatarState = this.stateManager.getState('avatar');
        
        this.ctx.clearRect(0, 0, canvas.width, canvas.height);
        
        const centerX = canvas.width / 2;
        const centerY = canvas.height / 2;
        const size = Math.min(canvas.width, canvas.height) * 0.3;
        
        // Draw simple avatar
        this.ctx.fillStyle = avatarState.color || '#E25303';
        this.ctx.beginPath();
        this.ctx.arc(centerX, centerY, size/2, 0, Math.PI * 2);
        this.ctx.fill();
        
        // Draw eyes
        this.ctx.fillStyle = '#FFFFFF';
        this.ctx.beginPath();
        this.ctx.arc(centerX - size/4, centerY - size/6, size/12, 0, Math.PI * 2);
        this.ctx.arc(centerX + size/4, centerY - size/6, size/12, 0, Math.PI * 2);
        this.ctx.fill();
        
        // Draw mouth based on emotion
        this.drawEmotionMouth(centerX, centerY + size/6, size/3, avatarState.emotion);
    }
    
    /**
     * Draw emotion-specific mouth
     */
    drawEmotionMouth(x, y, width, emotion) {
        this.ctx.strokeStyle = '#FFFFFF';
        this.ctx.lineWidth = 3;
        this.ctx.beginPath();
        
        switch (emotion) {
            case 'happy':
            case 'excited':
                this.ctx.arc(x, y - width/4, width/2, 0, Math.PI);
                break;
            case 'sad':
                this.ctx.arc(x, y + width/4, width/2, Math.PI, Math.PI * 2);
                break;
            case 'surprised':
                this.ctx.arc(x, y, width/4, 0, Math.PI * 2);
                break;
            default:
                this.ctx.moveTo(x - width/2, y);
                this.ctx.lineTo(x + width/2, y);
                break;
        }
        
        this.ctx.stroke();
    }
    
    /**
     * Update particles
     */
    updateParticles() {
        if (!this.particleCtx) return;
        
        const canvas = this.elements.particleCanvas;
        this.particleCtx.clearRect(0, 0, canvas.width, canvas.height);
        
        this.particles = this.particles.filter(particle => {
            particle.y -= particle.speed;
            particle.x += particle.drift;
            particle.life -= 0.008;
            
            if (particle.x < 0) particle.x = canvas.width;
            if (particle.x > canvas.width) particle.x = 0;
            
            if (particle.life > 0 && particle.y > -particle.size) {
                this.particleCtx.globalAlpha = Math.min(particle.life, 1);
                this.particleCtx.fillStyle = particle.color;
                
                this.particleCtx.beginPath();
                this.particleCtx.arc(particle.x, particle.y, particle.size/2, 0, Math.PI * 2);
                this.particleCtx.fill();
                
                return true;
            }
            return false;
        });
        
        // Create new particles
        if (Math.random() > 0.92) {
            this.createParticle();
        }
    }
    
    /**
     * Get emotion emoji
     */
    getEmotionEmoji(emotion) {
        const emojiMap = {
            'happy': '😊',
            'sad': '😢',
            'angry': '😠',
            'surprised': '😲',
            'love': '😍',
            'thinking': '🤔',
            'sleepy': '😴',
            'excited': '🤩'
        };
        return emojiMap[emotion] || '😊';
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
        if (this.animationFrame) {
            cancelAnimationFrame(this.animationFrame);
        }
        
        if (this.pixelEngine && this.pixelEngine.destroy) {
            this.pixelEngine.destroy();
        }
        
        clearTimeout(this.resizeTimeout);
        
        console.log('🤖 AvatarController destroyed');
    }
}

// Export for global use
window.AvatarController = AvatarController;