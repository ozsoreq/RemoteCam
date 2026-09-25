package app.holdthatpose.session

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** Battery and thermal snapshot of this phone. */
data class Health(val battery: Int, val charging: Boolean, val hot: Boolean)

fun readHealth(context: Context): Health {
    val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1

    val thermalHot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    } else {
        false
    }
    return Health(pct, charging, thermalHot || tempTenths >= 450)
}
