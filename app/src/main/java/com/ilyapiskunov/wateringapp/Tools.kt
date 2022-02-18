package com.ilyapiskunov.wateringapp

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import org.jetbrains.anko.alert

object Tools {

    private val repeatListener = RepeatListener(true, 200, 35);


    class RepeatListener(private val immediateClick: Boolean, private val interval: Int, private val intervalAcceleration: Int) :
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
                    if (!haveClicked) activeView!!.performClick()
                    clearHandler()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
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


    fun setTimeControlButtonListener(btn : Button, editTime : EditText, isInc: Boolean) {
        btn.setOnClickListener {

            var hrs = editTime.text.toString().toInt()
            hrs = if (isInc) ++hrs else --hrs
            editTime.setText(hrs.toString())
        }

        btn.setOnTouchListener(repeatListener)

    }

    fun setTimeIncButtonListener(btn: Button, editTime: EditText) {
        setTimeControlButtonListener(btn, editTime, true)
    }

    fun setTimeDecButtonListener(btn: Button, editTime: EditText) {
        setTimeControlButtonListener(btn, editTime, false)
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



    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }



}