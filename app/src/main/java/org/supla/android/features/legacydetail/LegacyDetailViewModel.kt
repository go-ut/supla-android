package org.supla.android.features.legacydetail

import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.supla.android.core.ui.BaseViewModel
import org.supla.android.core.ui.ViewEvent
import org.supla.android.core.ui.ViewState
import org.supla.android.db.ChannelBase
import org.supla.android.tools.SuplaSchedulers
import org.supla.android.usecases.channel.ReadChannelByRemoteIdUseCase
import org.supla.android.usecases.channel.ReadChannelGroupByRemoteIdUseCase
import javax.inject.Inject

@HiltViewModel
class LegacyDetailViewModel @Inject constructor(
  private val readChannelByRemoteIdUseCase: ReadChannelByRemoteIdUseCase,
  private val readChannelGroupByRemoteIdUseCase: ReadChannelGroupByRemoteIdUseCase,
  schedulers: SuplaSchedulers
) : BaseViewModel<LegacyDetailViewState, LegacyDetailViewEvent>(LegacyDetailViewState(), schedulers) {

  override fun loadingState(isLoading: Boolean) = currentState().copy(loading = isLoading)

  fun loadData(remoteId: Int, itemType: LegacyDetailFragment.ItemType) {
    getDataSource(remoteId, itemType)
      .attach()
      .subscribeBy(
        onSuccess = { sendEvent(LegacyDetailViewEvent.LoadDetailView(it)) }
      )
      .disposeBySelf()
  }

  private fun getDataSource(remoteId: Int, itemType: LegacyDetailFragment.ItemType) = when (itemType) {
    LegacyDetailFragment.ItemType.CHANNEL -> readChannelByRemoteIdUseCase(remoteId)
    LegacyDetailFragment.ItemType.GROUP -> readChannelGroupByRemoteIdUseCase(remoteId)
  }
}

sealed class LegacyDetailViewEvent : ViewEvent {
  data class LoadDetailView(val channelBase: ChannelBase) : LegacyDetailViewEvent()
}

data class LegacyDetailViewState(override val loading: Boolean = false) : ViewState(loading)