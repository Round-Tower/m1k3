#!/usr/bin/env python3
"""
M1K3 System Metrics
Collects device battery level, temperature, and other system information
"""

import subprocess
import platform
import psutil
import time
import locale
import socket
import json
from datetime import datetime, timezone
from typing import Dict, Optional, List
from dataclasses import dataclass, asdict

@dataclass
class SystemMetrics:
    # Power and thermal
    battery_percent: Optional[int] = None
    battery_plugged: Optional[bool] = None
    cpu_temp: Optional[float] = None
    
    # Performance
    cpu_usage: Optional[float] = None
    memory_percent: Optional[float] = None
    memory_total_gb: Optional[float] = None
    load_average: Optional[float] = None
    uptime_hours: Optional[float] = None
    
    # Hardware capabilities
    cpu_model: Optional[str] = None
    cpu_cores: Optional[int] = None
    cpu_threads: Optional[int] = None
    cpu_arch: Optional[str] = None
    gpu_info: Optional[str] = None
    
    # System environment
    os_name: Optional[str] = None
    os_version: Optional[str] = None
    hostname: Optional[str] = None
    locale_info: Optional[str] = None
    timezone: Optional[str] = None
    timezone_offset: Optional[int] = None
    current_time: Optional[str] = None
    
    # Network (privacy-safe)
    network_interfaces: Optional[int] = None
    has_wifi: Optional[bool] = None
    has_ethernet: Optional[bool] = None
    
    # Storage
    disk_usage_percent: Optional[float] = None
    disk_total_gb: Optional[float] = None
    disk_free_gb: Optional[float] = None
    
    # Display capabilities
    display_count: Optional[int] = None
    
    # Audio capabilities  
    audio_devices: Optional[int] = None
    has_microphone: Optional[bool] = None
    has_speakers: Optional[bool] = None
    
    def battery_status(self) -> str:
        """Get battery status description"""
        if self.battery_percent is None:
            return "unknown"
        elif self.battery_percent >= 80:
            return "excellent" if not self.battery_plugged else "charging-full"
        elif self.battery_percent >= 50:
            return "good" if not self.battery_plugged else "charging-good"
        elif self.battery_percent >= 20:
            return "low" if not self.battery_plugged else "charging-low"
        else:
            return "critical" if not self.battery_plugged else "charging-critical"
    
    def thermal_status(self) -> str:
        """Get thermal status description"""
        if self.cpu_temp is None:
            return "unknown"
        elif self.cpu_temp < 40:
            return "cool"
        elif self.cpu_temp < 60:
            return "warm"  
        elif self.cpu_temp < 80:
            return "hot"
        else:
            return "overheating"
    
    def performance_status(self) -> str:
        """Get overall performance status"""
        if self.cpu_usage is None or self.memory_percent is None:
            return "unknown"
        
        total_load = (self.cpu_usage + self.memory_percent) / 2
        if total_load < 30:
            return "idle"
        elif total_load < 60:
            return "moderate"
        elif total_load < 85:
            return "busy"
        else:
            return "stressed"

