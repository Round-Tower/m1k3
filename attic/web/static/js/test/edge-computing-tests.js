/**
 * M1K3 Edge Computing Tests
 * Comprehensive testing for refactored real-time web interface components
 */

class EdgeComputingTestSuite {
    constructor() {
        this.testResults = [];
        this.passed = 0;
        this.failed = 0;
        this.startTime = Date.now();
    }
    
    /**
     * Run all tests
     */
    async runAllTests() {
        console.log('🧪 Starting Edge Computing Test Suite...');
        
        // Test StateManager optimizations
        await this.testStateManagerMemoryOptimizations();
        await this.testStateManagerPerformanceMonitoring();
        
        // Test WebSocketManager enhancements
        await this.testWebSocketPerformanceMonitoring();
        await this.testWebSocketCircuitBreaker();
        await this.testWebSocketAdaptiveReconnection();
        await this.testWebSocketResourceLimits();
        await this.testWebSocketDataStructures();
        
        // Generate test report
        this.generateTestReport();
    }
    
    /**
     * Test StateManager memory optimizations
     */
    async testStateManagerMemoryOptimizations() {
        this.startTest('StateManager Memory Optimizations');
        
        try {
            // Create StateManager instance
            const stateManager = new StateManager({
                maxHistorySize: 10,
                performanceMonitoring: true,
                cleanupInterval: 1000
            });
            
            // Test circular buffer history
            for (let i = 0; i < 15; i++) {
                stateManager.updateState('test.value', i);
            }
            
            this.assert(stateManager.historySize <= 10, 'History size should be limited');
            this.assert(stateManager.stateHistory.length === 10, 'History buffer should be fixed size');
            
            // Test performance metrics
            const metrics = stateManager.getMetrics();
            this.assert(metrics.stateUpdates === 15, 'Should track state updates');
            this.assert(typeof metrics.avgUpdateTime === 'number', 'Should track average update time');
            
            // Test memory cleanup
            stateManager.forceCleanup();
            this.assert(stateManager.historySize <= 5, 'Cleanup should reduce history size');
            
            this.passTest('StateManager memory optimizations working correctly');
            
        } catch (error) {
            this.failTest('StateManager memory optimization test failed', error);
        }
    }
    
    /**
     * Test StateManager performance monitoring
     */
    async testStateManagerPerformanceMonitoring() {
        this.startTest('StateManager Performance Monitoring');
        
        try {
            const stateManager = new StateManager({
                performanceMonitoring: true,
                memoryThreshold: 1000
            });
            
            // Test performance metric collection
            for (let i = 0; i < 50; i++) {
                stateManager.updateState('performance.test', Math.random());
            }
            
            const metrics = stateManager.getMetrics();
            this.assert(metrics.stateUpdates === 50, 'Should track all updates');
            this.assert(metrics.memoryUsage !== undefined, 'Should track memory usage');
            
            this.passTest('StateManager performance monitoring working correctly');
            
        } catch (error) {
            this.failTest('StateManager performance monitoring test failed', error);
        }
    }
    
    /**
     * Test WebSocket performance monitoring
     */
    async testWebSocketPerformanceMonitoring() {
        this.startTest('WebSocket Performance Monitoring');
        
        try {
            const mockStateManager = { updateConnectionState: () => {}, emit: () => {} };
            const wsManager = new WebSocketManager(mockStateManager, {
                performanceMonitoring: true,
                maxBandwidth: 1000000
            });
            
            // Test bandwidth monitoring
            const canSend1 = wsManager.checkBandwidthLimits(500000); // 500KB
            this.assert(canSend1, 'Should allow message within bandwidth limit');
            
            const canSend2 = wsManager.checkBandwidthLimits(600000); // 600KB (exceeds 1MB total)
            this.assert(!canSend2, 'Should block message exceeding bandwidth limit');
            
            // Test performance metrics
            wsManager.updatePerformanceMetrics(150); // 150ms latency
            wsManager.updatePerformanceMetrics(200);
            wsManager.updatePerformanceMetrics(100);
            
            const snapshot = wsManager.getPerformanceSnapshot();
            this.assert(snapshot.averageLatency > 0, 'Should calculate average latency');
            this.assert(snapshot.peakLatency === 200, 'Should track peak latency');
            this.assert(snapshot.connectionQuality !== 'unknown', 'Should determine connection quality');
            
            this.passTest('WebSocket performance monitoring working correctly');
            
        } catch (error) {
            this.failTest('WebSocket performance monitoring test failed', error);
        }
    }
    
