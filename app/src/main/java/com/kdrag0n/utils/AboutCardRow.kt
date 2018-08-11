/*
 * Copyright 2016 dvdandroid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kdrag0n.utils

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import com.kdrag0n.tipatch.R

/**
 * @author dvdandroid
 */
class AboutCardRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private val title: String?
    private val icon: Drawable?

    private val mTitle: TextView
    private val mSummary: TextView
    private val mIcon: ImageView

    private val mView: View

    var summary: CharSequence
    set(s) {
        mSummary.text = s
    }
    get() = mSummary.text

    var uri: Int
    set(i) {
        setOnClickListener {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(i))))
        }
    }
    get() = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.info_item_row, this)
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.AboutCardRow, 0, 0)

        try {
            title = a.getString(R.styleable.AboutCardRow_text)
            icon = a.getDrawable(R.styleable.AboutCardRow_icon)
        } finally {
            a.recycle()
        }

        mView = findViewById(R.id.container)

        mTitle = findViewById(android.R.id.title)
        mSummary = findViewById(android.R.id.summary)
        mIcon = findViewById(android.R.id.icon)

        mTitle.text = title
        mIcon.setImageDrawable(icon)
    }

    override fun setOnClickListener(l: View.OnClickListener?) {
        super.setOnClickListener(l)

        mView.setOnClickListener(l)
    }

    fun removeSummary() {
        mSummary.visibility = View.GONE
    }
}