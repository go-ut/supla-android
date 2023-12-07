package org.supla.android.usecases.channel
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

import com.github.mikephil.charting.data.Entry
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.verifyZeroInteractions
import org.mockito.kotlin.whenever
import org.supla.android.R
import org.supla.android.data.model.chart.ChartDataAggregation
import org.supla.android.data.model.chart.ChartEntryType
import org.supla.android.data.model.chart.HistoryDataSet
import org.supla.android.data.source.TemperatureAndHumidityLogRepository
import org.supla.android.data.source.TemperatureLogRepository
import org.supla.android.data.source.local.entity.TemperatureAndHumidityLogEntity
import org.supla.android.data.source.local.entity.TemperatureLogEntity
import org.supla.android.db.Channel
import org.supla.android.extensions.date
import org.supla.android.extensions.toTimestamp
import org.supla.android.lib.SuplaConst
import java.util.Calendar

@RunWith(MockitoJUnitRunner::class)
class LoadChannelMeasurementsUseCaseTest : BaseLoadMeasurementsUseCaseTest() {

  @Mock
  private lateinit var readChannelByRemoteIdUseCase: ReadChannelByRemoteIdUseCase

  @Mock
  private lateinit var temperatureLogRepository: TemperatureLogRepository

  @Mock
  private lateinit var temperatureAndHumidityLogRepository: TemperatureAndHumidityLogRepository

  @Mock
  private lateinit var getChannelValueUseCase: GetChannelValueUseCase

  @InjectMocks
  private lateinit var useCase: LoadChannelMeasurementsUseCase

  @Test
  fun `should load temperature measurements`() {
    val entities = mockEntities(10, 10 * MINUTE_IN_MILLIS)
    val entryDetails = EntryDetails(ChartDataAggregation.MINUTES, ChartEntryType.TEMPERATURE, null, null)

    doTemperatureTest(entities, ChartDataAggregation.MINUTES) { entries ->
      assertThat(entries)
        .extracting({ it.x }, { it.y }, { it.data })
        .containsExactlyElementsOf(entities.map { tuple(it.date.toTimestamp().toFloat(), it.temperature, entryDetails) })
    }
  }

  @Test
  fun `should load temperature measurements (hours aggregation)`() {
    val entities = mockEntities(10, 10 * MINUTE_IN_MILLIS)

    doTemperatureTest(entities, ChartDataAggregation.HOURS) { entries ->
      assertThat(entries)
        .extracting({ it.x }, { it.y }, { it.data })
        .containsExactlyElementsOf(
          listOf(
            tuple(
              date(2022, 10, 11, 0, 30).toTimestamp().toFloat(),
              2.5f,
              EntryDetails(ChartDataAggregation.HOURS, ChartEntryType.TEMPERATURE, 0f, 5f)
            ),
            tuple(
              date(2022, 10, 11, 1, 30).toTimestamp().toFloat(),
              8f,
              EntryDetails(ChartDataAggregation.HOURS, ChartEntryType.TEMPERATURE, 6f, 10f)
            )
          )
        )
    }
  }

  @Test
  fun `should load temperature measurements (days aggregation)`() {
    val entities = mockEntities(10, 10 * MINUTE_IN_MILLIS)

    doTemperatureTest(entities, ChartDataAggregation.DAYS) { entries ->
      assertThat(entries)
        .extracting({ it.x }, { it.y }, { it.data })
        .containsExactlyElementsOf(
          listOf(
            tuple(
              date(2022, 10, 11, 12).toTimestamp().toFloat(),
              5f,
              EntryDetails(ChartDataAggregation.DAYS, ChartEntryType.TEMPERATURE, 0f, 10f)
            )
          )
        )
    }
  }

  @Test
  fun `should load temperature measurements (months aggregation)`() {
    val entities = mockEntities(10, 10 * MINUTE_IN_MILLIS)

    doTemperatureTest(entities, ChartDataAggregation.MONTHS) { entries ->
      assertThat(entries)
        .extracting({ it.x }, { it.y }, { it.data })
        .containsExactlyElementsOf(
          listOf(
            tuple(
              date(2022, 10, 15).toTimestamp().toFloat(),
              5f,
              EntryDetails(ChartDataAggregation.MONTHS, ChartEntryType.TEMPERATURE, 0f, 10f)
            )
          )
        )
    }
  }

