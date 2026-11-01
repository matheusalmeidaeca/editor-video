package com.editorvideo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Mantém o processo vivo durante a exportação (serviço de primeiro plano) e mostra
 * a notificação com progresso e botão Cancelar. Quem exporta continua sendo o Transformer
 * da MainActivity; o serviço só impede o sistema de encerrar o app com a tela apagada.
 */
class ExportService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            onCancel?.invoke()
            return START_NOT_STICKY
        }
        ensureChannels(this)
        lastPercent = -1
        startForeground(
            NOTIF_PROGRESS,
            buildProgress(this, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_CANCEL = "com.editorvideo.CANCEL_EXPORT"
        private const val CH_PROGRESS = "export_progress"
        private const val CH_DONE = "export_done"
        private const val NOTIF_PROGRESS = 41
        private const val NOTIF_DONE = 42

        /** Chamado quando o usuário toca em Cancelar na notificação. */
        @Volatile
        var onCancel: (() -> Unit)? = null

        @Volatile
        private var lastPercent = -1

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ExportService::class.java))
        }

        /** Atualiza o progresso (só mexe na notificação quando o número muda). */
        fun progress(ctx: Context, percent: Int) {
            if (percent == lastPercent) return
            lastPercent = percent
            try {
                nm(ctx).notify(NOTIF_PROGRESS, buildProgress(ctx, percent))
            } catch (e: Exception) {
                // sem permissão de notificação: a exportação segue normalmente
            }
        }

        /** Encerra o serviço e deixa uma notificação final (pronto, erro ou cancelado). */
        fun finish(ctx: Context, text: String, openUri: Uri?) {
            onCancel = null
            ctx.stopService(Intent(ctx, ExportService::class.java))
            try {
                ensureChannels(ctx)
                nm(ctx).cancel(NOTIF_PROGRESS)
                val open = if (openUri != null) {
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(openUri, "video/mp4")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                }
                val pi = PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val n = NotificationCompat.Builder(ctx, CH_DONE)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Editor de Vídeo")
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
                nm(ctx).notify(NOTIF_DONE, n)
            } catch (e: Exception) {
                // ignora
            }
        }

        private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        private fun ensureChannels(ctx: Context) {
            val m = nm(ctx)
            m.createNotificationChannel(
                NotificationChannel(CH_PROGRESS, "Exportação em andamento", NotificationManager.IMPORTANCE_LOW)
            )
            m.createNotificationChannel(
                NotificationChannel(CH_DONE, "Exportação concluída", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        private fun buildProgress(ctx: Context, percent: Int): Notification {
            val cancel = PendingIntent.getService(
                ctx, 2,
                Intent(ctx, ExportService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val open = PendingIntent.getActivity(
                ctx, 3,
                ctx.packageManager.getLaunchIntentForPackage(ctx.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Builder(ctx, CH_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Exportando vídeo…")
                .setContentText("$percent%")
                .setProgress(100, percent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(0, "Cancelar", cancel)
                .build()
        }
    }
}
