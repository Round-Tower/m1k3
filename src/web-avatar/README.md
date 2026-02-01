# M1K3 Web Avatar System

3D animated avatar system for M1K3 AI using THREE.js.

## Quick Start

```bash
npm install
npm run dev
# Open http://localhost:5174
```

## Pages

| Page | Description |
|------|-------------|
| `index.html` | Development test page with full controls |
| `demo.html` | Polished demo with M1K3 design system |
| `mcp-app.html` | Compact iframe for Claude Desktop MCP Apps |

## Architecture

```
src/
├── index.ts                    # Main exports
├── renderer/
│   ├── AvatarRenderer.ts       # THREE.js scene, lighting, camera
│   ├── GLBModelLoader.ts       # Load GLB models with metadata
│   └── CameraAutoFit.ts        # Auto-frame models
├── animation/
│   ├── AvatarState.ts          # Emotion/Activity types
│   ├── AnimationController.ts  # THREE.js AnimationMixer wrapper
│   └── AnimationIntrospector.ts # Fuzzy emotion→animation mapping
├── registry/
│   ├── ModelRegistry.ts        # 13 avatar models
│   └── ModelMetadata.ts        # Model config types
└── communication/
    ├── MCPAppBridge.ts         # postMessage for Claude Desktop
    └── WebSocketBridge.ts      # WebSocket for avatar_server.py
```

## Models (13 total)

### Quirky Series (FREE for commercial use)
- Colobus Monkey (default)
- Sparrow, Gecko, Herring, Muskrat, Pudu, Taipan, Inkfish
- Mask (static, procedural animation)

### Community (CC0)
- Fox, CesiumMan, BrainStem, Robot

## Avatar State

```typescript
interface AvatarState {
  emotion: 'NEUTRAL' | 'HAPPY' | 'SAD' | 'ANGRY' | 'SURPRISED' | 'LOVE' | 'THINKING' | 'SLEEPY' | 'EXCITED';
  activity: 'IDLE' | 'LISTENING' | 'THINKING' | 'GENERATING' | 'SPEAKING' | 'ERROR';
  intensity: number;  // 0-1
  message?: string;
}
```

## Usage

```typescript
import { AvatarRenderer, ModelRegistry } from './src/index.ts';

const renderer = new AvatarRenderer({
  container: '#avatar-container',
  assetsBasePath: '/',
  enableControls: true,  // Orbit controls
});

await renderer.loadModel('colobus');
renderer.start();

// Update state
renderer.setState({ emotion: 'HAPPY', intensity: 0.8 });
```

## Communication

### MCP App (Claude Desktop)
Uses `MCPAppBridge` for postMessage communication:
```typescript
bridge.onAvatarStateChange((state) => renderer.setState(state));
bridge.signalReady();
```

### Standalone/Direct
Uses `WebSocketBridge` for real-time updates:
```typescript
const bridge = new WebSocketBridge({ url: 'ws://localhost:8081' });
bridge.connect();
```

## Build

```bash
npm run build        # Library build (ES + UMD)
npm run typecheck    # TypeScript check
```

## Related

- `src/avatar-popover/` - Tauri standalone popover app
- `mcp_unified_server.py` - MCP server with avatar tools
- `scripts/avatar_server.py` - WebSocket avatar state server
