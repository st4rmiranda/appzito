package com.company.stuble

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
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
    private lateinit var editConfirmarSenha: EditText
    private lateinit var btnCadastrar: Button
    private lateinit var imgPerfil: ImageView
    private lateinit var btnEscolherFoto: View
    private lateinit var progressCadastro: ProgressBar

    private var imageUri: Uri? = null

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            imageUri = uri
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.e("CADASTRO", "Erro ao persistir permissão: ${e.message}")
            }
            imgPerfil.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)

        auth = Firebase.auth
        database = Firebase.database.reference

        editNome = findViewById(R.id.editNomeCadastro)
        editEmail = findViewById(R.id.editEmailCadastro)
        editSenha = findViewById(R.id.editSenhaCadastro)
        editConfirmarSenha = findViewById(R.id.editConfirmarSenhaCadastro)
        btnCadastrar = findViewById(R.id.btnFinalizarCadastro)
        imgPerfil = findViewById(R.id.imgPerfilCadastro)
        btnEscolherFoto = findViewById<View>(R.id.btnEscolherFoto)
        progressCadastro = findViewById(R.id.progressCadastro)

        btnEscolherFoto.setOnClickListener {
            selectImageLauncher.launch(arrayOf("image/*"))
        }

        btnCadastrar.setOnClickListener {
            if (isInternetAvailable()) {
                cadastrarUsuario()
            } else {
                Toast.makeText(this, "Sem conexão com a internet. Verifique seu Wi-Fi ou dados móveis.", Toast.LENGTH_SHORT).show()
            }
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

    private fun cadastrarUsuario() {
        val nome = editNome.text.toString().trim()
        val email = editEmail.text.toString().trim()
        val senha = editSenha.text.toString()
        val confirmarSenha = editConfirmarSenha.text.toString()

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

            confirmarSenha != senha -> {
                editConfirmarSenha.error = "As senhas não coincidem"
                editConfirmarSenha.requestFocus()
                return
            }
        }

        btnCadastrar.isEnabled = false
        btnCadastrar.text = "Aguarde..."
        progressCadastro.visibility = View.VISIBLE

        Log.d("CADASTRO", "Iniciando criação de usuário para: $email")

        auth.createUserWithEmailAndPassword(email, senha)
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {
                    Log.d("CADASTRO", "Usuário criado com sucesso no Auth")
                    val usuarioAtual = auth.currentUser
                    val userId = usuarioAtual?.uid

                    if (userId == null) {
                        restaurarBotao()
                        Toast.makeText(this, "Erro interno: ID do usuário nulo.", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }

                    // Atualiza o perfil do Firebase Auth com o nome e foto (se houver)
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(nome)
                        .apply {
                            imageUri?.let { setPhotoUri(it) }
                        }
                        .build()

                    usuarioAtual.updateProfile(profileUpdates).addOnCompleteListener { profileTask ->
                        if (profileTask.isSuccessful) {
                            Log.d("CADASTRO", "Profile display name atualizado")
                        }

                        // SALVA A FOTO LOCALMENTE (Para o ProfileFragment encontrar mesmo com perca de sessão)
                        imageUri?.let { uri ->
                            getSharedPreferences("stuble_profile_photo", MODE_PRIVATE)
                                .edit()
                                .putString(userId, uri.toString())
                                .apply()
                        }

                        val usuario = mutableMapOf(
                            "nome" to nome,
                            "email" to email,
                            "uid" to userId
                        )

                        imageUri?.let {
                            usuario["fotoUrl"] = it.toString()
                        }

                        database
                            .child("usuarios")
                            .child(userId)
                            .setValue(usuario)
                            .addOnSuccessListener {
                                Log.d("CADASTRO", "Dados salvos no Realtime Database")
                                Toast.makeText(this, "Bem-vindo ao Stuble, $nome! ✨", Toast.LENGTH_SHORT).show()
                                abrirPersonalizacao()
                            }
                            .addOnFailureListener { erro ->
                                Log.e("CADASTRO", "Erro ao salvar no banco: ${erro.message}")
                                restaurarBotao()
                                Toast.makeText(this, "Erro ao salvar dados no banco. Tente novamente.", Toast.LENGTH_LONG).show()
                            }
                    }

                } else {
                    val exception = task.exception
                    Log.e("CADASTRO", "Falha no Auth: ${exception?.message}")
                    restaurarBotao()

                    val mensagemErro = when {
                        exception?.message?.contains("timeout", ignoreCase = true) == true -> 
                            "Tempo de conexão esgotado. Verifique sua internet ou tente novamente em instantes."
                        exception?.message?.contains("network", ignoreCase = true) == true ->
                            "O Google não conseguiu validar sua conexão. Dica: abra o Chrome no emulador, acesse o google.com e tente cadastrar novamente aqui."
                        else -> "Não foi possível criar sua conta: ${exception?.message}"
                    }

                    Toast.makeText(this, mensagemErro, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun restaurarBotao() {
        btnCadastrar.isEnabled = true
        btnCadastrar.text = "CRIAR CONTA E CONTINUAR"
        progressCadastro.visibility = View.GONE
    }

    private fun abrirPersonalizacao() {
        val intent = Intent(this, PersonalizacaoActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
