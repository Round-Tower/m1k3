# M1K3 Project URL Documentation

## Overview
This document provides a comprehensive catalog of all HTML pages in the M1K3 project, their purposes, dependencies, and routing structure.

## URL Structure & Routing Plan

### Proposed Route Architecture
```
/                    → Landing page (index.html)
/dashboard           → Avatar dashboard (m1k3.html)
/chat                → AI chat interface (PWA)
/settings            → Configuration panel
/debug               → Debug tools (dev only)
/test                → Test suite (dev only)
/docs                → Documentation
```

## Page Inventory

### 🚀 Core Application Pages

#### 1. Main Landing Page
- **File**: `/index.html`
- **URL**: `/`
- **Title**: M1K3 - Local AI Assistant with Avatar Dashboard
- **Purpose**: Main entry point, hero section with integrated avatar
- **CSS Dependencies**:
  - `styles/design-tokens.css` - Design system variables
  - `styles/utilities.css` - Utility classes
  - `styles/animations.css` - Animation keyframes
  - `styles/background-animations.css` - Ambient effects
  - `styles/avatar-component.css` - Avatar styling
  - `styles/hero-section.css` - Landing page hero
- **Status**: ✅ Production

#### 2. Avatar Dashboard
- **File**: `/m1k3.html`
- **URL**: `/dashboard`
- **Title**: M1K3 Avatar Dashboard
- **Purpose**: Real-time avatar visualization with WebSocket chat
- **CSS Dependencies**:
  - `styles/design-tokens.css`
  - `styles/utilities.css`
  - `styles/animations.css`
  - `styles/background-animations.css`
  - `styles/avatar-component.css`
  - `styles/dashboard-components.css` - Dashboard UI components
- **WebSocket**: Port 8081 for real-time updates
- **Status**: ✅ Production

#### 3. PWA Frontend
- **File**: `/pwa-deployment/frontend/index.html`
- **URL**: `/chat` (when deployed)
- **Title**: M1K3 - Local AI Assistant
- **Purpose**: Progressive Web App with ONNX model loading
- **Features**:
  - Device-adaptive AI loading
  - Offline support via Service Worker
  - Browser-based inference
- **CSS Dependencies**:
  - `src/styles.css` - PWA-specific styles
- **JS Dependencies**:
  - `src/device-detector.js`
  - `src/model-loader.js`
  - `src/chat-interface.js`
  - `src/rag-engine.js`
  - `src/app.js`
- **Status**: ✅ Production

### 🧪 Test & Development Pages

#### 4. Test Dashboard
- **File**: `/tests/test_dashboard.html`
- **URL**: `/test/dashboard`
- **Title**: M1K3 Test Dashboard
- **Purpose**: Testing dashboard components
- **Status**: 🔧 Development

#### 5. Sound System Test
- **File**: `/tests/test_sound_fix.html`
- **URL**: `/test/sound`
- **Title**: M1K3 Sound System Test
- **Purpose**: Audio system testing and debugging
- **Status**: 🔧 Development

#### 6. Engine Tests
- **File**: `/test_engine_minimal.html`
- **URL**: `/test/engine-minimal`
- **Title**: M1K3 Avatar - Gameboy Pixel Engine
- **Purpose**: Minimal avatar engine testing
- **Status**: 🔧 Development

- **File**: `/basic_engine_test.html`
- **URL**: `/test/engine-basic`
- **Title**: Basic Engine Test
- **Purpose**: Basic avatar rendering tests
- **Status**: 🔧 Development

#### 7. Debug Test
- **File**: `/debug_test.html`
- **URL**: `/debug/test`
- **Title**: Debug Test - M1K3 Avatar
- **Purpose**: Avatar debugging interface
- **Status**: 🔧 Development

#### 8. Minimal Tests
- **File**: `/minimal_test.html`
- **URL**: `/test/minimal`
- **Title**: Minimal Test
- **Purpose**: Minimal functionality testing
- **Status**: 🔧 Development

