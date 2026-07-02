#!/bin/bash
# Download free animated 3D models for web-avatar
# All models are CC0 or CC-BY licensed

set -e

MODELS_DIR="$(dirname "$0")/../src/web-avatar/public/models"
mkdir -p "$MODELS_DIR"

echo "=================================================="
echo "  M1K3 Avatar Model Downloader"
echo "=================================================="
echo ""
echo "This script sets up directories and provides download instructions"
echo "for free animated 3D models (GLB format)."
echo ""

# Create directories
mkdir -p "$MODELS_DIR/kenney"
mkdir -p "$MODELS_DIR/quaternius"
mkdir -p "$MODELS_DIR/polypizza"

echo "Created model directories in: $MODELS_DIR"
echo ""

# ============================================================================
# KENNEY - Animated Characters 2
# ============================================================================
echo "--- KENNEY ANIMATED CHARACTERS ---"
echo "License: CC0 (Public Domain)"
echo "URL: https://kenney.nl/assets/animated-characters-2"
echo ""
echo "Manual download:"
echo "  1. Visit: https://kenney.nl/assets/animated-characters-2"
echo "  2. Click 'Download' button"
echo "  3. Extract ZIP to: $MODELS_DIR/kenney/"
echo "  4. GLB files are in: Models/GLTF format (.glb)/"
echo ""

# ============================================================================
# QUATERNIUS - LowPoly Robot
# ============================================================================
echo "--- QUATERNIUS LOWPOLY ROBOT ---"
echo "License: CC0 (Public Domain)"
echo "URL: https://quaternius.itch.io/lowpoly-robot"
echo ""
echo "Manual download:"
echo "  1. Visit: https://quaternius.itch.io/lowpoly-robot"
echo "  2. Click 'Download Now' (pay what you want, \$0 OK)"
echo "  3. Extract ZIP to: $MODELS_DIR/quaternius/"
echo ""

# ============================================================================
# POLY PIZZA - Various animated models
# ============================================================================
echo "--- POLY PIZZA (Animated Robot) ---"
echo "License: CC0 (Public Domain)"
echo "URL: https://poly.pizza/m/QCm7qe9uNJ"
echo ""
echo "Manual download:"
echo "  1. Visit: https://poly.pizza/m/QCm7qe9uNJ"
echo "  2. Click download icon (arrow)"
echo "  3. Select GLB format"
echo "  4. Save to: $MODELS_DIR/polypizza/"
echo ""

# ============================================================================
# SKETCHFAB - Free animated models
# ============================================================================
echo "--- SKETCHFAB (Account Required) ---"
echo "License: Varies (filter by CC0/CC-BY)"
echo "Search: https://sketchfab.com/search?features=downloadable&licenses=cc0&sort_by=-likeCount&type=models"
echo ""
echo "Good picks:"
echo "  - Cute Robot Companion: https://skfb.ly/6ZQZW"
echo "  - Low Poly Animated People: search 'animated character lowpoly'"
echo ""

# ============================================================================
# Existing Quirky Series (already linked)
# ============================================================================
echo "--- QUIRKY SERIES (Already Installed) ---"
echo "Location: $MODELS_DIR/*.glb (symlinked)"
echo "Models: Colobus, Sparrow, Gecko, Herring, Muskrat, Pudu, Taipan, Inkfish, Mask"
echo ""

echo "=================================================="
echo "After downloading, register new models in:"
echo "  src/web-avatar/src/registry/ModelRegistry.ts"
echo "=================================================="
