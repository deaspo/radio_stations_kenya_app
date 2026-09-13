package com.example.kenyanradiostations

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import com.example.kenyanradiostations.databinding.FragmentOnboardingPageBinding

class OnboardingPageFragment : Fragment() {

    private var _binding: FragmentOnboardingPageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return

        binding.onboardingIcon.setImageResource(args.getInt(ARG_ICON))
        binding.onboardingTitle.setText(args.getInt(ARG_TITLE))
        binding.onboardingMessage.setText(args.getInt(ARG_MESSAGE))

        applyIconStyle(args.getBoolean(ARG_FRAMED, true))
    }

    private fun applyIconStyle(framed: Boolean) {
        val icon = binding.onboardingIcon
        if (!framed) {
            icon.background = null
            icon.setPadding(0, 0, 0, 0)
            ImageViewCompat.setImageTintList(icon, null)
            return
        }

        val padding = resources.getDimensionPixelSize(R.dimen.onboarding_icon_padding)
        icon.setBackgroundResource(R.drawable.bg_onboarding_icon)
        icon.setPadding(padding, padding, padding, padding)
        ImageViewCompat.setImageTintList(
            icon,
            ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.md_on_primary_container)
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {

        private const val ARG_ICON = "arg_icon"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_FRAMED = "arg_framed"

        /** Resource ids rather than strings, so the pages are translatable. */
        fun newInstance(
            iconRes: Int,
            titleRes: Int,
            messageRes: Int,
            framed: Boolean
        ): OnboardingPageFragment = OnboardingPageFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_ICON, iconRes)
                putInt(ARG_TITLE, titleRes)
                putInt(ARG_MESSAGE, messageRes)
                putBoolean(ARG_FRAMED, framed)
            }
        }
    }
}
