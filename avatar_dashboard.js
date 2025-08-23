// M1K3 Avatar Dashboard JavaScript
// Enhanced chat interface with speech-to-text and sound effects

// Emotion expressions for pixel art (16x16 grid)
const pixelEmotions = {
    happy: {
        eyes: [[1,1,1,0,0,1,1,1], [1,0,0,0,0,0,0,1]],
        mouth: [[0,1,0,0,0,0,1,0], [1,0,1,1,1,1,0,1]]
    },
    sad: {
        eyes: [[1,1,0,0,0,0,1,1], [1,0,1,0,0,1,0,1]],
        mouth: [[1,0,1,1,1,1,0,1], [0,1,0,0,0,0,1,0]]
    },
    angry: {
        eyes: [[1,0,0,0,0,0,0,1], [0,1,1,0,0,1,1,0]],
        mouth: [[1,1,1,1,1,1,1,1], [1,0,0,0,0,0,0,1]]
    },
    surprised: {
        eyes: [[0,1,1,1,1,1,1,0], [1,0,0,0,0,0,0,1]],
        mouth: [[0,0,1,1,1,1,0,0], [0,1,0,0,0,0,1,0]]
    },
    love: {
        eyes: [[0,1,1,0,0,1,1,0], [1,1,1,1,1,1,1,1]],
        mouth: [[0,1,0,0,0,0,1,0], [1,0,1,1,1,1,0,1]]
    },
    thinking: {
        eyes: [[1,1,1,0,0,0,0,0], [1,0,0,0,0,1,1,1]],
        mouth: [[0,0,0,1,1,0,0,0], [0,0,1,0,0,1,0,0]]
    },
    sleepy: {
        eyes: [[0,0,0,0,0,0,0,0], [1,1,1,1,1,1,1,1]],
        mouth: [[0,0,1,1,1,1,0,0], [0,0,0,0,0,0,0,0]]
    },
    excited: {
        eyes: [[1,0,1,0,0,1,0,1], [0,1,0,1,1,0,1,0]],
        mouth: [[1,0,0,0,0,0,0,1], [0,1,1,1,1,1,1,0]]
    }
};

// Sound mappings
const soundMappings = {
    connect: ['chime1.wav', 'ding_ding.wav'],
    message_sent: ['coin.wav', 'plus_sfx.wav'],
    message_received: ['complete.wav', 'victory_confetti.wav'],
    error: ['incorrect_sfx.wav', 'donk.wav', 'oh_no.wav'],
    thinking: ['wave_alert.wav'], // Will loop
    typing: ['brush_sfx.wav'],
    voice_start: ['click_error.wav'],
    voice_end: ['chime1.wav'],
    emotion_change: ['cat_star_collect.wav', 'short_magic_shot.wav']
};

// Debug Console Management
class DebugConsole {
    constructor() {
        this.isVisible = false;
        this.sentCount = 0;
        this.receivedCount = 0;
        this.messages = [];
        this.maxMessages = 1000;
        this.lastActivity = null;
        
        // Bind methods
        this.log = this.log.bind(this);
        this.toggle = this.toggle.bind(this);
        this.clear = this.clear.bind(this);
        this.export = this.export.bind(this);
        
        console.log('🐛 Debug console initialized');
    }

    toggle() {
        this.isVisible = !this.isVisible;
        const console = document.getElementById('debugConsole');
        const button = document.getElementById('debugToggle');
        
        if (console && button) {
            console.classList.toggle('visible', this.isVisible);
            button.classList.toggle('active', this.isVisible);
        }
        
        this.log('info', this.isVisible ? 'Debug console opened' : 'Debug console closed');
    }

    log(type, message, data = null) {
        const timestamp = new Date().toLocaleTimeString('en-US', { hour12: false });
        const entry = {
            timestamp,
            type,
            message,
            data,
            id: Date.now() + Math.random()
        };
        
        this.messages.unshift(entry);
        if (this.messages.length > this.maxMessages) {
            this.messages = this.messages.slice(0, this.maxMessages);
        }
        
        this.lastActivity = timestamp;
        this.updateUI();
        
        // Also log to browser console for development
        const logMethod = type === 'error' ? 'error' : type === 'info' ? 'info' : 'log';
        console[logMethod](`[DEBUG ${timestamp}] ${message}`, data || '');
    }

