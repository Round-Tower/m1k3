/**
 * M1K3 User Flow Test Suite
 * Comprehensive testing of all major user workflows and interactions
 */

class UserFlowTestSuite {
    constructor() {
        this.testResults = [];
        this.passed = 0;
        this.failed = 0;
        this.startTime = Date.now();
        this.app = null;
        this.mockWebSocket = null;
    }
    
    /**
     * Run all user flow tests
     */
    async runAllTests() {
        console.log('🧪 Starting User Flow Test Suite...');
        
        // Setup test environment
        await this.setupTestEnvironment();
        
        // Test chat functionality
        await this.testChatMessageFlow();
        await this.testStreamingChatFlow();
        await this.testChatInputValidation();
        
        // Test WebSocket communication
        await this.testWebSocketConnection();
        await this.testWebSocketReconnection();
        await this.testWebSocketMessageQueue();
        
        // Test avatar functionality
        await this.testAvatarEmotionUpdates();
        await this.testAvatarStateTransitions();
        
        // Test system monitoring
        await this.testSystemMetricsUpdates();
        await this.testPerformanceMonitoring();
        
        // Test UI interactions
        await this.testTabNavigation();
        await this.testResponsiveLayout();
        
        // Test error handling
        await this.testErrorRecovery();
        await this.testCircuitBreakerBehavior();
        
        // Generate comprehensive report
        this.generateTestReport();
        
        return this.testResults;
    }
    
    /**
     * Setup test environment with mock objects
     */
    async setupTestEnvironment() {
        this.startTest('Test Environment Setup');
        
        try {
            // Create mock WebSocket
            this.mockWebSocket = new MockWebSocket();
            
            // Get or create app instance
            this.app = window.m1k3App;
            if (!this.app) {
                throw new Error('M1K3App not found - please ensure the app is loaded');
            }
            
            // Inject mock WebSocket
            this.originalWebSocket = window.WebSocket;
            window.WebSocket = MockWebSocket;
            
            this.passTest('Test environment setup complete');
            
        } catch (error) {
            this.failTest('Failed to setup test environment', error);
        }
    }
    
    /**
     * Test chat message sending and receiving
     */
    async testChatMessageFlow() {
        this.startTest('Chat Message Flow');
        
        try {
            const chatController = this.app.getController('chat');
            this.assert(chatController, 'Chat controller should be available');
            
            // Test sending a message
            const testMessage = 'Hello M1K3, this is a test message';
            const chatInput = document.getElementById('chatInput');
            
            if (chatInput) {
                chatInput.value = testMessage;
                chatController.sendMessage();
                
                // Verify message was added to state
                const messages = this.app.stateManager.getState('chat.messages');
                const lastMessage = messages[messages.length - 1];
                
                this.assert(lastMessage.message === testMessage, 'Message should be added to state');
                this.assert(lastMessage.sender === 'user', 'Message sender should be user');
                
                // Clear input
                this.assert(chatInput.value === '', 'Input should be cleared after sending');
            }
            
            // Test receiving AI message
            this.mockWebSocket.simulateMessage({
                type: 'chat_ai',
                message: 'Hello! This is a test AI response.'
            });
            
            // Wait for message processing
            await this.delay(100);
            
            const updatedMessages = this.app.stateManager.getState('chat.messages');
            const aiMessage = updatedMessages.find(msg => msg.sender === 'ai');
            
            this.assert(aiMessage, 'AI message should be added to state');
            this.assert(aiMessage.message === 'Hello! This is a test AI response.', 'AI message content should match');
            
            this.passTest('Chat message flow working correctly');
            
        } catch (error) {
            this.failTest('Chat message flow test failed', error);
        }
    }
    
