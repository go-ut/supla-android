package org.supla.android.usecases.icon
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

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.junit.MockitoJUnitRunner
import org.supla.android.R
import org.supla.android.lib.SuplaConst

@RunWith(MockitoJUnitRunner::class)
class GetDefaultIconResourceUseCaseTest {

  @InjectMocks
  private lateinit var useCase: GetDefaultIconResourceUseCase

  @Test
  fun `should get unknown icon when no handler available`() {
    // given
    val iconData: IconData = mockk {
      every { function } returns SuplaConst.SUPLA_CHANNELFNC_NONE
    }

    // when
    val result = useCase.invoke(iconData)

    // then
    assertThat(result).isEqualTo(R.drawable.ic_unknown_channel)
  }

  @Test
  fun `should get unknown icon when handler available`() {
    // given
    val iconData: IconData = mockk {
      every { function } returns SuplaConst.SUPLA_CHANNELFNC_DEPTHSENSOR
      every { icon(R.drawable.fnc_depth, R.drawable.fnc_depth_nm) } returns R.drawable.fnc_depth
    }

    // when
    val result = useCase.invoke(iconData)

    // then
    assertThat(result).isEqualTo(R.drawable.fnc_depth)
  }
}
