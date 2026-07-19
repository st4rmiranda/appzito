package com.company.stuble

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class CadastroActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var editNome: EditText
    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var btnCadastrar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)

        auth = Firebase.auth
        database = Firebase.database.reference

        editNome = findViewById(R.id.editNomeCadastro)
        editEmail = findViewById(R.id.editEmailCadastro)
        editSenha = findViewById(R.id.editSenhaCadastro)
        btnCadastrar = findViewById(R.id.btnFinalizarCadastro)

        btnCadastrar.setOnClickListener {
            cadastrarUsuario()
        }
    }

    private fun cadastrarUsuario() {
        val nome = editNome.text.toString().trim()
        val email = editEmail.text.toString().trim()
        val senha = editSenha.text.toString()

        when {
            nome.isBlank() -> {
                editNome.error = "Digite seu nome"
                editNome.requestFocus()
                return
            }

            email.isBlank() -> {
                editEmail.error = "Digite seu e-mail"
                editEmail.requestFocus()
                return
            }

            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                editEmail.error = "Digite um e-mail válido"
                editEmail.requestFocus()
                return
            }

            senha.isBlank() -> {
                editSenha.error = "Digite uma senha"
                editSenha.requestFocus()
                return
            }

            senha.length < 6 -> {
                editSenha.error = "A senha precisa ter pelo menos 6 caracteres"
                editSenha.requestFocus()
                return
            }
        }

        btnCadastrar.isEnabled = false
        btnCadastrar.text = "CADASTRANDO..."

        auth.createUserWithEmailAndPassword(email, senha)
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {
                    val usuarioAtual = auth.currentUser
                    val userId = usuarioAtual?.uid

                    if (userId == null) {
                        restaurarBotao()

                        Toast.makeText(
                            this,
                            "Não foi possível identificar o usuário.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnCompleteListener
                    }

                    val usuario = mapOf(
                        "nome" to nome,
                        "email" to email
                    )

                    database
                        .child("usuarios")
                        .child(userId)
                        .setValue(usuario)
                        .addOnSuccessListener {

                            Toast.makeText(
                                this,
                                "Conta criada! Agora personalize seus estudos.",
                                Toast.LENGTH_SHORT
                            ).show()

                            abrirPersonalizacao()
                        }
                        .addOnFailureListener { erro ->
                            restaurarBotao()

                            Toast.makeText(
                                this,
                                "Erro ao salvar os dados: ${erro.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                } else {
                    restaurarBotao()

                    Toast.makeText(
                        this,
                        "Erro ao criar a conta: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun abrirPersonalizacao() {
        val intent = Intent(
            this,
            PersonalizacaoActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
    }

    private fun restaurarBotao() {
        btnCadastrar.isEnabled = true
        btnCadastrar.text = "FINALIZAR CADASTRO"
    }
}