    /**
     * Test streaming chat functionality
     */
    async testStreamingChatFlow() {
        this.startTest('Streaming Chat Flow');
        
        try {
            const chatController = this.app.getController('chat');
            
            // Simulate streaming message chunks
            const chunks = ['Hello', ' there!', ' This', ' is', ' streaming.'];
            
            for (const chunk of chunks) {
                this.mockWebSocket.simulateMessage({
                    type: 'chat_ai',
                    chunk: chunk
                });
                await this.delay(50); // Simulate streaming delay
            }
            
            // Finalize streaming
            this.mockWebSocket.simulateMessage({
                type: 'chat_ai',
                complete: true
            });
            
            await this.delay(100);
            
            // Verify streaming message was properly handled
            const streamingElement = document.getElementById('streaming-message');
            this.assert(!streamingElement, 'Streaming element should be removed after completion');
            
            const messages = this.app.stateManager.getState('chat.messages');
            const streamedMessage = messages.find(msg => 
                msg.sender === 'ai' && 
                msg.message && 
                msg.message.includes('Hello there! This is streaming.')
            );
            
            this.assert(streamedMessage, 'Streamed message should be in final messages');
            
            this.passTest('Streaming chat flow working correctly');
            
        } catch (error) {
            this.failTest('Streaming chat flow test failed', error);
        }
    }
    
    /**
     * Test chat input validation
     */
    async testChatInputValidation() {
        this.startTest('Chat Input Validation');
        
        try {
            const chatController = this.app.getController('chat');
            const chatInput = document.getElementById('chatInput');
            
            if (chatInput) {
                // Test empty message
                const initialMessageCount = this.app.stateManager.getState('chat.messages').length;
                chatInput.value = '';
                chatController.sendMessage();
                
                const afterEmptyMessageCount = this.app.stateManager.getState('chat.messages').length;
                this.assert(afterEmptyMessageCount === initialMessageCount, 'Empty messages should not be sent');
                
                // Test whitespace-only message
                chatInput.value = '   \\n\\t  ';
                chatController.sendMessage();
                
                const afterWhitespaceCount = this.app.stateManager.getState('chat.messages').length;
                this.assert(afterWhitespaceCount === initialMessageCount, 'Whitespace-only messages should not be sent');
                
                // Test valid message
                chatInput.value = 'Valid message';
                chatController.sendMessage();
                
                const afterValidMessageCount = this.app.stateManager.getState('chat.messages').length;
                this.assert(afterValidMessageCount === initialMessageCount + 1, 'Valid messages should be sent');
            }
            
            this.passTest('Chat input validation working correctly');
            
        } catch (error) {
            this.failTest('Chat input validation test failed', error);
        }
    }
    
    /**
     * Test WebSocket connection handling
     */
    async testWebSocketConnection() {
        this.startTest('WebSocket Connection');
        
        try {
            const wsManager = this.app.getController('websocket');
            this.assert(wsManager, 'WebSocket manager should be available');
            
            // Test connection state
            const connectionState = this.app.stateManager.getState('connection.websocket');
            this.assert(['connected', 'connecting', 'disconnected'].includes(connectionState), 
                       'Connection state should be valid');
            
            // Test send functionality
            const testData = { type: 'test', message: 'Test message' };
            const sendResult = wsManager.send(testData);
            
            if (wsManager.isConnected) {
                this.assert(sendResult !== false, 'Message should be sent when connected');
            } else {
                // Should queue message when not connected
                this.assert(wsManager.messageQueue.length > 0, 'Message should be queued when not connected');
            }
            
            this.passTest('WebSocket connection handling working correctly');
            
        } catch (error) {
            this.failTest('WebSocket connection test failed', error);
        }
    }
    
    /**
     * Test WebSocket reconnection logic
     */
    async testWebSocketReconnection() {
        this.startTest('WebSocket Reconnection');
        
        try {
            const wsManager = this.app.getController('websocket');
            
            // Test adaptive reconnection
            wsManager.statistics.connectionQuality = 'poor';
            wsManager.circuitBreaker.failures = 2;
            
            const adaptiveDelay = wsManager.calculateAdaptiveDelay();
            this.assert(adaptiveDelay > 1000, 'Adaptive delay should be higher for poor connections');
            
            // Test circuit breaker integration
            wsManager.circuitBreaker.state = 'open';
            const shouldReconnect = wsManager.checkCircuitBreaker();
            this.assert(!shouldReconnect, 'Should not reconnect when circuit breaker is open');
            
            // Reset circuit breaker
            wsManager.circuitBreaker.state = 'closed';
            wsManager.circuitBreaker.failures = 0;
            
            this.passTest('WebSocket reconnection logic working correctly');
            
        } catch (error) {
            this.failTest('WebSocket reconnection test failed', error);
        }
    }
    
