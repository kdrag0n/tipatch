package com.kdrag0n.tipatch

import android.os.Bundle
import android.preference.PreferenceFragment

class OptionFragment : PreferenceFragment() {
    lateinit var inputEvent: () -> Unit
    lateinit var outputEvent: () -> Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.options)

        with (preferenceManager) {
            findPreference("input").setOnPreferenceClickListener {
                inputEvent()
                return@setOnPreferenceClickListener true
            }

            findPreference("output").setOnPreferenceClickListener {
                outputEvent()
                return@setOnPreferenceClickListener true
            }
        }
    }
}