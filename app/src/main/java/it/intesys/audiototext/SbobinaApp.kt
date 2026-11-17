package it.intesys.audiototext

import android.app.Application
import it.intesys.audiototext.audio.AudioDecoder
import it.intesys.audiototext.crash.CrashReporter

class SbobinaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.get(this).install()
        // The temporary audio copy is normally deleted right after decoding; a killed process can leave one.
        AudioDecoder.deleteStaleTempFiles(cacheDir)
    }
}
