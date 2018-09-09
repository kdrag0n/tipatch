package com.kdrag0n.tipatch

import android.content.Context
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.PreferenceFragment
import android.widget.ListView

class OptionFragment : PreferenceFragment() {
    private var handler: Callbacks? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        retainInstance = true

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

    override fun onActivityCreated(state: Bundle?) {
        super.onActivityCreated(state)
        view?.findViewById<ListView>(android.R.id.list)?.divider = null
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