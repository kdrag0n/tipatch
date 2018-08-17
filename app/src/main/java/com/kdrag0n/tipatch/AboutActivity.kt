package com.kdrag0n.tipatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_about.*

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        about_version.summary = BuildConfig.VERSION_NAME

        about_source.summary = R.string.about_src_desc()
        about_source.uri = R.string.source_uri

        about_oss.removeSummary()
        about_oss.setOnClickListener {
            val intent = Intent(this, LicenseActivity::class.java)
            startActivity(intent)
        }

        about_donate.summary = R.string.about_donate_desc()
        about_donate.uri = R.string.donate_uri

        about_author.summary = R.string.author_nick()
        about_author.uri = R.string.website_uri

        about_github.removeSummary()
        about_github.uri = R.string.github_uri

        about_telegram.removeSummary()
        about_telegram.uri = R.string.telegram_uri

        about_xda.removeSummary()
        about_xda.uri = R.string.xda_uri

        about_email.removeSummary()
        about_email.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("mailto:" + R.string.contact_mail().replace(" (at) ", "@"))))
        }
    }

    private operator fun Int.invoke(vararg fmt: Any): String {
        return resources.getString(this, *fmt)
    }
}
