package com.kdrag0n.tipatch

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.kdrag0n.utils.asyncExec
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    @Volatile private var libLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        asyncExec {
            setupLibrary()
        }

        patchBtn.setOnClickListener { _ ->
            if (!libLoaded) {
                with (AlertDialog.Builder(this)) {
                    setMessage("Tipatch has not finished loading. Please wait a few seconds or re-open the app.")
                    setPositiveButton(getString(R.string.ok)) { _, _ -> }
                    show()
                }
            }

            val ctx = this
            val task =
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
                    TimeUnit.SECONDS.sleep(2)
                }

                override fun onPostExecute(result: Unit?) {
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                }
            }

            task.execute()
        }
    }

    private fun setupLibrary() {
        // TODO: catch and handle errors
        System.loadLibrary("gojni")

        libLoaded = true
    }

    private fun showContactDialog() {
        Toast.makeText(applicationContext, "TODO: contact buttons", Toast.LENGTH_SHORT).show()
    }

    private fun errorDialog(message: String) {
        runOnUiThread {
            with (AlertDialog.Builder(this)) {
                setTitle("Oops...")
                setMessage(message)
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
