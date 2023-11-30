package org.supla.android.features.thermostatdetail.thermostatgeneral
/*
Copyright (C) AC SOFTWARE SP. Z O.O.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.PublishSubject
import org.supla.android.R
import org.supla.android.Trace
import org.supla.android.core.infrastructure.DateProvider
import org.supla.android.core.networking.suplaclient.DelayableState
import org.supla.android.core.networking.suplaclient.SuplaClientProvider
import org.supla.android.core.ui.BaseViewModel
import org.supla.android.core.ui.BitmapProvider
import org.supla.android.core.ui.StringProvider
import org.supla.android.core.ui.ViewEvent
import org.supla.android.core.ui.ViewState
import org.supla.android.data.ValuesFormatter
import org.supla.android.data.source.local.entity.ChannelRelationType
import org.supla.android.data.source.local.entity.ThermostatValue
import org.supla.android.data.source.local.temperature.TemperatureCorrection
import org.supla.android.data.source.remote.ChannelConfigType
import org.supla.android.data.source.remote.ConfigResult
import org.supla.android.data.source.remote.SuplaDeviceConfig
import org.supla.android.data.source.remote.hvac.SuplaChannelHvacConfig
import org.supla.android.data.source.remote.hvac.SuplaChannelWeeklyScheduleConfig
import org.supla.android.data.source.remote.hvac.SuplaHvacMode
import org.supla.android.data.source.remote.hvac.ThermostatSubfunction
import org.supla.android.data.source.remote.thermostat.SuplaThermostatFlags
import org.supla.android.db.Channel
import org.supla.android.events.ChannelConfigEventsManager
import org.supla.android.events.DeviceConfigEventsManager
import org.supla.android.events.LoadingTimeoutManager
import org.supla.android.events.UpdateEventsManager
import org.supla.android.extensions.TAG
import org.supla.android.extensions.fromSuplaTemperature
import org.supla.android.extensions.getTimerStateValue
import org.supla.android.extensions.guardLet
import org.supla.android.extensions.ifLet
import org.supla.android.extensions.mapMerged
import org.supla.android.features.thermostatdetail.thermostatgeneral.data.SensorIssue
import org.supla.android.features.thermostatdetail.thermostatgeneral.data.ThermostatIssueItem
import org.supla.android.features.thermostatdetail.thermostatgeneral.data.ThermostatProgramInfo
import org.supla.android.features.thermostatdetail.thermostatgeneral.data.build
import org.supla.android.features.thermostatdetail.thermostatgeneral.ui.ThermostatGeneralViewProxy
import org.supla.android.features.thermostatdetail.ui.TimerHeaderState
import org.supla.android.lib.SuplaConst.SUPLA_CHANNELFNC_HVAC_DOMESTIC_HOT_WATER
import org.supla.android.lib.SuplaConst.SUPLA_CHANNELFNC_HVAC_THERMOSTAT
import org.supla.android.lib.SuplaConst.SUPLA_CHANNELFNC_HVAC_THERMOSTAT_AUTO
import org.supla.android.tools.SuplaSchedulers
import org.supla.android.ui.lists.data.IssueIconType
import org.supla.android.usecases.channel.ChannelWithChildren
import org.supla.android.usecases.channel.ReadChannelWithChildrenUseCase
import org.supla.android.usecases.thermostat.CreateTemperaturesListUseCase
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt

private const val REFRESH_DELAY_MS = 3000

@HiltViewModel
class ThermostatGeneralViewModel @Inject constructor(
  private val readChannelWithChildrenUseCase: ReadChannelWithChildrenUseCase,
  private val createTemperaturesListUseCase: CreateTemperaturesListUseCase,
  private val valuesFormatter: ValuesFormatter,
  private val delayedThermostatActionSubject: DelayedThermostatActionSubject,
  private val channelConfigEventsManager: ChannelConfigEventsManager,
  private val deviceConfigEventsManager: DeviceConfigEventsManager,
  private val suplaClientProvider: SuplaClientProvider,
  private val loadingTimeoutManager: LoadingTimeoutManager,
  private val dateProvider: DateProvider,
  private val schedulers: SuplaSchedulers,
  private val updateEventsManager: UpdateEventsManager
) : BaseViewModel<ThermostatGeneralViewState, ThermostatGeneralViewEvent>(ThermostatGeneralViewState(), schedulers),
  ThermostatGeneralViewProxy {

  private val updateSubject: PublishSubject<Int> = PublishSubject.create()
  private val channelSubject: PublishSubject<ChannelWithChildren> = PublishSubject.create()

  override fun onViewCreated() {
    loadingTimeoutManager.watch({ currentState().loadingState }) {
      updateState { state ->
        state.viewModelState?.remoteId?.let {
          triggerDataLoad(it)
        }

        state.copy(loadingState = state.loadingState.changingLoading(false, dateProvider))
      }
    }.disposeBySelf()
  }

  fun observeData(remoteId: Int, deviceId: Int) {
    updateEventsManager.observeChannelsUpdate()
      .debounce(1, TimeUnit.SECONDS)
      .subscribeBy(
        onNext = { triggerDataLoad(remoteId) },
        onError = defaultErrorHandler("observeData($remoteId)")
      )
      .disposeBySelf()

    updateSubject.attachSilent()
      .debounce(1, TimeUnit.SECONDS)
      .subscribeBy(
        onNext = { triggerDataLoad(remoteId) },
        onError = defaultErrorHandler("observeData($remoteId)")
      )
      .disposeBySelf()

    Observable.combineLatest(
      channelSubject.mapMerged { createTemperaturesListUseCase(it) },
      channelConfigEventsManager.observerConfig(remoteId)
        .filter { it.config is SuplaChannelHvacConfig && it.result == ConfigResult.RESULT_TRUE },
      channelConfigEventsManager.observerConfig(remoteId)
        .filter { it.config is SuplaChannelWeeklyScheduleConfig },
      deviceConfigEventsManager.observerConfig(deviceId)
    ) { pair, channelConfig, weeklySchedule, deviceConfig ->
      LoadedData(
        channelWithChildren = pair.first,
        temperatures = pair.second,
        config = channelConfig.config as SuplaChannelHvacConfig,
        weeklySchedule = weeklySchedule.config as SuplaChannelWeeklyScheduleConfig,
        deviceConfig = deviceConfig.config
      )
    }
      .debounce(50, TimeUnit.MILLISECONDS, schedulers.computation)
      .attachSilent()
      .subscribeBy(
        onNext = { handleData(it) },
        onError = defaultErrorHandler("observeData($remoteId)")
      )
      .disposeBySelf()
  }

  fun loadData(remoteId: Int, deviceId: Int) {
    suplaClientProvider.provide()?.getChannelConfig(remoteId, ChannelConfigType.DEFAULT)
    suplaClientProvider.provide()?.getChannelConfig(remoteId, ChannelConfigType.WEEKLY_SCHEDULE)
    suplaClientProvider.provide()?.getDeviceConfig(deviceId)

    triggerDataLoad(remoteId)
  }

  fun triggerDataLoad(remoteId: Int) {
    readChannelWithChildrenUseCase(remoteId)
      .attachSilent()
      .subscribeBy(
        onSuccess = { channelSubject.onNext(it) },
        onError = defaultErrorHandler("triggerDataLoad($remoteId)")
      )
      .disposeBySelf()
  }

  fun loadTemperature(remoteId: Int) {
    val state = currentState()

    if (state.temperatures.firstOrNull { it.remoteId == remoteId } != null) {
      state.viewModelState?.remoteId?.let { triggerDataLoad(it) }
    }
  }

  override fun heatingModeChanged() {
    currentState().viewModelState?.let { viewModelState ->
      val newMode = when (val mode = viewModelState.mode) {
        SuplaHvacMode.AUTO -> SuplaHvacMode.COOL
        SuplaHvacMode.COOL -> SuplaHvacMode.HEAT
        SuplaHvacMode.HEAT -> SuplaHvacMode.COOL
        else -> mode
      }

      val newState = viewModelState.copy(mode = newMode)
      updateState { it.copy(viewModelState = newState) }
      delayedThermostatActionSubject.emit(newState)
    }
  }

  override fun coolingModeChanged() {
    currentState().viewModelState?.let { viewModelState ->
      val newMode = when (val mode = viewModelState.mode) {
        SuplaHvacMode.AUTO -> SuplaHvacMode.HEAT
        SuplaHvacMode.COOL -> SuplaHvacMode.HEAT
        SuplaHvacMode.HEAT -> SuplaHvacMode.COOL
        else -> mode
      }

      val newState = viewModelState.copy(mode = newMode)
      updateState { it.copy(viewModelState = newState) }
      delayedThermostatActionSubject.emit(newState)
    }
  }

  override fun setpointTemperatureChanged(heatPercentage: Float?, coolPercentage: Float?) {
    val state = currentState()

    state.viewModelState?.let { viewModelState ->
      if (heatPercentage != null && heatPercentage != state.setpointHeatTemperaturePercentage) {
        val temperature = getTemperatureForPosition(heatPercentage, viewModelState)

        updateStateForHeatChange(viewModelState.copy(setpointHeatTemperature = temperature, lastChangedHeat = true))
      } else if (coolPercentage != null && coolPercentage != state.setpointCoolTemperaturePercentage) {
        val temperature = getTemperatureForPosition(coolPercentage, viewModelState)

        updateStateForCoolChange(viewModelState.copy(setpointCoolTemperature = temperature, lastChangedHeat = false))
      } else {
        updateState { it.copy(changing = false) }
      }
    }
  }

  override fun changeSetpointTemperature(correction: TemperatureCorrection) {
    val state = currentState()
    state.viewModelState?.let { viewModelState ->
      if (viewModelState.lastChangedHeat) {
        changeHeatTemperature(state, viewModelState, correction.step())
      } else {
        changeCoolTemperature(state, viewModelState, correction.step())
      }
    }
  }

  override fun turnOnOffClicked() {
    val state = currentState()

    state.viewModelState?.let { viewModelState ->
      updateState { it.copy(loadingState = it.loadingState.changingLoading(true, dateProvider), lastInteractionTime = null) }

      val newMode = when {
        state.programmedModeActive && state.isOff -> SuplaHvacMode.OFF
        state.isOff -> SuplaHvacMode.CMD_TURN_ON
        else -> SuplaHvacMode.OFF
      }

      delayedThermostatActionSubject.sendImmediately(
        viewModelState.copy(
          mode = newMode,
          setpointCoolTemperature = null,
          setpointHeatTemperature = null
        )
      )
        .attachSilent()
        .subscribeBy(onError = defaultErrorHandler("turnOnOffClicked()"))
        .disposeBySelf()
    }
  }

  override fun manualModeClicked() {
    val state = currentState()

    state.viewModelState?.let { viewModelState ->
      updateState { it.copy(loadingState = it.loadingState.changingLoading(true, dateProvider), lastInteractionTime = null) }

      delayedThermostatActionSubject.sendImmediately(
        viewModelState.copy(
          mode = SuplaHvacMode.CMD_SWITCH_TO_MANUAL,
          setpointHeatTemperature = null,
          setpointCoolTemperature = null
        )
      )
        .attachSilent()
        .subscribeBy(onError = defaultErrorHandler("manualModeClicked()"))
        .disposeBySelf()
    }
  }

  override fun weeklyScheduledModeClicked() {
    val state = currentState()

    state.viewModelState?.let { viewModelState ->
      updateState { it.copy(loadingState = it.loadingState.changingLoading(true, dateProvider), lastInteractionTime = null) }

      delayedThermostatActionSubject.sendImmediately(
        viewModelState.copy(
          mode = SuplaHvacMode.CMD_WEEKLY_SCHEDULE,
          setpointHeatTemperature = null,
          setpointCoolTemperature = null
        )
      )
        .attachSilent()
        .subscribeBy(onError = defaultErrorHandler("weeklyScheduledModeClicked()"))
        .disposeBySelf()
    }
  }

  override fun getTemperatureText(minPercentage: Float?, maxPercentage: Float?, state: ThermostatGeneralViewState): StringProvider {
    val minTemperature = minPercentage?.let {
      getTemperatureForPosition(it, state.viewModelState!!)
    }
    val maxTemperature = maxPercentage?.let {
      getTemperatureForPosition(it, state.viewModelState!!)
    }

    return state.viewModelState?.let {
      calculateTemperatureControlText(state.isOffline, it.mode, minTemperature, maxTemperature)
    } ?: { "" }
  }

  override fun markChanging() {
    updateState {
      it.copy(
        viewModelState = it.viewModelState?.copy(
          mode = getModeForOffChanges(it, it.viewModelState)
        ),
        changing = true
      )
    }
  }

  private fun getTemperatureForPosition(percentagePosition: Float, viewModelState: ThermostatGeneralViewModelState) =
    viewModelState.configMinTemperature.plus(
      viewModelState.configMaxTemperature.minus(viewModelState.configMinTemperature).times(percentagePosition)
    ).times(10).roundToInt().toFloat().div(10)

  private fun handleData(data: LoadedData) {
    val channel = data.channelWithChildren.channel
    val value = channel.value.asThermostatValue()
    val timerState = channel.getTimerStateValue()

    val setpointHeatTemperature = getSetpointHeatTemperature(channel, value)
    val setpointCoolTemperature = getSetpointCoolTemperature(channel, value)

    val (configMinTemperature) = guardLet(data.config.temperatures.roomMin?.fromSuplaTemperature()) { return }
    val (configMaxTemperature) = guardLet(data.config.temperatures.roomMax?.fromSuplaTemperature()) { return }

    val isOff = channel.onLine.not() || value.mode == SuplaHvacMode.OFF || value.mode == SuplaHvacMode.NOT_SET

    updateState {
      if (it.changing) {
        Trace.d(TAG, "update skipped because of changing")
        return@updateState it // Do not change anything, when user makes manual operations
      }
      if (it.lastInteractionTime != null && it.lastInteractionTime + REFRESH_DELAY_MS > System.currentTimeMillis()) {
        Trace.d(TAG, "update skipped because of last interaction time")
        updateSubject.onNext(0)
        return@updateState it // Do not change anything during 3 secs after last user interaction
      }
      Trace.d(TAG, "updating state with data")

      it.copy(
        viewModelState = ThermostatGeneralViewModelState(
          remoteId = channel.remoteId,
          function = channel.func,
          lastChangedHeat = lastChangedHeat(it.viewModelState, value, setpointHeatTemperature),
          setpointHeatTemperature = setpointHeatTemperature,
          setpointCoolTemperature = setpointCoolTemperature,
          configMinTemperature = configMinTemperature,
          configMaxTemperature = configMaxTemperature,
          mode = value.mode,
          subfunction = value.subfunction,
          timerEndDate = timerState?.countdownEndsAt
        ),

        temperatures = data.temperatures,

        isOffline = !channel.onLine,
        isOff = isOff,
        isAutoFunction = channel.func == SUPLA_CHANNELFNC_HVAC_THERMOSTAT_AUTO,
        heatingModeActive = isHeatingModeActive(channel, value),
        coolingModeActive = isCoolingModeActive(channel, value),

        showHeatingIndicator = channel.onLine && value.state.isOn() && value.flags.contains(SuplaThermostatFlags.HEATING),
        showCoolingIndicator = channel.onLine && value.state.isOn() && value.flags.contains(SuplaThermostatFlags.COOLING),

        configMinTemperatureString = valuesFormatter.getTemperatureString(configMinTemperature),
        configMaxTemperatureString = valuesFormatter.getTemperatureString(configMaxTemperature),

        currentTemperaturePercentage = calculateCurrentTemperature(data.channelWithChildren, configMinTemperature, configMaxTemperature),

        manualModeActive = isOff.not() && value.flags.contains(SuplaThermostatFlags.WEEKLY_SCHEDULE).not(),
        programmedModeActive = channel.onLine && value.flags.contains(SuplaThermostatFlags.WEEKLY_SCHEDULE),

        temporaryChangeActive = channel.onLine && value.flags.contains(SuplaThermostatFlags.WEEKLY_SCHEDULE_TEMPORAL_OVERRIDE),
        temporaryProgramInfo = buildProgramInfo(data.weeklySchedule, data.deviceConfig, value, channel.onLine),

        sensorIssue = SensorIssue.build(value, data.channelWithChildren.children),

        issues = createThermostatIssues(value.flags),

        loadingState = it.loadingState.changingLoading(false, dateProvider)
      )
    }
  }

  private fun buildProgramInfo(
    weeklyConfig: SuplaChannelWeeklyScheduleConfig,
    deviceConfig: SuplaDeviceConfig?,
    value: ThermostatValue,
    channelOnline: Boolean
  ) =
    ThermostatProgramInfo.Builder().also {
      it.dateProvider = this@ThermostatGeneralViewModel.dateProvider
      it.weeklyScheduleConfig = weeklyConfig
      it.deviceConfig = deviceConfig
      it.thermostatFlags = value.flags
      it.currentMode = value.mode
      it.currentTemperature = when (value.subfunction) {
        ThermostatSubfunction.HEAT -> value.setpointTemperatureHeat
        ThermostatSubfunction.COOL -> value.setpointTemperatureCool
        else -> null
      }
      it.channelOnline = channelOnline
    }
      .build()

  private fun isHeatingModeActive(channel: Channel, value: ThermostatValue) =
    channel.func == SUPLA_CHANNELFNC_HVAC_THERMOSTAT_AUTO &&
      (value.mode == SuplaHvacMode.AUTO || value.mode == SuplaHvacMode.HEAT)

  private fun isCoolingModeActive(channel: Channel, value: ThermostatValue) =
    channel.func == SUPLA_CHANNELFNC_HVAC_THERMOSTAT_AUTO &&
      (value.mode == SuplaHvacMode.AUTO || value.mode == SuplaHvacMode.COOL)

  private fun calculateTemperatureControlText(
    isOffline: Boolean,
    mode: SuplaHvacMode,
    setpointMinTemperature: Float?,
    setpointMaxTemperature: Float?
  ): StringProvider {
    return when {
      isOffline -> { resources -> resources.getString(R.string.offline) }
      mode == SuplaHvacMode.NOT_SET || mode == SuplaHvacMode.OFF -> { resources ->
        resources.getString(R.string.thermostat_detail_off).lowercase()
      }

      else -> { _ -> getOnlineTemperatureText(setpointMinTemperature, setpointMaxTemperature) }
    }
  }

  private fun getOnlineTemperatureText(setpointMinTemperature: Float?, setpointMaxTemperature: Float?): String {
    val setPointMinTemperatureString = setpointMinTemperature?.let { valuesFormatter.getTemperatureString(it.toDouble()) }
    val setPointMaxTemperatureString = setpointMaxTemperature?.let { valuesFormatter.getTemperatureString(it.toDouble()) }

    return when {
      setPointMinTemperatureString != null && setPointMaxTemperatureString != null ->
        "$setPointMinTemperatureString - $setPointMaxTemperatureString"

      setPointMinTemperatureString != null -> setPointMinTemperatureString
      setPointMaxTemperatureString != null -> setPointMaxTemperatureString
      else -> ""
    }
  }

  private fun calculateCurrentTemperature(data: ChannelWithChildren, configMinTemperature: Float, configMaxTemperature: Float): Float? {
    return data.children.firstOrNull { it.relationType == ChannelRelationType.MAIN_THERMOMETER }?.let {
      val temperature = it.channel.value.getTemp(it.channel.func)

      if (temperature < -273) {
        null
      } else if (temperature < configMinTemperature) {
        0f
      } else if (temperature > configMaxTemperature) {
        1f
      } else {
        val range = configMaxTemperature - configMinTemperature
        temperature.minus(configMinTemperature).div(range).toFloat()
      }
    }
  }

  private fun getSetpointHeatTemperature(channel: Channel, thermostatValue: ThermostatValue): Float? {
    val setpointSet = thermostatValue.flags.contains(SuplaThermostatFlags.SETPOINT_TEMP_MIN_SET)
    if (channel.func == SUPLA_CHANNELFNC_HVAC_DOMESTIC_HOT_WATER && setpointSet) {
      return thermostatValue.setpointTemperatureHeat
    }
    if (channel.func == SUPLA_CHANNELFNC_HVAC_THERMOSTAT_AUTO && setpointSet) {
      return thermostatValue.setpointTemperatureHeat
    }
    val isHeatSubfunction = thermostatValue.subfunction == ThermostatSubfunction.HEAT
    if (channel.func == SUPLA_CHANNELFNC_HVAC_THERMOSTAT && isHeatSubfunction && setpointSet) {
      return thermostatValue.setpointTemperatureHeat
    }

    return null
  }

  private fun getSetpointCoolTemperature(channel: Channel, thermostatValue: ThermostatValue): Float? {
    val setpointSet = thermostatValue.flags.contains(SuplaThermostatFlags.SETPOINT_TEMP_MAX_SET)
    if (channel.func == SUPLA_CHANNELFNC_HVAC_THERMOSTAT_AUTO && setpointSet) {
      return thermostatValue.setpointTemperatureCool
    }
    val isCoolSubfunction = thermostatValue.subfunction == ThermostatSubfunction.COOL
    if (channel.func == SUPLA_CHANNELFNC_HVAC_THERMOSTAT && isCoolSubfunction && setpointSet) {
      return thermostatValue.setpointTemperatureCool
    }

    return null
  }

  private fun changeHeatTemperature(state: ThermostatGeneralViewState, viewModelState: ThermostatGeneralViewModelState, step: Float) {
    viewModelState.setpointHeatTemperature?.let {
      val minTemperature = viewModelState.configMinTemperature
      val maxTemperature = viewModelState.configMaxTemperature
      val temperature = it.plus(step).let { temperature ->
        if (temperature < minTemperature) {
          minTemperature
        } else if (temperature > maxTemperature) {
          maxTemperature
        } else {
          temperature
        }
      }

      updateStateForHeatChange(
        viewModelState.copy(
          setpointHeatTemperature = temperature,
          mode = getModeForOffChanges(state, viewModelState)
        )
      )
    }
  }

  private fun updateStateForHeatChange(viewModelState: ThermostatGeneralViewModelState) {
    updateState { state ->
      if (state.programmedModeActive) {
        delayedThermostatActionSubject.emit(viewModelState.copy(mode = SuplaHvacMode.NOT_SET))
      } else {
        delayedThermostatActionSubject.emit(viewModelState)
      }

      state.copy(
        viewModelState = viewModelState,
        lastInteractionTime = dateProvider.currentTimestamp(),
        changing = false
      )
    }
  }

  private fun changeCoolTemperature(state: ThermostatGeneralViewState, viewModelState: ThermostatGeneralViewModelState, step: Float) {
    viewModelState.setpointCoolTemperature?.let {
      val minTemperature = viewModelState.configMinTemperature
      val maxTemperature = viewModelState.configMaxTemperature
      val temperature = it.plus(step).let { temperature ->
        if (temperature < minTemperature) {
          minTemperature
        } else if (temperature > maxTemperature) {
          maxTemperature
        } else {
          temperature
        }
      }

      updateStateForCoolChange(
        viewModelState.copy(
          setpointCoolTemperature = temperature,
          mode = getModeForOffChanges(state, viewModelState)
        )
      )
    }
  }

  private fun updateStateForCoolChange(viewModelState: ThermostatGeneralViewModelState) {
    updateState { state ->
      if (state.programmedModeActive) {
        delayedThermostatActionSubject.emit(viewModelState.copy(mode = SuplaHvacMode.NOT_SET))
      } else {
        delayedThermostatActionSubject.emit(viewModelState)
      }

      state.copy(
        viewModelState = viewModelState,
        lastInteractionTime = dateProvider.currentTimestamp(),
        changing = false
      )
    }
  }

  private fun createThermostatIssues(flags: List<SuplaThermostatFlags>): List<ThermostatIssueItem> =
    mutableListOf<ThermostatIssueItem>().apply {
      if (flags.contains(SuplaThermostatFlags.THERMOMETER_ERROR)) {
        add(ThermostatIssueItem(IssueIconType.ERROR, R.string.thermostat_thermometer_error))
      }
      if (flags.contains(SuplaThermostatFlags.CLOCK_ERROR)) {
        add(ThermostatIssueItem(IssueIconType.WARNING, R.string.thermostat_clock_error))
      }
    }

  private fun getModeForOffChanges(state: ThermostatGeneralViewState, modelState: ThermostatGeneralViewModelState): SuplaHvacMode =
    if (modelState.mode == SuplaHvacMode.OFF && state.isOffline.not() && state.programmedModeActive) {
      if (modelState.lastChangedHeat) {
        SuplaHvacMode.HEAT
      } else {
        SuplaHvacMode.COOL
      }
    } else {
      modelState.mode
    }

  private fun lastChangedHeat(state: ThermostatGeneralViewModelState?, value: ThermostatValue, setpointHeatTemperature: Float?): Boolean {
    return if (state == null) {
      (setpointHeatTemperature != null)
    } else if (state.subfunction != null && state.subfunction != value.subfunction) {
      value.subfunction == ThermostatSubfunction.HEAT
    } else {
      state.lastChangedHeat
    }
  }

  private data class LoadedData(
    val channelWithChildren: ChannelWithChildren,
    val temperatures: List<MeasurementValue>,
    val config: SuplaChannelHvacConfig,
    val weeklySchedule: SuplaChannelWeeklyScheduleConfig,
    val deviceConfig: SuplaDeviceConfig?
  )
}

sealed class ThermostatGeneralViewEvent : ViewEvent

data class ThermostatGeneralViewState(
  val viewModelState: ThermostatGeneralViewModelState? = null,

  val temperatures: List<MeasurementValue> = emptyList(),

  val isOffline: Boolean = false,
  val isOff: Boolean = false,

  val isAutoFunction: Boolean = false,
  val heatingModeActive: Boolean = false,
  val coolingModeActive: Boolean = false,
  val showHeatingIndicator: Boolean = false,
  val showCoolingIndicator: Boolean = false,

  val configMinTemperatureString: String = "",
  val configMaxTemperatureString: String = "",

  val currentTemperaturePercentage: Float? = null,

  val manualModeActive: Boolean = false,
  val programmedModeActive: Boolean = false,

  val temporaryChangeActive: Boolean = false,
  val temporaryProgramInfo: List<ThermostatProgramInfo> = emptyList(),

  val sensorIssue: SensorIssue? = null,

  val issues: List<ThermostatIssueItem> = emptyList(),

  val loadingState: LoadingTimeoutManager.LoadingState = LoadingTimeoutManager.LoadingState(),
  val lastInteractionTime: Long? = null,
  val changing: Boolean = false
) : ViewState(), TimerHeaderState {

  val setpointHeatTemperaturePercentage: Float?
    get() {
      ifLet(
        viewModelState?.setpointHeatTemperature,
        viewModelState?.configMinTemperature,
        viewModelState?.configMaxTemperature
      ) { (heat, min, max) ->
        return when {
          viewModelState?.mode == SuplaHvacMode.HEAT || viewModelState?.mode == SuplaHvacMode.AUTO ->
            heat.minus(min).div(max - min)

          viewModelState?.mode == SuplaHvacMode.OFF && programmedModeActive ->
            heat.minus(min).div(max - min)

          else -> null
        }
      }
      return null
    }

  val setpointCoolTemperaturePercentage: Float?
    get() {
      ifLet(
        viewModelState?.setpointCoolTemperature,
        viewModelState?.configMinTemperature,
        viewModelState?.configMaxTemperature
      ) { (cool, min, max) ->
        return when {
          viewModelState?.mode == SuplaHvacMode.COOL || viewModelState?.mode == SuplaHvacMode.AUTO ->
            cool.minus(min).div(max - min)

          viewModelState?.mode == SuplaHvacMode.OFF && programmedModeActive ->
            cool.minus(min).div(max - min)

          else -> null
        }
      }
      return null
    }

  val canDecreaseTemperature: Boolean
    get() {
      ifLet(viewModelState) { (state) ->
        return if (state.lastChangedHeat) {
          state.setpointHeatTemperature?.let { it > state.configMinTemperature } ?: false
        } else {
          state.setpointCoolTemperature?.let { it > state.configMinTemperature } ?: false
        }
      }

      return false
    }

  val canIncreaseTemperature: Boolean
    get() {
      ifLet(viewModelState) { (state) ->
        return if (state.lastChangedHeat) {
          state.setpointHeatTemperature?.let { it < state.configMaxTemperature } ?: false
        } else {
          state.setpointCoolTemperature?.let { it < state.configMaxTemperature } ?: false
        }
      }

      return false
    }

  override val endDateText: StringProvider
    get() = TimerHeaderState.endDateText(viewModelState?.timerEndDate)

  override val currentStateIcon: Int?
    get() = TimerHeaderState.currentStateIcon(viewModelState?.mode)

  override val currentStateIconColor: Int
    get() = TimerHeaderState.currentStateIconColor(viewModelState?.mode)

  override val currentStateValue: StringProvider
    get() = TimerHeaderState.currentStateValue(
      viewModelState?.mode,
      viewModelState?.setpointHeatTemperature,
      viewModelState?.setpointCoolTemperature
    )
}

data class MeasurementValue(
  val remoteId: Int,
  val iconProvider: BitmapProvider,
  val valueStringProvider: StringProvider
)

data class ThermostatGeneralViewModelState(
  val remoteId: Int,
  val function: Int,
  val lastChangedHeat: Boolean,
  val configMinTemperature: Float,
  val configMaxTemperature: Float,
  val mode: SuplaHvacMode,
  val setpointHeatTemperature: Float? = null,
  val setpointCoolTemperature: Float? = null,
  val subfunction: ThermostatSubfunction? = null,
  val timerEndDate: Date? = null,
  override val sent: Boolean = false
) : DelayableState {

  override fun sentState(): DelayableState = copy(sent = true)
  override fun delayableCopy(): DelayableState = copy()
}
