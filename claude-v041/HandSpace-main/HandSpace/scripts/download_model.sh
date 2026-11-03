#!/usr/bin/env bash
# Baixa o modelo MediaPipe Hand Landmarker para os assets do app.
# Execute uma vez a partir da raiz do projeto: ./scripts/download_model.sh
set -euo pipefail

URL="https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/hand_landmarker.task"

mkdir -p "$(dirname "$DEST")"
curl -fL --retry 3 "$URL" -o "$DEST"
echo "Modelo salvo em: $DEST"
