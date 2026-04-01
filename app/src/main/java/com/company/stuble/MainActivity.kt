package com.company.stuble
import androidx.fragment.app.Fragment // Resolve o erro de Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView // Resolve o erro de BottomNavigationView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.database.database


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = Firebase.database

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        trocarTela(HomeFragment())

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
            .replace(R.id.fragment_container, fragment) // Coloca a tela no "espaço vazio"
            .commit()
    }
    
}