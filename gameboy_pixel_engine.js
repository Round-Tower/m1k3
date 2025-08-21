/**
 * Gameboy Color Pixel Engine for M1K3
 * Multi-purpose pixel rendering system: avatars, text, graphs, e-ink
 */

class GameboyPixelEngine {
    constructor(canvas, options = {}) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.ctx.imageSmoothingEnabled = false;
        
        // Configuration
        this.pixelSize = options.pixelSize || 8;
        this.canvasWidth = canvas.width;
        this.canvasHeight = canvas.height;
        this.gridWidth = Math.floor(this.canvasWidth / this.pixelSize);
        this.gridHeight = Math.floor(this.canvasHeight / this.pixelSize);
        
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
        
        // Layers for efficient rendering
        this.layers = {
            background: this.createOffscreenCanvas(),
            avatar: this.createOffscreenCanvas(),
            text: this.createOffscreenCanvas(),
            ui: this.createOffscreenCanvas(),
            effects: this.createOffscreenCanvas()
        };
        
        // Pixel fonts
        this.fonts = {
            tiny: new PixelFont(4, 6),
            small: new PixelFont(6, 8),
            normal: new PixelFont(8, 12),
            large: new PixelFont(12, 16)
        };
        
        // System state for context-aware rendering
        this.systemState = {
            battery: 100,
            cpu: 0,
            memory: 0,
            network: true,
            temperature: 25,
            ecoSavings: 0
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
    
    init() {
        this.clearCanvas();
        this.startAnimationLoop();
    }
    
    createOffscreenCanvas() {
        const canvas = document.createElement('canvas');
        canvas.width = this.canvasWidth;
        canvas.height = this.canvasHeight;
        const ctx = canvas.getContext('2d');
        ctx.imageSmoothingEnabled = false;
        return { canvas, ctx };
    }
    
    // === CORE RENDERING SYSTEM ===
    
    clearCanvas(color = this.currentPalette[0]) {
        this.ctx.fillStyle = color;
        this.ctx.fillRect(0, 0, this.canvasWidth, this.canvasHeight);
    }
    
    setPixel(x, y, colorIndex, layer = 'avatar') {
        if (x < 0 || x >= this.gridWidth || y < 0 || y >= this.gridHeight) return;
        if (colorIndex < 0 || colorIndex >= this.currentPalette.length) return;
        
        const ctx = this.layers[layer].ctx;
        const color = this.currentPalette[colorIndex];
        
        ctx.fillStyle = color;
        ctx.fillRect(x * this.pixelSize, y * this.pixelSize, this.pixelSize, this.pixelSize);
        
        this.markDirty(x * this.pixelSize, y * this.pixelSize, this.pixelSize, this.pixelSize);
    }
    
    getPixel(x, y, layer = 'avatar') {
        if (x < 0 || x >= this.gridWidth || y < 0 || y >= this.gridHeight) return 0;
        
        const ctx = this.layers[layer].ctx;
        const imageData = ctx.getImageData(x * this.pixelSize, y * this.pixelSize, 1, 1);
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
        const layer = this.layers.avatar;
        layer.ctx.clearRect(0, 0, this.canvasWidth, this.canvasHeight);
        
        const centerX = Math.floor(this.gridWidth / 2);
        const centerY = Math.floor(this.gridHeight / 2);
        
        // Avatar body based on evolution level
        const size = Math.floor(3 + this.avatarState.evolution);
        this.renderAvatarBody(centerX, centerY, size, layer.ctx);
        
        // Eyes with blinking animation
        if (this.shouldBlink()) {
            this.renderAvatarEyes(centerX, centerY, 'closed', layer.ctx);
        } else {
            this.renderAvatarEyes(centerX, centerY, this.avatarState.mood, layer.ctx);
        }
        
        // Mouth based on mood
        this.renderAvatarMouth(centerX, centerY, this.avatarState.mood, layer.ctx);
        
        // Status indicators
        this.renderAvatarStatus(layer.ctx);
        
        // Continuous animations
        this.applyAvatarAnimations(centerX, centerY, layer.ctx);
    }
    
    renderAvatarBody(x, y, size, ctx) {
        const colorIndex = this.getBodyColorFromMood();
        
        // Different body shapes based on evolution
        switch(Math.floor(this.avatarState.evolution)) {
            case 0: // Basic circle
                this.drawCircle(x, y, size, colorIndex, 'avatar');
                break;
            case 1: // Rounded square
                this.drawRoundedRect(x-size, y-size, size*2, size*2, 1, colorIndex, 'avatar');
                break;
            case 2: // Diamond
                this.drawDiamond(x, y, size, colorIndex, 'avatar');
                break;
            case 3: // Complex shape
                this.drawComplexShape(x, y, size, colorIndex, 'avatar');
                break;
            default: // Masterpiece form
                this.drawMasterpieceForm(x, y, size, colorIndex, 'avatar');
        }
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
                    this.drawHorizontalLine(pos.x, pos.y, 1, 3, 'avatar');
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
                    this.drawHorizontalLine(pos.x, pos.y, 1, 2, 'avatar');
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
                    this.drawHorizontalLine(pos.x, pos.y, 1, 3, 'avatar');
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
                this.drawRect(x - 1, y + 1, 3, 2, 3, 'avatar');
                break;
            case 'hot':
            case 'noisy':
                // Panting
                this.drawRect(x - 1, y + 1, 3, 1, 3, 'avatar');
                break;
            case 'busy':
            case 'memory_pressure':
                // Tight line (concentration)
                this.drawHorizontalLine(x - 1, y + 1, 3, 3, 'avatar');
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
            this.setPixel(2 + (i * spacing), 2, 2, 'avatar'); // Red hearts
        }
        
        // Energy indicator (top-right)
        const energyLevel = Math.floor(this.avatarState.energy / 25);
        for (let i = 0; i < energyLevel; i++) {
            this.setPixel(this.gridWidth - 6 - (i * spacing), 2, 1, 'avatar'); // Blue energy
        }
        
        // Evolution indicator (bottom-center)
        const evoLevel = Math.floor(this.avatarState.evolution);
        if (evoLevel > 0) {
            for (let i = 0; i < evoLevel; i++) {
                this.setPixel(this.gridWidth/2 - 2 + i, this.gridHeight - 4, 3, 'avatar');
            }
        }
        
        // Mood indicator (small color dot)
        const moodColor = this.getBodyColorFromMood();
        this.setPixel(2, this.gridHeight - 4, moodColor, 'avatar');
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
        const layer = this.layers.ui;
        layer.ctx.clearRect(0, 0, this.canvasWidth, this.canvasHeight);
        
        // Battery meter
        this.renderBatteryMeter(2, 2, this.systemState.battery);
        
        // CPU usage bar
        this.renderUsageBar(2, 6, this.systemState.cpu, 'CPU');
        
        // Memory usage bar
        this.renderUsageBar(2, 10, this.systemState.memory, 'MEM');
        
        // Network signal
        this.renderNetworkSignal(this.gridWidth - 6, 2, this.systemState.network);
        
        // Eco metrics
        this.renderEcoMetrics(this.gridWidth - 8, 6);
        
        // Temperature indicator
        this.renderTemperature(this.gridWidth - 4, 10, this.systemState.temperature);
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
        this.isAnimating = true;
        this.animate();
    }
    
