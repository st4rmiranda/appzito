package com.company.stuble

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.button.MaterialButton

class MissionCompleteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mission_complete)

        val trophyAnimation =
            findViewById<LottieAnimationView>(R.id.animationTrophy)

        trophyAnimation.playAnimation()

        val btnVoltarInicio =
            findViewById<MaterialButton>(R.id.btnVoltarInicio)

        btnVoltarInicio.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

            startActivity(intent)
            finish()
        }
    }
}