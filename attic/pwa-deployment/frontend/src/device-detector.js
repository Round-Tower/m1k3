/**
 * M1K3 Device Capability Detection
 * Analyzes device capabilities and selects optimal AI model
 */

class DeviceDetector {
    constructor() {
        this.capabilities = null;
        this.selectedModel = null;
        this.modelTiers = {
            high: { minMemory: 8, preferredModel: 'medium', fallback: 'small' },
            mid: { minMemory: 6, preferredModel: 'small', fallback: 'tiny' },
            low: { minMemory: 4, preferredModel: 'tiny', fallback: 'tiny' }
        };
    }

    async detectCapabilities() {
        console.log('🔍 Detecting device capabilities...');
        
        const capabilities = {
            // Memory detection
            memory: this.getMemoryInfo(),
            
            // CPU detection
            cores: navigator.hardwareConcurrency || 2,
            
            // WebGPU support
            webgpu: await this.checkWebGPU(),
            
            // WebNN support (Neural Network API)
            webnn: this.checkWebNN(),
            
            // WebAssembly support
            wasm: this.checkWebAssembly(),
            
            // Connection info
            connection: this.getConnectionInfo(),
            
            // Platform info
            platform: this.getPlatformInfo(),
            
            // Performance estimate
            performance: await this.estimatePerformance()
        };

        // Determine device tier
        capabilities.tier = this.determineTier(capabilities);
        
        this.capabilities = capabilities;
        console.log('📊 Device capabilities:', capabilities);
        
        return capabilities;
    }

    getMemoryInfo() {
        // Try to get actual memory info
        if ('deviceMemory' in navigator) {
            return navigator.deviceMemory;
        }
        
        // Estimate based on user agent and other factors
        const ua = navigator.userAgent;
        
        // Mobile devices typically have less memory
        if (/iPhone|iPad|iPod|Android/i.test(ua)) {
            if (/iPhone\s*1[5-9]|iPad\s*Air\s*[5-9]|iPad\s*Pro/i.test(ua)) {
                return 8; // Recent high-end mobile devices
            } else if (/iPhone\s*1[2-4]|iPad\s*Air\s*[2-4]/i.test(ua)) {
                return 4; // Mid-range mobile devices
            } else {
                return 2; // Older or budget mobile devices
            }
        }
        
        // Desktop/laptop estimation
        if (/Windows NT 10|macOS|Mac OS X/i.test(ua)) {
            return 8; // Conservative estimate for modern desktops
        }
        
        // Default conservative estimate
        return 4;
    }

    async checkWebGPU() {
        if (!('gpu' in navigator)) {
            return { supported: false, reason: 'WebGPU not available' };
        }

        try {
            const adapter = await navigator.gpu.requestAdapter({
                powerPreference: 'high-performance'
            });
            
            if (!adapter) {
                return { supported: false, reason: 'No WebGPU adapter available' };
            }

            const device = await adapter.requestDevice();
            
            return {
                supported: true,
                adapter: {
                    vendor: adapter.info?.vendor || 'unknown',
                    architecture: adapter.info?.architecture || 'unknown'
                }
            };
        } catch (error) {
            return { supported: false, reason: error.message };
        }
    }

    checkWebNN() {
        if ('ml' in navigator) {
            return { supported: true, version: 'experimental' };
        }
        return { supported: false, reason: 'WebNN not available' };
    }

    checkWebAssembly() {
        try {
            if (typeof WebAssembly === 'object' && typeof WebAssembly.instantiate === 'function') {
                // Test SIMD support
                const simdSupported = (() => {
                    try {
                        return typeof WebAssembly.SIMD === 'object';
                    } catch {
                        return false;
                    }
                })();

                // Test threads support
                const threadsSupported = (() => {
                    try {
                        return typeof SharedArrayBuffer !== 'undefined';
                    } catch {
                        return false;
                    }
                })();

                return {
                    supported: true,
                    simd: simdSupported,
                    threads: threadsSupported
                };
            }
        } catch {
            // WebAssembly not supported
        }
        
        return { supported: false, reason: 'WebAssembly not available' };
    }

    getConnectionInfo() {
        if ('connection' in navigator) {
            const conn = navigator.connection;
            return {
                effectiveType: conn.effectiveType || 'unknown',
                downlink: conn.downlink || 0,
                rtt: conn.rtt || 0,
                saveData: conn.saveData || false
            };
        }
        
        return { effectiveType: 'unknown' };
    }

    getPlatformInfo() {
        const ua = navigator.userAgent;
        
        return {
            mobile: /iPhone|iPad|iPod|Android|BlackBerry|IEMobile|Opera Mini/i.test(ua),
            ios: /iPhone|iPad|iPod/i.test(ua),
            android: /Android/i.test(ua),
            desktop: !/iPhone|iPad|iPod|Android|BlackBerry|IEMobile|Opera Mini/i.test(ua),
            userAgent: ua
        };
    }

