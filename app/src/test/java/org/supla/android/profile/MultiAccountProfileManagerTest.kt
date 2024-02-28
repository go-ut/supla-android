package org.supla.android.profile

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.core.Completable
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import org.supla.android.core.SuplaAppApi
import org.supla.android.core.SuplaAppProvider
import org.supla.android.core.networking.suplaclient.SuplaClientApi
import org.supla.android.core.networking.suplacloud.SuplaCloudConfigHolder
import org.supla.android.data.source.ProfileRepository
import org.supla.android.db.AuthProfileItem
import org.supla.android.events.UpdateEventsManager
import org.supla.android.lib.singlecall.SingleCall
import org.supla.android.usecases.icon.LoadUserIconsIntoCacheUseCase
import org.supla.android.widget.WidgetManager

@RunWith(MockitoJUnitRunner::class)
class MultiAccountProfileManagerTest {

  @Mock
  private lateinit var profileRepository: ProfileRepository

  @Mock
  private lateinit var widgetManager: WidgetManager

  @Mock
  private lateinit var updateEventsManager: UpdateEventsManager

  @Mock
  private lateinit var profileIdHolder: ProfileIdHolder

  @Mock
  private lateinit var suplaAppProvider: SuplaAppProvider

  @Mock
  private lateinit var singleCallProvider: SingleCall.Provider

  @Mock
  private lateinit var suplaCloudConfigHolder: SuplaCloudConfigHolder

  @Mock
  private lateinit var loadUserIconsIntoCacheUseCase: LoadUserIconsIntoCacheUseCase

  private lateinit var profileManager: MultiAccountProfileManager

  @Before
  fun setUp() {
    profileManager = MultiAccountProfileManager(
      profileRepository,
      profileIdHolder,
      widgetManager,
      updateEventsManager,
      suplaAppProvider,
      singleCallProvider,
      suplaCloudConfigHolder,
      loadUserIconsIntoCacheUseCase
    )
  }

  @Test
  fun `should create profile`() {
    // given
    val profileId = 123L
    val profile = mockk<AuthProfileItem>()
    every { profile.id } returns null
    every { profile.id = 123L } answers { }

    whenever(profileRepository.createProfile(profile)).thenReturn(profileId)

    // when
    val testObserver = profileManager.create(profile).test()

    // then
    testObserver.assertComplete()

    verify(profileRepository).createProfile(profile)
    verifyNoMoreInteractions(profileRepository)

    io.mockk.verify {
      profile.id
      profile.id = profileId
    }
    confirmVerified(profile)
  }

  @Test
  fun `should fail when trying to create profile with id`() {
    // given
    val profileId = 123L
    val profile = mockk<AuthProfileItem>()
    every { profile.id } returns profileId

    // when
    val testObserver = profileManager.create(profile).test()

    // then
    testObserver.assertFailure(IllegalArgumentException::class.java)

    verifyZeroInteractions(profileRepository)

    io.mockk.verify {
      profile.id
    }
    confirmVerified(profile)
  }

  @Test
  fun `should read profile from repository`() {
    // given
    val profileId = 123L
    val profile = mockk<AuthProfileItem>()
    whenever(profileRepository.getProfile(profileId)).thenReturn(profile)

    // when
    val testObserver = profileManager.read(profileId).test()

    // then
    testObserver.assertValue(profile)

    verify(profileRepository).getProfile(profileId)
    verifyNoMoreInteractions(profileRepository)
  }

  @Test
  fun `should update profile`() {
    // given
    val profileId = 123L
    val profile = mockk<AuthProfileItem>()
    every { profile.id } returns profileId

    // when
    val testObserver = profileManager.update(profile).test()

    // then
    testObserver.assertComplete()

    verify(profileRepository).updateProfile(profile)
    verifyNoMoreInteractions(profileRepository)

    io.mockk.verify {
      profile.id
    }
    confirmVerified(profile)
  }

  @Test
  fun `should fail when trying to update profile without id`() {
    // given
    val profile = mockk<AuthProfileItem>()
    every { profile.id } returns null

    // when
    val testObserver = profileManager.update(profile).test()

    // then
    testObserver.assertFailure(IllegalArgumentException::class.java)

    verifyZeroInteractions(profileRepository)

    io.mockk.verify {
      profile.id
    }
    confirmVerified(profile)
  }

  @Test
  fun `should delete profile`() {
    // given
    val profileId = 123L

    // when
    val testObserver = profileManager.delete(profileId).test()

    // then
    testObserver.assertComplete()

    verify(profileRepository).deleteProfile(profileId)
    verify(profileRepository).getProfile(profileId)
    verify(widgetManager).onProfileRemoved(profileId)
    verifyNoMoreInteractions(profileRepository, widgetManager)
  }

  @Test
  fun `should read all profiles from repository`() {
    // given
    val profile = mockk<AuthProfileItem>()
    whenever(profileRepository.allProfiles).thenReturn(listOf(profile))

    // when
    val testObserver = profileManager.getAllProfiles().test()

    // then
    testObserver.assertValue(listOf(profile))

    verify(profileRepository).allProfiles
    verifyNoMoreInteractions(profileRepository)
  }

  @Test
  fun `should get currently active profile`() {
    // given
    val profiles = listOf(
      mockk<AuthProfileItem>().apply { every { isActive } returns false },
      mockk<AuthProfileItem>().apply { every { isActive } returns false },
      mockk<AuthProfileItem>().apply { every { isActive } returns true }
    )
    whenever(profileRepository.allProfiles).thenReturn(profiles)

    // when
    val testObserver = profileManager.getCurrentProfile().test()

    // then
    testObserver.assertValue(profiles.last())

    verify(profileRepository).allProfiles
    verifyNoMoreInteractions(profileRepository)
  }