  @Test
  fun `should load temperature measurements (year aggregation)`() {
    val entities = mockEntities(10, 10 * MINUTE_IN_MILLIS)

    doTemperatureTest(entities, ChartDataAggregation.YEARS) { entries ->
      assertThat(entries)
        .extracting({ it.x }, { it.y }, { it.data })
        .containsExactlyElementsOf(
          listOf(
            tuple(
              date(2022, Calendar.JULY, 1).toTimestamp().toFloat(),
              5f,
              EntryDetails(ChartDataAggregation.YEARS, ChartEntryType.TEMPERATURE, 0f, 10f)
            )
          )
        )
    }
  }

  @Test
  fun `should load temperature with humidity measurements`() {
    val entities = mockEntitiesWithHumidity(10, 10 * MINUTE_IN_MILLIS)
    val temperatureEntryDetails = EntryDetails(ChartDataAggregation.MINUTES, ChartEntryType.TEMPERATURE, null, null)
    val humidityEntryDetails = EntryDetails(ChartDataAggregation.MINUTES, ChartEntryType.HUMIDITY, null, null)

    doTemperatureWithHumidityTest(
      entities,
      ChartDataAggregation.MINUTES,
      { entries ->
        assertThat(entries)
          .extracting({ it.x }, { it.y }, { it.data })
          .containsExactlyElementsOf(entities.map { tuple(it.date.toTimestamp().toFloat(), it.temperature, temperatureEntryDetails) })
      },
      { entries ->
        assertThat(entries)
          .extracting({ it.x }, { it.y }, { it.data })
          .containsExactlyElementsOf(entities.map { tuple(it.date.toTimestamp().toFloat(), it.humidity, humidityEntryDetails) })
      }
    )
  }

  @Test
  fun `should load temperature with humidity measurements (hours aggregation)`() {
    val entities = mockEntitiesWithHumidity(10, 10 * MINUTE_IN_MILLIS)

    doTemperatureWithHumidityTest(
      entities,
      ChartDataAggregation.HOURS,
      { entries ->
        assertThat(entries)
          .extracting({ it.x }, { it.y }, { it.data })
          .containsExactlyElementsOf(
            listOf(
              tuple(
                date(2022, 10, 11, 0, 30).toTimestamp().toFloat(),
                2.5f,
                EntryDetails(ChartDataAggregation.HOURS, ChartEntryType.TEMPERATURE, 0f, 5f)
              ),
              tuple(
                date(2022, 10, 11, 1, 30).toTimestamp().toFloat(),
                8f,
                EntryDetails(ChartDataAggregation.HOURS, ChartEntryType.TEMPERATURE, 6f, 10f)
              )
            )
          )
      },
      { entries ->
        assertThat(entries)
          .extracting({ it.x }, { it.y }, { it.data })
          .containsExactlyElementsOf(
            listOf(
              tuple(
                date(2022, 10, 11, 0, 30).toTimestamp().toFloat(),
                7.5f,
                EntryDetails(ChartDataAggregation.HOURS, ChartEntryType.HUMIDITY, 5f, 10f)
              ),
              tuple(
                date(2022, 10, 11, 1, 30).toTimestamp().toFloat(),
                2.0f,
                EntryDetails(ChartDataAggregation.HOURS, ChartEntryType.HUMIDITY, 0f, 4f)
              )
            )
          )
      }
    )
  }

  @Test
  fun `should load temperature with humidity measurements (days aggregation)`() {
    val entities = mockEntitiesWithHumidity(10, 10 * MINUTE_IN_MILLIS)

    doTemperatureWithHumidityTest(
      entities,
      ChartDataAggregation.DAYS,
      { entries ->
        assertThat(entries)
          .extracting({ it.x }, { it.y }, { it.data })
          .containsExactlyElementsOf(
            listOf(
              tuple(
                date(2022, 10, 11, 12).toTimestamp().toFloat(),
                5f,
                EntryDetails(ChartDataAggregation.DAYS, ChartEntryType.TEMPERATURE, 0f, 10f)
              )
            )
          )
      },
      { entries ->
        assertThat(entries)
          .extracting({ it.x }, { it.y }, { it.data })
          .containsExactlyElementsOf(
            listOf(
              tuple(
                date(2022, 10, 11, 12).toTimestamp().toFloat(),
                5f,
                EntryDetails(ChartDataAggregation.DAYS, ChartEntryType.HUMIDITY, 0f, 10f)
              )
            )
          )
      }
    )
  }