    async estimatePerformance() {
        // Simple performance benchmark
        const start = performance.now();
        
        // CPU-intensive task
        let result = 0;
        for (let i = 0; i < 100000; i++) {
            result += Math.sqrt(i) * Math.sin(i);
        }
        
        const cpuTime = performance.now() - start;
        
        // Memory allocation test
        const memStart = performance.now();
        try {
            const largeArray = new Array(1000000).fill(0).map((_, i) => i);
            const memTime = performance.now() - memStart;
            
            return {
                cpu: cpuTime,
                memory: memTime,
                score: this.calculatePerformanceScore(cpuTime, memTime)
            };
        } catch (error) {
            return {
                cpu: cpuTime,
                memory: Infinity,
                score: 'low',
                error: error.message
            };
        }
    }

    calculatePerformanceScore(cpuTime, memTime) {
        // Simple scoring based on benchmark times
        if (cpuTime < 10 && memTime < 5) return 'high';
        if (cpuTime < 25 && memTime < 15) return 'medium';
        return 'low';
    }

    determineTier(capabilities) {
        const { memory, webgpu, performance, platform } = capabilities;
        
        // Mobile devices are generally lower tier
        if (platform.mobile) {
            if (memory >= 8 && webgpu.supported && performance.score === 'high') {
                return 'mid'; // High-end mobile
            } else {
                return 'low'; // Standard mobile
            }
        }
        
        // Desktop/laptop tiering
        if (memory >= 8 && webgpu.supported && performance.score === 'high') {
            return 'high';
        } else if (memory >= 6 && performance.score !== 'low') {
            return 'mid';
        } else {
            return 'low';
        }
    }

    selectOptimalModel(capabilities = this.capabilities) {
        if (!capabilities) {
            throw new Error('Device capabilities not detected yet');
        }

        const tierConfig = this.modelTiers[capabilities.tier];
        let selectedModel = tierConfig.preferredModel;

        // Additional checks for model viability
        if (!capabilities.wasm.supported) {
            console.warn('⚠️ WebAssembly not supported, using fallback');
            selectedModel = 'tiny'; // Smallest possible model
        }

        if (capabilities.connection.saveData) {
            console.log('📱 Data saver mode detected, using smaller model');
            selectedModel = tierConfig.fallback;
        }

        this.selectedModel = {
            name: selectedModel,
            tier: capabilities.tier,
            reasoning: this.getSelectionReasoning(capabilities, selectedModel)
        };

        console.log('🎯 Selected model:', this.selectedModel);
        return this.selectedModel;
    }

    getSelectionReasoning(capabilities, selectedModel) {
        const reasons = [];
        
        reasons.push(`Device tier: ${capabilities.tier}`);
        reasons.push(`Memory: ${capabilities.memory}GB`);
        
        if (capabilities.webgpu.supported) {
            reasons.push('WebGPU acceleration available');
        } else {
            reasons.push('CPU-only inference');
        }
        
        if (capabilities.platform.mobile) {
            reasons.push('Mobile device optimization');
        }
        
        if (capabilities.connection.saveData) {
            reasons.push('Data saver mode active');
        }
        
        return reasons;
    }

    displayCapabilities(containerId = 'device-specs') {
        if (!this.capabilities) return;

        const container = document.getElementById(containerId);
        if (!container) return;

        const { capabilities } = this;
        
        container.innerHTML = `
            <div class="spec-item">
                <span class="spec-label">Memory:</span>
                <span class="spec-value">${capabilities.memory}GB</span>
            </div>
            <div class="spec-item">
                <span class="spec-label">CPU Cores:</span>
                <span class="spec-value">${capabilities.cores}</span>
            </div>
            <div class="spec-item">
                <span class="spec-label">WebGPU:</span>
                <span class="spec-value ${capabilities.webgpu.supported ? 'supported' : 'not-supported'}">
                    ${capabilities.webgpu.supported ? '✅ Available' : '❌ Not Available'}
                </span>
            </div>
            <div class="spec-item">
                <span class="spec-label">Platform:</span>
                <span class="spec-value">${capabilities.platform.mobile ? '📱 Mobile' : '🖥️ Desktop'}</span>
            </div>
            <div class="spec-item">
                <span class="spec-label">Performance:</span>
                <span class="spec-value performance-${capabilities.performance.score}">
                    ${capabilities.performance.score.toUpperCase()}
                </span>
            </div>
        `;
    }

    displaySelectedModel(containerId = 'selected-model') {
        if (!this.selectedModel) return;

        const container = document.getElementById(containerId);
        if (!container) return;

        container.innerHTML = `
            <div class="selected-model-info">
                <div class="model-name">Selected: M1K3-${this.selectedModel.name.toUpperCase()}</div>
                <div class="model-reasoning">
                    ${this.selectedModel.reasoning.join(' • ')}
                </div>
            </div>
        `;
    }
}

// Export for use in other modules
window.DeviceDetector = DeviceDetector;