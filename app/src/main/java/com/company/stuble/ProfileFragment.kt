package com.company.stuble

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?

    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false

        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            // IDs atualizados conforme o seu XML
            val nomeTextView = view.findViewById<TextView>(R.id.txtNomeUsuario)
            val emailTextView = view.findViewById<TextView>(R.id.txtEmailUsuario)
            val fotoPerfil = view.findViewById<ImageView>(R.id.imgProfile)

            nomeTextView.text = user.displayName
            emailTextView.text = user.email

            // Carrega a foto do Google usando o Glide
            Glide.with(this)
                .load(user.photoUrl)
                .circleCrop() // Deixa a imagem redonda se o placeholder não for
                .placeholder(R.drawable.ic_user_placeholder)
                .into(fotoPerfil)
        }
    }
}