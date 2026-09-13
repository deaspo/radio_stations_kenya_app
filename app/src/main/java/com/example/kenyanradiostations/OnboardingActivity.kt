package com.example.kenyanradiostations

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.example.kenyanradiostations.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val lastPage get() = OnboardingAdapter.PAGE_COUNT - 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        buildPageIndicator()

        binding.viewPager.adapter = OnboardingAdapter(this)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateControls(position)
            }
        })

        binding.buttonNext.setOnClickListener {
            if (binding.viewPager.currentItem == lastPage) {
                acceptAndContinue()
            } else {
                binding.viewPager.currentItem += 1
            }
        }

        binding.buttonPrevious.setOnClickListener {
            binding.viewPager.currentItem -= 1
        }

        // Skips the feature tour, not the agreement: it jumps to the last page,
        // which still has to be accepted.
        binding.buttonSkip.setOnClickListener {
            binding.viewPager.currentItem = lastPage
        }

        updateControls(binding.viewPager.currentItem)
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    private fun buildPageIndicator() {
        val size = resources.getDimensionPixelSize(R.dimen.onboarding_dot_size)
        val gap = resources.getDimensionPixelSize(R.dimen.onboarding_dot_gap)

        binding.pageIndicator.removeAllViews()
        repeat(OnboardingAdapter.PAGE_COUNT) {
            val dot = ImageView(this).apply {
                setBackgroundResource(R.drawable.dot_indicator)
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val params = LinearLayout.LayoutParams(size, size)
            params.marginStart = gap
            params.marginEnd = gap
            binding.pageIndicator.addView(dot, params)
        }
    }

    private fun updateControls(position: Int) {
        val onLastPage = position == lastPage

        binding.buttonPrevious.visibility =
            if (position > 0) View.VISIBLE else View.INVISIBLE
        binding.buttonSkip.visibility =
            if (onLastPage) View.INVISIBLE else View.VISIBLE
        binding.buttonNext.setText(
            if (onLastPage) R.string.onboarding_finish else R.string.onboarding_next
        )

        for (index in 0 until binding.pageIndicator.childCount) {
            binding.pageIndicator.getChildAt(index).isSelected = index == position
        }
    }

    private fun acceptAndContinue() {
        getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private companion object {
        const val APP_PREFS = "app_prefs"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
