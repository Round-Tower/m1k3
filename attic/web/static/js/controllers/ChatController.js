/**
 * M1K3 Chat Controller
 * Handles chat interface, message history, and real-time conversation
 */
class ChatController extends EventTarget {
    constructor(stateManager, websocketManager) {
        super();
        
        this.stateManager = stateManager;
        this.websocketManager = websocketManager;
        
        this.elements = {};
        this.speechRecognition = null;
        this.isRecording = false;
        this.typingTimer = null;
        this.lastMessageId = 0;
        
        this.settings = {
            autoScroll: true,
            showTimestamps: true,
            soundEnabled: true,
            speechEnabled: false,
            maxMessages: 1000,
            typingIndicatorDelay: 500
        };
        
        console.log('💬 ChatController initialized');
    }
    
    /**
     * Initialize chat controller
     */
    async initialize() {
        this.findElements();
        this.setupEventListeners();
        this.initializeSpeechRecognition();
        this.loadMessageHistory();
        
        // Subscribe to state changes
        this.subscribeToStateChanges();
        
        console.log('💬 Chat controller initialized');
        return this;
    }
    
    /**
     * Find DOM elements
     */
    findElements() {
        this.elements = {
            chatContainer: document.getElementById('chatContainer'),
            chatMessages: document.getElementById('chatMessages'),
            chatInput: document.getElementById('chatInput'),
            sendButton: document.getElementById('sendButton'),
            voiceButton: document.getElementById('voiceButton'),
            clearButton: document.getElementById('clearChatButton'),
            settingsButton: document.getElementById('chatSettingsButton'),
            typingIndicator: document.getElementById('typingIndicator'),
            messageCounter: document.getElementById('messageCounter'),
            scrollToBottom: document.getElementById('scrollToBottomButton')
        };
        
        // Create missing elements
        this.createMissingElements();
    }
    
