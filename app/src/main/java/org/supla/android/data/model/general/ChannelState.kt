package org.supla.android.data.model.general
/*
 Copyright (C) AC SOFTWARE SP. Z O.O.

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the``````
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

data class ChannelState(
  val value: Value,
  val complex: List<Value>? = null
) {

  enum class Value {
    // active states
    OPEN,
    ON,
    TRANSPARENT,

    // inactive states
    PARTIALLY_OPENED,
    CLOSED,
    OFF,
    OPAQUE,

    // thermostat
    HEAT,
    COOL,

    // others
    NOT_USED,
    COMPLEX
  }

  fun isActive(): Boolean {
    return when (value) {
      Value.CLOSED, Value.ON, Value.TRANSPARENT -> true
      else -> false
    }
  }
}