  @Test
  fun `should load temperature with humidity measurements (months aggregation)`() {
    val entities = mockEntitiesWithHumidity(10, 10 * MINUTE_IN_MILLIS)

    doTemperatureWithHumidityTest(
      entities,
      ChartDataAggregation.MONTHS,
      { entries ->
        assertThat(entries)
          .extracting({ it.x }, { it.y }, { it.data })
          .containsExactlyElementsOf(
            listOf(
              tuple(
                date(2022, 10, 15).toTimestamp().toFloat(),
                5f,
                EntryDetails(ChartDataAggregation.MONTHS, ChartEntryType.TEMPERATURE, 0f, 10f)
              )
            )
          )
      },
      { entries ->
        assertThat(entries)
          .extracting({ it.x }, { it.y }, { it.data })
          .containsExactlyElementsOf(
            listOf(
              tuple(
                date(2022, 10, 15).toTimestamp().toFloat(),
                5f,
                EntryDetails(ChartDataAggregation.MONTHS, ChartEntryType.HUMIDITY, 0f, 10f)
              )
            )
          )
      }
    )
  }

  @Test
  fun `should load temperature with humidity measurements (year aggregation)`() {
    val entities = mockEntitiesWithHumidity(10, 10 * MINUTE_IN_MILLIS)

    doTemperatureWithHumidityTest(
      entities,
      ChartDataAggregation.YEARS,
      { entries ->
        assertThat(entries)
          .extracting({ it.x }, { it.y }, { it.data })
          .containsExactlyElementsOf(
            listOf(
              tuple(
                date(2022, Calendar.JULY, 1).toTimestamp().toFloat(),
                5f,
                EntryDetails(ChartDataAggregation.YEARS, ChartEntryType.TEMPERATURE, 0f, 10f)
              )
            )
          )
      },
      { entries ->
        assertThat(entries)
          .extracting({ it.x }, { it.y }, { it.data })
          .containsExactlyElementsOf(
            listOf(
              tuple(
                date(2022, Calendar.JULY, 1).toTimestamp().toFloat(),
                5f,
                EntryDetails(ChartDataAggregation.YEARS, ChartEntryType.HUMIDITY, 0f, 10f)
              )
            )
          )
      }
    )
  }

  @Test
  fun `should separate entries when there is a gab in dates`() {
    // given
    val remoteId = 123
    val profileId = 321L
    val startDate = date(2022, 10, 11)
    val endDate = date(2022, 11, 11)
    val channel: Channel = mockk()
    val aggregation = ChartDataAggregation.MINUTES
    every { channel.remoteId } returns remoteId
    every { channel.func } returns SuplaConst.SUPLA_CHANNELFNC_THERMOMETER

    val entities = mockEntities(10, 20 * MINUTE_IN_MILLIS)
    whenever(readChannelByRemoteIdUseCase.invoke(remoteId)).thenReturn(Maybe.just(channel))
    whenever(temperatureLogRepository.findMeasurements(remoteId, profileId, startDate, endDate))
      .thenReturn(Observable.just(entities))

    // when
    val testObserver = useCase.invoke(remoteId, profileId, startDate, endDate, aggregation).test()

    // then
    testObserver.assertComplete()
    testObserver.assertValueCount(1)

    val result = testObserver.values()[0]
    assertThat(result).extracting({ it.setId }, { it.color }, { it.active })
      .containsExactly(
        tuple(HistoryDataSet.Id(remoteId, ChartEntryType.TEMPERATURE), R.color.chart_temperature_1, true)
      )

    val entryDetails = EntryDetails(aggregation, ChartEntryType.TEMPERATURE, null, null)
    assertThat(result[0].entries)
      .extracting({ it[0].x }, { it[0].y }, { it[0].data })
      .containsExactlyElementsOf(entities.map { tuple(it.date.toTimestamp().toFloat(), it.temperature, entryDetails) })

    verify(readChannelByRemoteIdUseCase).invoke(remoteId)
    verify(temperatureLogRepository).findMeasurements(remoteId, profileId, startDate, endDate)
    verifyNoMoreInteractions(readChannelByRemoteIdUseCase, temperatureLogRepository)
    verifyZeroInteractions(temperatureAndHumidityLogRepository)
  }

