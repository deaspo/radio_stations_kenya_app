package com.example.kenyanradiostations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class StationAdapter(
    private val stations: List<RadioStation>,
    private val onItemClick: (RadioStation) -> Unit
) : RecyclerView.Adapter<StationAdapter.StationViewHolder>() {

    class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.station_logo)
        val name: TextView = view.findViewById(R.id.station_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = stations[position]
        holder.name.text = station.name
        holder.logo.load(station.logoUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_background) // Add a placeholder
        }
        holder.itemView.setOnClickListener { onItemClick(station) }
    }

    override fun getItemCount() = stations.size
}