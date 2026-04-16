package com.company.stuble

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Firebase
import com.google.firebase.database.database

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. ESSA LINHA É OBRIGATÓRIA: Ela liga o Kotlin ao seu activity_main.xml
        setContentView(R.layout.activity_main)

        // Inicializa o Firebase (opcional se já estiver no Application)
        val database = Firebase.database

        // 2. Agora o findViewById vai funcionar, pois o layout já foi carregado acima
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Define a tela inicial padrão
        if (savedInstanceState == null) {
            trocarTela(HomeFragment())
        }

        // Configura os cliques no menu
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    trocarTela(HomeFragment())
                    true
                }
                R.id.nav_search -> {
                    trocarTela(SearchFragment())
                    true
                }
                R.id.nav_profile -> {
                    trocarTela(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun trocarTela(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}