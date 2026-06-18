package com.company.stuble

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var tryCount = 0

        // Se der erro 503 (ou 408/502), tenta novamente até o limite estipulado
        while (!response.isSuccessful && response.code == 503 && tryCount < maxRetries) {
            tryCount++
            // Espera um tempo progressivo (1s na primeira, 2s na segunda...) antes de tentar de novo
            val backoffTime = (tryCount * 1000).toLong()
            try {
                Thread.sleep(backoffTime)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException(e)
            }

            // Fecha a resposta anterior para não vazar memória e tenta de novo
            response.close()
            response = chain.proceed(request)
        }

        return response
    }
}