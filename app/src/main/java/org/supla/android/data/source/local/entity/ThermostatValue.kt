package org.supla.android.data.source.local.entity
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

import androidx.annotation.DrawableRes
import org.supla.android.R
import org.supla.android.data.ValuesFormatter
import org.supla.android.data.source.remote.hvac.SuplaHvacMode
import org.supla.android.data.source.remote.hvac.ThermostatSubfunction
import org.supla.android.data.source.remote.thermostat.SuplaThermostatFlags
import org.supla.android.data.source.remote.thermostat.SuplaThermostatFlags.HEAT_OR_COOL
import org.supla.android.extensions.fromSuplaTemperature
import org.supla.android.extensions.toShort
import org.supla.android.extensions.toShortVararg
import org.supla.android.ui.lists.data.IssueIconType

@Suppress("DataClassPrivateConstructor")
data class ThermostatValue private constructor(
  val online: Boolean,
  val state: ThermostatState,
  val mode: SuplaHvacMode,
  val setpointTemperatureHeat: Float,
  val setpointTemperatureCool: Float,
  val flags: List<SuplaThermostatFlags>
) {

  val subfunction: ThermostatSubfunction
    get() = if (flags.contains(HEAT_OR_COOL)) ThermostatSubfunction.COOL else ThermostatSubfunction.HEAT

  @DrawableRes
  fun getIndicatorIcon() = when {
    online && flags.contains(SuplaThermostatFlags.FORCED_OFF_BY_SENSOR) -> R.drawable.ic_sensor_alert
    online && flags.contains(SuplaThermostatFlags.COOLING) -> R.drawable.ic_cooling
    online && flags.contains(SuplaThermostatFlags.HEATING) -> R.drawable.ic_heating
    online && mode != SuplaHvacMode.OFF -> R.drawable.ic_standby
    else -> null
  }

  fun getIssueIconType() = when {
    online && flags.contains(SuplaThermostatFlags.THERMOMETER_ERROR) -> IssueIconType.ERROR
    online && flags.contains(SuplaThermostatFlags.CLOCK_ERROR) -> IssueIconType.WARNING
    else -> null
  }

  fun getSetpointText(valuesFormatter: ValuesFormatter): String {
    val temperatureMin = valuesFormatter.getTemperatureString(setpointTemperatureHeat, true)
    val temperatureMax = valuesFormatter.getTemperatureString(setpointTemperatureCool, true)
    return when {
      online.not() -> ""
      mode == SuplaHvacMode.COOL -> temperatureMax
      mode == SuplaHvacMode.HEAT_COOL -> "$temperatureMin - $temperatureMax"
      mode == SuplaHvacMode.HEAT -> temperatureMin
      mode == SuplaHvacMode.OFF -> "Off"
      else -> ""
    }
  }

  fun getIssueMessage(): Int? {
    return if (flags.contains(SuplaThermostatFlags.THERMOMETER_ERROR)) {
      R.string.thermostat_thermometer_error
    } else if (flags.contains(SuplaThermostatFlags.CLOCK_ERROR)) {
      R.string.thermostat_clock_error
    } else {
      null
    }
  }

  companion object {
    fun from(online: Boolean, bytes: ByteArray): ThermostatValue {
      return ThermostatValue(
        online = online,
        state = ThermostatState(bytes[0].toShort()),
        mode = SuplaHvacMode.from(bytes[1]),
        setpointTemperatureHeat = bytes.toTemperature(2, 3),
        setpointTemperatureCool = bytes.toTemperature(4, 5),
        flags = SuplaThermostatFlags.from(bytes.toShortVararg(6, 7))
      )
    }
  }
}

data class ThermostatState(val value: Short) {
  fun isOn() = value > 0
  fun isOff() = value.toInt() == 0
}

private fun ByteArray.toTemperature(vararg byteIndices: Int): Float {
  val bytes = ByteArray(byteIndices.size)
  byteIndices.sorted().forEachIndexed { index, byte -> bytes[index] = this[byte] }
  return toShort(byteIndices).fromSuplaTemperature()
}