    stopAnimationLoop() {
        this.isAnimating = false;
        if (this.animationFrame) {
            cancelAnimationFrame(this.animationFrame);
        }
    }
    
    animate() {
        if (!this.isAnimating) return;
        
        const now = performance.now();
        const deltaTime = now - this.lastFrameTime;
        
        if (deltaTime >= this.frameInterval) {
            this.animationTime += deltaTime;
            
            // Update avatar care mechanics
            this.updateAvatarCare();
            
            // Render all layers
            this.renderFrame();
            
            this.lastFrameTime = now;
        }
        
        this.animationFrame = requestAnimationFrame(() => this.animate());
    }
    
    renderFrame() {
        // Clear main canvas
        this.ctx.clearRect(0, 0, this.canvasWidth, this.canvasHeight);
        
        // Composite all layers
        Object.values(this.layers).forEach(layer => {
            this.ctx.drawImage(layer.canvas, 0, 0);
        });
        
        // Render current content
        this.renderAvatar();
        this.renderSystemMetrics();
        
        // Apply e-ink dithering if enabled
        if (this.currentPalette === this.palettes.eink) {
            this.applyDithering();
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
        // Breathing animation
        const breathCycle = Math.sin(this.animationTime * 0.001) * 0.5 + 0.5;
        
        // Subtle movements based on system state
        if (this.systemState.cpu > 50) {
            // Vibration when under load
            const jitter = Math.sin(this.animationTime * 0.01) * 0.5;
            // Apply slight offset to avatar rendering
        }
        
        if (this.avatarState.energy < 30) {
            // Slower animations when low energy
            // Reduce animation frequency
        }
    }
    
    // === UTILITY DRAWING FUNCTIONS ===
    
    drawRect(x, y, width, height, colorIndex, layer = 'avatar') {
        for (let dy = 0; dy < height; dy++) {
            for (let dx = 0; dx < width; dx++) {
                this.setPixel(x + dx, y + dy, colorIndex, layer);
            }
        }
    }
    
    drawCircle(centerX, centerY, radius, colorIndex, layer = 'avatar') {
        for (let y = -radius; y <= radius; y++) {
            for (let x = -radius; x <= radius; x++) {
                if (x*x + y*y <= radius*radius) {
                    this.setPixel(centerX + x, centerY + y, colorIndex, layer);
                }
            }
        }
    }
    
    drawLine(x1, y1, x2, y2, colorIndex, layer = 'avatar') {
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
    
    drawHorizontalLine(x, y, width, colorIndex, layer = 'avatar') {
        for (let i = 0; i < width; i++) {
            this.setPixel(x + i, y, colorIndex, layer);
        }
    }
    
    // Complex shapes for evolution levels
    drawDiamond(centerX, centerY, size, colorIndex, layer = 'avatar') {
        for (let y = -size; y <= size; y++) {
            for (let x = -size; x <= size; x++) {
                if (Math.abs(x) + Math.abs(y) <= size) {
                    this.setPixel(centerX + x, centerY + y, colorIndex, layer);
                }
            }
        }
    }
    
    drawComplexShape(centerX, centerY, size, colorIndex, layer = 'avatar') {
        // Star or more complex geometric shape
        const points = 5;
        for (let i = 0; i < points; i++) {
            const angle = (i * 2 * Math.PI) / points;
            const x = Math.round(centerX + size * Math.cos(angle));
            const y = Math.round(centerY + size * Math.sin(angle));
            this.drawLine(centerX, centerY, x, y, colorIndex, layer);
        }
    }
    
    drawMasterpieceForm(centerX, centerY, size, colorIndex, layer = 'avatar') {
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
    
    drawRoundedRect(x, y, width, height, radius, colorIndex, layer = 'avatar') {
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