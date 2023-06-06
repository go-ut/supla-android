package org.supla.android.core.networking.suplaclient

import org.supla.android.lib.actions.ActionParameters

interface SuplaClientProvider {
  fun provide(): SuplaClientApi?
}

interface SuplaClientApi {
  fun cancel()

  @Throws(InterruptedException::class)
  fun join()

  fun reconnect()

  fun open(ID: Int, Group: Boolean, Open: Int): Boolean

  fun setRGBW(
    ID: Int,
    Group: Boolean,
    Color: Int,
    ColorBrightness: Int,
    Brightness: Int,
    TurnOnOff: Boolean
  ): Boolean

  fun executeAction(parameters: ActionParameters): Boolean
}