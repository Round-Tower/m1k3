# M1K3 Avatar System

Real-time avatar visualization system with WebSocket-based communication and emotion tracking.

## Architecture

### Core Components
- **avatar_server.py**: WebSocket server for real-time avatar communication
- **avatar_controller.py**: Avatar state management and emotion processing

### Communication Protocol
The avatar system uses a custom WebSocket protocol for real-time updates:

```javascript
{
  "type": "emotion_update",
  "emotion": "happy",
  "intensity": 0.8,
  "timestamp": 1234567890
}
```

## Emotion System

### 8 Core Emotions
- Happy, Sad, Angry, Surprised, Love, Thinking, Sleepy, Excited
- Each emotion supports intensity levels (0-100)
- Smooth transitions between emotional states

### Avatar Styles
- Robot, Organic, Crystal, Ghost, Energy, Cute
- Style-specific rendering and animation characteristics
- Mobile-first responsive design

## Design Decisions

### WebSocket Architecture
Real-time communication enables:
- Live emotion updates during AI responses
- Synchronized visual feedback with voice synthesis
- Multi-device access across local network

### Mobile-First Design
- Progressive Web App (PWA) capabilities
- Touch-optimized controls
- Responsive layouts for all screen sizes

### Emotion Intelligence
- Content-aware emotion detection
- Contextual emotional responses
- Smooth interpolation between states

## Technical Implementation

### Performance Optimization
- Efficient WebSocket message batching
- Minimal DOM manipulation for smooth animations
- Lazy-loaded avatar assets

### Network Architecture
- Auto-discovery on local network
- Graceful degradation when avatar server unavailable
- Reconnection logic for network interruptions