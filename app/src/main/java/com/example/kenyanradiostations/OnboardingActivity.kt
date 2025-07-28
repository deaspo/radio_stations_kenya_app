package com.example.kenyanradiostations

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.kenyanradiostations.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = OnboardingAdapter(this)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateControls(position)
            }
        })

        binding.buttonNext.setOnClickListener {
            if (binding.viewPager.currentItem == 2) {
                // Final "Agree & Continue" button
                markOnboardingAsComplete()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                binding.viewPager.currentItem += 1
            }
        }

        binding.buttonPrevious.setOnClickListener {
            binding.viewPager.currentItem -= 1
        }
    }

    private fun updateControls(position: Int) {
        binding.buttonPrevious.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
        binding.buttonNext.text = if (position == 2) "Agree & Continue" else "Next"
    }

    private fun markOnboardingAsComplete() {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean("onboarding_complete", true)
            apply()
        }
    }
}
