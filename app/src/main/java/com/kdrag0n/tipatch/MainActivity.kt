package com.kdrag0n.tipatch

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.PreferenceManager
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import com.commonsware.cwac.crossport.design.widget.Snackbar
import com.crashlytics.android.Crashlytics
import com.kdrag0n.tipatch.jni.Image
import com.kdrag0n.tipatch.jni.ImageException
import com.kdrag0n.utils.*
import com.squareup.leakcanary.LeakCanary
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuProcessFileInputStream
import com.topjohnwu.superuser.io.SuProcessFileOutputStream
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private const val REQ_SAF_INPUT = 100
private const val REQ_SAF_OUTPUT = 101

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var inputSource = ImageLocation.FILE
    private var outputDest = ImageLocation.FILE
    private lateinit var safInput: Uri
    private lateinit var safOutput: Uri
    private lateinit var opts: SharedPreferences
    private lateinit var optFrag: OptionFragment
    private var firstRun = false
    private val reversePref: CheckBoxPreference
        get() = optFrag.preferenceManager.findPreference("reverse") as CheckBoxPreference
    private var isRooted = false
    private var slotsPatched = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (LeakCanary.isInAnalyzerProcess(this)) {
            return
        }
        LeakCanary.install(application)

        setContentView(R.layout.activity_main)

        opts = PreferenceManager.getDefaultSharedPreferences(baseContext)
        opts.registerOnSharedPreferenceChangeListener(this)

        optFrag = OptionFragment()
        optFrag.retainInstance = true

        fragmentManager
                .beginTransaction()
                .add(R.id.opt_container, optFrag)
                .commit()

        if (savedInstanceState == null) {
            asyncExec {
                try {
                    if (Shell.rootAccess()) {
                        hasRoot()
                    } else {
                        noRoot()
                    }
                } catch (e: Exception) {
                    noRoot()
                }
            }
        } else {
            isRooted = savedInstanceState.getBoolean("rooted", false)
            inputSource = ImageLocation.valueOf(savedInstanceState.getString("input", "FILE"))
            outputDest = ImageLocation.valueOf(savedInstanceState.getString("output", "FILE"))
        }

        optFrag.inputEvent = {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/octet-stream" // no .img type

            startActivityForResult(intent, REQ_SAF_INPUT)
        }

        optFrag.outputEvent = {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)

            intent.type = "application/octet-stream" // no .img type

            var outName = outputFileName(if (::safInput.isInitialized) safInput else null)
            if (reversePref.isChecked) {
                outName = outName.replace("-tipatched", "")
            }

            intent.putExtra(Intent.EXTRA_TITLE, outName)

            startActivityForResult(intent, REQ_SAF_OUTPUT)
        }

        patchBtn.setOnClickListener { _ ->
            asyncPatch(getProp("ro.boot.slot_suffix"))
        }

        if (Build.VERSION.SDK_INT < 26) {
            patchBtn.setOnLongClickListener { _ ->
                Toast.makeText(this, R.string.patch_btn, Toast.LENGTH_SHORT).show()
                true
            }
        }

        if (resources.getBoolean(R.bool.isPhone)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        if (opts.getBoolean("first_run", true)) {
            firstRun = true
            showHelpDialog()

            opts.edit().putBoolean("first_run", false).apply()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.actions, menu ?: return true)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.aboutOpt -> showAboutActivity()
            R.id.contactOpt -> contactDev()
            R.id.donateOpt -> openUri(R.string.donate_uri())
            R.id.helpOpt -> showHelpDialog()
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
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                REQ_SAF_INPUT -> safInput = data.data ?: return
                REQ_SAF_OUTPUT -> safOutput = data.data ?: return
            }
        }
    }

    private operator fun Int.invoke(vararg fmt: Any): String {
        return resources.getString(this, *fmt)
    }

    private fun hasRoot() {
        isRooted = true

        runOnUiThread {
            with (optFrag.preferenceManager) {
                val optPart = findPreference("partition")
                optPart?.isEnabled = true

                if (firstRun || opts.getBoolean("partition", false)) {
                    findPreference("input")?.isEnabled = false
                    findPreference("output")?.isEnabled = false

                    (optPart as CheckBoxPreference?)?.isChecked = true
                    inputSource = ImageLocation.PARTITION
                    outputDest = ImageLocation.PARTITION
                }
            }
        }
    }

    private fun noRoot() {
        isRooted = false

        runOnUiThread {
            with (optFrag.preferenceManager) {
                val optPart = findPreference("partition")
                optPart?.isEnabled = false
                (optPart as CheckBoxPreference?)?.isChecked = false

                findPreference("input")?.isEnabled = true
                findPreference("output")?.isEnabled = true

                inputSource = ImageLocation.FILE
                outputDest = ImageLocation.FILE

                opts.edit().putBoolean("partition", false).apply()
            }
        }
    }

    private fun patch(progress: (String) -> Unit, fis: InputStream, fos: OutputStream): Boolean {
        progress(R.string.step1_read_unpack())
        val image = Image(fis)

        val cMode = image.detectCompressor()
        val cName = when (cMode) {
            Image.CompGzip -> "gzip"
            Image.CompLz4 -> "lz4"
            Image.CompLzo -> "lzo"
            Image.CompXz -> "xz"
            Image.CompLzma -> "lzma"
            Image.CompBzip2 -> "bzip2"
            else -> "unknown"
        }

        /*

        progress(R.string.step2_decompress(cName))
        image.decompressRamdisk(cMode)

        val direction = when (reversePref.isChecked) {
            true -> Tipatch.ReplReverse
            false -> Tipatch.ReplNormal
        }

        if (reversePref.isChecked) {
            progress(R.string.step3_patch_rev())
        } else {
            progress(R.string.step3_patch())
        }

        image.patchRamdisk(direction)

        progress(R.string.step4_compress())
        image.compressRamdisk(cMode)

        progress(R.string.step5_pack_write())
        val wrapped = Tipatch.wrapWriter(writer)
        image.writeHeader(wrapped)
        image.writeKernel(wrapped)
        image.writeRamdisk(wrapped)
        image.writeSecond(wrapped)
        image.writeDeviceTree(wrapped)*/

        return true
    }

    private fun snack(text: String): Snackbar {
        return Snackbar.make(rootCoordinator, text, Snackbar.LENGTH_SHORT)
    }

    private fun snack(textRes: Int): Snackbar {
        return snack(textRes())
    }

    private fun asyncPatch(slot: String?) {
        if (inputSource == ImageLocation.FILE) {
            if (!::safInput.isInitialized) {
                errorDialog(R.string.file_select_input())
                return
            } else if (!::safOutput.isInitialized) {
                errorDialog(R.string.file_select_output())
                return
            }
        }

        val partiPath = if (inputSource == ImageLocation.PARTITION) {
            val pp = partPath(slot)
            if (pp == null) {
                errorDialog(R.string.part_not_found())
                return
            }

            pp
        } else {
            null
        }

        val currentSlot = if (slot != null && inputSource == ImageLocation.PARTITION) {
            when {
                slot.equals("_a", true) -> "A"
                slot.equals("_b", true) -> "B"
                else -> "unknown"
            }
        } else {
            null
        }

        val ctx = this

        val task =
        @SuppressLint("StaticFieldLeak")
        object : AsyncTask<Unit, Unit, Unit>() {
            private var dialog = ProgressDialog(ctx, R.style.DialogTheme)
            private var success = false
            private var currentStep = ""

            override fun onPreExecute() {
                with (dialog) {
                    setTitle(when (currentSlot) {
                        null -> R.string.header_patching()
                        "unknown" -> R.string.header_patching_unknown_slot()
                        else -> R.string.header_patching_slot(currentSlot)
                    })
                    setMessage(R.string.step0_start())
                    setCancelable(false)
                    show()
                }

                patchBtn.isEnabled = false
            }

            override fun doInBackground(vararg params: Unit?) {
                val fis = when (inputSource) {
                    ImageLocation.FILE -> openSafInput()
                    ImageLocation.PARTITION -> SuProcessFileInputStream(partiPath!!)
                }

                val fos = try {
                    when (inputSource) {
                        ImageLocation.FILE -> openSafOutput()
                        ImageLocation.PARTITION -> {
                            SuProcessFileOutputStream(partiPath ?:
                            throw IllegalStateException(R.string.part_not_found()))
                        }
                    }
                } catch (e: IllegalStateException) {
                    errorDialog(e.message!!, appIssue = inputSource == ImageLocation.PARTITION)
                    return
                }

                success = try {
                    patch(
                            progress = { step ->
                                Log.i(logTag, "$step slot=$currentSlot")
                                currentStep = step

                                runOnUiThread {
                                    dialog.setMessage(step)
                                }
                            },

                            fis = fis,
                            fos = fos
                    )
                } catch (e: Exception) {
                    dialog.dismiss()

                    if (e is ImageException) {
                        Crashlytics.log("Native error: ${e.message}")
                        if (e.message == null) {
                            errorDialog(R.string.err_native_empty())
                            Crashlytics.logException(e)
                            return
                        }

                        errorDialog(e.message!!)
                    } else {
                        errorDialog(R.string.err_java(e::class.java.simpleName, e.message ?: "null"), appIssue = true)
                        Crashlytics.logException(e)
                    }

                    Crashlytics.log("Last step: $currentStep")
                    currentStep = ""

                    false
                } finally {
                    try {
                        fos.flush()
                        fos.close()
                    } catch (ex: Exception) {
                        if (dialog.isShowing) {
                            dialog.dismiss()
                        }

                        errorDialog(R.string.err_close_output(ex.message ?: "null"))
                    }
                }
            }

            override fun onPostExecute(result: Unit?) {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }

                patchBtn.isEnabled = true

                if (!success) {
                    return
                }

                when (inputSource) {
                    ImageLocation.FILE -> {
                        with (snack(R.string.file_complete)) {
                            setAction(R.string.share) {
                                val intent = Intent()
                                intent.action = Intent.ACTION_SEND
                                intent.putExtra(Intent.EXTRA_STREAM, safOutput)
                                intent.type = "application/octet-stream"

                                startActivity(Intent.createChooser(intent, resources.getText(R.string.share)))
                            }

                            show()
                        }
                    }

                    ImageLocation.PARTITION -> {
                        if (slot != null) {
                            ++slotsPatched

                            if (slotsPatched >= 2) {
                                slotsPatched = 0
                                snack(R.string.part_complete_slot).show()
                                return
                            }

                            val otherSlot = when (currentSlot) {
                                "A" -> "_b"
                                "B" -> "_a"
                                else -> return
                            }

                            asyncPatch(otherSlot)
                            return
                        } else {
                            snack(R.string.part_complete).show()
                        }
                    }
                }
            }
        }

        task.execute()
    }

    private fun contactDev() {
        openUri("mailto:" + R.string.contact_mail().replace(" (at) ", "@"))
    }

    private fun showAboutActivity() {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    private fun showHelpDialog() {
        val dialog = with (AlertDialog.Builder(this, R.style.DialogTheme)) {
            setMessage(parseHtml(resources.getString(R.string.full_info)))
            setPositiveButton(android.R.string.ok) { _, _ -> }
            create()
        }

        dialog.show()
        dialog.findViewById<TextView>(android.R.id.message).movementMethod = LinkMovementMethod.getInstance()
    }

    private fun errorDialog(message: String, appIssue: Boolean = false) {
        runOnUiThread {
            with (AlertDialog.Builder(this, R.style.DialogTheme)) {
                setTitle(R.string.err_generic)
                setMessage(message)

                if (appIssue) {
                    setPositiveButton(android.R.string.ok) { _, _ ->
                    }

                    setNegativeButton(R.string.contact) { _, _ ->
                        contactDev()
                    }
                } else {
                    setPositiveButton(android.R.string.ok) { _, _ -> }
                }

                setCancelable(false)
                show()
            }
        }
    }

    private fun openSafInput(): InputStream {
        return contentResolver.openInputStream(safInput) ?:
        throw IllegalStateException(R.string.file_error_input())
    }

    private fun openSafOutput(): OutputStream {
        return contentResolver.openOutputStream(safOutput) ?:
        throw IllegalStateException(R.string.file_error_output())
    }

    private fun partPath(slot: String?): String? {
        // the most common one
        val bdPath = when (slot) {
            null -> "/dev/block/bootdevice/by-name/recovery"
            else -> "/dev/block/bootdevice/by-name/boot$slot"
        }

        if (SuFile(bdPath).exists()) {
            return bdPath
        }

        val partNames = when (slot) {
            null -> setOf("recovery", "RECOVERY", "SOS", "recovery_ramdisk")
            else -> setOf("boot$slot", "BOOT$slot")
        }

        // time to do some hunting...
        // need API 26 for nio.Files
        findPartitionDirs().forEach { dir ->
            File(dir).listFiles().forEach {
                if (it.name in partNames) {
                    return it.absolutePath
                }
            }
        }

        return null
    }

    private fun outputFileName(inputUri: Uri?): String {
        return if (inputUri != null) {
            val inputName = inputUri.getFileName(this)

            if (inputName == null) {
                val devName = getProp("ro.product.device")
                if (devName != null) "twrp-$devName-tipatched.img" else "twrp-tipatched.img"
            } else {
                val split = inputName.split('.').toMutableList()

                if (split.size > 1) {
                    split[split.size - 2] += "-tipatched"
                    split.joinToString(".")
                } else {
                    "$inputName-tipatched"
                }
            }
        } else {
            val devName = getProp("ro.product.device")
            if (devName != null) "twrp-$devName-tipatched.img" else "twrp-tipatched.img"
        }
    }

    override fun onSharedPreferenceChanged(pref: SharedPreferences?, key: String?) {
        if (key == "partition") {
            val partEnabled = pref?.getBoolean(key, false) ?: false

            optFrag.preferenceManager.findPreference("input")?.isEnabled = !partEnabled
            optFrag.preferenceManager.findPreference("output")?.isEnabled = !partEnabled

            inputSource = when (partEnabled) {
                true -> ImageLocation.PARTITION
                false -> ImageLocation.FILE
            }
        }
    }

    companion object {
        init {
            System.loadLibrary("tipatch")
        }
    }
}
