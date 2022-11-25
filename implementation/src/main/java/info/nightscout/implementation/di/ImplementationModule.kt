package info.nightscout.implementation.di

import dagger.Binds
import dagger.Module
import info.nightscout.core.graph.OverviewData
import info.nightscout.implementation.AndroidPermissionImpl
import info.nightscout.implementation.BolusTimerImpl
import info.nightscout.implementation.CarbTimerImpl
import info.nightscout.implementation.DefaultValueHelperImpl
import info.nightscout.implementation.HardLimitsImpl
import info.nightscout.implementation.LocalAlertUtilsImpl
import info.nightscout.implementation.TranslatorImpl
import info.nightscout.implementation.TrendCalculatorImpl
import info.nightscout.implementation.UserEntryLoggerImpl
import info.nightscout.implementation.XDripBroadcastImpl
import info.nightscout.implementation.androidNotification.NotificationHolderImpl
import info.nightscout.implementation.db.PersistenceLayerImpl
import info.nightscout.implementation.logging.LoggerUtilsImpl
import info.nightscout.implementation.maintenance.PrefFileListProviderImpl
import info.nightscout.implementation.overview.OverviewDataImpl
import info.nightscout.implementation.plugin.PluginStore
import info.nightscout.implementation.profiling.ProfilerImpl
import info.nightscout.implementation.protection.PasswordCheckImpl
import info.nightscout.implementation.protection.ProtectionCheckImpl
import info.nightscout.implementation.pump.DetailedBolusInfoStorageImpl
import info.nightscout.implementation.pump.PumpSyncImplementation
import info.nightscout.implementation.pump.TemporaryBasalStorageImpl
import info.nightscout.implementation.pump.WarnColorsImpl
import info.nightscout.implementation.queue.CommandQueueImplementation
import info.nightscout.implementation.resources.IconsProviderImplementation
import info.nightscout.implementation.resources.ResourceHelperImpl
import info.nightscout.implementation.stats.DexcomTirCalculatorImpl
import info.nightscout.implementation.stats.TddCalculatorImpl
import info.nightscout.implementation.stats.TirCalculatorImpl
import info.nightscout.interfaces.AndroidPermission
import info.nightscout.interfaces.BolusTimer
import info.nightscout.interfaces.CarbTimer
import info.nightscout.interfaces.LocalAlertUtils
import info.nightscout.interfaces.NotificationHolder
import info.nightscout.interfaces.Translator
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.db.PersistenceLayer
import info.nightscout.interfaces.logging.LoggerUtils
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.maintenance.PrefFileListProvider
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profiling.Profiler
import info.nightscout.interfaces.protection.PasswordCheck
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.pump.DetailedBolusInfoStorage
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.TemporaryBasalStorage
import info.nightscout.interfaces.pump.WarnColors
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.stats.DexcomTirCalculator
import info.nightscout.interfaces.stats.TddCalculator
import info.nightscout.interfaces.stats.TirCalculator
import info.nightscout.interfaces.ui.IconsProvider
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.interfaces.utils.TrendCalculator
import info.nightscout.shared.interfaces.ResourceHelper

@Module(
    includes = [
        CommandQueueModule::class
    ]
)

@Suppress("unused")
open class ImplementationModule {

    @Module
    interface Bindings {
        @Binds fun bindPersistenceLayer(persistenceLayerImpl: PersistenceLayerImpl): PersistenceLayer
        @Binds fun bindActivePlugin(pluginStore: PluginStore): ActivePlugin
        @Binds fun bindPrefFileListProvider(prefFileListProviderImpl: PrefFileListProviderImpl): PrefFileListProvider
        @Binds fun bindOverviewData(overviewData: OverviewDataImpl): OverviewData
        @Binds fun bindUserEntryLogger(userEntryLoggerImpl: UserEntryLoggerImpl): UserEntryLogger
        @Binds fun bindDetailedBolusInfoStorage(detailedBolusInfoStorageImpl: DetailedBolusInfoStorageImpl): DetailedBolusInfoStorage
        @Binds fun bindTemporaryBasalStorage(temporaryBasalStorageImpl: TemporaryBasalStorageImpl): TemporaryBasalStorage
        @Binds fun bindTranslator(translatorImpl: TranslatorImpl): Translator
        @Binds fun bindDefaultValueHelper(defaultValueHelperImpl: DefaultValueHelperImpl): DefaultValueHelper
        @Binds fun bindProtectionCheck(protectionCheckImpl: ProtectionCheckImpl): ProtectionCheck
        @Binds fun bindPasswordCheck(passwordCheckImpl: PasswordCheckImpl): PasswordCheck
        @Binds fun bindLoggerUtils(loggerUtilsImpl: LoggerUtilsImpl): LoggerUtils
        @Binds fun bindProfiler(profilerImpl: ProfilerImpl): Profiler
        @Binds fun bindWarnColors(warnColorsImpl: WarnColorsImpl): WarnColors
        @Binds fun bindHardLimits(hardLimitsImpl: HardLimitsImpl): HardLimits
        @Binds fun bindResourceHelper(resourceHelperImpl: ResourceHelperImpl): ResourceHelper

        @Binds fun bindTrendCalculatorInterface(trendCalculator: TrendCalculatorImpl): TrendCalculator
        @Binds fun bindTddCalculatorInterface(tddCalculator: TddCalculatorImpl): TddCalculator
        @Binds fun bindTirCalculatorInterface(tirCalculator: TirCalculatorImpl): TirCalculator
        @Binds fun bindDexcomTirCalculatorInterface(dexcomTirCalculator: DexcomTirCalculatorImpl): DexcomTirCalculator
        @Binds fun bindPumpSyncInterface(pumpSyncImplementation: PumpSyncImplementation): PumpSync
        @Binds fun bindXDripBroadcastInterface(xDripBroadcastImpl: XDripBroadcastImpl): XDripBroadcast
        @Binds fun bindCarbTimerInterface(carbTimer: CarbTimerImpl): CarbTimer
        @Binds fun bindBolusTimerInterface(bolusTimer: BolusTimerImpl): BolusTimer
        @Binds fun bindAndroidPermissionInterface(androidPermission: AndroidPermissionImpl): AndroidPermission
        @Binds fun bindLocalAlertUtilsInterface(localAlertUtils: LocalAlertUtilsImpl): LocalAlertUtils
        @Binds fun bindIconsProviderInterface(iconsProvider: IconsProviderImplementation): IconsProvider
        @Binds fun bindNotificationHolderInterface(notificationHolder: NotificationHolderImpl): NotificationHolder
        @Binds fun bindCommandQueue(commandQueue: CommandQueueImplementation): CommandQueue
    }
}