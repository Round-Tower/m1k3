#!/usr/bin/env python3
"""
Hardware Insights Generator for M1K3
Provides exciting, educational facts about the user's hardware
"""

import random
from typing import Optional, List

class HardwareInsights:
    """Generate exciting insights about hardware capabilities"""
    
    @staticmethod
    def get_cpu_insight(cpu_model: str, cores: int, threads: int) -> str:
        """Generate insight about CPU"""
        insights = []
        
        # Apple Silicon specific insights
        if "M1" in cpu_model or "M2" in cpu_model or "M3" in cpu_model:
            if "Max" in cpu_model:
                insights.extend([
                    f"Your {cpu_model} has 57.6 TFLOPS of GPU power - that's equivalent to 10 high-end graphics cards from 2015!",
                    f"The {cpu_model} can decode 4K ProRes video in real-time while using less power than a light bulb!",
                    f"Your {cpu_model} has dedicated neural engines that can perform 15.8 trillion operations per second!",
                    f"The unified memory architecture in your {cpu_model} means AI models load 3x faster than traditional systems!"
                ])
            elif "Pro" in cpu_model:
                insights.extend([
                    f"Your {cpu_model} delivers 200GB/s memory bandwidth - that's like downloading 40 HD movies per second!",
                    f"The {cpu_model} neural engine can recognize 1000 images per second while barely warming up!",
                    f"Your chip uses 5nm technology - transistors smaller than a virus, packed by the billions!"
                ])
            else:
                insights.extend([
                    f"Your {cpu_model} is more powerful than the world's fastest supercomputer from 1996!",
                    f"The {cpu_model} can run AI models that required server farms just 5 years ago!",
                    f"Apple Silicon means zero-copy memory sharing between CPU and GPU - instant AI inference!"
                ])
        
        # Intel/AMD insights based on core count
        if cores >= 16:
            insights.extend([
                f"With {cores} cores, you have more processing power than entire data centers from the 1990s!",
                f"Your {cores}-core beast can handle Hollywood-grade video rendering!",
                f"Each of your {cores} cores is like having a dedicated AI assistant!"
            ])
        elif cores >= 8:
            insights.extend([
                f"Your {cores} cores provide perfect parallelization for AI inference!",
                f"With {cores} cores, you can run multiple AI models simultaneously!",
                f"Modern {cores}-core CPUs outperform supercomputers from just 20 years ago!"
            ])
        elif cores >= 4:
            insights.extend([
                f"Your {cores} cores are optimized for the perfect balance of power and efficiency!",
                f"Even with {cores} cores, you're running AI that needed cloud servers in 2020!",
                f"Quality over quantity - your {cores} cores are architectural marvels!"
            ])
        
        # Thread-specific insights
        if threads > cores:
            insights.append(f"Hyperthreading gives you {threads} threads from {cores} cores - like having virtual assistants!")
        
        if not insights:
            insights = [
                f"Your processor is a marvel of modern engineering!",
                f"Local AI processing keeps getting more amazing!",
                f"Every CPU cycle contributes to private, secure AI!"
            ]
        
        return random.choice(insights)
    
    @staticmethod
    def get_memory_insight(memory_gb: float) -> str:
        """Generate insight about RAM"""
        insights = []
        
        if memory_gb >= 64:
            insights = [
                f"{memory_gb:.0f}GB of RAM - you could load entire language models that power ChatGPT!",
                f"With {memory_gb:.0f}GB, you have more RAM than most cloud AI servers!",
                f"{memory_gb:.0f}GB means you can run multiple 7B parameter models simultaneously!",
                f"Your {memory_gb:.0f}GB could store the entire English Wikipedia in RAM... 10 times over!"
            ]
        elif memory_gb >= 32:
            insights = [
                f"{memory_gb:.0f}GB of RAM - perfect for running advanced AI models locally!",
                f"Your {memory_gb:.0f}GB can handle AI models that required $10k/month cloud services!",
                f"With {memory_gb:.0f}GB, context windows of 100k+ tokens are a breeze!"
            ]
        elif memory_gb >= 16:
            insights = [
                f"{memory_gb:.0f}GB RAM enables smooth multitasking with AI assistance!",
                f"Your {memory_gb:.0f}GB is 4000x more than the computer that guided Apollo 11!",
                f"With {memory_gb:.0f}GB, you can run models that fit entire books in context!"
            ]
        elif memory_gb >= 8:
            insights = [
                f"{memory_gb:.0f}GB RAM is perfect for efficient local AI models!",
                f"Your {memory_gb:.0f}GB would have cost $1 million in 1980!",
                f"Optimized models run beautifully in your {memory_gb:.0f}GB of RAM!"
            ]
        else:
            insights = [
                f"Efficient AI models are designed to run smoothly in {memory_gb:.0f}GB!",
                f"M1K3 is optimized to make the most of your {memory_gb:.0f}GB RAM!",
                f"Small but mighty - {memory_gb:.0f}GB with smart caching goes far!"
            ]
        
        return random.choice(insights)
    
    @staticmethod
    def get_gpu_insight(gpu_info: str) -> str:
        """Generate insight about GPU"""
        insights = []
        
        if "Apple" in gpu_info or "M1" in gpu_info or "M2" in gpu_info:
            insights = [
                "Apple's unified GPU architecture means zero-latency AI acceleration!",
                "Your Apple GPU shares memory with the CPU - no slow PCIe transfers!",
                "The Metal Performance Shaders make AI inference incredibly efficient!",
                "Apple GPUs are optimized for machine learning from the silicon up!"
            ]
        elif "NVIDIA" in gpu_info:
            insights = [
                "NVIDIA GPUs pioneered the AI revolution with CUDA cores!",
                "Your NVIDIA GPU can accelerate AI models by 10-100x!",
                "Tensor cores in modern NVIDIA GPUs are built specifically for AI!",
                "NVIDIA's parallel processing is perfect for neural networks!"
            ]
        elif "AMD" in gpu_info or "Radeon" in gpu_info:
            insights = [
                "AMD GPUs offer incredible compute power for AI workloads!",
                "ROCm enables your AMD GPU to accelerate machine learning!",
                "AMD's infinity cache speeds up AI inference dramatically!",
                "Your Radeon GPU has thousands of stream processors for parallel AI!"
            ]
        elif "Intel" in gpu_info:
            insights = [
                "Intel's integrated graphics are surprisingly capable for AI inference!",
                "Intel GPUs now include dedicated AI acceleration engines!",
                "Your Intel GPU can handle many AI workloads efficiently!",
                "Integrated graphics mean better battery life during AI tasks!"
            ]
        else:
            insights = [
                "Your GPU accelerates AI computations beyond CPU capabilities!",
                "Modern GPUs are the workhorses of the AI revolution!",
                "GPU parallel processing is perfect for neural networks!",
                "Every GPU cycle contributes to faster AI responses!"
            ]
        
        return random.choice(insights)
    
    @staticmethod
    def get_eco_insight(water_saved: float, energy_saved: float, co2_saved: float) -> str:
        """Generate insight about ecological impact"""
        insights = []
        
        if water_saved > 1000:
            liters = water_saved / 1000
            insights.append(f"You've saved {liters:.1f} liters of water - enough for {liters*4:.0f} cups of coffee!")
        
        if energy_saved > 100:
            insights.append(f"You've saved {energy_saved:.0f}Wh - enough to charge your phone {energy_saved/20:.0f} times!")
        
        if co2_saved > 100:
            insights.append(f"You've prevented {co2_saved:.0f}g of CO₂ - like planting {co2_saved/20:.1f} trees!")
        
        if not insights:
            insights = [
                "Every local query saves data center resources!",
                "Local AI is the greenest AI!",
                "You're part of the sustainable computing revolution!",
                "Zero cloud means zero carbon footprint from data transmission!"
            ]
        
        return random.choice(insights)
    
    @staticmethod
    def get_performance_insight(cpu_usage: float, memory_usage: float, response_time: float = None) -> str:
        """Generate insight about current performance"""
        insights = []
        
        if cpu_usage < 20 and memory_usage < 30:
            insights = [
                "Your system is practically idling while running advanced AI!",
                "So much headroom - you could run multiple AI assistants!",
                "Efficiency at its finest - powerful AI with minimal resource usage!",
                "Your system is barely breaking a sweat!"
            ]
        elif cpu_usage > 80 or memory_usage > 80:
            insights = [
                "Your system is working hard to deliver amazing AI capabilities!",
                "Maximum performance mode engaged - full power ahead!",
                "Every resource focused on delivering intelligent responses!",
                "Peak performance - your hardware is showing its strength!"
            ]
        else:
            insights = [
                "Perfect balance of performance and efficiency!",
                "Your system is in the optimal zone for AI processing!",
                "Smooth sailing with plenty of resources available!",
                "Balanced load means consistent, reliable AI performance!"
            ]
        
        if response_time and response_time < 0.5:
            insights.append(f"Response in {response_time:.2f}s - faster than human reaction time!")
        elif response_time and response_time < 1.0:
            insights.append(f"Sub-second response at {response_time:.2f}s - lightning fast!")
        
        return random.choice(insights)
    
    @staticmethod
    def get_privacy_insight() -> str:
        """Generate insight about privacy"""
        insights = [
            "100% local processing means your thoughts stay yours!",
            "Zero bytes transmitted - complete privacy achieved!",
            "No cookies, no tracking, no surveillance - just pure AI!",
            "Your data never leaves your device - true digital sovereignty!",
            "Private as a diary, powerful as a datacenter!",
            "What happens on your device, stays on your device!",
            "No cloud company can access your conversations - ever!",
            "End-to-end encryption? We're better - no transmission at all!",
            "Your AI assistant that even the NSA can't eavesdrop on!",
            "Privacy by design, not by policy - technically impossible to breach!"
        ]
        return random.choice(insights)
    
    @staticmethod
    def get_motivational_insight() -> str:
        """Generate motivational insight"""
        insights = [
            "You're running AI that was science fiction just years ago!",
            "The future of computing is happening on your device right now!",
            "You're part of the local AI revolution!",
            "Every query makes you an AI pioneer!",
            "You're experiencing the democratization of AI!",
            "Welcome to the age of personal AI sovereignty!",
            "You own your AI - no subscriptions, no limits!",
            "The power of AI, the freedom of ownership!",
            "You're living in the golden age of personal computing!",
            "Today's session could spark tomorrow's breakthrough!"
        ]
        return random.choice(insights)

