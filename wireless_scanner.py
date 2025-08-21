#!/usr/bin/env python3
"""
M1K3 Wireless Scanner
Advanced BLE, WiFi, and GPS scanning for enhanced capabilities
"""

import subprocess
import json
import time
import threading
from pathlib import Path
from typing import Dict, List, Any, Optional
import re

try:
    import psutil
    PSUTIL_AVAILABLE = True
except ImportError:
    PSUTIL_AVAILABLE = False

class WirelessScanner:
    """Comprehensive wireless scanning and analysis"""
    
    def __init__(self):
        self.platform = self._detect_platform()
        self.scan_cache = {}
        self.last_scan_time = {}
        self.cache_duration = 30  # seconds
        
    def _detect_platform(self) -> str:
        """Detect platform for scanner optimization"""
        try:
            import platform
            return platform.system().lower()
        except:
            return "unknown"
    
    def scan_wifi_networks(self, include_hidden: bool = False) -> List[Dict[str, Any]]:
        """Scan for WiFi networks with detailed information"""
        cache_key = f"wifi_{include_hidden}"
        
        if self._is_cached(cache_key):
            return self.scan_cache[cache_key]
        
        networks = []
        
        try:
            if self.platform == "darwin":  # macOS
                networks = self._scan_wifi_macos(include_hidden)
            elif self.platform == "linux":
                networks = self._scan_wifi_linux(include_hidden)
            elif self.platform == "windows":
                networks = self._scan_wifi_windows(include_hidden)
                
            # Cache results
            self.scan_cache[cache_key] = networks
            self.last_scan_time[cache_key] = time.time()
            
        except Exception as e:
            print(f"📡 WiFi scan error: {e}")
            
        return networks
    
    def _scan_wifi_macos(self, include_hidden: bool = False) -> List[Dict[str, Any]]:
        """macOS WiFi scanning using airport utility"""
        networks = []
        
        try:
            # Use airport utility for detailed scan
            airport_path = "/System/Library/PrivateFrameworks/Apple80211.framework/Versions/Current/Resources/airport"
            
            if Path(airport_path).exists():
                result = subprocess.run([airport_path, "-s"], 
                                      capture_output=True, text=True, timeout=30)
                
                if result.returncode == 0:
                    lines = result.stdout.strip().split('\n')[1:]  # Skip header
                    
                    for line in lines:
                        if not line.strip():
                            continue
                            
                        # Parse airport output format (more robust)
                        parts = line.split()
                        if len(parts) >= 6:
                            ssid = parts[0] if parts[0] != "" else "<Hidden>"
                            bssid = parts[1]
                            try:
                                rssi = int(parts[2])
                            except ValueError:
                                continue  # Skip malformed lines
                            channel = parts[3]
                            security = " ".join(parts[6:]) if len(parts) > 6 else "Open"
                            
                            if include_hidden or ssid != "<Hidden>":
                                networks.append({
                                    "ssid": ssid,
                                    "bssid": bssid,
                                    "rssi": rssi,
                                    "channel": channel,
                                    "security": security,
                                    "signal_strength": self._rssi_to_strength(rssi),
                                    "band": self._channel_to_band(channel)
                                })
            
            # Fallback to system_profiler
            if not networks:
                networks = self._scan_wifi_system_profiler()
                
        except Exception as e:
            print(f"📡 macOS WiFi scan error: {e}")
            
        return networks
    
    def _scan_wifi_linux(self, include_hidden: bool = False) -> List[Dict[str, Any]]:
        """Linux WiFi scanning using iwlist/nmcli"""
        networks = []
        
        try:
            # Try nmcli first (more reliable)
            result = subprocess.run(["nmcli", "-t", "-f", "SSID,BSSID,MODE,CHAN,FREQ,RATE,SIGNAL,BARS,SECURITY", 
                                   "device", "wifi"], capture_output=True, text=True, timeout=30)
            
            if result.returncode == 0:
                lines = result.stdout.strip().split('\n')
                for line in lines:
                    parts = line.split(':')
                    if len(parts) >= 9:
                        ssid = parts[0] if parts[0] else "<Hidden>"
                        if include_hidden or ssid != "<Hidden>":
                            networks.append({
                                "ssid": ssid,
                                "bssid": parts[1],
                                "channel": parts[3],
                                "frequency": parts[4],
                                "signal": int(parts[6]) if parts[6].isdigit() else -100,
                                "security": parts[8],
                                "signal_strength": self._signal_to_strength(int(parts[6]) if parts[6].isdigit() else -100)
                            })
            
            # Fallback to iwlist
            if not networks:
                networks = self._scan_wifi_iwlist(include_hidden)
                
        except Exception as e:
            print(f"📡 Linux WiFi scan error: {e}")
            
        return networks
    
    def scan_bluetooth_devices(self, scan_duration: int = 10) -> List[Dict[str, Any]]:
        """Scan for Bluetooth/BLE devices"""
        cache_key = f"bluetooth_{scan_duration}"
        
        if self._is_cached(cache_key):
            return self.scan_cache[cache_key]
        
        devices = []
        
        try:
            if self.platform == "darwin":
                devices = self._scan_bluetooth_macos(scan_duration)
            elif self.platform == "linux":
                devices = self._scan_bluetooth_linux(scan_duration)
                
            self.scan_cache[cache_key] = devices
            self.last_scan_time[cache_key] = time.time()
            
        except Exception as e:
            print(f"📱 Bluetooth scan error: {e}")
            
        return devices
    
    def _scan_bluetooth_macos(self, scan_duration: int) -> List[Dict[str, Any]]:
        """macOS Bluetooth scanning"""
        devices = []
        
        try:
            # Use system_profiler for connected devices
            result = subprocess.run(["system_profiler", "SPBluetoothDataType", "-json"], 
                                  capture_output=True, text=True, timeout=30)
            
            if result.returncode == 0:
                data = json.loads(result.stdout)
                bluetooth_info = data.get("SPBluetoothDataType", [])
                
                for bt_controller in bluetooth_info:
                    paired_devices = bt_controller.get("device_title", {})
                    
                    for device_name, device_info in paired_devices.items():
                        if isinstance(device_info, dict):
                            devices.append({
                                "name": device_name,
                                "address": device_info.get("device_address", "Unknown"),
                                "type": "Paired",
                                "services": device_info.get("device_services", []),
                                "connected": device_info.get("device_isconnected", "No") == "Yes"
                            })
        
        except Exception as e:
            print(f"📱 macOS Bluetooth scan error: {e}")
            
        return devices
    
    def get_gps_location(self) -> Optional[Dict[str, Any]]:
        """Get GPS/location information (where available)"""
        cache_key = "gps_location"
        
        if self._is_cached(cache_key):
            return self.scan_cache[cache_key]
        
        location_info = None
        
        try:
            if self.platform == "darwin":
                location_info = self._get_location_macos()
            elif self.platform == "linux":
                location_info = self._get_location_linux()
                
            if location_info:
                self.scan_cache[cache_key] = location_info
                self.last_scan_time[cache_key] = time.time()
                
        except Exception as e:
            print(f"🌍 GPS location error: {e}")
            
        return location_info
    
    def _get_location_macos(self) -> Optional[Dict[str, Any]]:
        """macOS location services (requires permission)"""
        try:
            # Try using CoreLocation via Python (requires additional setup)
            # This is a placeholder - actual implementation would need CoreLocation bindings
            return {
                "source": "macOS Location Services",
                "status": "Permission required",
                "note": "Enable location services for enhanced capabilities"
            }
        except:
            return None
    
    def analyze_wireless_environment(self) -> Dict[str, Any]:
        """Comprehensive wireless environment analysis"""
        analysis = {
            "timestamp": time.time(),
            "wifi": {
                "networks_found": 0,
                "channels_used": [],
                "security_types": {},
                "signal_strengths": [],
                "congestion_analysis": {}
            },
            "bluetooth": {
                "devices_found": 0,
                "device_types": [],
                "active_connections": 0
            },
            "recommendations": []
        }
        
        try:
            # WiFi Analysis
            wifi_networks = self.scan_wifi_networks(include_hidden=True)
            analysis["wifi"]["networks_found"] = len(wifi_networks)
            
            channels = []
            securities = {}
            signals = []
            
            for network in wifi_networks:
                if "channel" in network:
                    channels.append(network["channel"])
                
                if "security" in network:
                    sec_type = network["security"]
                    securities[sec_type] = securities.get(sec_type, 0) + 1
                
                if "rssi" in network:
                    signals.append(network["rssi"])
            
            analysis["wifi"]["channels_used"] = list(set(channels))
            analysis["wifi"]["security_types"] = securities
            analysis["wifi"]["signal_strengths"] = signals
            
            # Channel congestion analysis
            channel_counts = {}
            for channel in channels:
                channel_counts[channel] = channel_counts.get(channel, 0) + 1
            
            analysis["wifi"]["congestion_analysis"] = channel_counts
            
            # Generate recommendations
            if channel_counts:
                most_congested = max(channel_counts, key=channel_counts.get)
                least_congested = min(channel_counts, key=channel_counts.get)
                
                analysis["recommendations"].append(
                    f"WiFi: Channel {most_congested} is most congested ({channel_counts[most_congested]} networks), "
                    f"consider channel {least_congested} ({channel_counts[least_congested]} networks)"
                )
            
            # Bluetooth Analysis
            bluetooth_devices = self.scan_bluetooth_devices()
            analysis["bluetooth"]["devices_found"] = len(bluetooth_devices)
            
            active_connections = sum(1 for device in bluetooth_devices if device.get("connected", False))
            analysis["bluetooth"]["active_connections"] = active_connections
            
        except Exception as e:
            analysis["error"] = str(e)
            
        return analysis
    
    def _is_cached(self, cache_key: str) -> bool:
        """Check if data is cached and still valid"""
        if cache_key not in self.scan_cache:
            return False
        
        last_scan = self.last_scan_time.get(cache_key, 0)
        return (time.time() - last_scan) < self.cache_duration
    
    def _rssi_to_strength(self, rssi: int) -> str:
        """Convert RSSI to human-readable strength"""
        if rssi >= -50:
            return "Excellent"
        elif rssi >= -60:
            return "Good"
        elif rssi >= -70:
            return "Fair"
        else:
            return "Poor"
    
    def _signal_to_strength(self, signal: int) -> str:
        """Convert signal level to strength description"""
        if signal >= 80:
            return "Excellent"
        elif signal >= 60:
            return "Good"
        elif signal >= 40:
            return "Fair"
        else:
            return "Poor"
    
    def _channel_to_band(self, channel: str) -> str:
        """Determine WiFi band from channel"""
        try:
            ch_num = int(channel)
            if ch_num <= 14:
                return "2.4GHz"
            else:
                return "5GHz"
        except:
            return "Unknown"
    
    def get_scanner_stats(self) -> Dict[str, Any]:
        """Get scanner statistics and capabilities"""
        return {
            "platform": self.platform,
            "wifi_scanning": self.platform in ["darwin", "linux", "windows"],
            "bluetooth_scanning": self.platform in ["darwin", "linux"],
            "gps_location": self.platform in ["darwin", "linux"],
            "cache_entries": len(self.scan_cache),
            "supported_features": self._get_supported_features()
        }
    
    def _get_supported_features(self) -> List[str]:
        """Get list of supported scanning features"""
        features = []
        
        if self.platform == "darwin":
            features.extend(["WiFi scanning", "Bluetooth discovery", "Location services"])
        elif self.platform == "linux":
            features.extend(["WiFi scanning", "Bluetooth discovery", "GPS location"])
        elif self.platform == "windows":
            features.extend(["WiFi scanning", "Basic Bluetooth"])
        
        return features

