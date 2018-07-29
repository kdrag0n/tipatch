package com.kdrag0n.tipatch

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.Preference
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.CompositePermissionListener
import com.karumi.dexter.listener.single.PermissionListener
import com.karumi.dexter.listener.single.SnackbarOnDeniedPermissionListener
import com.kdrag0n.utils.asyncExec
import com.kdrag0n.utils.logTag
import com.kdrag0n.utils.raw
import eu.chainfire.libsuperuser.Shell
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

class MainActivity : AppCompatActivity() {
    var inputSource = inputs[0]
    var outputDest = outputs[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // could end up with overlapping fragments
        if (savedInstanceState == null) {
            fragmentManager
                    .beginTransaction()
                    .add(R.id.optContainer, OptionFragment())
                    .commit()
        }

        patchBtn.setOnClickListener { _ ->
            Dexter.withActivity(this)
                    .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .withListener(CompositePermissionListener(
                            SnackbarOnDeniedPermissionListener.Builder
                                    .with(findViewById(android.R.id.content), "Storage permission is needed to load/save files")
                                    .withOpenSettingsButton("Grant in Settings")
                                    .build(),

                            object : PermissionListener {
                                override fun onPermissionDenied(response: PermissionDeniedResponse?) {}

                                override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                                    asyncPatch()
                                }

                                override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest?, token: PermissionToken?) {}
                            }
                    ))
                    .check()
        }

        asyncExec {
            if (Shell.SU.available()) {
                runOnUiThread {
                    // TODO: enable partition pref
                    Toast.makeText(this, "SU detected", Toast.LENGTH_SHORT)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.aboutOpt -> Toast.makeText(this, "TODO: about screen", Toast.LENGTH_SHORT).show()
            R.id.contactOpt -> showContactDialog()
            R.id.helpOpt -> Toast.makeText(this, "TODO: help dialog", Toast.LENGTH_SHORT).show()
        }

        return true
    }

    private fun patch(inputPath: String, outputPath: String, progress: (String) -> Unit) {
        progress("Opening image")
        val input = File("/sdcard/twrp_in.img") // TODO: SAF
        val fin = input.inputStream()

        progress("Unpacking image")
        val image = Tipatch.unpackImageFd(fin.fd.raw().toLong())

        fin.close()

        val cMode = Tipatch.detectCompressor(image.ramdisk)
        val cName = when (cMode) {
            Tipatch.CompGzip -> "gzip"
            Tipatch.CompLz4 -> "lz4"
            Tipatch.CompLzo -> "lzo"
            Tipatch.CompXz -> "xz"
            Tipatch.CompLzma -> "lzma"
            Tipatch.CompBzip2 -> "bzip2"
            else -> "unknown"
        }

        progress("Decompressing ramdisk of type $cName")
        var ramdisk = Tipatch.extractRamdisk(image.ramdisk, cMode)

        progress("Patching ramdisk")
        ramdisk = Tipatch.patchRamdisk(ramdisk)

        progress("Compressing ramdisk")
        image.ramdisk = Tipatch.compressRamdisk(ramdisk, cMode)

        progress("Repacking and writing image")
        val output = File("/sdcard/twrp_out.img") // TODO: SAF
        val fout = output.outputStream()

        image.writeToFd(fout.fd.raw().toLong())

        fout.close()
        progress("Finished!")

        runOnUiThread {
            Toast.makeText(this, "Finished!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun asyncPatch() {
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
                patch("", "") { step ->
                    Log.i(logTag, step)

                    runOnUiThread {
                        dialog.setMessage(step)
                    }
                }
            }

            override fun onPostExecute(result: Unit?) {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
        }

        task.execute()
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

    companion object {
        private val inputs = arrayOf("Recovery partition (root)", "File")
        private val outputs = inputs
    }
}
