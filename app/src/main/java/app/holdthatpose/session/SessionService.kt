package app.holdthatpose.session

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
import app.holdthatpose.MainActivity
import app.holdthatpose.PoseApp
import app.holdthatpose.R
import app.holdthatpose.net.Role

/**
 * Foreground service that keeps the Nearby link alive while shooting, so a notification,
 * a pulled-down shade or the Remote's screen turning off never drops the session.
 *
 * On the Camera the notification says who is watching ("LIVE · name") and has a Stop action.
 */
class SessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val session = (application as PoseApp).cameraSession
            if (session.isRunning) {
                session.endSession("Stopped on the Camera")
            } else {
                stopSelf()
            }
            return START_NOT_STICKY
        }
        val role = roleOf(intent)
        val notification = build(this, role, intent?.getStringExtra(EXTRA_TEXT))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
            .onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "session"
        private const val NOTIFICATION_ID = 7
        private const val EXTRA_ROLE = "role"
        private const val EXTRA_TEXT = "text"
        private const val ACTION_STOP = "app.holdthatpose.action.STOP"

        @Volatile private var runningRole: Role? = null

        private fun roleOf(intent: Intent?): Role =
            intent?.getStringExtra(EXTRA_ROLE)?.let { r -> Role.entries.firstOrNull { it.name == r } } ?: Role.Camera

        private fun build(context: Context, role: Role, text: String?): Notification {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                    },
                )
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(
                    text ?: context.getString(if (role == Role.Camera) R.string.notif_camera else R.string.notif_remote),
                )
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
            if (role == Role.Camera) {
                val stop = PendingIntent.getService(
                    context, 1,
                    Intent(context, SessionService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.addAction(0, context.getString(R.string.notif_stop), stop)
            }
            return builder.build()
        }

        fun start(context: Context, role: Role) {
            runningRole = role
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SessionService::class.java).putExtra(EXTRA_ROLE, role.name),
                )
            }
        }

        /** Replaces the notification text while the service runs; null = the role's default text. */
        fun update(context: Context, role: Role, text: String?) {
            if (runningRole != role) return
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, build(context, role, text))
            }
        }

        fun stop(context: Context) {
            runningRole = null
            context.stopService(Intent(context, SessionService::class.java))
        }
    }
}
