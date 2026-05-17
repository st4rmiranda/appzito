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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = auth.currentUser

        if (user != null) {
            val nomeTextView = view.findViewById<TextView>(R.id.txtNomeUsuario)
            val emailTextView = view.findViewById<TextView>(R.id.txtEmailUsuario)
            val fotoPerfil = view.findViewById<ImageView>(R.id.imgProfile)

            // Se o usuário não tiver displayName (comum em cadastro por e-mail novo), põe um texto padrão
            nomeTextView.text = if (!user.displayName.isNullOrEmpty()) user.displayName else "Estudante"
            emailTextView.text = user.email

            // Carrega a foto de perfil
            Glide.with(this)
                .load(user.photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_user_placeholder)
                .into(fotoPerfil)
        }

        // 1. CLIQUE PARA ALTERAR FOTO (Abre a galeria do celular)
        view.findViewById<CardView>(R.id.btnChangePhoto).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            startActivityForResult(intent, 100)
        }

        // 2. CLIQUE PARA ALTERAR NOME (Atualiza local e no Firebase Auth)
        view.findViewById<MaterialButton>(R.id.btnEditName).setOnClickListener {
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

                    // Salva a alteração no Firebase de verdade
                    user?.updateProfile(userProfileChangeRequest)
                        ?.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                view.findViewById<TextView>(R.id.txtNomeUsuario).text = novoNome
                                Toast.makeText(context, "Nome atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Erro ao atualizar no servidor.", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            }
            builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
            builder.show()
        }

        // 3. CLIQUE PARA REDEFINIR SENHA (Envia o e-mail oficial do Firebase)
        view.findViewById<MaterialButton>(R.id.btnChangePassword).setOnClickListener {
            val emailUsuario = user?.email
            if (emailUsuario != null) {
                auth.sendPasswordResetEmail(emailUsuario)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(context, "E-mail de redefinição enviado para: $emailUsuario", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Erro ao solicitar redefinição de senha.", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        // 4. CLIQUE PARA LOGOUT (Desconecta e manda o usuário de volta para a tela de Login)
        view.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            Toast.makeText(context, "Sessão encerrada!", Toast.LENGTH_SHORT).show()

            // Substitua 'LoginActivity::class.java' pelo nome real da sua tela de Login/Cadastro
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    // Método que recebe a imagem selecionada da galeria pelo usuário
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == android.app.Activity.RESULT_OK) {
            val imageUri = data?.data
            if (imageUri != null) {
                val fotoPerfil = view?.findViewById<ImageView>(R.id.imgProfile)
                if (fotoPerfil != null) {
                    // Atualiza a imagem na tela na mesma hora
                    Glide.with(this).load(imageUri).circleCrop().into(fotoPerfil)

                    // TODO: Futuramente, se quiser salvar na nuvem, você enviará essa 'imageUri'
                    // para o Firebase Storage e usará o 'user?.updateProfile' com a URL gerada.
                    Toast.makeText(context, "Foto atualizada no dispositivo!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}