def generate_hardware_insight(metrics) -> str:
    """Generate a random exciting insight based on system metrics"""
    insight_generators = []
    
    # Add applicable insight generators based on available data
    if metrics.cpu_model and metrics.cpu_cores:
        insight_generators.append(
            lambda: HardwareInsights.get_cpu_insight(
                metrics.cpu_model, metrics.cpu_cores, metrics.cpu_threads
            )
        )
    
    if metrics.memory_total_gb:
        insight_generators.append(
            lambda: HardwareInsights.get_memory_insight(metrics.memory_total_gb)
        )
    
    if metrics.gpu_info:
        insight_generators.append(
            lambda: HardwareInsights.get_gpu_insight(metrics.gpu_info)
        )
    
    if hasattr(metrics, 'water_saved_ml') and metrics.water_saved_ml > 0:
        insight_generators.append(
            lambda: HardwareInsights.get_eco_insight(
                metrics.water_saved_ml,
                metrics.energy_saved_wh,
                metrics.co2_saved_g
            )
        )
    
    if metrics.cpu_usage and metrics.memory_percent:
        insight_generators.append(
            lambda: HardwareInsights.get_performance_insight(
                metrics.cpu_usage, metrics.memory_percent
            )
        )
    
    # Always available insights
    insight_generators.extend([
        HardwareInsights.get_privacy_insight,
        HardwareInsights.get_motivational_insight
    ])
    
    # Select and generate a random insight
    generator = random.choice(insight_generators)
    return generator()

if __name__ == "__main__":
    # Test the insights system
    print("🧪 Testing Hardware Insights Generator")
    print("=" * 50)
    
    # Create mock metrics for testing
    class MockMetrics:
        cpu_model = "Apple M1 Max"
        cpu_cores = 10
        cpu_threads = 10
        memory_total_gb = 64.0
        gpu_info = "Apple GPU"
        cpu_usage = 35.0
        memory_percent = 42.0
        water_saved_ml = 1200
        energy_saved_wh = 150
        co2_saved_g = 200
    
    metrics = MockMetrics()
    
    print("\n💡 Sample Insights:")
    for i in range(5):
        insight = generate_hardware_insight(metrics)
        print(f"{i+1}. {insight}")
    
    print("\n✅ Insights system ready to inspire!")