    /**
     * Test WebSocket message queue functionality
     */
    async testWebSocketMessageQueue() {
        this.startTest('WebSocket Message Queue');
        
        try {
            const wsManager = this.app.getController('websocket');
            
            // Simulate disconnected state
            const originalConnected = wsManager.isConnected;
            wsManager.isConnected = false;
            
            // Send messages while disconnected
            const testMessages = [
                { type: 'test1', data: 'Message 1' },
                { type: 'test2', data: 'Message 2' },
                { type: 'test3', data: 'Message 3' }
            ];
            
            testMessages.forEach(msg => {
                wsManager.send(msg);
            });
            
            this.assert(wsManager.messageQueue.length >= testMessages.length, 
                       'Messages should be queued when disconnected');
            
            // Test queue size limit
            const originalQueueSize = wsManager.messageQueue.length;
            for (let i = 0; i < wsManager.config.maxQueueSize + 10; i++) {
                wsManager.send({ type: 'overflow', data: `Message ${i}` });
            }
            
            this.assert(wsManager.messageQueue.length <= wsManager.config.maxQueueSize,
                       'Queue size should be limited');
            
            // Restore connection state
            wsManager.isConnected = originalConnected;
            
            this.passTest('WebSocket message queue working correctly');
            
        } catch (error) {
            this.failTest('WebSocket message queue test failed', error);
        }
    }
    
    /**
     * Test avatar emotion updates
     */
    async testAvatarEmotionUpdates() {
        this.startTest('Avatar Emotion Updates');
        
        try {
            const avatarController = this.app.getController('avatar');
            this.assert(avatarController, 'Avatar controller should be available');
            
            // Test emotion change
            const testEmotion = 'happy';
            const testIntensity = 75;
            
            this.mockWebSocket.simulateMessage({
                type: 'emotion',
                emotion: testEmotion,
                intensity: testIntensity
            });
            
            await this.delay(100);
            
            const currentEmotion = this.app.stateManager.getState('avatar.emotion');
            const currentIntensity = this.app.stateManager.getState('avatar.intensity');
            
            this.assert(currentEmotion === testEmotion, 'Avatar emotion should update');
            this.assert(currentIntensity === testIntensity, 'Avatar intensity should update');
            
            this.passTest('Avatar emotion updates working correctly');
            
        } catch (error) {
            this.failTest('Avatar emotion update test failed', error);
        }
    }
    
    /**
     * Test avatar state transitions
     */
    async testAvatarStateTransitions() {
        this.startTest('Avatar State Transitions');
        
        try {
            const states = ['idle', 'thinking', 'speaking', 'listening'];
            
            for (const state of states) {
                this.mockWebSocket.simulateMessage({
                    type: 'avatar_state',
                    state: state
                });
                
                await this.delay(50);
                
                const currentState = this.app.stateManager.getState('avatar.state');
                this.assert(currentState === state, `Avatar state should transition to ${state}`);
            }
            
            this.passTest('Avatar state transitions working correctly');
            
        } catch (error) {
            this.failTest('Avatar state transition test failed', error);
        }
    }
    
    /**
     * Test system metrics updates
     */
    async testSystemMetricsUpdates() {
        this.startTest('System Metrics Updates');
        
        try {
            const testMetrics = {
                cpu: 45,
                memory: 60,
                temperature: 35,
                battery: 85,
                network: true,
                networkStrength: 90
            };
            
            this.mockWebSocket.simulateMessage({
                type: 'system_metrics',
                ...testMetrics
            });
            
            await this.delay(100);
            
            const systemState = this.app.stateManager.getState('system');
            
            Object.keys(testMetrics).forEach(key => {
                this.assert(systemState[key] === testMetrics[key], 
                           `System metric ${key} should update correctly`);
            });
            
            this.passTest('System metrics updates working correctly');
            
        } catch (error) {
            this.failTest('System metrics update test failed', error);
        }
    }
    
