package com.company.stuble

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.company.stuble.data.RevisaoErrosManager
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson

class RevisaoErrosActivity : AppCompatActivity() {

    private lateinit var adapter: RevisaoErrosAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var txtVazia: TextView
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_revisao_erros)

        adapter = RevisaoErrosAdapter { pergunta ->
            val intent = Intent(this, RevisaoQuestaoActivity::class.java).apply {
                putExtra(RevisaoQuestaoActivity.EXTRA_PERGUNTA_JSON, gson.toJson(pergunta))
            }
            startActivity(intent)
        }

        recycler = findViewById(R.id.recyclerRevisaoErros)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        txtVazia = findViewById(R.id.txtRevisaoVazia)

        findViewById<MaterialButton>(R.id.btnVoltarRevisao)
            .setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        carregarLista()
    }

    private fun carregarLista() {
        val erros = RevisaoErrosManager.obterErros(this)

        adapter.atualizarLista(erros)
        recycler.isVisible = erros.isNotEmpty()
        txtVazia.isVisible = erros.isEmpty()
    }
}