    updateStats(connectionStatus) {
        const elements = {
            debugSentCount: document.getElementById('debugSentCount'),
            debugReceivedCount: document.getElementById('debugReceivedCount'),
            debugConnectionStatus: document.getElementById('debugConnectionStatus'),
            debugLastActivity: document.getElementById('debugLastActivity')
        };

        if (elements.debugSentCount) elements.debugSentCount.textContent = this.sentCount;
        if (elements.debugReceivedCount) elements.debugReceivedCount.textContent = this.receivedCount;
        if (elements.debugConnectionStatus) {
            elements.debugConnectionStatus.textContent = connectionStatus || 'Disconnected';
            elements.debugConnectionStatus.style.color = 
                connectionStatus === 'Connected' ? 'var(--m1k3-green)' : 'var(--m1k3-red)';
        }
        if (elements.debugLastActivity) {
            elements.debugLastActivity.textContent = this.lastActivity || 'Never';
        }
    }

    updateUI() {
        const messagesContainer = document.getElementById('debugMessages');
        if (!messagesContainer) return;

        // Only update if visible for performance
        if (!this.isVisible) return;

        const html = this.messages.map(entry => `
            <div class="debug-message ${entry.type}">
                <span class="debug-timestamp">${entry.timestamp}</span>
                <span class="debug-type">[${entry.type.toUpperCase()}]</span>
                <span class="debug-content">${entry.message}${entry.data ? ' • ' + JSON.stringify(entry.data) : ''}</span>
            </div>
        `).join('');
        
        messagesContainer.innerHTML = html;
    }

    clear() {
        this.messages = [];
        this.sentCount = 0;
        this.receivedCount = 0;
        this.lastActivity = null;
        this.updateUI();
        this.updateStats();
        this.log('info', 'Debug log cleared');
    }