    /**
     * Test performance monitoring
     */
    async testPerformanceMonitoring() {
        this.startTest('Performance Monitoring');
        
        try {
            const wsManager = this.app.getController('websocket');
            const stateManager = this.app.stateManager;
            
            // Test bandwidth monitoring
            const canSend = wsManager.checkBandwidthLimits(500000); // 500KB
            this.assert(typeof canSend === 'boolean', 'Bandwidth check should return boolean');
            
            // Test performance metrics
            wsManager.updatePerformanceMetrics(150);
            wsManager.updatePerformanceMetrics(200);
            
            const performanceSnapshot = wsManager.getPerformanceSnapshot();
            this.assert(performanceSnapshot.averageLatency > 0, 'Should calculate average latency');
            this.assert(performanceSnapshot.connectionQuality, 'Should determine connection quality');
            
            // Test state manager metrics
            const stateMetrics = stateManager.getMetrics();
            this.assert(typeof stateMetrics.stateUpdates === 'number', 'Should track state updates');
            this.assert(typeof stateMetrics.memoryUsage === 'number', 'Should track memory usage');
            
            this.passTest('Performance monitoring working correctly');
            
        } catch (error) {
            this.failTest('Performance monitoring test failed', error);
        }
    }
    
    /**
     * Test tab navigation
     */
    async testTabNavigation() {
        this.startTest('Tab Navigation');
        
        try {
            const navigationManager = this.app.getController('navigation');
            this.assert(navigationManager, 'Navigation manager should be available');
            
            const tabs = ['dashboard', 'chat', 'avatar', 'settings'];
            
            for (const tab of tabs) {
                const switched = navigationManager.switchTo(tab);
                if (switched) {
                    await this.delay(100);
                    const currentTab = this.app.stateManager.getState('ui.currentTab');
                    this.assert(currentTab === tab, `Should switch to ${tab} tab`);
                }
            }
            
            this.passTest('Tab navigation working correctly');
            
        } catch (error) {
            this.failTest('Tab navigation test failed', error);
        }
    }
    
    /**
     * Test responsive layout
     */
    async testResponsiveLayout() {
        this.startTest('Responsive Layout');
        
        try {
            const dashboard = document.querySelector('.dashboard-container');
            this.assert(dashboard, 'Dashboard container should exist');
            
            // Test CSS grid layout
            const computedStyle = window.getComputedStyle(dashboard);
            this.assert(computedStyle.display.includes('grid'), 'Dashboard should use CSS Grid');
            
            // Test viewport meta tag
            const viewportMeta = document.querySelector('meta[name="viewport"]');
            this.assert(viewportMeta, 'Viewport meta tag should exist for responsive design');
            
            this.passTest('Responsive layout configured correctly');
            
        } catch (error) {
            this.failTest('Responsive layout test failed', error);
        }
    }
    
    /**
     * Test error recovery
     */
    async testErrorRecovery() {
        this.startTest('Error Recovery');
        
        try {
            const wsManager = this.app.getController('websocket');
            
            // Test circuit breaker error recovery
            wsManager.recordCircuitBreakerFailure();
            wsManager.recordCircuitBreakerFailure();
            wsManager.recordCircuitBreakerFailure();
            
            this.assert(wsManager.circuitBreaker.state === 'open', 'Circuit breaker should open after failures');
            
            // Test recovery
            wsManager.circuitBreaker.state = 'half-open';
            wsManager.recordCircuitBreakerSuccess();
            
            this.assert(wsManager.circuitBreaker.state === 'closed', 'Circuit breaker should close on success');
            
            this.passTest('Error recovery working correctly');
            
        } catch (error) {
            this.failTest('Error recovery test failed', error);
        }
    }
    
    /**
     * Test circuit breaker behavior
     */
    async testCircuitBreakerBehavior() {
        this.startTest('Circuit Breaker Behavior');
        
        try {
            const wsManager = this.app.getController('websocket');
            
            // Reset circuit breaker
            wsManager.circuitBreaker.state = 'closed';
            wsManager.circuitBreaker.failures = 0;
            
            // Test failure accumulation
            for (let i = 0; i < wsManager.config.circuitBreakerThreshold; i++) {
                wsManager.recordCircuitBreakerFailure();
            }
            
            this.assert(wsManager.circuitBreaker.state === 'open', 
                       'Circuit breaker should open after threshold failures');
            
            // Test blocked operations
            const canOperate = wsManager.checkCircuitBreaker();
            this.assert(!canOperate, 'Operations should be blocked when circuit breaker is open');
            
            this.passTest('Circuit breaker behavior working correctly');
            
        } catch (error) {
            this.failTest('Circuit breaker behavior test failed', error);
        }
    }
    
