package com.company.stuble.com.company.stuble

data class Pergunta(
    val pergunta: String = "",
    val opcoes: List<String> = emptyList(),
    val correta: Int = 0,
    val explicacao: String = "",
    val area: String = "Linguagens e Códigos"
)