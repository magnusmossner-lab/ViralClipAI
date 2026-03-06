FROM python:3.12-slim

# Install system dependencies including ffmpeg with all codecs
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# Install yt-dlp (latest)
RUN pip install --no-cache-dir yt-dlp

WORKDIR /app

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy app
COPY main.py .

# Create clips directory
RUN mkdir -p /tmp/viralclip_clips

EXPOSE 8000

# Use PORT env variable (Railway sets this dynamically)
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-8000}"]