    /**
     * Helper methods
     */
    startTest(testName) {
        this.currentTest = testName;
        console.log(`🧪 Running: ${testName}`);
    }
    
    assert(condition, message) {
        if (!condition) {
            throw new Error(`Assertion failed: ${message}`);
        }
    }
    
    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
    
    passTest(message) {
        this.passed++;
        this.testResults.push({
            test: this.currentTest,
            status: 'PASS',
            message,
            timestamp: Date.now()
        });
        console.log(`✅ PASS: ${this.currentTest} - ${message}`);
    }
    
    failTest(message, error) {
        this.failed++;
        this.testResults.push({
            test: this.currentTest,
            status: 'FAIL',
            message,
            error: error?.message || error,
            timestamp: Date.now()
        });
        console.error(`❌ FAIL: ${this.currentTest} - ${message}`, error);
    }
    
    /**
     * Generate comprehensive test report
     */
    generateTestReport() {
        const duration = Date.now() - this.startTime;
        const total = this.passed + this.failed;
        const successRate = total > 0 ? ((this.passed / total) * 100).toFixed(1) : '0.0';
        
        console.log('\\n🏁 User Flow Test Suite Complete');
        console.log('==========================================');
        console.log(`Total Tests: ${total}`);
        console.log(`Passed: ${this.passed}`);
        console.log(`Failed: ${this.failed}`);
        console.log(`Success Rate: ${successRate}%`);
        console.log(`Duration: ${duration}ms`);
        console.log('==========================================\\n');
        
        // Store results globally
        window.userFlowTestResults = {
            summary: {
                total,
                passed: this.passed,
                failed: this.failed,
                successRate: parseFloat(successRate),
                duration
            },
            results: this.testResults
        };
        
        // Cleanup
        this.cleanup();
        
        return this.testResults;
    }
    
    /**
     * Cleanup test environment
     */
    cleanup() {
        // Restore original WebSocket
        if (this.originalWebSocket) {
            window.WebSocket = this.originalWebSocket;
        }
        
        // Clear any test data
        if (this.app && this.app.stateManager) {
            // Reset streaming state
            const chatController = this.app.getController('chat');
            if (chatController && chatController.currentStreamingMessage) {
                chatController.finalizeStreamingMessage();
            }
        }
        
        console.log('🧹 Test environment cleaned up');
    }
}

/**
 * Mock WebSocket for testing
 */
class MockWebSocket extends EventTarget {
    constructor(url) {
        super();
        this.url = url;
        this.readyState = WebSocket.CONNECTING;
        this.sentMessages = [];
        
        // Simulate connection after delay
        setTimeout(() => {
            this.readyState = WebSocket.OPEN;
            this.dispatchEvent(new Event('open'));
        }, 100);
    }
    
    send(data) {
        if (this.readyState === WebSocket.OPEN) {
            this.sentMessages.push(data);
            return true;
        }
        return false;
    }
    
    close() {
        this.readyState = WebSocket.CLOSED;
        this.dispatchEvent(new Event('close'));
    }
    
    simulateMessage(data) {
        if (this.readyState === WebSocket.OPEN) {
            const event = new MessageEvent('message', {
                data: JSON.stringify(data)
            });
            this.dispatchEvent(event);
        }
    }
}

// Export for global use
if (typeof window !== 'undefined') {
    window.UserFlowTestSuite = UserFlowTestSuite;
    window.MockWebSocket = MockWebSocket;
    
    // Add global test runner
    window.runUserFlowTests = async function() {
        const testSuite = new UserFlowTestSuite();
        return await testSuite.runAllTests();
    };
    
    console.log('🧪 User Flow Test Suite loaded. Run tests with: runUserFlowTests()');
}

// Export for Node.js if applicable
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { UserFlowTestSuite, MockWebSocket };
}