package tss.t.tsiptv

import android.app.Application

class TSAndroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: TSAndroidApplication
    }
}
