package app.holdthatpose.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

/** Gallery I/O plus the small image helpers the capture pipeline needs. */
class PhotoStore(private val context: Context) {

    private val album = "Hold That Pose"

    fun hasSpace(minBytes: Long = 60L * 1024 * 1024): Boolean = runCatching {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        StatFs(dir.path).availableBytes > minBytes
    }.getOrDefault(true)

    /** Copies a JPEG file into the public gallery ("Pictures/Hold That Pose"). */
    fun saveToGallery(source: File, name: String): Uri? = saveToGallery(name) { out -> source.inputStream().use { it.copyTo(out) } }

    fun saveToGallery(bytes: ByteArray, name: String): Uri? = saveToGallery(name) { it.write(bytes) }

    private fun saveToGallery(name: String, write: (java.io.OutputStream) -> Unit): Uri? = runCatching {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$album")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use(write)
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), album).apply { mkdirs() }
            val file = File(dir, "$name.jpg")
            file.outputStream().use(write)
            MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("image/jpeg"), null)
            Uri.fromFile(file)
        }
    }.getOrNull()

    fun delete(uri: Uri): Boolean = runCatching {
        if (uri.scheme == "file") {
            val file = File(uri.path!!)
            val ok = file.delete()
            MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)
            ok
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
    }.getOrDefault(false)

    companion object {
        /**
         * Sharpness score = variance of a 3×3 Laplacian over a small grayscale copy.
         * Blurry or motion-smeared frames score low; used to keep the best of a mini-burst.
         */
        fun sharpness(file: File): Double {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, 640)
            }
            val bmp = BitmapFactory.decodeFile(file.path, opts) ?: return 0.0
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()
            val gray = FloatArray(w * h) { i ->
                val c = px[i]
                0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF)
            }
            var sum = 0.0
            var sumSq = 0.0
            var n = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val i = y * w + x
                    val lap = (4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - w] - gray[i + w]).toDouble()
                    sum += lap
                    sumSq += lap * lap
                    n++
                }
            }
            if (n == 0) return 0.0
            val mean = sum / n
            return sumSq / n - mean * mean
        }

        /** Upright JPEG of about [targetPixels] for sending to the Remote. */
        fun reviewCopy(file: File, targetPixels: Int = 2_000_000, quality: Int = 86): Triple<ByteArray, Int, Int>? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val scale = sqrt(bounds.outWidth.toDouble() * bounds.outHeight / targetPixels)
            val sample = Integer.highestOneBit(max(1, scale.toInt()))
            val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val rotation = ExifInterface(file.path).rotationDegrees
            val remaining = sqrt(decoded.width.toDouble() * decoded.height / targetPixels).coerceAtLeast(1.0)
            val matrix = Matrix().apply {
                postScale((1 / remaining).toFloat(), (1 / remaining).toFloat())
                postRotate(rotation.toFloat())
            }
            val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (upright !== decoded) decoded.recycle()
            val out = ByteArrayOutputStream()
            upright.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val result = Triple(out.toByteArray(), upright.width, upright.height)
            upright.recycle()
            result
        }.getOrNull()

        fun sampleSizeFor(w: Int, h: Int, maxSide: Int): Int {
            var s = 1
            while (max(w, h) / (s * 2) >= maxSide) s *= 2
            return s
        }

        fun decodeThumb(bytes: ByteArray, maxSide: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide) }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }

        fun fileName(takenAt: Long): String {
            val f = java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US)
            return "HoldThatPose_" + f.format(java.util.Date(takenAt))
        }

    }
}
