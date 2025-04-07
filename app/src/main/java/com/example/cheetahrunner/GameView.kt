package com.example.cheetahrunner

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.MotionEvent
import androidx.core.content.ContextCompat

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    // Звуковые эффекты
    private val backgroundPlayer: MediaPlayer by lazy {
        MediaPlayer.create(context, R.raw.fon_music).apply {
            isLooping = true
            setVolume(0.7f, 0.7f)
        }
    }
    private val jumpPlayer: MediaPlayer by lazy {
        MediaPlayer.create(context, R.raw.jump).apply {
            setVolume(1.0f, 1.0f)
        }
    }
    private val losePlayer: MediaPlayer by lazy {
        MediaPlayer.create(context, R.raw.lose).apply {
            setVolume(1.0f, 1.0f)
        }
    }

    // Графика
    private val backgroundBitmap: Bitmap? by lazy {
        ContextCompat.getDrawable(context, R.drawable.gepard_fon)?.let { drawable ->
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
            }
        }
    }
    private val paint = Paint().apply {
        color = Color.BLACK
        textSize = 50f
        isAntiAlias = true
    }
    private val backgroundRect = Rect()

    // Игровой процесс
    private var gameThread: GameThread? = null
    private var isGameOver = false
    private var score = 0
    private var gameSpeed = 12f
    private var lastUpdateTime = System.currentTimeMillis()
    private var lastObstacleGroupPassed = -1

    // Персонаж (увеличенные размеры)
    private var cheetahY = 0f
    private var isJumping = false
    private var jumpVelocity = 0f
    private val gravity = 1.4f
    private val initialJumpVelocity = -55f
    private val cheetahWidth = 250f  // Увеличено с 150f
    private val cheetahHeight = 250f // Увеличено с 150f
    private val cheetahX = 200f
    private val groundLevel: Float get() = height.toFloat() - 100f

    // Препятствия (увеличенные размеры)
    private val obstacles = mutableListOf<Obstacle>()
    private var obstacleSpawnTimer = 0L
    private val obstacleSpawnDelay = 1800L
    private var obstacleCount = 1
    private val obstacleWidth = 150f  // Увеличено с 90f
    private val obstacleHeight = 220f // Увеличено с 140f
    private val minDistanceInGroup = width.toFloat() * 0.25f
    private var nextObstacleIncreaseScore = 50
    private var currentObstacleGroup = 0

    // Анимация
    private val cheetahMovie: Movie? by lazy {
        resources.openRawResource(R.drawable.gepard).use {
            Movie.decodeStream(it)
        }
    }
    private var movieStartTime = 0L
    private val tempRect = RectF()

    // Пауза
    private var isPaused = false
    private var pausedBackground: Bitmap? = null
    private val pauseButtonRect = RectF()

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backgroundRect.set(0, 0, w, h)
        resetGame()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        gameThread = GameThread(holder, this).apply {
            setRunning(true)
            start()
        }
        backgroundPlayer.start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopGame()
    }

    private fun stopGame() {
        gameThread?.setRunning(false)
        gameThread = null
        backgroundPlayer.release()
        jumpPlayer.release()
        losePlayer.release()
    }

    fun update() {
        if (isGameOver || isPaused) return

        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastUpdateTime).coerceAtMost(50)
        lastUpdateTime = currentTime

        if (currentTime - lastUpdateTime > 15000) {
            gameSpeed += 0.5f
            lastUpdateTime = currentTime
        }

        if (score >= nextObstacleIncreaseScore) {
            obstacleCount++
            nextObstacleIncreaseScore += 50
        }

        if (isJumping) {
            cheetahY += jumpVelocity * deltaTime / 16f
            jumpVelocity += gravity * deltaTime / 16f

            if (cheetahY >= groundLevel - cheetahHeight) {
                cheetahY = groundLevel - cheetahHeight
                isJumping = false
                jumpVelocity = 0f
            }
        }

        if (currentTime - obstacleSpawnTimer > obstacleSpawnDelay) {
            spawnObstacle()
            obstacleSpawnTimer = currentTime
            currentObstacleGroup++
        }

        var groupPassed = false
        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next().apply { update(gameSpeed * deltaTime / 16f) }

            if (checkCollision(obstacle)) {
                gameOver()
                break
            }

            if (obstacle.isPassed(cheetahX) && !groupPassed && obstacle.groupId > lastObstacleGroupPassed) {
                score += 5
                lastObstacleGroupPassed = obstacle.groupId
                groupPassed = true
            }

            if (obstacle.isOffScreen()) iterator.remove()
        }
    }

    private fun checkCollision(obstacle: Obstacle): Boolean {
        tempRect.set(
            cheetahX + cheetahWidth * 0.2f,
            cheetahY + cheetahHeight * 0.2f,
            cheetahX + cheetahWidth * 0.8f,
            cheetahY + cheetahHeight * 0.8f
        )
        return tempRect.intersect(
            obstacle.x + obstacleWidth * 0.2f,
            obstacle.y + obstacleHeight * 0.2f,
            obstacle.x + obstacleWidth * 0.8f,
            obstacle.y + obstacleHeight * 0.8f
        )
    }

    private fun gameOver() {
        isGameOver = true
        backgroundPlayer.pause()
        losePlayer.start()
        showGameOverDialog()
    }

    private fun spawnObstacle() {
        val baseX = width.toFloat()
        repeat(obstacleCount) { i ->
            obstacles.add(Obstacle(
                x = baseX + i * (obstacleWidth + minDistanceInGroup),
                y = groundLevel - obstacleHeight,
                width = obstacleWidth,
                height = obstacleHeight,
                context = context,
                groupId = currentObstacleGroup
            ))
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (isPaused && pausedBackground != null) {
            canvas.drawBitmap(pausedBackground!!, null, backgroundRect, null)
        } else {
            backgroundBitmap?.let {
                canvas.drawBitmap(it, null, backgroundRect, null)
            } ?: run {
                canvas.drawColor(Color.rgb(144, 238, 144))
            }

            if (isPaused && pausedBackground == null) {
                pausedBackground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val tempCanvas = Canvas(pausedBackground!!)
                backgroundBitmap?.let {
                    tempCanvas.drawBitmap(it, null, backgroundRect, null)
                } ?: run {
                    tempCanvas.drawColor(Color.rgb(144, 238, 144))
                }
                obstacles.forEach { it.draw(tempCanvas) }
                drawCheetah(tempCanvas)
            }
        }

        if (!isPaused) {
            obstacles.forEach { it.draw(canvas) }
            drawCheetah(canvas)
        }

        // Кнопка паузы (левый верхний угол)
        pauseButtonRect.set(30f, 30f, 130f, 130f)
        paint.color = Color.argb(150, 100, 100, 100)
        canvas.drawRoundRect(pauseButtonRect, 20f, 20f, paint)
        paint.color = Color.WHITE
        paint.textSize = 60f
        canvas.drawText("II", pauseButtonRect.left + 35f, pauseButtonRect.top + 80f, paint)

        // Счет (правый верхний угол)
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = 2f
        paint.textSize = 50f
        val scoreText = "Очки: $score"
        val textWidth = paint.measureText(scoreText)
        canvas.drawText(scoreText, width - textWidth - 50f, 80f, paint) // Правая сторона с отступом 50px

        if (isGameOver) {
            paint.color = Color.RED
            paint.textSize = 100f
            canvas.drawText("ИГРА ОКОНЧЕНА", width / 2f - 250f, height / 2f, paint)
        }
    }

    private fun drawCheetah(canvas: Canvas) {
        cheetahMovie?.let { movie ->
            val now = System.currentTimeMillis()
            if (movieStartTime == 0L) movieStartTime = now

            movie.setTime(((now - movieStartTime) % movie.duration()).toInt())

            val scale = cheetahWidth / movie.width()
            canvas.save()
            canvas.scale(scale, scale)
            movie.draw(canvas, cheetahX / scale, cheetahY / scale)
            canvas.restore()
        } ?: run {
            paint.color = Color.BLACK
            canvas.drawRect(cheetahX, cheetahY, cheetahX + cheetahWidth, cheetahY + cheetahHeight, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isGameOver) return false

                if (pauseButtonRect.contains(event.x, event.y)) {
                    if (!isPaused) {
                        isPaused = true
                        backgroundPlayer.pause()
                        showPauseMenu()
                    }
                    return true
                } else if (!isPaused) {
                    jump()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun jump() {
        if (!isJumping) {
            isJumping = true
            jumpVelocity = initialJumpVelocity
            jumpPlayer.seekTo(0)
            jumpPlayer.start()
        }
    }

    private fun resetGame() {
        isGameOver = false
        isPaused = false
        pausedBackground = null
        score = 0
        obstacleCount = 1
        nextObstacleIncreaseScore = 50
        cheetahY = groundLevel - cheetahHeight
        obstacles.clear()
        gameSpeed = 12f
        movieStartTime = 0
        lastObstacleGroupPassed = -1
        currentObstacleGroup = 0
        backgroundPlayer.seekTo(0)
        backgroundPlayer.start()
    }

    private fun showPauseMenu() {
        (context as MainActivity).runOnUiThread {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 50, 50, 50)
                setBackgroundColor(Color.argb(220, 40, 40, 40))

                val title = TextView(context).apply {
                    text = "ПАУЗА"
                    setTextColor(Color.YELLOW)
                    textSize = 28f
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 0, 0, 30)
                }
                addView(title)

                val scoreText = TextView(context).apply {
                    text = "Ваш счет: $score"
                    setTextColor(Color.WHITE)
                    textSize = 24f
                    gravity = android.view.Gravity.CENTER
                }
                addView(scoreText)
            }

            AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton("Вернуться") { _, _ ->
                    postDelayed({
                        isPaused = false
                        pausedBackground = null
                        backgroundPlayer.start()
                    }, 2000)
                }
                .setNegativeButton("В меню") { _, _ ->
                    (context as MainActivity).finish()
                }
                .setCancelable(false)
                .create()
                .also { dialog ->
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.show()

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.argb(100, 0, 150, 0))
                    }
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.argb(100, 150, 0, 0))
                    }
                }
        }
    }

    private fun showGameOverDialog() {
        (context as MainActivity).runOnUiThread {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 50, 50, 50)
                setBackgroundColor(Color.argb(220, 40, 40, 40))

                val title = TextView(context).apply {
                    text = "ИГРА ОКОНЧЕНА"
                    setTextColor(Color.RED)
                    textSize = 28f
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 0, 0, 30)
                }
                addView(title)

                val scoreText = TextView(context).apply {
                    text = "Счёт: $score"
                    setTextColor(Color.WHITE)
                    textSize = 24f
                    gravity = android.view.Gravity.CENTER
                }
                addView(scoreText)
            }

            AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton("Заново") { _, _ ->
                    resetGame()
                    backgroundPlayer.start()
                }
                .setNegativeButton("В меню") { _, _ ->
                    (context as MainActivity).finish()
                }
                .setCancelable(false)
                .create()
                .also { dialog ->
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.show()

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.argb(100, 0, 150, 0))
                    }
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.argb(100, 150, 0, 0))
                    }
                }
        }
    }
}