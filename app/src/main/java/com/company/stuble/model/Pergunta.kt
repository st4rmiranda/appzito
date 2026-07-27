package com.company.stuble.model

data class Pergunta(
    val pergunta: String = "",
    val opcoes: List<String> = emptyList(),
    val correta: Int = 0,
    val explicacao: String = "",
    val area: String = "Linguagens e Códigos"
)
