package com.company.stuble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NotificationReceiver", "Recebendo broadcast para notificação")
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = NotificationHelper.CHANNEL_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Lembretes de Estudo",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val frases = listOf(
            "Vamos completar a sequência diária hoje?",
            "Pronto para arrasar nos estudos?",
            "Um pouco de cada vez leva à perfeição! 📚",
            "Hora de turbinar seu cérebro com o Stuble! ⚡",
            "Não pare agora! O sucesso está logo ali.",
            "Que tal resolver 5 questões rapidinho?",
            "Sua meta diária está te esperando! 🎯",
            "Estudar hoje é o presente para o seu futuro.",
            "O Mentor IA tem novas dicas para você! 🤖",
            "Bora bater seu recorde de ofensiva? 🔥",
            "Persistência é a chave. Vamos estudar?",
            "Transforme seus sonhos em planos hoje mesmo.",
            "Um passo de cada vez, mas nunca pare! 🚶‍♂️",
            "Seu cérebro precisa de um treino hoje! 🧠",
            "O conhecimento é a única coisa que ninguém te tira.",
            "Prepare-se para os vestibulares com o Stuble! 🎓",
            "Foco nos estudos! Você é capaz de tudo.",
            "Hora da revisão! Vamos nessa?",
            "Sua evolução começa com a primeira questão.",
            "Mantenha a constância! Estude agora. ✍️"
        )

        val fraseAleatoria = frases.random()

        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Stuble 📚")
            .setContentText(fraseAleatoria)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
