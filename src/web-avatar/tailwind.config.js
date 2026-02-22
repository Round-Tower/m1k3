/** @type {import('tailwindcss').Config} */
export default {
  content: ['./*.html', './src/**/*.{ts,js}'],
  theme: {
    extend: {
      colors: {
        'crt': {
          'white': '#E8E8E8',
          'bright': '#FFFFFF',
          'dim': '#A0A0A0',
          'dark': '#0A0A0A',
          'darker': '#050505',
        },
      },
      fontFamily: {
        'pixel': ['VT323', 'Courier New', 'monospace'],
        'mono': ['IBM Plex Mono', 'JetBrains Mono', 'monospace'],
      },
      boxShadow: {
        'crt': '0 0 10px rgba(255,255,255,0.3), 0 0 20px rgba(255,255,255,0.1)',
        'crt-sm': '0 0 5px rgba(255,255,255,0.2)',
        'crt-glow': '0 0 2px #fff, 0 0 8px rgba(255,255,255,0.4)',
      },
      animation: {
        'blink': 'blink 1s step-end infinite',
        'flicker': 'flicker 0.15s infinite',
        'pulse-slow': 'pulse 3s ease-in-out infinite',
      },
      keyframes: {
        blink: {
          '50%': { opacity: '0' },
        },
        flicker: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.98' },
        },
      },
    },
  },
  plugins: [require('daisyui')],
  daisyui: {
    themes: [{
      terminal: {
        'primary': '#E8E8E8',
        'secondary': '#A0A0A0',
        'accent': '#FFFFFF',
        'neutral': '#1a1a1a',
        'base-100': '#0A0A0A',
        'base-200': '#0F0F0F',
        'base-300': '#151515',
        'info': '#E8E8E8',
        'success': '#E8E8E8',
        'warning': '#E8E8E8',
        'error': '#E8E8E8',
      }
    }],
    darkTheme: 'terminal',
  },
}
