package com.example.kenyanradiostations

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.kenyanradiostations.databinding.ItemStationBinding

/**
 * Grid of stations.
 *
 * Uses [ListAdapter] so that re-scraping or filtering diffs against the previous
 * list instead of rebuilding the adapter, which keeps scroll position and item
 * animations intact. Favourite and now-playing changes are dispatched as a
 * payload so only the affected views are re-bound - artwork is never reloaded.
 */
class StationAdapter(
    private val onItemClick: (RadioStation) -> Unit,
    private val onToggleFavourite: (RadioStation) -> Unit
) : ListAdapter<RadioStation, StationAdapter.StationViewHolder>(DIFF_CALLBACK) {

    private var favouriteIds: Set<String> = emptySet()
    private var playingStationId: String? = null

    fun setFavourites(ids: Set<String>) {
        if (ids == favouriteIds) return
        favouriteIds = ids
        notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE)
    }

    fun setPlayingStation(stationId: String?) {
        if (stationId == playingStationId) return
        playingStationId = stationId
        notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val binding = ItemStationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: StationViewHolder) {
        super.onViewRecycled(holder)
        // The pulse is an infinite animation; leaving it running on a recycled
        // view would keep a tile blinking for a station it no longer shows.
        holder.stopPulse()
    }

    override fun onBindViewHolder(
        holder: StationViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_STATE)) {
            holder.bindState(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class StationViewHolder(
        private val binding: ItemStationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(station: RadioStation) {
            val context = binding.root.context

            binding.stationName.text = station.name
            binding.stationLogo.contentDescription =
                context.getString(R.string.station_logo_of, station.name)
            binding.stationLogo.load(station.logoUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_radio_icon)
                error(R.drawable.ic_radio_icon)
            }

            binding.root.contentDescription =
                context.getString(R.string.play_station, station.name)
            binding.root.setOnClickListener { onItemClick(station) }
            binding.stationFavourite.setOnClickListener { onToggleFavourite(station) }

            bindState(station)
        }

        fun bindState(station: RadioStation) {
            val context = binding.root.context
            val isFavourite = favouriteIds.contains(station.id)
            val isPlaying = station.id == playingStationId

            binding.stationFavourite.setIconResource(
                if (isFavourite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            binding.stationFavourite.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (isFavourite) R.color.md_primary else R.color.md_on_surface_variant
                )
            )
            binding.stationFavourite.contentDescription = context.getString(
                if (isFavourite) R.string.remove_from_favourites else R.string.add_to_favourites,
                station.name
            )

            binding.nowPlayingBadge.visibility = if (isPlaying) View.VISIBLE else View.GONE
            binding.root.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (isPlaying) R.color.md_primary_container else R.color.md_surface_variant
                )
            )

            if (isPlaying) startPulse() else stopPulse()
        }

        /** Slow blink on the live dot, so the playing tile is obvious mid-scroll. */
        fun startPulse() {
            if (binding.nowPlayingDot.animation != null) return
            binding.nowPlayingDot.startAnimation(
                AlphaAnimation(1f, 0.25f).apply {
                    duration = PULSE_DURATION_MS
                    repeatMode = Animation.REVERSE
                    repeatCount = Animation.INFINITE
                }
            )
        }

        fun stopPulse() {
            binding.nowPlayingDot.clearAnimation()
        }
    }

    private companion object {

        const val PAYLOAD_STATE = "payload_state"
        const val PULSE_DURATION_MS = 750L

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RadioStation>() {
            override fun areItemsTheSame(oldItem: RadioStation, newItem: RadioStation): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: RadioStation, newItem: RadioStation): Boolean =
                oldItem == newItem
        }
    }
}
