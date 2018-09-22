package com.kdrag0n.tipatch

import android.content.Context
import android.os.Bundle
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.PreferenceFragmentCompat
import android.widget.ListView

class OptionFragment : PreferenceFragmentCompat() {
    private var handler: Callbacks? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        retainInstance = true
    }

    override fun onCreatePreferences(p0: Bundle?, p1: String?) {
        addPreferencesFromResource(R.xml.options)

        with (preferenceManager) {
            findPreference("input")?.setOnPreferenceClickListener {
                handler?.onInputSelect()
                true
            }

            findPreference("output")?.setOnPreferenceClickListener {
                handler?.onOutputSelect()
                true
            }
            findPreference("partition")?.setOnPreferenceClickListener {
                handler?.onPartitionChange((it as CheckBoxPreference).isChecked)
                true
            }
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        handler = context as Callbacks?
    }

    override fun onDetach() {
        super.onDetach()
        handler = null
    }

    internal interface Callbacks {
        fun onInputSelect()
        fun onOutputSelect()
        fun onPartitionChange(state: Boolean)
    }
}