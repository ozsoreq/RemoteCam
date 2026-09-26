package app.holdthatpose.ui.permissions

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import app.holdthatpose.ui.components.NoticePill
import app.holdthatpose.ui.components.Tone
import app.holdthatpose.ui.components.pressable
import kotlinx.coroutines.delay

/** Bluetooth is present but switched off (Nearby can't find or link phones). */
@SuppressLint("MissingPermission")
fun Context.bluetoothOff(): Boolean = runCatching {
    val adapter = getSystemService(BluetoothManager::class.java)?.adapter
    adapter != null && !adapter.isEnabled
}.getOrDefault(false)

/** Android 11 and older need Location switched on to discover nearby phones. */
fun Context.locationOffBlocksNearby(): Boolean {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) return false
    return runCatching {
        val lm = getSystemService(LocationManager::class.java)
        lm != null && !LocationManagerCompat.isLocationEnabled(lm)
    }.getOrDefault(false)
}

private fun Context.openSettings(action: String) {
    runCatching { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** "Turn on Bluetooth" / "Turn on Location" pills; tapping one opens the right settings page. */
@Composable
fun RadioNotices(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            tick++
        }
    }
    val btOff = remember(tick) { context.bluetoothOff() }
    val locationOff = remember(tick) { context.locationOffBlocksNearby() }
    if (!btOff && !locationOff) return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (btOff) {
            NoticePill(
                "Turn on Bluetooth",
                Tone.Warn,
                Modifier.pressable { context.openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
            )
        }
        if (locationOff) {
            NoticePill(
                "Turn on Location",
                Tone.Warn,
                Modifier.pressable { context.openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
            )
        }
    }
}
