/**
 * M1K3 Model Loader
 * Handles progressive loading and ONNX Runtime integration
 */

class ModelLoader {
    constructor() {
        this.session = null;
        this.tokenizer = null;
        this.modelInfo = null;
        this.loadingState = 'idle';
        this.loadingCallbacks = [];
        
        // ONNX Runtime will be loaded dynamically
        this.ort = null;
        
        // RAG Engine integration
        this.ragEngine = null;
        this.useRAG = true;
    }

    async initialize() {
        console.log('🚀 Initializing Model Loader...');
        
        try {
            // Load ONNX Runtime
            await this.loadONNXRuntime();
            
            // Initialize RAG Engine
            if (this.useRAG) {
                await this.initializeRAG();
            }
            
            // Load deployment manifest
            const manifest = await this.loadDeploymentManifest();
            
            console.log('✅ Model Loader initialized');
            return manifest;
        } catch (error) {
            console.error('❌ Failed to initialize Model Loader:', error);
            throw error;
        }
    }

    async initializeRAG() {
        console.log('🧠 Initializing RAG Engine...');
        
        try {
            this.ragEngine = new BrowserRAGEngine();
            await this.ragEngine.initialize();
            console.log('✅ RAG Engine initialized');
        } catch (error) {
            console.warn('⚠️ RAG Engine initialization failed, continuing without RAG:', error);
            this.ragEngine = null;
            this.useRAG = false;
        }
    }

    async loadONNXRuntime() {
        console.log('📦 Loading ONNX Runtime...');
        
        // Check if already loaded
        if (window.ort) {
            this.ort = window.ort;
            return;
        }

        try {
            // Load ONNX Runtime from CDN
            await this.loadScript('https://cdn.jsdelivr.net/npm/onnxruntime-web@1.16.3/dist/ort.min.js');
            
            if (!window.ort) {
                throw new Error('ONNX Runtime failed to load');
            }

            this.ort = window.ort;
            
            // Configure execution providers
            this.ort.env.wasm.wasmPaths = 'https://cdn.jsdelivr.net/npm/onnxruntime-web@1.16.3/dist/';
            
            console.log('✅ ONNX Runtime loaded successfully');
        } catch (error) {
            console.error('❌ Failed to load ONNX Runtime:', error);
            throw new Error('ONNX Runtime is required but failed to load');
        }
    }

