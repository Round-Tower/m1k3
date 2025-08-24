/**
 * M1K3 Chat Controller - Manages chat interface and messaging
 */
class ChatController {
    constructor(stateManager, navigationManager) {
        this.stateManager = stateManager;
        this.navigationManager = navigationManager;
        this.chatInput = null;
        this.chatMessages = null;
        this.sendButton = null;
        this.voiceButton = null;
        this.clearChatBtn = null;
        this.voiceToggleBtn = null;
        this.recognition = null;
        this.isRecording = false;
        
        console.log('💬 ChatController initialized');
    }
    
    // Initialize chat controller
    async initialize() {
        this.bindElements();
        this.setupEventListeners();
        this.initializeSpeechRecognition();
        this.restoreFromState();
        
        console.log('💬 Chat interface ready');
    }
    
    // Bind DOM elements
    bindElements() {
        this.chatInput = document.getElementById('chatInput');
        this.chatMessages = document.getElementById('chatMessages');
        this.sendButton = document.getElementById('sendButton');
        this.voiceButton = document.getElementById('voiceButton');
        this.clearChatBtn = document.getElementById('clearChatBtn');
        this.voiceToggleBtn = document.getElementById('voiceToggleBtn');
        this.miniAvatar = document.getElementById('miniAvatar');
        this.miniAvatarEmoji = document.getElementById('miniAvatarEmoji');
        this.connectionStatus = document.getElementById('connectionStatus');
        this.connectionText = document.getElementById('connectionText');
        this.statusDot = document.getElementById('statusDot');
    }
    
