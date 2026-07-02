/**
 * Gameboy Color Pixel Engine for M1K3
 * Multi-purpose pixel rendering system: avatars, text, graphs, e-ink
 */

class GameboyPixelEngine {
    constructor(canvas, options = {}) {
        console.log('🚀 GameboyPixelEngine constructor called');
        
        this.canvas = canvas;
        console.log('📺 Canvas element:', canvas);
        
        this.ctx = canvas.getContext('2d');
        console.log('🎨 Canvas context:', this.ctx);
        
        if (!this.ctx) {
            console.error('❌ Failed to get canvas context!');
            throw new Error('Canvas context is null');
        }
        
        this.ctx.imageSmoothingEnabled = false;
        console.log('🔧 Image smoothing disabled');
        
        // Configuration with adaptive scaling
        this.basePixelSize = options.pixelSize || 8;
        this.minPixelSize = options.minPixelSize || 2;
        this.maxPixelSize = options.maxPixelSize || 16;
        this.canvasWidth = canvas.width;
        this.canvasHeight = canvas.height;
        this.containerWidth = canvas.style.width ? parseInt(canvas.style.width) : canvas.width;
        this.containerHeight = canvas.style.height ? parseInt(canvas.style.height) : canvas.height;
        this.devicePixelRatio = window.devicePixelRatio || 1;
        
        // Adaptive pixel size calculation
        this.pixelSize = this.calculateOptimalPixelSize();
        this.gridWidth = Math.floor(this.containerWidth / this.pixelSize);
        this.gridHeight = Math.floor(this.containerHeight / this.pixelSize);
        
        // Container adaptation settings
        this.adaptiveMode = options.adaptiveMode !== false; // Default: true
        this.layoutMode = options.layoutMode || 'auto'; // auto, fullscreen, mini, cinematic
        this.aspectRatioMode = options.aspectRatioMode || 'adaptive'; // fixed, adaptive, stretch
        
        console.log(`📐 Canvas: ${this.canvasWidth}x${this.canvasHeight}px`);
        console.log(`🔢 Grid: ${this.gridWidth}x${this.gridHeight} (${this.pixelSize}px per cell)`);
        console.log(`📱 Options:`, options);
        
        // Gameboy Color palettes
        this.palettes = {
            classic: ['#9BBC0F', '#8BAC0F', '#306230', '#0F380F'],
            ocean: ['#6B73FF', '#0000FF', '#0F0F23', '#000040'],
            sunset: ['#FFDC00', '#FF8800', '#CC4400', '#661100'],
            forest: ['#9BBC0F', '#8BAC0F', '#306230', '#0F380F'],
            crystal: ['#E0E6FF', '#B7C7FF', '#6B73FF', '#000040'],
            monochrome: ['#FFFFFF', '#AAAAAA', '#555555', '#000000'],
            eink: ['#FFFFFF', '#000000'] // High contrast e-ink
        };
        
        this.currentPalette = this.palettes.classic;
        
        // Performance optimization
        this.frameCache = new Map();
        this.dirtyRects = [];
        this.lastFrameTime = 0;
        this.targetFPS = 60;
        this.frameInterval = 1000 / this.targetFPS;
        
        // Animation state
        this.animationFrame = null;
        this.isAnimating = false;
        this.animationTime = 0;
        
        // Single canvas direct rendering (no layers)
        // Removed layer system for cleaner pixel rendering
        
        // Pixel fonts - base sizes for adaptive scaling
        this.baseFonts = {
            tiny: { width: 4, height: 6 },
            small: { width: 6, height: 8 },
            normal: { width: 8, height: 12 },
            large: { width: 12, height: 16 }
        };
        
        // Fonts will be initialized after pixel size calculation (see init())
        
        // System state for context-aware rendering
        this.systemState = {
            battery: 100,
            cpu: 0,
            memory: 0,
            network: true,
            networkStrength: 75,
            temperature: 25,
            ecoSavings: 0
        };
        
        // System metrics display settings
        this.systemMetrics = {
            visible: true,
            position: 'bottom'
        };
        
        // Avatar care mechanics
        this.avatarState = {
            health: 100,
            mood: 'happy',
            energy: 100,
            evolution: 0,
            lastInteraction: Date.now()
        };
        
        // LLM-generated pixel art queue
        this.generatedArt = [];
        this.artGenerationQueue = [];
        
        this.init();
    }
    
    // === ADAPTIVE PIXEL SCALING SYSTEM ===
    
    calculateOptimalPixelSize() {
        if (!this.adaptiveMode) {
            return this.basePixelSize;
        }
        
        // Calculate viewing distance factor (larger screens = further viewing distance)
        const screenDiagonal = Math.sqrt(this.containerWidth * this.containerWidth + this.containerHeight * this.containerHeight);
        const viewingDistanceFactor = Math.max(0.5, Math.min(2.0, screenDiagonal / 400));
        
        // Calculate pixel density based on container size and device pixel ratio
        const containerArea = this.containerWidth * this.containerHeight;
        const densityFactor = Math.sqrt(containerArea) / 300; // Base 300px reference
        
        // Adjust for device pixel ratio (high-DPI screens can use smaller pixels)
        const dpiAdjustment = Math.max(0.7, Math.min(1.5, this.devicePixelRatio / 2));
        
        // Calculate optimal pixel size
        let optimalSize = this.basePixelSize * densityFactor * viewingDistanceFactor * dpiAdjustment;
        
        // Apply layout mode adjustments
        switch (this.layoutMode) {
            case 'mini':
                optimalSize *= 0.6; // Smaller pixels for compact display
                break;
            case 'fullscreen':
                optimalSize *= 1.4; // Larger pixels for immersive experience  
                break;
            case 'cinematic':
                optimalSize *= 1.2; // Medium-large pixels for wide screens
                break;
        }
        
        // Ensure pixel size stays within bounds
        optimalSize = Math.max(this.minPixelSize, Math.min(this.maxPixelSize, optimalSize));
        
        // Round to nearest 0.5 for crisp rendering
        optimalSize = Math.round(optimalSize * 2) / 2;
        
        console.log(`🧮 Pixel size calculation: base=${this.basePixelSize}, density=${densityFactor.toFixed(2)}, viewing=${viewingDistanceFactor.toFixed(2)}, dpi=${dpiAdjustment.toFixed(2)}, result=${optimalSize}`);
        
        return optimalSize;
    }
    
    // === ADAPTIVE FONT SCALING SYSTEM ===
    
    createAdaptiveFonts() {
        const fonts = {};
        const fontScale = this.getFontScalingFactor();
        
        for (const [name, baseFont] of Object.entries(this.baseFonts)) {
            const scaledWidth = Math.max(1, Math.round(baseFont.width * fontScale));
            const scaledHeight = Math.max(1, Math.round(baseFont.height * fontScale));
            fonts[name] = new PixelFont(scaledWidth, scaledHeight);
        }
        
        console.log(`📝 Font scaling applied: factor=${fontScale.toFixed(2)}, tiny=${fonts.tiny.charWidth}x${fonts.tiny.charHeight}, normal=${fonts.normal.charWidth}x${fonts.normal.charHeight}`);
        
        return fonts;
    }
    
    getFontScalingFactor() {
        if (!this.adaptiveMode) {
            return 1.0;
        }
        
        // Base font scaling on pixel size ratio
        const basePixelRatio = this.pixelSize / this.basePixelSize;
        
        // Apply layout mode adjustments to font scaling
        let layoutFactor = 1.0;
        switch (this.layoutMode) {
            case 'mini':
                layoutFactor = 0.7; // Smaller fonts for compact display
                break;
            case 'fullscreen':
                layoutFactor = 1.3; // Larger fonts for immersive experience
                break;
            case 'cinematic':
                layoutFactor = 1.1; // Slightly larger fonts for wide screens
                break;
        }
        
        // Calculate final scaling factor
        let scalingFactor = basePixelRatio * layoutFactor;
        
        // Ensure reasonable bounds (fonts shouldn't get too small or too large)
        scalingFactor = Math.max(0.5, Math.min(2.0, scalingFactor));
        
        return scalingFactor;
    }
    
    // Update fonts when pixel size changes
    updateAdaptiveFonts() {
        this.fonts = this.createAdaptiveFonts();
    }
    
    // Get optimal grid dimensions for avatar rendering
    getOptimalAvatarDimensions() {
        const aspectRatio = this.containerWidth / this.containerHeight;
        let avatarWidth, avatarHeight;
        
        // Determine optimal avatar size based on container shape
        if (aspectRatio > 1.5) {
            // Wide container - utilize width more efficiently
            avatarWidth = Math.floor(this.gridWidth * 0.4); // 40% of width
            avatarHeight = Math.floor(this.gridHeight * 0.8); // 80% of height
        } else if (aspectRatio < 0.8) {
            // Tall container - utilize height more efficiently  
            avatarWidth = Math.floor(this.gridWidth * 0.8);  // 80% of width
            avatarHeight = Math.floor(this.gridHeight * 0.4); // 40% of height
        } else {
            // Square-ish container - balanced utilization
            const utilization = Math.min(0.7, Math.max(0.5, Math.min(this.gridWidth, this.gridHeight) / 32));
            avatarWidth = Math.floor(this.gridWidth * utilization);
            avatarHeight = Math.floor(this.gridHeight * utilization);
        }
        
        return { width: avatarWidth, height: avatarHeight, aspectRatio };
    }
    
    // Dynamic positioning based on container shape and layout mode
    calculateAvatarPosition() {
        const { width: avatarWidth, height: avatarHeight, aspectRatio } = this.getOptimalAvatarDimensions();
        let centerX, centerY;
        
        switch (this.layoutMode) {
            case 'fullscreen':
                centerX = Math.floor(this.gridWidth / 2);
                centerY = Math.floor(this.gridHeight / 2);
                break;
                
            case 'mini':
                centerX = Math.floor(this.gridWidth / 2);
                centerY = Math.floor(this.gridHeight / 2);
                break;
                
            case 'cinematic':
                // Off-center positioning for cinematic feel
                centerX = Math.floor(this.gridWidth * 0.35);
                centerY = Math.floor(this.gridHeight / 2);
                break;
                
            default: // 'auto'
                if (aspectRatio > 1.8) {
                    // Ultra-wide - position avatar left-of-center
                    centerX = Math.floor(this.gridWidth * 0.3);
                    centerY = Math.floor(this.gridHeight / 2);
                } else if (aspectRatio > 1.5) {
                    // Wide - position avatar slightly left
                    centerX = Math.floor(this.gridWidth * 0.4);
                    centerY = Math.floor(this.gridHeight / 2);
                } else if (aspectRatio < 0.6) {
                    // Very tall - position avatar upper portion
                    centerX = Math.floor(this.gridWidth / 2);
                    centerY = Math.floor(this.gridHeight * 0.35);
                } else {
                    // Standard positioning
                    centerX = Math.floor(this.gridWidth / 2);
                    centerY = Math.floor((this.gridHeight - 16) / 2); // Account for status area
                }
        }
        
        return { centerX, centerY, avatarWidth, avatarHeight };
    }
    
    init() {
        // Initialize adaptive fonts after pixel size has been calculated
        this.fonts = this.createAdaptiveFonts();
        console.log('📝 Adaptive fonts initialized');
        
        this.clearCanvas();
        this.startAnimationLoop();
    }
    
    // Removed createOffscreenCanvas - using single canvas rendering
    
    // === CORE RENDERING SYSTEM ===
    
    clearCanvas(color = this.currentPalette[0]) {
        this.ctx.fillStyle = color;
        // Use actual canvas internal dimensions, not CSS dimensions
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
    }
    
