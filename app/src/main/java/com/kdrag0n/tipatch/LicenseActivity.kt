package com.kdrag0n.tipatch

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import com.kdrag0n.utils.AboutCardRow
import kotlinx.android.synthetic.main.activity_license.*

class LicenseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)

        actionBar?.setDisplayHomeAsUpEnabled(true)

        oss_libsu.license(License.APACHE, 2018, "John \"topjohnwu\" Wu")
        oss_about.license(License.APACHE, 2016, "dvdandroid")
        oss_leak.license(License.APACHE, 2015, "Square, Inc")
        oss_gzipcpp.license(License.MIT, 2016, "Mera, Inc.")
    }

    private fun AboutCardRow.license(license: License, year: Int, author: String) {
        val text = String.format(license.text, year, author)

        setOnClickListener {
            val dialog = with (AlertDialog.Builder(this@LicenseActivity)) {
                setMessage(text)
                setPositiveButton(android.R.string.ok) { _, _ -> }
                create()
            }

            dialog.show()
            val textView = dialog.findViewById<TextView>(android.R.id.message)
            Linkify.addLinks(textView, Linkify.WEB_URLS)
            textView.movementMethod = LinkMovementMethod.getInstance()
        }

        summary = "by $author\n${license.shortName}"
    }

    private enum class License(val shortName: String, val text: String) {
        MIT("MIT License", """Copyright © %d %s

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."""),
        APACHE("Apache License 2.0", """Copyright %d %s

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."""),
        GPL2("GPL v2", """Due to size, the full GNU General Public License v2 has not been included. You can view the full license here: https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html#SEC1""")
    }

    private operator fun Int.invoke(vararg fmt: Any): String {
        return resources.getString(this, *fmt)
    }
}
