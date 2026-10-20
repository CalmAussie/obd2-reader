package com.obdreader.app.session

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.obdreader.app.obd.DriveTrack
import com.obdreader.app.ui.OverlayRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns a recorded drive into an MP4 telemetry overlay for dashcam or GoPro footage: rpm, speed,
 * shift lights, throttle and a 30-second trace, at 30 frames a second, on a green screen to key
 * out (or black, for a Screen blend). Encoded on the phone with Android's own video encoder.
 */
object OverlayExporter {

    enum class Size(val label: String, val width: Int, val height: Int, val bitRate: Int) {
        HD1080("1080p (1920 × 1080)", 1920, 1080, 12_000_000),
        HD720("720p (1280 × 720), quicker", 1280, 720, 6_000_000),
    }

    const val FPS = 30

    /** Writes the overlay video to [out]. [onProgress] runs with 0..1 as frames are encoded. */
    suspend fun export(
        context: Context, drive: DriveTrack, out: File, background: Int, size: Size, redline: Float, rpmMax: Float,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val w = size.width; val h = size.height
        val renderer = OverlayRenderer(context, background, redline, rpmMax)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, size.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxing = false
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pixels = IntArray(w * h)
        val frames = (drive.duration * FPS).toInt().coerceAtLeast(1)
        val info = MediaCodec.BufferInfo()
        var frame = 0
        var inputDone = false
        try {
            while (true) {
                ensureActive()
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val pts = frame * 1_000_000L / FPS
                        if (frame >= frames) {
                            codec.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            renderer.draw(canvas, drive, frame.toDouble() / FPS)
                            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                            val image = codec.getInputImage(i) ?: error("The video encoder gave no input image")
                            fillYuv(image, pixels, w, h)
                            codec.queueInputBuffer(i, 0, w * h * 3 / 2, pts, 0)
                            frame++
                            if (frame % 15 == 0) withContext(Dispatchers.Main) { onProgress(frame.toFloat() / frames) }
                        }
                    }
                }
                var o = codec.dequeueOutputBuffer(info, 10_000)
                while (o != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    } else if (o >= 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxing) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buf, info)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            withContext(Dispatchers.Main) { onProgress(1f) }
                            return@withContext
                        }
                    }
                    o = codec.dequeueOutputBuffer(info, 0)
                }
            }
        } finally {
            try { codec.stop() } catch (ignored: Exception) {}
            codec.release()
            try { if (muxing) muxer.stop() } catch (ignored: Exception) {}
            muxer.release()
            bitmap.recycle()
        }
    }

    /** Copies the frame (ARGB) into the encoder's YUV 4:2:0 planes, whatever their layout. */
    private fun fillYuv(image: Image, argb: IntArray, w: Int, h: Int) {
        val planes = image.planes
        val y = planes[0]; val u = planes[1]; val v = planes[2]
        val yBuf = y.buffer; val uBuf = u.buffer; val vBuf = v.buffer
        val row = ByteArray(w)
        for (j in 0 until h) {
            val base = j * w
            for (i in 0 until w) {
                val c = argb[base + i]
                val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                row[i] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
            }
            if (y.pixelStride == 1) {
                yBuf.position(j * y.rowStride); yBuf.put(row, 0, w)
            } else {
                for (i in 0 until w) yBuf.put(j * y.rowStride + i * y.pixelStride, row[i])
            }
        }
        for (j in 0 until h / 2) {
            for (i in 0 until w / 2) {
                val c = argb[(j * 2) * w + i * 2]
                val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                uBuf.put(j * u.rowStride + i * u.pixelStride, (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte())
                vBuf.put(j * v.rowStride + i * v.pixelStride, (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte())
            }
        }
    }

    /** Puts a copy in Movies › OBD2 Reader, where video editors and the gallery find it (Android 10 and later). */
    fun saveToMovies(context: Context, file: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/OBD2 Reader")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
