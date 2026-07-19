package com.company.stuble

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.company.stuble.data.PerfilManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class ProfileFragment : Fragment() {

    companion object {
        private const val REQUEST_IMAGE_PICK = 100
        private const val PREFS_FOTO = "stuble_profile_photo"
    }

    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        carregarDadosUsuario(view)
        carregarEstatisticas(view)
        carregarPersonalizacao(view)
        configurarBotoes(view)
    }

    override fun onResume() {
        super.onResume()

        view?.let {
            carregarDadosUsuario(it)
            carregarEstatisticas(it)
            carregarPersonalizacao(it)
        }
    }

    private fun carregarDadosUsuario(view: View) {
        val user = auth.currentUser ?: return

        val nomeTextView = view.findViewById<TextView>(R.id.txtNomeUsuario)
        val emailTextView = view.findViewById<TextView>(R.id.txtEmailUsuario)
        val fotoPerfil = view.findViewById<ImageView>(R.id.imgProfile)

        nomeTextView.text =
            user.displayName?.takeIf { it.isNotBlank() } ?: "Estudante"

        emailTextView.text =
            user.email ?: "E-mail não encontrado"

        val fotoLocal = obterFotoLocalDoUsuario()

        Glide.with(this)
            .load(fotoLocal ?: user.photoUrl)
            .circleCrop()
            .placeholder(R.drawable.ic_user_placeholder)
            .error(R.drawable.ic_user_placeholder)
            .into(fotoPerfil)
    }

    private fun carregarEstatisticas(view: View) {
        val txtQuestoes = view.findViewById<TextView>(R.id.txtStatQuestoes)
        val txtAcertos = view.findViewById<TextView>(R.id.txtStatAcertos)
        val txtOfensiva = view.findViewById<TextView>(R.id.txtStatOfensiva)

        txtQuestoes.text =
            ProgressManagerAntigo.getTotalQuestoes(requireContext()).toString()

        txtAcertos.text =
            "${ProgressManagerAntigo.getTaxaAcerto(requireContext())}%"

        val questoesHoje =
            ProgressManagerAntigo.getQuestoesHoje(requireContext())

        txtOfensiva.text = when (questoesHoje) {
            0 -> "Comece sua missão de hoje"
            1 -> "1 questão concluída hoje"
            else -> "$questoesHoje questões concluídas hoje"
        }
    }

    private fun carregarPersonalizacao(view: View) {
        val perfil = PerfilManager.carregarPerfil(requireContext())

        val txtPerfilObjetivo =
            view.findViewById<TextView>(R.id.txtPerfilObjetivo)

        val txtPerfilNivel =
            view.findViewById<TextView>(R.id.txtPerfilNivel)

        val txtPerfilMaterias =
            view.findViewById<TextView>(R.id.txtPerfilMaterias)

        val objetivo = perfil.objetivo
            .takeIf { it.isNotBlank() }
            ?: "Defina seu objetivo"

        val curso = perfil.cursoDesejado
            .takeIf { it.isNotBlank() }

        val nivel = perfil.dificuldadePreferida
            .takeIf { it.isNotBlank() }
            ?: "Misto"

        val ano = perfil.anoEscolar
            .takeIf { it.isNotBlank() }

        val materias = perfil.materiasDificuldade
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?: "Nenhuma matéria selecionada"

        txtPerfilObjetivo.text =
            if (curso != null) "$objetivo • $curso" else objetivo

        txtPerfilNivel.text =
            if (ano != null) "$nivel • $ano" else nivel

        txtPerfilMaterias.text = materias
    }

    private fun configurarBotoes(view: View) {
        val user = auth.currentUser

        view.findViewById<CardView>(R.id.btnChangePhoto)
            .setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                startActivityForResult(intent, REQUEST_IMAGE_PICK)
            }

        view.findViewById<MaterialButton>(R.id.btnPersonalizarEstudos)
            .setOnClickListener {
                val intent = Intent(
                    requireContext(),
                    PersonalizacaoActivity::class.java
                )

                startActivity(intent)
            }

        view.findViewById<MaterialButton>(R.id.btnEditName)
            .setOnClickListener {
                abrirDialogAlterarNome(view)
            }

        view.findViewById<MaterialButton>(R.id.btnChangePassword)
            .setOnClickListener {
                val emailUsuario = user?.email

                if (emailUsuario.isNullOrBlank()) {
                    Toast.makeText(
                        requireContext(),
                        "Não foi possível localizar o e-mail da conta.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                auth.sendPasswordResetEmail(emailUsuario)
                    .addOnCompleteListener { task ->
                        val mensagem = if (task.isSuccessful) {
                            "Enviamos um link de redefinição para $emailUsuario"
                        } else {
                            "Não foi possível enviar o e-mail de redefinição."
                        }

                        Toast.makeText(
                            requireContext(),
                            mensagem,
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }

        view.findViewById<MaterialButton>(R.id.btnNotifications)
            .setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                ).apply {
                    putExtra(
                        Settings.EXTRA_APP_PACKAGE,
                        requireContext().packageName
                    )
                }

                startActivity(intent)
            }

        view.findViewById<MaterialButton>(R.id.btnLogout)
            .setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Sair da conta")
                    .setMessage("Deseja realmente encerrar sua sessão no Stuble?")
                    .setPositiveButton("Sair") { _, _ ->
                        auth.signOut()

                        val intent = Intent(
                            requireContext(),
                            LoginActivity::class.java
                        ).apply {
                            flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }

                        startActivity(intent)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
    }

    private fun abrirDialogAlterarNome(view: View) {
        val input = EditText(requireContext()).apply {
            hint = "Digite seu novo nome"
            setText(auth.currentUser?.displayName.orEmpty())
            setSelection(text.length)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Como podemos chamar você?")
            .setMessage("Esse nome aparecerá na Home e no seu perfil.")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val novoNome = input.text.toString().trim()

                if (novoNome.isBlank()) {
                    Toast.makeText(
                        requireContext(),
                        "Digite um nome válido.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                val alteracao = UserProfileChangeRequest.Builder()
                    .setDisplayName(novoNome)
                    .build()

                auth.currentUser
                    ?.updateProfile(alteracao)
                    ?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            view.findViewById<TextView>(
                                R.id.txtNomeUsuario
                            ).text = novoNome

                            Toast.makeText(
                                requireContext(),
                                "Nome atualizado! ✨",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Não foi possível atualizar o nome.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    @Deprecated("Mantido para compatibilidade com o fluxo atual do projeto.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode != REQUEST_IMAGE_PICK ||
            resultCode != Activity.RESULT_OK
        ) {
            return
        }

        val imageUri = data?.data ?: return

        try {
            requireContext().contentResolver.takePersistableUriPermission(
                imageUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Alguns aparelhos não oferecem permissão persistente.
        }

        salvarFotoLocalDoUsuario(imageUri)

        view?.findViewById<ImageView>(R.id.imgProfile)?.let {
            Glide.with(this)
                .load(imageUri)
                .circleCrop()
                .placeholder(R.drawable.ic_user_placeholder)
                .into(it)
        }

        Toast.makeText(
            requireContext(),
            "Foto atualizada! 📸",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun salvarFotoLocalDoUsuario(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return

        requireContext()
            .getSharedPreferences(PREFS_FOTO, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(uid, uri.toString())
            .apply()
    }

    private fun obterFotoLocalDoUsuario(): Uri? {
        val uid = auth.currentUser?.uid ?: return null

        val valor = requireContext()
            .getSharedPreferences(PREFS_FOTO, android.content.Context.MODE_PRIVATE)
            .getString(uid, null)
            ?: return null

        return Uri.parse(valor)
    }
}