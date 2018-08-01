package com.kdrag0n.tipatch

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.PreferenceManager
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
import com.kdrag0n.utils.*
import eu.chainfire.libsuperuser.Shell
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.File
import java.io.InputStreamReader

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var inputSource = ImageLocation.FILE
    private var outputDest = ImageLocation.FILE
    private lateinit var safInput: Uri
    private lateinit var opts: SharedPreferences
    private var isRooted = false
    private var slotsPatched = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        opts = PreferenceManager.getDefaultSharedPreferences(baseContext)
        opts.registerOnSharedPreferenceChangeListener(this)

        // could end up with overlapping fragments
        if (savedInstanceState == null) {
            optFrag = OptionFragment()
            optFrag.retainInstance = true

            fragmentManager
                    .beginTransaction()
                    .add(R.id.optContainer, optFrag)
                    .commit()

            asyncExec {
                if (Shell.SU.available()) {
                    hasRoot()
                }
            }
        } else {
            isRooted = savedInstanceState.getBoolean("rooted", false)
            inputSource = ImageLocation.valueOf(savedInstanceState.getString("input"))
            outputDest = ImageLocation.valueOf(savedInstanceState.getString("output"))
        }

        optFrag.inputEvent = {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*" // .img

            startActivityForResult(intent, 42)
        }

        optFrag.outputEvent = {

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
                                    asyncPatch(getProp("ro.boot.slot_suffix"))
                                }

                                override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest?, token: PermissionToken?) {}
                            }
                    ))
                    .check()
        }

        if (resources.getBoolean(R.bool.isPhone)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
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

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        outState?.putBoolean("rooted", isRooted)
        outState?.putString("input", inputSource.name)
        outState?.putString("output", outputDest.name)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 42 && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                safInput = data.data
            }
        }
    }

    private fun hasRoot() {
        isRooted = true

        runOnUiThread {
            with (optFrag.preferenceManager) {
                val optPart = findPreference("partition")
                optPart.isEnabled = true
                (optPart as CheckBoxPreference).isChecked = true

                findPreference("input").isEnabled = false
                findPreference("output").isEnabled = false

                inputSource = ImageLocation.PARTITION
                outputDest = ImageLocation.PARTITION
            }
        }
    }

    private fun patch(progress: (String) -> Unit, reader: () -> ByteArray?,
                      writer: (ByteArray) -> Unit) {
        progress("Reading image")
        val data = reader()

        if (data == null) {
            when (inputSource) {
                ImageLocation.FILE -> errorDialog("Tipatch was unable to read your image file. Make sure you have permission to access it.")
                ImageLocation.PARTITION ->
                    errorDialog("Tipatch was unable to find your device's recovery partition. Select an image file and flash it instead.")
            }
            return
        }

        progress("Unpacking image")
        val image = Tipatch.unpackImageBytes(data)

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

        progress("Repacking image")
        val bytes = image.dumpBytes()

        progress("Writing image")
        writer(bytes)

        progress("Finished!")
        runOnUiThread {
            Toast.makeText(this, "Finished!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun asyncPatch(slot: String?) {
        val parti = when (inputSource) {
            ImageLocation.PARTITION -> partPath(slot) ?: {
                errorDialog("Tipatch was unable to find your device's recovery partition. Select an image file and flash it instead.")
            }()
            else -> null
        }

        if (parti == Unit) {
            return
        }

        val partiPath = parti as String?

        val currentSlot = when (slot) {
            null -> null
            else -> {
                when {
                    inputSource == ImageLocation.FILE -> null
                    slot.equals("_a", true) -> "A"
                    slot.equals("_b", true) -> "B"
                    else -> "unknown"
                }
            }
        }

        val ctx = this

        val task =
        @SuppressLint("StaticFieldLeak")
        object : AsyncTask<Unit, Unit, Unit>() {
            private var dialog = ProgressDialog(ctx)

            override fun onPreExecute() {
                val message = "Patching image"

                with (dialog) {
                    setTitle(when (currentSlot) {
                        null -> message
                        "unknown" -> "$message (unknown slot)"
                        else -> "$message (slot $currentSlot)"
                    })
                    setMessage("Starting patcher")
                    show()
                }
            }

            override fun doInBackground(vararg params: Unit?) {
                patch(
                        progress = { step ->
                            Log.i(logTag, "step slot=$currentSlot")

                            runOnUiThread {
                                dialog.setMessage(step)
                            }
                        },

                        reader = {
                            when (inputSource) {
                                ImageLocation.FILE -> getSafData()
                                ImageLocation.PARTITION -> readRootFile(partiPath!!)
                            }
                        },

                        writer = {
                            when (inputSource) {
                                ImageLocation.FILE -> writeSafData(it)
                                ImageLocation.PARTITION -> writeRootFile(partiPath!!, it)
                            }
                        }
                )
            }

            override fun onPostExecute(result: Unit?) {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }

                if (slot != null) {
                    ++slotsPatched

                    if (slotsPatched >= 2) {
                        slotsPatched = 0
                        return
                    }

                    val otherSlot = when (currentSlot) {
                        "A" -> "_b"
                        "B" -> "_a"
                        else -> return
                    }

                    asyncPatch(otherSlot)
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

    private fun getSafData(): ByteArray? {
        return if (::safInput.isInitialized) {
            val fis = contentResolver.openInputStream(safInput)
            val buf = ByteArray(fis.available())

            DataInputStream(fis).use {
                it.readFully(buf)
            }

            buf
        } else {
            null
        }
    }

    private fun writeSafData(data: ByteArray) {

    }

    private fun partPath(slot: String?): String? {
        // the most common one
        val bdPath = when (slot) {
            null -> "/dev/block/bootdevice/by-name/recovery"
            else -> "/dev/block/bootdevice/by-name/boot$slot"
        }

        if (File(bdPath).exists()) {
            return bdPath
        }

        val partNames = when (slot) {
            null -> setOf("recovery", "RECOVERY", "SOS")
            else -> setOf("boot$slot", "BOOT$slot")
        }

        // time to do some hunting...
        // need API 26 for nio.Files
        findPartitionDirs().forEach {
            it.listFiles().forEach {
                if (it.name in partNames) {
                    return it.absolutePath
                }
            }
        }

        return null
    }

    override fun onSharedPreferenceChanged(pref: SharedPreferences?, key: String?) {
        if (key == "partition") {
            val partEnabled = pref?.getBoolean(key, false) ?: false

            optFrag.preferenceManager.findPreference("input").isEnabled = !partEnabled
            optFrag.preferenceManager.findPreference("output").isEnabled = !partEnabled

            inputSource = when (partEnabled) {
                true -> ImageLocation.PARTITION
                false -> ImageLocation.FILE
            }
        }
    }

    companion object {
        lateinit var optFrag: OptionFragment
    }
}
