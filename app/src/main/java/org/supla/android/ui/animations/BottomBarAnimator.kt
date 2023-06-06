package org.supla.android.ui.animations

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import com.google.android.material.bottomappbar.BottomAppBar

const val DEFAULT_ANIMATION_DURATION = 300L

fun animateFadeOut(bar: BottomAppBar, barHeight: Float) {
  ObjectAnimator.ofFloat(bar, "translationY", barHeight).apply {
    duration = DEFAULT_ANIMATION_DURATION
    addListener(BottomBarAnimatorListener { bar.visibility = View.GONE })
    start()
  }
}

fun animateFadeIn(bar: BottomAppBar, animationEndCallback: () -> Unit) {
  bar.visibility = View.VISIBLE

  ObjectAnimator.ofFloat(bar, "translationY", 0f).apply {
    duration = DEFAULT_ANIMATION_DURATION
    addListener(BottomBarAnimatorListener { animationEndCallback() })
    start()
  }
}

private class BottomBarAnimatorListener(private val animationEndCallback: () -> Unit) : AnimatorListenerAdapter() {

  override fun onAnimationEnd(p0: Animator?) {
    animationEndCallback()
  }
}