package app.aaps.plugins.main.general.overview

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.text.toSpanned
import androidx.recyclerview.widget.LinearLayoutManager
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.VariableSensitivityResult
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.extensions.runOnUiThread
import app.aaps.core.interfaces.extensions.toVisibility
import app.aaps.core.interfaces.extensions.toVisibilityKeepSpace
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.defs.PumpType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAcceptOpenLoopChange
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.rx.events.EventEffectiveProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventNewOpenLoopNotification
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.rx.events.EventScale
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.rx.events.EventTempTargetChange
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewGraph
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewIobCob
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewSensitivity
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.main.constraints.ConstraintObject
import app.aaps.core.main.extensions.directionToIcon
import app.aaps.core.main.graph.OverviewData
import app.aaps.core.main.iob.displayText
import app.aaps.core.main.profile.ProfileSealed
import app.aaps.core.main.wizard.QuickWizard
import app.aaps.core.ui.UIRunnable
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.elements.SingleClickButton
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.JsonHelper
import app.aaps.database.entities.UserEntry.Action
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.database.entities.interfaces.end
import app.aaps.database.impl.AppRepository
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.OverviewFragmentBinding
import app.aaps.plugins.main.general.overview.graphData.GraphData
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import app.aaps.plugins.main.general.overview.notifications.events.EventUpdateOverviewNotification
import app.aaps.plugins.main.general.overview.ui.StatusLightHandler
import app.aaps.plugins.main.skins.SkinProvider
import com.jjoe64.graphview.GraphView
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min

class OverviewFragment : DaggerFragment(), View.OnClickListener, OnLongClickListener {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Inject lateinit var nsSettingsStatus: NSSettingsStatus
    @Inject lateinit var loop: Loop
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var dexcomBoyda: DexcomBoyda
    @Inject lateinit var xDripSource: XDripSource
    @Inject lateinit var notificationStore: NotificationStore
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var config: Config
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var automation: Automation
    @Inject lateinit var bgQualityCheck: BgQualityCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var decimalFormatter: DecimalFormatter

    private val disposable = CompositeDisposable()

    private var smallWidth = false
    private var smallHeight = false
    private lateinit var dm: DisplayMetrics
    private var axisWidth: Int = 0
    private lateinit var refreshLoop: Runnable
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private val secondaryGraphs = ArrayList<GraphView>()
    private val secondaryGraphsLabel = ArrayList<TextView>()

    private var carbAnimation: AnimationDrawable? = null

    private var _binding: OverviewFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OverviewFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            //check screen width
            dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                activity?.display?.getRealMetrics(dm)
            else
                activity?.windowManager?.defaultDisplay?.getMetrics(dm)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // pre-process landscape mode
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        smallWidth = screenWidth <= Constants.SMALL_WIDTH
        smallHeight = screenHeight <= Constants.SMALL_HEIGHT
        val landscape = screenHeight < screenWidth

        skinProvider.activeSkin().preProcessLandscapeOverviewLayout(binding, landscape, rh.gb(app.aaps.core.ui.R.bool.isTablet), smallHeight)
        binding.nsclientCard.visibility = config.NSCLIENT.toVisibility()

        binding.notifications.setHasFixedSize(false)
        binding.notifications.layoutManager = LinearLayoutManager(view.context)
        axisWidth = if (dm.densityDpi <= 120) 3 else if (dm.densityDpi <= 160) 10 else if (dm.densityDpi <= 320) 35 else if (dm.densityDpi <= 420) 50 else if (dm.densityDpi <= 560) 70 else 80
        binding.graphsLayout.bgGraph.gridLabelRenderer?.gridColor = rh.gac(context, app.aaps.core.ui.R.attr.graphGrid)
        binding.graphsLayout.bgGraph.gridLabelRenderer?.reloadStyles()
        binding.graphsLayout.bgGraph.gridLabelRenderer?.labelVerticalWidth = axisWidth
        binding.graphsLayout.bgGraph.layoutParams?.height = rh.dpToPx(skinProvider.activeSkin().mainGraphHeight)

        carbAnimation = binding.infoLayout.carbsIcon.background as AnimationDrawable?
        carbAnimation?.setEnterFadeDuration(1200)
        carbAnimation?.setExitFadeDuration(1200)

