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
import com.kdrag0n.jni.tipatch.Tipatch
import com.kdrag0n.utils.*
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuProcessFileInputStream
import com.topjohnwu.superuser.io.SuProcessFileOutputStream
import go.Seq
import kotlinx.android.synthetic.main.activity_main.*
import org.apache.commons.io.IOUtils
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream

private const val REQ_SAF_INPUT = 100
private const val REQ_SAF_OUTPUT = 101

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var inputSource = ImageLocation.FILE
    private var outputDest = ImageLocation.FILE
    private lateinit var safInput: Uri
    private lateinit var safOutput: Uri
    private lateinit var opts: SharedPreferences
    private val reversePref: CheckBoxPreference
        get() = optFrag.preferenceManager.findPreference("reverse") as CheckBoxPreference
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
                    .add(R.id.opt_container, optFrag)
                    .commit()

            asyncExec {
                if (Shell.rootAccess()) {
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
            showHelpDialog()

            opts.edit().putBoolean("first_run", false).apply()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
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
                REQ_SAF_INPUT -> safInput = data.data
                REQ_SAF_OUTPUT -> safOutput = data.data
            }
        }
    }

    private operator fun Int.invoke(): String {
        return resources.getString(this)
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
                      writer: (ByteArray) -> Long): Boolean {
        progress("Reading image")
        val data = reader()

        if (data == null) {
            when (inputSource) {
                ImageLocation.FILE -> errorDialog("Please select an image file to patch.")
                ImageLocation.PARTITION ->
                    errorDialog(R.string.part_not_found() + R.string.part_opt_report(), partition = true)
            }

            return false
        }

        progress("Unpacking image")
        val image = Tipatch.unpackImageBytes(data)

        val cMode = image.detectCompressor()
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
        image.decompressRamdisk(cMode)

        val direction = when (reversePref.isChecked) {
            true -> Tipatch.ReplReverse
            false -> Tipatch.ReplNormal
        }

        if (reversePref.isChecked) {
            progress("Reversing ramdisk patches")
        } else {
            progress("Patching ramdisk")
        }

        image.patchRamdisk(direction)

        progress("Compressing ramdisk")
        image.compressRamdisk(cMode)

        progress("Repacking & writing image")
        val wrapped = Tipatch.wrapWriter(writer)
        image.writeHeader(wrapped)
        image.writeData(wrapped)

        return true
    }

    private fun snack(text: String): Snackbar {
        return Snackbar.make(rootCoordinator, text, Snackbar.LENGTH_SHORT)
    }

    private fun asyncPatch(slot: String?) {
        if (inputSource == ImageLocation.FILE && !::safOutput.isInitialized) {
            errorDialog("Please select an output file.")
            return
        }

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
            private var success = false
            private var currentStep = ""

            override fun onPreExecute() {
                val message = "Patching image"

                with (dialog) {
                    setTitle(when (currentSlot) {
                        null -> message
                        "unknown" -> "$message (unknown slot)"
                        else -> "$message (slot $currentSlot)"
                    })
                    setMessage("Starting patcher")
                    setCancelable(false)
                    show()
                }

                patchBtn.isEnabled = false
            }

            override fun doInBackground(vararg params: Unit?) {
                val fos = try {
                    when (inputSource) {
                        ImageLocation.FILE -> openSafOutput()
                        ImageLocation.PARTITION -> {
                            SuProcessFileOutputStream(partiPath ?:
                            throw IllegalStateException(R.string.part_not_found()))
                        }
                    }
                } catch (e: IllegalStateException) {
                    errorDialog(e.message!!, appIssue = true)
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

                            reader = {
                                when (inputSource) {
                                    ImageLocation.FILE -> getSafData()
                                    ImageLocation.PARTITION -> {
                                        SuProcessFileInputStream(partiPath ?: return@patch null).use {
                                            IOUtils.toByteArray(it)
                                        }
                                    }
                                }
                            },

                            writer = {
                                try {
                                    fos.write(it)
                                    it.size.toLong()
                                } catch (e: Exception) {
                                    errorDialog("An error occurred writing the image: ${e.message}.")

                                    -1
                                }
                            }
                    )
                } catch (e: Exception) {
                    dialog.dismiss()

                    if (e is Seq.Proxy) {
                        Crashlytics.log("Native error: ${e.message}")
                        if (e.message == null) {
                            errorDialog("An unknown error occurred processing the input image.")
                            Crashlytics.logException(e)
                            return
                        }

                        val sep = e.message!!.indexOf(';')
                        if (sep == -1) { // our *intended* errors all have 2 parts
                            errorDialog("An error occurred: ${e.message}", appIssue = true)
                            return
                        }

                        with (e.message!!) {
                            val action = slice(0 until sep)
                            val message = slice(sep + 2 until length)
                            val msgFirst = this[sep + 1].toUpperCase()

                            errorDialog("An error occurred $action. $msgFirst$message", appIssue = false)
                        }
                    } else {
                        errorDialog("An internal error of type ${e::class.java.simpleName} occurred: ${e.message}.", appIssue = true)
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

                        errorDialog("An error occurred cleaning up the written image: ${ex.message}.")
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
                        with (snack("Image patched!")) {
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
                        if (parti != null && slot != null) {
                            ++slotsPatched

                            if (slotsPatched >= 2) {
                                slotsPatched = 0
                                snack("Recovery in both slots patched!").show()
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
                            snack("Recovery patched!").show()
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
        val dialog = with (AlertDialog.Builder(this)) {
            setMessage(parseHtml(resources.getString(R.string.full_info)))
            setPositiveButton(android.R.string.ok) { _, _ -> }
            create()
        }

        dialog.show()
        dialog.findViewById<TextView>(android.R.id.message).movementMethod = LinkMovementMethod.getInstance()
    }

    private fun errorDialog(message: String, appIssue: Boolean = false, partition: Boolean = false) {
        runOnUiThread {
            with (AlertDialog.Builder(this)) {
                setTitle("Oops...")
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

                if (partition) {
                    setNeutralButton(R.string.report) { _, _ ->
                        Toast.makeText(this@MainActivity, "TODO: device reports", Toast.LENGTH_SHORT).show()
                    }
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

    private fun openSafOutput(): OutputStream {
        return if (::safOutput.isInitialized) {
            contentResolver.openOutputStream(safOutput)
        } else {
            throw IllegalStateException("Please select a valid output file.")
        }
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
        lateinit var optFrag: OptionFragment
    }
}
