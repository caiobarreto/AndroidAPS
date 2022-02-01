package info.nightscout.androidaps.services

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import dagger.android.DaggerService
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.NotificationHolder
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.pow

class AlarmSoundService : DaggerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var notificationHolder: NotificationHolder
    @Inject lateinit var sp: SP

    private var player: MediaPlayer? = null
    private var resourceId = R.raw.error

    companion object {

        private const val VOLUME_INCREASE_STEPS = 40 // Total number of steps to increase volume with
        private const val VOLUME_INCREASE_INITIAL_SILENT_TIME_MILLIS = 3_000L // Number of milliseconds that the notification should initially be silent
        private const val VOLUME_INCREASE_BASE_DELAY_MILLIS = 15_000 // Base delay between volume increments
        private const val VOLUME_INCREASE_MIN_DELAY_MILLIS = 2_000L // Minimum delay between volume increments

        /*
         * Delay until the next volume increment will be the lowest value of VOLUME_INCREASE_MIN_DELAY_MILLIS and
         * VOLUME_INCREASE_BASE_DELAY_MILLIS - (currentVolumeLevel - 1) ^ VOLUME_INCREASE_DELAY_DECREMENT_EXPONENT * 1000
         *
         */
        private const val VOLUME_INCREASE_DELAY_DECREMENT_EXPONENT = 2.0

    }

    inner class LocalBinder : Binder() {

        fun getService(): AlarmSoundService = this@AlarmSoundService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    private val increaseVolumeHandler = Handler(Looper.getMainLooper())
    private var currentVolumeLevel = 0

    override fun onCreate() {
        super.onCreate()
        aapsLogger.debug(LTag.CORE, "onCreate parent called")
        startForeground(notificationHolder.notificationID, notificationHolder.notification)
        aapsLogger.debug(LTag.CORE, "onCreate End")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        aapsLogger.debug(LTag.CORE, "onStartCommand")
        startForeground(notificationHolder.notificationID, notificationHolder.notification)
        aapsLogger.debug(LTag.CORE, "onStartCommand Foreground called")

        player?.let { if (it.isPlaying) it.stop() }

        if (intent?.hasExtra(ErrorHelperActivity.SOUND_ID) == true) resourceId = intent.getIntExtra(ErrorHelperActivity.SOUND_ID, R.raw.error)
        player = MediaPlayer()
        try {
            val afd = rh.openRawResourceFd(resourceId) ?: return START_STICKY
            player?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            player?.isLooping = true
            val audioManager = getAudioManager()
            if (!audioManager.isMusicActive) {
                if (sp.getBoolean(R.string.key_gradually_increase_notification_volume, false)) {
                    currentVolumeLevel = 0
                    player?.setVolume(0f, 0f)
                    increaseVolumeHandler.postDelayed(volumeUpdater, VOLUME_INCREASE_INITIAL_SILENT_TIME_MILLIS)
                } else {
                    player?.setVolume(1f, 1f)
                }
            }
            player?.prepare()
            player?.start()
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
        aapsLogger.debug(LTag.CORE, "onStartCommand End")
        return START_STICKY
    }

    override fun onDestroy() {
        aapsLogger.debug(LTag.CORE, "onDestroy")
        increaseVolumeHandler.removeCallbacks(volumeUpdater)
        player?.stop()
        player?.release()
        aapsLogger.debug(LTag.CORE, "onDestroy End")
    }

    private fun getAudioManager() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // TODO replace with VolumeShaper when min API level >= 26
    private val volumeUpdater = object : Runnable {
        override fun run() {
            currentVolumeLevel++

            val volumePercentage = 100.0.coerceAtMost(currentVolumeLevel / VOLUME_INCREASE_STEPS.toDouble() * 100)
            val volume = (1 - (ln(1.0.coerceAtLeast(100.0 - volumePercentage)) / ln(100.0))).toFloat()

            aapsLogger.debug(LTag.CORE, "Setting notification volume to {} ({} %)", volume, volumePercentage)

            player?.setVolume(volume, volume)

            if (currentVolumeLevel < VOLUME_INCREASE_STEPS) {
                // Increase volume faster as time goes by
                val delay = VOLUME_INCREASE_MIN_DELAY_MILLIS.coerceAtLeast(VOLUME_INCREASE_BASE_DELAY_MILLIS -
                    ((currentVolumeLevel - 1).toDouble().pow(VOLUME_INCREASE_DELAY_DECREMENT_EXPONENT) * 1000).toLong())
                aapsLogger.debug(LTag.CORE, "Next notification volume increment in {}ms", delay)
                increaseVolumeHandler.postDelayed(this, delay)
            }
        }
    }
}