    export() {
        const data = {
            exportTime: new Date().toISOString(),
            stats: {
                sentCount: this.sentCount,
                receivedCount: this.receivedCount,
                lastActivity: this.lastActivity
            },
            messages: this.messages
        };
        
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `m1k3-debug-${new Date().toISOString().slice(0,19).replace(/:/g, '-')}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        
        this.log('info', 'Debug log exported');
    }

    logSent(message) {
        this.sentCount++;
        this.log('sent', 'Outgoing message', message);
        this.updateStats();
    }

    logReceived(message) {
        this.receivedCount++;
        this.log('received', 'Incoming message', message);
        this.updateStats();
    }

    logError(error) {
        this.log('error', 'Error occurred', error);
    }

    logConnection(status) {
        this.log('info', `Connection ${status}`);
        this.updateStats(status);
    }
}

// Audio manager
class AudioManager {
    constructor() {
        this.sounds = {};
        this.volume = 0.7;
        this.enabled = true;
        this.preloadSounds();
    }

    async preloadSounds() {
        const allSounds = new Set();
        Object.values(soundMappings).flat().forEach(sound => allSounds.add(sound));
        
        for (const soundFile of allSounds) {
            try {
                const audio = new Audio(`sounds/${soundFile}`);
                audio.preload = 'auto';
                audio.volume = this.volume;
                this.sounds[soundFile] = audio;
            } catch (e) {
                console.warn(`Failed to preload sound: ${soundFile}`, e);
            }
        }
        console.log(`Preloaded ${Object.keys(this.sounds).length} sound effects`);
    }

    play(eventType) {
        if (!this.enabled) return;
        
        const soundOptions = soundMappings[eventType];
        if (!soundOptions || soundOptions.length === 0) return;
        
        const soundFile = soundOptions[Math.floor(Math.random() * soundOptions.length)];
        const audio = this.sounds[soundFile];
        
        if (audio) {
            audio.currentTime = 0;
            audio.volume = this.volume;
            audio.play().catch(e => console.warn(`Failed to play sound: ${soundFile}`, e));
        }
    }

    setVolume(volume) {
        this.volume = volume / 100;
        Object.values(this.sounds).forEach(audio => {
            audio.volume = this.volume;
        });
    }

    setEnabled(enabled) {
        this.enabled = enabled;
    }
}

// Particle system
class Particle {
    constructor(x, y, type, color) {
        this.x = x;
        this.y = y;
        this.vx = (Math.random() - 0.5) * 4;
        this.vy = (Math.random() - 0.5) * 4;
        this.life = 1.0;
        this.decay = 0.02 + Math.random() * 0.02;
        this.size = 2 + Math.random() * 4;
        this.type = type;
        this.color = color;
        this.angle = Math.random() * Math.PI * 2;
        this.spin = (Math.random() - 0.5) * 0.2;
    }
    
    update() {
        this.x += this.vx;
        this.y += this.vy;
        this.vy += 0.1; // Gravity
        this.vx *= 0.99; // Air resistance
        this.life -= this.decay;
        this.angle += this.spin;
        return this.life > 0;
    }
    
    draw(ctx) {
        ctx.save();
        ctx.globalAlpha = this.life;
        ctx.translate(this.x, this.y);
        ctx.rotate(this.angle);
        
        if (this.type === 'sparkle') {
            ctx.fillStyle = this.color;
            ctx.fillRect(-this.size/2, -this.size/2, this.size, this.size);
        } else if (this.type === 'heart') {
            ctx.fillStyle = this.color;
            ctx.font = `${this.size * 2}px serif`;
            ctx.textAlign = 'center';
            ctx.fillText('♥', 0, this.size/2);
        } else if (this.type === 'star') {
            ctx.fillStyle = this.color;
            ctx.font = `${this.size * 2}px serif`;
            ctx.textAlign = 'center';
            ctx.fillText('✨', 0, this.size/2);
        } else {
            ctx.fillStyle = this.color;
            ctx.beginPath();
            ctx.arc(0, 0, this.size, 0, Math.PI * 2);
            ctx.fill();
        }
        
        ctx.restore();
    }
}

// Initialize chat interface
function initializeChat() {
    const chatInput = document.getElementById('chatInput');
    const sendButton = document.getElementById('sendButton');
    
    // Auto-resize textarea
    chatInput.addEventListener('input', function() {
        this.style.height = 'auto';
        this.style.height = Math.min(this.scrollHeight, 120) + 'px';
    });
    
    // Handle Enter key
    chatInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendChatMessage();
        }
    });
    
    // Send button click
    sendButton.addEventListener('click', sendChatMessage);
    
    // Chat controls
    document.getElementById('clearChat').addEventListener('click', clearChat);
    document.getElementById('exportChat').addEventListener('click', exportChat);
    document.getElementById('soundToggle').addEventListener('click', toggleSounds);
    document.getElementById('testSoundBtn').addEventListener('click', testSound);
}

// Initialize speech recognition
function initializeSpeechRecognition() {
    if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        recognition = new SpeechRecognition();
        
        recognition.continuous = false;
        recognition.interimResults = false;
        recognition.lang = 'en-US';
        
        recognition.onstart = function() {
            isRecording = true;
            document.getElementById('voiceButton').classList.add('recording');
            audioManager.play('voice_start');
        };
        
        recognition.onresult = function(event) {
            const text = event.results[0][0].transcript;
            document.getElementById('chatInput').value = text;
            sendChatMessage();
        };
        
        recognition.onend = function() {
            isRecording = false;
            document.getElementById('voiceButton').classList.remove('recording');
            audioManager.play('voice_end');
        };
        
        recognition.onerror = function(event) {
            console.error('Speech recognition error:', event.error);
            isRecording = false;
            document.getElementById('voiceButton').classList.remove('recording');
            audioManager.play('error');
        };
        
        // Voice button events
        const voiceButton = document.getElementById('voiceButton');
        
        voiceButton.addEventListener('mousedown', startRecording);
        voiceButton.addEventListener('mouseup', stopRecording);
        voiceButton.addEventListener('mouseleave', stopRecording);
        
        voiceButton.addEventListener('touchstart', startRecording);
        voiceButton.addEventListener('touchend', stopRecording);
        
    } else {
        // Hide voice button if not supported
        document.getElementById('voiceButton').style.display = 'none';
        console.warn('Speech recognition not supported in this browser');
    }
}

// Initialize audio system
function initializeAudio() {
    // Create audio manager but don't initialize sounds yet
    window.audioManager = null;
    window.audioInitialized = false;
    
    // Volume control
    const volumeSlider = document.getElementById('volumeSlider');
    const volumeValue = document.getElementById('volumeValue');
    
    volumeSlider.addEventListener('input', function() {
        const volume = parseInt(this.value);
        currentVolume = volume;
        if (window.audioManager) {
            audioManager.setVolume(volume);
        }
        volumeValue.textContent = volume + '%';
    });
    
    // Initialize audio on first user interaction
    const initAudioOnInteraction = async () => {
        if (!window.audioInitialized) {
            console.log('🎵 Initializing audio on user interaction...');
            try {
                window.audioManager = new AudioManager();
                window.audioInitialized = true;
                console.log('✅ Audio system ready!');
                
                // Show audio status
                const audioStatus = document.getElementById('audioStatus');
                if (audioStatus) {
                    audioStatus.style.display = 'flex';
                    audioStatus.className = 'connection-status connected';
                }
            } catch (e) {
                console.error('❌ Audio initialization failed:', e);
            }
            
            // Remove the listeners
            document.removeEventListener('click', initAudioOnInteraction);
            document.removeEventListener('keydown', initAudioOnInteraction);
        }
    };
    
    // Add listeners for first interaction
    document.addEventListener('click', initAudioOnInteraction);
    document.addEventListener('keydown', initAudioOnInteraction);
}

// Initialize controls
function initializeControls() {
    // Theme toggle (placeholder)
    document.getElementById('themeToggle').addEventListener('click', function() {
        // Theme switching would go here
        console.log('Theme toggle clicked');
    });
}

// Chat functions
function sendChatMessage() {
    const input = document.getElementById('chatInput');
    const message = input.value.trim();
    
    if (!message) return;
    
    // Add user message to chat
    addMessage(message, 'user');
    
    // Send to server
    sendMessage({
        type: 'chat_user',
        message: message
    });
    
    // Clear input
    input.value = '';
    input.style.height = 'auto';
    
    // Play sound
    audioManager.play('message_sent');
    
    // Update message count
    messageCount++;
    updateMessageCount();
}

function addMessage(text, sender) {
    const messagesContainer = document.getElementById('chatMessages');
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${sender}`;
    
    const avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.textContent = sender === 'user' ? '👤' : '🤖';
    
    const content = document.createElement('div');
    content.className = 'message-content';
    
    const textDiv = document.createElement('div');
    textDiv.className = 'message-text';
    textDiv.textContent = text;
    
    const timeDiv = document.createElement('div');
    timeDiv.className = 'message-time';
    timeDiv.textContent = new Date().toLocaleTimeString();
    
    content.appendChild(textDiv);
    content.appendChild(timeDiv);
    
    messageDiv.appendChild(avatar);
    messageDiv.appendChild(content);
    
    messagesContainer.appendChild(messageDiv);
    
    // Scroll to bottom
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
    
    // Store reference for streaming updates
    if (sender === 'ai' && !text) {
        messageDiv.classList.add('streaming');
    }
}

function appendToLastMessage(chunk) {
    const messagesContainer = document.getElementById('chatMessages');
    const streamingMessage = messagesContainer.querySelector('.message.streaming .message-text');
    
    if (streamingMessage) {
        streamingMessage.textContent += chunk;
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
}

function showTypingIndicator(show) {
    const messagesContainer = document.getElementById('chatMessages');
    let typingIndicator = messagesContainer.querySelector('.typing-indicator');
    
    if (show && !typingIndicator) {
        typingIndicator = document.createElement('div');
        typingIndicator.className = 'typing-indicator';
        typingIndicator.innerHTML = `
            <span>M1K3 is typing</span>
            <div class="typing-dots">
                <div class="typing-dot"></div>
                <div class="typing-dot"></div>
                <div class="typing-dot"></div>
            </div>
        `;
        messagesContainer.appendChild(typingIndicator);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    } else if (!show && typingIndicator) {
        typingIndicator.remove();
        // Remove streaming class from last message
        const streamingMessage = messagesContainer.querySelector('.message.streaming');
        if (streamingMessage) {
            streamingMessage.classList.remove('streaming');
        }
    }
}

function clearChat() {
    const messagesContainer = document.getElementById('chatMessages');
    messagesContainer.innerHTML = '';
    
    // Add welcome message back
    addMessage("Hello! I'm M1K3, your AI companion. Start chatting to see real-time avatar emotions!", 'ai');
    
    messageCount = 1;
    updateMessageCount();
}

function exportChat() {
    const messages = document.querySelectorAll('.message');
    const chatData = Array.from(messages).map(msg => {
        const sender = msg.classList.contains('user') ? 'User' : 'M1K3';
        const text = msg.querySelector('.message-text').textContent;
        const time = msg.querySelector('.message-time').textContent;
        return `[${time}] ${sender}: ${text}`;
    }).join('\n');
    
    const blob = new Blob([chatData], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `m1k3-chat-${new Date().toISOString().split('T')[0]}.txt`;
    a.click();
    URL.revokeObjectURL(url);
}

function toggleSounds() {
    soundsEnabled = !soundsEnabled;
    if (window.audioManager) {
        audioManager.setEnabled(soundsEnabled);
    }
    
    const button = document.getElementById('soundToggle');
    button.textContent = soundsEnabled ? '🔊' : '🔇';
    button.classList.toggle('active', soundsEnabled);
}

function testSound() {
    console.log('🔔 Testing sound system...');
    
    if (!window.audioInitialized) {
        console.log('🎵 Audio not initialized yet - initializing now...');
        // Audio will be initialized on this click
        setTimeout(() => {
            playSound('connect');
        }, 100);
    } else {
        playSound('connect');
    }
}

// Speech recognition functions
function startRecording() {
    if (recognition && !isRecording) {
        recognition.start();
    }
}

function stopRecording() {
    if (recognition && isRecording) {
        recognition.stop();
    }
}

// Avatar functions
function updateEmotion(emotion, intensity, message) {
    if (emotion && emotion !== currentEmotion) {
        currentEmotion = emotion;
        emotionIntensity = intensity || 50;
        
        // Update UI
        const emotionEmojis = {
            'happy': '😊', 'sad': '😢', 'angry': '😠', 'surprised': '😲',
            'love': '😍', 'thinking': '🤔', 'sleepy': '😴', 'excited': '🤩'
        };
        
        const emoji = emotionEmojis[emotion] || '😊';
        const emotionName = emotion.charAt(0).toUpperCase() + emotion.slice(1);
        document.getElementById('currentEmotion').textContent = `${emoji} ${emotionName}`;
        
        generateAvatar();
        createEmotionParticles(emotion, intensity);
        audioManager.play('emotion_change');
        
        console.log(`Emotion updated: ${emotion} (${intensity}%)`);
    }
}

function updateState(state) {
    if (state && state !== currentState) {
        currentState = state;
        
        // Update UI
        const stateEmojis = {
            'idle': '💤', 'thinking': '🤔', 'generating': '⚡',
            'speaking': '🔊', 'error': '❌', 'loading': '⏳',
            'pre_thinking': '🧠', 'post_response': '✅', 'farewell': '👋'
        };
        
        const emoji = stateEmojis[state] || '💤';
        const stateName = state.replace('_', ' ').split(' ').map(word => 
            word.charAt(0).toUpperCase() + word.slice(1)
        ).join(' ');
        document.getElementById('currentState').textContent = `${emoji} ${stateName}`;
        
        // Update avatar display class for animations
        const avatarDisplay = document.getElementById('avatarDisplay');
        avatarDisplay.className = 'avatar-display ' + state;
        
        // Show/hide state overlay
        updateStateOverlay(state);
        
        console.log(`State updated: ${state}`);
    }
}

function updateStyle(style, color) {
    if (style && style !== currentStyle) {
        currentStyle = style;
        
        const styleEmojis = {
            'robot': '🤖', 'organic': '🌿', 'crystal': '💎',
            'ghost': '👻', 'energy': '⚡', 'cute': '🐣'
        };
        
        const emoji = styleEmojis[style] || '🤖';
        const styleName = style.charAt(0).toUpperCase() + style.slice(1);
        document.getElementById('currentStyle').textContent = `${emoji} ${styleName}`;
        
        generateAvatar();
    }
    
    if (color && color !== currentColor) {
        currentColor = color;
        generateAvatar();
    }
}

function updateStateOverlay(state) {
    const overlay = document.getElementById('stateOverlay');
    const icon = document.getElementById('stateIcon');
    const progressBar = document.getElementById('progressBar');
    
    const stateConfigs = {
        'pre_thinking': { icon: '🧠', showProgress: false, show: true },
        'thinking': { icon: '💭', showProgress: true, show: true },
        'generating': { icon: '⚡', showProgress: true, show: true },
        'speaking': { icon: '🗣️', showProgress: false, show: true },
        'post_response': { icon: '✅', showProgress: false, show: true },
        'error': { icon: '❌', showProgress: false, show: true },
        'loading': { icon: '⏳', showProgress: true, show: true },
        'farewell': { icon: '👋', showProgress: false, show: true },
        'idle': { show: false }
    };
    
    const config = stateConfigs[state] || { show: false };
    
    if (config.show) {
        overlay.style.display = 'flex';
        icon.textContent = config.icon;
        progressBar.style.display = config.showProgress ? 'block' : 'none';
    } else {
        overlay.style.display = 'none';
    }
}

function updateProgress(stage, progress, tokens, message) {
    const progressFill = document.getElementById('progressFill');
    if (progressFill) {
        progressFill.style.width = progress + '%';
    }
    
    // Update token counter
    if (tokens > 0) {
        const tokenStatus = document.getElementById('tokenStatus');
        const tokenText = document.getElementById('currentTokens');
        
        tokenStatus.style.display = 'block';
        tokenText.textContent = `🎯 ${tokens}`;
        tokenStatus.classList.add('active');
        setTimeout(() => tokenStatus.classList.remove('active'), 300);
    }
    
    console.log(`Progress: ${stage} ${progress}% (${tokens} tokens)`);
}

function updateMetrics(data) {
    if (data.energy_saved) {
        document.getElementById('energySaved').textContent = data.energy_saved + ' Wh';
    }
    if (data.water_saved) {
        document.getElementById('waterSaved').textContent = data.water_saved + ' ml';
    }
    if (data.co2_saved) {
        document.getElementById('co2Saved').textContent = data.co2_saved + 'g';
    }
    if (data.message_count) {
        document.getElementById('messageCount').textContent = data.message_count;
    }
}

function updateConnectionStatus(connected) {
    const statusElement = document.getElementById('connectionStatus');
    const textElement = document.getElementById('connectionText');
    const wsStatusElement = document.getElementById('wsStatus');
    
    if (connected) {
        statusElement.className = 'connection-status connected';
        textElement.textContent = 'Connected';
        wsStatusElement.textContent = 'Connected';
    } else {
        statusElement.className = 'connection-status disconnected';
        textElement.textContent = 'Disconnected';
        wsStatusElement.textContent = 'Disconnected';
    }
}

function updateMessageCount() {
    document.getElementById('totalMessages').textContent = messageCount;
}

// Particle functions
function createEmotionParticles(emotion, intensity) {
    const centerX = particleCanvas.width / 2;
    const centerY = particleCanvas.height / 2;
    const count = Math.floor(intensity / 10) + 3;
    
    const emotionConfigs = {
        'happy': { type: 'sparkle', color: '#FFD700', spread: 40 },
        'excited': { type: 'star', color: '#FF6B6B', spread: 60 },
        'love': { type: 'heart', color: '#FF69B4', spread: 30 },
        'surprised': { type: 'sparkle', color: '#00FFFF', spread: 80 },
        'thinking': { type: 'circle', color: '#9370DB', spread: 20 },
        'sad': { type: 'circle', color: '#4169E1', spread: 15 },
        'angry': { type: 'sparkle', color: '#FF4500', spread: 50 },
        'sleepy': { type: 'circle', color: '#DDA0DD', spread: 10 }
    };
    
    const config = emotionConfigs[emotion] || emotionConfigs['happy'];
    
    for (let i = 0; i < count; i++) {
        const angle = (Math.PI * 2 * i) / count + Math.random() * 0.5;
        const distance = Math.random() * config.spread;
        const x = centerX + Math.cos(angle) * distance;
        const y = centerY + Math.sin(angle) * distance;
        
        particles.push(new Particle(x, y, config.type, config.color));
    }
}

function updateParticles() {
    particleCtx.clearRect(0, 0, particleCanvas.width, particleCanvas.height);
    
    for (let i = particles.length - 1; i >= 0; i--) {
        const particle = particles[i];
        if (!particle.update()) {
            particles.splice(i, 1);
        } else {
            particle.draw(particleCtx);
        }
    }
}

// Avatar generation
function generateAvatar() {
    const pixelSize = 16;
    const gridSize = 16;
    
    // Clear canvas
    ctx.fillStyle = '#2D2D2D';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // Draw base shape
    drawAvatarShape(ctx, pixelSize, gridSize);
    
    // Apply emotion
    drawEmotion(ctx, pixelSize, gridSize);
    
    frameCount++;
}

function drawAvatarShape(ctx, pixelSize, gridSize) {
    ctx.fillStyle = currentColor;
    
    switch (currentStyle) {
        case 'robot':
            drawRobot(ctx, pixelSize, gridSize);
            break;
        case 'organic':
            drawOrganic(ctx, pixelSize, gridSize);
            break;
        case 'crystal':
            drawCrystal(ctx, pixelSize, gridSize);
            break;
        case 'ghost':
            drawGhost(ctx, pixelSize, gridSize);
            break;
        case 'energy':
            drawEnergy(ctx, pixelSize, gridSize);
            break;
        case 'cute':
            drawCute(ctx, pixelSize, gridSize);
            break;
        default:
            drawRobot(ctx, pixelSize, gridSize);
    }
}

function drawRobot(ctx, pixelSize, gridSize) {
    // Head outline
    for (let x = 4; x < 12; x++) {
        for (let y = 2; y < 10; y++) {
            if (y === 2 || y === 9 || x === 4 || x === 11) {
                ctx.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize);
            }
        }
    }
    
    // Fill head
    ctx.fillRect(5 * pixelSize, 3 * pixelSize, 6 * pixelSize, 6 * pixelSize);
    
    // Body
    for (let x = 5; x < 11; x++) {
        for (let y = 10; y < 14; y++) {
            ctx.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize);
        }
    }
    
    // Antenna
    ctx.fillRect(7 * pixelSize, pixelSize, pixelSize, pixelSize);
    ctx.fillRect(8 * pixelSize, pixelSize, pixelSize, pixelSize);
    ctx.fillRect(7 * pixelSize, 0, pixelSize, pixelSize);
    ctx.fillRect(8 * pixelSize, 0, pixelSize, pixelSize);
}

function drawOrganic(ctx, pixelSize, gridSize) {
    const centerX = 8;
    const centerY = 8;
    const radius = 5;
    
    for (let x = 0; x < gridSize; x++) {
        for (let y = 0; y < gridSize; y++) {
            const distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
            if (distance <= radius) {
                ctx.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize);
            }
        }
    }
}

