package app.holdthatpose.session

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Reads the Camera phone's tilt so the Remote can draw a horizon level, and flags sudden
 * jolts ("someone bumped the phone on the rock") so the level turns red.
 */
/**
 * From the gravity vector in device coordinates to (frame tilt in −45…45°, device rotation
 * in 90° steps matching Surface.ROTATION_*: 90 = rotated counter-clockwise).
 */
internal fun tiltFromGravity(gx: Float, gy: Float): Pair<Float, Int> {
    val angle = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
    val q = (angle / 90f).roundToInt()
    return (angle - q * 90f) to ((q % 4) + 4) % 4 * 90
}

class LevelSensor(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gravity = FloatArray(3)
    private var primed = false

    /** Frame tilt in degrees relative to the nearest upright orientation (−45…45). */
    @Volatile var roll: Float = 0f
        private set

    /** Device orientation in 90° steps (0, 90, 180, 270), used for capture rotation. */
    @Volatile var quadrant: Int = 0
        private set

    @Volatile private var bumpedAt = 0L
    val recentlyBumped: Boolean get() = System.currentTimeMillis() - bumpedAt < 2_500

    fun start() {
        primed = false
        accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (!primed) {
            v.copyInto(gravity, endIndex = 3)
            primed = true
        }
        val a = 0.9f
        for (i in 0..2) gravity[i] = a * gravity[i] + (1 - a) * v[i]
        val lx = v[0] - gravity[0]
        val ly = v[1] - gravity[1]
        val lz = v[2] - gravity[2]
        if (sqrt(lx * lx + ly * ly + lz * lz) > 3.2f) bumpedAt = System.currentTimeMillis()

        val gx = gravity[0]
        val gy = gravity[1]
        if (sqrt(gx * gx + gy * gy) < 3f) return // lying flat: roll is meaningless
        val (r, q) = tiltFromGravity(gx, gy)
        roll = r
        quadrant = q
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