class SystemMonitor:
    """Cross-platform system metrics collector"""
    
    def __init__(self):
        self.platform = platform.system().lower()
        
    def get_battery_info(self) -> tuple[Optional[int], Optional[bool]]:
        """Get battery percentage and charging status"""
        try:
            if self.platform == "darwin":  # macOS
                # Use system_profiler for battery info
                result = subprocess.run([
                    "system_profiler", "SPPowerDataType"
                ], capture_output=True, text=True, timeout=5)
                
                output = result.stdout
                battery_percent = None
                is_charging = None
                
                # Parse battery percentage
                for line in output.split('\n'):
                    if 'State of Charge' in line and '%' in line:
                        try:
                            battery_percent = int(line.split('(')[1].split('%')[0])
                        except:
                            pass
                    elif 'Charging' in line:
                        is_charging = 'Yes' in line
                        
                # Fallback to psutil
                if battery_percent is None:
                    battery = psutil.sensors_battery()
                    if battery:
                        battery_percent = int(battery.percent)
                        is_charging = battery.power_plugged
                        
                return battery_percent, is_charging
                
            else:  # Linux and others
                battery = psutil.sensors_battery()
                if battery:
                    return int(battery.percent), battery.power_plugged
                    
        except Exception as e:
            print(f"Battery info error: {e}")
            
        return None, None
        
    def get_cpu_temperature(self) -> Optional[float]:
        """Get CPU temperature in Celsius"""
        try:
            if self.platform == "darwin":  # macOS
                # Try using powermetrics (requires sudo, so fallback)
                try:
                    result = subprocess.run([
                        "sudo", "-n", "powermetrics", "--samplers", "smc", "-n", "1", "-i", "1"
                    ], capture_output=True, text=True, timeout=3)
                    
                    for line in result.stdout.split('\n'):
                        if 'CPU die temperature' in line:
                            temp_str = line.split(':')[1].strip().replace('C', '')
                            return float(temp_str)
                except:
                    pass
                    
                # Alternative: Use built-in thermal sensors via psutil
                temps = psutil.sensors_temperatures()
                if temps:
                    for name, entries in temps.items():
                        if entries:
                            return entries[0].current
                            
            elif self.platform == "linux":
                temps = psutil.sensors_temperatures()
                if 'coretemp' in temps and temps['coretemp']:
                    # Average core temperatures
                    core_temps = [entry.current for entry in temps['coretemp']]
                    return sum(core_temps) / len(core_temps)
                    
        except Exception as e:
            print(f"Temperature error: {e}")
            
        return None
        
    def get_system_load(self) -> tuple[Optional[float], Optional[float], Optional[float]]:
        """Get CPU usage, memory usage, and load average"""
        try:
            cpu_usage = psutil.cpu_percent(interval=0.1)
            memory_usage = psutil.virtual_memory().percent
            
            # Load average (1 minute)
            if hasattr(psutil, 'getloadavg'):
                load_avg = psutil.getloadavg()[0]  # 1-minute average
            else:
                load_avg = None
                
            return cpu_usage, memory_usage, load_avg
            
        except Exception as e:
            print(f"System load error: {e}")
            return None, None, None
            
    def get_uptime(self) -> Optional[float]:
        """Get system uptime in hours"""
        try:
            boot_time = psutil.boot_time()
            uptime_seconds = time.time() - boot_time
            return uptime_seconds / 3600  # Convert to hours
        except:
            return None
    
    def get_hardware_info(self) -> tuple[Optional[str], Optional[int], Optional[int], Optional[str], Optional[str]]:
        """Get CPU and GPU hardware information (privacy-safe)"""
        try:
            # CPU information
            cpu_model = None
            cpu_cores = psutil.cpu_count(logical=False)  # Physical cores
            cpu_threads = psutil.cpu_count(logical=True)  # Logical cores
            cpu_arch = platform.machine()
            
            # Get CPU model name
            if self.platform == "darwin":  # macOS
                try:
                    result = subprocess.run([
                        "sysctl", "-n", "machdep.cpu.brand_string"
                    ], capture_output=True, text=True, timeout=3)
                    cpu_model = result.stdout.strip()
                except:
                    cpu_model = "Apple Silicon" if "arm" in cpu_arch.lower() else "Intel"
            elif self.platform == "linux":
                try:
                    with open("/proc/cpuinfo", "r") as f:
                        for line in f:
                            if "model name" in line:
                                cpu_model = line.split(":")[1].strip()
                                break
                except:
                    pass
            
            # GPU information (basic, privacy-safe)
            gpu_info = None
            try:
                if self.platform == "darwin":
                    result = subprocess.run([
                        "system_profiler", "SPDisplaysDataType"
                    ], capture_output=True, text=True, timeout=5)
                    
                    # Extract GPU type without specific model
                    output = result.stdout.lower()
                    if "apple" in output:
                        gpu_info = "Apple GPU"
                    elif "nvidia" in output:
                        gpu_info = "NVIDIA GPU"
                    elif "amd" in output or "radeon" in output:
                        gpu_info = "AMD GPU"
                    elif "intel" in output:
                        gpu_info = "Intel GPU"
                        
            except:
                pass
                
            return cpu_model, cpu_cores, cpu_threads, cpu_arch, gpu_info
            
        except Exception as e:
            print(f"Hardware info error: {e}")
            return None, None, None, None, None
    
    def get_system_environment(self) -> tuple[Optional[str], Optional[str], Optional[str], Optional[str], Optional[str], Optional[int], Optional[str]]:
        """Get system environment information (privacy-safe)"""
        try:
            # OS information
            os_name = platform.system()
            os_version = platform.release()
            
            # Hostname (anonymized for privacy)
            try:
                hostname = socket.gethostname()
                # Replace with generic if contains personal info
                if any(word in hostname.lower() for word in ['macbook', 'imac', 'pc', 'desktop', 'laptop']):
                    hostname = f"{os_name.lower()}-device"
                elif len(hostname) > 20:  # Likely contains personal info
                    hostname = f"{os_name.lower()}-system"
            except:
                hostname = f"{os_name.lower()}-device"
            
            # Locale information
            try:
                loc = locale.getlocale()
                locale_info = f"{loc[0]}" if loc[0] else "en_US"
            except:
                locale_info = "en_US"
                
            # Timezone information
            try:
                now = datetime.now(timezone.utc)
                local_time = now.astimezone()
                timezone_name = str(local_time.tzinfo)
                timezone_offset = int(local_time.utcoffset().total_seconds() / 3600)
                current_time = local_time.strftime("%Y-%m-%d %H:%M:%S %Z")
            except:
                timezone_name = "UTC"
                timezone_offset = 0
                current_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                
            return os_name, os_version, hostname, locale_info, timezone_name, timezone_offset, current_time
            
        except Exception as e:
            print(f"System environment error: {e}")
            return None, None, None, None, None, None, None
    
    def get_network_info(self) -> tuple[Optional[int], Optional[bool], Optional[bool]]:
        """Get network interface information (privacy-safe, no IPs)"""
        try:
            interfaces = psutil.net_if_addrs()
            interface_count = len([name for name in interfaces.keys() if name != 'lo'])
            
            # Check for common WiFi and Ethernet interface patterns
            has_wifi = any(
                name.lower().startswith(('wl', 'wifi', 'wlan', 'en0', 'en1'))
                for name in interfaces.keys()
            )
            
            has_ethernet = any(
                name.lower().startswith(('eth', 'en', 'eno', 'enp'))
                for name in interfaces.keys()
            )
            
            return interface_count, has_wifi, has_ethernet
            
        except Exception as e:
            print(f"Network info error: {e}")
            return None, None, None
    
    def get_storage_info(self) -> tuple[Optional[float], Optional[float], Optional[float]]:
        """Get storage information"""
        try:
            disk = psutil.disk_usage('/')
            
            total_gb = disk.total / (1024**3)
            free_gb = disk.free / (1024**3)
            usage_percent = (disk.used / disk.total) * 100
            
            return usage_percent, total_gb, free_gb
            
        except Exception as e:
            print(f"Storage info error: {e}")
            return None, None, None
    
    def get_display_info(self) -> Optional[int]:
        """Get display count (privacy-safe)"""
        try:
            if self.platform == "darwin":
                result = subprocess.run([
                    "system_profiler", "SPDisplaysDataType"
                ], capture_output=True, text=True, timeout=5)
                
                # Count display entries
                display_count = result.stdout.count("Display Type:")
                return display_count if display_count > 0 else 1
                
            elif self.platform == "linux":
                # Try xrandr if available
                try:
                    result = subprocess.run([
                        "xrandr", "--listmonitors"
                    ], capture_output=True, text=True, timeout=3)
                    
                    lines = result.stdout.strip().split('\n')
                    if lines and 'Monitors:' in lines[0]:
                        return int(lines[0].split(':')[1].strip())
                except:
                    pass
                    
            # Default to 1 display
            return 1
            
        except Exception as e:
            print(f"Display info error: {e}")
            return None
    
    def get_audio_info(self) -> tuple[Optional[int], Optional[bool], Optional[bool]]:
        """Get audio device information (privacy-safe)"""
        try:
            # Try to use psutil or system commands for audio device detection
            audio_device_count = None
            has_microphone = None
            has_speakers = None
            
            if self.platform == "darwin":
                try:
                    result = subprocess.run([
                        "system_profiler", "SPAudioDataType"
                    ], capture_output=True, text=True, timeout=5)
                    
                    output = result.stdout.lower()
                    # Count audio devices (input + output)
                    audio_device_count = output.count("audio id:")
                    
                    # Check for microphone and speakers
                    has_microphone = "input" in output or "microphone" in output
                    has_speakers = "output" in output or "speaker" in output
                    
                except:
                    pass
                    
            # Try sounddevice if available
            try:
                import sounddevice as sd
                devices = sd.query_devices()
                
                if isinstance(devices, list):
                    audio_device_count = len(devices)
                    has_microphone = any(d.get('max_input_channels', 0) > 0 for d in devices)
                    has_speakers = any(d.get('max_output_channels', 0) > 0 for d in devices)
                elif hasattr(devices, 'name'):
                    # Single device
                    audio_device_count = 1
                    has_microphone = getattr(devices, 'max_input_channels', 0) > 0
                    has_speakers = getattr(devices, 'max_output_channels', 0) > 0
                    
            except ImportError:
                pass
            except Exception:
                pass
                
            return audio_device_count, has_microphone, has_speakers
            
        except Exception as e:
            print(f"Audio info error: {e}")
            return None, None, None
            
    def collect_metrics(self) -> SystemMetrics:
        """Collect all comprehensive system metrics"""
        # Basic metrics
        battery_percent, battery_plugged = self.get_battery_info()
        cpu_temp = self.get_cpu_temperature()
        cpu_usage, memory_percent, load_average = self.get_system_load()
        uptime_hours = self.get_uptime()
        
        # Hardware info
        cpu_model, cpu_cores, cpu_threads, cpu_arch, gpu_info = self.get_hardware_info()
        
        # System environment
        os_name, os_version, hostname, locale_info, timezone_name, timezone_offset, current_time = self.get_system_environment()
        
        # Network info
        network_interfaces, has_wifi, has_ethernet = self.get_network_info()
        
        # Storage info
        disk_usage_percent, disk_total_gb, disk_free_gb = self.get_storage_info()
        
        # Display info
        display_count = self.get_display_info()
        
        # Audio info
        audio_devices, has_microphone, has_speakers = self.get_audio_info()
        
        # Memory total
        memory_total_gb = None
        try:
            memory_total_gb = psutil.virtual_memory().total / (1024**3)
        except:
            pass
        
        return SystemMetrics(
            # Power and thermal
            battery_percent=battery_percent,
            battery_plugged=battery_plugged,
            cpu_temp=cpu_temp,
            
            # Performance
            cpu_usage=cpu_usage,
            memory_percent=memory_percent,
            memory_total_gb=memory_total_gb,
            load_average=load_average,
            uptime_hours=uptime_hours,
            
            # Hardware capabilities
            cpu_model=cpu_model,
            cpu_cores=cpu_cores,
            cpu_threads=cpu_threads,
            cpu_arch=cpu_arch,
            gpu_info=gpu_info,
            
            # System environment
            os_name=os_name,
            os_version=os_version,
            hostname=hostname,
            locale_info=locale_info,
            timezone=timezone_name,
            timezone_offset=timezone_offset,
            current_time=current_time,
            
            # Network (privacy-safe)
            network_interfaces=network_interfaces,
            has_wifi=has_wifi,
            has_ethernet=has_ethernet,
            
            # Storage
            disk_usage_percent=disk_usage_percent,
            disk_total_gb=disk_total_gb,
            disk_free_gb=disk_free_gb,
            
            # Display capabilities
            display_count=display_count,
            
            # Audio capabilities
            audio_devices=audio_devices,
            has_microphone=has_microphone,
            has_speakers=has_speakers
        )
    
    def get_context_summary(self, metrics: SystemMetrics) -> str:
        """Generate a comprehensive but privacy-safe context summary"""
        context_parts = []
        
        # Hardware context
        if metrics.cpu_model:
            context_parts.append(f"CPU: {metrics.cpu_model}")
        if metrics.cpu_cores and metrics.cpu_threads:
            context_parts.append(f"Cores: {metrics.cpu_cores}c/{metrics.cpu_threads}t")
        if metrics.gpu_info:
            context_parts.append(f"GPU: {metrics.gpu_info}")
        if metrics.memory_total_gb:
            context_parts.append(f"RAM: {metrics.memory_total_gb:.0f}GB")
            
        # System environment
        if metrics.os_name and metrics.os_version:
            context_parts.append(f"OS: {metrics.os_name} {metrics.os_version}")
        if metrics.timezone and metrics.timezone_offset is not None:
            sign = "+" if metrics.timezone_offset >= 0 else "-"
            context_parts.append(f"TZ: UTC{sign}{abs(metrics.timezone_offset)}")
        if metrics.locale_info:
            context_parts.append(f"Locale: {metrics.locale_info}")
            
        # Capabilities
        capabilities = []
        if metrics.has_microphone:
            capabilities.append("🎤")
        if metrics.has_speakers:
            capabilities.append("🔊")
        if metrics.has_wifi:
            capabilities.append("📶")
        if metrics.has_ethernet:
            capabilities.append("🌐")
        if metrics.display_count and metrics.display_count > 1:
            capabilities.append(f"🖥️x{metrics.display_count}")
        
        if capabilities:
            context_parts.append(f"Capabilities: {' '.join(capabilities)}")
            
        # Current status
        status_parts = []
        if metrics.battery_percent is not None:
            status_parts.append(f"Battery: {metrics.battery_percent}%")
        if metrics.cpu_temp is not None:
            status_parts.append(f"CPU: {metrics.cpu_temp:.0f}°C")
        if metrics.disk_usage_percent is not None:
            status_parts.append(f"Disk: {metrics.disk_usage_percent:.0f}%")
            
        if status_parts:
            context_parts.append(f"Status: {', '.join(status_parts)}")
            
        return " | ".join(context_parts)
    
    def export_context_json(self, metrics: SystemMetrics, include_sensitive: bool = False) -> str:
        """Export context as JSON for AI consumption"""
        context_data = asdict(metrics)
        
        # Remove sensitive data unless specifically requested
        if not include_sensitive:
            # Remove hostname and detailed system info that might identify user
            context_data.pop('hostname', None)
            
        # Add derived status information
        context_data['derived_status'] = {
            'battery_status': metrics.battery_status(),
            'thermal_status': metrics.thermal_status(),
            'performance_status': metrics.performance_status(),
            'context_summary': self.get_context_summary(metrics)
        }
        
        return json.dumps(context_data, indent=2, default=str)

