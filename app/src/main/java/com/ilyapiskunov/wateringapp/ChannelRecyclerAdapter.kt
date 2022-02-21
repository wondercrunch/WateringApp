package com.ilyapiskunov.wateringapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.children
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
                if (holder.radioSelectDays.checkedRadioButtonId != R.id.radioCustomDay) {
                    holder.radioSelectDays.check(R.id.radioCustomDay)
                    dayOfWeek1.isChecked = !dayOfWeek1.isChecked
                }
            }

            val dayOfWeek2 = holder.daysOfWeek2[i] as ToggleButton
            dayOfWeek2.isChecked = channel.week2[i]
            dayOfWeek2.setOnClickListener {
                if (holder.radioSelectDays.checkedRadioButtonId != R.id.radioCustomDay) {
                    holder.radioSelectDays.check(R.id.radioCustomDay)
                    dayOfWeek2.isChecked = !dayOfWeek2.isChecked
                }
            }
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
        var radioSelectDays : RadioGroup = itemView.radioSelectDays
        val tvWeek1 : TextView = itemView.tvWeek1
        val tvWeek2 : TextView = itemView.tvWeek2
        var daysOfWeek1 : LinearLayout = itemView.days_of_week_1 as LinearLayout
        var daysOfWeek2 : LinearLayout = itemView.days_of_week_2 as LinearLayout
        val tvTimeOn : TextView = itemView.tvTimeOn
        val tvTimeOff : TextView = itemView.tvTimeOff
        var timeOn : View = itemView.time_on
        var timeOff : View = itemView.time_off



        init {


            radioSelectDays.setOnCheckedChangeListener { group, checkedId ->
                when (checkedId) {
                    R.id.radioEveryDay -> {
                        daysOfWeek1.children.forEach { view: View ->
                            (view as ToggleButton).isChecked = true
                        }

                        daysOfWeek2.children.forEach { view: View ->
                            (view as ToggleButton).isChecked = true
                        }
                    }

                    R.id.radioSkipDay -> {
                        for (i in 0..6) {
                            if (i % 2 == 0) {
                                (daysOfWeek1[i] as ToggleButton).isChecked = true
                                (daysOfWeek2[i] as ToggleButton).isChecked = false
                            } else {
                                (daysOfWeek1[i] as ToggleButton).isChecked = false
                                (daysOfWeek2[i] as ToggleButton).isChecked = true
                            }
                        }
                    }

                }

            }

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

            //Tools.setTimeControlEditListener(timeOn.editHrs, true)
           // Tools.setTimeControlEditListener(timeOn.editMin, false)
           // Tools.setTimeControlEditListener(timeOn.editSec, false)

           // Tools.setTimeControlEditListener(timeOff.editHrs, true)
           // Tools.setTimeControlEditListener(timeOff.editMin, false)
           // Tools.setTimeControlEditListener(timeOff.editSec, false)

        }



    }






}