  private fun doTemperatureTest(
    entities: List<TemperatureLogEntity>,
    aggregation: ChartDataAggregation,
    entriesAssertion: (List<Entry>) -> Unit
  ) {
    // given
    val remoteId = 123
    val profileId = 321L
    val startDate = date(2022, 10, 11)
    val endDate = date(2022, 11, 11)
    val channel: Channel = mockk()
    every { channel.remoteId } returns remoteId
    every { channel.func } returns SuplaConst.SUPLA_CHANNELFNC_THERMOMETER

    whenever(readChannelByRemoteIdUseCase.invoke(remoteId)).thenReturn(Maybe.just(channel))
    whenever(temperatureLogRepository.findMeasurements(remoteId, profileId, startDate, endDate))
      .thenReturn(Observable.just(entities))

    // when
    val testObserver = useCase.invoke(remoteId, profileId, startDate, endDate, aggregation).test()

    // then
    testObserver.assertComplete()
    testObserver.assertValueCount(1)

    val result = testObserver.values()[0]
    assertThat(result).extracting({ it.setId }, { it.color }, { it.active })
      .containsExactly(
        tuple(HistoryDataSet.Id(remoteId, ChartEntryType.TEMPERATURE), R.color.chart_temperature_1, true)
      )

    entriesAssertion(result[0].entries[0])

    verify(readChannelByRemoteIdUseCase).invoke(remoteId)
    verify(temperatureLogRepository).findMeasurements(remoteId, profileId, startDate, endDate)
    verifyNoMoreInteractions(readChannelByRemoteIdUseCase, temperatureLogRepository)
    verifyZeroInteractions(temperatureAndHumidityLogRepository)
  }

  private fun doTemperatureWithHumidityTest(
    entities: List<TemperatureAndHumidityLogEntity>,
    aggregation: ChartDataAggregation,
    temperatureEntriesAssertion: (List<Entry>) -> Unit,
    humidityEntriesAssertion: (List<Entry>) -> Unit
  ) {
    // given
    val remoteId = 123
    val profileId = 321L
    val startDate = date(2022, 10, 11)
    val endDate = date(2022, 11, 11)
    val channel: Channel = mockk()
    every { channel.remoteId } returns remoteId
    every { channel.func } returns SuplaConst.SUPLA_CHANNELFNC_HUMIDITYANDTEMPERATURE

    whenever(readChannelByRemoteIdUseCase.invoke(remoteId)).thenReturn(Maybe.just(channel))
    whenever(temperatureAndHumidityLogRepository.findMeasurements(remoteId, profileId, startDate, endDate))
      .thenReturn(Observable.just(entities))

    // when
    val testObserver = useCase.invoke(remoteId, profileId, startDate, endDate, aggregation).test()

    // then
    testObserver.assertComplete()
    testObserver.assertValueCount(1)

    val result = testObserver.values()[0]
    assertThat(result).extracting({ it.setId }, { it.color }, { it.active })
      .containsExactly(
        tuple(HistoryDataSet.Id(remoteId, ChartEntryType.TEMPERATURE), R.color.chart_temperature_1, true),
        tuple(HistoryDataSet.Id(remoteId, ChartEntryType.HUMIDITY), R.color.chart_humidity_1, true)
      )

    temperatureEntriesAssertion(result[0].entries[0])
    humidityEntriesAssertion(result[1].entries[0])

    verify(readChannelByRemoteIdUseCase).invoke(remoteId)
    verify(temperatureAndHumidityLogRepository).findMeasurements(remoteId, profileId, startDate, endDate)
    verifyNoMoreInteractions(readChannelByRemoteIdUseCase, temperatureAndHumidityLogRepository)
    verifyZeroInteractions(temperatureLogRepository)
  }
}
