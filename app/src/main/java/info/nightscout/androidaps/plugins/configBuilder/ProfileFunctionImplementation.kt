package info.nightscout.androidaps.plugins.configBuilder

import androidx.collection.LongSparseArray
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.ProfileSwitch
import info.nightscout.androidaps.database.transactions.InsertOrUpdateProfileSwitch
import info.nightscout.androidaps.extensions.fromConstant
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.security.spec.InvalidParameterSpecException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileFunctionImplementation @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val repository: AppRepository,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val hardLimits: HardLimits
) : ProfileFunction {

    val cache = LongSparseArray<Profile>()

    private val disposable = CompositeDisposable()

    override fun getProfileName(): String =
        getProfileName(System.currentTimeMillis(), customized = true, showRemainingTime = false)

    override fun getOriginalProfileName(): String =
        getProfileName(System.currentTimeMillis(), customized = false, showRemainingTime = false)

    override fun getProfileNameWithRemainingTime(): String =
        getProfileName(System.currentTimeMillis(), customized = true, showRemainingTime = true)

    fun getProfileName(time: Long, customized: Boolean, showRemainingTime: Boolean): String {
        var profileName = resourceHelper.gs(R.string.noprofileselected)

        val profileSwitch = repository.getEffectiveProfileSwitchActiveAt(time).blockingGet()
        if (profileSwitch is ValueWrapper.Existing) {
            profileName = if (customized) profileSwitch.value.originalCustomizedName else profileSwitch.value.originalProfileName
            if (showRemainingTime && profileSwitch.value.originalDuration != 0L) {
                profileName += dateUtil.untilString(profileSwitch.value.originalEnd, resourceHelper)
            }
        }
        return profileName
    }

    override fun isProfileValid(from: String): Boolean = getProfile() != null

    override fun getProfile(): Profile? =
        getProfile(dateUtil.now())

    @Synchronized
    override fun getProfile(time: Long): Profile? {
        val rounded = time - time % 1000
        val cached = cache[rounded]
        if (cached != null) {
//            aapsLogger.debug("HIT getProfile for $time $rounded")
            return cached
        }
//        aapsLogger.debug("getProfile called for $time")
        val ps = repository.getEffectiveProfileSwitchActiveAt(time).blockingGet()
        if (ps is ValueWrapper.Existing) {
            val sealed = ProfileSealed.EPS(ps.value)
            cache.put(rounded, sealed)
            return sealed
        }
        return null
    }

    override fun getRequestedProfile(): ProfileSwitch? = repository.getActiveProfileSwitch(dateUtil.now())

    override fun getUnits(): GlucoseUnit =
        if (sp.getString(R.string.key_units, Constants.MGDL) == Constants.MGDL) GlucoseUnit.MGDL
        else GlucoseUnit.MMOL

    override fun buildProfileSwitch(profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long) : ProfileSwitch {
        val pureProfile = profileStore.getSpecificProfile(profileName)
            ?: throw InvalidParameterSpecException(profileName)
        return ProfileSwitch(
            timestamp = timestamp,
            basalBlocks = pureProfile.basalBlocks,
            isfBlocks = pureProfile.isfBlocks,
            icBlocks = pureProfile.icBlocks,
            targetBlocks = pureProfile.targetBlocks,
            glucoseUnit = ProfileSwitch.GlucoseUnit.fromConstant(pureProfile.glucoseUnit),
            profileName = profileName,
            timeshift = T.hours(timeShiftInHours.toLong()).msecs(),
            percentage = percentage,
            duration = T.mins(durationInMinutes.toLong()).msecs(),
            insulinConfiguration = activePlugin.activeInsulin.insulinConfiguration.also {
                it.insulinEndTime = (pureProfile.dia * 3600 * 1000).toLong()
            }
        )
    }

    override fun createProfileSwitch(profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long) {
        val ps = buildProfileSwitch(profileStore, profileName, durationInMinutes, percentage, timeShiftInHours, timestamp)
        disposable += repository.runTransactionForResult(InsertOrUpdateProfileSwitch(ps))
            .subscribe({ result ->
                           result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch $it") }
                           result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated ProfileSwitch $it") }
                       }, {
                           aapsLogger.error(LTag.DATABASE, "Error while saving ProfileSwitch", it)
                       })
    }

    override fun createProfileSwitch(durationInMinutes: Int, percentage: Int, timeShiftInHours: Int): Boolean {
        val profile = repository.getPermanentProfileSwitch(dateUtil.now())
            ?: throw InvalidParameterSpecException("No active ProfileSwitch")
        val profileStore = activePlugin.activeProfileSource.profile ?: return false
        val ps = buildProfileSwitch(profileStore, profile.profileName, durationInMinutes, percentage, 0, dateUtil.now())
        val validity = ProfileSealed.PS(ps).isValid(
            resourceHelper.gs(info.nightscout.androidaps.automation.R.string.careportal_profileswitch),
            activePlugin.activePump,
            config,
            resourceHelper,
            rxBus,
            hardLimits
        )
        var returnValue = true
        if (validity.isValid) {
            repository.runTransactionForResult(InsertOrUpdateProfileSwitch(ps))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving ProfileSwitch", it)
                    returnValue = false
                }
                .blockingGet()
                .also { result ->
                    result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch $it") }
                    result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated ProfileSwitch $it") }
                }
        } else returnValue = false
        return returnValue
    }
}