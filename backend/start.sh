#!/bin/bash
echo "Starting ViralClip AI Server..."
pip install -r requirements.txt 2>/dev/null
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
