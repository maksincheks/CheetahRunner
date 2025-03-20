package com.example.cheetahrunner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

class Obstacle(
    private var x: Float, // Позиция по X
    private val y: Float, // Позиция по Y
    private val width: Float, // Ширина препятствия
    private val height: Float, // Высота препятствия
    private val context: Context // Контекст для доступа к ресурсам
) {
    private val paint = Paint().apply {
        color = Color.RED
    }
    var isScored: Boolean = false // Флаг для отслеживания начисления очков

    // Загрузка изображения из ресурсов
    private val obstacleImage: Bitmap? = ContextCompat.getDrawable(context, R.drawable.zabor)?.toBitmap()

    fun update(speed: Float) {
        x -= speed // Двигаем препятствие влево
    }

    fun draw(canvas: Canvas) {
        // Рисуем изображение вместо прямоугольника
        obstacleImage?.let {
            canvas.drawBitmap(it, x, y, paint)
        }
    }

    fun checkCollision(cheetahX: Float, cheetahY: Float, cheetahWidth: Float, cheetahHeight: Float): Boolean {
        return x < cheetahX + cheetahWidth &&
                x + width > cheetahX &&
                y < cheetahY + cheetahHeight &&
                y + height > cheetahY
    }

    fun isOffScreen(): Boolean {
        return x + width < 0 // Препятствие вышло за пределы экрана, если его правая граница меньше 0
    }

    fun isPassed(cheetahX: Float): Boolean {
        return x + width < cheetahX // Препятствие пройдено, если его правая граница меньше позиции гепарда
    }
}

// Helper function to convert Drawable to Bitmap
fun Drawable.toBitmap(): Bitmap {
    if (this is android.graphics.drawable.BitmapDrawable && this.bitmap != null) {
        return this.bitmap
    }

    val bitmap = Bitmap.createBitmap(
        this.intrinsicWidth.coerceAtLeast(1),
        this.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(bitmap)
    this.setBounds(0, 0, canvas.width, canvas.height)
    this.draw(canvas)
    return bitmap
}