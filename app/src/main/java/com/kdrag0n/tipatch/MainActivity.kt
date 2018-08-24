package com.kdrag0n.tipatch

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Process
import android.preference.CheckBoxPreference
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import com.crashlytics.android.Crashlytics
import com.kdrag0n.tipatch.jni.*
import com.kdrag0n.utils.*
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuProcessFileInputStream
import com.topjohnwu.superuser.io.SuProcessFileOutputStream
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.lang.IndexOutOfBoundsException

private const val REQ_SAF_INPUT = 100
private const val REQ_SAF_OUTPUT = 101
private const val BACKUP_PREFIX = "parti_backup"

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var inputSource = ImageLocation.FILE
    private var outputDest = ImageLocation.FILE
    private lateinit var safInput: Uri
    private lateinit var safOutput: Uri
    private lateinit var opts: SharedPreferences
    private lateinit var optFrag: OptionFragment
    private var firstRun = false
    private var isRooted = false
    private var slotsPatched = 0
    private var ifName: String? = null

    // task
    private var success = false
    private lateinit var currentStep: String
    private lateinit var patchTitle: String
    private var currentPatchStep = PatchStep.BACKUP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar_main as Toolbar?)

        opts = PreferenceManager.getDefaultSharedPreferences(baseContext)
        opts.registerOnSharedPreferenceChangeListener(this)

        optFrag = OptionFragment()
        optFrag.inputEvent = {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/octet-stream" // no .img type

            try {
                startActivityForResult(intent, REQ_SAF_INPUT)
            } catch (e: ActivityNotFoundException) {
                errorDialog(R.string.err_no_file_handler())
            }
        }

        optFrag.outputEvent = {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)

            intent.type = "application/octet-stream" // no .img type

            val outName = outputFileName()
            intent.putExtra(Intent.EXTRA_TITLE, outName)

            try {
                startActivityForResult(intent, REQ_SAF_OUTPUT)
            } catch (e: ActivityNotFoundException) {
                errorDialog(R.string.err_no_file_handler())
            }
        }

        optFrag.retainInstance = true

        fragmentManager
                .beginTransaction()
                .add(R.id.opt_container, optFrag)
                .commit()


        val pDialog = ProgressDialog(this, R.style.DialogTheme)
        try {
            patchDialog.value = pDialog
        } catch (e: UninitializedPropertyAccessException) {
            patchDialog = Box(pDialog)
        }
        currentStep = R.string.step0_backup()

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
            success = savedInstanceState.getBoolean("success", success)
            currentStep = savedInstanceState.getString("currentStep", currentStep)
            currentPatchStep = PatchStep.values()[savedInstanceState.getInt("currentPatchStep", currentPatchStep.ordinal)]
            patchTitle = savedInstanceState.getString("patchTitle", R.string.header_patching())

            var pcu = savedInstanceState.getParcelable<Uri>("safInput")
            if (pcu != null) {
                safInput = pcu
            }

            pcu = savedInstanceState.getParcelable("safOutput")
            if (pcu != null) {
                safOutput = pcu
            }

            if (task?.status == AsyncTask.Status.RUNNING) {
                with (patchDialog.value) {
                    setTitle(patchTitle)
                    setMessage(currentStep)
                    setCancelable(false)
                    show()
                }
            }
        }

        patch_dial.addActionItem(SpeedDialActionItem.Builder(R.id.fab_patch, R.drawable.ic_apply)
                .setFabBackgroundColor(ContextCompat.getColor(this, R.color.btn_green))
                .setLabel(R.string.patch_btn())
                .setLabelColor(ContextCompat.getColor(this, R.color.about_ic_color))
                .setLabelBackgroundColor(ContextCompat.getColor(this, R.color.card_dark))
                .create())
        patch_dial.addActionItem(SpeedDialActionItem.Builder(R.id.fab_undo_patch, R.drawable.ic_undo)
                .setFabBackgroundColor(ContextCompat.getColor(this, R.color.btn_red))
                .setLabel(R.string.undo())
                .setLabelColor(ContextCompat.getColor(this, R.color.about_ic_color))
                .setLabelBackgroundColor(ContextCompat.getColor(this, R.color.card_dark))
                .create())
        patch_dial.addActionItem(SpeedDialActionItem.Builder(R.id.fab_restore_backups, R.drawable.ic_restore)
                .setFabBackgroundColor(ContextCompat.getColor(this, R.color.btn_blue))
                .setLabel(R.string.restore())
                .setLabelColor(ContextCompat.getColor(this, R.color.about_ic_color))
                .setLabelBackgroundColor(ContextCompat.getColor(this, R.color.card_dark))
                .create())
        patch_dial.addActionItem(SpeedDialActionItem.Builder(R.id.fab_delete_backups, R.drawable.ic_delete)
                .setFabBackgroundColor(ContextCompat.getColor(this, R.color.btn_orange))
                .setLabel(R.string.delete_backup())
                .setLabelColor(ContextCompat.getColor(this, R.color.about_ic_color))
                .setLabelBackgroundColor(ContextCompat.getColor(this, R.color.card_dark))
                .create())

        patch_dial.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected() = false

            override fun onToggleChanged(isOpen: Boolean) {
            }
        })

        patch_dial.setOnActionSelectedListener { act ->
            when (act.id) {
                R.id.fab_patch -> asyncPatch(getProp("ro.boot.slot_suffix"), Image.REPL_NORMAL)
                R.id.fab_undo_patch -> asyncPatch(getProp("ro.boot.slot_suffix"), Image.REPL_REVERSE)
                R.id.fab_restore_backups -> {
                    val dialog = ProgressDialog(this, R.style.DialogTheme)
                    with (dialog) {
                        setMessage(R.string.restore_backup_progress())
                    }

                    asyncExec {
                        try {
                            restoreBackups(dialog)
                        } finally {
                            runOnUiThread {
                                dialog.dismiss()
                            }
                        }
                    }
                }
                R.id.fab_delete_backups -> asyncExec {
                    File(noBackupFilesDir.absolutePath).listFiles()?.forEach {
                        if (it.isFile && it.name.startsWith(BACKUP_PREFIX)) {
                            it.delete()
                        }
                    }

                    opts.edit().putStringSet("backups", setOf()).apply()

                    runOnUiThread {
                        snack(R.string.delete_backup_success).show()
                    }
                }
            }

            false
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
            R.id.helpOpt -> showHelpDialog()

            R.id.aboutOpt -> showAboutActivity()
            R.id.contactOpt -> contactDev()
            R.id.donateOpt -> openUri(R.string.donate_uri())
        }

        return true
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        outState?.putBoolean("rooted", isRooted)
        outState?.putString("input", inputSource.name)
        outState?.putString("output", outputDest.name)
        outState?.putBoolean("success", success)
        outState?.putString("currentStep", currentStep)
        outState?.putInt("currentPatchStep", currentPatchStep.ordinal)

        if (task?.status == AsyncTask.Status.RUNNING) {
            outState?.putString("patchTitle", patchTitle)
        }

        outState?.putParcelable("safInput", if (::safInput.isInitialized) safInput else null)
        outState?.putParcelable("safOutput", if (::safOutput.isInitialized) safOutput else null)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (patchDialog.value.isShowing) {
            patchDialog.value.dismiss()
        }
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

                    findPreference("backup")?.isEnabled = true

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

                findPreference("backup")?.isEnabled = false

                inputSource = ImageLocation.FILE
                outputDest = ImageLocation.FILE

                opts.edit().putBoolean("partition", false).apply()
            }
        }
    }

    private fun patch(progress: (String, PatchStep) -> Unit, fis: InputStream, fos: OutputStream, direction: Byte): Boolean {
        progress(R.string.step1_read_unpack(), PatchStep.READ)
        val image = Image(fis)

        val cMode = image.detectCompressor()
        val cName = Image.compressorName(cMode)

        progress(R.string.step2_decompress(cName), PatchStep.DECOMPRESS)
        image.decompressRamdisk(cMode)

        if (direction == Image.REPL_REVERSE) {
            progress(R.string.step3_patch_rev(), PatchStep.PATCH)
        } else {
            progress(R.string.step3_patch(), PatchStep.PATCH)
        }

        image.patchRamdisk(direction)

        progress(when (cMode) {
            Image.COMP_LZMA -> R.string.step4_compress_lzma()
            else -> R.string.step4_compress(cName)
        }, PatchStep.COMPRESS)
        image.compressRamdisk(cMode)

        progress(R.string.step5_pack_write(), PatchStep.WRITE)
        image.write(fos)

        return true
    }

    private fun snack(text: String): Snackbar {
        return Snackbar.make(rootCoordinator, text, Snackbar.LENGTH_LONG)
    }

    private fun snack(textRes: Int): Snackbar {
        return snack(textRes())
    }

    private fun asyncPatch(slot: String?, direction: Byte) {
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
            val pp = try {
                partPath(slot)
            } catch (e: IllegalStateException) {
                errorDialog(if (e.message != null) R.string.err_part_root(e.message!!) else R.string.err_part_empty())
                return
            }

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

        task =
        @SuppressLint("StaticFieldLeak")
        object : AsyncTask<Unit, Unit, Unit>() {
            override fun onPreExecute() {
                patchTitle = when (inputSource) {
                    ImageLocation.FILE -> R.string.header_patching()
                    ImageLocation.PARTITION -> when (currentSlot) {
                        null -> R.string.header_patching_part()
                        "unknown" -> R.string.header_patching_unknown_slot()
                        else -> R.string.header_patching_slot(currentSlot)
                    }
                }

                with (patchDialog.value) {
                    setTitle(patchTitle)
                    setMessage(currentStep)
                    setCancelable(false)
                    show()
                }
            }

            override fun doInBackground(vararg params: Unit?) {
                if (inputSource == ImageLocation.PARTITION && opts.getBoolean("backup", true)) {
                    val res = doBackup(slot ?: "", partiPath!!)
                    if (!res.isSuccess) {
                        val errStr = if (res.err.isNotEmpty())
                            res.err.joinToString()
                        else
                            "Unknown error"

                        errorDialog(R.string.err_backup(errStr), appIssue = true)
                        Crashlytics.log("Slot = $slot")
                        Crashlytics.logException(RuntimeException("Partition backup failed: $errStr"))
                        return
                    }
                }

                val fis = try {
                    when (inputSource) {
                        ImageLocation.FILE -> openSafInput()
                        ImageLocation.PARTITION -> SuProcessFileInputStream(partiPath!!)
                    }
                } catch (e: Exception) {
                    when (e) {
                        is FileNotFoundException, is EOFException -> {
                            if (inputSource == ImageLocation.PARTITION) {
                                errorDialog(R.string.err_open_part(), appIssue = true)
                            } else {
                                errorDialog(R.string.err_open_file(R.string.err_open_file_inp()))
                            }
                        }
                        else -> throw e
                    }

                    return
                }

                val fos = try {
                    when (inputSource) {
                        ImageLocation.FILE -> openSafOutput()
                        ImageLocation.PARTITION -> SuProcessFileOutputStream(partiPath!!)
                    }
                } catch (e: FileNotFoundException) {
                    if (inputSource == ImageLocation.PARTITION) {
                        errorDialog(R.string.err_open_part_out(), appIssue = true)
                    } else {
                        errorDialog(R.string.err_open_file(R.string.err_open_file_out()))
                    }

                    return
                } catch (e: Exception) {
                    throw e
                }

                success = try {
                    patch(
                            progress = { step, patchStep ->
                                Log.i(logTag, "$step $patchStep slot=$currentSlot")
                                currentStep = step
                                currentPatchStep = patchStep

                                runOnUiThread {
                                    patchDialog.value.setMessage(step)
                                }
                            },

                            fis = fis,
                            fos = fos,
                            direction = direction
                    )
                } catch (e: Throwable) {
                    patchDialog.value.dismiss()

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
                            if (inputSource == ImageLocation.PARTITION) {
                                Crashlytics.logException(e) // should never happen if it boots
                            }
                        }
                        is IOException -> {
                            if (currentPatchStep == PatchStep.READ) {
                                if (e.message != null) {
                                    errorDialog(R.string.err_native_io_read(e.message!!))
                                } else {
                                    errorDialog(R.string.err_native_io_read_empty())
                                }
                            } else if (e.message != null) {
                                errorDialog(R.string.err_native_io_write(e.message!!))
                            } else {
                                errorDialog(R.string.err_native_io_write_empty())
                            }

                            if (inputSource == ImageLocation.PARTITION) {
                                Crashlytics.logException(e) // should never happen
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
                        is RamdiskMagicException -> {
                            val cMode = e.message!!.toByte()
                            val cName = Image.compressorName(cMode)

                            when (cMode) {
                                Image.COMP_UNKNOWN -> errorDialog(R.string.err_native_comp_magic())
                                else -> errorDialog(R.string.err_native_comp_method(cName), request = cName)
                            }
                        }
                        is OutOfMemoryError -> {
                            errorDialog(R.string.err_oom(), oom = true)
                            Crashlytics.logException(e)
                        }
                        is IndexOutOfBoundsException -> {
                            if (e.message != null) {
                                errorDialog(R.string.err_native_unknown(e.message!!))
                            } else {
                                errorDialog(R.string.err_native_empty())
                            }

                            Crashlytics.logException(e)
                        }
                        is NativeException -> {
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
                        if (patchDialog.value.isShowing) {
                            patchDialog.value.dismiss()
                        }

                        errorDialog(R.string.err_close_output(ex.message ?: "null"))
                    }
                }
            }

            override fun onPostExecute(result: Unit?) {
                if (patchDialog.value.isShowing) {
                    patchDialog.value.dismiss()
                }

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

                            asyncPatch(otherSlot, direction)
                            return
                        } else {
                            snack(R.string.part_complete).show()
                        }
                    }
                }
            }
        }

        task!!.execute()
    }

    private fun doBackup(slot: String, path: String): Shell.Result {
        val backupSet = opts.getStringSet("backups", setOf())?.toHashSet() ?: hashSetOf()

        val file = File("${noBackupFilesDir.absolutePath}/$BACKUP_PREFIX$slot.img.gz")
        file.outputStream().close() // create

        val res = Shell.su("gzip -1 -c \"$path\" > \"${file.absolutePath}\"").exec()
        if (!res.isSuccess) {
            return res
        }

        backupSet += slot
        opts.edit().putStringSet("backups", backupSet).apply()
        return res
    }

    private fun restoreBackups(dialog: ProgressDialog) {
        val backups = opts.getStringSet("backups", setOf())?.toHashSet() ?: hashSetOf()
        if (backups.isEmpty()) {
            runOnUiThread {
                snack(R.string.no_backups).show()
            }
            return
        }

        runOnUiThread {
            dialog.show()
        }

        for (slot in backups) {
            val file = File("${noBackupFilesDir.absolutePath}/$BACKUP_PREFIX$slot.img.gz")
            if (!file.exists()) {
                val copy = opts.getStringSet("backups", null)?.toHashSet()!!
                copy -= slot
                opts.edit().putStringSet("backups", copy).apply()
                continue
            }

            val partiPath = try {
                partPath(if (slot == "") null else slot)
            } catch (e: IllegalStateException) {
                errorDialog(if (e.message != null) R.string.err_part_root(e.message!!) else R.string.err_part_empty())
                return
            }

            if (partiPath == null) {
                errorDialog(R.string.part_not_found())
                return
            }

            if (!Shell.su("gzip -d -c \"${file.absolutePath}\" > \"$partiPath\"").exec().isSuccess) {
                runOnUiThread {
                    snack(R.string.restore_backup_fail).show()
                }
                return
            }
        }

        runOnUiThread {
            snack(R.string.restore_backup_success).show()
        }
    }

    private fun contactDev(extra: String = "") {
        val addr = R.string.contact_mail().replace(" (at) ", "@")

        try {
            openUri("mailto:$addr$extra")
        } catch (e: ActivityNotFoundException) {
            errorDialog(R.string.err_mailto_handler(addr))
        }
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

    private fun errorDialog(message: String, appIssue: Boolean = false, oom: Boolean = false, request: String = "") {
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
                        setPositiveButton(android.R.string.ok) { _, _ -> }

                        setNegativeButton(R.string.contact) { _, _ ->
                            contactDev()
                        }
                    }
                    request != "" -> {
                        setPositiveButton(android.R.string.ok) { _, _ -> }

                        setNegativeButton(R.string.request_support) { _, _ ->
                            contactDev("?subject=$request compression&body=I would like to request support for the $request compression method for my device '${getProp("ro.product.device")}'. Thanks in advance.".replace(" ", "%20"))
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
        return contentResolver.openInputStream(safInput) ?: throw FileNotFoundException()
    }

    private fun openSafOutput(): OutputStream {
        return contentResolver.openOutputStream(safOutput) ?: throw FileNotFoundException()
    }

    private fun partPath(slot: String?): String? {
        // the most common one
        var bdPath = when (slot) {
            null -> "/dev/block/bootdevice/by-name/recovery"
            else -> "/dev/block/bootdevice/by-name/boot$slot"
        }

        if (SuFile(bdPath).exists()) {
            return bdPath
        }

        // kirin
        bdPath = "/dev/block/bootdevice/by-name/recovery_ramdisk"
        if (SuFile(bdPath).exists())
            return bdPath

        val partNames = when (slot) {
            null -> setOf("recovery", "RECOVERY", "SOS", "recovery_ramdisk")
            else -> setOf("boot$slot", "BOOT$slot")
        }

        // time to do some hunting...
        // need API 26 for nio.Files
        findPartitionDirs().forEach { dir ->
            // use root to bypass strict SELinux policies
            val res = Shell.su("ls -1 \"$dir/\"").exec()
            if (!res.isSuccess) {
                return@forEach
            }

            for (part in res.out) {
                if (part in partNames) {
                    return "$dir/$part"
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

            optFrag.preferenceManager.findPreference("backup")?.isEnabled = partEnabled

            inputSource = when (partEnabled) {
                true -> ImageLocation.PARTITION
                false -> ImageLocation.FILE
            }
        }
    }

    companion object {
        private var task: AsyncTask<Unit, Unit, Unit>? = null
        private lateinit var patchDialog: Box<ProgressDialog>

        init {
            System.loadLibrary("tipatch")
        }
    }
}
