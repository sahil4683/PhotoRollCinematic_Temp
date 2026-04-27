package com.photoroll.cinematic.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.photoroll.cinematic.model.CinematicStyle
import com.photoroll.cinematic.model.PhotoItem
import com.photoroll.cinematic.model.ScrollDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoEncoder {

    private const val VIDEO_WIDTH = 1280
    private const val VIDEO_HEIGHT = 720
    private const val FRAME_RATE = 30
    private const val IFRAME_INTERVAL = 1
    private const val BIT_RATE = 4_000_000
    private const val SECONDS_PER_PHOTO = 3
    private const val FRAMES_PER_PHOTO = FRAME_RATE * SECONDS_PER_PHOTO

    data class Progress(val percent: Int, val message: String)

    suspend fun encode(
        context: Context,
        photos: List<PhotoItem>,
        scrollDirection: ScrollDirection,
        cinematicStyle: CinematicStyle,
        onProgress: suspend (Progress) -> Unit
    ): Uri = withContext(Dispatchers.IO) {

        onProgress(Progress(5, "Loading photos..."))

        // Load and scale all bitmaps
        val bitmaps = photos.mapIndexed { i, photo ->
            onProgress(Progress(5 + (i * 20 / photos.size), "Loading photo ${i + 1}/${photos.size}..."))
            loadScaledBitmap(context, photo.uri)
        }

        onProgress(Progress(25, "Setting up encoder..."))

        // Prepare output file
        val outputFile = File(context.cacheDir, "cinematic_${System.currentTimeMillis()}.mp4")

        // Set up MediaCodec encoder
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        var presentationTimeUs = 0L
        val frameDurationUs = 1_000_000L / FRAME_RATE
        val totalFrames = photos.size * FRAMES_PER_PHOTO

        onProgress(Progress(30, "Encoding video..."))

        var frameCount = 0
        for ((photoIdx, bitmap) in bitmaps.withIndex()) {
            for (frameInPhoto in 0 until FRAMES_PER_PHOTO) {
                val progress = frameCount.toFloat() / totalFrames
                val t = frameInPhoto.toFloat() / FRAMES_PER_PHOTO  // 0.0 -> 1.0 within this photo

                // Apply cinematic transform
                val frame = applyEffect(bitmap, t, scrollDirection, cinematicStyle)

                // Feed frame to encoder
                val inputBufferIndex = codec.dequeueInputBuffer(10_000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                    inputBuffer.clear()
                    bitmapToYuv(frame, inputBuffer)
                    frame.recycle()
                    codec.queueInputBuffer(inputBufferIndex, 0, inputBuffer.limit(), presentationTimeUs, 0)
                    presentationTimeUs += frameDurationUs
                }

                // Drain encoder output
                drainEncoder(codec, muxer, bufferInfo, { idx ->
                    trackIndex = idx
                    muxer.start()
                    muxerStarted = true
                    trackIndex
                }, trackIndex, muxerStarted)

                frameCount++
                if (frameCount % 15 == 0) {
                    val encodingProgress = 30 + (progress * 55).toInt()
                    onProgress(Progress(encodingProgress, "Encoding photo ${photoIdx + 1}/${photos.size}..."))
                }
            }
            bitmap.recycle()
        }

        // Signal end of stream
        val inputBufferIndex = codec.dequeueInputBuffer(10_000)
        if (inputBufferIndex >= 0) {
            codec.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }

        // Drain remaining
        var endOfStream = false
        while (!endOfStream) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> endOfStream = true
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        endOfStream = true
                    }
                }
            }
        }

        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()

        onProgress(Progress(88, "Saving to gallery..."))

        // Save to MediaStore gallery
        val savedUri = saveToGallery(context, outputFile)
        outputFile.delete()

        onProgress(Progress(100, "Done!"))
        savedUri
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        onTrackAdded: (Int) -> Int,
        currentTrack: Int,
        muxerStarted: Boolean
    ) {
        var track = currentTrack
        var started = muxerStarted
        var tryAgain = false
        while (!tryAgain) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> tryAgain = true
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!started) {
                        track = onTrackAdded(muxer.addTrack(codec.outputFormat))
                        started = true
                    }
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && started) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(track, outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                }
            }
        }
    }

    private fun applyEffect(
        source: Bitmap,
        t: Float,           // 0.0 = start of this photo, 1.0 = end
        direction: ScrollDirection,
        style: CinematicStyle
    ): Bitmap {
        val out = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val matrix = Matrix()

        val scale: Float
        val tx: Float
        val ty: Float

        when (style) {
            CinematicStyle.SLOW_PAN -> {
                scale = 1.15f
                val maxPan = (source.width * scale - VIDEO_WIDTH) / 2f
                tx = when (direction) {
                    ScrollDirection.LEFT  -> lerp(-maxPan, maxPan, t)
                    ScrollDirection.RIGHT -> lerp(maxPan, -maxPan, t)
                    else -> 0f
                }
                ty = when (direction) {
                    ScrollDirection.UP   -> lerp(-maxPan, maxPan, t)
                    ScrollDirection.DOWN -> lerp(maxPan, -maxPan, t)
                    else -> 0f
                }
            }
            CinematicStyle.ZOOM_IN -> {
                scale = lerp(1.0f, 1.3f, t)
                tx = 0f; ty = 0f
            }
            CinematicStyle.KEN_BURNS -> {
                // Zoom + gentle pan combined
                scale = lerp(1.05f, 1.25f, easeInOut(t))
                val panAmount = VIDEO_WIDTH * 0.05f
                tx = when (direction) {
                    ScrollDirection.LEFT  -> lerp(panAmount, -panAmount, t)
                    ScrollDirection.RIGHT -> lerp(-panAmount, panAmount, t)
                    else -> 0f
                }
                ty = when (direction) {
                    ScrollDirection.UP   -> lerp(panAmount, -panAmount, t)
                    ScrollDirection.DOWN -> lerp(-panAmount, panAmount, t)
                    else -> 0f
                }
            }
            CinematicStyle.CUSTOM_DAVINCI -> {
                // Ease-in zoom with slight rotation drift
                scale = lerp(1.0f, 1.2f, easeInOut(t))
                val angle = lerp(-0.5f, 0.5f, t)
                matrix.postScale(scale, scale, VIDEO_WIDTH / 2f, VIDEO_HEIGHT / 2f)
                matrix.postRotate(angle, VIDEO_WIDTH / 2f, VIDEO_HEIGHT / 2f)
                canvas.drawBitmap(source, matrix, paint)
                return out
            }
        }

        // Center the scaled bitmap then apply pan
        val scaledW = source.width * scale
        val scaledH = source.height * scale
        val left = (VIDEO_WIDTH - scaledW) / 2f + tx
        val top = (VIDEO_HEIGHT - scaledH) / 2f + ty
        matrix.postScale(scale, scale)
        matrix.postTranslate(left, top)
        canvas.drawBitmap(source, matrix, paint)
        return out
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
    private fun easeInOut(t: Float) = (t * t * (3f - 2f * t)).coerceIn(0f, 1f)

    private fun loadScaledBitmap(context: Context, uri: Uri): Bitmap {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        val sampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, VIDEO_WIDTH * 2, VIDEO_HEIGHT * 2)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)

        // Scale to fill VIDEO_WIDTH x VIDEO_HEIGHT (center-crop)
        val scaleX = VIDEO_WIDTH.toFloat() / raw.width
        val scaleY = VIDEO_HEIGHT.toFloat() / raw.height
        val scale = maxOf(scaleX, scaleY)
        val scaledW = (raw.width * scale).toInt()
        val scaledH = (raw.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(raw, scaledW, scaledH, true)
        raw.recycle()
        val x = (scaledW - VIDEO_WIDTH) / 2
        val y = (scaledH - VIDEO_HEIGHT) / 2
        val cropped = Bitmap.createBitmap(scaled, x, y, VIDEO_WIDTH, VIDEO_HEIGHT)
        if (cropped !== scaled) scaled.recycle()
        return cropped.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun calculateSampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        var size = 1
        if (h > reqH || w > reqW) {
            val halfH = h / 2
            val halfW = w / 2
            while ((halfH / size) >= reqH && (halfW / size) >= reqW) size *= 2
        }
        return size
    }

    private fun bitmapToYuv(bitmap: Bitmap, buffer: ByteBuffer) {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)

        // Y plane
        for (j in 0 until h) {
            for (i in 0 until w) {
                val px = argb[j * w + i]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                buffer.put(y.toByte())
            }
        }

        // UV plane (NV12 interleaved)
        for (j in 0 until h / 2) {
            for (i in 0 until w / 2) {
                val px = argb[(j * 2) * w + (i * 2)]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                buffer.put(u.toByte())
                buffer.put(v.toByte())
            }
        }
    }

    private fun saveToGallery(context: Context, file: File): Uri {
        val fileName = "PhotoRollCinematic_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoRollCinematic")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!

        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        return uri
    }
}
