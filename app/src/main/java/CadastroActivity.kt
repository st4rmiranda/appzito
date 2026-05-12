package com.company.stuble // AJUSTE: Verifique se este é o seu pacote real

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

    // Inicializamos as variáveis do Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)

        // Instanciamos o Auth e o Database
        auth = Firebase.auth
        database = Firebase.database.reference

        val btnCadastrar = findViewById<Button>(R.id.btnFinalizarCadastro)

        btnCadastrar.setOnClickListener {
            cadastrarUsuario()
        }
    }

    private fun cadastrarUsuario() {
        val nome = findViewById<EditText>(R.id.editNomeCadastro).text.toString()
        val email = findViewById<EditText>(R.id.editEmailCadastro).text.toString()
        val senha = findViewById<EditText>(R.id.editSenhaCadastro).text.toString()

        if (nome.isEmpty() || email.isEmpty() || senha.isEmpty()) {
            Toast.makeText(this, "Preencha tudo!", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, senha)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    val usuario = mapOf("nome" to nome, "email" to email)

                    // 1. Primeiro salva no banco de dados
                    userId?.let {
                        database.child("usuarios").child(it).setValue(usuario)
                            .addOnSuccessListener {
                                // 2. SÓ MUDA DE TELA SE O BANCO CONFIRMAR O SALVAMENTO
                                Toast.makeText(this, "Cadastro OK!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this, MainActivity::class.java)
                                // Limpa a pilha de telas para não voltar ao cadastro ao clicar em "voltar"
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Erro no Database: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    // Se cair aqui, ele te diz exatamente por que não foi (ex: senha curta)
                    Toast.makeText(this, "Erro no Auth: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}