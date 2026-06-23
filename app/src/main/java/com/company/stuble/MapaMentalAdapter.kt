package com.company.stuble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MapaMentalAdapter(
    private val lista: List<TopicoMapa>
) : RecyclerView.Adapter<MapaMentalAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTituloTopico: TextView = view.findViewById(R.id.txtTituloTopico)
        val txtItensTopico: TextView = view.findViewById(R.id.txtItensTopico)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_topico_mapa, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val topico = lista[position]

        holder.txtTituloTopico.text = topico.titulo
        holder.txtItensTopico.text = topico.itens.joinToString(separator = "\n") {
            "• $it"
        }
    }
}