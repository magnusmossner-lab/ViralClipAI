package com.viralclipai.app.utils

import android.content.Context
import android.media.*
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Compresses videos > 100MB to 720p / 2Mbps before upload.
 * Uses only Android's built-in MediaCodec APIs (no extra libraries needed).
 */
object VideoCompressor {

    private const val TAG = "VideoCompressor"
    private const val MAX_SIZE_BYTES = 100L * 1024 * 1024  // 100 MB threshold
    private const val TARGET_BITRATE = 8_000_000           // 8 Mbps video (HD quality)
    private const val AUDIO_BITRATE = 192_000              // 192 kbps audio
    private const val TARGET_HEIGHT = 1080                 // 1080p max (HD)

    /**
     * Compresses [inputFile] if it's over 100MB.
     * Returns the compressed file (or original if small enough).
     * [onProgress] called with 0-100 during compression.
     */
    fun compress(
        context: Context,
        inputFile: File,
        onProgress: (Int) -> Unit
    ): File {
        val fileSize = inputFile.length()
        Log.d(TAG, "Input size: ${fileSize / 1024 / 1024} MB")

        if (fileSize <= MAX_SIZE_BYTES) {
            Log.d(TAG, "File small enough, skipping compression")
            onProgress(100)
            return inputFile
        }

        val outputFile = File(context.cacheDir, "compressed_${inputFile.name}")
        if (outputFile.exists()) outputFile.delete()

        return try {
            transcodeVideo(inputFile, outputFile, onProgress)
            Log.d(TAG, "Compressed to: ${outputFile.length() / 1024 / 1024} MB")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed, using original: ${e.message}")
            outputFile.delete()
            onProgress(100)
            inputFile
        }
    }

    private fun transcodeVideo(input: File, output: File, onProgress: (Int) -> Unit) {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") && videoTrackIndex < 0) {
                videoTrackIndex = i
                videoFormat = fmt
            } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                audioTrackIndex = i
                audioFormat = fmt
            }
        }

        if (videoTrackIndex < 0) throw Exception("No video track found")

        val srcVideoFmt = videoFormat!!
        val srcW = srcVideoFmt.getInteger(MediaFormat.KEY_WIDTH)
        val srcH = srcVideoFmt.getInteger(MediaFormat.KEY_HEIGHT)
        val duration = if (srcVideoFmt.containsKey(MediaFormat.KEY_DURATION))
            srcVideoFmt.getLong(MediaFormat.KEY_DURATION) else 0L

        // Scale down keeping aspect ratio
        val scale = if (srcH > TARGET_HEIGHT) TARGET_HEIGHT.toFloat() / srcH else 1f
        val dstW = ((srcW * scale) / 2).toInt() * 2  // must be even
        val dstH = ((srcH * scale) / 2).toInt() * 2

        Log.d(TAG, "Transcoding ${srcW}x${srcH} → ${dstW}x${dstH}")

        // --- Video decoder ---
        val decoder = MediaCodec.createDecoderByType(
            srcVideoFmt.getString(MediaFormat.KEY_MIME)!!
        )

        // --- Video encoder ---
        val encFmt = MediaFormat.createVideoFormat("video/avc", dstW, dstH).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }
        val encoder = MediaCodec.createEncoderByType("video/avc")
        encoder.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        val inputSurface = encoder.createInputSurface()
        decoder.configure(srcVideoFmt, inputSurface, null, 0)

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        encoder.start()
        decoder.start()

        val bufInfo = MediaCodec.BufferInfo()
        var muxerVideoTrack = -1
        var muxerAudioTrack = -1
        var muxerStarted = false
        var sawInputEOS = false
        var sawOutputEOS = false

        // Also copy audio track directly (no re-encode needed for audio)
        val audioExtractor = if (audioTrackIndex >= 0) {
            MediaExtractor().also { it.setDataSource(input.absolutePath) }
        } else null

        extractor.selectTrack(videoTrackIndex)

        // Process video
        while (!sawOutputEOS) {
            // Feed decoder
            if (!sawInputEOS) {
                val inIdx = decoder.dequeueInputBuffer(10_000L)
                if (inIdx >= 0) {
                    val buf = decoder.getInputBuffer(inIdx)!!
                    val sampleSize = extractor.readSampleData(buf, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, sampleSize,
                            extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // Poll encoder output
            val encIdx = encoder.dequeueOutputBuffer(bufInfo, 10_000L)
            when {
                encIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                        if (audioExtractor != null && audioFormat != null) {
                            muxerAudioTrack = muxer.addTrack(audioFormat)
                        }
                        muxer.start()
                        muxerStarted = true
                    }
                }
                encIdx >= 0 -> {
                    val encBuf = encoder.getOutputBuffer(encIdx)!!
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                        muxerStarted) {
                        muxer.writeSampleData(muxerVideoTrack, encBuf, bufInfo)
                        if (duration > 0) {
                            val pct = (bufInfo.presentationTimeUs * 100L / duration).toInt()
                                .coerceIn(0, 95)
                            onProgress(pct)
                        }
                    }
                    encoder.releaseOutputBuffer(encIdx, false)
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }
            }

            // Drain decoder → encoder surface
            val decIdx = decoder.dequeueOutputBuffer(bufInfo, 10_000L)
            if (decIdx >= 0) {
                val render = bufInfo.size > 0
                decoder.releaseOutputBuffer(decIdx, render)
            }
        }

        // Copy audio
        if (audioExtractor != null && audioTrackIndex >= 0 && muxerStarted && muxerAudioTrack >= 0) {
            audioExtractor.selectTrack(audioTrackIndex)
            val audioBuf = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val sz = audioExtractor.readSampleData(audioBuf, 0)
                if (sz < 0) break
                info.set(0, sz, audioExtractor.sampleTime, audioExtractor.sampleFlags)
                muxer.writeSampleData(muxerAudioTrack, audioBuf, info)
                audioExtractor.advance()
            }
            audioExtractor.release()
        }

        decoder.stop(); decoder.release()
        encoder.stop(); encoder.release()
        extractor.release()
        muxer.stop(); muxer.release()

        onProgress(100)
    }
}