- **File**: `/simple_working_test.html`
- **URL**: `/test/simple`
- **Title**: Simple Working Avatar Test
- **Purpose**: Simple avatar implementation test
- **Status**: 🔧 Development

### 🛠️ Utility Pages

#### 9. Pixel Avatar Generator
- **File**: `/pixel_avatar_generator.html`
- **URL**: `/tools/avatar-generator`
- **Title**: Keeper 3D Avatar Generator with Emotions
- **Purpose**: Tool for generating avatar variations
- **Status**: 🔧 Utility

#### 10. WebSocket Logger
- **File**: `/websocket_logger.html`
- **URL**: `/tools/websocket-logger`
- **Title**: M1K3 WebSocket Logger
- **Purpose**: WebSocket connection debugging and logging
- **Status**: 🔧 Utility

### 📚 Documentation Pages

#### 11. PWA Architecture Documentation
- **File**: `/pwa-deployment/architecture.html`
- **URL**: `/docs/architecture`
- **Title**: M1K3 PWA Architecture - Interactive Documentation
- **Purpose**: Interactive architecture documentation
- **Status**: 📖 Documentation

### 🗄️ Deprecated/Backup Pages

#### 12. Old Dashboard Backup
- **File**: `/backup/m1k3-old.html`
- **Purpose**: Previous version of dashboard
- **Status**: ⚠️ Deprecated

#### 13. Old Index Backup
- **File**: `/backup/index-old.html`
- **Purpose**: Previous version of landing page
- **Status**: ⚠️ Deprecated

## CSS Architecture

### Design System Structure
```
/styles/
├── design-tokens.css      # Core design variables (colors, spacing, etc.)
├── utilities.css          # Utility classes and helpers
├── animations.css         # Animation keyframes (30+ animations)
├── background-animations.css  # Ambient background effects
├── avatar-component.css   # Avatar-specific styling
├── hero-section.css      # Landing page hero styling
└── dashboard-components.css   # Dashboard UI components
```

### Design Principles
- **Pure Black Foundation**: #000000 base with white transparency overlays
- **Modular Architecture**: Component-based CSS for maintainability
- **Mobile-First**: Touch-optimized with 44px minimum tap targets
- **Performance**: GPU-accelerated animations at 60fps
- **Accessibility**: Reduced motion support, high contrast modes

## JavaScript Architecture

### PWA Components
```
/pwa-deployment/frontend/src/
├── config.js              # Centralized configuration
├── device-detector.js     # Hardware capability detection
├── model-loader.js        # ONNX Runtime integration
├── chat-interface.js      # Chat UI management
├── rag-engine.js         # Knowledge retrieval
├── error-handler.js      # Global error handling
├── loading-indicators.js  # Loading states
└── app.js                # Main application orchestrator
```

## Routing Implementation Status

### Current State
- Static HTML files with no routing system
- Direct file access via filesystem paths
- No navigation between pages
- PWA uses Service Worker for offline caching

### Planned Implementation
1. **Hash-based routing** for compatibility
2. **Navigation component** using existing design system
3. **Route guards** for development/production modes
4. **Lazy loading** for performance
5. **History management** for browser navigation

## Server Configuration

### Development Server
```bash
# PWA Test Server
cd pwa-deployment
python test_server.py --port 9090

# Avatar Dashboard Server
python avatar_server.py --port 8080
```

### WebSocket Ports
- **8081**: Avatar emotion updates
- **8082**: Debug WebSocket logger

### Docker Deployment
```bash
# Simple deployment
docker-compose -f docker-compose.simple.yml up

# Production deployment
docker-compose up --build
```

## Next Steps

1. ✅ Document all existing pages (complete)
2. 🔄 Implement router.js module
3. 🔄 Create unified navigation
4. 🔄 Consolidate duplicate functionality
5. 🔄 Set up route guards for dev/prod
6. 🔄 Update Service Worker for SPA routing

## Notes

- All pages use the pure black design system with modular CSS
- Mobile-first responsive design throughout
- Avatar system integrated across multiple pages
- PWA provides offline-first functionality
- Test pages should be excluded from production builds