def generate_dynamic_greeting(metrics: SystemMetrics) -> str:
    """Generate a unique greeting based on system metrics"""
    
    # Base greeting parts
    greetings = [
        "Hello there!", "Hey!", "Greetings!", "Hi!", "Welcome back!",
        "Good to see you!", "Ready to help!", "At your service!"
    ]
    
    # Battery-based comments
    battery_comments = {
        "excellent": "Your battery is looking fantastic",
        "good": "Battery levels are solid", 
        "low": "Might want to charge up soon",
        "critical": "Better find a charger quick",
        "charging-full": "Almost fully charged",
        "charging-good": "Charging nicely",
        "charging-low": "Good thing you're charging",
        "charging-critical": "Charging just in time",
        "unknown": "Can't check your battery right now"
    }
    
    # Thermal comments  
    thermal_comments = {
        "cool": "Your system is running nice and cool",
        "warm": "Temperature is comfortable",
        "hot": "Things are heating up a bit",
        "overheating": "Whoa, your CPU is running hot!",
        "unknown": "Can't read temperature sensors"
    }
    
    # Performance comments
    performance_comments = {
        "idle": "System is relaxed and ready",
        "moderate": "Moderate load, all good",
        "busy": "You've got the system working hard",
        "stressed": "System is under heavy load",
        "unknown": "System metrics unavailable"
    }
    
    import random
    
    # Build greeting
    greeting = random.choice(greetings)
    
    # Add system-specific comment
    battery_status = metrics.battery_status()
    thermal_status = metrics.thermal_status()
    perf_status = metrics.performance_status()
    
    comments = []
    
    # Add battery comment (most important)
    if battery_status in battery_comments:
        comments.append(battery_comments[battery_status])
        
    # Add thermal comment if notable
    if thermal_status in ["hot", "overheating", "cool"]:
        comments.append(thermal_comments[thermal_status])
    elif thermal_status == "warm" and random.random() < 0.3:
        comments.append(thermal_comments[thermal_status])
        
    # Add performance comment if notable
    if perf_status in ["busy", "stressed"]:
        comments.append(performance_comments[perf_status])
    elif perf_status == "idle" and random.random() < 0.4:
        comments.append(performance_comments[perf_status])
        
    # Combine greeting with comments
    if comments:
        if len(comments) == 1:
            return f"{greeting} {comments[0]}."
        elif len(comments) == 2:
            return f"{greeting} {comments[0]}, and {comments[1].lower()}."
        else:
            return f"{greeting} {comments[0]}."
    else:
        return f"{greeting} I'm M1K3, ready to assist you!"

