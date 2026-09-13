package com.example.kenyanradiostations

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = PAGES.size

    override fun createFragment(position: Int): Fragment {
        val page = PAGES[position]
        return OnboardingPageFragment.newInstance(
            iconRes = page.iconRes,
            titleRes = page.titleRes,
            messageRes = page.messageRes,
            framed = page.framed
        )
    }

    /**
     * @param framed draws the icon on a circular plate and tints it. The logo on
     *               the first page is full-colour artwork and is left alone; the
     *               rest are 24dp system-style icons that would look lost at this
     *               size without it.
     */
    private data class Page(
        val iconRes: Int,
        val titleRes: Int,
        val messageRes: Int,
        val framed: Boolean = true
    )

    companion object {

        /**
         * Single source of truth for the page count, so the activity cannot
         * disagree with the adapter about which page is last.
         *
         * The user agreement stays last: the final button is what accepts it.
         */
        private val PAGES = listOf(
            Page(
                iconRes = R.drawable.ic_splash_logo,
                titleRes = R.string.onboarding_title_welcome,
                messageRes = R.string.onboarding_message_welcome,
                framed = false
            ),
            Page(
                iconRes = R.drawable.ic_search,
                titleRes = R.string.onboarding_title_find,
                messageRes = R.string.onboarding_message_find
            ),
            Page(
                iconRes = R.drawable.ic_features,
                titleRes = R.string.onboarding_title_listen,
                messageRes = R.string.onboarding_message_listen
            ),
            Page(
                iconRes = R.drawable.ic_record,
                titleRes = R.string.onboarding_title_record,
                messageRes = R.string.onboarding_message_record
            ),
            Page(
                iconRes = R.drawable.ic_shield,
                titleRes = R.string.onboarding_title_agreement,
                messageRes = R.string.onboarding_message_agreement
            )
        )

        val PAGE_COUNT: Int = PAGES.size
    }
}
