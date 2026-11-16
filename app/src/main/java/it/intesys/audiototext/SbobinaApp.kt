package it.intesys.audiototext

import android.app.Application
import it.intesys.audiototext.crash.CrashReporter

class SbobinaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.get(this).install()
    }
}
