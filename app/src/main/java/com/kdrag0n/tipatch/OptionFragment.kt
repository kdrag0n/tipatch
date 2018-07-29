package com.kdrag0n.tipatch

import android.os.Bundle
import android.preference.PreferenceFragment

class OptionFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.options)
    }
}