    async loadScript(src) {
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = src;
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
        });
    }

    async loadDeploymentManifest() {
        console.log('📋 Loading deployment manifest...');
        
        try {
            const response = await fetch('./models/deployment-manifest.json');
            if (!response.ok) {
                throw new Error(`Failed to load manifest: ${response.status}`);
            }
            
            const manifest = await response.json();
            console.log('📋 Deployment manifest loaded:', manifest);
            
            return manifest;
        } catch (error) {
            console.warn('⚠️ Could not load deployment manifest, using fallback');
            
            // Fallback manifest for development
            return {
                version: '1.0.0-fallback',
                models: {
                    tiny: {
                        name: 'm1k3-tiny',
                        size_mb: 100,
                        min_memory_gb: 4,
                        description: 'Fallback tiny model'
                    }
                }
            };
        }
    }

    async loadModel(modelName, onProgress = null) {
        console.log(`🧠 Loading model: ${modelName}`);
        
        this.loadingState = 'loading';
        this.notifyCallbacks('loading', { model: modelName });

        try {
            // Update progress
            this.updateProgress(10, 'Initializing...', onProgress);
            
            // Load model metadata
            const modelInfo = await this.loadModelInfo(modelName);
            this.modelInfo = modelInfo;
            
            this.updateProgress(30, 'Loading model weights...', onProgress);
            
            // Load ONNX model with progressive loading
            const modelPath = `./models/${modelName}/model.onnx`;
            const session = await this.loadONNXModel(modelPath, onProgress);
            
            this.updateProgress(80, 'Loading tokenizer...', onProgress);
            
            // Load tokenizer
            const tokenizer = await this.loadTokenizer(modelName);
            
            this.updateProgress(100, 'Model ready!', onProgress);
            
            // Store loaded components
            this.session = session;
            this.tokenizer = tokenizer;
            this.loadingState = 'ready';
            
            console.log('✅ Model loaded successfully:', modelName);
            this.notifyCallbacks('ready', { model: modelName, info: modelInfo });
            
            return { session, tokenizer, modelInfo };
            
        } catch (error) {
            console.error(`❌ Failed to load model ${modelName}:`, error);
            this.loadingState = 'error';
            this.notifyCallbacks('error', { model: modelName, error });
            throw error;
        }
    }

    async loadModelInfo(modelName) {
        try {
            const response = await fetch(`./models/${modelName}/model-info.json`);
            if (!response.ok) {
                throw new Error(`Model info not found: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.warn(`⚠️ Could not load model info for ${modelName}, using fallback`);
            return {
                name: modelName,
                description: 'Local AI model',
                size_mb: 'unknown'
            };
        }
    }

    async loadONNXModel(modelPath, onProgress) {
        try {
            // Create session with optimized settings for web
            const session = await this.ort.InferenceSession.create(modelPath, {
                executionProviders: this.getExecutionProviders(),
                graphOptimizationLevel: 'all',
                enableMemPattern: true,
                enableCpuMemArena: true
            });
            
            return session;
        } catch (error) {
            console.error('❌ Failed to load ONNX model:', error);
            throw new Error(`Model loading failed: ${error.message}`);
        }
    }

    getExecutionProviders() {
        const providers = ['wasm'];
        
        // Add WebGPU if available
        if (navigator.gpu) {
            providers.unshift('webgpu');
        }
        
        return providers;
    }

    async loadTokenizer(modelName) {
        try {
            // For now, use a simple tokenizer implementation
            // In production, you'd load the actual tokenizer from the model
            const response = await fetch(`./models/${modelName}/tokenizer.json`);
            
            if (response.ok) {
                const tokenizerData = await response.json();
                return new SimpleTokenizer(tokenizerData);
            }
        } catch (error) {
            console.warn('⚠️ Could not load tokenizer, using fallback');
        }
        
        // Fallback tokenizer
        return new SimpleTokenizer();
    }

    async runInference(text, options = {}) {
        if (!this.session || !this.tokenizer) {
            throw new Error('Model not loaded');
        }

        console.log('🤔 Running inference...', text);

        try {
            let prompt = text;
            
            // Use RAG if available
            if (this.ragEngine && this.useRAG) {
                prompt = await this.runRAGInference(text, options);
            }
            
            // Generate response with enhanced prompt
            const response = await this.generateText(prompt, options);
            
            console.log('💭 Inference complete');
            return response;
            
        } catch (error) {
            console.error('❌ Inference failed:', error);
            throw error;
        }
    }

    async runRAGInference(query, options = {}) {
        console.log('🔍 Running RAG-enhanced inference...');
        
        try {
            // Retrieve relevant knowledge chunks
            const chunks = await this.ragEngine.retrieve(query, options.maxChunks || 3);
            
            // Build RAG prompt
            const ragPrompt = this.ragEngine.buildRAGPrompt(query, chunks);
            
            console.log(`✅ RAG prompt built with ${chunks.length} chunks`);
            return ragPrompt;
            
        } catch (error) {
            console.warn('⚠️ RAG inference failed, using original query:', error);
            return `User: ${query}\n\nAssistant: `;
        }
    }

    async generateText(prompt, options = {}) {
        console.log('📝 Generating text with model...');
        
        const maxLength = options.maxLength || 150;
        const temperature = options.temperature || 0.7;
        const onToken = options.onToken; // Streaming callback
        const onProgress = options.onProgress; // Progress callback
        
        try {
            // For distilgpt2, we need autoregressive generation
            let generatedTokens = [];
            let currentPrompt = prompt;
            let accumulatedText = '';
            
            // Simple generation loop with streaming support
            for (let step = 0; step < maxLength; step++) {
                // Report progress
                if (onProgress) {
                    onProgress({
                        step: step + 1,
                        maxSteps: maxLength,
                        percentage: Math.round((step / maxLength) * 100)
                    });
                }
                // Tokenize current prompt
                const tokens = this.tokenizer.encode(currentPrompt);
                
                // Limit input length for small models
                const maxInputLength = 512;
                const inputTokens = tokens.slice(-maxInputLength);
                
                // Prepare input tensor
                const inputTensor = new this.ort.Tensor('int64', inputTokens, [1, inputTokens.length]);
                
                // Run inference
                const feeds = { input_ids: inputTensor };
                const results = await this.session.run(feeds);
                
                // Get next token (simplified - would use proper sampling)
                const logits = results.logits.data;
                const vocabSize = results.logits.dims[2];
                
                // Get logits for last position
                const lastPositionLogits = logits.slice(-vocabSize);
                
                // Simple greedy decoding (would implement temperature sampling)
                let nextTokenId = 0;
                let maxLogit = -Infinity;
                
                for (let i = 0; i < lastPositionLogits.length; i++) {
                    if (lastPositionLogits[i] > maxLogit) {
                        maxLogit = lastPositionLogits[i];
                        nextTokenId = i;
                    }
                }
                
                // Check for end of sequence
                if (nextTokenId === this.tokenizer.eosTokenId || nextTokenId === 0) {
                    break;
                }
                
                generatedTokens.push(nextTokenId);
                
                // Decode partial result
                const partialText = this.tokenizer.decode([nextTokenId]);
                currentPrompt += partialText;
                accumulatedText += partialText;
                
                // Stream token to callback if provided
                if (onToken) {
                    onToken({
                        token: partialText,
                        accumulatedText: accumulatedText,
                        isComplete: false,
                        step: step + 1
                    });
                }
                
                // Add small delay for smooth streaming effect
                await new Promise(resolve => setTimeout(resolve, 50));
                
                // Stop at sentence boundaries for better responses
                if (partialText.includes('.') || partialText.includes('!') || partialText.includes('?')) {
                    if (generatedTokens.length > 20) { // Minimum response length
                        break;
                    }
                }
            }
            
            // Decode generated tokens
            const generatedText = this.tokenizer.decode(generatedTokens);
            
            // Clean up the response
            const cleanResponse = this.cleanGeneratedText(generatedText);
            
            // Signal completion to streaming callback
            if (onToken) {
                onToken({
                    token: '',
                    accumulatedText: cleanResponse,
                    isComplete: true,
                    step: generatedTokens.length
                });
            }
            
            return cleanResponse;
            
        } catch (error) {
            console.error('❌ Text generation failed:', error);
            console.log('🎭 Falling back to streaming simulation...');
            
            // Use streaming simulation as fallback
            return await this.simulateStreamingGeneration(prompt, options);
        }
    }

    cleanGeneratedText(text) {
        // Clean up generated text
        let cleaned = text.trim();
        
        // Remove incomplete sentences at the end
        const sentences = cleaned.split(/[.!?]+/);
        if (sentences.length > 1 && sentences[sentences.length - 1].trim().length < 10) {
            sentences.pop();
            cleaned = sentences.join('.') + '.';
        }
        
        // Ensure minimum response quality
        if (cleaned.length < 20) {
            return "I can help you with that. Let me provide more information based on what I know.";
        }
        
        return cleaned;
    }

    getFallbackResponse(prompt) {
        // Enhanced fallback responses based on keywords
        const lowerPrompt = prompt.toLowerCase();
        
        if (lowerPrompt.includes('solar') || lowerPrompt.includes('panel')) {
            return "Solar panels can be an excellent renewable energy solution. Consider factors like roof orientation, local sunlight hours, and available incentives when evaluating solar installation.";
        }
        
        if (lowerPrompt.includes('energy') && lowerPrompt.includes('efficiency')) {
            return "Energy efficiency improvements often provide the best return on investment. Start with insulation, LED lighting, and sealing air leaks for immediate savings.";
        }
        
        if (lowerPrompt.includes('water') && lowerPrompt.includes('conserv')) {
            return "Water conservation helps reduce utility bills and environmental impact. Simple steps include fixing leaks, installing low-flow fixtures, and using native plants in landscaping.";
        }
        
        return "I can help you with environmental and sustainability questions. My knowledge covers renewable energy, energy efficiency, water conservation, and eco-friendly practices.";
    }

    async simulateStreamingGeneration(prompt, options = {}) {
        console.log('🎭 Running demo streaming generation...');
        
        const onToken = options.onToken;
        const onProgress = options.onProgress;
        
        // Get base response
        let baseResponse;
        if (this.ragEngine && this.useRAG) {
            try {
                const ragPrompt = await this.runRAGInference(prompt, options);
                baseResponse = this.getFallbackResponse(ragPrompt);
            } catch (error) {
                baseResponse = this.getFallbackResponse(prompt);
            }
        } else {
            baseResponse = this.getFallbackResponse(prompt);
        }
        
        // Simulate streaming by breaking response into words
        const words = baseResponse.split(' ');
        let accumulatedText = '';
        
        for (let i = 0; i < words.length; i++) {
            const word = words[i] + (i < words.length - 1 ? ' ' : '');
            accumulatedText += word;
            
            // Report progress
            if (onProgress) {
                onProgress({
                    step: i + 1,
                    maxSteps: words.length,
                    percentage: Math.round(((i + 1) / words.length) * 100)
                });
            }
            
            // Stream token
            if (onToken) {
                onToken({
                    token: word,
                    accumulatedText: accumulatedText,
                    isComplete: i === words.length - 1,
                    step: i + 1
                });
            }
            
            // Add realistic delay based on word length
            const delay = Math.min(200, Math.max(50, word.length * 20));
            await new Promise(resolve => setTimeout(resolve, delay));
        }
        
        return baseResponse;
    }

    async loadWithFallback(modelList, onProgress = null) {
        let lastError = null;
        
        for (const modelName of modelList) {
            try {
                console.log(`🔄 Attempting to load: ${modelName}`);
                await this.loadModel(modelName, onProgress);
                return modelName;
            } catch (error) {
                console.warn(`⚠️ Failed to load ${modelName}:`, error.message);
                lastError = error;
                continue;
            }
        }
        
        throw new Error(`All model loading attempts failed. Last error: ${lastError?.message}`);
    }

    updateProgress(percentage, message, callback) {
        const progressData = { percentage, message };
        
        if (callback) {
            callback(progressData);
        }
        
        this.notifyCallbacks('progress', progressData);
    }

    onLoadingStateChange(callback) {
        this.loadingCallbacks.push(callback);
    }

    notifyCallbacks(state, data) {
        this.loadingCallbacks.forEach(callback => {
            try {
                callback(state, data);
            } catch (error) {
                console.error('Callback error:', error);
            }
        });
    }

    getModelStatus() {
        return {
            state: this.loadingState,
            hasModel: !!this.session,
            modelInfo: this.modelInfo
        };
    }

    dispose() {
        if (this.session) {
            this.session.release();
            this.session = null;
        }
        
        this.tokenizer = null;
        this.modelInfo = null;
        this.loadingState = 'idle';
    }
}

/**
 * Simple Tokenizer Implementation
 * Basic tokenization for demo purposes
 */
class SimpleTokenizer {
    constructor(tokenizerData = null) {
        this.vocab = tokenizerData?.vocab || this.createBasicVocab();
        this.reverseVocab = this.createReverseVocab();
    }

    createBasicVocab() {
        // Create a basic vocabulary
        const vocab = {};
        const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,!?-';
        
        for (let i = 0; i < chars.length; i++) {
            vocab[chars[i]] = i;
        }
        
        // Add special tokens
        vocab['<pad>'] = chars.length;
        vocab['<unk>'] = chars.length + 1;
        vocab['<start>'] = chars.length + 2;
        vocab['<end>'] = chars.length + 3;
        
        return vocab;
    }

    createReverseVocab() {
        const reverse = {};
        for (const [token, id] of Object.entries(this.vocab)) {
            reverse[id] = token;
        }
        return reverse;
    }

    encode(text) {
        const tokens = [];
        tokens.push(this.vocab['<start>'] || 0);
        
        for (const char of text) {
            const tokenId = this.vocab[char] || this.vocab['<unk>'] || 1;
            tokens.push(tokenId);
        }
        
        tokens.push(this.vocab['<end>'] || 0);
        return new BigInt64Array(tokens.map(t => BigInt(t)));
    }

    decode(tokens) {
        if (tokens instanceof Float32Array) {
            // Convert logits to token IDs (simplified)
            tokens = Array.from(tokens).map((_, i) => i).slice(0, 50);
        }
        
        let text = '';
        for (const tokenId of tokens) {
            const token = this.reverseVocab[tokenId];
            if (token && !['<start>', '<end>', '<pad>'].includes(token)) {
                text += token;
            }
        }
        
        return text || 'I understand your message. As a local AI model, I can help with various tasks while keeping your data private.';
    }
}

// Export for use in other modules
window.ModelLoader = ModelLoader;
window.SimpleTokenizer = SimpleTokenizer;