    // Setup event listeners
    setupEventListeners() {
        // Chat input events
        if (this.chatInput) {
            this.chatInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    this.sendMessage();
                }
            });
            
            // Auto-resize textarea
            this.chatInput.addEventListener('input', () => {
                this.chatInput.style.height = 'auto';
                this.chatInput.style.height = Math.min(this.chatInput.scrollHeight, 120) + 'px';
            });
        }
        
        // Send button
        if (this.sendButton) {
            this.sendButton.addEventListener('click', () => this.sendMessage());
        }
        
        // Voice button
        if (this.voiceButton) {
            this.voiceButton.addEventListener('mousedown', () => this.startRecording());
            this.voiceButton.addEventListener('mouseup', () => this.stopRecording());
            this.voiceButton.addEventListener('mouseleave', () => this.stopRecording());
            this.voiceButton.addEventListener('touchstart', () => this.startRecording());
            this.voiceButton.addEventListener('touchend', () => this.stopRecording());
        }
        
        // Clear chat button
        if (this.clearChatBtn) {
            this.clearChatBtn.addEventListener('click', () => this.clearChat());
        }
        
        // Voice toggle button
        if (this.voiceToggleBtn) {
            this.voiceToggleBtn.addEventListener('click', () => this.toggleVoice());
        }
        
        // State listeners
        this.stateManager.subscribe('isConnected', (connected) => {
            this.updateConnectionStatus(connected);
        });
        
        this.stateManager.subscribe('currentEmotion', (emotion) => {
            this.updateMiniAvatar(emotion);
        });
        
        this.stateManager.subscribe('chat.messages', (messages) => {
            this.renderMessages(messages);
        });
        
        this.stateManager.subscribe('ui.soundsEnabled', (enabled) => {
            this.updateVoiceButton(enabled);
        });
    }
    
    // Initialize speech recognition
    initializeSpeechRecognition() {
        if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
            const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
            this.recognition = new SpeechRecognition();
            this.recognition.continuous = false;
            this.recognition.interimResults = false;
            this.recognition.lang = 'en-US';
            
            this.recognition.onresult = (event) => {
                const transcript = event.results[0][0].transcript;
                if (this.chatInput) {
                    this.chatInput.value = transcript;
                }
            };
            
            this.recognition.onerror = (event) => {
                console.error('Speech recognition error:', event.error);
                this.stopRecording();
            };
            
            this.recognition.onend = () => {
                this.stopRecording();
            };
        }
    }
    
    // Restore interface from state
    restoreFromState() {
        const messages = this.stateManager.get('chat.messages') || [];
        this.renderMessages(messages);
        
        const connected = this.stateManager.get('isConnected');
        this.updateConnectionStatus(connected);
        
        const emotion = this.stateManager.get('currentEmotion');
        this.updateMiniAvatar(emotion);
        
        const soundsEnabled = this.stateManager.get('ui.soundsEnabled');
        this.updateVoiceButton(soundsEnabled);
    }
    
    // Send a message
    sendMessage() {
        if (!this.chatInput || !this.chatInput.value.trim()) return;
        
        const message = this.chatInput.value.trim();
        
        // Add to messages
        this.addMessage(message, 'user');
        
        // Send via WebSocket if connected
        const websocket = this.stateManager.get('websocket');
        if (websocket && websocket.readyState === WebSocket.OPEN) {
            websocket.send(JSON.stringify({
                type: 'chat_user',
                message: message,
                timestamp: Date.now()
            }));
        }
        
        // Clear input
        this.chatInput.value = '';
        this.chatInput.style.height = 'auto';
        
        // Play sound
        this.playSound('message_sent');
        
        console.log('💬 Message sent:', message);
    }
    
    // Add message to chat
    addMessage(message, sender, timestamp = null) {
        const messages = this.stateManager.get('chat.messages') || [];
        const newMessage = {
            id: Date.now() + Math.random(),
            message,
            sender,
            timestamp: timestamp || Date.now()
        };
        
        messages.push(newMessage);
        this.stateManager.set('chat.messages', messages);
        
        // Auto-scroll to bottom
        this.scrollToBottom();
    }
    
    // Render messages from state
    renderMessages(messages) {
        if (!this.chatMessages) return;
        
        // Keep the initial greeting if no messages
        if (!messages || messages.length === 0) {
            this.chatMessages.innerHTML = `
                <div class="message ai">
                    <div class="message-content">
                        <div class="message-text">Hello! I'm M1K3, your AI companion. How can I assist you today?</div>
                        <div class="message-time">Just now</div>
                    </div>
                </div>
            `;
            return;
        }
        
        // Render all messages
        this.chatMessages.innerHTML = '';
        messages.forEach(msg => {
            const messageDiv = this.createMessageElement(msg);
            this.chatMessages.appendChild(messageDiv);
        });
        
        this.scrollToBottom();
    }
    
    // Create message DOM element
    createMessageElement(message) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${message.sender}`;
        
        const content = document.createElement('div');
        content.className = 'message-content';
        
        const text = document.createElement('div');
        text.className = 'message-text';
        text.textContent = message.message;
        
        const time = document.createElement('div');
        time.className = 'message-time';
        time.textContent = new Date(message.timestamp).toLocaleTimeString();
        
        content.appendChild(text);
        content.appendChild(time);
        messageDiv.appendChild(content);
        
        return messageDiv;
    }
    
    // Append text to last AI message (for streaming)
    appendToLastMessage(chunk) {
        if (!this.chatMessages) return;
        
        const lastMessage = this.chatMessages.lastElementChild;
        if (lastMessage && lastMessage.classList.contains('ai')) {
            const textElement = lastMessage.querySelector('.message-text');
            if (textElement) {
                textElement.textContent += chunk;
                this.scrollToBottom();
            }
        }
    }
    
    // Show typing indicator
    showTypingIndicator(show) {
        if (!this.chatMessages) return;
        
        const existingIndicator = this.chatMessages.querySelector('.typing-indicator');
        if (existingIndicator) {
            existingIndicator.remove();
        }
        
        if (show) {
            const indicator = document.createElement('div');
            indicator.className = 'typing-indicator';
            indicator.innerHTML = `
                <span>M1K3 is thinking</span>
                <div class="typing-dots">
                    <div class="typing-dot"></div>
                    <div class="typing-dot"></div>
                    <div class="typing-dot"></div>
                </div>
            `;
            this.chatMessages.appendChild(indicator);
            this.scrollToBottom();
        }
    }
    
    // Clear chat messages
    clearChat() {
        this.stateManager.set('chat.messages', []);
        this.renderMessages([]);
        console.log('💬 Chat cleared');
    }
    
    // Start voice recording
    startRecording() {
        if (this.recognition && !this.isRecording) {
            this.isRecording = true;
            this.voiceButton?.classList.add('recording');
            
            try {
                this.recognition.start();
                this.playSound('voice_start');
            } catch (error) {
                console.error('Error starting speech recognition:', error);
                this.stopRecording();
            }
        }
    }
    
    // Stop voice recording
    stopRecording() {
        if (this.recognition && this.isRecording) {
            this.isRecording = false;
            this.voiceButton?.classList.remove('recording');
            
            try {
                this.recognition.stop();
                this.playSound('voice_end');
            } catch (error) {
                console.error('Error stopping speech recognition:', error);
            }
        }
    }
    
    // Toggle voice synthesis
    toggleVoice() {
        const soundsEnabled = this.stateManager.get('ui.soundsEnabled');
        this.stateManager.set('ui.soundsEnabled', !soundsEnabled);
        this.playSound('click');
    }
    
    // Update connection status display
    updateConnectionStatus(connected) {
        if (this.statusDot && this.connectionText) {
            if (connected) {
                this.statusDot.classList.remove('offline');
                this.connectionText.textContent = 'Connected';
            } else {
                this.statusDot.classList.add('offline');
                this.connectionText.textContent = 'Disconnected';
            }
        }
    }
    
    // Update mini avatar display
    updateMiniAvatar(emotion) {
        if (this.miniAvatarEmoji) {
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
            
            this.miniAvatarEmoji.textContent = emojiMap[emotion] || '😊';
        }
        
        if (this.miniAvatar) {
            if (emotion === 'thinking') {
                this.miniAvatar.classList.add('thinking');
            } else {
                this.miniAvatar.classList.remove('thinking');
            }
        }
    }
    
    // Update voice button appearance
    updateVoiceButton(soundsEnabled) {
        if (this.voiceToggleBtn) {
            this.voiceToggleBtn.textContent = soundsEnabled ? '🔊 Voice' : '🔇 Voice';
            this.voiceToggleBtn.style.opacity = soundsEnabled ? '1' : '0.6';
        }
    }
    
    // Scroll chat to bottom
    scrollToBottom() {
        if (this.chatMessages) {
            this.chatMessages.scrollTop = this.chatMessages.scrollHeight;
        }
    }
    
    // Play sound effect
    playSound(soundName) {
        const soundsEnabled = this.stateManager.get('ui.soundsEnabled');
        if (!soundsEnabled) return;
        
        // This would integrate with audio system if available
        console.log(`🔊 Playing sound: ${soundName}`);
    }
    
    // Handle incoming WebSocket messages
    handleWebSocketMessage(data) {
        switch (data.type) {
            case 'chat_ai_start':
                this.addMessage('', 'ai');
                this.showTypingIndicator(true);
                break;
                
            case 'chat_ai_chunk':
                this.appendToLastMessage(data.chunk);
                break;
                
            case 'chat_ai_complete':
                this.showTypingIndicator(false);
                this.playSound('message_received');
                break;
                
            case 'chat_user':
                this.addMessage(data.message, 'user');
                break;
        }
    }
    
    // Called when view is shown
    async onShow() {
        // Focus input when chat view is shown
        setTimeout(() => {
            if (this.chatInput && window.innerWidth > 767) {
                this.chatInput.focus();
            }
        }, 100);
    }
    
    // Called when view is hidden
    async onHide() {
        // Stop any ongoing voice recording
        this.stopRecording();
    }
    
    // Cleanup resources
    cleanup() {
        this.stopRecording();
        
        // Stop speech recognition
        if (this.recognition) {
            try {
                this.recognition.abort();
            } catch (error) {
                // Ignore errors on cleanup
            }
        }
    }
}

// Export as global
if (typeof window !== 'undefined') {
    window.ChatController = ChatController;
}

// Module exports
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ChatController;
}