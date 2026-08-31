package com.company.stuble

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class MapaMentalAdapter(
    private val topicos: List<TopicoMapa>
) : RecyclerView.Adapter<MapaMentalAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val titulo: TextView =
            view.findViewById(R.id.txtTituloTopico)

        val itens: TextView =
            view.findViewById(R.id.txtItensTopico)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_topico_mapa,
                parent,
                false
            )

        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val topico = topicos[position]

        holder.titulo.text = topico.titulo

        holder.itens.text =
            topico.itens.joinToString("\n") {
                "• $it"
            }
    }

    override fun getItemCount(): Int {
        return topicos.size
    }
}