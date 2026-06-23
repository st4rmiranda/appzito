package com.company.stuble

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
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
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class ProfileFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        carregarDadosUsuario(view)
        carregarEstatisticas(view)
        configurarBotoes(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            carregarEstatisticas(it)
        }
    }

    private fun carregarDadosUsuario(view: View) {
        val user = auth.currentUser ?: return

        val nomeTextView = view.findViewById<TextView>(R.id.txtNomeUsuario)
        val emailTextView = view.findViewById<TextView>(R.id.txtEmailUsuario)
        val fotoPerfil = view.findViewById<ImageView>(R.id.imgProfile)

        nomeTextView.text =
            if (!user.displayName.isNullOrEmpty()) user.displayName else "Estudante"

        emailTextView.text = user.email ?: "E-mail não encontrado"

        Glide.with(this)
            .load(user.photoUrl)
            .circleCrop()
            .placeholder(R.drawable.ic_user_placeholder)
            .into(fotoPerfil)
    }

    private fun carregarEstatisticas(view: View) {
        val txtQuestoes = view.findViewById<TextView>(R.id.txtStatQuestoes)
        val txtAcertos = view.findViewById<TextView>(R.id.txtStatAcertos)

        txtQuestoes.text =
            ProgressManager.getTotalQuestoes(requireContext()).toString()

        txtAcertos.text =
            "${ProgressManager.getTaxaAcerto(requireContext())}%"

        val txtOfensiva = view.findViewById<TextView?>(R.id.txtStatOfensiva)

        txtOfensiva?.text =
            "${ProgressManager.getQuestoesHoje(requireContext())} questões hoje"
    }

    private fun configurarBotoes(view: View) {
        val user = auth.currentUser

        view.findViewById<CardView>(R.id.btnChangePhoto).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            startActivityForResult(intent, 100)
        }

        view.findViewById<MaterialButton>(R.id.btnEditName).setOnClickListener {
            abrirDialogAlterarNome(view)
        }

        view.findViewById<MaterialButton>(R.id.btnChangePassword).setOnClickListener {
            val emailUsuario = user?.email

            if (emailUsuario != null) {
                auth.sendPasswordResetEmail(emailUsuario)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(
                                context,
                                "E-mail de redefinição enviado para: $emailUsuario",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Erro ao solicitar redefinição de senha.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            }
        }

        view.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Sair da Conta")
                .setMessage("Deseja realmente encerrar sua sessão?")
                .setPositiveButton("Sair") { _, _ ->
                    auth.signOut()

                    Toast.makeText(
                        context,
                        "Sessão encerrada!",
                        Toast.LENGTH_SHORT
                    ).show()

                    val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

                    startActivity(intent)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun abrirDialogAlterarNome(view: View) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Alterar Nome")

        val input = EditText(requireContext())
        input.hint = "Digite seu novo nome"
        builder.setView(input)

        builder.setPositiveButton("Salvar") { _, _ ->
            val novoNome = input.text.toString().trim()

            if (novoNome.isNotEmpty()) {
                val userProfileChangeRequest = UserProfileChangeRequest.Builder()
                    .setDisplayName(novoNome)
                    .build()

                auth.currentUser?.updateProfile(userProfileChangeRequest)
                    ?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            view.findViewById<TextView>(R.id.txtNomeUsuario).text = novoNome

                            Toast.makeText(
                                context,
                                "Nome atualizado com sucesso!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Erro ao atualizar no servidor.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            }
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == android.app.Activity.RESULT_OK) {
            val imageUri = data?.data

            if (imageUri != null) {
                val fotoPerfil = view?.findViewById<ImageView>(R.id.imgProfile)

                if (fotoPerfil != null) {
                    Glide.with(this)
                        .load(imageUri)
                        .circleCrop()
                        .into(fotoPerfil)

                    Toast.makeText(
                        context,
                        "Foto atualizada no dispositivo!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}