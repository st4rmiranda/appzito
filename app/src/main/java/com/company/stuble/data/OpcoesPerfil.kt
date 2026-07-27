package com.company.stuble.data

object OpcoesPerfil {

    const val NENHUMA_MATERIA = "Nenhuma matéria em especial"

    val anosEscolares = listOf(
        "9º ano do Ensino Fundamental",
        "1º ano do Ensino Médio",
        "2º ano do Ensino Médio",
        "3º ano do Ensino Médio",
        "Já concluí o Ensino Médio"
    )

    val cursosPorArea: LinkedHashMap<String, List<String>> = linkedMapOf(
        "Área da Saúde" to listOf(
            "Medicina",
            "Biomedicina",
            "Enfermagem",
            "Odontologia",
            "Farmácia",
            "Fisioterapia",
            "Medicina Veterinária",
            "Nutrição",
            "Psicologia"
        ),
        "Área da Tecnologia" to listOf(
            "Ciência da Computação",
            "Engenharia da Computação",
            "Engenharia de Software",
            "Sistemas de Informação",
            "Análise e Desenvolvimento de Sistemas",
            "Ciência de Dados",
            "Inteligência Artificial"
        ),
        "Área de Engenharias" to listOf(
            "Engenharia Civil",
            "Engenharia Mecânica",
            "Engenharia Elétrica",
            "Engenharia Química",
            "Engenharia de Produção",
            "Engenharia Ambiental",
            "Engenharia de Alimentos",
            "Engenharia Aeroespacial"
        ),
        "Área de Humanas" to listOf(
            "Direito",
            "Administração",
            "Pedagogia",
            "Relações Internacionais",
            "Jornalismo",
            "Publicidade e Propaganda",
            "História",
            "Geografia",
            "Letras",
            "Serviço Social"
        ),
        "Área de Exatas" to listOf(
            "Matemática",
            "Física",
            "Química",
            "Estatística",
            "Economia",
            "Ciências Contábeis",
            "Arquitetura e Urbanismo"
        ),
        "Área de Ciências Biológicas" to listOf(
            "Ciências Biológicas",
            "Biotecnologia",
            "Ecologia",
            "Agronomia",
            "Zootecnia",
            "Engenharia Florestal"
        ),
        "Ainda não sei" to listOf(
            "Ainda não sei qual curso quero"
        )
    )

    val objetivos = listOf(
        "ENEM",
        "Vestibular tradicional",
        "FUVEST",
        "UNICAMP",
        "Provas escolares",
        "Reforço e revisão",
        "Ainda estou decidindo"
    )

    val dificuldades = listOf(
        "Básico",
        "Intermediário",
        "Avançado",
        "Misto",
        "Adaptativo"
    )

    val materias = listOf(
        "Matemática",
        "Física",
        "Química",
        "Biologia",
        "Português",
        "Literatura",
        "Redação",
        "História",
        "Geografia",
        "Filosofia",
        "Sociologia",
        "Inglês",
        "Espanhol",
        NENHUMA_MATERIA
    )
}
