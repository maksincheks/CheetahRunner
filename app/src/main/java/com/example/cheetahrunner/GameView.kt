package com.example.cheetahrunner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null
    private val paint = Paint()
    private var cheetahY: Float = 0f
    private var isJumping: Boolean = false
    private var jumpVelocity: Float = 0f
    private var score: Int = 0
    private val obstacles = mutableListOf<Obstacle>()
    private var obstacleSpawnTimer: Long = 0
    private val obstacleSpawnDelay: Long = 2000 // Задержка между появлением препятствий (2 секунды)
    private var isGameOver: Boolean = false
    private var gameSpeed: Float = 5f // Начальная скорость игры
    private val speedIncreaseInterval: Long = 10000 // Интервал увеличения скорости (10 секунд)
    private var lastSpeedIncreaseTime: Long = System.currentTimeMillis()
    private var lastUpdateTime: Long = System.currentTimeMillis() // Время последнего обновления

    // Физика прыжков
    private val gravity: Float = 0.8f // Гравитация (сила притяжения)
    private val initialJumpVelocity: Float = -30f // Начальная скорость прыжка

    // Константы для препятствий
    private val obstacleWidth: Float = 100f // Фиксированная ширина препятствия
    private val obstacleHeight: Float = 100f // Фиксированная высота препятствия
    private var obstacleCount: Int = 1 // Количество препятствий подряд

    init {
        holder.addCallback(this)
        paint.color = Color.BLACK
        paint.textSize = 50f
        isFocusable = true // Делаем View фокусируемой для обработки касаний
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetGame() // Инициализируем игру после получения реальных размеров экрана
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        gameThread = GameThread(holder, this)
        gameThread?.setRunning(true)
        gameThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        gameThread?.setRunning(false)
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        gameThread = null
    }

    fun update() {
        if (isGameOver) return

        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastUpdateTime // Время, прошедшее с последнего обновления
        lastUpdateTime = currentTime // Обновляем время последнего обновления

        // Увеличение скорости игры с течением времени
        if (currentTime - lastSpeedIncreaseTime > speedIncreaseInterval) {
            gameSpeed += 1f // Увеличиваем скорость игры
            lastSpeedIncreaseTime = currentTime
        }

        // Логика обновления игры
        if (isJumping) {
            cheetahY += jumpVelocity // Обновляем позицию по Y
            jumpVelocity += gravity // Применяем гравитацию

            // Принудительное приземление
            if (cheetahY >= height - 200f) {
                cheetahY = height - 200f
                isJumping = false
                jumpVelocity = 0f // Сбрасываем скорость
            }
        }

        // Увеличение количества препятствий каждые 65 очков
        if (score / 65 >= obstacleCount) {
            obstacleCount++
        }

        // Обновление препятствий
        if (currentTime - obstacleSpawnTimer > obstacleSpawnDelay) {
            spawnObstacle()
            obstacleSpawnTimer = currentTime
        }

        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()
            obstacle.update(gameSpeed) // Скорость движения препятствий

            // Проверка столкновения
            if (obstacle.checkCollision(200f, cheetahY, 100f, 100f)) {
                isGameOver = true
                break
            }

            // Проверка, перепрыгнул ли игрок препятствие
            if (obstacle.isPassed(200f) && !obstacle.isScored) {
                score += 5 // +5 очков за прыжок через препятствие
                obstacle.isScored = true
            }

            if (obstacle.isOffScreen()) {
                iterator.remove() // Удаляем препятствие, если оно вышло за пределы экрана
            }
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.drawColor(Color.WHITE)

        // Отрисовка счета
        paint.color = Color.BLACK
        canvas.drawText("Score: $score", 50f, 100f, paint)

        // Отрисовка персонажа
        paint.color = if (isGameOver) Color.RED else Color.BLACK
        canvas.drawRect(200f, cheetahY, 300f, cheetahY + 100f, paint)

        // Отрисовка препятствий
        for (obstacle in obstacles) {
            obstacle.draw(canvas)
        }

        // Отрисовка сообщения о завершении игры
        if (isGameOver) {
            paint.color = Color.RED
            paint.textSize = 100f
            canvas.drawText("Game Over", width / 4f, height / 2f, paint)
            paint.textSize = 50f
            canvas.drawText("Tap to Restart", width / 4f, height / 2f + 100f, paint)
        }
    }

    private fun jump() {
        if (!isJumping) {
            isJumping = true
            jumpVelocity = initialJumpVelocity // Устанавливаем начальную скорость прыжка
        }
    }

    private fun spawnObstacle() {
        // Создаем несколько препятствий подряд в зависимости от obstacleCount
        for (i in 0 until obstacleCount) {
            val obstacleX = width.toFloat() + i * obstacleWidth // Располагаем препятствия рядом
            val obstacleY = height - 100f - obstacleHeight // Нижняя граница препятствия на уровне персонажа
            val obstacle = Obstacle(obstacleX, obstacleY, obstacleWidth, obstacleHeight, context)
            obstacles.add(obstacle)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isGameOver) {
                    // Рестарт игры при нажатии на экран после завершения
                    resetGame()
                    return true
                }

                // Обычный прыжок при нажатии в любом месте экрана
                if (!isJumping) {
                    jump()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun resetGame() {
        isGameOver = false
        score = 0
        cheetahY = height - 200f // Персонаж внизу экрана
        obstacles.clear()
        obstacleSpawnTimer = System.currentTimeMillis()
        gameSpeed = 5f // Сброс скорости игры
        lastSpeedIncreaseTime = System.currentTimeMillis()
        obstacleCount = 1 // Сброс количества препятствий
    }
}