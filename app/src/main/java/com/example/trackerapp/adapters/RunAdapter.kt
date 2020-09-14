package com.example.trackerapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.trackerapp.R
import com.example.trackerapp.db.Run
import com.example.trackerapp.other.TrackingUtility
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.item_run.view.*
import java.text.SimpleDateFormat
import java.util.*


class RunAdapter(listener: OnListInteractionListener) :
    RecyclerView.Adapter<RunAdapter.RunViewHolder>() {


    interface OnListInteractionListener {
        fun onListInteraction(run: Run)
    }

    private val mListener: OnListInteractionListener = listener


    inner class RunViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private val diffCallback = object : DiffUtil.ItemCallback<Run>() {
        override fun areItemsTheSame(oldItem: Run, newItem: Run): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Run, newItem: Run): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }

    }

    private val differ = AsyncListDiffer(this, diffCallback)

    fun submitList(list: List<Run>) = differ.submitList(list)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
        return RunViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_run, parent, false)
        )
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
        val run = differ.currentList[position]
        holder.itemView.apply {
            Glide.with(this).load(run.img).into(ivRunImage)

            val calender = Calendar.getInstance().apply {
                timeInMillis = run.timestamp!!
            }
            val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
            tvDate.text = dateFormat.format(calender.time)

            val avgSpeed = "${run.avgSpeedInKMH}Km/hr"
            tvAvgSpeed.text = avgSpeed

            val distance = "${run.distanceInMeters / 1000f}Km"
            tvDistance.text = distance

            tvTime.text = TrackingUtility.getFormattedStopWatchTime(run.timeInMillis)

            val caloriesBurned = "${run.caloriesBurned}kcal"
            tvCalories.text = caloriesBurned


            setOnLongClickListener {
                val dialog = MaterialAlertDialogBuilder(it.context, R.style.AlertDialogTheme)
                    .setTitle("delete the run ?")
                    .setMessage("Are you sure")
                    .setPositiveButton("Yes") { _, _ ->
                        mListener.onListInteraction(run)
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.cancel()
                    }
                dialog.show()
                return@setOnLongClickListener true
            }
        }
    }

}