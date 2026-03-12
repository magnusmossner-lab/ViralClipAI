FROM python:3.12-slim

# Install system dependencies + fonts + Node.js (required by yt-dlp for YouTube)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    fonts-dejavu-core \
    fonts-liberation \
    fontconfig \
    wget \
    unzip \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js (yt-dlp needs a JS runtime for YouTube extraction)
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y --no-install-recommends nodejs && \
    rm -rf /var/lib/apt/lists/*

# Install Google Fonts from GitHub (direct TTF download - more reliable than Google Fonts API)
RUN mkdir -p /usr/share/fonts/truetype/google && \
    wget -q "https://github.com/google/fonts/raw/main/ofl/anton/Anton-Regular.ttf" \
         -O /usr/share/fonts/truetype/google/Anton-Regular.ttf && \
    wget -q "https://github.com/google/fonts/raw/main/ofl/montserrat/Montserrat%5Bwght%5D.ttf" \
         -O /usr/share/fonts/truetype/google/Montserrat.ttf && \
    wget -q "https://github.com/google/fonts/raw/main/ofl/bebasneue/BebasNeue-Regular.ttf" \
         -O /usr/share/fonts/truetype/google/BebasNeue-Regular.ttf && \
    fc-cache -fv

# Install yt-dlp (latest version)
RUN pip install --no-cache-dir yt-dlp

WORKDIR /app

# Install requirements (no torch needed - faster-whisper uses ctranslate2 which is much lighter)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy app code
COPY main.py .
COPY pipeline.py .
COPY app/ app/

RUN mkdir -p /tmp/viralclip_clips /tmp/viralclip_jobs

EXPOSE 8000

# Increased timeouts:
# --timeout-keep-alive 600  = keep connections alive 10 minutes
# --timeout-graceful-shutdown 30 = graceful shutdown
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-8000} --timeout-keep-alive 600 --timeout-graceful-shutdown 30"]
