FROM python:3.12-slim

# Install system dependencies + fonts
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    fonts-dejavu-core \
    fonts-liberation \
    fontconfig \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Install Google Font "Anton" (used for viral-style subtitles)
RUN mkdir -p /usr/share/fonts/truetype/google && \
    wget -q "https://fonts.google.com/download?family=Anton" -O /tmp/anton.zip && \
    unzip -o /tmp/anton.zip -d /usr/share/fonts/truetype/google/ && \
    rm /tmp/anton.zip && \
    # Also get Montserrat and Bebas Neue as alternatives
    wget -q "https://fonts.google.com/download?family=Montserrat" -O /tmp/montserrat.zip && \
    unzip -o /tmp/montserrat.zip -d /usr/share/fonts/truetype/google/ && \
    rm /tmp/montserrat.zip && \
    wget -q "https://fonts.google.com/download?family=Bebas+Neue" -O /tmp/bebas.zip && \
    unzip -o /tmp/bebas.zip -d /usr/share/fonts/truetype/google/ && \
    rm /tmp/bebas.zip && \
    fc-cache -fv

# Install yt-dlp
RUN pip install --no-cache-dir yt-dlp

WORKDIR /app

# Install CPU-only PyTorch FIRST (200MB vs 8GB GPU version)
RUN pip install --no-cache-dir torch torchaudio --index-url https://download.pytorch.org/whl/cpu

# Install remaining requirements
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy app code
COPY main.py .
COPY pipeline.py .
COPY app/ app/

RUN mkdir -p /tmp/viralclip_clips

EXPOSE 8000

CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-8000}"]