    setPixel(x, y, colorIndex, layer = 'main') {
        if (x < 0 || x >= this.gridWidth || y < 0 || y >= this.gridHeight) {
            // console.log(`🚫 setPixel out of bounds: (${x}, ${y}) - grid: ${this.gridWidth}x${this.gridHeight}`);
            return;
        }
        if (colorIndex < 0 || colorIndex >= this.currentPalette.length) {
            console.log(`🚫 setPixel invalid color: ${colorIndex} - palette length: ${this.currentPalette.length}`);
            return;
        }
        
        const color = this.currentPalette[colorIndex];
        
        // Calculate scaling factors between CSS size and internal canvas size
        const scaleX = this.canvas.width / this.canvasWidth;
        const scaleY = this.canvas.height / this.canvasHeight;
        
        // Styled pixel rendering with padding and rounded corners
        const pixelX = x * this.pixelSize * scaleX;
        const pixelY = y * this.pixelSize * scaleY;
        const scaledPixelSize = this.pixelSize * Math.min(scaleX, scaleY);
        const padding = Math.max(1, Math.floor(scaledPixelSize * 0.1));
        
        // Debug first few pixels
        if (Math.random() < 0.01) { // Log 1% of pixels to avoid spam
            console.log(`🎨 setPixel: (${x}, ${y}) -> canvas(${pixelX + padding}, ${pixelY + padding}) scale(${scaleX.toFixed(2)}, ${scaleY.toFixed(2)}) color=${color}`);
        }
        const pixelSize = scaledPixelSize - (padding * 2);
        const radius = Math.min(pixelSize / 4, 1); // 1px radius
        
        this.ctx.fillStyle = color;
        
        // Use simple fillRect for maximum compatibility
        this.ctx.fillRect(pixelX + padding, pixelY + padding, pixelSize, pixelSize);
    }
    
    getPixel(x, y, layer = 'main') {
        if (x < 0 || x >= this.gridWidth || y < 0 || y >= this.gridHeight) return 0;
        
        const imageData = this.ctx.getImageData(x * this.pixelSize, y * this.pixelSize, 1, 1);
        const [r, g, b] = imageData.data;
        const hex = '#' + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1).toUpperCase();
        
