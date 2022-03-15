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

class ChannelRecyclerAdapter(private val channels : ArrayList<Channel>) : RecyclerView.Adapter<ChannelRecyclerAdapter.ChannelViewHolder>() {

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


        val timeOn : AlarmTime = channel.timeOn

        holder.timeOn.editHrs.setText(timeOn.toString(AlarmTime.TimeField.HOURS))
        holder.timeOn.editMin.setText(timeOn.toString(AlarmTime.TimeField.MINUTES))
        holder.timeOn.editSec.setText(timeOn.toString(AlarmTime.TimeField.SECONDS))

        val timeOff : AlarmTime = channel.timeOff
        holder.timeOff.editHrs.setText(timeOff.toString(AlarmTime.TimeField.HOURS))
        holder.timeOff.editMin.setText(timeOff.toString(AlarmTime.TimeField.MINUTES))
        holder.timeOff.editSec.setText(timeOff.toString(AlarmTime.TimeField.SECONDS))
        
        val holderTimeOn = holder.timeOn
        val holderTimeOff = holder.timeOff
        setTimeControlButtonListener(holderTimeOn.btnIncHrs, holderTimeOn.editHrs, position) {timeOn.incHours() }
        setTimeControlButtonListener(holderTimeOn.btnIncMin, holderTimeOn.editMin, position) {timeOn.incMinutes()}
        setTimeControlButtonListener(holderTimeOn.btnIncSec, holderTimeOn.editSec, position) {timeOn.incSeconds()}
        setTimeControlButtonListener(holderTimeOn.btnDecHrs, holderTimeOn.editHrs, position) {timeOn.decHours()}
        setTimeControlButtonListener(holderTimeOn.btnDecMin, holderTimeOn.editMin, position) {timeOn.decMinutes()}
        setTimeControlButtonListener(holderTimeOn.btnDecSec, holderTimeOn.editSec, position) {timeOn.decSeconds()}

        setTimeControlButtonListener(holderTimeOff.btnIncHrs, holderTimeOff.editHrs, position) {timeOff.incHours() }
        setTimeControlButtonListener(holderTimeOff.btnIncMin, holderTimeOff.editMin, position) {timeOff.incMinutes() }
        setTimeControlButtonListener(holderTimeOff.btnIncSec, holderTimeOff.editSec, position) {timeOff.incSeconds() }
        setTimeControlButtonListener(holderTimeOff.btnDecHrs, holderTimeOff.editHrs, position) {timeOff.decHours() }
        setTimeControlButtonListener(holderTimeOff.btnDecMin, holderTimeOff.editMin, position) {timeOff.decMinutes() }
        setTimeControlButtonListener(holderTimeOff.btnDecSec, holderTimeOff.editSec, position) {timeOff.decSeconds() }

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
        val btnOpen : Button = itemView.btnOpen
        val btnClose : Button = itemView.btnClose
        var btnSkipDay : Button = itemView.btnSkipDay
        var btnEveryDay : Button = itemView.btnEveryDay
        val tvWeekOdd : TextView = itemView.tvWeekOdd
        val tvWeekEven : TextView = itemView.tvWeekEven
        var daysOfWeek1 : LinearLayout = itemView.days_of_week_1 as LinearLayout
        var daysOfWeek2 : LinearLayout = itemView.days_of_week_2 as LinearLayout
        val tvTimeOn : TextView = itemView.tvTimeOn
        val tvTimeOff : TextView = itemView.tvTimeOff
        var timeOn : View = itemView.time_on
        var timeOff : View = itemView.time_off

    }

    class RepeatListener(private val immediateClick: Boolean, private val interval: Int, private val intervalAcceleration: Int/*, private val notifyCallback: () -> Unit*/) :
        View.OnTouchListener {

        private var activeView: View? = null
        private val handler = Handler(Looper.getMainLooper())
        private var handlerRunnable: Runnable
        private var haveClicked = false
        //private val notifyHandler = Handler(Looper.getMainLooper())
        private var repeatInterval: Int = interval
        private val repeatIntervalMin: Int = 70
        //private val notifyRunnable = Runnable (notifyCallback)

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
                    //notifyHandler.removeCallbacks(notifyRunnable)
                    handler.removeCallbacks(handlerRunnable)
                    handler.postDelayed(handlerRunnable, repeatInterval.toLong())
                    activeView = view
                    if (immediateClick) activeView!!.performClick()
                    haveClicked = immediateClick
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    // If we haven't clicked yet, click now
                    Log.i("timer click handler", "ACTION UP")
                    if (!haveClicked) activeView!!.performClick()
                    clearHandler()
                    //notifyHandler.removeCallbacks(notifyRunnable)
                    //notifyHandler.postDelayed(notifyRunnable, 600)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    Log.i("timer click handler", "ACTION CANCEL")
                    clearHandler()
                    //notifyCallback()
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


    fun setTimeControlButtonListener(btn : Button, editTime : EditText, position: Int, applyOperation: () -> Int) {
        btn.setOnClickListener {
            val value = applyOperation()
            editTime.setText(String.format("%02d", value))
        }
        val repeatListener = RepeatListener(true, 200, 35) //{ notifyItemChanged(position) }

        btn.setOnTouchListener(repeatListener)

    }


    class TimeFormattingTextWatcher(isHours: Boolean) : TextWatcher {
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