    /**
     * Test WebSocket circuit breaker
     */
    async testWebSocketCircuitBreaker() {
        this.startTest('WebSocket Circuit Breaker');
        
        try {
            const mockStateManager = { updateConnectionState: () => {}, emit: () => {} };
            const wsManager = new WebSocketManager(mockStateManager, {
                circuitBreakerThreshold: 3
            });
            
            // Test initial state
            this.assert(wsManager.circuitBreaker.state === 'closed', 'Circuit breaker should start closed');
            this.assert(wsManager.checkCircuitBreaker(), 'Should allow operations when closed');
            
            // Test failure recording
            wsManager.recordCircuitBreakerFailure();
            wsManager.recordCircuitBreakerFailure();
            this.assert(wsManager.circuitBreaker.state === 'closed', 'Should remain closed under threshold');
            
            wsManager.recordCircuitBreakerFailure();
            this.assert(wsManager.circuitBreaker.state === 'open', 'Should open after threshold failures');
            this.assert(!wsManager.checkCircuitBreaker(), 'Should block operations when open');
            
            // Test success recording
            wsManager.circuitBreaker.state = 'half-open';
            wsManager.recordCircuitBreakerSuccess();
            this.assert(wsManager.circuitBreaker.state === 'closed', 'Should close on success in half-open state');
            
            this.passTest('WebSocket circuit breaker working correctly');
            
        } catch (error) {
            this.failTest('WebSocket circuit breaker test failed', error);
        }
    }
    
    /**
     * Test WebSocket adaptive reconnection
     */
    async testWebSocketAdaptiveReconnection() {
        this.startTest('WebSocket Adaptive Reconnection');
        
        try {
            const mockStateManager = { updateConnectionState: () => {}, updatePerformanceMetrics: () => {} };
            const wsManager = new WebSocketManager(mockStateManager, {
                adaptiveReconnect: true,
                reconnectInterval: 1000,
                reconnectDecay: 1.5
            });
            
            // Set up test conditions
            wsManager.statistics.connectionQuality = 'poor';
            wsManager.circuitBreaker.failures = 1;
            
            const adaptiveDelay = wsManager.calculateAdaptiveDelay();
            const baseDelay = 1000 * Math.pow(1.5, 0); // First attempt
            
            this.assert(adaptiveDelay > baseDelay, 'Adaptive delay should be higher for poor connections');
            
            // Test with excellent connection
            wsManager.statistics.connectionQuality = 'excellent';
            wsManager.circuitBreaker.failures = 0;
            const excellentDelay = wsManager.calculateAdaptiveDelay();
            
            this.assert(excellentDelay < baseDelay, 'Adaptive delay should be lower for excellent connections');
            
            this.passTest('WebSocket adaptive reconnection working correctly');
            
        } catch (error) {
            this.failTest('WebSocket adaptive reconnection test failed', error);
        }
    }
    
    /**
     * Test WebSocket resource limits
     */
    async testWebSocketResourceLimits() {
        this.startTest('WebSocket Resource Limits');
        
        try {
            const mockStateManager = { updateConnectionState: () => {}, emit: () => {} };
            const wsManager = new WebSocketManager(mockStateManager, {
                maxMessageSize: 1000,
                maxQueueSize: 5
            });
            
            // Test message size limits
            const largeMessage = { data: 'x'.repeat(2000) };
            const sendResult = wsManager.send(largeMessage);
            this.assert(!sendResult, 'Should reject messages exceeding size limit');
            
            // Test queue size limits
            wsManager.isConnected = false;
            for (let i = 0; i < 10; i++) {
                wsManager.send({ data: `message${i}` });
            }
            
            this.assert(wsManager.messageQueue.length <= 5, 'Should limit queue size');
            this.assert(wsManager.statistics.queueDropped > 0, 'Should track dropped messages');
            
            this.passTest('WebSocket resource limits working correctly');
            
        } catch (error) {
            this.failTest('WebSocket resource limits test failed', error);
        }
    }
    
