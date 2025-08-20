#!/usr/bin/env python3
"""
M1K3 - Clean Startup Script
Suppresses warnings and starts M1K3 with PlayStation 1 retro voice
"""

import os
import sys
import warnings

# Suppress compatibility warnings for cleaner output
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)
os.environ['PYTHONWARNINGS'] = 'ignore'

# Import and run CLI
from cli import main

if __name__ == "__main__":
    sys.exit(main())