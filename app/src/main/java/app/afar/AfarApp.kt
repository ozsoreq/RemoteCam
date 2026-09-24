package app.afar

import android.app.Application
import app.afar.data.Prefs
import app.afar.media.Beeper
import app.afar.media.PhotoStore
import app.afar.net.NearbyLink
import app.afar.session.CameraSession
import app.afar.session.RemoteSession

/** Hand-rolled service locator: the whole graph is six objects, no DI framework needed. */
class AfarApp : Application() {
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
