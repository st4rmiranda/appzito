package com.company.stuble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.company.stuble.model.Pergunta

class RevisaoErrosAdapter(
    private val aoClicar: (Pergunta) -> Unit
) : RecyclerView.Adapter<RevisaoErrosAdapter.ViewHolder>() {

    private var itens: List<Pergunta> = emptyList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtErroArea: TextView = view.findViewById(R.id.txtErroArea)
        val txtErroEnunciado: TextView = view.findViewById(R.id.txtErroEnunciado)
    }

    fun atualizarLista(novaLista: List<Pergunta>) {
        itens = novaLista
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_erro_revisao, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = itens.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pergunta = itens[position]

        holder.txtErroArea.text = pergunta.area
        holder.txtErroEnunciado.text = pergunta.pergunta

        holder.itemView.setOnClickListener {
            aoClicar(pergunta)
        }
    }
}
