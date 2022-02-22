package com.ilyapiskunov.wateringapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.get
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.channel.view.*
import kotlinx.android.synthetic.main.time.view.*
import java.util.*

class ChannelRecyclerAdapter(private val channels : ArrayList<Channel>) : RecyclerView.Adapter<ChannelRecyclerAdapter.ChannelViewHolder>() {



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.channel, parent, false)
        return ChannelViewHolder(itemView)
    }

    override fun getItemCount() = channels.size

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.tvChannelId.text = "Канал ".plus(position+1)
        val channel : Channel = channels[position]

        for (i in 0..6) {
            val dayOfWeek1 : ToggleButton = holder.daysOfWeek1[i] as ToggleButton
            dayOfWeek1.isChecked = channel.week1[i]
            dayOfWeek1.setOnClickListener {
                channel.week1[i] = !channel.week1[i]
                notifyItemChanged(position)
            }

            val dayOfWeek2 = holder.daysOfWeek2[i] as ToggleButton
            dayOfWeek2.isChecked = channel.week2[i]
            dayOfWeek2.setOnClickListener {
                channel.week2[i] = !channel.week2[i]
                notifyItemChanged(position)
            }
        }

        holder.btnEveryDay.setOnClickListener {
            channel.week1.fill(true)
            channel.week2.fill(true)
            notifyItemChanged(position)
        }

        holder.btnSkipDay.setOnClickListener {
            for (i in 0..6) {
                val isEven = i % 2 == 0
                channel.week1[i] = isEven
                channel.week2[i] = !isEven
            }
            notifyItemChanged(position)
        }


        val dateOn : Calendar = channel.timeOn

        holder.timeOn.editHrs.setText(dateOn.get(Calendar.HOUR_OF_DAY).toString())
        holder.timeOn.editMin.setText(dateOn.get(Calendar.MINUTE).toString())
        holder.timeOn.editSec.setText(dateOn.get(Calendar.SECOND).toString())

        val dateOff : Calendar = channel.timeOff
        holder.timeOff.editHrs.setText(dateOff.get(Calendar.HOUR_OF_DAY).toString())
        holder.timeOff.editMin.setText(dateOff.get(Calendar.MINUTE).toString())
        holder.timeOff.editSec.setText(dateOff.get(Calendar.SECOND).toString())
    }



    class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvChannelId : TextView = itemView.name
        val btnOpen : Button = itemView.btnOpen
        val btnClose : Button = itemView.btnClose
        var btnSkipDay : Button = itemView.btnSkipDay
        var btnEveryDay : Button = itemView.btnEveryDay
        val tvWeek1 : TextView = itemView.tvWeek1
        val tvWeek2 : TextView = itemView.tvWeek2
        var daysOfWeek1 : LinearLayout = itemView.days_of_week_1 as LinearLayout
        var daysOfWeek2 : LinearLayout = itemView.days_of_week_2 as LinearLayout
        val tvTimeOn : TextView = itemView.tvTimeOn
        val tvTimeOff : TextView = itemView.tvTimeOff
        var timeOn : View = itemView.time_on
        var timeOff : View = itemView.time_off



        init {


            Tools.setTimeIncButtonListener(timeOn.btnIncHrs, timeOn.editHrs)
            Tools.setTimeIncButtonListener(timeOn.btnIncMin, timeOn.editMin)
            Tools.setTimeIncButtonListener(timeOn.btnIncSec, timeOn.editSec)
            Tools.setTimeDecButtonListener(timeOn.btnDecHrs, timeOn.editHrs)
            Tools.setTimeDecButtonListener(timeOn.btnDecMin, timeOn.editMin)
            Tools.setTimeDecButtonListener(timeOn.btnDecSec, timeOn.editSec)

            Tools.setTimeIncButtonListener(timeOff.btnIncHrs, timeOff.editHrs)
            Tools.setTimeIncButtonListener(timeOff.btnIncMin, timeOff.editMin)
            Tools.setTimeIncButtonListener(timeOff.btnIncSec, timeOff.editSec)
            Tools.setTimeDecButtonListener(timeOff.btnDecHrs, timeOff.editHrs)
            Tools.setTimeDecButtonListener(timeOff.btnDecMin, timeOff.editMin)
            Tools.setTimeDecButtonListener(timeOff.btnDecSec, timeOff.editSec)

            Tools.setTimeControlEditListener(timeOn.editHrs, true)
            Tools.setTimeControlEditListener(timeOn.editMin, false)
            Tools.setTimeControlEditListener(timeOn.editSec, false)

            Tools.setTimeControlEditListener(timeOff.editHrs, true)
            Tools.setTimeControlEditListener(timeOff.editMin, false)
            Tools.setTimeControlEditListener(timeOff.editSec, false)
        }



    }






}