    /**
     * Test WebSocket data structures
     */
    async testWebSocketDataStructures() {
        this.startTest('WebSocket Data Structures');
        
        try {
            const mockStateManager = { updateConnectionState: () => {}, emit: () => {} };
            const wsManager = new WebSocketManager(mockStateManager);
            
            // Test message compression
            const testData = {
                type: 'chat',
                message: 'Hello',
                timestamp: Date.now(),
                user: 'test',
                status: 'connected'
            };
            
            const compressed = wsManager.compressMessage(testData);
            this.assert(compressed.originalSize > 0, 'Should have original size');
            
            if (compressed.compressed) {
                this.assert(compressed.compressedSize < compressed.originalSize, 'Compressed should be smaller');
                
                const decompressed = wsManager.decompressMessage(compressed);
                const parsedDecompressed = JSON.parse(decompressed);
                this.assert(parsedDecompressed.type === 'chat', 'Should decompress correctly');
            }
            
            // Test message caching
            wsManager.cacheMessage('test1', { data: 'cached' });
            const cached = wsManager.getCachedMessage('test1');
            this.assert(cached.data === 'cached', 'Should retrieve cached messages');
            
            // Test metrics aggregator
            wsManager.addMetric(100);
            wsManager.addMetric(200);
            wsManager.addMetric(150);
            
            const stats = wsManager.getAggregatedStats();
            this.assert(stats.avg === 150, 'Should calculate correct average');
            this.assert(stats.min === 100, 'Should track minimum');
            this.assert(stats.max === 200, 'Should track maximum');
            
            // Test memory stats
            const memoryStats = wsManager.getMemoryStats();
            this.assert(memoryStats.totalEstimatedMemory > 0, 'Should estimate memory usage');
            
            this.passTest('WebSocket data structures working correctly');
            
        } catch (error) {
            this.failTest('WebSocket data structures test failed', error);
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
        
        console.log('\n🏁 Edge Computing Test Suite Complete');
        console.log('============================================');
        console.log(`Total Tests: ${total}`);
        console.log(`Passed: ${this.passed}`);
        console.log(`Failed: ${this.failed}`);
        console.log(`Success Rate: ${successRate}%`);
        console.log(`Duration: ${duration}ms`);
        console.log('============================================\n');
        
        // Create HTML report
        const htmlReport = this.generateHTMLReport(total, successRate, duration);
        
        // Store results globally for inspection
        window.edgeComputingTestResults = {
            summary: {
                total,
                passed: this.passed,
                failed: this.failed,
                successRate: parseFloat(successRate),
                duration
            },
            results: this.testResults,
            htmlReport
        };
        
        return this.testResults;
    }
    
    /**
     * Generate HTML test report
     */
    generateHTMLReport(total, successRate, duration) {
        const timestamp = new Date().toISOString();
        
        return `
<!DOCTYPE html>
<html>
<head>
    <title>M1K3 Edge Computing Test Report</title>
    <style>
        body { font-family: 'Monaco', 'Menlo', monospace; margin: 20px; background: #1a1a1a; color: #e0e0e0; }
        .header { background: #2d2d2d; padding: 20px; border-radius: 8px; margin-bottom: 20px; }
        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin: 20px 0; }
        .metric { background: #333; padding: 15px; border-radius: 8px; text-align: center; }
        .metric-value { font-size: 2em; font-weight: bold; color: #4CAF50; }
        .test-result { margin: 10px 0; padding: 15px; border-radius: 8px; border-left: 4px solid; }
        .pass { background: #1b5e20; border-color: #4CAF50; }
        .fail { background: #b71c1c; border-color: #f44336; }
        .test-name { font-weight: bold; margin-bottom: 5px; }
        .test-details { font-size: 0.9em; opacity: 0.8; }
    </style>
</head>
<body>
    <div class="header">
        <h1>🧪 M1K3 Edge Computing Test Report</h1>
        <p>Generated: ${timestamp}</p>
        <p>Real-time web interface refactoring validation</p>
    </div>
    
    <div class="summary">
        <div class="metric">
            <div class="metric-value">${total}</div>
            <div>Total Tests</div>
        </div>
        <div class="metric">
            <div class="metric-value" style="color: #4CAF50">${this.passed}</div>
            <div>Passed</div>
        </div>
        <div class="metric">
            <div class="metric-value" style="color: ${this.failed > 0 ? '#f44336' : '#4CAF50'}">${this.failed}</div>
            <div>Failed</div>
        </div>
        <div class="metric">
            <div class="metric-value" style="color: ${parseFloat(successRate) >= 90 ? '#4CAF50' : '#ff9800'}">${successRate}%</div>
            <div>Success Rate</div>
        </div>
        <div class="metric">
            <div class="metric-value">${duration}ms</div>
            <div>Duration</div>
        </div>
    </div>
    
    <h2>Test Results</h2>
    ${this.testResults.map(result => `
        <div class="test-result ${result.status.toLowerCase()}">
            <div class="test-name">${result.status === 'PASS' ? '✅' : '❌'} ${result.test}</div>
            <div class="test-details">${result.message}</div>
            ${result.error ? `<div class="test-details" style="color: #ff5722;">Error: ${result.error}</div>` : ''}
        </div>
    `).join('')}
    
    <div class="header" style="margin-top: 30px;">
        <h3>Edge Computing Optimizations Tested</h3>
        <ul>
            <li>Memory Management with Circular Buffers</li>
            <li>Performance Monitoring and Resource Limits</li>
            <li>Error Resilience with Circuit Breakers</li>
            <li>Adaptive WebSocket Connection Handling</li>
            <li>Efficient Data Structures (Compression, Caching, Aggregation)</li>
        </ul>
        <p><strong>Status:</strong> ${this.failed === 0 ? '🟢 Production Ready' : '🟡 Needs Attention'}</p>
    </div>
</body>
</html>
        `;
    }
}

// Auto-run tests when loaded
if (typeof window !== 'undefined') {
    window.EdgeComputingTestSuite = EdgeComputingTestSuite;
    
    // Add global test runner
    window.runEdgeComputingTests = async function() {
        const testSuite = new EdgeComputingTestSuite();
        return await testSuite.runAllTests();
    };
    
    console.log('🧪 Edge Computing Test Suite loaded. Run tests with: runEdgeComputingTests()');
}

// Export for Node.js if applicable
if (typeof module !== 'undefined' && module.exports) {
    module.exports = EdgeComputingTestSuite;
}