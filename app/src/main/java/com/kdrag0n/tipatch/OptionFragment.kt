package com.kdrag0n.tipatch

import android.os.Bundle
import android.preference.PreferenceFragment
import android.widget.ListView

class OptionFragment : PreferenceFragment() {
    lateinit var inputEvent: () -> Unit
    lateinit var outputEvent: () -> Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.options)

        with (preferenceManager) {
            findPreference("input")?.setOnPreferenceClickListener {
                inputEvent()
                true
            }

            findPreference("output")?.setOnPreferenceClickListener {
                outputEvent()
                true
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        view?.findViewById<ListView>(android.R.id.list)?.divider = null
    }
}