        binding.graphsLayout.bgGraph.setOnLongClickListener {
            overviewData.rangeToDisplay += 6
            overviewData.rangeToDisplay = if (overviewData.rangeToDisplay > 24) 6 else overviewData.rangeToDisplay
            sp.putInt(app.aaps.core.utils.R.string.key_rangetodisplay, overviewData.rangeToDisplay)
            rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.utils.R.string.key_rangetodisplay)))
            sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusescale, true)
            false
        }
        prepareGraphsIfNeeded(overviewMenus.setting.size)
        context?.let { overviewMenus.setupChartMenu(it, binding.graphsLayout.chartMenuButton) }

        binding.activeProfile.setOnClickListener(this)
        binding.activeProfile.setOnLongClickListener(this)
        binding.tempTarget.setOnClickListener(this)
        binding.tempTarget.setOnLongClickListener(this)
        binding.buttonsLayout.acceptTempButton.setOnClickListener(this)
        binding.buttonsLayout.treatmentButton.setOnClickListener(this)
        binding.buttonsLayout.wizardButton.setOnClickListener(this)
        binding.buttonsLayout.calibrationButton.setOnClickListener(this)
        binding.buttonsLayout.cgmButton.setOnClickListener(this)
        binding.buttonsLayout.insulinButton.setOnClickListener(this)
        binding.buttonsLayout.carbsButton.setOnClickListener(this)
        binding.buttonsLayout.quickWizardButton.setOnClickListener(this)
        binding.buttonsLayout.quickWizardButton.setOnLongClickListener(this)
        binding.infoLayout.apsMode.setOnClickListener(this)
        binding.infoLayout.apsMode.setOnLongClickListener(this)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewCalcProgress::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateCalcProgress() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewIobCob::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateIobCob() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewSensitivity::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateSensitivity() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewGraph::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGraph() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewNotification::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateNotification() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventScale::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           overviewData.rangeToDisplay = it.hours
                           sp.putInt(app.aaps.core.utils.R.string.key_rangetodisplay, it.hours)
                           rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.utils.R.string.key_rangetodisplay)))
                           sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusescale, true)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventBucketedDataCreated::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateBg() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           if (it.now) refreshAll()
                           else scheduleUpdateGUI()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAcceptOpenLoopChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewOpenLoopNotification::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .delay(30, TimeUnit.MILLISECONDS, aapsSchedulers.main)
            .subscribe({
                           overviewData.pumpStatus = it.getStatus(requireContext())
                           updatePumpStatus()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ processButtonsVisibility() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventEffectiveProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateTemporaryTarget() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateExtendedBolus() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateTemporaryBasal() }, fabricPrivacy::logException)

        refreshLoop = Runnable {
            refreshAll()
            handler.postDelayed(refreshLoop, 60 * 1000L)
        }
        handler.postDelayed(refreshLoop, 60 * 1000L)

        handler.post { refreshAll() }
        updatePumpStatus()
        updateCalcProgress()
    }

    fun refreshAll() {
        if (!config.appInitialized) return
        runOnUiThread {
            _binding ?: return@runOnUiThread
            updateTime()
            updateSensitivity()
            updateGraph()
            updateNotification()
        }
        updateBg()
        updateTemporaryBasal()
        updateExtendedBolus()
        updateIobCob()
        processButtonsVisibility()
        processAps()
        updateProfile()
        updateTemporaryTarget()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onClick(v: View) {
        // try to fix  https://fabric.io/nightscout3/android/apps/info.nightscout.androidaps/issues/5aca7a1536c7b23527eb4be7?time=last-seven-days
        // https://stackoverflow.com/questions/14860239/checking-if-state-is-saved-before-committing-a-fragmenttransaction
        if (childFragmentManager.isStateSaved) return
        activity?.let { activity ->
            when (v.id) {
                R.id.treatment_button    -> protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runTreatmentDialog(childFragmentManager) })

                R.id.wizard_button       -> protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runWizardDialog(childFragmentManager) })

                R.id.insulin_button      -> protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runInsulinDialog(childFragmentManager) })

                R.id.quick_wizard_button -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { if (isAdded) onClickQuickWizard() })
                R.id.carbs_button        -> protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runCarbsDialog(childFragmentManager) })

                R.id.temp_target         -> protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runTempTargetDialog(childFragmentManager) })

                R.id.active_profile      -> {
                    uiInteraction.runProfileViewerDialog(
                        childFragmentManager,
                        dateUtil.now(),
                        UiInteraction.Mode.RUNNING_PROFILE
                    )
                }

                R.id.cgm_button          -> {
                    if (xDripSource.isEnabled())
                        openCgmApp("com.eveningoutpost.dexdrip")
                    else if (dexcomBoyda.isEnabled()) {
                        dexcomBoyda.findDexcomPackageName()?.let {
                            openCgmApp(it)
                        }
                            ?: ToastUtils.infoToast(activity, rh.gs(R.string.dexcom_app_not_installed))
                    }
                }

                R.id.calibration_button  -> {
                    if (xDripSource.isEnabled()) {
                        uiInteraction.runCalibrationDialog(childFragmentManager)
                    } else if (dexcomBoyda.isEnabled()) {
                        try {
                            dexcomBoyda.findDexcomPackageName()?.let {
                                startActivity(
                                    Intent("com.dexcom.cgm.activities.MeterEntryActivity")
                                        .setPackage(it)
                                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                )
                            }
                                ?: ToastUtils.infoToast(activity, rh.gs(R.string.dexcom_app_not_installed))
                        } catch (e: ActivityNotFoundException) {
                            ToastUtils.infoToast(activity, rh.gs(R.string.dexcom_app_not_detected))
                        }
                    }
                }

                R.id.accept_temp_button  -> {
                    profileFunction.getProfile() ?: return
                    if ((loop as PluginBase).isEnabled()) {
                        handler.post {
                            val lastRun = loop.lastRun
                            loop.invoke("Accept temp button", false)
                            if (lastRun?.lastAPSRun != null && lastRun.constraintsProcessed?.isChangeRequested == true) {
                                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                                    if (isAdded)
                                        OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.tempbasal_label), lastRun.constraintsProcessed?.toSpanned()
                                            ?: "".toSpanned(), {
                                                                      uel.log(Action.ACCEPTS_TEMP_BASAL, Sources.Overview)
                                                                      (context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?)?.cancel(Constants.notificationID)
                                                                      rxBus.send(EventMobileToWear(EventData.CancelNotification(dateUtil.now())))
                                                                      handler.post { loop.acceptChangeRequest() }
                                                                      binding.buttonsLayout.acceptTempButton.visibility = View.GONE
                                                                  })
                                })
                            }
                        }
                    }
                }

                R.id.aps_mode            -> {
                    protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                        if (isAdded) uiInteraction.runLoopDialog(childFragmentManager, 1)
                    })
                }
            }
        }
    }

    private fun openCgmApp(packageName: String) {
        context?.let {
            val packageManager = it.packageManager
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: throw ActivityNotFoundException()
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                it.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                OKDialog.show(it, "", rh.gs(R.string.error_starting_cgm))
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        when (v.id) {
            R.id.quick_wizard_button -> {
                startActivity(Intent(v.context, uiInteraction.quickWizardListActivity))
                return true
            }

            R.id.aps_mode            -> {
                activity?.let { activity ->
                    protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                        uiInteraction.runLoopDialog(childFragmentManager, 0)
                    })
                }
            }

            R.id.temp_target         -> v.performClick()
            R.id.active_profile      -> activity?.let { activity ->
                if (loop.isDisconnected) OKDialog.show(activity, rh.gs(R.string.not_available_full), rh.gs(R.string.smscommunicator_pump_disconnected))
                else
                    protectionCheck.queryProtection(
                        activity,
                        ProtectionCheck.Protection.BOLUS,
                        UIRunnable { uiInteraction.runProfileSwitchDialog(childFragmentManager) })
            }

        }
        return false
    }

    private fun onClickQuickWizard() {
        val actualBg = iobCobCalculator.ads.actualBg()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val pump = activePlugin.activePump
        val quickWizardEntry = quickWizard.getActive()
        if (quickWizardEntry != null && actualBg != null && profile != null) {
            binding.buttonsLayout.quickWizardButton.visibility = View.VISIBLE
            val wizard = quickWizardEntry.doCalc(profile, profileName, actualBg)
            if (wizard.calculatedTotalInsulin > 0.0 && quickWizardEntry.carbs() > 0.0) {
                val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(quickWizardEntry.carbs(), aapsLogger)).value()
                activity?.let {
                    if (abs(wizard.insulinAfterConstraints - wizard.calculatedTotalInsulin) >= pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints) || carbsAfterConstraints != quickWizardEntry.carbs()) {
                        OKDialog.show(it, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), rh.gs(R.string.constraints_violation) + "\n" + rh.gs(R.string.change_your_input))
                        return
                    }
                    wizard.confirmAndExecute(it)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processButtonsVisibility() {
        val lastBG = iobCobCalculator.ads.lastBg()
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val actualBG = iobCobCalculator.ads.actualBg()

        // QuickWizard button
        val quickWizardEntry = quickWizard.getActive()
        runOnUiThread {
            _binding ?: return@runOnUiThread
            if (quickWizardEntry != null && lastBG != null && profile != null && pump.isInitialized() && !pump.isSuspended() && !loop.isDisconnected) {
                binding.buttonsLayout.quickWizardButton.visibility = View.VISIBLE
                val wizard = quickWizardEntry.doCalc(profile, profileName, lastBG)
                binding.buttonsLayout.quickWizardButton.text = quickWizardEntry.buttonText() + "\n" + rh.gs(app.aaps.core.main.R.string.format_carbs, quickWizardEntry.carbs()) +
                    " " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.calculatedTotalInsulin)
                if (wizard.calculatedTotalInsulin <= 0) binding.buttonsLayout.quickWizardButton.visibility = View.GONE
            } else binding.buttonsLayout.quickWizardButton.visibility = View.GONE
        }

        // **** Temp button ****
        val lastRun = loop.lastRun
        val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()

        val showAcceptButton = !closedLoopEnabled.value() && // Open mode needed
            lastRun != null &&
            (lastRun.lastOpenModeAccept == 0L || lastRun.lastOpenModeAccept < lastRun.lastAPSRun) &&// never accepted or before last result
            lastRun.constraintsProcessed?.isChangeRequested == true // change is requested

        runOnUiThread {
            _binding ?: return@runOnUiThread
            if (showAcceptButton && pump.isInitialized() && !pump.isSuspended() && (loop as PluginBase).isEnabled()) {
                binding.buttonsLayout.acceptTempButton.visibility = View.VISIBLE
                binding.buttonsLayout.acceptTempButton.text = "${rh.gs(R.string.set_basal_question)}\n${lastRun!!.constraintsProcessed}"
            } else {
                binding.buttonsLayout.acceptTempButton.visibility = View.GONE
            }

            // **** Various treatment buttons ****
            binding.buttonsLayout.carbsButton.visibility =
                (/*(!activePlugin.activePump.pumpDescription.storesCarbInfo || pump.isInitialized() && !pump.isSuspended()) &&*/ profile != null
                    && sp.getBoolean(R.string.key_show_carbs_button, true)).toVisibility()
            binding.buttonsLayout.treatmentButton.visibility = (!loop.isDisconnected && pump.isInitialized() && !pump.isSuspended() && profile != null
                && sp.getBoolean(R.string.key_show_treatment_button, false)).toVisibility()
            binding.buttonsLayout.wizardButton.visibility = (!loop.isDisconnected && pump.isInitialized() && !pump.isSuspended() && profile != null
                && sp.getBoolean(R.string.key_show_wizard_button, true)).toVisibility()
            binding.buttonsLayout.insulinButton.visibility = (!loop.isDisconnected && pump.isInitialized() && !pump.isSuspended() && profile != null
                && sp.getBoolean(R.string.key_show_insulin_button, true)).toVisibility()

            // **** Calibration & CGM buttons ****
            val xDripIsBgSource = xDripSource.isEnabled()
            val dexcomIsSource = dexcomBoyda.isEnabled()
            binding.buttonsLayout.calibrationButton.visibility = (xDripIsBgSource && actualBG != null && sp.getBoolean(R.string.key_show_calibration_button, true)).toVisibility()
            if (dexcomIsSource) {
                binding.buttonsLayout.cgmButton.setCompoundDrawablesWithIntrinsicBounds(null, rh.gd(R.drawable.ic_byoda), null, null)
                for (drawable in binding.buttonsLayout.cgmButton.compoundDrawables) {
                    drawable?.mutate()
                    drawable?.colorFilter = PorterDuffColorFilter(rh.gac(context, app.aaps.core.ui.R.attr.cgmDexColor), PorterDuff.Mode.SRC_IN)
                }
                binding.buttonsLayout.cgmButton.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.cgmDexColor))
            } else if (xDripIsBgSource) {
                binding.buttonsLayout.cgmButton.setCompoundDrawablesWithIntrinsicBounds(null, rh.gd(app.aaps.core.main.R.drawable.ic_xdrip), null, null)
                for (drawable in binding.buttonsLayout.cgmButton.compoundDrawables) {
                    drawable?.mutate()
                    drawable?.colorFilter = PorterDuffColorFilter(rh.gac(context, app.aaps.core.ui.R.attr.cgmXdripColor), PorterDuff.Mode.SRC_IN)
                }
                binding.buttonsLayout.cgmButton.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.cgmXdripColor))
            }
            binding.buttonsLayout.cgmButton.visibility = (sp.getBoolean(R.string.key_show_cgm_button, false) && (xDripIsBgSource || dexcomIsSource)).toVisibility()

            // Automation buttons
            binding.buttonsLayout.userButtonsLayout.removeAllViews()
            val events = automation.userEvents()
            if (!loop.isDisconnected && pump.isInitialized() && !pump.isSuspended() && profile != null)
                for (event in events)
                    if (event.isEnabled && event.canRun())
                        context?.let { context ->
                            SingleClickButton(context, null, app.aaps.core.ui.R.attr.customBtnStyle).also {
                                it.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.treatmentButton))
                                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                                it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.5f).also { l ->
                                    l.setMargins(0, 0, rh.dpToPx(-4), 0)
                                }
                                it.setCompoundDrawablesWithIntrinsicBounds(null, rh.gd(app.aaps.core.ui.R.drawable.ic_user_options), null, null)
                                it.text = event.title

                                it.setOnClickListener {
                                    OKDialog.showConfirmation(context, rh.gs(R.string.run_question, event.title), { handler.post { automation.processEvent(event) } })
                                }
                                binding.buttonsLayout.userButtonsLayout.addView(it)
                            }
                        }
            binding.buttonsLayout.userButtonsLayout.visibility = events.isNotEmpty().toVisibility()
        }
    }

    private fun processAps() {
        val pump = activePlugin.activePump

        // aps mode
        val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()

        fun apsModeSetA11yLabel(stringRes: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                binding.infoLayout.apsMode.stateDescription = rh.gs(stringRes)
            } else {
                binding.infoLayout.apsMode.contentDescription = rh.gs(app.aaps.core.ui.R.string.aps_mode_title) + " " + rh.gs(stringRes)
            }
        }

        runOnUiThread {
            _binding ?: return@runOnUiThread
            if (config.APS && pump.pumpDescription.isTempBasalCapable) {
                binding.infoLayout.apsMode.visibility = View.VISIBLE
                binding.infoLayout.timeLayout.visibility = View.GONE
                when {
                    (loop as PluginBase).isEnabled() && loop.isSuperBolus                       -> {
                        binding.infoLayout.apsMode.setImageResource(R.drawable.ic_loop_superbolus)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.superbolus)
                        binding.infoLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE
                    }

                    loop.isDisconnected                                                         -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_disconnected)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.disconnected)
                        binding.infoLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE
                    }

                    (loop as PluginBase).isEnabled() && loop.isSuspended                        -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_paused)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.suspendloop_label)
                        binding.infoLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE
                    }

                    pump.isSuspended()                                                          -> {
                        binding.infoLayout.apsMode.setImageResource(
                            if (pump.model() == PumpType.OMNIPOD_EROS || pump.model() == PumpType.OMNIPOD_DASH) {
                                // For Omnipod, indicate the pump as disconnected when it's suspended.
                                // The only way to 'reconnect' it, is through the Omnipod tab
                                apsModeSetA11yLabel(app.aaps.core.ui.R.string.disconnected)
                                app.aaps.core.ui.R.drawable.ic_loop_disconnected
                            } else {
                                apsModeSetA11yLabel(app.aaps.core.ui.R.string.pump_paused)
                                app.aaps.core.ui.R.drawable.ic_loop_paused
                            }
                        )
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }

                    (loop as PluginBase).isEnabled() && closedLoopEnabled.value() && loop.isLGS -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_lgs)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.uel_lgs_loop_mode)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }

                    (loop as PluginBase).isEnabled() && closedLoopEnabled.value()               -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.main.R.drawable.ic_loop_closed)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.closedloop)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }

                    (loop as PluginBase).isEnabled() && !closedLoopEnabled.value()              -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_open)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.openloop)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }

                    else                                                                        -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_disabled)
                        apsModeSetA11yLabel(R.string.disabled_loop)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }
                }
            } else {
                //nsclient
                binding.infoLayout.apsMode.visibility = View.GONE
                binding.infoLayout.apsModeText.visibility = View.GONE
                binding.infoLayout.timeLayout.visibility = View.VISIBLE
            }

            // pump status from ns
            binding.pump.text = processedDeviceStatusData.pumpStatus(nsSettingsStatus)
            binding.pump.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(app.aaps.core.ui.R.string.pump), processedDeviceStatusData.extendedPumpStatus) } }

            // OpenAPS status from ns
            binding.openaps.text = processedDeviceStatusData.openApsStatus
            binding.openaps.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(R.string.openaps), processedDeviceStatusData.extendedOpenApsStatus) } }

            // Uploader status from ns
            binding.uploader.text = processedDeviceStatusData.uploaderStatusSpanned
            binding.uploader.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(R.string.uploader), processedDeviceStatusData.extendedUploaderStatus) } }
        }
    }

    private fun prepareGraphsIfNeeded(numOfGraphs: Int) {
        if (numOfGraphs != secondaryGraphs.size - 1) {
            //aapsLogger.debug("New secondary graph count ${numOfGraphs-1}")
            // rebuild needed
            secondaryGraphs.clear()
            secondaryGraphsLabel.clear()
            binding.graphsLayout.iobGraph.removeAllViews()
            for (i in 1 until numOfGraphs) {
                val relativeLayout = RelativeLayout(context)
                relativeLayout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                val graph = GraphView(context)
                graph.layoutParams =
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rh.dpToPx(skinProvider.activeSkin().secondaryGraphHeight)).also { it.setMargins(0, rh.dpToPx(15), 0, rh.dpToPx(10)) }
                graph.gridLabelRenderer?.gridColor = rh.gac(context, app.aaps.core.ui.R.attr.graphGrid)
                graph.gridLabelRenderer?.reloadStyles()
                graph.gridLabelRenderer?.isHorizontalLabelsVisible = false
                graph.gridLabelRenderer?.labelVerticalWidth = axisWidth
                graph.gridLabelRenderer?.numVerticalLabels = 3
                graph.viewport.backgroundColor = rh.gac(context, app.aaps.core.ui.R.attr.viewPortBackgroundColor)
                relativeLayout.addView(graph)

                val label = TextView(context)
                val layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(rh.dpToPx(30), rh.dpToPx(25), 0, 0) }
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                label.layoutParams = layoutParams
                relativeLayout.addView(label)
                secondaryGraphsLabel.add(label)

                binding.graphsLayout.iobGraph.addView(relativeLayout)
                secondaryGraphs.add(graph)
            }
        }
    }

    var task: Runnable? = null

    private fun scheduleUpdateGUI() {
        class UpdateRunnable : Runnable {

            override fun run() {
                refreshAll()
                task = null
            }
        }
        task?.let { handler.removeCallbacks(it) }
        task = UpdateRunnable()
        task?.let { handler.postDelayed(it, 500) }
    }

    @SuppressLint("SetTextI18n")
    fun updateBg() {
        val lastBg = overviewData.lastBg(iobCobCalculator.ads)
        val lastBgColor = overviewData.lastBgColor(context, iobCobCalculator.ads)
        val isActualBg = overviewData.isActualBg(iobCobCalculator.ads)
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        val trendDescription = trendCalculator.getTrendDescription(iobCobCalculator.ads)
        val trendArrow = trendCalculator.getTrendArrow(iobCobCalculator.ads)
        val lastBgDescription = overviewData.lastBgDescription(iobCobCalculator.ads)
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.bg.text = profileUtil.fromMgdlToStringInUnits(lastBg?.recalculated)
            binding.infoLayout.bg.setTextColor(lastBgColor)
            trendArrow?.let { binding.infoLayout.arrow.setImageResource(it.directionToIcon()) }
            binding.infoLayout.arrow.visibility = (trendArrow != null).toVisibilityKeepSpace()
            binding.infoLayout.arrow.setColorFilter(lastBgColor)
            binding.infoLayout.arrow.contentDescription = lastBgDescription + " " + rh.gs(app.aaps.core.ui.R.string.and) + " " + trendDescription

            if (glucoseStatus != null) {
                binding.infoLayout.deltaLarge.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta)
                binding.infoLayout.deltaLarge.setTextColor(lastBgColor)
                binding.infoLayout.delta.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta)
                binding.infoLayout.avgDelta.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.shortAvgDelta)
                binding.infoLayout.longAvgDelta.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.longAvgDelta)
            } else {
                binding.infoLayout.deltaLarge.text = ""
                binding.infoLayout.delta.text = "Δ " + rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
                binding.infoLayout.avgDelta.text = ""
                binding.infoLayout.longAvgDelta.text = ""
            }

            // strike through if BG is old
            binding.infoLayout.bg.paintFlags =
                if (!isActualBg) binding.infoLayout.bg.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                else binding.infoLayout.bg.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

            val outDate = (if (!isActualBg) rh.gs(R.string.a11y_bg_outdated) else "")
            binding.infoLayout.bg.contentDescription = rh.gs(R.string.a11y_blood_glucose) + " " + binding.infoLayout.bg.text.toString() + " " + lastBgDescription + " " + outDate

            binding.infoLayout.timeAgo.text = dateUtil.minAgo(rh, lastBg?.timestamp)
            binding.infoLayout.timeAgo.contentDescription = dateUtil.minAgoLong(rh, lastBg?.timestamp)
            binding.infoLayout.timeAgoShort.text = "(" + dateUtil.minAgoShort(lastBg?.timestamp) + ")"

            val qualityIcon = bgQualityCheck.icon()
            if (qualityIcon != 0) {
                binding.infoLayout.bgQuality.visibility = View.VISIBLE
                binding.infoLayout.bgQuality.setImageResource(qualityIcon)
                binding.infoLayout.bgQuality.contentDescription = rh.gs(R.string.a11y_bg_quality) + " " + bgQualityCheck.stateDescription()
                binding.infoLayout.bgQuality.setOnClickListener {
                    context?.let { context -> OKDialog.show(context, rh.gs(R.string.data_status), bgQualityCheck.message) }
                }
            } else {
                binding.infoLayout.bgQuality.visibility = View.GONE
            }
        }
    }

    private fun updateProfile() {
        val profile = profileFunction.getProfile()
        runOnUiThread {
            _binding ?: return@runOnUiThread
            val profileBackgroundColor = profile?.let {
                if (it is ProfileSealed.EPS) {
                    if (it.value.originalPercentage != 100 || it.value.originalTimeshift != 0L || it.value.originalDuration != 0L)
                        app.aaps.core.ui.R.attr.ribbonWarningColor
                    else app.aaps.core.ui.R.attr.ribbonDefaultColor
                } else app.aaps.core.ui.R.attr.ribbonDefaultColor
            } ?: app.aaps.core.ui.R.attr.ribbonCriticalColor

            val profileTextColor = profile?.let {
                if (it is ProfileSealed.EPS) {
                    if (it.value.originalPercentage != 100 || it.value.originalTimeshift != 0L || it.value.originalDuration != 0L)
                        app.aaps.core.ui.R.attr.ribbonTextWarningColor
                    else app.aaps.core.ui.R.attr.ribbonTextDefaultColor
                } else app.aaps.core.ui.R.attr.ribbonTextDefaultColor
            } ?: app.aaps.core.ui.R.attr.ribbonTextDefaultColor
            setRibbon(binding.activeProfile, profileTextColor, profileBackgroundColor, profileFunction.getProfileNameWithRemainingTime())
        }
    }

    private fun updateTemporaryBasal() {
        val temporaryBasalText = overviewData.temporaryBasalText(iobCobCalculator)
        val temporaryBasalColor = overviewData.temporaryBasalColor(context, iobCobCalculator)
        val temporaryBasalIcon = overviewData.temporaryBasalIcon(iobCobCalculator)
        val temporaryBasalDialogText = overviewData.temporaryBasalDialogText(iobCobCalculator)
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.baseBasal.text = temporaryBasalText
            binding.infoLayout.baseBasal.setTextColor(temporaryBasalColor)
            binding.infoLayout.baseBasalIcon.setImageResource(temporaryBasalIcon)
            binding.infoLayout.basalLayout.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(app.aaps.core.ui.R.string.basal), temporaryBasalDialogText) } }
        }
    }

    private fun updateExtendedBolus() {
        val pump = activePlugin.activePump
        val extendedBolus = iobCobCalculator.getExtendedBolus(dateUtil.now())
        val extendedBolusText = overviewData.extendedBolusText(iobCobCalculator)
        val extendedBolusDialogText = overviewData.extendedBolusDialogText(iobCobCalculator)
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.extendedBolus.text = extendedBolusText
            binding.infoLayout.extendedLayout.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(app.aaps.core.ui.R.string.extended_bolus), extendedBolusDialogText) } }
            binding.infoLayout.extendedLayout.visibility = (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses).toVisibility()
        }
    }

    private fun updateTime() {
        _binding ?: return
        binding.infoLayout.time.text = dateUtil.timeString(dateUtil.now())
        // Status lights
        val pump = activePlugin.activePump
        val isPatchPump = pump.pumpDescription.isPatchPump
        binding.statusLightsLayout.apply {
            cannulaOrPatch.setImageResource(if (isPatchPump) app.aaps.core.main.R.drawable.ic_patch_pump_outline else R.drawable.ic_cp_age_cannula)
            cannulaOrPatch.contentDescription = rh.gs(if (isPatchPump) R.string.statuslights_patch_pump_age else R.string.statuslights_cannula_age)
            insulinAge.visibility = isPatchPump.not().toVisibility()
            batteryLayout.visibility = (!isPatchPump || pump.pumpDescription.useHardwareLink).toVisibility()
            pbAge.visibility = (pump.pumpDescription.isBatteryReplaceable || pump.isBatteryChangeLoggingEnabled()).toVisibility()
            val useBatteryLevel = (pump.model() == PumpType.OMNIPOD_EROS)
                || (pump.model() != PumpType.ACCU_CHEK_COMBO && pump.model() != PumpType.OMNIPOD_DASH)
            pbLevel.visibility = useBatteryLevel.toVisibility()
            statusLightsLayout.visibility = (sp.getBoolean(R.string.key_show_statuslights, true) || config.NSCLIENT).toVisibility()
        }
        statusLightHandler.updateStatusLights(
            binding.statusLightsLayout.cannulaAge,
            null,
            binding.statusLightsLayout.insulinAge,
            binding.statusLightsLayout.reservoirLevel,
            binding.statusLightsLayout.sensorAge,
            null,
            binding.statusLightsLayout.pbAge,
            binding.statusLightsLayout.pbLevel
        )
    }

    private fun updateIobCob() {
        val iobText = overviewData.iobText(iobCobCalculator)
        val iobDialogText = overviewData.iobDialogText(iobCobCalculator)
        val displayText = overviewData.cobInfo(iobCobCalculator).displayText(rh, decimalFormatter)
        val lastCarbsTime = overviewData.lastCarbsTime
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.iob.text = iobText
            binding.infoLayout.iobLayout.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(app.aaps.core.ui.R.string.iob), iobDialogText) } }
            // cob
            var cobText = displayText ?: rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)

            val constraintsProcessed = loop.lastRun?.constraintsProcessed
            val lastRun = loop.lastRun
            if (config.APS && constraintsProcessed != null && lastRun != null) {
                if (constraintsProcessed.carbsReq > 0) {
                    //only display carbsreq when carbs have not been entered recently
                    if (lastCarbsTime < lastRun.lastAPSRun) {
                        cobText += "\n" + constraintsProcessed.carbsReq + " " + rh.gs(app.aaps.core.ui.R.string.required)
                    }
                    if (carbAnimation?.isRunning == false)
                        carbAnimation?.start()
                } else {
                    carbAnimation?.stop()
                    carbAnimation?.selectDrawable(0)
                }
            }
            binding.infoLayout.cob.text = cobText
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateTemporaryTarget() {
        val units = profileFunction.getUnits()
        val tempTarget = overviewData.temporaryTarget
        runOnUiThread {
            _binding ?: return@runOnUiThread
            if (tempTarget != null) {
                setRibbon(
                    binding.tempTarget,
                    app.aaps.core.ui.R.attr.ribbonTextWarningColor,
                    app.aaps.core.ui.R.attr.ribbonWarningColor,
                    profileUtil.toTargetRangeString(tempTarget.lowTarget, tempTarget.highTarget, GlucoseUnit.MGDL, units) + " " + dateUtil.untilString(tempTarget.end, rh)
                )
            } else {
                profileFunction.getProfile()?.let { profile ->
                    // If the target is not the same as set in the profile then oref has overridden it
                    val targetUsed =
                        if (config.APS) loop.lastRun?.constraintsProcessed?.targetBG ?: 0.0
                        else if (config.NSCLIENT) JsonHelper.safeGetDouble(processedDeviceStatusData.getAPSResult().json, "targetBG")
                        else 0.0

                    if (targetUsed != 0.0 && abs(profile.getTargetMgdl() - targetUsed) > 0.01) {
                        aapsLogger.debug("Adjusted target. Profile: ${profile.getTargetMgdl()} APS: $targetUsed")
                        setRibbon(
                            binding.tempTarget,
                            app.aaps.core.ui.R.attr.ribbonTextWarningColor,
                            app.aaps.core.ui.R.attr.tempTargetBackgroundColor,
                            profileUtil.toTargetRangeString(targetUsed, targetUsed, GlucoseUnit.MGDL, units)
                        )
                    } else {
                        setRibbon(
                            binding.tempTarget,
                            app.aaps.core.ui.R.attr.ribbonTextDefaultColor,
                            app.aaps.core.ui.R.attr.ribbonDefaultColor,
                            profileUtil.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), GlucoseUnit.MGDL, units)
                        )
                    }
                }
            }
        }
    }

    private fun setRibbon(view: TextView, attrResText: Int, attrResBack: Int, text: String) {
        with(view) {
            setText(text)
            setBackgroundColor(rh.gac(context, attrResBack))
            setTextColor(rh.gac(context, attrResText))
            compoundDrawables[0]?.setTint(rh.gac(context, attrResText))
        }
    }

    private fun updateGraph() {
        _binding ?: return
        val pump = activePlugin.activePump
        val graphData = GraphData(injector, binding.graphsLayout.bgGraph, overviewData)
        val menuChartSettings = overviewMenus.setting
        if (menuChartSettings.isEmpty()) return
        graphData.addInRangeArea(overviewData.fromTime, overviewData.endTime, defaultValueHelper.determineLowLine(), defaultValueHelper.determineHighLine())
        graphData.addBgReadings(menuChartSettings[0][OverviewMenus.CharType.PRE.ordinal], context)
        graphData.addBucketedData()
        graphData.addTreatments(context)
        graphData.addEps(context, 0.95)
        if (menuChartSettings[0][OverviewMenus.CharType.TREAT.ordinal])
            graphData.addTherapyEvents()
        if (menuChartSettings[0][OverviewMenus.CharType.ACT.ordinal])
            graphData.addActivity(0.8)
        if ((pump.pumpDescription.isTempBasalCapable || config.NSCLIENT) && menuChartSettings[0][OverviewMenus.CharType.BAS.ordinal])
            graphData.addBasals()
        graphData.addTargetLine()
        graphData.addNowLine(dateUtil.now())

        // set manual x bounds to have nice steps
        graphData.setNumVerticalLabels()
        graphData.formatAxis(overviewData.fromTime, overviewData.endTime)

        graphData.performUpdate()

        // 2nd graphs
        prepareGraphsIfNeeded(menuChartSettings.size)
        val secondaryGraphsData: ArrayList<GraphData> = ArrayList()

        val now = System.currentTimeMillis()
        for (g in 0 until min(secondaryGraphs.size, menuChartSettings.size + 1)) {
            val secondGraphData = GraphData(injector, secondaryGraphs[g], overviewData)
            var useABSForScale = false
            var useIobForScale = false
            var useCobForScale = false
            var useDevForScale = false
            var useRatioForScale = false
            var useDSForScale = false
            var useBGIForScale = false
            var useHRForScale = false
            when {
                menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal]      -> useABSForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]      -> useIobForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal]      -> useCobForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal]      -> useDevForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]      -> useBGIForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal]      -> useRatioForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] -> useDSForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.HR.ordinal]       -> useHRForScale = true
            }
            val alignDevBgiScale = menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal] && menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]

            if (menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal]) secondGraphData.addAbsIob(useABSForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]) secondGraphData.addIob(useIobForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal]) secondGraphData.addCob(useCobForScale, if (useCobForScale) 1.0 else 0.5)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal]) secondGraphData.addDeviations(useDevForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]) secondGraphData.addMinusBGI(useBGIForScale, if (alignDevBgiScale) 1.0 else 0.8)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal]) secondGraphData.addRatio(useRatioForScale, if (useRatioForScale) 1.0 else 0.8)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] && config.isDev()) secondGraphData.addDeviationSlope(
                useDSForScale,
                if (useDSForScale) 1.0 else 0.8,
                useRatioForScale
            )
            if (menuChartSettings[g + 1][OverviewMenus.CharType.HR.ordinal]) secondGraphData.addHeartRate(useHRForScale, if (useHRForScale) 1.0 else 0.8)

            // set manual x bounds to have nice steps
            secondGraphData.formatAxis(overviewData.fromTime, overviewData.endTime)
            secondGraphData.addNowLine(now)
            secondaryGraphsData.add(secondGraphData)
        }
        for (g in 0 until min(secondaryGraphs.size, menuChartSettings.size + 1)) {
            secondaryGraphsLabel[g].text = overviewMenus.enabledTypes(g + 1)
            secondaryGraphs[g].visibility = (
                menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.HR.ordinal]
                ).toVisibility()
            secondaryGraphsData[g].performUpdate()
        }
    }

    private fun updateCalcProgress() {
        _binding ?: return
        binding.progressBar.visibility = (overviewData.calcProgressPct != 100).toVisibility()
        binding.progressBar.progress = overviewData.calcProgressPct
    }

    private fun updateSensitivity() {
        _binding ?: return
        val lastAutosensData = overviewData.lastAutosensData(iobCobCalculator)
        if (config.NSCLIENT && sp.getBoolean(app.aaps.core.utils.R.string.key_used_autosens_on_main_phone, false) ||
            !config.NSCLIENT && constraintChecker.isAutosensModeEnabled().value()
        ) {
            binding.infoLayout.sensitivityIcon.setImageResource(app.aaps.core.main.R.drawable.ic_swap_vert_black_48dp_green)
        } else {
            binding.infoLayout.sensitivityIcon.setImageResource(app.aaps.core.main.R.drawable.ic_x_swap_vert)
        }

        binding.infoLayout.sensitivity.text =
            lastAutosensData?.let {
                String.format(Locale.ENGLISH, "%.0f%%", it.autosensResult.ratio * 100)
            } ?: ""
        // Show variable sensitivity
        val profile = profileFunction.getProfile()
        val request = loop.lastRun?.request
        val isfMgdl = profile?.getIsfMgdl()
        val variableSens =
            if (config.APS && request is VariableSensitivityResult) request.variableSens ?: 0.0
            else if (config.NSCLIENT) JsonHelper.safeGetDouble(processedDeviceStatusData.getAPSResult().json, "variable_sens")
            else 0.0

        if (variableSens != isfMgdl && variableSens != 0.0 && isfMgdl != null) {
            binding.infoLayout.variableSensitivity.text =
                String.format(
                    Locale.getDefault(), "%1$.1f→%2$.1f",
                    profileUtil.fromMgdlToUnits(isfMgdl, profileFunction.getUnits()),
                    profileUtil.fromMgdlToUnits(variableSens, profileFunction.getUnits())
                )
            binding.infoLayout.variableSensitivity.visibility = View.VISIBLE
        } else binding.infoLayout.variableSensitivity.visibility = View.GONE
    }

    private fun updatePumpStatus() {
        _binding ?: return
        val status = overviewData.pumpStatus
        binding.pumpStatus.text = status
        binding.pumpStatusLayout.visibility = (status != "").toVisibility()
    }

    private fun updateNotification() {
        _binding ?: return
        binding.notifications.let { notificationStore.updateNotifications(it) }
    }
}
