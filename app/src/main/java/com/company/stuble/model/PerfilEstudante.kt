package com.company.stuble.model

data class PerfilEstudante(
    val anoEscolar: String = "",
    val areaInteresse: String = "",
    val cursoDesejado: String = "",
    val objetivo: String = "",
    val dificuldadePreferida: String = "Intermediário",
    val materiasDificuldade: Set<String> = emptySet()
)
