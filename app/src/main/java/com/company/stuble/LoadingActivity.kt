package com.company.stuble

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class LoadingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        Handler(Looper.getMainLooper()).postDelayed({

            val intent = Intent(this, QuizActivity::class.java)

            val competenciaFiltro =
                getIntent().getStringExtra("COMPETENCIA_FILTRO")

            val ehTreinoLivre =
                getIntent().getBooleanExtra("EH_TREINO_LIVRE", false)

            if (competenciaFiltro != null) {
                intent.putExtra("COMPETENCIA_FILTRO", competenciaFiltro)
            }

            intent.putExtra("EH_TREINO_LIVRE", ehTreinoLivre)

            startActivity(intent)
            finish()

        }, 2500)
    }
}