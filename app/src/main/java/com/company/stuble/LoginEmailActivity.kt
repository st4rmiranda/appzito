package com.company.stuble

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class LoginEmailActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var btnEntrar: Button
    private lateinit var btnVoltar: TextView
    private lateinit var progressLogin: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_email)

        auth = Firebase.auth
        database = Firebase.database.reference

        editEmail = findViewById(R.id.editEmailLogin)
        editSenha = findViewById(R.id.editSenhaLogin)
        btnEntrar = findViewById(R.id.btnEntrarEmail)
        btnVoltar = findViewById(R.id.btnVoltarLogin)
        progressLogin = findViewById(R.id.progressLogin)

        btnEntrar.setOnClickListener {
            if (isInternetAvailable()) {
                verificarEEntrar()
            } else {
                Toast.makeText(this, "Sem conexão. Verifique sua internet.", Toast.LENGTH_SHORT).show()
            }
        }

        btnVoltar.setOnClickListener {
            finish()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    private fun verificarEEntrar() {
        val email = editEmail.text.toString().trim()
        val senha = editSenha.text.toString()

        if (email.isEmpty()) {
            editEmail.error = "Digite seu e-mail"
            return
        }

        if (senha.isEmpty()) {
            editSenha.error = "Digite sua senha"
            return
        }

        btnEntrar.isEnabled = false
        btnEntrar.text = "Aguarde..."
        progressLogin.visibility = View.VISIBLE

        Log.d("LOGIN", "Verificando e-mail: $email")

        // Verifica se o e-mail existe no banco de dados (Realtime Database)
        database.child("usuarios").orderByChild("email").equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        Log.d("LOGIN", "E-mail encontrado no banco, procedendo ao login")
                        fazerLogin(email, senha)
                    } else {
                        Log.w("LOGIN", "E-mail não cadastrado")
                        restaurarBotao()
                        exibirAlertaEmailNaoCadastrado()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("LOGIN", "Erro no banco: ${error.message}")
                    restaurarBotao()
                    Toast.makeText(this@LoginEmailActivity, "Erro de conexão: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun fazerLogin(email: String, senha: String) {
        btnEntrar.text = "Entrando..."
        auth.signInWithEmailAndPassword(email, senha)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LOGIN", "Sucesso no Firebase Auth")
                    irParaHome()
                } else {
                    val exception = task.exception
                    Log.e("LOGIN", "Falha no Auth: ${exception?.message}")
                    restaurarBotao()
                    
                    val msg = when {
                        exception?.message?.contains("password", ignoreCase = true) == true -> "Senha incorreta."
                        exception?.message?.contains("network", ignoreCase = true) == true -> "Erro de rede. Verifique sua internet."
                        else -> "Erro ao entrar: ${exception?.message}"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun restaurarBotao() {
        btnEntrar.isEnabled = true
        btnEntrar.text = "ENTRAR"
        progressLogin.visibility = View.GONE
    }

    private fun exibirAlertaEmailNaoCadastrado() {
        AlertDialog.Builder(this)
            .setTitle("Conta não encontrada")
            .setMessage("O e-mail digitado não está cadastrado no Stuble. Deseja criar uma nova conta?")
            .setPositiveButton("CRIAR CONTA") { _, _ ->
                startActivity(Intent(this, CadastroActivity::class.java))
            }
            .setNegativeButton("TENTAR NOVAMENTE", null)
            .show()
    }

    private fun irParaHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
