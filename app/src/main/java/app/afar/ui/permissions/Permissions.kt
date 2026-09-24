package app.afar.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.afar.net.Role

data class PermissionInfo(val permissions: List<String>, val title: String, val reason: String)

/** Grouped runtime permissions, each with the one-line reason shown to the user. */
fun permissionGroups(role: Role): List<PermissionInfo> = buildList {
    if (role == Role.Camera) {
        add(PermissionInfo(listOf(Manifest.permission.CAMERA), "Camera", "To take the photo with this phone."))
    }
    val nearby = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    if (nearby.isNotEmpty()) {
        add(PermissionInfo(nearby, "Nearby devices", "To find and link your two phones — no internet needed."))
    }
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        add(
            PermissionInfo(
                listOf(Manifest.permission.ACCESS_FINE_LOCATION),
                "Location",
                "Android needs it to scan for nearby phones. Afar never reads where you are.",
            ),
        )
    }
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        add(PermissionInfo(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), "Photos", "To save your shots to the gallery."))
    }
}

fun optionalPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.missingPermissions(role: Role): List<String> =
    permissionGroups(role).flatMap { it.permissions }.filterNot { isGranted(it) }
