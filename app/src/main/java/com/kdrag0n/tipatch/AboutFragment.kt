package com.kdrag0n.tipatch

import android.os.Bundle
import android.preference.PreferenceFragment
import android.widget.ListView
import com.kdrag0n.utils.openUri

class AboutFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.about)

        with (preferenceManager) {
            val ver = findPreference("version")
            ver.summary = "Version ${BuildConfig.VERSION_NAME}"

            findPreference("source").setOnPreferenceClickListener {
                activity.openUri(R.string.source_uri())
                return@setOnPreferenceClickListener true
            }

            findPreference("donate").setOnPreferenceClickListener {
                activity.openUri(R.string.donate_uri())
                return@setOnPreferenceClickListener true
            }

            findPreference("telegram").setOnPreferenceClickListener {
                activity.openUri(R.string.telegram_uri())
                return@setOnPreferenceClickListener true
            }

            findPreference("github").setOnPreferenceClickListener {
                activity.openUri(R.string.github_uri())
                return@setOnPreferenceClickListener true
            }

            findPreference("xda").setOnPreferenceClickListener {
                activity.openUri(R.string.xda_uri())
                return@setOnPreferenceClickListener true
            }

            findPreference("email").setOnPreferenceClickListener {
                activity.openUri("mailto:" + R.string.contact_mail().replace(" (at) ", "@"))
                return@setOnPreferenceClickListener true
            }
        }
    }

    private operator fun Int.invoke(): String {
        return resources.getString(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        view.findViewById<ListView>(android.R.id.list).divider = null
    }
}