  @Test
  fun `should skip activation when profile active and force is false`() {
    // given
    val activeProfileId = 123L
    val profiles = listOf(
      mockk<AuthProfileItem>().apply { every { isActive } returns false },
      mockk<AuthProfileItem>().apply { every { isActive } returns false },
      mockk<AuthProfileItem>().apply {
        every { id } returns activeProfileId
        every { isActive } returns true
      }
    )
    whenever(profileRepository.allProfiles).thenReturn(profiles)
    whenever(loadUserIconsIntoCacheUseCase.invoke()).thenReturn(Completable.complete())

    // when
    val testObserver = profileManager.activateProfile(activeProfileId, false).test()

    // then
    testObserver.assertComplete()

    verify(profileRepository).allProfiles
    verifyNoMoreInteractions(profileRepository)
    verifyZeroInteractions(profileIdHolder, updateEventsManager, suplaAppProvider, suplaCloudConfigHolder)
  }

  @Test
  fun `should activate other profile`() {
    // given
    val activeProfileId = 123L
    val newActiveProfileId = 234L
    val profiles = listOf(
      mockk<AuthProfileItem>().apply { every { isActive } returns false },
      mockk<AuthProfileItem>().apply {
        every { id } returns newActiveProfileId
        every { isActive } returns false
      },
      mockk<AuthProfileItem>().apply {
        every { id } returns activeProfileId
        every { isActive } returns true
      }
    )
    whenever(profileRepository.allProfiles).thenReturn(profiles)
    whenever(profileRepository.setProfileActive(newActiveProfileId)).thenReturn(true)

    val suplaClient = mockk<SuplaClientApi>()
    every { suplaClient.reconnect() } answers { }

    val suplaApp = mockk<SuplaAppApi>()
    every { suplaApp.CancelAllRestApiClientTasks(true) } answers { }
    every { suplaApp.cleanupToken() } answers { }
    every { suplaApp.getSuplaClient() } returns suplaClient
    whenever(suplaAppProvider.provide()).thenReturn(suplaApp)

    whenever(loadUserIconsIntoCacheUseCase.invoke()).thenReturn(Completable.complete())

    // when
    val testObserver = profileManager.activateProfile(newActiveProfileId, false).test()

    // then
    testObserver.assertComplete()

    verify(profileRepository).allProfiles
    verify(profileRepository).setProfileActive(newActiveProfileId)
    verify(profileIdHolder).profileId = newActiveProfileId
    verify(updateEventsManager).cleanup()
    verify(updateEventsManager).emitScenesUpdate()
    verify(updateEventsManager).emitGroupsUpdate()
    verify(updateEventsManager).emitChannelsUpdate()
    verify(suplaAppProvider).provide()
    verify(suplaCloudConfigHolder).clean()
    verifyNoMoreInteractions(profileRepository, profileIdHolder, updateEventsManager, suplaAppProvider, suplaCloudConfigHolder)

    io.mockk.verify {
      suplaApp.CancelAllRestApiClientTasks(true)
      suplaApp.cleanupToken()
      suplaApp.getSuplaClient()
      suplaClient.reconnect()
    }
    confirmVerified(suplaApp, suplaClient)
  }

  @Test
  fun `should reactivate same profile with force`() {
    // given
    val activeProfileId = 123L
    val profiles = listOf(
      mockk<AuthProfileItem>().apply { every { isActive } returns false },
      mockk<AuthProfileItem>().apply {
        every { id } returns activeProfileId
        every { isActive } returns true
      }
    )
    whenever(profileRepository.allProfiles).thenReturn(profiles)
    whenever(profileRepository.setProfileActive(activeProfileId)).thenReturn(true)

    val suplaClient = mockk<SuplaClientApi>()
    every { suplaClient.reconnect() } answers { }

    val suplaApp = mockk<SuplaAppApi>()
    every { suplaApp.CancelAllRestApiClientTasks(true) } answers { }
    every { suplaApp.cleanupToken() } answers { }
    every { suplaApp.getSuplaClient() } returns suplaClient
    whenever(suplaAppProvider.provide()).thenReturn(suplaApp)

    whenever(loadUserIconsIntoCacheUseCase.invoke()).thenReturn(Completable.complete())

    // when
    val testObserver = profileManager.activateProfile(activeProfileId, true).test()

    // then
    testObserver.assertComplete()

    verify(profileRepository).allProfiles
    verify(profileRepository).setProfileActive(activeProfileId)
    verify(profileIdHolder).profileId = activeProfileId
    verify(updateEventsManager).cleanup()
    verify(updateEventsManager).emitScenesUpdate()
    verify(updateEventsManager).emitGroupsUpdate()
    verify(updateEventsManager).emitChannelsUpdate()
    verify(suplaAppProvider).provide()
    verify(suplaCloudConfigHolder).clean()
    verifyNoMoreInteractions(profileRepository, profileIdHolder, updateEventsManager, suplaAppProvider, suplaCloudConfigHolder)

    io.mockk.verify {
      suplaApp.CancelAllRestApiClientTasks(true)
      suplaApp.cleanupToken()
      suplaApp.getSuplaClient()
      suplaClient.reconnect()
    }
    confirmVerified(suplaApp, suplaClient)
  }
}
