package com.kdrag0n.tipatch

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        if (savedInstanceState == null) {
            val frag = AboutFragment()
            frag.retainInstance = true

            fragmentManager
                    .beginTransaction()
                    .add(R.id.about_container, frag)
                    .commit()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}
