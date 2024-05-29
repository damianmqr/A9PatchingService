package com.lmqr.ha9_comp_service

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference

class ColorProgressPreference(context: Context, attrs: AttributeSet) : SeekBarPreference(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val seekBar = holder.findViewById(androidx.preference.R.id.seekbar) as? SeekBar
        val valueTextView = holder.findViewById(androidx.preference.R.id.seekbar_value) as? TextView
        seekBar?.run {
            progressDrawable = AppCompatResources.getDrawable(context, R.drawable.color_gradient)
            setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueTextView.setColorProgress(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    this@ColorProgressPreference.value = progress
                }
            })
            valueTextView.setColorProgress(progress)
        }
        valueTextView?.visibility = View.VISIBLE
    }
}

fun Int.progressToHex() = (((this * 0xFF / 100) and 0xFF)*0x10101).toString(16).padStart(6, '0').uppercase()

private fun TextView?.setColorProgress(v: Int){
    val vStr = "0x${v.progressToHex()}"
    this?.text = vStr
}