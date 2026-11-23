package ke.floodguard.app

import android.app.Application
import ke.floodguard.app.data.ApiClient
import ke.floodguard.app.data.Locator
import ke.floodguard.app.data.TokenStore
import org.maplibre.android.MapLibre

/** Holds the app-wide singletons. Small enough that a DI framework would be more code than it saves. */
class FloodGuardApp : Application() {
    lateinit var api: ApiClient
        private set
    lateinit var tokens: TokenStore
        private set
    lateinit var locator: Locator
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        api = ApiClient(BuildConfig.API_URL, cacheDir)
        tokens = TokenStore(this)
        locator = Locator(this)
    }
}