        return this.currentPalette.indexOf(hex);
    }
    
    markDirty(x, y, width, height) {
        this.dirtyRects.push({ x, y, width, height });
    }
    
    // === GAMEBOY COLOR AVATAR SYSTEM ===
    
    updateSystemState(newState) {
        Object.assign(this.systemState, newState);
        this.updateAvatarCare();
    }
    
    updateDimensions(width, height, options = {}) {
        console.log(`🔄 Updating GameboyPixelEngine dimensions to ${width}x${height}`);
        
        // Update container dimensions
        this.containerWidth = width;
        this.containerHeight = height;
        this.canvasWidth = width;  // Keep for backward compatibility
        this.canvasHeight = height;
        
        // Update device pixel ratio
        this.devicePixelRatio = window.devicePixelRatio || 1;
        
        // Update layout mode if provided
        if (options.layoutMode) {
            this.layoutMode = options.layoutMode;
        }
        
        // Recalculate optimal pixel size for new dimensions
        const oldPixelSize = this.pixelSize;
        this.pixelSize = this.calculateOptimalPixelSize();
        
        // Recalculate grid dimensions with new pixel size
        this.gridWidth = Math.floor(this.containerWidth / this.pixelSize);
        this.gridHeight = Math.floor(this.containerHeight / this.pixelSize);
        
        // Update adaptive fonts if pixel size changed
        if (oldPixelSize !== this.pixelSize) {
            this.updateAdaptiveFonts();
        }
        
        console.log(`📐 Adaptive resize: ${oldPixelSize}px → ${this.pixelSize}px pixels`);
        console.log(`🔢 New grid: ${this.gridWidth}x${this.gridHeight} (${this.pixelSize}px per cell)`);
        console.log(`📱 Canvas internal: ${this.canvas.width}x${this.canvas.height}, container: ${width}x${height}`);
        console.log(`🎨 Layout mode: ${this.layoutMode}, aspect ratio: ${(width/height).toFixed(2)}`);
        
        // Clear frame cache since dimensions changed
        this.frameCache.clear();
        this.dirtyRects = [];
        
        // Clear and redraw
        this.clearCanvas();
        
        // Re-render avatar if in avatar mode
        if (this.mode === 'avatar') {
            this.renderAvatar();
        }
    }
    
    // Dynamic layout mode switching
    setLayoutMode(newMode, options = {}) {
        const oldMode = this.layoutMode;
        this.layoutMode = newMode;
        
        console.log(`🎨 Layout mode changed: ${oldMode} → ${newMode}`);
        
        // Recalculate pixel size for new layout mode
        const oldPixelSize = this.pixelSize;
        this.pixelSize = this.calculateOptimalPixelSize();
        this.gridWidth = Math.floor(this.containerWidth / this.pixelSize);
        this.gridHeight = Math.floor(this.containerHeight / this.pixelSize);
        
        // Update adaptive fonts for new layout mode
        if (oldPixelSize !== this.pixelSize || oldMode !== newMode) {
            this.updateAdaptiveFonts();
        }
        
        // Apply layout-specific options
        if (options.palette) {
            this.currentPalette = this.palettes[options.palette] || this.currentPalette;
        }
        
        if (options.animationSpeed) {
            this.animationSpeed = options.animationSpeed;
        }
        
        // Clear cache and re-render
        this.frameCache.clear();
        this.clearCanvas();
        
        if (this.mode === 'avatar') {
            this.renderAvatar();
        }
        
        return this;
    }
    
    // Get current layout info for debugging/UI
    getLayoutInfo() {
        const { centerX, centerY, avatarWidth, avatarHeight } = this.calculateAvatarPosition();
        
        return {
            layoutMode: this.layoutMode,
            pixelSize: this.pixelSize,
            gridSize: `${this.gridWidth}x${this.gridHeight}`,
            containerSize: `${this.containerWidth}x${this.containerHeight}`,
            aspectRatio: (this.containerWidth / this.containerHeight).toFixed(2),
            avatarPosition: `${centerX},${centerY}`,
            avatarSize: `${avatarWidth}x${avatarHeight}`,
            utilizationPercent: Math.round((avatarWidth * avatarHeight) / (this.gridWidth * this.gridHeight) * 100)
        };
    }
    
    updateAvatarCare() {
        const now = Date.now();
        const timeSinceInteraction = (now - this.avatarState.lastInteraction) / 1000 / 60; // minutes
        
        // Battery affects energy and mood with more granular states
        if (this.systemState.battery < 10) {
            this.avatarState.energy = Math.max(0, this.avatarState.energy - 2);
            this.avatarState.mood = 'critical';
            this.avatarState.health = Math.max(0, this.avatarState.health - 0.5);
        } else if (this.systemState.battery < 20) {
            this.avatarState.energy = Math.max(0, this.avatarState.energy - 1);
            this.avatarState.mood = 'sleepy';
        } else if (this.systemState.battery > 90) {
            this.avatarState.energy = Math.min(100, this.avatarState.energy + 1);
            this.avatarState.mood = 'energetic';
        } else if (this.systemState.battery > 80) {
            this.avatarState.energy = Math.min(100, this.avatarState.energy + 0.5);
        }
        
        // CPU load affects stress levels  
        if (this.systemState.cpu > 90) {
            this.avatarState.mood = 'stressed';
            this.avatarState.energy = Math.max(0, this.avatarState.energy - 0.5);
        } else if (this.systemState.cpu > 70) {
            this.avatarState.mood = 'busy';
        } else if (this.systemState.cpu < 10) {
            this.avatarState.mood = 'relaxed';
            this.avatarState.energy = Math.min(100, this.avatarState.energy + 0.2);
        }
        
        // Temperature affects comfort and mood
        if (this.systemState.temperature > 80) {
            this.avatarState.mood = 'overheating';
            this.avatarState.health = Math.max(0, this.avatarState.health - 0.3);
        } else if (this.systemState.temperature > 70) {
            this.avatarState.mood = 'hot';
        } else if (this.systemState.temperature < 30) {
            this.avatarState.mood = 'cool';
        } else {
            // Optimal temperature range
            this.avatarState.health = Math.min(100, this.avatarState.health + 0.1);
        }
        
        // Network connectivity affects social mood
        if (!this.systemState.network) {
            this.avatarState.mood = 'lonely';
            this.avatarState.energy = Math.max(0, this.avatarState.energy - 0.2);
        } else if (this.systemState.wifi_strength && this.systemState.wifi_strength > 80) {
            this.avatarState.mood = 'connected';
            this.avatarState.energy = Math.min(100, this.avatarState.energy + 0.1);
        } else if (this.systemState.wifi_strength && this.systemState.wifi_strength < 30) {
            this.avatarState.mood = 'poor_signal';
        }
        
        // Memory pressure affects performance mood
        if (this.systemState.memory > 90) {
            this.avatarState.mood = 'memory_pressure';
        }
        
        // Eco achievements create happiness and evolution
        if (this.systemState.ecoSavings > 500) {
            this.avatarState.health = Math.min(100, this.avatarState.health + 0.3);
            this.avatarState.mood = 'eco_champion';
            this.avatarState.evolution = Math.min(5, this.avatarState.evolution + 0.02);
        } else if (this.systemState.ecoSavings > 100) {
            this.avatarState.health = Math.min(100, this.avatarState.health + 0.1);
            this.avatarState.mood = 'eco_happy';
            this.avatarState.evolution = Math.min(5, this.avatarState.evolution + 0.01);
        }
        
        // Noise levels affect mood (if available)
        if (this.systemState.noise_level) {
            if (this.systemState.noise_level > 80) {
                this.avatarState.mood = 'noisy';
                this.avatarState.energy = Math.max(0, this.avatarState.energy - 0.3);
            } else if (this.systemState.noise_level < 20) {
                this.avatarState.mood = 'peaceful';
                this.avatarState.energy = Math.min(100, this.avatarState.energy + 0.2);
            }
        }
        
        // Time-based care mechanics
        if (timeSinceInteraction > 180) { // 3 hours - very neglected
            this.avatarState.health = Math.max(0, this.avatarState.health - 1);
            this.avatarState.mood = 'neglected';
        } else if (timeSinceInteraction > 60) { // 1 hour - needs attention
            this.avatarState.health = Math.max(0, this.avatarState.health - 0.5);
            this.avatarState.mood = 'bored';
        } else if (timeSinceInteraction < 5) { // Recent interaction
            this.avatarState.mood = 'engaged';
            this.avatarState.energy = Math.min(100, this.avatarState.energy + 0.5);
        }
        
        // Evolution based on overall care quality
        const careQuality = (this.avatarState.health + this.avatarState.energy) / 2;
        if (careQuality > 95) {
            this.avatarState.evolution = Math.min(5, this.avatarState.evolution + 0.02);
        } else if (careQuality > 80) {
            this.avatarState.evolution = Math.min(5, this.avatarState.evolution + 0.01);
        } else if (careQuality < 20) {
            // Devolution due to poor care
            this.avatarState.evolution = Math.max(0, this.avatarState.evolution - 0.01);
        }
        
        // Mood priority system (most critical conditions override others)
        if (this.systemState.battery < 10) this.avatarState.mood = 'critical';
        else if (this.systemState.temperature > 80) this.avatarState.mood = 'overheating';
        else if (this.systemState.cpu > 90) this.avatarState.mood = 'stressed';
        else if (timeSinceInteraction > 180) this.avatarState.mood = 'neglected';
        
        // Log care state changes for debugging
        console.log('🧘 Avatar Care Update:', {
            health: Math.round(this.avatarState.health),
            energy: Math.round(this.avatarState.energy),
            mood: this.avatarState.mood,
            evolution: this.avatarState.evolution.toFixed(2),
            careQuality: Math.round(careQuality)
        });
    }
    
    renderAvatar() {
        console.log('🎭 renderAvatar() called with adaptive positioning');
        
        // Use new adaptive positioning system
        const { centerX, centerY, avatarWidth, avatarHeight } = this.calculateAvatarPosition();
        const aspectRatio = this.containerWidth / this.containerHeight;
        
        console.log(`🎯 Adaptive avatar positioning: center(${centerX}, ${centerY}), size(${avatarWidth}x${avatarHeight})`);
        console.log(`📐 Container: ${this.containerWidth}x${this.containerHeight}, aspect ratio: ${aspectRatio.toFixed(2)}, layout: ${this.layoutMode}`);
        
        // Apply continuous animations (breathing, jitter, etc.)
        this.applyAvatarAnimations(centerX, centerY, this.ctx);
        
        // Apply jitter from system load
        const jitterX = this.avatarState.jitterX || 0;
        const jitterY = this.avatarState.jitterY || 0;
        const actualCenterX = centerX + Math.floor(jitterX);
        const actualCenterY = centerY + Math.floor(jitterY);
        
        // Avatar body with adaptive sizing based on available space
        const maxAvatarSize = Math.min(avatarWidth, avatarHeight) * 0.6; // Use 60% of available space
        const baseSize = Math.max(6, Math.min(maxAvatarSize, 8 + this.avatarState.evolution * 2));
        const breathScale = this.avatarState.breathScale || 1.0;
        const size = Math.floor(baseSize * breathScale);
        
        // Scale avatar features based on pixel density
        const featureScale = Math.max(0.5, Math.min(2.0, this.pixelSize / 8));
        const scaledSize = Math.floor(size * featureScale);
        
        console.log(`🔮 Adaptive avatar: size=${scaledSize} (base=${size}), pixel scale=${featureScale.toFixed(2)}, evolution=${this.avatarState.evolution}, mood=${this.avatarState.mood}`);
        
        this.renderAvatarBody(actualCenterX, actualCenterY, scaledSize, this.ctx);
        
        // Eyes with blinking animation (scaled)
        if (this.shouldBlink()) {
            this.renderAvatarEyes(actualCenterX, actualCenterY, 'closed', this.ctx, featureScale);
        } else {
            this.renderAvatarEyes(actualCenterX, actualCenterY, this.avatarState.mood, this.ctx, featureScale);
        }
        
        // Mouth based on mood (scaled)
        this.renderAvatarMouth(actualCenterX, actualCenterY, this.avatarState.mood, this.ctx, featureScale);
        
        // Status indicators (but not system metrics - those go in bottom strip)
        this.renderAvatarStatus(this.ctx);
        
        console.log('✅ renderAvatar() completed');
    }
    
    renderAvatarBody(x, y, size, ctx) {
        const colorIndex = this.getBodyColorFromMood();
        const evolution = Math.floor(this.avatarState.evolution);
        
        console.log(`👤 renderAvatarBody: pos(${x}, ${y}), size=${size}, colorIndex=${colorIndex}, evolution=${evolution}`);
        
        // Different body shapes based on evolution
        switch(evolution) {
            case 0: // Basic circle
                console.log('⭕ Drawing circle');
                this.drawCircle(x, y, size, colorIndex);
                break;
            case 1: // Rounded square
                console.log('⬜ Drawing rounded square');
                this.drawRoundedRect(x-size, y-size, size*2, size*2, 1, colorIndex);
                break;
            case 2: // Diamond
                console.log('💎 Drawing diamond');
                this.drawDiamond(x, y, size, colorIndex);
                break;
            case 3: // Complex shape
                console.log('🌟 Drawing complex shape');
                this.drawComplexShape(x, y, size, colorIndex);
                break;
            default: // Masterpiece form
                console.log('✨ Drawing masterpiece form');
                this.drawMasterpieceForm(x, y, size, colorIndex);
        }
        console.log('👤 renderAvatarBody completed');
    }
    
    renderAvatarEyes(x, y, mood, ctx) {
        const eyePositions = [
            {x: x - 2, y: y - 1},
            {x: x + 2, y: y - 1}
        ];
        
        eyePositions.forEach(pos => {
            switch(mood) {
                case 'happy':
                case 'eco_happy':
                case 'eco_champion':
                case 'connected':
                case 'energetic':
                    this.setPixel(pos.x, pos.y, 3); // Dark happy eyes
                    break;
                case 'sad':
                case 'lonely':
                case 'neglected':
                case 'bored':
                    this.setPixel(pos.x, pos.y, 3);
                    this.setPixel(pos.x, pos.y + 1, 3); // Droopy eyes
                    break;
                case 'sleepy':
                case 'relaxed':
                case 'peaceful':
                    this.drawHorizontalLine(pos.x, pos.y, 1, 3);
                    break;
                case 'critical':
                case 'overheating':
                case 'stressed':
                    // Wide stressed eyes
                    this.setPixel(pos.x - 1, pos.y, 3);
                    this.setPixel(pos.x, pos.y, 3);
                    this.setPixel(pos.x + 1, pos.y, 3);
                    break;
                case 'hot':
                case 'busy':
                case 'noisy':
                    // Squinting
                    this.drawHorizontalLine(pos.x, pos.y, 1, 2);
                    break;
                case 'cool':
                case 'poor_signal':
                case 'memory_pressure':
                    // Neutral/concerned
                    this.setPixel(pos.x, pos.y, 2);
                    break;
                case 'engaged':
                    // Bright alert eyes
                    this.setPixel(pos.x, pos.y, 3);
                    this.setPixel(pos.x, pos.y - 1, 1); // Highlight
                    break;
                case 'closed':
                    this.drawHorizontalLine(pos.x, pos.y, 1, 3);
                    break;
                default:
                    this.setPixel(pos.x, pos.y, 2);
            }
        });
    }
    
    renderAvatarMouth(x, y, mood, ctx) {
        switch(mood) {
            case 'happy':
            case 'eco_happy':
            case 'eco_champion':
            case 'connected':
            case 'energetic':
            case 'engaged':
                // Smile
                this.setPixel(x - 1, y + 1, 3);
                this.setPixel(x, y + 2, 3);
                this.setPixel(x + 1, y + 1, 3);
                break;
            case 'sad':
            case 'lonely':
            case 'neglected':
            case 'bored':
                // Frown
                this.setPixel(x - 1, y + 2, 3);
                this.setPixel(x, y + 1, 3);
                this.setPixel(x + 1, y + 2, 3);
                break;
            case 'sleepy':
            case 'relaxed':
            case 'peaceful':
                // Small o
                this.setPixel(x, y + 1, 3);
                break;
            case 'critical':
            case 'overheating':
            case 'stressed':
                // Wide open distressed mouth
                this.drawRect(x - 1, y + 1, 3, 2, 3);
                break;
            case 'hot':
            case 'noisy':
                // Panting
                this.drawRect(x - 1, y + 1, 3, 1, 3);
                break;
            case 'busy':
            case 'memory_pressure':
                // Tight line (concentration)
                this.drawHorizontalLine(x - 1, y + 1, 3, 3);
                break;
            case 'cool':
            case 'poor_signal':
                // Slight concern
                this.setPixel(x - 1, y + 1, 2);
                this.setPixel(x + 1, y + 1, 2);
                break;
            default:
                this.setPixel(x, y + 1, 2);
        }
    }
    
    getBodyColorFromMood() {
        switch(this.avatarState.mood) {
            // Critical/emergency states - darkest color
            case 'critical':
            case 'overheating':
            case 'neglected':
                return 3; // Darkest
            
            // Negative states - dark color
            case 'sad':
            case 'lonely':
            case 'stressed':
            case 'bored':
                return 3; // Dark
                
            // Hot/active states - warm color
            case 'hot':
            case 'busy':
            case 'noisy':
            case 'energetic':
                return 2; // Orange/warm
                
            // Cool/calm states - cool color  
            case 'cool':
            case 'relaxed':
            case 'peaceful':
            case 'sleepy':
                return 1; // Cool blue
                
            // Eco/positive states - bright color
            case 'eco_happy':
            case 'eco_champion':
            case 'connected':
            case 'engaged':
                return 1; // Bright green
                
            // Neutral/tech states - medium color
            case 'poor_signal':
            case 'memory_pressure':
                return 2; // Medium
                
            // Default happy state
            case 'happy':
            default:
                return 2; // Standard orange
        }
    }
    
    renderAvatarStatus(ctx) {
        // Render status indicators around the avatar
        const indicatorSize = 2;
        const spacing = 4;
        
        // Health indicator (top-left)
        const healthLevel = Math.floor(this.avatarState.health / 25);
        for (let i = 0; i < healthLevel; i++) {
            this.setPixel(2 + (i * spacing), 2, 2); // Red hearts
        }
        
        // Energy indicator (top-right)
        const energyLevel = Math.floor(this.avatarState.energy / 25);
        for (let i = 0; i < energyLevel; i++) {
            this.setPixel(this.gridWidth - 6 - (i * spacing), 2, 1); // Blue energy
        }
        
        // Evolution indicator (bottom-center)
        const evoLevel = Math.floor(this.avatarState.evolution);
        if (evoLevel > 0) {
            for (let i = 0; i < evoLevel; i++) {
                this.setPixel(this.gridWidth/2 - 2 + i, this.gridHeight - 4, 3);
            }
        }
        
        // Mood indicator (small color dot)
        const moodColor = this.getBodyColorFromMood();
        this.setPixel(2, this.gridHeight - 4, moodColor);
    }
    
    // === PIXEL TEXT RENDERING ===
    
    renderText(text, x, y, font = 'normal', colorIndex = 3, layer = 'text') {
        const pixelFont = this.fonts[font];
        let currentX = x;
        
        for (let char of text) {
            const charData = pixelFont.getChar(char);
            if (charData) {
                this.renderCharacter(charData, currentX, y, colorIndex, layer);
                currentX += pixelFont.charWidth + 1;
            }
        }
    }
    
    renderScrollingText(text, x, y, speed = 1, font = 'normal', colorIndex = 3) {
        const scrollOffset = Math.floor(this.animationTime * speed) % (text.length * 8);
        this.renderText(text, x - scrollOffset, y, font, colorIndex, 'text');
        // Wrap around
        this.renderText(text, x - scrollOffset + (text.length * 8), y, font, colorIndex, 'text');
    }
    
    renderCharacter(charData, x, y, colorIndex, layer) {
        for (let row = 0; row < charData.length; row++) {
            for (let col = 0; col < charData[row].length; col++) {
                if (charData[row][col]) {
                    this.setPixel(x + col, y + row, colorIndex, layer);
                }
            }
        }
    }
    
    // === ENHANCED TEXT RENDERING ===
    
    renderCenteredText(text, centerX, y, font = 'normal', colorIndex = 3, layer = 'text') {
        const textWidth = this.calculateTextWidth(text, font);
        const startX = centerX - Math.floor(textWidth / 2);
        this.renderText(text, startX, y, font, colorIndex, layer);
    }
    
    renderTextWithShadow(text, x, y, font = 'normal', textColor = 3, shadowColor = 0, layer = 'text') {
        // Render shadow first (offset by 1,1)
        this.renderText(text, x + 1, y + 1, font, shadowColor, layer);
        // Render main text
        this.renderText(text, x, y, font, textColor, layer);
    }
    
    renderTextBox(text, x, y, width, height, font = 'normal', textColor = 3, bgColor = 0, layer = 'text') {
        // Draw background box
        this.drawFilledRect(x, y, width, height, bgColor, layer);
        this.drawRect(x, y, width, height, textColor, layer);
        
        // Word wrap and render text inside box
        const lines = this.wrapText(text, width - 2, font);
        const lineHeight = this.fonts[font].charHeight + 1;
        
        for (let i = 0; i < lines.length && i * lineHeight < height - 2; i++) {
            this.renderText(lines[i], x + 1, y + 1 + (i * lineHeight), font, textColor, layer);
        }
    }
    
    renderBlinkingText(text, x, y, font = 'normal', colorIndex = 3, layer = 'text', blinkSpeed = 1) {
        // Blink based on animation time
        const blink = Math.sin(this.animationTime * blinkSpeed * 4) > 0;
        if (blink) {
            this.renderText(text, x, y, font, colorIndex, layer);
        }
    }
    
    renderAnimatedText(text, x, y, font = 'normal', colorIndex = 3, layer = 'text', style = 'typewriter') {
        switch (style) {
            case 'typewriter':
                const charsToShow = Math.floor(this.animationTime * 10) % (text.length + 10);
                const visibleText = text.substring(0, Math.max(0, charsToShow));
                this.renderText(visibleText, x, y, font, colorIndex, layer);
                break;
                
            case 'wave':
                for (let i = 0; i < text.length; i++) {
                    const char = text[i];
                    const offsetY = Math.sin(this.animationTime * 4 + i * 0.5) * 2;
                    const charX = x + (i * (this.fonts[font].charWidth + 1));
                    const charData = this.fonts[font].getChar(char);
                    if (charData) {
                        this.renderCharacter(charData, charX, y + offsetY, colorIndex, layer);
                    }
                }
                break;
                
            case 'rainbow':
                for (let i = 0; i < text.length; i++) {
                    const char = text[i];
                    const colorIndex = (Math.floor(this.animationTime * 4 + i) % 3) + 1;
                    const charX = x + (i * (this.fonts[font].charWidth + 1));
                    const charData = this.fonts[font].getChar(char);
                    if (charData) {
                        this.renderCharacter(charData, charX, y, colorIndex, layer);
                    }
                }
                break;
                
            default:
                this.renderText(text, x, y, font, colorIndex, layer);
        }
    }
    
    calculateTextWidth(text, font = 'normal') {
        const pixelFont = this.fonts[font];
        return text.length * (pixelFont.charWidth + 1) - 1; // -1 for no spacing after last char
    }
    
    calculateTextHeight(font = 'normal') {
        return this.fonts[font].charHeight;
    }
    
    wrapText(text, maxWidth, font = 'normal') {
        const words = text.split(' ');
        const lines = [];
        let currentLine = '';
        
        for (const word of words) {
            const testLine = currentLine ? currentLine + ' ' + word : word;
            const testWidth = this.calculateTextWidth(testLine, font);
            
            if (testWidth <= maxWidth) {
                currentLine = testLine;
            } else {
                if (currentLine) {
                    lines.push(currentLine);
                    currentLine = word;
                } else {
                    // Word is too long, break it
                    lines.push(word.substring(0, Math.floor(maxWidth / (this.fonts[font].charWidth + 1))));
                    currentLine = word.substring(Math.floor(maxWidth / (this.fonts[font].charWidth + 1)));
                }
            }
        }
        
        if (currentLine) {
            lines.push(currentLine);
        }
        
        return lines;
    }
    
    renderSystemStatusText() {
        // Clear text layer
        this.layers.text.ctx.clearRect(0, 0, this.canvasWidth, this.canvasHeight);
        
        // System status with context-aware coloring
        const batteryColor = this.systemState.battery > 20 ? 1 : 2;
        const cpuColor = this.systemState.cpu > 80 ? 2 : 1;
        const moodText = this.avatarState.mood.charAt(0).toUpperCase() + this.avatarState.mood.slice(1);
        
        // Render status information
        this.renderText(`BAT: ${Math.round(this.systemState.battery)}%`, 2, 2, 'tiny', batteryColor, 'text');
        this.renderText(`CPU: ${Math.round(this.systemState.cpu)}%`, 2, 8, 'tiny', cpuColor, 'text');
        this.renderText(`MOOD: ${moodText}`, 2, 14, 'tiny', 3, 'text');
        
        // Health and energy bars as text
        const healthBars = '♥'.repeat(Math.floor(this.avatarState.health / 20));
        const energyBars = '★'.repeat(Math.floor(this.avatarState.energy / 20));
        
        this.renderText(healthBars, 2, 20, 'tiny', 2, 'text');
        this.renderText(energyBars, 2, 26, 'tiny', 1, 'text');
        
        // Evolution indicator
        const evoLevel = Math.floor(this.avatarState.evolution);
        this.renderText(`EVO: ${evoLevel}`, this.gridWidth - 12, 2, 'tiny', 1, 'text');
    }
    
    // === DATA VISUALIZATION ===
    
    renderSystemMetrics() {
        if (!this.systemMetrics || !this.systemMetrics.visible) return;
        
        // Render metrics in bottom 16 rows (y: 80-96)
        const metricsStartY = this.gridHeight - 16;
        
        // Draw background separator line
        for (let x = 0; x < this.gridWidth; x++) {
            this.setPixel(x, metricsStartY, 2); // Dark line separator
        }
        
        // Layout metrics in columns across the bottom strip
        this.renderMetricColumn(5, metricsStartY + 2, 'BAT', `${this.systemState.battery}%`, this.systemState.battery < 20 ? 3 : 1);
        this.renderMetricColumn(25, metricsStartY + 2, 'CPU', `${this.systemState.cpu}%`, this.systemState.cpu > 80 ? 3 : 1);
        this.renderMetricColumn(45, metricsStartY + 2, 'TMP', `${this.systemState.temperature}°`, this.systemState.temperature > 70 ? 3 : 1);
        this.renderMetricColumn(65, metricsStartY + 2, 'MEM', `${this.systemState.memory}%`, this.systemState.memory > 80 ? 3 : 1);
        this.renderMetricColumn(85, metricsStartY + 2, 'NET', this.getNetworkBars(), 1);
        
        // Avatar status metrics
        this.renderMetricColumn(105, metricsStartY + 2, 'MOOD', this.getMoodIcon(), 1);
        this.renderMetricColumn(120, metricsStartY + 8, 'LVL', `${this.avatarState.evolution}`, 1);
    }
    
    renderMetricColumn(x, y, label, value, colorIndex) {
        // Render label (small text)
        this.renderSmallText(label, x, y, 2);
        // Render value below label
        this.renderSmallText(value.toString(), x, y + 6, colorIndex);
    }
    
    renderSmallText(text, x, y, colorIndex) {
        // Simple 3x5 pixel font for metrics
        const chars = {
            'A': [0b111, 0b101, 0b111, 0b101, 0b101],
            'B': [0b110, 0b101, 0b110, 0b101, 0b110],
            'C': [0b111, 0b100, 0b100, 0b100, 0b111],
            'T': [0b111, 0b010, 0b010, 0b010, 0b010],
            'P': [0b110, 0b101, 0b110, 0b100, 0b100],
            'M': [0b101, 0b111, 0b101, 0b101, 0b101],
            'E': [0b111, 0b100, 0b110, 0b100, 0b111],
            'U': [0b101, 0b101, 0b101, 0b101, 0b111],
            'N': [0b101, 0b111, 0b111, 0b101, 0b101],
            'L': [0b100, 0b100, 0b100, 0b100, 0b111],
            'V': [0b101, 0b101, 0b101, 0b101, 0b010],
            'O': [0b111, 0b101, 0b101, 0b101, 0b111],
            'D': [0b110, 0b101, 0b101, 0b101, 0b110],
            '0': [0b111, 0b101, 0b101, 0b101, 0b111],
            '1': [0b010, 0b110, 0b010, 0b010, 0b111],
            '2': [0b111, 0b001, 0b111, 0b100, 0b111],
            '3': [0b111, 0b001, 0b111, 0b001, 0b111],
            '4': [0b101, 0b101, 0b111, 0b001, 0b001],
            '5': [0b111, 0b100, 0b111, 0b001, 0b111],
            '6': [0b111, 0b100, 0b111, 0b101, 0b111],
            '7': [0b111, 0b001, 0b001, 0b001, 0b001],
            '8': [0b111, 0b101, 0b111, 0b101, 0b111],
            '9': [0b111, 0b101, 0b111, 0b001, 0b111],
            '%': [0b101, 0b001, 0b010, 0b100, 0b101],
            '°': [0b010, 0b101, 0b010, 0b000, 0b000],
            '●': [0b000, 0b010, 0b111, 0b010, 0b000],
            '○': [0b000, 0b010, 0b101, 0b010, 0b000],
            ' ': [0b000, 0b000, 0b000, 0b000, 0b000]
        };
        
        let currentX = x;
        for (let char of text) {
            const pattern = chars[char] || chars[' '];
            for (let row = 0; row < 5; row++) {
                for (let col = 0; col < 3; col++) {
                    if (pattern[row] & (1 << (2 - col))) {
                        this.setPixel(currentX + col, y + row, colorIndex);
                    }
                }
            }
            currentX += 4; // 3 char width + 1 space
        }
    }
    
    getNetworkBars() {
        const strength = this.systemState.networkStrength;
        const bars = Math.floor(strength / 25);
        return '●'.repeat(bars) + '○'.repeat(4 - bars);
    }
    
    getMoodIcon() {
        const moodIcons = {
            'happy': 'H',
            'sad': 'S', 
            'excited': 'E',
            'thinking': 'T',
            'sleepy': 'Z',
            'angry': 'A',
            'love': 'L',
            'surprised': '!'
        };
        return moodIcons[this.avatarState.mood] || 'H';
    }

    renderBatteryMeter(x, y, percent) {
        // Battery outline
        this.drawRect(x, y, 8, 3, 3, 'ui');
        this.setPixel(x + 8, y + 1, 3, 'ui');
        
        // Battery fill
        const fillWidth = Math.floor((percent / 100) * 6);
        const fillColor = percent > 20 ? 1 : 2;
        this.drawRect(x + 1, y + 1, fillWidth, 1, fillColor, 'ui');
        
        // Percentage text
        this.renderText(`${percent}%`, x, y + 4, 'tiny', 3, 'ui');
    }
    
    renderUsageBar(x, y, percent, label) {
        // Background bar
        this.drawRect(x, y, 10, 1, 0, 'ui');
        
        // Usage fill
        const fillWidth = Math.floor((percent / 100) * 10);
        const fillColor = percent > 80 ? 2 : percent > 50 ? 1 : 3;
        this.drawRect(x, y, fillWidth, 1, fillColor, 'ui');
        
        // Label
        this.renderText(label, x, y + 2, 'tiny', 3, 'ui');
    }
    
    renderNetworkSignal(x, y, connected) {
        if (connected) {
            // Signal bars
            for (let i = 0; i < 4; i++) {
                const barHeight = i + 1;
                this.drawRect(x + i, y + 4 - barHeight, 1, barHeight, 1, 'ui');
            }
        } else {
            // X for no connection
            this.setPixel(x, y, 2, 'ui');
            this.setPixel(x + 1, y + 1, 2, 'ui');
            this.setPixel(x + 2, y + 2, 2, 'ui');
            this.setPixel(x + 2, y, 2, 'ui');
            this.setPixel(x + 1, y + 1, 2, 'ui');
            this.setPixel(x, y + 2, 2, 'ui');
        }
    }
    
    renderEcoMetrics(x, y) {
        // Simple eco tree that grows with savings
        const treeHeight = Math.min(5, Math.floor(this.systemState.ecoSavings / 20));
        
        // Tree trunk
        this.drawRect(x + 1, y + treeHeight, 1, 2, 3, 'ui');
        
        // Tree crown
        for (let i = 0; i < treeHeight; i++) {
            this.drawRect(x, y + i, 3, 1, 1, 'ui');
        }
    }
    
    renderTemperature(x, y, temp) {
        const colorIndex = temp > 70 ? 2 : temp > 50 ? 1 : 3;
        
        // Simple thermometer
        this.drawRect(x, y, 1, 4, 3, 'ui');
        this.setPixel(x, y + 4, colorIndex, 'ui');
    }
    
    // === ADVANCED DATA VISUALIZATION ===
    
    renderLineChart(data, x, y, width, height, colorIndex = 3, layer = 'ui') {
        if (data.length < 2) return;
        
        const maxValue = Math.max(...data);
        const minValue = Math.min(...data);
        const range = maxValue - minValue || 1;
        
        // Draw chart border
        this.drawRect(x, y, width, height, colorIndex, layer);
        
        // Plot data points and connect with lines
        for (let i = 0; i < data.length - 1; i++) {
            const x1 = x + 1 + Math.floor((i / (data.length - 1)) * (width - 2));
            const y1 = y + height - 1 - Math.floor(((data[i] - minValue) / range) * (height - 2));
            const x2 = x + 1 + Math.floor(((i + 1) / (data.length - 1)) * (width - 2));
            const y2 = y + height - 1 - Math.floor(((data[i + 1] - minValue) / range) * (height - 2));
            
            this.drawLine(x1, y1, x2, y2, colorIndex, layer);
        }
        
        // Add value labels
        this.renderText(`${maxValue}`, x + width + 1, y, 'tiny', colorIndex, layer);
        this.renderText(`${minValue}`, x + width + 1, y + height - 6, 'tiny', colorIndex, layer);
    }
    
    renderBarChart(data, labels, x, y, width, height, layer = 'ui') {
        if (data.length === 0) return;
        
        const maxValue = Math.max(...data);
        const barWidth = Math.floor((width - 2) / data.length);
        
        // Draw chart border
        this.drawRect(x, y, width, height, 3, layer);
        
        // Draw bars
        for (let i = 0; i < data.length; i++) {
            const barHeight = Math.floor((data[i] / maxValue) * (height - 2));
            const barX = x + 1 + (i * barWidth);
            const barY = y + height - 1 - barHeight;
            
            // Color code bars based on value
            const colorIndex = data[i] > maxValue * 0.8 ? 2 : data[i] > maxValue * 0.5 ? 1 : 3;
            
            this.drawFilledRect(barX, barY, barWidth - 1, barHeight, colorIndex, layer);
            
            // Add label if provided
            if (labels && labels[i]) {
                this.renderText(labels[i].substring(0, 3), barX, y + height + 1, 'tiny', 3, layer);
            }
        }
        
        // Add max value label
        this.renderText(`${maxValue}`, x + width + 1, y, 'tiny', 3, layer);
    }
    
    renderSparkline(data, x, y, width, colorIndex = 3, layer = 'ui') {
        if (data.length < 2) return;
        
        const maxValue = Math.max(...data);
        const minValue = Math.min(...data);
        const range = maxValue - minValue || 1;
        
        // Draw simple line connecting data points
        for (let i = 0; i < data.length - 1; i++) {
            const x1 = x + Math.floor((i / (data.length - 1)) * width);
            const y1 = y + 3 - Math.floor(((data[i] - minValue) / range) * 3);
            const x2 = x + Math.floor(((i + 1) / (data.length - 1)) * width);
            const y2 = y + 3 - Math.floor(((data[i + 1] - minValue) / range) * 3);
            
            this.drawLine(x1, y1, x2, y2, colorIndex, layer);
        }
        
        // Highlight current value
        const currentX = x + width - 1;
        const currentY = y + 3 - Math.floor(((data[data.length - 1] - minValue) / range) * 3);
        this.setPixel(currentX, currentY, 2, layer);
    }
    
    renderHistogram(data, bins, x, y, width, height, layer = 'ui') {
        if (data.length === 0) return;
        
        const minValue = Math.min(...data);
        const maxValue = Math.max(...data);
        const binSize = (maxValue - minValue) / bins;
        const binCounts = new Array(bins).fill(0);
        
        // Count data points in each bin
        for (const value of data) {
            const binIndex = Math.min(Math.floor((value - minValue) / binSize), bins - 1);
            binCounts[binIndex]++;
        }
        
        // Render as bar chart
        this.renderBarChart(binCounts, null, x, y, width, height, layer);
    }
    
    renderPieChart(data, labels, x, y, radius, layer = 'ui') {
        if (data.length === 0) return;
        
        const total = data.reduce((sum, value) => sum + value, 0);
        let currentAngle = 0;
        
        // Draw pie slices
        for (let i = 0; i < data.length; i++) {
            const sliceAngle = (data[i] / total) * 2 * Math.PI;
            const colorIndex = (i % 3) + 1;
            
            // Simple pie slice representation using filled triangles
            const centerX = x + radius;
            const centerY = y + radius;
            
            // Calculate slice points
            const steps = Math.max(1, Math.floor(sliceAngle * radius / 2));
            for (let step = 0; step < steps; step++) {
                const angle = currentAngle + (step / steps) * sliceAngle;
                const endX = centerX + Math.cos(angle) * radius;
                const endY = centerY + Math.sin(angle) * radius;
                
                this.drawLine(centerX, centerY, Math.floor(endX), Math.floor(endY), colorIndex, layer);
            }
            
            currentAngle += sliceAngle;
        }
        
        // Draw center dot
        this.setPixel(x + radius, y + radius, 3, layer);
    }
    
    renderSystemDashboard() {
        // Comprehensive system metrics dashboard
        this.layers.ui.ctx.clearRect(0, 0, this.canvasWidth, this.canvasHeight);
        
        // Title
        this.renderCenteredText('M1K3 SYSTEM STATUS', this.gridWidth / 2, 2, 'small', 3, 'ui');
        
        // Battery and CPU usage bars with sparklines
        const batteryHistory = this.systemState.batteryHistory || [this.systemState.battery];
        const cpuHistory = this.systemState.cpuHistory || [this.systemState.cpu];
        
        this.renderUsageBar(2, 8, this.systemState.battery, 'BAT');
        this.renderSparkline(batteryHistory, 2, 12, 15, 1, 'ui');
        
        this.renderUsageBar(2, 16, this.systemState.cpu, 'CPU');
        this.renderSparkline(cpuHistory, 2, 20, 15, 2, 'ui');
        
        // Memory and temperature
        this.renderUsageBar(2, 24, this.systemState.memory, 'MEM');
        this.renderTemperature(this.gridWidth - 8, 8, this.systemState.temperature);
        
        // Network and eco metrics
        this.renderNetworkSignal(this.gridWidth - 8, 12, this.systemState.network);
        this.renderEcoMetrics(this.gridWidth - 8, 16);
        
        // Avatar health pie chart
        const healthData = [
            this.avatarState.health,
            this.avatarState.energy,
            100 - this.avatarState.health,
            100 - this.avatarState.energy
        ];
        this.renderPieChart(healthData.filter(v => v > 0), ['Health', 'Energy'], this.gridWidth - 12, 20, 4, 'ui');
        
        // System load over time
        if (this.systemState.loadHistory && this.systemState.loadHistory.length > 1) {
            this.renderLineChart(this.systemState.loadHistory, 2, this.gridHeight - 12, 20, 8, 3, 'ui');
            this.renderText('LOAD', 2, this.gridHeight - 14, 'tiny', 3, 'ui');
        }
    }
    
    renderDataPoint(x, y, value, maxValue, colorIndex = 3, layer = 'ui') {
        // Render a single data point with size based on value
        const size = Math.max(1, Math.floor((value / maxValue) * 3));
        this.drawFilledRect(x, y, size, size, colorIndex, layer);
    }
    
    renderHeatMap(data, x, y, cellSize = 2, layer = 'ui') {
        // 2D heatmap visualization
        for (let row = 0; row < data.length; row++) {
            for (let col = 0; col < data[row].length; col++) {
                const value = data[row][col];
                const intensity = Math.min(3, Math.max(0, Math.floor(value * 4)));
                const cellX = x + (col * cellSize);
                const cellY = y + (row * cellSize);
                
                if (intensity > 0) {
                    this.drawFilledRect(cellX, cellY, cellSize, cellSize, intensity, layer);
                }
            }
        }
    }
    
    // === E-INK RENDERING MODE ===
    
    enableEinkMode() {
        this.currentPalette = this.palettes.eink;
        this.targetFPS = 1; // Very low refresh rate like real e-ink
        this.frameInterval = 1000;
        this.einkMode = true;
        console.log('📖 E-ink mode enabled - High contrast, low refresh rate');
    }
    
    disableEinkMode() {
        this.currentPalette = this.palettes.classic;
        this.targetFPS = 60;
        this.frameInterval = 1000 / this.targetFPS;
        this.einkMode = false;
        console.log('🎮 Standard Gameboy Color mode restored');
    }
    
    toggleEinkMode() {
        if (this.einkMode) {
            this.disableEinkMode();
        } else {
            this.enableEinkMode();
        }
    }
    
    renderEinkDashboard() {
        // Specialized e-ink dashboard with high contrast and minimal updates
        if (!this.einkMode) return;
        
        this.clearCanvas('#FFFFFF'); // White background
        
        // High contrast text-based interface
        this.renderTextWithShadow('M1K3 E-INK DISPLAY', 2, 2, 'normal', 1, 0, 'ui');
        
        // System status in text format for maximum readability
        const statusLines = [
            `Battery: ${Math.round(this.systemState.battery)}%`,
            `CPU: ${Math.round(this.systemState.cpu)}%`,
            `Memory: ${Math.round(this.systemState.memory)}%`,
            `Network: ${this.systemState.network ? 'Connected' : 'Offline'}`,
            `Mood: ${this.avatarState.mood}`,
            `Health: ${Math.round(this.avatarState.health)}%`,
            `Energy: ${Math.round(this.avatarState.energy)}%`,
            `Evolution: Level ${Math.floor(this.avatarState.evolution)}`
        ];
        
        let yPos = 14;
        for (const line of statusLines) {
            this.renderText(line, 2, yPos, 'small', 1, 'ui');
            yPos += 8;
        }
        
        // Simple avatar representation
        const avatarX = this.gridWidth - 16;
        const avatarY = 8;
        this.renderEinkAvatar(avatarX, avatarY);
        
        // Data visualization with dithering
        this.renderWithDithering(() => {
            // Render simple bar charts
            this.renderUsageBar(2, this.gridHeight - 10, this.systemState.battery, 'BAT');
            this.renderUsageBar(2, this.gridHeight - 6, this.systemState.cpu, 'CPU');
        });
    }
    
    renderEinkAvatar(x, y) {
        // Simplified avatar for e-ink display
        const size = 8;
        const centerX = x + size / 2;
        const centerY = y + size / 2;
        
        // Simple geometric shapes based on mood
        switch (this.avatarState.mood) {
            case 'happy':
            case 'eco_happy':
            case 'energetic':
                // Circle
                this.drawCircle(centerX, centerY, size / 2, 1, 'ui');
                break;
            case 'sad':
            case 'lonely':
            case 'bored':
                // Downward triangle
                this.drawLine(centerX, y, x, y + size, 1, 'ui');
                this.drawLine(centerX, y, x + size, y + size, 1, 'ui');
                this.drawLine(x, y + size, x + size, y + size, 1, 'ui');
                break;
            case 'critical':
            case 'stressed':
            case 'overheating':
                // Sharp diamond
                this.drawDiamond(centerX, centerY, size / 2, 1, 'ui');
                break;
            default:
                // Square
                this.drawRect(x, y, size, size, 1, 'ui');
        }
        
        // Health indicator
        const healthBars = Math.floor(this.avatarState.health / 25);
        for (let i = 0; i < healthBars; i++) {
            this.setPixel(x + i, y + size + 2, 1, 'ui');
        }
    }
    
    renderEinkGraph(data, x, y, width, height) {
        // High contrast line graph optimized for e-ink
        if (data.length < 2) return;
        
        const maxValue = Math.max(...data);
        const minValue = Math.min(...data);
        const range = maxValue - minValue || 1;
        
        // Draw axes
        this.drawLine(x, y + height, x + width, y + height, 1, 'ui'); // X-axis
        this.drawLine(x, y, x, y + height, 1, 'ui'); // Y-axis
        
        // Plot data with thick lines for better visibility
        for (let i = 0; i < data.length - 1; i++) {
            const x1 = x + Math.floor((i / (data.length - 1)) * width);
            const y1 = y + height - Math.floor(((data[i] - minValue) / range) * height);
            const x2 = x + Math.floor(((i + 1) / (data.length - 1)) * width);
            const y2 = y + height - Math.floor(((data[i + 1] - minValue) / range) * height);
            
            // Draw thick line by drawing adjacent pixels
            this.drawLine(x1, y1, x2, y2, 1, 'ui');
            this.drawLine(x1 + 1, y1, x2 + 1, y2, 1, 'ui');
        }
    }
    
    renderWithDithering(callback) {
        // Apply Floyd-Steinberg dithering for e-ink
        callback();
        this.applyDithering();
    }
    
    applyDithering() {
        const imageData = this.ctx.getImageData(0, 0, this.canvasWidth, this.canvasHeight);
        const data = imageData.data;
        
        for (let y = 0; y < this.canvasHeight; y++) {
            for (let x = 0; x < this.canvasWidth; x++) {
                const idx = (y * this.canvasWidth + x) * 4;
                const gray = (data[idx] + data[idx + 1] + data[idx + 2]) / 3;
                
                const newGray = gray > 128 ? 255 : 0;
                const error = gray - newGray;
                
                data[idx] = data[idx + 1] = data[idx + 2] = newGray;
                
                // Distribute error (Floyd-Steinberg)
                if (x < this.canvasWidth - 1) {
                    const rightIdx = (y * this.canvasWidth + x + 1) * 4;
                    data[rightIdx] += error * 7 / 16;
                    data[rightIdx + 1] += error * 7 / 16;
                    data[rightIdx + 2] += error * 7 / 16;
                }
                
                if (y < this.canvasHeight - 1) {
                    const belowIdx = ((y + 1) * this.canvasWidth + x) * 4;
                    data[belowIdx] += error * 5 / 16;
                    data[belowIdx + 1] += error * 5 / 16;
                    data[belowIdx + 2] += error * 5 / 16;
                }
            }
        }
        
        this.ctx.putImageData(imageData, 0, 0);
    }
    
    // === LLM PIXEL ART INTEGRATION ===
    
    async requestPixelArt(complexity = 'moderate') {
        const prompt = this.generatePixelArtPrompt(complexity);
        
        try {
            // This would integrate with the M1K3 AI system
            const response = await this.sendToLLM(prompt);
            const parsedArt = this.parsePixelArt(response);
            
            if (parsedArt) {
                this.generatedArt.push(parsedArt);
                this.displayGeneratedArt(parsedArt);
            }
        } catch (error) {
            console.error('Pixel art generation failed:', error);
        }
    }
    
    generatePixelArtPrompt(complexity) {
        const contextualTheme = this.getContextualTheme();
        
        return `Create a ${this.gridWidth}x${this.gridHeight} pixel art of ${contextualTheme} using only these colors: ${this.currentPalette.join(', ')}. 
                Complexity level: ${complexity}. 
                Current system state: battery ${this.systemState.battery}%, mood: ${this.avatarState.mood}.
                Make it reflect the current system condition.`;
    }
    
    getContextualTheme() {
        if (this.systemState.battery < 20) return "a sleepy or low-energy scene";
        if (this.systemState.temperature > 70) return "something hot or fiery";
        if (!this.systemState.network) return "an isolated or offline theme";
        if (this.systemState.ecoSavings > 100) return "nature or environmental theme";
        return "a cheerful digital companion";
    }
    
    displayGeneratedArt(artData) {
        const layer = this.layers.background;
        layer.ctx.clearRect(0, 0, this.canvasWidth, this.canvasHeight);
        
        // Render the pixel art to background layer
        for (let y = 0; y < artData.height; y++) {
            for (let x = 0; x < artData.width; x++) {
                const colorIndex = artData.pixels[y][x];
                this.setPixel(x, y, colorIndex, 'background');
            }
        }
    }
    
    // === ANIMATION SYSTEM ===
    
    startAnimationLoop() {
        console.log('🚀 startAnimationLoop() called');
        this.isAnimating = true;
        console.log('🎬 Animation state set to:', this.isAnimating);
        this.animate();
        console.log('✅ startAnimationLoop() completed');
    }
    
    stopAnimationLoop() {
        this.isAnimating = false;
        if (this.animationFrame) {
            cancelAnimationFrame(this.animationFrame);
        }
    }
    
    animate() {
        if (!this.isAnimating) {
            console.log('❌ animate() called but isAnimating is false');
            return;
        }
        
        const now = performance.now();
        const deltaTime = now - this.lastFrameTime;
        
        // Log first few animation frames
        if (this.animationTime < 1000) {
            console.log(`⏰ animate() called - deltaTime: ${deltaTime.toFixed(1)}ms, frameInterval: ${this.frameInterval}ms`);
        }
        
        if (deltaTime >= this.frameInterval) {
            this.animationTime += deltaTime;
            
            // Log frame renders for first second
            if (this.animationTime < 1000) {
                console.log(`🖼️ Rendering frame - animationTime: ${this.animationTime.toFixed(1)}ms`);
            }
            
            // Update avatar care mechanics
            this.updateAvatarCare();
            
            // Render all layers
            this.renderFrame();
            
            this.lastFrameTime = now;
        }
        
        this.animationFrame = requestAnimationFrame(() => this.animate());
    }
    
    renderFrame() {
        // Log first few frame renders
        if (this.animationTime < 1000) {
            console.log('🎬 renderFrame() called');
        }
        
        // Clear entire canvas with background color
        const bgColor = this.currentPalette[0];
        if (this.animationTime < 1000) {
            console.log(`🧹 clearCanvas() with color: ${bgColor}`);
        }
        this.clearCanvas(bgColor);
        
        // Direct single-canvas rendering (no layers)
        this.renderAvatar();
        this.renderSystemMetrics();
        
        // Apply e-ink dithering if enabled
        if (this.currentPalette === this.palettes.eink) {
            this.applyDithering();
        }
        
        if (this.animationTime < 1000) {
            console.log('✅ renderFrame() completed');
        }
    }
    
    shouldBlink() {
        // Blink every 3-7 seconds randomly
        const blinkCycle = 5000; // 5 seconds
        const blinkDuration = 150; // 150ms
        const cyclePos = (this.animationTime % blinkCycle) / blinkCycle;
        
        return cyclePos > 0.97; // Blink for last 3% of cycle
    }
    
    applyAvatarAnimations(centerX, centerY, ctx) {
        // Breathing animation based on avatar state
        let breathSpeed = 0.002; // Base breathing speed
        let breathIntensity = 1.0; // Base breathing intensity
        
        // Adjust breathing based on avatar mood and state
        switch(this.avatarState.mood) {
            case 'excited':
            case 'happy':
                breathSpeed = 0.003; // Faster breathing when excited
                breathIntensity = 1.2;
                break;
            case 'sleepy':
            case 'relaxed':
                breathSpeed = 0.001; // Slower breathing when sleepy
                breathIntensity = 0.8;
                break;
            case 'stressed':
            case 'critical':
                breathSpeed = 0.004; // Rapid breathing when stressed
                breathIntensity = 1.5;
                break;
            case 'thinking':
                breathSpeed = 0.0015; // Calm, contemplative breathing
                breathIntensity = 0.9;
                break;
        }
        
        // Apply energy level to breathing
        if (this.avatarState.energy < 30) {
            breathSpeed *= 0.7; // Slower when low energy
            breathIntensity *= 0.6;
        } else if (this.avatarState.energy > 80) {
            breathSpeed *= 1.2; // Faster when high energy
            breathIntensity *= 1.1;
        }
        
        const breathCycle = Math.sin(this.animationTime * breathSpeed) * breathIntensity;
        
        // Apply breathing to avatar size (subtle scale effect)
        const breathScale = 1.0 + (breathCycle * 0.03); // 3% size variation
        this.avatarState.breathScale = breathScale;
        
        // Subtle movements based on system state
        if (this.systemState.cpu > 50) {
            // Vibration when under load
            const jitter = Math.sin(this.animationTime * 0.01) * 0.5;
            this.avatarState.jitterX = jitter * 0.3;
            this.avatarState.jitterY = jitter * 0.2;
        } else {
            this.avatarState.jitterX = 0;
            this.avatarState.jitterY = 0;
        }
        
        // Color pulsing for special states
        if (this.avatarState.mood === 'excited') {
            const pulse = Math.sin(this.animationTime * 0.005) * 0.5 + 0.5;
            this.avatarState.colorPulse = pulse;
        } else {
            this.avatarState.colorPulse = 0;
        }
    }
    
    // === ENHANCED AI INTEGRATION METHODS ===
    
    setEmotionWithMetadata(emotion, intensity, metadata) {
        this.avatarState.mood = emotion;
        this.avatarState.intensity = intensity;
        
        // Store classification metadata for specialized behaviors
        this.avatarState.metadata = metadata || {};
        
        // Apply intent-specific avatar modifications
        const intent = metadata?.intent;
        if (intent) {
            this.applyIntentSpecificBehavior(intent, metadata);
        }
        
        // Update the avatar appearance
        this.renderAvatar();
    }
    
    createEnhancedParticles(emotion, intensity, metadata) {
        const intent = metadata?.intent;
        const confidence = metadata?.confidence || 0.5;
        
        // Create context-aware particles based on AI intent
        switch(intent) {
            case 'mathematical_calculation':
                this.createMathematicalParticles(intensity, confidence);
                break;
            case 'creative_writing':
                this.createCreativeParticles(intensity, confidence);
                break;
            case 'code_debugging':
                this.createTechnicalParticles(intensity, confidence);
                break;
            case 'explanation_request':
                this.createKnowledgeParticles(intensity, confidence);
                break;
            default:
                this.createEmotionParticles(emotion, intensity);
        }
    }
    
    setThinkingPhase(phase, progress, confidence) {
        this.avatarState.thinkingPhase = phase;
        this.avatarState.thinkingProgress = progress;
        this.avatarState.thinkingConfidence = confidence;
        
        // Update visual representation based on thinking phase
        this.updateThinkingVisualization(phase, progress, confidence);
    }
    
    setGenerationActivity(tokenCount, generationSpeed) {
        this.avatarState.generationTokens = tokenCount;
        this.avatarState.generationSpeed = generationSpeed;
        
        // Adjust avatar breathing/animation speed based on generation activity
        const activityLevel = Math.min(generationSpeed / 10, 1.0); // Normalize to 0-1
        this.avatarState.activityLevel = activityLevel;
        
        // Create generation particles
        this.createGenerationParticles(tokenCount, generationSpeed);
    }
    
    applyIntentSpecificBehavior(intent, metadata) {
        const confidence = metadata?.confidence || 0.5;
        const strategy = metadata?.response_strategy || 'balanced';
        
        // Modify avatar appearance based on AI intent
        switch(intent) {
            case 'mathematical_calculation':
                // Sharp, focused appearance for math
                this.avatarState.visualStyle = 'analytical';
                this.avatarState.focusLevel = confidence;
                this.currentPalette = confidence > 0.8 ? this.palettes.crystal : this.palettes.classic;
                break;
                
            case 'creative_writing':
                // Flowing, artistic appearance for creativity
                this.avatarState.visualStyle = 'creative';
                this.avatarState.inspirationLevel = confidence;
                this.currentPalette = this.palettes.sunset;
                break;
                
            case 'code_debugging':
                // Technical, systematic appearance
                this.avatarState.visualStyle = 'technical';
                this.avatarState.debugMode = true;
                this.currentPalette = this.palettes.monochrome;
                break;
                
            case 'explanation_request':
                // Wise, teaching appearance
                this.avatarState.visualStyle = 'educational';
                this.avatarState.wisdomLevel = confidence;
                this.currentPalette = this.palettes.forest;
                break;
                
            case 'casual_conversation':
                // Friendly, relaxed appearance
                this.avatarState.visualStyle = 'conversational';
                this.avatarState.socialLevel = confidence;
                this.currentPalette = this.palettes.ocean;
                break;
                
            default:
                this.avatarState.visualStyle = 'default';
                this.currentPalette = this.palettes.classic;
        }
        
        // Strategy-based modifications
        if (strategy === 'deterministic') {
            this.avatarState.precision = 0.9;
        } else if (strategy === 'creative') {
            this.avatarState.creativity = 0.9;
        }
    }
    
    createMathematicalParticles(intensity, confidence) {
        // Create particles that look like mathematical symbols
        const particleCount = Math.floor(intensity / 10);
        const symbols = ['=', '+', '-', '*', '/', '%', '∑', '∏'];
        
        for (let i = 0; i < particleCount; i++) {
            this.createSymbolParticle(
                Math.random() * this.gridWidth,
                Math.random() * this.gridHeight,
                symbols[Math.floor(Math.random() * symbols.length)],
                confidence
            );
        }
    }
    
    createCreativeParticles(intensity, confidence) {
        // Create flowing, artistic particles
        const particleCount = Math.floor(intensity / 8);
        const colors = [1, 2, 3]; // Use different palette colors
        
        for (let i = 0; i < particleCount; i++) {
            this.createFlowingParticle(
                Math.random() * this.gridWidth,
                Math.random() * this.gridHeight,
                colors[Math.floor(Math.random() * colors.length)],
                confidence * 2 // More dynamic
            );
        }
    }
    
    createTechnicalParticles(intensity, confidence) {
        // Create geometric, technical particles
        const particleCount = Math.floor(intensity / 12);
        
        for (let i = 0; i < particleCount; i++) {
            this.createGeometricParticle(
                Math.random() * this.gridWidth,
                Math.random() * this.gridHeight,
                3, // Dark color for technical look
                confidence
            );
        }
    }
    
    createKnowledgeParticles(intensity, confidence) {
        // Create particles that suggest learning/knowledge
        const particleCount = Math.floor(intensity / 9);
        
        for (let i = 0; i < particleCount; i++) {
            this.createLightBulbParticle(
                Math.random() * this.gridWidth,
                Math.random() * this.gridHeight,
                1, // Bright color for knowledge
                confidence
            );
        }
    }
    
    createGenerationParticles(tokenCount, generationSpeed) {
        // Create particles representing text generation
        if (tokenCount % 5 === 0) { // Every 5 tokens
            const speedLevel = Math.min(generationSpeed / 5, 2.0);
            this.createTextStreamParticle(speedLevel);
        }
    }
    
    updateThinkingVisualization(phase, progress, confidence) {
        // Update avatar expression based on thinking phase
        switch(phase) {
            case 'analyzing':
                this.avatarState.eyeExpression = 'focused';
                break;
            case 'reasoning':
                this.avatarState.eyeExpression = 'contemplative';
                break;
            case 'calculating':
                this.avatarState.eyeExpression = 'sharp';
                break;
            case 'synthesizing':
                this.avatarState.eyeExpression = 'creative';
                break;
            case 'concluding':
                this.avatarState.eyeExpression = 'confident';
                break;
        }
        
        // Update progress indicator if needed
        this.avatarState.progressLevel = progress;
    }
    
    // Enhanced particle creation methods
    createSymbolParticle(x, y, symbol, confidence) {
        // Create a particle that displays a mathematical symbol
        this.createTextParticle(x, y, symbol, 3, confidence * 100);
    }
    
    createFlowingParticle(x, y, color, speed) {
        // Create a particle with flowing movement
        const particle = {
            x: x,
            y: y,
            color: this.currentPalette[color],
            life: 1.0,
            speed: speed,
            type: 'flowing',
            dx: (Math.random() - 0.5) * 2,
            dy: -Math.random(),
            size: 1
        };
        
        this.addParticleToSystem(particle);
    }
    
    createGeometricParticle(x, y, color, confidence) {
        // Create a sharp, geometric particle
        const particle = {
            x: x,
            y: y,
            color: this.currentPalette[color],
            life: confidence,
            speed: 0.5,
            type: 'geometric',
            size: 2,
            angle: Math.random() * Math.PI * 2
        };
        
        this.addParticleToSystem(particle);
    }
    
    createLightBulbParticle(x, y, color, confidence) {
        // Create a particle suggesting knowledge/insight
        const particle = {
            x: x,
            y: y,
            color: this.currentPalette[color],
            life: confidence,
            speed: 0.3,
            type: 'lightbulb',
            size: 1,
            pulse: Math.random() * Math.PI
        };
        
        this.addParticleToSystem(particle);
    }
    
    createTextStreamParticle(speedLevel) {
        // Create particles representing flowing text generation
        const particle = {
            x: this.gridWidth - 2,
            y: Math.random() * this.gridHeight,
            color: this.currentPalette[1],
            life: 1.0,
            speed: speedLevel,
            type: 'textstream',
            size: 1,
            dx: -speedLevel
        };
        
        this.addParticleToSystem(particle);
    }
    
    addParticleToSystem(particle) {
        // Add particle to rendering system (simplified for now)
        // In a full implementation, this would integrate with the existing particle system
        console.log('Enhanced particle created:', particle.type);
    }
    
    // === MISSING API METHODS FOR HTML INTEGRATION ===
    
    setEmotion(emotion, intensity) {
        this.avatarState.mood = emotion;
        this.avatarState.health = intensity;
        this.avatarState.lastInteraction = Date.now();
        this.renderAvatar();
        console.log(`🎭 Avatar emotion set: ${emotion} (${intensity}%)`);
    }
    
    setState(state) {
        const stateToMoodMap = {
            'thinking': 'thinking',
            'pre_thinking': 'thinking', 
            'analyzing': 'thinking',
            'reasoning': 'thinking',
            'calculating': 'thinking',
            'synthesizing': 'thinking',
            'concluding': 'excited',
            'generating': 'excited', 
            'speaking': 'happy',
            'error': 'sad',
            'idle': 'sleepy',
            'loading': 'thinking'
        };
        this.avatarState.mood = stateToMoodMap[state] || 'happy';
        this.avatarState.lastInteraction = Date.now();
        this.renderAvatar();
        console.log(`🔄 Avatar state set: ${state} -> ${this.avatarState.mood}`);
    }
    
    setStyle(style, palette) {
        const stylePaletteMap = {
            'robot': 'monochrome',
            'organic': 'forest',
            'crystal': 'crystal',
            'ghost': 'ocean',
            'energy': 'sunset',
            'cute': 'classic'
        };
        const paletteKey = stylePaletteMap[style] || 'monochrome';
        if (this.palettes[paletteKey]) {
            this.currentPalette = this.palettes[paletteKey];
        }
        this.renderAvatar();
        console.log(`🎨 Avatar style set: ${style} with ${paletteKey} palette`);
    }
    
    setAvatar(config) {
        if (config.emotion) {
            this.avatarState.mood = config.emotion;
        }
        if (config.intensity !== undefined) {
            this.avatarState.health = config.intensity;
        }
        if (config.style === 'robot') {
            this.currentPalette = this.palettes.monochrome;
        }
        this.avatarState.lastInteraction = Date.now();
        
        // CRITICAL: Trigger rendering like other methods
        this.renderAvatar();
        console.log(`🎭 Avatar set: emotion=${config.emotion}, intensity=${config.intensity}, style=${config.style}`);
    }
    
    // === UTILITY DRAWING FUNCTIONS ===
    
    drawRect(x, y, width, height, colorIndex, layer = 'main') {
        for (let dy = 0; dy < height; dy++) {
            for (let dx = 0; dx < width; dx++) {
                this.setPixel(x + dx, y + dy, colorIndex, layer);
            }
        }
    }
    
    drawCircle(centerX, centerY, radius, colorIndex, layer = 'main') {
        console.log(`🔵 drawCircle: center(${centerX}, ${centerY}), radius=${radius}, colorIndex=${colorIndex}`);
        let pixelsDrawn = 0;
        for (let y = -radius; y <= radius; y++) {
            for (let x = -radius; x <= radius; x++) {
                if (x*x + y*y <= radius*radius) {
                    this.setPixel(centerX + x, centerY + y, colorIndex);
                    pixelsDrawn++;
                }
            }
        }
        console.log(`🔵 drawCircle: drew ${pixelsDrawn} pixels`);
    }
    
    drawLine(x1, y1, x2, y2, colorIndex, layer = 'main') {
        const dx = Math.abs(x2 - x1);
        const dy = Math.abs(y2 - y1);
        const sx = x1 < x2 ? 1 : -1;
        const sy = y1 < y2 ? 1 : -1;
        let err = dx - dy;
        
        let x = x1, y = y1;
        
        while (true) {
            this.setPixel(x, y, colorIndex, layer);
            
            if (x === x2 && y === y2) break;
            
            const e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
    
    drawHorizontalLine(x, y, width, colorIndex, layer = 'main') {
        for (let i = 0; i < width; i++) {
            this.setPixel(x + i, y, colorIndex, layer);
        }
    }
    
    // Complex shapes for evolution levels
    drawDiamond(centerX, centerY, size, colorIndex, layer = 'main') {
        for (let y = -size; y <= size; y++) {
            for (let x = -size; x <= size; x++) {
                if (Math.abs(x) + Math.abs(y) <= size) {
                    this.setPixel(centerX + x, centerY + y, colorIndex, layer);
                }
            }
        }
    }
    
    drawComplexShape(centerX, centerY, size, colorIndex, layer = 'main') {
        // Star or more complex geometric shape
        const points = 5;
        for (let i = 0; i < points; i++) {
            const angle = (i * 2 * Math.PI) / points;
            const x = Math.round(centerX + size * Math.cos(angle));
            const y = Math.round(centerY + size * Math.sin(angle));
            this.drawLine(centerX, centerY, x, y, colorIndex, layer);
        }
    }
    
    drawMasterpieceForm(centerX, centerY, size, colorIndex, layer = 'main') {
        // Most advanced form - complex pattern
        for (let y = -size; y <= size; y++) {
            for (let x = -size; x <= size; x++) {
                const distance = Math.sqrt(x*x + y*y);
                const pattern = Math.sin(distance + this.animationTime * 0.001) > 0;
                if (distance <= size && pattern) {
                    this.setPixel(centerX + x, centerY + y, colorIndex, layer);
                }
            }
        }
    }
    
    drawRoundedRect(x, y, width, height, radius, colorIndex, layer = 'main') {
        // Simplified rounded rectangle for pixel art
        this.drawRect(x + radius, y, width - 2*radius, height, colorIndex, layer);
        this.drawRect(x, y + radius, width, height - 2*radius, colorIndex, layer);
        
        // Corners (simplified)
        this.setPixel(x + radius, y + radius, colorIndex, layer);
        this.setPixel(x + width - radius - 1, y + radius, colorIndex, layer);
        this.setPixel(x + radius, y + height - radius - 1, colorIndex, layer);
        this.setPixel(x + width - radius - 1, y + height - radius - 1, colorIndex, layer);
    }
}

// Pixel Font System
class PixelFont {
    constructor(charWidth, charHeight) {
        this.charWidth = charWidth;
        this.charHeight = charHeight;
        
        // Complete 4x6 Gameboy-style font data
        this.fontData = {
            // Letters A-Z
            'A': [[0,1,1,0],[1,0,0,1],[1,1,1,1],[1,0,0,1],[1,0,0,1],[0,0,0,0]],
            'B': [[1,1,1,0],[1,0,0,1],[1,1,1,0],[1,0,0,1],[1,1,1,0],[0,0,0,0]],
            'C': [[0,1,1,1],[1,0,0,0],[1,0,0,0],[1,0,0,0],[0,1,1,1],[0,0,0,0]],
            'D': [[1,1,1,0],[1,0,0,1],[1,0,0,1],[1,0,0,1],[1,1,1,0],[0,0,0,0]],
            'E': [[1,1,1,1],[1,0,0,0],[1,1,1,0],[1,0,0,0],[1,1,1,1],[0,0,0,0]],
            'F': [[1,1,1,1],[1,0,0,0],[1,1,1,0],[1,0,0,0],[1,0,0,0],[0,0,0,0]],
            'G': [[0,1,1,1],[1,0,0,0],[1,0,1,1],[1,0,0,1],[0,1,1,1],[0,0,0,0]],
            'H': [[1,0,0,1],[1,0,0,1],[1,1,1,1],[1,0,0,1],[1,0,0,1],[0,0,0,0]],
            'I': [[0,1,1,1],[0,0,1,0],[0,0,1,0],[0,0,1,0],[0,1,1,1],[0,0,0,0]],
            'J': [[0,0,0,1],[0,0,0,1],[0,0,0,1],[1,0,0,1],[0,1,1,0],[0,0,0,0]],
            'K': [[1,0,0,1],[1,0,1,0],[1,1,0,0],[1,0,1,0],[1,0,0,1],[0,0,0,0]],
            'L': [[1,0,0,0],[1,0,0,0],[1,0,0,0],[1,0,0,0],[1,1,1,1],[0,0,0,0]],
            'M': [[1,0,0,1],[1,1,1,1],[1,1,1,1],[1,0,0,1],[1,0,0,1],[0,0,0,0]],
            'N': [[1,0,0,1],[1,1,0,1],[1,1,1,1],[1,0,1,1],[1,0,0,1],[0,0,0,0]],
            'O': [[0,1,1,0],[1,0,0,1],[1,0,0,1],[1,0,0,1],[0,1,1,0],[0,0,0,0]],
            'P': [[1,1,1,0],[1,0,0,1],[1,1,1,0],[1,0,0,0],[1,0,0,0],[0,0,0,0]],
            'Q': [[0,1,1,0],[1,0,0,1],[1,0,1,1],[1,0,0,1],[0,1,1,1],[0,0,0,0]],
            'R': [[1,1,1,0],[1,0,0,1],[1,1,1,0],[1,0,1,0],[1,0,0,1],[0,0,0,0]],
            'S': [[0,1,1,1],[1,0,0,0],[0,1,1,0],[0,0,0,1],[1,1,1,0],[0,0,0,0]],
            'T': [[1,1,1,1],[0,0,1,0],[0,0,1,0],[0,0,1,0],[0,0,1,0],[0,0,0,0]],
            'U': [[1,0,0,1],[1,0,0,1],[1,0,0,1],[1,0,0,1],[0,1,1,0],[0,0,0,0]],
            'V': [[1,0,0,1],[1,0,0,1],[1,0,0,1],[0,1,1,0],[0,0,1,0],[0,0,0,0]],
            'W': [[1,0,0,1],[1,0,0,1],[1,1,1,1],[1,1,1,1],[1,0,0,1],[0,0,0,0]],
            'X': [[1,0,0,1],[0,1,1,0],[0,0,1,0],[0,1,1,0],[1,0,0,1],[0,0,0,0]],
            'Y': [[1,0,0,1],[1,0,0,1],[0,1,1,0],[0,0,1,0],[0,0,1,0],[0,0,0,0]],
            'Z': [[1,1,1,1],[0,0,0,1],[0,0,1,0],[0,1,0,0],[1,1,1,1],[0,0,0,0]],
            
            // Numbers 0-9
            '0': [[0,1,1,0],[1,0,0,1],[1,0,0,1],[1,0,0,1],[0,1,1,0],[0,0,0,0]],
            '1': [[0,0,1,0],[0,1,1,0],[0,0,1,0],[0,0,1,0],[0,1,1,1],[0,0,0,0]],
            '2': [[0,1,1,0],[1,0,0,1],[0,0,1,0],[0,1,0,0],[1,1,1,1],[0,0,0,0]],
            '3': [[1,1,1,0],[0,0,0,1],[0,1,1,0],[0,0,0,1],[1,1,1,0],[0,0,0,0]],
            '4': [[1,0,0,1],[1,0,0,1],[1,1,1,1],[0,0,0,1],[0,0,0,1],[0,0,0,0]],
            '5': [[1,1,1,1],[1,0,0,0],[1,1,1,0],[0,0,0,1],[1,1,1,0],[0,0,0,0]],
            '6': [[0,1,1,0],[1,0,0,0],[1,1,1,0],[1,0,0,1],[0,1,1,0],[0,0,0,0]],
            '7': [[1,1,1,1],[0,0,0,1],[0,0,1,0],[0,1,0,0],[0,1,0,0],[0,0,0,0]],
            '8': [[0,1,1,0],[1,0,0,1],[0,1,1,0],[1,0,0,1],[0,1,1,0],[0,0,0,0]],
            '9': [[0,1,1,0],[1,0,0,1],[0,1,1,1],[0,0,0,1],[0,1,1,0],[0,0,0,0]],
            
            // Symbols and punctuation
            ' ': [[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]],
            '.': [[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,1,0],[0,0,0,0]],
            ',': [[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,1,0],[0,1,0,0],[0,0,0,0]],
            '!': [[0,0,1,0],[0,0,1,0],[0,0,1,0],[0,0,0,0],[0,0,1,0],[0,0,0,0]],
            '?': [[0,1,1,0],[1,0,0,1],[0,0,1,0],[0,0,0,0],[0,0,1,0],[0,0,0,0]],
            ':': [[0,0,0,0],[0,0,1,0],[0,0,0,0],[0,0,1,0],[0,0,0,0],[0,0,0,0]],
            ';': [[0,0,0,0],[0,0,1,0],[0,0,0,0],[0,0,1,0],[0,1,0,0],[0,0,0,0]],
            '-': [[0,0,0,0],[0,0,0,0],[1,1,1,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]],
            '+': [[0,0,0,0],[0,1,0,0],[1,1,1,0],[0,1,0,0],[0,0,0,0],[0,0,0,0]],
            '*': [[0,0,0,0],[1,0,1,0],[0,1,0,0],[1,0,1,0],[0,0,0,0],[0,0,0,0]],
            '/': [[0,0,0,1],[0,0,1,0],[0,1,0,0],[1,0,0,0],[0,0,0,0],[0,0,0,0]],
            '=': [[0,0,0,0],[1,1,1,0],[0,0,0,0],[1,1,1,0],[0,0,0,0],[0,0,0,0]],
            '%': [[1,1,0,1],[1,1,1,0],[0,0,1,0],[0,1,1,1],[1,0,1,1],[0,0,0,0]],
            '&': [[0,1,0,0],[1,0,1,0],[0,1,0,0],[1,0,1,0],[0,1,0,1],[0,0,0,0]],
            '#': [[0,1,0,1],[1,1,1,1],[0,1,0,1],[1,1,1,1],[0,1,0,1],[0,0,0,0]],
            '@': [[0,1,1,0],[1,0,1,1],[1,0,1,1],[1,0,0,0],[0,1,1,1],[0,0,0,0]],
            
            // Brackets and quotes
            '(': [[0,0,1,0],[0,1,0,0],[0,1,0,0],[0,1,0,0],[0,0,1,0],[0,0,0,0]],
            ')': [[0,1,0,0],[0,0,1,0],[0,0,1,0],[0,0,1,0],[0,1,0,0],[0,0,0,0]],
            '[': [[0,1,1,0],[0,1,0,0],[0,1,0,0],[0,1,0,0],[0,1,1,0],[0,0,0,0]],
            ']': [[0,1,1,0],[0,0,1,0],[0,0,1,0],[0,0,1,0],[0,1,1,0],[0,0,0,0]],
            '"': [[1,0,1,0],[1,0,1,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]],
            "'": [[0,1,0,0],[0,1,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]],
            
            // Arrows and special chars
            '^': [[0,1,0,0],[1,0,1,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]],
            '<': [[0,0,1,0],[0,1,0,0],[1,0,0,0],[0,1,0,0],[0,0,1,0],[0,0,0,0]],
            '>': [[1,0,0,0],[0,1,0,0],[0,0,1,0],[0,1,0,0],[1,0,0,0],[0,0,0,0]],
            '|': [[0,1,0,0],[0,1,0,0],[0,1,0,0],[0,1,0,0],[0,1,0,0],[0,0,0,0]],
            '~': [[0,0,0,0],[0,1,0,1],[1,0,1,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]],
            '_': [[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[1,1,1,1],[0,0,0,0]],
            
            // Custom pixel art symbols for M1K3
            '♥': [[0,1,0,1],[1,1,1,1],[0,1,1,1],[0,0,1,0],[0,0,0,0],[0,0,0,0]], // Heart
            '♪': [[0,0,0,1],[0,0,1,1],[0,1,0,1],[1,1,0,1],[1,0,0,0],[0,0,0,0]], // Music note
            '★': [[0,0,1,0],[0,1,1,1],[1,1,1,1],[0,1,1,1],[1,0,1,0],[0,0,0,0]], // Star
            '◆': [[0,0,1,0],[0,1,1,1],[1,1,1,1],[0,1,1,1],[0,0,1,0],[0,0,0,0]], // Diamond
            '•': [[0,0,0,0],[0,0,0,0],[0,1,1,0],[0,1,1,0],[0,0,0,0],[0,0,0,0]]  // Bullet
        };
    }
    
    getChar(char) {
        return this.fontData[char.toUpperCase()] || this.fontData[' '];
    }
}

// Export for use
if (typeof module !== 'undefined' && module.exports) {
    module.exports = GameboyPixelEngine;
} else if (typeof window !== 'undefined') {
    window.GameboyPixelEngine = GameboyPixelEngine;
}