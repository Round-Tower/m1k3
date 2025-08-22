/**
 * M1K3 Chat Interface
 * Handles user interactions and message display
 */

class ChatInterface {
    constructor(modelLoader) {
        this.modelLoader = modelLoader;
        this.chatMessages = document.getElementById('chat-messages');
        this.userInput = document.getElementById('user-input');
        this.sendBtn = document.getElementById('send-btn');
        this.typingIndicator = document.getElementById('typing-indicator');
        
        this.conversationHistory = [];
        this.isProcessing = false;
        
        this.setupEventListeners();
    }

    setupEventListeners() {
        // Send button click
        this.sendBtn.addEventListener('click', () => this.sendMessage());
        
        // Enter key handling
        this.userInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });
        
        // Input change handling
        this.userInput.addEventListener('input', () => {
            this.updateSendButton();
            this.autoResizeTextarea();
        });
        
        // Initial state
        this.updateSendButton();
    }

    updateSendButton() {
        const hasText = this.userInput.value.trim().length > 0;
        const modelReady = this.modelLoader.getModelStatus().hasModel;
        
        this.sendBtn.disabled = !hasText || !modelReady || this.isProcessing;
        
        if (!modelReady) {
            this.sendBtn.title = 'Model loading...';
        } else if (this.isProcessing) {
            this.sendBtn.title = 'Processing...';
        } else if (!hasText) {
            this.sendBtn.title = 'Type a message first';
        } else {
            this.sendBtn.title = 'Send message';
        }
    }

    autoResizeTextarea() {
        this.userInput.style.height = 'auto';
        const maxHeight = 120; // 6 lines approximately
        const newHeight = Math.min(this.userInput.scrollHeight, maxHeight);
        this.userInput.style.height = newHeight + 'px';
    }

    async sendMessage() {
        const message = this.userInput.value.trim();
        if (!message || this.isProcessing) return;

        this.isProcessing = true;
        this.updateSendButton();

        try {
            // Add user message to chat
            this.addMessage(message, 'user');
            
            // Clear input
            this.userInput.value = '';
            this.autoResizeTextarea();
            
            // Create streaming AI message placeholder
            const aiMessageElement = this.addStreamingMessage('', 'ai');
            
            // Show typing indicator briefly
            this.showTypingIndicator();
            await new Promise(resolve => setTimeout(resolve, 500));
            this.hideTypingIndicator();
            
            // Get streaming AI response
            await this.getStreamingAIResponse(message, 
                (tokenData) => this.updateStreamingMessage(aiMessageElement, tokenData),
                (progressData) => this.updateProgress(progressData)
            );
            
        } catch (error) {
            console.error('Error sending message:', error);
            this.hideTypingIndicator();
            this.addMessage('Sorry, I encountered an error processing your message. Please try again.', 'ai', true);
        } finally {
            this.isProcessing = false;
            this.updateSendButton();
        }
    }

    async getAIResponse(message) {
        try {
            // Add to conversation history
            this.conversationHistory.push({ role: 'user', content: message });
            
            // Prepare context
            const context = this.buildContext();
            
            // Run inference
            const response = await this.modelLoader.runInference(context);
            
            // Add to conversation history
            this.conversationHistory.push({ role: 'assistant', content: response });
            
            // Keep conversation history manageable
            if (this.conversationHistory.length > 20) {
                this.conversationHistory = this.conversationHistory.slice(-20);
            }
            
            return response;
            
        } catch (error) {
            console.error('AI response error:', error);
            
            // Fallback responses based on message content
            return this.getFallbackResponse(message);
        }
    }

    async getStreamingAIResponse(message, onToken, onProgress) {
        try {
            // Add to conversation history
            this.conversationHistory.push({ role: 'user', content: message });
            
            // Prepare context
            const context = this.buildContext();
            
            // Run streaming inference
            const response = await this.modelLoader.runInference(context, {
                onToken: onToken,
                onProgress: onProgress
            });
            
            // Add to conversation history
            this.conversationHistory.push({ role: 'assistant', content: response });
            
            // Keep conversation history manageable
            if (this.conversationHistory.length > 20) {
                this.conversationHistory = this.conversationHistory.slice(-20);
            }
            
            return response;
            
        } catch (error) {
            console.error('AI response error:', error);
            
            // Fallback responses based on message content
            return this.getFallbackResponse(message);
        }
    }

    buildContext() {
        // Build conversation context for the model
        let context = "You are M1K3, a helpful AI assistant running locally in the user's browser. ";
        context += "Respond naturally and helpfully. Keep responses concise but informative.\n\n";
        
        // Add recent conversation history
        const recentHistory = this.conversationHistory.slice(-6); // Last 3 exchanges
        
        for (const turn of recentHistory) {
            if (turn.role === 'user') {
                context += `Human: ${turn.content}\n`;
            } else {
                context += `Assistant: ${turn.content}\n`;
            }
        }
        
        context += "Assistant: ";
        return context;
    }

    getFallbackResponse(message) {
        const msg = message.toLowerCase();
        
        // Simple pattern matching for fallback responses
        if (msg.includes('hello') || msg.includes('hi') || msg.includes('hey')) {
            return "Hello! I'm M1K3, your local AI assistant. How can I help you today?";
        }
        
        if (msg.includes('thank') || msg.includes('thanks')) {
            return "You're welcome! I'm happy to help.";
        }
        
        if (msg.includes('how are you') || msg.includes('how do you feel')) {
            return "I'm doing well! As a local AI, I'm running entirely in your browser, keeping your conversations private.";
        }
        
        if (msg.includes('what can you do') || msg.includes('help')) {
            return "I can help with various tasks like answering questions, writing, brainstorming, and general conversation. What would you like assistance with?";
        }
        
        if (msg.includes('privacy') || msg.includes('data')) {
            return "Your privacy is my priority! I run completely locally in your browser - no data is sent to external servers.";
        }
        
        // Default response
        return "I understand your message. As a local AI assistant, I'm here to help while keeping your data completely private. Could you tell me more about what you'd like assistance with?";
    }

    addMessage(content, sender, isError = false) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender}-message${isError ? ' error-message' : ''}`;
        
        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = sender === 'user' ? '👤' : '🤖';
        
        const messageContent = document.createElement('div');
        messageContent.className = 'message-content';
        
        const messageText = document.createElement('div');
        messageText.className = 'message-text';
        messageText.textContent = content;
        
        const messageTime = document.createElement('div');
        messageTime.className = 'message-time';
        messageTime.textContent = this.formatTime(new Date());
        
        messageContent.appendChild(messageText);
        messageContent.appendChild(messageTime);
        messageDiv.appendChild(avatar);
        messageDiv.appendChild(messageContent);
        
        // Add with animation
        messageDiv.style.opacity = '0';
        messageDiv.style.transform = 'translateY(20px)';
        this.chatMessages.appendChild(messageDiv);
        
        // Animate in
        requestAnimationFrame(() => {
            messageDiv.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
            messageDiv.style.opacity = '1';
            messageDiv.style.transform = 'translateY(0)';
        });
        
        // Scroll to bottom
        this.scrollToBottom();
    }

    showTypingIndicator() {
        this.typingIndicator.style.display = 'flex';
        this.scrollToBottom();
    }

    hideTypingIndicator() {
        this.typingIndicator.style.display = 'none';
    }

    scrollToBottom() {
        requestAnimationFrame(() => {
            this.chatMessages.scrollTop = this.chatMessages.scrollHeight;
        });
    }

    formatTime(date) {
        return date.toLocaleTimeString([], { 
            hour: '2-digit', 
            minute: '2-digit' 
        });
    }

    updateModelInfo(modelInfo) {
        const modelNameEl = document.getElementById('current-model-name');
        const modelTierEl = document.getElementById('current-model-tier');
        
        if (modelNameEl && modelInfo) {
            modelNameEl.textContent = modelInfo.name || 'Local Model';
        }
        
        if (modelTierEl && modelInfo) {
            modelTierEl.textContent = modelInfo.tier ? `(${modelInfo.tier.toUpperCase()})` : '';
        }
    }

    showError(message) {
        this.addMessage(message, 'ai', true);
    }

    clearChat() {
        this.chatMessages.innerHTML = `
            <div class="message ai-message">
                <div class="message-avatar">🤖</div>
                <div class="message-content">
                    <div class="message-text">
                        Hello! I'm M1K3, your local AI assistant. I'm running entirely in your browser with no data sent to external servers. How can I help you today?
                    </div>
                    <div class="message-time">${this.formatTime(new Date())}</div>
                </div>
            </div>
        `;
        
        this.conversationHistory = [];
    }

    setProcessingState(isProcessing) {
        this.isProcessing = isProcessing;
        this.updateSendButton();
        
        if (isProcessing) {
            this.userInput.disabled = true;
        } else {
            this.userInput.disabled = false;
            this.userInput.focus();
        }
    }

    addStreamingMessage(content, sender) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender}-message streaming-message`;
        
        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = sender === 'user' ? '👤' : '🤖';
        
        const messageContent = document.createElement('div');
        messageContent.className = 'message-content';
        
        const messageText = document.createElement('div');
        messageText.className = 'message-text';
        messageText.textContent = content;
        
        const messageTime = document.createElement('div');
        messageTime.className = 'message-time';
        messageTime.textContent = this.formatTime(new Date());
        
        // Progress indicator for streaming
        const progressBar = document.createElement('div');
        progressBar.className = 'streaming-progress';
        progressBar.innerHTML = '<div class="progress-fill"></div>';
        
        messageContent.appendChild(messageText);
        messageContent.appendChild(progressBar);
        messageContent.appendChild(messageTime);
        messageDiv.appendChild(avatar);
        messageDiv.appendChild(messageContent);
        
        // Add with animation
        messageDiv.style.opacity = '0';
        messageDiv.style.transform = 'translateY(20px)';
        this.chatMessages.appendChild(messageDiv);
        
        // Animate in
        requestAnimationFrame(() => {
            messageDiv.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
            messageDiv.style.opacity = '1';
            messageDiv.style.transform = 'translateY(0)';
        });
        
        this.scrollToBottom();
        return messageDiv;
    }

    updateStreamingMessage(messageElement, tokenData) {
        const messageText = messageElement.querySelector('.message-text');
        const progressBar = messageElement.querySelector('.progress-fill');
        
        if (tokenData.isComplete) {
            // Final update
            messageText.textContent = tokenData.accumulatedText;
            messageElement.classList.remove('streaming-message');
            
            // Remove progress bar
            const progressContainer = messageElement.querySelector('.streaming-progress');
            if (progressContainer) {
                progressContainer.remove();
            }
        } else {
            // Streaming update
            messageText.textContent = tokenData.accumulatedText;
            
            // Add typing cursor effect
            if (!messageText.textContent.endsWith('▋')) {
                messageText.textContent += '▋';
            }
        }
        
        this.scrollToBottom();
    }

    updateProgress(progressData) {
        const progressBar = document.querySelector('.streaming-message .progress-fill');
        if (progressBar && progressData.percentage) {
            progressBar.style.width = `${progressData.percentage}%`;
        }
    }
}

// Export for use in other modules
window.ChatInterface = ChatInterface;