package com.example.kenyanradiostations

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OnboardingPageFragment.newInstance(
                R.drawable.ic_splash_logo,
                "Welcome to Radio Diaspora!",
                "Your home for Kenyan radio, wherever you are. Stream all your favorite stations live, for free."
            )
            1 -> OnboardingPageFragment.newInstance(
                R.drawable.ic_features, // Create a new icon for this
                "Listen Anywhere",
                "Play music in the background while you use other apps, or cast the vibe to any Google Cast-enabled speaker or TV on your network."
            )
            else -> OnboardingPageFragment.newInstance(
                R.drawable.ic_shield, // Create a new icon for this
                "User Agreement",
                "By using Radio Diaspora, you agree to our terms. This app streams content that is publicly available on the internet from Kenyan radio broadcasters. We do not host or own this content. All rights belong to the respective owners. This app is provided for personal, non-commercial use only."
            )
        }
    }
}
