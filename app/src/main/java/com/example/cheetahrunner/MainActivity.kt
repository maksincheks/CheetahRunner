package com.example.cheetahrunner

import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = GameView(this)
        setContentView(gameView)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Передаем событие касания в GameView
        return gameView.onTouchEvent(event) || super.onTouchEvent(event)
    }
}