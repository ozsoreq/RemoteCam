package app.holdthatpose

import android.app.Application
import app.holdthatpose.data.Prefs
import app.holdthatpose.media.Beeper
import app.holdthatpose.media.PhotoStore
import app.holdthatpose.net.NearbyLink
import app.holdthatpose.session.CameraSession
import app.holdthatpose.session.RemoteSession

/** Hand-rolled service locator: the whole graph is six objects, no DI framework needed. */
class PoseApp : Application() {
    lateinit var prefs: Prefs private set
    val link by lazy { NearbyLink(this, prefs.installId) }
    val store by lazy { PhotoStore(this) }
    val beeper by lazy { Beeper(this) }
    val cameraSession by lazy { CameraSession(this, link, prefs, store, beeper) }
    val remoteSession by lazy { RemoteSession(this, link, prefs, store, beeper) }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }
}
