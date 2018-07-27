package com.kdrag0n.tipatch

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.kdrag0n.utils.asyncExec
import com.kdrag0n.utils.getProp
import eu.chainfire.libsuperuser.Shell
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    private lateinit var execPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        asyncExec {
            try {
                setupExecutable()
            } catch (err: IllegalStateException) {
                runOnUiThread {
                    with (AlertDialog.Builder(this)) {
                        setTitle("Oops...")
                        setMessage(err.message)
                        setPositiveButton(R.string.contact) { _, _ ->
                            showContactDialog()
                        }
                        setNegativeButton(R.string.exit) { _, _ ->
                            finish()
                        }
                        setCancelable(false)
                        show()
                    }
                }
            }
        }

        patchBtn.setOnClickListener { _ ->
            if (!::execPath.isInitialized) {
                with (AlertDialog.Builder(this)) {
                    setTitle("Status")
                    setMessage("Tipatch has not finished setting up. Please wait a few seconds or re-open the app.")
                    show()
                }
            }

            val ctx = this
            @SuppressLint("StaticFieldLeak")
            object : AsyncTask<Unit, Unit, Unit>() {
                private val dialog = ProgressDialog(ctx)

                override fun onPreExecute() {
                    with (dialog) {
                        setTitle("Patching image")
                        setMessage("Starting patcher")
                        show()
                    }
                }

                override fun doInBackground(vararg params: Unit?) {
                    val proc = Runtime.getRuntime().exec(arrayOf(execPath, inputPath))
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                }

                override fun onPostExecute(result: Unit?) {
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                }
            }
        }
    }

    private fun getArch(): String? {
        val abi = getProp("ro.product.cpu.abi")

        return when (abi) {
            "armeabi" -> "arm"
            "armeabi-v7a" -> "armv7"
            "x86" -> "x86"
            "arm64-v8a" -> "arm64"
            "x86_64" -> "x86_64"
            "mips" -> "mips"
            "mips64" -> "mips64"
            else -> abi
        }
    }

    private fun setupExecutable() {
        val arch = getArch()

        val res = resources.openRawResource(when (arch) {
            "armv7" -> R.raw.tipatch_armv7
            "arm64" -> R.raw.tipatch_arm64
            "x86" -> R.raw.tipatch_x86
            "x86_64" -> R.raw.tipatch_x86_64
            else -> {
                if (arch == null) {
                    throw IllegalStateException("Sorry, Tipatch was unable to detect the architecture of your device. Please contact the developer for assistance.")
                } else if ("armeabi" in arch && arch != "armeabi") { // other variants..?
                    R.raw.tipatch_armv7
                } else {
                    throw IllegalStateException("Sorry, Tipatch does not currently support your architecture '$arch'. Contact the developer for help.")
                }
            }
        })

        val buf = ByteArray(res.available())
        res.use {
            it.read(buf)
        }

        openFileOutput("tipatch_bin", Context.MODE_PRIVATE).use {
            it.write(buf)
        }

        val file = getFileStreamPath("tipatch_bin")
        file.setExecutable(true)

        execPath = file.absolutePath
    }

    private fun showContactDialog() {
        Toast.makeText(applicationContext, "TODO: contact buttons", Toast.LENGTH_SHORT).show()
    }
}