function drawCrystal(ctx, pixelSize, gridSize) {
    const centerX = 8;
    const centerY = 8;
    
    for (let x = 0; x < gridSize; x++) {
        for (let y = 0; y < gridSize; y++) {
            const distance = Math.abs(x - centerX) + Math.abs(y - centerY);
            if (distance <= 6) {
                ctx.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize);
            }
        }
    }
}

function drawGhost(ctx, pixelSize, gridSize) {
    for (let x = 4; x < 12; x++) {
        for (let y = 3; y < 13; y++) {
            if (y < 10 || (y >= 10 && (x % 2 === 0))) {
                ctx.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize);
            }
        }
    }
}

function drawEnergy(ctx, pixelSize, gridSize) {
    const bolt = [
        [7, 2], [8, 2],
        [6, 3], [7, 3], [8, 3],
        [5, 4], [6, 4], [7, 4],
        [6, 5], [7, 5], [8, 5],
        [7, 6], [8, 6], [9, 6],
        [8, 7], [9, 7], [10, 7],
        [7, 8], [8, 8], [9, 8],
        [6, 9], [7, 9], [8, 9],
        [7, 10], [8, 10],
        [8, 11], [9, 11],
        [9, 12], [10, 12]
    ];
    
    bolt.forEach(([x, y]) => {
        ctx.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize);
    });
}