if __name__ == "__main__":
    # Test comprehensive system metrics collection
    print("🧘 M1K3 System Context Collection")
    print("=" * 50)
    
    monitor = SystemMonitor()
    
    print("\n⏳ Collecting comprehensive device metrics...")
    metrics = monitor.collect_metrics()
    
    print("\n🖥️  Hardware Information:")
    if metrics.cpu_model:
        print(f"  CPU: {metrics.cpu_model} ({metrics.cpu_cores}c/{metrics.cpu_threads}t)")
    if metrics.cpu_arch:
        print(f"  Architecture: {metrics.cpu_arch}")
    if metrics.gpu_info:
        print(f"  GPU: {metrics.gpu_info}")
    if metrics.memory_total_gb:
        print(f"  Memory: {metrics.memory_total_gb:.1f}GB")
    
    print("\n🌍 System Environment:")
    if metrics.os_name and metrics.os_version:
        print(f"  OS: {metrics.os_name} {metrics.os_version}")
    if metrics.hostname:
        print(f"  Device: {metrics.hostname}")
    if metrics.timezone and metrics.timezone_offset is not None:
        sign = "+" if metrics.timezone_offset >= 0 else "-"
        print(f"  Timezone: {metrics.timezone} (UTC{sign}{abs(metrics.timezone_offset)})")
    if metrics.locale_info:
        print(f"  Locale: {metrics.locale_info}")
    if metrics.current_time:
        print(f"  Current Time: {metrics.current_time}")
    
    print("\n🔌 Device Capabilities:")
    capabilities = []
    if metrics.has_microphone:
        capabilities.append("🎤 Microphone")
    if metrics.has_speakers:
        capabilities.append("🔊 Audio Output")
    if metrics.has_wifi:
        capabilities.append("📶 WiFi")
    if metrics.has_ethernet:
        capabilities.append("🌐 Ethernet")
    if metrics.display_count:
        capabilities.append(f"🖥️ {metrics.display_count} Display(s)")
    if metrics.audio_devices:
        capabilities.append(f"🎵 {metrics.audio_devices} Audio Device(s)")
    
    for cap in capabilities:
        print(f"  {cap}")
    
    print("\n📊 Current Status:")
    if metrics.battery_percent is not None:
        status_emoji = "🔋" if not metrics.battery_plugged else "⚡"
        print(f"  {status_emoji} Battery: {metrics.battery_percent}% ({metrics.battery_status()})")
    if metrics.cpu_temp is not None:
        print(f"  🌡️  CPU Temp: {metrics.cpu_temp:.1f}°C ({metrics.thermal_status()})")
    if metrics.cpu_usage is not None:
        print(f"  ⚙️  CPU Usage: {metrics.cpu_usage:.1f}%")
    if metrics.memory_percent is not None:
        print(f"  🧠 Memory: {metrics.memory_percent:.1f}%") 
    if metrics.disk_usage_percent is not None:
        print(f"  💾 Storage: {metrics.disk_usage_percent:.1f}% used")
    if metrics.uptime_hours is not None:
        print(f"  ⏰ Uptime: {metrics.uptime_hours:.1f}h")
    print(f"  🎯 Performance: {metrics.performance_status()}")
    
    print("\n🎤 Dynamic Greeting:")
    greeting = generate_dynamic_greeting(metrics)
    print(f"  \"{greeting}\"")
    
    print("\n📋 Context Summary:")
    context_summary = monitor.get_context_summary(metrics)
    print(f"  {context_summary}")
    
    print("\n📄 Privacy-Safe JSON Export:")
    json_context = monitor.export_context_json(metrics, include_sensitive=False)
    print("  (Context data ready for AI consumption)")
    
    print("\n✅ System context collection complete!")