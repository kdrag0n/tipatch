package com.kdrag0n.tipatch

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.View
import kotlinx.android.synthetic.main.activity_about.*

class AboutActivity : AppCompatActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_about)

        setSupportActionBar(toolbar_about as Toolbar?)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        about_version.summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        about_version.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                startActivity(intent)
            }
        }

        about_source.summary = getString(R.string.about_src_desc)
        about_source.uri = R.string.source_uri

        about_oss.removeSummary()
        about_oss.setOnClickListener {
            val intent = Intent(this, LicenseActivity::class.java)
            startActivity(intent)
        }

        if (resources.getBoolean(R.bool.showPaypal)) {
            about_donate.summary = getString(R.string.about_donate_desc)
            about_donate.uri = R.string.donate_uri
        } else {
            about_donate.visibility = View.GONE
        }

        about_author.summary = getString(R.string.author_nick)
        about_author.uri = R.string.website_uri

        about_telegram.removeSummary()
        about_telegram.uri = R.string.telegram_uri

        about_xda.removeSummary()
        about_xda.uri = R.string.xda_uri

        about_email.removeSummary()
        about_email.setOnClickListener {
            mainParent.contactDev(ctx = this)
        }
    }

    companion object {
        lateinit var mainParent: MainActivity
    }
}