    /**
     * Create missing DOM elements
     */
    createMissingElements() {
        if (!this.elements.chatContainer) {
            console.warn('💬 Chat container not found, creating minimal structure');
            return;
        }
        
        // Create typing indicator if missing
        if (!this.elements.typingIndicator) {
            this.elements.typingIndicator = document.createElement('div');
            this.elements.typingIndicator.id = 'typingIndicator';
            this.elements.typingIndicator.className = 'typing-indicator';
            this.elements.typingIndicator.innerHTML = `
                <div class="typing-animation">
                    <span>M1K3 is thinking</span>
                    <div class="typing-dots">
                        <div class="dot"></div>
                        <div class="dot"></div>
                        <div class="dot"></div>
                    </div>
                </div>
            `;
            this.elements.typingIndicator.style.display = 'none';
            
            if (this.elements.chatMessages) {
                this.elements.chatMessages.appendChild(this.elements.typingIndicator);
            }
        }
        
        // Create scroll to bottom button if missing
        if (!this.elements.scrollToBottom && this.elements.chatContainer) {
            this.elements.scrollToBottom = document.createElement('button');
            this.elements.scrollToBottom.id = 'scrollToBottomButton';
            this.elements.scrollToBottom.className = 'scroll-to-bottom-btn';
            this.elements.scrollToBottom.innerHTML = '↓';
            this.elements.scrollToBottom.title = 'Scroll to bottom';
            this.elements.scrollToBottom.style.display = 'none';
            
            this.elements.chatContainer.appendChild(this.elements.scrollToBottom);
        }
    }
    
    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Chat input events
        if (this.elements.chatInput) {
            this.elements.chatInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    this.sendMessage();
                } else if (e.key === 'Enter' && e.shiftKey) {
                    // Allow line break with Shift+Enter
                    return;
                }
            });
            
            this.elements.chatInput.addEventListener('input', () => {
                this.handleInputChange();
                this.autoResizeInput();
            });
            
            this.elements.chatInput.addEventListener('paste', (e) => {
                setTimeout(() => this.autoResizeInput(), 0);
            });
        }
        
        // Send button
        if (this.elements.sendButton) {
            this.elements.sendButton.addEventListener('click', () => {
                this.sendMessage();
            });
        }
        
        // Voice button
        if (this.elements.voiceButton) {
            this.elements.voiceButton.addEventListener('mousedown', () => {
                this.startVoiceInput();
            });
            
            this.elements.voiceButton.addEventListener('mouseup', () => {
                this.stopVoiceInput();
            });
            
            this.elements.voiceButton.addEventListener('mouseleave', () => {
                this.stopVoiceInput();
            });
            
            // Touch events for mobile
            this.elements.voiceButton.addEventListener('touchstart', (e) => {
                e.preventDefault();
                this.startVoiceInput();
            });
            
            this.elements.voiceButton.addEventListener('touchend', (e) => {
                e.preventDefault();
                this.stopVoiceInput();
            });
        }
        
        // Clear button
        if (this.elements.clearButton) {
            this.elements.clearButton.addEventListener('click', () => {
                this.clearMessages();
            });
        }
        
        // Scroll to bottom button
        if (this.elements.scrollToBottom) {
            this.elements.scrollToBottom.addEventListener('click', () => {
                this.scrollToBottom();
            });
        }
        
        // Auto-hide scroll button on manual scroll
        if (this.elements.chatMessages) {
            this.elements.chatMessages.addEventListener('scroll', () => {
                this.handleScroll();
            });
        }
        
        // WebSocket message events
        if (this.websocketManager) {
            this.websocketManager.addEventListener('message.chat_ai', (e) => {
                this.handleAIMessage(e.detail.data);
            });
            
            this.websocketManager.addEventListener('message.ai_response', (e) => {
                this.handleAIMessage(e.detail.data);
            });
            
            // Handle streaming chunks
            this.websocketManager.addEventListener('chat.chunk', (e) => {
                this.handleStreamingChunk(e.detail.chunk);
            });
            
            // Handle typing indicators
            this.websocketManager.addEventListener('message.typing_start', () => {
                this.showTypingIndicator();
            });
            
            this.websocketManager.addEventListener('message.typing_stop', () => {
                this.hideTypingIndicator();
            });
        }
    }
    
    /**
     * Subscribe to state changes
     */
    subscribeToStateChanges() {
        // Listen for chat messages
        this.stateManager.subscribe('chat.messages', (event) => {
            this.renderMessages();
        });
        
        // Listen for AI typing state
        this.stateManager.subscribe('ai.state', (event) => {
            const aiState = event.detail.value;
            if (aiState === 'thinking' || aiState === 'generating') {
                this.showTypingIndicator();
            } else {
                this.hideTypingIndicator();
            }
        });
        
        // Listen for voice state changes
        this.stateManager.subscribe('voice.synthesizing', (event) => {
            this.updateVoiceStatus(event.detail.value);
        });
    }
    
    /**
     * Send chat message
     */
    sendMessage() {
        const input = this.elements.chatInput;
        if (!input) return;
        
        const message = input.value.trim();
        if (!message) return;
        
        // Add to state
        this.stateManager.addChatMessage(message, 'user');
        
        // Send via WebSocket
        if (this.websocketManager && this.websocketManager.isConnected) {
            this.websocketManager.send({
                type: 'chat_user',
                message: message,
                timestamp: Date.now()
            });
        }
        
        // Clear input
        input.value = '';
        this.autoResizeInput();
        
        // Focus back to input
        setTimeout(() => input.focus(), 0);
        
        // Play sound
        this.playSound('message_sent');
        
        this.emit('message.sent', { message });
    }
    
    /**
     * Handle AI message
     */
    handleAIMessage(data) {
        if (data.message) {
            this.stateManager.addChatMessage(data.message, 'ai');
            this.playSound('message_received');
        }
        
        if (data.chunk) {
            this.handleStreamingChunk(data.chunk);
        }
        
        if (data.complete) {
            this.hideTypingIndicator();
        }
    }
    
    /**
     * Handle streaming message chunks for real-time display
     */
    handleStreamingChunk(chunk) {
        if (!chunk) return;
        
        // Show typing indicator if this is the start of a new message
        if (!this.currentStreamingMessage) {
            this.showTypingIndicator();
            this.currentStreamingMessage = {
                id: Date.now() + Math.random(),
                sender: 'ai',
                content: '',
                timestamp: Date.now(),
                isStreaming: true
            };
        }
        
        // Append chunk to current streaming message
        this.currentStreamingMessage.content += chunk;
        
        // Update the display
        this.renderStreamingMessage();
    }
    
    /**
     * Render streaming message in real-time
     */
    renderStreamingMessage() {
        if (!this.currentStreamingMessage) return;
        
        let streamingElement = document.getElementById('streaming-message');
        
        if (!streamingElement) {
            streamingElement = this.createMessageElement(this.currentStreamingMessage);
            streamingElement.id = 'streaming-message';
            streamingElement.classList.add('streaming');
            
            if (this.elements.chatMessages) {
                this.elements.chatMessages.appendChild(streamingElement);
                this.scrollToBottom();
            }
        }
        
        // Update content
        const messageContent = streamingElement.querySelector('.message-content');
        if (messageContent) {
            messageContent.textContent = this.currentStreamingMessage.content;
        }
    }
    
    /**
     * Finalize streaming message
     */
    finalizeStreamingMessage() {
        if (this.currentStreamingMessage) {
            // Add to state manager as completed message
            this.stateManager.addChatMessage(this.currentStreamingMessage.content, 'ai');
            
            // Remove streaming indicator
            const streamingElement = document.getElementById('streaming-message');
            if (streamingElement) {
                streamingElement.remove();
            }
            
            // Clear streaming state
            this.currentStreamingMessage = null;
            this.hideTypingIndicator();
        }
    }
    
    /**
     * Append text to last AI message (for streaming)
     */
    appendToLastMessage(chunk) {
        const messages = this.stateManager.getState('chat.messages') || [];
        const lastMessage = messages[messages.length - 1];
        
        if (lastMessage && lastMessage.sender === 'ai') {
            lastMessage.message += chunk;
            lastMessage.timestamp = Date.now();
            
            this.stateManager.updateState('chat.messages', messages, { silent: true });
            this.renderLastMessage();
            
            if (this.settings.autoScroll) {
                this.scrollToBottom();
            }
        }
    }
    
    /**
     * Render all messages
     */
    renderMessages() {
        if (!this.elements.chatMessages) return;
        
        const messages = this.stateManager.getState('chat.messages') || [];
        this.elements.chatMessages.innerHTML = '';
        
        messages.forEach(message => this.renderMessage(message));
        
        // Re-add typing indicator
        if (this.elements.typingIndicator) {
            this.elements.chatMessages.appendChild(this.elements.typingIndicator);
        }
        
        this.updateMessageCounter(messages.length);
        
        if (this.settings.autoScroll) {
            this.scrollToBottom();
        }
    }
    
    /**
     * Render last message (for streaming updates)
     */
    renderLastMessage() {
        if (!this.elements.chatMessages) return;
        
        const messages = this.stateManager.getState('chat.messages') || [];
        const lastMessage = messages[messages.length - 1];
        
        if (!lastMessage) return;
        
        const messageElements = this.elements.chatMessages.querySelectorAll('.chat-message');
        const lastMessageElement = messageElements[messageElements.length - 1];
        
        if (lastMessageElement) {
            const contentElement = lastMessageElement.querySelector('.message-content');
            if (contentElement) {
                contentElement.innerHTML = this.formatMessageContent(lastMessage.message);
            }
        }
    }
    
    /**
     * Render single message
     */
    renderMessage(message) {
        if (!this.elements.chatMessages) return;
        
        const messageElement = document.createElement('div');
        messageElement.className = `chat-message message-${message.sender}`;
        messageElement.dataset.messageId = message.id;
        
        const avatarElement = document.createElement('div');
        avatarElement.className = 'message-avatar';
        avatarElement.textContent = message.sender === 'user' ? '👤' : '🤖';
        
        const contentWrapper = document.createElement('div');
        contentWrapper.className = 'message-wrapper';
        
        const contentElement = document.createElement('div');
        contentElement.className = 'message-content';
        contentElement.innerHTML = this.formatMessageContent(message.message);
        
        const timeElement = document.createElement('div');
        timeElement.className = 'message-time';
        timeElement.textContent = this.formatTime(message.timestamp);
        
        contentWrapper.appendChild(contentElement);
        if (this.settings.showTimestamps) {
            contentWrapper.appendChild(timeElement);
        }
        
        messageElement.appendChild(avatarElement);
        messageElement.appendChild(contentWrapper);
        
        // Insert before typing indicator if it exists
        const typingIndicator = this.elements.chatMessages.querySelector('.typing-indicator');
        if (typingIndicator) {
            this.elements.chatMessages.insertBefore(messageElement, typingIndicator);
        } else {
            this.elements.chatMessages.appendChild(messageElement);
        }
        
        // Animate in
        requestAnimationFrame(() => {
            messageElement.classList.add('animate-in');
        });
    }
    
    /**
     * Format message content (basic markdown support)
     */
    formatMessageContent(content) {
        return content
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.*?)\*/g, '<em>$1</em>')
            .replace(/`(.*?)`/g, '<code>$1</code>')
            .replace(/\n/g, '<br>');
    }
    
    /**
     * Format timestamp
     */
    formatTime(timestamp) {
        return new Date(timestamp).toLocaleTimeString('en-US', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit'
        });
    }
    
    /**
     * Show typing indicator
     */
    showTypingIndicator() {
        if (this.elements.typingIndicator) {
            this.elements.typingIndicator.style.display = 'block';
            
            if (this.settings.autoScroll) {
                this.scrollToBottom();
            }
        }
    }
    
    /**
     * Hide typing indicator
     */
    hideTypingIndicator() {
        if (this.elements.typingIndicator) {
            this.elements.typingIndicator.style.display = 'none';
        }
    }
    
    /**
     * Auto-resize chat input
     */
    autoResizeInput() {
        const input = this.elements.chatInput;
        if (!input) return;
        
        input.style.height = 'auto';
        input.style.height = Math.min(input.scrollHeight, 120) + 'px';
        
        // Update send button state
        if (this.elements.sendButton) {
            this.elements.sendButton.disabled = !input.value.trim();
        }
    }
    
    /**
     * Handle input change (for typing indicators, etc.)
     */
    handleInputChange() {
        clearTimeout(this.typingTimer);
        
        this.typingTimer = setTimeout(() => {
            // Could send typing indicator to other clients here
        }, this.settings.typingIndicatorDelay);
    }
    
    /**
     * Handle scroll events
     */
    handleScroll() {
        const container = this.elements.chatMessages;
        if (!container) return;
        
        const isNearBottom = container.scrollTop + container.clientHeight >= container.scrollHeight - 100;
        
        // Show/hide scroll to bottom button
        if (this.elements.scrollToBottom) {
            this.elements.scrollToBottom.style.display = isNearBottom ? 'none' : 'block';
        }
        
        // Update auto-scroll setting based on user behavior
        this.settings.autoScroll = isNearBottom;
    }
    
    /**
     * Scroll to bottom
     */
    scrollToBottom() {
        const container = this.elements.chatMessages;
        if (!container) return;
        
        container.scrollTop = container.scrollHeight;
        
        if (this.elements.scrollToBottom) {
            this.elements.scrollToBottom.style.display = 'none';
        }
    }
    
    /**
     * Clear all messages
     */
    clearMessages() {
        if (confirm('Clear all chat messages?')) {
            this.stateManager.updateState('chat.messages', []);
            this.hideTypingIndicator();
            
            // Focus back to input
            if (this.elements.chatInput) {
                this.elements.chatInput.focus();
            }
            
            this.emit('messages.cleared');
        }
    }
    
    /**
     * Update message counter
     */
    updateMessageCounter(count) {
        if (this.elements.messageCounter) {
            this.elements.messageCounter.textContent = `${count} messages`;
        }
    }
    
    /**
     * Initialize speech recognition
     */
    initializeSpeechRecognition() {
        if ('SpeechRecognition' in window || 'webkitSpeechRecognition' in window) {
            const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
            this.speechRecognition = new SpeechRecognition();
            
            this.speechRecognition.continuous = false;
            this.speechRecognition.interimResults = false;
            this.speechRecognition.lang = 'en-US';
            
            this.speechRecognition.onresult = (event) => {
                const transcript = event.results[0][0].transcript;
                if (this.elements.chatInput) {
                    this.elements.chatInput.value = transcript;
                    this.autoResizeInput();
                }
                this.emit('speech.result', { transcript });
            };
            
            this.speechRecognition.onerror = (event) => {
                console.error('Speech recognition error:', event.error);
                this.stopVoiceInput();
                this.emit('speech.error', { error: event.error });
            };
            
            this.speechRecognition.onend = () => {
                this.stopVoiceInput();
            };
            
            this.settings.speechEnabled = true;
        } else {
            console.warn('Speech recognition not supported');
            if (this.elements.voiceButton) {
                this.elements.voiceButton.disabled = true;
            }
        }
    }
    
    /**
     * Start voice input
     */
    startVoiceInput() {
        if (!this.speechRecognition || this.isRecording) return;
        
        try {
            this.speechRecognition.start();
            this.isRecording = true;
            
            if (this.elements.voiceButton) {
                this.elements.voiceButton.classList.add('recording');
            }
            
            this.playSound('voice_start');
            this.emit('voice.start');
            
        } catch (error) {
            console.error('Failed to start speech recognition:', error);
        }
    }
    
    /**
     * Stop voice input
     */
    stopVoiceInput() {
        if (!this.speechRecognition || !this.isRecording) return;
        
        try {
            this.speechRecognition.stop();
            this.isRecording = false;
            
            if (this.elements.voiceButton) {
                this.elements.voiceButton.classList.remove('recording');
            }
            
            this.playSound('voice_end');
            this.emit('voice.stop');
            
        } catch (error) {
            console.error('Failed to stop speech recognition:', error);
        }
    }
    
    /**
     * Update voice status display
     */
    updateVoiceStatus(synthesizing) {
        // Could show voice synthesis indicator
        this.emit('voice.status', { synthesizing });
    }
    
    /**
     * Play sound effect
     */
    playSound(soundName) {
        if (!this.settings.soundEnabled) return;
        
        // Could integrate with audio system
        console.log(`🔊 Playing sound: ${soundName}`);
        this.emit('sound.play', { soundName });
    }
    
    /**
     * Load message history
     */
    loadMessageHistory() {
        // Could load from localStorage or server
        const saved = localStorage.getItem('m1k3_chat_history');
        if (saved) {
            try {
                const messages = JSON.parse(saved);
                this.stateManager.updateState('chat.messages', messages);
            } catch (error) {
                console.error('Failed to load chat history:', error);
            }
        }
    }
    
    /**
     * Save message history
     */
    saveMessageHistory() {
        const messages = this.stateManager.getState('chat.messages') || [];
        try {
            localStorage.setItem('m1k3_chat_history', JSON.stringify(messages.slice(-100))); // Keep last 100
        } catch (error) {
            console.error('Failed to save chat history:', error);
        }
    }
    
    /**
     * Export chat history
     */
    exportHistory() {
        const messages = this.stateManager.getState('chat.messages') || [];
        const content = messages.map(msg => 
            `[${this.formatTime(msg.timestamp)}] ${msg.sender}: ${msg.message}`
        ).join('\n');
        
        const blob = new Blob([content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `m1k3-chat-${Date.now()}.txt`;
        a.click();
        URL.revokeObjectURL(url);
    }
    
    /**
     * Update settings
     */
    updateSettings(newSettings) {
        Object.assign(this.settings, newSettings);
        
        // Apply settings
        if ('autoScroll' in newSettings) {
            // Auto scroll setting changed
        }
        
        if ('showTimestamps' in newSettings) {
            this.renderMessages(); // Re-render to show/hide timestamps
        }
        
        this.emit('settings.updated', { settings: this.settings });
    }
    
    /**
     * Get current settings
     */
    getSettings() {
        return { ...this.settings };
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
        this.saveMessageHistory();
        
        if (this.speechRecognition) {
            this.speechRecognition.abort();
        }
        
        clearTimeout(this.typingTimer);
        
        console.log('💬 ChatController destroyed');
    }
}

// Export for global use
window.ChatController = ChatController;