# Enhanced capabilities ideas
class AdvancedWirelessCapabilities:
    """Advanced wireless capabilities for M1K3"""
    
    @staticmethod
    def detect_device_types(wireless_scanner: WirelessScanner) -> Dict[str, List[str]]:
        """Detect types of devices in the environment"""
        device_types = {
            "smart_home": [],
            "mobile_devices": [],
            "computers": [],
            "iot_devices": [],
            "unknown": []
        }
        
        # WiFi network name analysis
        wifi_networks = wireless_scanner.scan_wifi_networks()
        for network in wifi_networks:
            ssid = network.get("ssid", "").lower()
            
            # Smart home devices
            if any(keyword in ssid for keyword in ["nest", "ring", "philips", "hue", "echo", "alexa", "google"]):
                device_types["smart_home"].append(network["ssid"])
            
            # IoT devices
            elif any(keyword in ssid for keyword in ["iot", "sensor", "cam", "thermostat"]):
                device_types["iot_devices"].append(network["ssid"])
            
            # Mobile hotspots
            elif any(keyword in ssid for keyword in ["iphone", "android", "mobile", "hotspot"]):
                device_types["mobile_devices"].append(network["ssid"])
            
            else:
                device_types["unknown"].append(network["ssid"])
        
        return device_types
    
    @staticmethod
    def security_analysis(wireless_scanner: WirelessScanner) -> Dict[str, Any]:
        """Analyze security of wireless environment"""
        wifi_networks = wireless_scanner.scan_wifi_networks()
        
        security_analysis = {
            "open_networks": [],
            "wep_networks": [],
            "weak_security": [],
            "strong_security": [],
            "recommendations": []
        }
        
        for network in wifi_networks:
            ssid = network.get("ssid", "Unknown")
            security = network.get("security", "").lower()
            
            if "none" in security or security == "":
                security_analysis["open_networks"].append(ssid)
            elif "wep" in security:
                security_analysis["wep_networks"].append(ssid)
            elif any(weak in security for weak in ["wps", "tkip"]):
                security_analysis["weak_security"].append(ssid)
            elif any(strong in security for strong in ["wpa3", "wpa2", "aes"]):
                security_analysis["strong_security"].append(ssid)
        
        # Generate recommendations
        if security_analysis["open_networks"]:
            security_analysis["recommendations"].append(
                f"Found {len(security_analysis['open_networks'])} open networks - avoid for sensitive data"
            )
        
        if security_analysis["wep_networks"]:
            security_analysis["recommendations"].append(
                f"Found {len(security_analysis['wep_networks'])} WEP networks - upgrade to WPA2/WPA3"
            )
        
        return security_analysis