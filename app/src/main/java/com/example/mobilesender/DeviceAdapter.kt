package com.example.mobilesender

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val onClick: (TvDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceVH>() {

    private val items = mutableListOf<TvDevice>()

    fun submit(list: List<TvDevice>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceVH(view)
    }

    override fun onBindViewHolder(holder: DeviceVH, position: Int) {
        val device = items[position]
        holder.text.text = "${device.name} (${device.host}:${device.port})"
        holder.itemView.setOnClickListener { onClick(device) }
    }

    override fun getItemCount(): Int = items.size

    class DeviceVH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.itemText)
    }
}
