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
import android.os.Process
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
import com.kdrag0n.tipatch.jni.CompressException
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
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.IndexOutOfBoundsException

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
    private var ifName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (LeakCanary.isInAnalyzerProcess(this)) {
            return
        }

        try {
            LeakCanary.install(application)
        } catch (e: UnsupportedOperationException) {}
        catch (e: Throwable) {
            throw e
        }

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

            var outName = outputFileName()
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
                REQ_SAF_INPUT -> {
                    safInput = data.data ?: return
                    ifName = data.data?.getFileName(this) ?: return
                    optFrag.preferenceManager.findPreference("input")?.summary = ifName
                }
                REQ_SAF_OUTPUT -> {
                    safOutput = data.data ?: return
                    val ofName = data.data?.getFileName(this) ?: return
                    optFrag.preferenceManager.findPreference("output")?.summary = ofName
                }
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

    private fun patch(progress: (String, PatchStep) -> Unit, fis: InputStream, fos: OutputStream): Boolean {
        progress(R.string.step1_read_unpack(), PatchStep.READ)
        val image = Image(fis)

        val cMode = image.detectCompressor()
        val cName = when (cMode) {
            Image.COMP_GZIP -> "gzip"
            Image.COMP_LZ4 -> "lz4"
            Image.COMP_LZO -> "lzo"
            Image.COMP_XZ -> "xz"
            Image.COMP_LZMA -> "lzma"
            Image.COMP_BZIP2 -> "bzip2"
            else -> "unknown"
        }

        progress(R.string.step2_decompress(cName), PatchStep.DECOMPRESS)
        image.decompressRamdisk(cMode)

        val direction = when (reversePref.isChecked) {
            true -> Image.REPL_REVERSE
            false -> Image.REPL_NORMAL
        }

        if (reversePref.isChecked) {
            progress(R.string.step3_patch_rev(), PatchStep.PATCH)
        } else {
            progress(R.string.step3_patch(), PatchStep.PATCH)
        }

        image.patchRamdisk(direction)

        progress(R.string.step4_compress(), PatchStep.COMPRESS)
        image.compressRamdisk(cMode)

        progress(R.string.step5_pack_write(), PatchStep.WRITE)
        image.write(fos)

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
            private var currentPatchStep = PatchStep.NONE

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
                            SuProcessFileOutputStream(partiPath
                                    ?: throw IllegalStateException(R.string.part_not_found()))
                        }
                    }
                } catch (e: IllegalStateException) {
                    errorDialog(e.message!!, appIssue = inputSource == ImageLocation.PARTITION)
                    return
                }

                success = try {
                    patch(
                            progress = { step, patchStep ->
                                Log.i(logTag, "$step $patchStep slot=$currentSlot")
                                currentStep = step
                                currentPatchStep = patchStep

                                runOnUiThread {
                                    dialog.setMessage(step)
                                }
                            },

                            fis = fis,
                            fos = fos
                    )
                } catch (e: Throwable) {
                    dialog.dismiss()

                    Crashlytics.log("Last step: $currentPatchStep $currentStep")
                    currentStep = ""

                    when (e) {
                        is ImageException -> {
                            Crashlytics.log("Native image error: ${e.message}")
                            if (e.message == null) {
                                errorDialog(R.string.err_native_empty())
                                Crashlytics.logException(e)
                                return
                            }

                            errorDialog(e.message!!)
                        }
                        is IOException -> when (currentPatchStep) {
                            PatchStep.READ -> if (e.message != null) {
                                errorDialog(R.string.err_native_io_read(e.message!!))
                            } else {
                                errorDialog(R.string.err_native_io_read_empty())
                            }
                            else -> if (e.message != null) {
                                errorDialog(R.string.err_native_io_write(e.message!!))
                            } else {
                                errorDialog(R.string.err_native_io_write_empty())
                            }
                        }
                        is CompressException -> {
                            if (currentPatchStep == PatchStep.COMPRESS) {
                                if (e.message != null) {
                                    errorDialog(R.string.err_native_comp(e.message!!))
                                } else {
                                    errorDialog(R.string.err_native_comp_empty())
                                }
                            } else if (e.message != null) {
                                errorDialog(R.string.err_native_decomp(e.message!!))
                            } else {
                                errorDialog(R.string.err_native_decomp_empty())
                            }

                            Crashlytics.logException(e)
                        }
                        is OutOfMemoryError -> {
                            errorDialog(R.string.err_oom(), oom = true)
                            Crashlytics.logException(e)
                        }
                        is IndexOutOfBoundsException -> {
                            errorDialog(R.string.err_native_empty())
                            Crashlytics.logException(e)
                        }
                        is Error -> {
                            if (e.message != null) {
                                errorDialog(R.string.err_native_unknown(e.message!!), appIssue = true)
                            } else {
                                errorDialog(R.string.err_native_empty(), appIssue = true)
                            }
                            Crashlytics.logException(e)
                        }
                        else -> {
                            errorDialog(R.string.err_java(e::class.java.simpleName, e.message
                                    ?: "null"), appIssue = true)
                            Crashlytics.logException(e)
                        }
                    }

                    false
                } finally {
                    try {
                        fis.close()
                    } catch (ex: Exception) {}

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

    private fun errorDialog(message: String, appIssue: Boolean = false, oom: Boolean = false) {
        runOnUiThread {
            with (AlertDialog.Builder(this, R.style.DialogTheme)) {
                setTitle(R.string.err_generic)
                setMessage(message)

                when {
                    oom -> setPositiveButton(R.string.exit) { _, _ ->
                        // we don't use Activity#finish() because it keeps the JVM running
                        Process.killProcess(Process.myPid())
                        System.exit(1) // last resort
                    }
                    appIssue -> {
                        setPositiveButton(android.R.string.ok) { _, _ ->
                        }

                        setNegativeButton(R.string.contact) { _, _ ->
                            contactDev()
                        }
                    }
                    else -> setPositiveButton(android.R.string.ok) { _, _ -> }
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

    private fun outputFileName(): String {
        return if (ifName != null) {
            val split = ifName!!.split('.').toMutableList()

            if (split.size > 1) {
                split[split.size - 2] += "-tipatched"
                split.joinToString(".")
            } else {
                "$ifName-tipatched"
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
