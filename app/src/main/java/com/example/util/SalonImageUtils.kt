package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

object SalonImageUtils {

    private val bitmapCache = object : LruCache<Int, ImageBitmap>(24) {}

    data class PresetSalonCover(
        val id: String,
        val title: String,
        val subtitle: String,
        val primaryColorHex: Int,
        val secondaryColorHex: Int,
        val accentColorHex: Int
    )

    val PRESET_COVERS = listOf(
        PresetSalonCover(
            id = "preset_luxury_gold",
            title = "Luxury Gold Studio",
            subtitle = "Warm Amber & Executive Chairs",
            primaryColorHex = 0xFF1E1B18.toInt(),
            secondaryColorHex = 0xFF3D2610.toInt(),
            accentColorHex = 0xFFF59E0B.toInt()
        ),
        PresetSalonCover(
            id = "preset_modern_fade",
            title = "Modern Fade Lounge",
            subtitle = "Neon Cyber & Crisp Lineups",
            primaryColorHex = 0xFF0F172A.toInt(),
            secondaryColorHex = 0xFF1E3A8A.toInt(),
            accentColorHex = 0xFF38BDF8.toInt()
        ),
        PresetSalonCover(
            id = "preset_royal_velvet",
            title = "Royal Velvet Parlour",
            subtitle = "Plush Interior & Spa Care",
            primaryColorHex = 0xFF1E1B4B.toInt(),
            secondaryColorHex = 0xFF4C1D95.toInt(),
            accentColorHex = 0xFFC084FC.toInt()
        ),
        PresetSalonCover(
            id = "preset_emerald_groom",
            title = "Emerald A/C Salon",
            subtitle = "Clean Sanitized Instant Chairs",
            primaryColorHex = 0xFF062E22.toInt(),
            secondaryColorHex = 0xFF064E3B.toInt(),
            accentColorHex = 0xFF10B981.toInt()
        )
    )

    /**
     * Reads an image from a gallery Uri, scales it down to maxWidth (default 600px),
     * compresses as JPEG (60% quality), and returns a Base64 data URL string suitable
     * for storing directly in a Firestore `salons` document without paid Firebase Storage.
     */
    fun encodeUriToBase64Jpeg(
        context: Context,
        uri: Uri,
        maxWidth: Int = 600,
        quality: Int = 60
    ): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat().coerceAtLeast(1f)
            val targetWidth = if (originalBitmap.width > maxWidth) maxWidth else originalBitmap.width
            val targetHeight = (targetWidth / ratio).toInt().coerceAtLeast(1)

            val scaledBitmap = if (targetWidth != originalBitmap.width) {
                Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            } else {
                originalBitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generates a stylized high-contrast salon cover photo as a Base64 JPEG data URL
     * so 1-tap preset cover selection persists directly in Firestore `salons/{salonId}`.
     */
    fun generatePresetCoverBase64(preset: PresetSalonCover): String {
        val width = 600
        val height = 280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(preset.primaryColorHex, preset.secondaryColorHex, 0xFF090D16.toInt()),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle studio mirror arches in background
        val archPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = preset.accentColorHex
            alpha = 40
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val archFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            alpha = 14
            style = Paint.Style.FILL
        }
        for (i in 0..2) {
            val left = 60f + i * 175f
            val rect = RectF(left, 36f, left + 125f, 220f)
            canvas.drawRoundRect(rect, 62f, 62f, archFillPaint)
            canvas.drawRoundRect(rect, 62f, 62f, archPaint)
        }

        // Glowing top ceiling light bar
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = preset.accentColorHex
            alpha = 90
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(40f, 14f, width - 40f, 22f), 4f, 4f, glowPaint)

        // Barber pole / chair silhouette accents at bottom
        val counterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F172A.toInt()
            alpha = 210
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 220f, width.toFloat(), height.toFloat(), counterPaint)

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    /**
     * Decodes a Base64 string or `data:image/jpeg;base64,...` data URL into a cached Compose ImageBitmap.
     */
    fun decodeBase64ToImageBitmap(rawString: String?): ImageBitmap? {
        if (rawString.isNullOrBlank()) return null
        val cacheKey = rawString.hashCode()
        bitmapCache.get(cacheKey)?.let { return it }

        return try {
            val cleanBase64 = if (rawString.contains(",")) {
                rawString.substringAfter(",")
            } else {
                rawString
            }
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val imageBitmap = bitmap.asImageBitmap()
            bitmapCache.put(cacheKey, imageBitmap)
            imageBitmap
        } catch (e: Exception) {
            null
        }
    }
}
