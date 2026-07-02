#!/usr/bin/env python3
"""
Test voice synthesis with longer text to reproduce ONNX BERT error
"""

from enhanced_voice_engine import create_voice_engine

def test_long_text_synthesis():
    print("🔊 Testing Voice Synthesis with Long Text\n" + "="*50)
    
    voice_engine = create_voice_engine()
    
    print("🔄 Loading voice engine...")
    if voice_engine.load_model():
        print("✅ Voice engine loaded successfully")
        
        # Test progressively longer texts
        test_texts = [
            "Short text test.",
            
            "This is a medium length text that should work fine with most voice synthesis systems.",
            
            "This is a longer text that might trigger ONNX BERT model issues. It contains multiple sentences and should be long enough to potentially cause shape mismatches in the neural network layers. The issue typically occurs when the input sequence exceeds certain length limits or when the BERT tokenizer creates tensors that don't match the expected input dimensions.",
            
            "This is an even longer text designed to specifically trigger the ONNX runtime error with the BERT model used in KittenML TTS. When processing very long sequences, the BERT model's attention mechanism and tokenization can create tensors with shapes that don't match the expected input dimensions defined in the ONNX model file. This often manifests as an 'invalid expand shape' error in the ExecuteKernel function when the Expand node tries to broadcast tensors to incompatible shapes. The error typically occurs during the forward pass of the neural network when processing the text embeddings and attention weights. This is a common issue with ONNX models that have fixed input shapes or when the dynamic shape inference fails to properly calculate the expected dimensions for variable-length sequences."
        ]
        
        for i, test_text in enumerate(test_texts, 1):
            print(f"\n🗣️  Test {i} ({len(test_text)} chars): Testing synthesis...")
            print(f"   Text preview: '{test_text[:50]}{'...' if len(test_text) > 50 else ''}'")
            
            try:
                result = voice_engine.synthesize_and_play(test_text, background=False)
                
                if result:
                    print(f"   ✅ Success - synthesis completed")
                else:
                    print(f"   ❌ Failed - synthesis failed")
                    
            except Exception as e:
                print(f"   💥 Exception: {e}")
                break
        
        # Check final status
        status = voice_engine.get_status()
        print(f"\n📊 Final Status:")
        print(f"   Engine: {status.get('engine', 'unknown')}")
        print(f"   Available: {status.get('available', False)}")
        print(f"   Enabled: {status.get('enabled', False)}")
        
    else:
        print("❌ Failed to load voice engine")

if __name__ == "__main__":
    test_long_text_synthesis()