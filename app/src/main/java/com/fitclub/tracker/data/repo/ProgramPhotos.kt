package com.fitclub.tracker.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object ProgramPhotos {
    private const val MAX_BYTES = 20 * 1024 * 1024
    private val namePattern = Regex("[a-f0-9-]{36}\\.jpg")
    fun file(context: Context, name: String?): File? =
        name?.takeIf { namePattern.matches(it) }?.let { File(context.filesDir, "program_covers/$it") }
    fun thumbnail(context: Context, name: String?): Bitmap? = runCatching {
        file(context, name)?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }.getOrNull()

    suspend fun importPhoto(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("fitclub-cover-", ".tmp", context.cacheDir)
        var original: Bitmap? = null
        var rotated: Bitmap? = null
        var scaled: Bitmap? = null
        var target: File? = null
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Не удалось открыть изображение")
            input.use { source ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_BYTES) { "Выберите изображение размером до 20 МБ" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Этот файл не является поддерживаемым изображением" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1536) sample *= 2
            val bitmap = BitmapFactory.decodeFile(temporary.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("Не удалось прочитать изображение")
            original = bitmap
            val orientation = runCatching { ExifInterface(temporary.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                }
            }
            val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            rotated = upright
            val ratio = (1024f / maxOf(upright.width, upright.height)).coerceAtMost(1f)
            val normalized = Bitmap.createScaledBitmap(upright, (upright.width * ratio).toInt().coerceAtLeast(1),
                (upright.height * ratio).toInt().coerceAtLeast(1), true)
            scaled = normalized
            val name = UUID.randomUUID().toString() + ".jpg"
            val saved = file(context, name)!!
            target = saved
            saved.parentFile!!.mkdirs()
            saved.outputStream().use { require(normalized.compress(Bitmap.CompressFormat.JPEG, 88, it)) { "Не удалось сохранить фото" } }
            name
        } catch (error: Exception) {
            target?.delete()
            throw error
        } finally {
            temporary.delete()
            listOfNotNull(original, rotated, scaled).distinct().forEach { it.recycle() }
        }
    }
}
