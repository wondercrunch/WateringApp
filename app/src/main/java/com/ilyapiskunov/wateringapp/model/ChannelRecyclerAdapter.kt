package com.ilyapiskunov.wateringapp.model

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.get
import androidx.recyclerview.widget.RecyclerView
import com.ilyapiskunov.wateringapp.R
import kotlinx.android.synthetic.main.channel.view.*
import kotlinx.android.synthetic.main.time.view.*
import java.util.*

class ChannelRecyclerAdapter(private val channels : List<Channel>) : RecyclerView.Adapter<ChannelRecyclerAdapter.ChannelViewHolder>() {

    private var currentDevice : WateringDevice? = null
    private val isCurrentWeekEven
        get() = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR) % 2 == 0


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.channel, parent, false)
        return ChannelViewHolder(itemView)
    }

    override fun getItemCount() = channels.size

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.tvChannelId.text = holder.itemView.context.getString(R.string.tv_channel_format, position+1)
        val channel : Channel = channels[position]
        if (isCurrentWeekEven) {
            holder.tvWeekEven.setTextColor(Color.GREEN)
        } else {
            holder.tvWeekOdd.setTextColor(Color.GREEN)
        }
        for (i in 0..6) {
            val dayOfWeek1 : ToggleButton = holder.daysOfWeek1[i] as ToggleButton
            dayOfWeek1.isChecked = channel.week1[i]
            dayOfWeek1.setOnClickListener {
                channel.week1[i] = !channel.week1[i]
                dayOfWeek1.isChecked = channel.week1[i]
            }

            val dayOfWeek2 = holder.daysOfWeek2[i] as ToggleButton
            dayOfWeek2.isChecked = channel.week2[i]
            dayOfWeek2.setOnClickListener {
                channel.week2[i] = !channel.week2[i]
                dayOfWeek2.isChecked = channel.week2[i]
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


        val timerOn : AlarmTimer = channel.timerOn

        holder.timerOn.editHrs.setText(timerOn.toString(AlarmTimer.TimeField.HOURS))
        holder.timerOn.editMin.setText(timerOn.toString(AlarmTimer.TimeField.MINUTES))
        holder.timerOn.editSec.setText(timerOn.toString(AlarmTimer.TimeField.SECONDS))

        val timerOff : AlarmTimer = channel.timerOff
        holder.timerOff.editHrs.setText(timerOff.toString(AlarmTimer.TimeField.HOURS))
        holder.timerOff.editMin.setText(timerOff.toString(AlarmTimer.TimeField.MINUTES))
        holder.timerOff.editSec.setText(timerOff.toString(AlarmTimer.TimeField.SECONDS))
        
        val holderTimerOn = holder.timerOn
        val holderTimerOff = holder.timerOff
        setTimeControlButtonListener(holderTimerOn.btnIncHrs, holderTimerOn.editHrs, position) {timerOn.incHours() }
        setTimeControlButtonListener(holderTimerOn.btnIncMin, holderTimerOn.editMin, position) {timerOn.incMinutes()}
        setTimeControlButtonListener(holderTimerOn.btnIncSec, holderTimerOn.editSec, position) {timerOn.incSeconds()}
        setTimeControlButtonListener(holderTimerOn.btnDecHrs, holderTimerOn.editHrs, position) {timerOn.decHours()}
        setTimeControlButtonListener(holderTimerOn.btnDecMin, holderTimerOn.editMin, position) {timerOn.decMinutes()}
        setTimeControlButtonListener(holderTimerOn.btnDecSec, holderTimerOn.editSec, position) {timerOn.decSeconds()}

        setTimeControlButtonListener(holderTimerOff.btnIncHrs, holderTimerOff.editHrs, position) {timerOff.incHours() }
        setTimeControlButtonListener(holderTimerOff.btnIncMin, holderTimerOff.editMin, position) {timerOff.incMinutes() }
        setTimeControlButtonListener(holderTimerOff.btnIncSec, holderTimerOff.editSec, position) {timerOff.incSeconds() }
        setTimeControlButtonListener(holderTimerOff.btnDecHrs, holderTimerOff.editHrs, position) {timerOff.decHours() }
        setTimeControlButtonListener(holderTimerOff.btnDecMin, holderTimerOff.editMin, position) {timerOff.decMinutes() }
        setTimeControlButtonListener(holderTimerOff.btnDecSec, holderTimerOff.editSec, position) {timerOff.decSeconds() }

        holder.btnOpen.setOnClickListener {
            currentDevice?.toggleChannel(true, position + 1)
        }

        holder.btnClose.setOnClickListener {
            currentDevice?.toggleChannel(false, position + 1)
        }
    }

    fun setCurrentDevice(device: WateringDevice?) {
        currentDevice = device
    }

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvChannelId : TextView = itemView.name
        val btnOpen : Button = itemView.btn_open
        val btnClose : Button = itemView.btn_close
        var btnSkipDay : Button = itemView.btn_skip_day
        var btnEveryDay : Button = itemView.btn_every_day
        val tvWeekOdd : TextView = itemView.tv_week_odd
        val tvWeekEven : TextView = itemView.tv_week_even
        var daysOfWeek1 : LinearLayout = itemView.days_of_week_1 as LinearLayout
        var daysOfWeek2 : LinearLayout = itemView.days_of_week_2 as LinearLayout
        val tvTimeOn : TextView = itemView.tv_timer_on
        val tvTimeOff : TextView = itemView.tv_timer_off
        var timerOn : View = itemView.time_on
        var timerOff : View = itemView.time_off

    }

    private class RepeatListener(private val immediateClick: Boolean, private val interval: Int, private val intervalAcceleration: Int/*, private val notifyCallback: () -> Unit*/) :
        View.OnTouchListener {

        private var activeView: View? = null
        private val handler = Handler(Looper.getMainLooper())
        private var handlerRunnable: Runnable
        private var haveClicked = false
        private var repeatInterval: Int = interval
        private val repeatIntervalMin: Int = 70

        init {
            handlerRunnable = object : Runnable {
                override fun run() {

                    if(activeView!!.isEnabled) {
                        haveClicked = true

                        // Schedule the next repetitions of the click action,
                        // faster and faster until it reaches repeaterIntervalMin
                        if (repeatInterval > repeatIntervalMin) {
                            repeatInterval -= intervalAcceleration
                        }

                        handler.postDelayed(this, repeatInterval.toLong())
                        activeView!!.performClick()

                    }else{
                        clearHandler() //stop the loop if the view is disabled during the process
                    }
                }
            }
        }

        override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(handlerRunnable)
                    handler.postDelayed(handlerRunnable, repeatInterval.toLong())
                    activeView = view
                    if (immediateClick) activeView!!.performClick()
                    haveClicked = immediateClick
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    // If we haven't clicked yet, click now
                    //Log.i("timer click handler", "ACTION UP")
                    if (!haveClicked) activeView!!.performClick()
                    clearHandler()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    //Log.i("timer click handler", "ACTION CANCEL")
                    clearHandler()
                    return true
                }
            }
            return false
        }

        private fun clearHandler(){
            handler.removeCallbacks(handlerRunnable)
            activeView = null

            //reset the interval to avoid starting with the sped up interval
            repeatInterval = interval

        }
    }


    private fun setTimeControlButtonListener(btn : Button, editTime : EditText, position: Int, applyOperation: () -> Int) {
        btn.setOnClickListener {
            val value = applyOperation()
            editTime.setText(String.format("%02d", value))
        }
        val repeatListener = RepeatListener(true, 200, 35) //{ notifyItemChanged(position) }

        btn.setOnTouchListener(repeatListener)

    }


    private class TimeFormattingTextWatcher(isHours: Boolean) : TextWatcher {
        private var current = "00"
        private val limit = if (isHours) 23 else 59

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun afterTextChanged(s: Editable) {
            if (s.toString() != current) {
                val userInput = s.toString()
                if (userInput.length <= 2) {
                    when {
                        userInput.isEmpty() -> {
                            current = "00"
                            s.filters = arrayOfNulls<InputFilter>(0)
                        }
                        userInput.toInt() < 0 -> {
                            current = limit.toString()
                            s.filters = arrayOfNulls<InputFilter>(0)
                        }
                        userInput.toInt() > limit -> {
                            current = "00"
                            s.filters = arrayOfNulls<InputFilter>(0)
                        }


                        userInput.length == 1 -> {
                            current = "0".plus(userInput)
                            s.filters = arrayOfNulls<InputFilter>(0)
                        }


                        else -> current = userInput

                    }
                }
                s.replace(0, s.length, current, 0, current.length)
            }
        }

        companion object {
            private val nonDigits = Regex("[^\\d]")
        }
    }


    fun setTimeControlEditListener(editTime: EditText, isHours : Boolean) {
        editTime.addTextChangedListener(TimeFormattingTextWatcher(isHours))
    }







}