package org.supla.android.navigator

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import org.supla.android.R
import org.supla.android.features.legacydetail.LegacyDetailFragment
import org.supla.android.usecases.details.DetailType
import javax.inject.Inject

@ActivityScoped
class MainNavigator @Inject constructor(@ActivityContext private val activityContext: Context) {

  private val navController: NavController
    get() = (activityContext as Activity).findNavController(R.id.nav_host_fragment)

  fun navigateTo(@IdRes destinationId: Int, bundle: Bundle? = null) {
    navController.navigate(destinationId, bundle)
  }

  fun navigateToLegacyDetails(remoteId: Int, detailType: DetailType, itemType: LegacyDetailFragment.ItemType) {
    val navBuilder = NavOptions.Builder()
    navBuilder.setEnterAnim(R.anim.slide_left_in)
      .setExitAnim(R.anim.slide_left_out)
      .setPopEnterAnim(R.anim.slide_right_in)
      .setPopExitAnim(R.anim.slide_right_out)

    navController.navigate(
      R.id.legacy_detail_fragment,
      LegacyDetailFragment.bundle(remoteId, detailType, itemType),
      navBuilder.build()
    )
  }

  fun back(): Boolean = navController.navigateUp()
}