function drawCute(ctx, pixelSize, gridSize) {
    // Main body
    for (let x = 5; x < 11; x++) {
        for (let y = 5; y < 11; y++) {
            ctx.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize);
        }
    }
    
    // Ears
    ctx.fillRect(4 * pixelSize, 3 * pixelSize, pixelSize * 2, pixelSize * 2);
    ctx.fillRect(10 * pixelSize, 3 * pixelSize, pixelSize * 2, pixelSize * 2);
}

function drawEmotion(ctx, pixelSize, gridSize) {
    const emotion = pixelEmotions[currentEmotion] || pixelEmotions.happy;
    const intensity = emotionIntensity / 100;
    
    // Eyes
    ctx.fillStyle = '#FFFFFF';
    const eyeY = 5;
    
    for (let i = 0; i < emotion.eyes[0].length; i++) {
        if (emotion.eyes[Math.floor(intensity + 0.5)][i]) {
            const x = i % 4;
            const y = Math.floor(i / 4);
            ctx.fillRect((5 + x) * pixelSize, (eyeY + y) * pixelSize, pixelSize, pixelSize);
            ctx.fillRect((8 + x) * pixelSize, (eyeY + y) * pixelSize, pixelSize, pixelSize);
        }
    }
    
    // Mouth
    const mouthY = 7;
    for (let i = 0; i < emotion.mouth[0].length; i++) {
        if (emotion.mouth[Math.floor(intensity + 0.5)][i]) {
            const x = i % 4;
            const y = Math.floor(i / 4);
            ctx.fillRect((6 + x) * pixelSize, (mouthY + y) * pixelSize, pixelSize, pixelSize);
        }
    }
}

// Animation loop
function startAnimation() {
    function animate() {
        animationState.time += 0.016; // ~60fps
        
        // Breathing effect
        animationState.breathing = Math.sin(animationState.time * 1.2) * 0.01;
        
        // Apply breathing to canvas
        const scale = 1 + animationState.breathing;
        canvas.style.transform = `scale(${scale})`;
        
        // Update particles
        updateParticles();
        
        animationFrame = requestAnimationFrame(animate);
    }
    
    animate();
}

// Uptime counter
function startUptime() {
    function updateUptime() {
        const elapsed = Date.now() - startTime;
        const minutes = Math.floor(elapsed / 60000);
        const seconds = Math.floor((elapsed % 60000) / 1000);
        document.getElementById('uptime').textContent = `${minutes}:${seconds.toString().padStart(2, '0')}`;
    }
    
    updateUptime();
    setInterval(updateUptime, 1000);
}

// Utility function to play sounds
function playSound(eventType) {
    if (window.audioManager) {
        window.audioManager.play(eventType);
        console.log(`🔊 Playing sound: ${eventType}`);
    } else {
        console.log(`🔇 Audio not initialized yet, can't play: ${eventType}`);
        console.log('💡 Click anywhere on the page to enable audio');
    }
}