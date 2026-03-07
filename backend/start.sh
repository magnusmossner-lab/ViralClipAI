#!/bin/bash
echo "Starting ViralClip AI v4.2 Server..."
pip install -r requirements.txt 2>/dev/null
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
