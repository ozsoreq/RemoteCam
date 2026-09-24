package app.afar.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.afar.MainActivity
import app.afar.R
import app.afar.net.Role

/**
 * Foreground service that keeps the Nearby link alive while shooting, so a notification,
 * a pulled-down shade or the Remote's screen turning off never drops the session.
 */
class SessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val role = intent?.getStringExtra(EXTRA_ROLE)?.let { r -> Role.entries.firstOrNull { it.name == r } } ?: Role.Camera
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(if (role == Role.Camera) R.string.notif_camera else R.string.notif_remote))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
            .onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "session"
        private const val NOTIFICATION_ID = 7
        private const val EXTRA_ROLE = "role"

        fun start(context: Context, role: Role) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SessionService::class.java).putExtra(EXTRA_ROLE, role.name),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionService::class.java))
        }
    }
}
