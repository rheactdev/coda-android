package com.codabookmarker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors

private object DarkPalette {
    val background = Color.rgb(17, 19, 24)
    val surface = Color.rgb(26, 29, 36)
    val input = Color.rgb(34, 38, 48)
    val border = Color.rgb(61, 67, 82)
    val primary = Color.rgb(139, 156, 255)
    val primaryStrong = Color.rgb(101, 116, 216)
    val text = Color.rgb(242, 243, 247)
    val muted = Color.rgb(174, 181, 196)
    val successText = Color.rgb(143, 224, 172)
    val successSurface = Color.rgb(25, 60, 42)
    val errorText = Color.rgb(255, 170, 170)
    val errorSurface = Color.rgb(72, 32, 38)
}

class MainActivity : Activity() {
    private lateinit var store: AppStore
    private lateinit var urlInput: EditText
    private lateinit var formSpinner: Spinner
    private lateinit var fieldsContainer: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var saveButton: Button
    private var visibleForms: List<SavedForm> = emptyList()
    private val fieldInputs = linkedMapOf<CodaColumn, FieldEditor>()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AppStore(this)
        setContentView(buildContent())
        handleIntent(intent)
        refreshForms()
        if (!settingsComplete(store.loadSettings())) {
            mainHandler.post { showSettingsDialog() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(DarkPalette.background)
        }

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "Save to Coda"
                textSize = 24f
                setTextColor(DarkPalette.text)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(actionButton("Forms") { showFormsDialog() })
            addView(actionButton("Settings") { showSettingsDialog() })
        })

        root.addView(label("Shared URL"), topMargin(22))
        urlInput = EditText(this).apply {
            hint = "Share a webpage to this app, or paste its URL"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            minLines = 2
            maxLines = 4
            applyDarkInputStyle()
        }
        root.addView(urlInput, matchWidth())

        root.addView(label("Saved form"), topMargin(16))
        formSpinner = Spinner(this)
        formSpinner.setBackgroundColor(DarkPalette.input)
        root.addView(formSpinner, matchWidth())

        fieldsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(fieldsContainer, matchWidth())

        saveButton = Button(this).apply {
            text = "Save bookmark"
            setTextColor(Color.rgb(16, 18, 24))
            background = roundedBackground(DarkPalette.primary, DarkPalette.primary, 0)
            setOnClickListener { saveBookmark() }
        }
        root.addView(saveButton, topMargin(22))

        statusView = TextView(this).apply {
            visibility = View.GONE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            textSize = 13f
        }
        root.addView(statusView, topMargin(12))

        return ScrollView(this).apply { addView(root) }
    }

    private fun handleIntent(incoming: Intent) {
        if (incoming.action != Intent.ACTION_SEND || incoming.type != "text/plain") return
        val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val sharedUrl = extractUrl(sharedText)
        urlInput.setText(sharedUrl.ifBlank { sharedText.trim() })
    }

    private fun refreshForms(preferredId: String = store.selectedFormId()) {
        val token = store.loadSettings().codaToken
        val fingerprint = tokenFingerprint(token)
        visibleForms = store.loadForms()
            .filter { it.tokenFingerprint == fingerprint }
            .sortedBy { it.name.lowercase() }
        val labels = if (visibleForms.isEmpty()) listOf("No saved forms") else visibleForms.map { it.name }
        formSpinner.adapter = spinnerAdapter(labels)
        val selectedIndex = visibleForms.indexOfFirst { it.id == preferredId }.takeIf { it >= 0 } ?: 0
        if (visibleForms.isNotEmpty()) formSpinner.setSelection(selectedIndex)
        formSpinner.onItemSelectedListener = SimpleItemSelectedListener {
            if (visibleForms.isNotEmpty()) {
                val form = visibleForms[formSpinner.selectedItemPosition]
                store.selectForm(form.id)
                renderFields(form)
            } else {
                fieldsContainer.removeAllViews()
            }
        }
        saveButton.isEnabled = visibleForms.isNotEmpty()
    }

    private fun renderFields(form: SavedForm) {
        fieldsContainer.removeAllViews()
        fieldInputs.clear()
        val savedValues = store.loadValues(form.id)
        form.columns.forEach { column ->
            fieldsContainer.addView(label(column.name), topMargin(14))
            val stored = savedValues[column.id]
            val editor = if (column.multiple) {
                val tokenInput = TokenInputView(this, column.options).apply {
                    setValues((stored as? List<*>)?.map(Any?::toString).orEmpty())
                }
                TokenEditor(tokenInput)
            } else {
                val input = EditText(this).apply {
                    hint = when {
                    column.type.contains("date") -> "YYYY-MM-DD"
                    column.type.contains("url") || column.type.contains("link") -> "https://example.com"
                    else -> ""
                    }
                    inputType = when {
                        column.type.contains("url") || column.type.contains("link") ->
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                        column.type.contains("number") || column.type.contains("currency") ->
                            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        else -> InputType.TYPE_CLASS_TEXT
                    }
                    setText(stored?.toString().orEmpty())
                    if (column.options.isNotEmpty()) {
                        hint = column.options.take(4).joinToString(", ")
                    }
                    applyDarkInputStyle()
                }
                ScalarEditor(input)
            }
            fieldsContainer.addView(editor.view, matchWidth())
            fieldInputs[column] = editor
        }
    }

    private fun saveBookmark() {
        val settings = store.loadSettings()
        val form = visibleForms.getOrNull(formSpinner.selectedItemPosition)
        val sharedUrl = urlInput.text.toString().trim()
        val error = when {
            !settingsComplete(settings) -> "Complete Settings before saving."
            form == null -> "Create or select a saved form."
            !validHttpUrl(sharedUrl) -> "Enter a valid http or https URL."
            else -> null
        }
        if (error != null) {
            showStatus(error, true)
            return
        }

        val selectedForm = requireNotNull(form)
        val valuesById = fieldInputs.entries.associate { (column, editor) ->
            column.id to editor.value()
        }
        val valuesByLabel = fieldInputs.entries.associate { (column, editor) ->
            column.name to editor.value()
        }
        store.saveValues(selectedForm.id, valuesById)
        setBusy(true, "Sending bookmark to the backend...")
        runTask(
            task = { CodaApi.saveBookmark(settings, sharedUrl, selectedForm, valuesByLabel) },
            success = { result ->
                setBusy(false)
                val run = result.workflowRunId?.let { " Workflow run: $it" }.orEmpty()
                showStatus("Bookmark accepted.$run", false)
                if (intent.action == Intent.ACTION_SEND) {
                    mainHandler.postDelayed({ finish() }, 700)
                }
            },
            failure = {
                setBusy(false)
                showStatus(safeMessage(it), true)
            },
        )
    }

    private fun showSettingsDialog() {
        val settings = store.loadSettings()
        val content = verticalPanel()
        val backendInput = dialogInput("Backend URL", settings.backendBaseUrl, false)
        val apiKeyInput = dialogInput("Bookmark API key", settings.bookmarkApiKey, true)
        val tokenInput = dialogInput("Coda API token", settings.codaToken, true)
        content.addView(backendInput)
        content.addView(apiKeyInput, topMargin(10))
        content.addView(tokenInput, topMargin(10))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(wrapDialog(content))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updated = Settings(
                    backendBaseUrl = backendInput.text.toString().trim(),
                    bookmarkApiKey = apiKeyInput.text.toString().trim(),
                    codaToken = tokenInput.text.toString().trim(),
                )
                if (!validHttpUrl(updated.backendBaseUrl)) {
                    backendInput.error = "Enter a valid http or https URL"
                    return@setOnClickListener
                }
                if (updated.bookmarkApiKey.isBlank()) {
                    apiKeyInput.error = "Required"
                    return@setOnClickListener
                }
                if (updated.codaToken.isBlank()) {
                    tokenInput.error = "Required"
                    return@setOnClickListener
                }
                store.saveSettings(updated)
                refreshForms()
                dialog.dismiss()
                showStatus("Settings saved.", false)
            }
        }
        dialog.show()
    }

    private fun showFormsDialog() {
        val settings = store.loadSettings()
        if (settings.codaToken.isBlank()) {
            showStatus("Add a Coda API token in Settings first.", true)
            showSettingsDialog()
            return
        }

        val content = verticalPanel()
        val formName = dialogInput("Form name", "", false)
        val docSpinner = Spinner(this)
        val tableSpinner = Spinner(this)
        val refreshButton = Button(this).apply { text = "Refresh Coda docs" }
        val loadColumnsButton = Button(this).apply {
            text = "Load table fields"
            isEnabled = false
        }
        val columnsPanel = verticalPanel()
        val progress = TextView(this).apply {
            textSize = 13f
            setTextColor(DarkPalette.muted)
        }

        content.addView(formName)
        content.addView(label("Coda doc"), topMargin(12))
        content.addView(docSpinner)
        content.addView(label("Coda table"), topMargin(10))
        content.addView(tableSpinner)
        content.addView(refreshButton, topMargin(10))
        content.addView(loadColumnsButton, topMargin(8))
        content.addView(progress, topMargin(8))
        content.addView(columnsPanel)

        var docs: List<CodaDoc> = emptyList()
        var tables: List<CodaTable> = emptyList()
        var columns: List<CodaColumn> = emptyList()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Create saved form")
            .setView(wrapDialog(content))
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete selected", null)
            .setPositiveButton("Save form", null)
            .create()

        fun setDialogBusy(busy: Boolean, message: String = "") {
            refreshButton.isEnabled = !busy
            loadColumnsButton.isEnabled = !busy && tables.isNotEmpty()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy
            progress.text = message
        }

        fun loadTables(doc: CodaDoc) {
            setDialogBusy(true, "Loading tables...")
            runTask(
                task = { CodaApi.listTables(settings.codaToken, doc.id) },
                success = { result ->
                    tables = result
                    tableSpinner.adapter = spinnerAdapter(
                        if (tables.isEmpty()) listOf("No tables found") else tables.map { it.name },
                    )
                    setDialogBusy(false, "Loaded ${tables.size} tables.")
                },
                failure = {
                    setDialogBusy(false, safeMessage(it))
                },
            )
        }

        refreshButton.setOnClickListener {
            setDialogBusy(true, "Loading Coda docs...")
            runTask(
                task = { CodaApi.listDocs(settings.codaToken) },
                success = { result ->
                    docs = result
                    docSpinner.adapter = spinnerAdapter(
                        if (docs.isEmpty()) listOf("No docs found") else docs.map { it.name },
                    )
                    docSpinner.onItemSelectedListener = SimpleItemSelectedListener {
                        docs.getOrNull(docSpinner.selectedItemPosition)?.let(::loadTables)
                    }
                    if (docs.isEmpty()) setDialogBusy(false, "No docs are available for this token.")
                },
                failure = { setDialogBusy(false, safeMessage(it)) },
            )
        }

        loadColumnsButton.setOnClickListener {
            val doc = docs.getOrNull(docSpinner.selectedItemPosition) ?: return@setOnClickListener
            val table = tables.getOrNull(tableSpinner.selectedItemPosition) ?: return@setOnClickListener
            setDialogBusy(true, "Loading table fields...")
            runTask(
                task = { CodaApi.listColumns(settings.codaToken, doc.id, table.id) },
                success = { result ->
                    columns = result
                    columnsPanel.removeAllViews()
                    columns.forEach { column ->
                        columnsPanel.addView(CheckBox(this).apply {
                            text = column.name
                            tag = column.id
                            setTextColor(DarkPalette.text)
                            buttonTintList = android.content.res.ColorStateList.valueOf(DarkPalette.primary)
                        })
                    }
                    setDialogBusy(false, "Choose fields shown when sharing.")
                },
                failure = { setDialogBusy(false, safeMessage(it)) },
            )
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val doc = docs.getOrNull(docSpinner.selectedItemPosition)
                val table = tables.getOrNull(tableSpinner.selectedItemPosition)
                val name = formName.text.toString().trim()
                if (name.isBlank()) {
                    formName.error = "Name this form"
                    return@setOnClickListener
                }
                if (doc == null || table == null) {
                    progress.text = "Refresh and select a Coda doc and table."
                    return@setOnClickListener
                }
                val selectedIds = (0 until columnsPanel.childCount)
                    .map { columnsPanel.getChildAt(it) }
                    .filterIsInstance<CheckBox>()
                    .filter(CheckBox::isChecked)
                    .map { it.tag.toString() }
                    .toSet()
                val form = SavedForm(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    docId = doc.id,
                    docName = doc.name,
                    tableId = table.id,
                    tableName = table.name,
                    tokenFingerprint = tokenFingerprint(settings.codaToken),
                    columns = columns.filter { it.id in selectedIds },
                )
                val existing = store.loadForms().filterNot {
                    it.tokenFingerprint == form.tokenFingerprint &&
                        it.name.equals(form.name, ignoreCase = true)
                }
                store.saveForms(existing + form)
                store.selectForm(form.id)
                refreshForms(form.id)
                dialog.dismiss()
                showStatus("Saved form: ${form.name}.", false)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val selected = visibleForms.getOrNull(formSpinner.selectedItemPosition)
                if (selected == null) {
                    progress.text = "No saved form is selected."
                    return@setOnClickListener
                }
                store.saveForms(store.loadForms().filterNot { it.id == selected.id })
                refreshForms()
                dialog.dismiss()
                showStatus("Deleted form: ${selected.name}.", false)
            }
        }
        dialog.show()
    }

    private fun <T> runTask(task: () -> T, success: (T) -> Unit, failure: (Throwable) -> Unit) {
        executor.execute {
            runCatching(task).fold(
                onSuccess = { mainHandler.post { success(it) } },
                onFailure = { mainHandler.post { failure(it) } },
            )
        }
    }

    private fun setBusy(busy: Boolean, message: String = "") {
        saveButton.isEnabled = !busy && visibleForms.isNotEmpty()
        formSpinner.isEnabled = !busy
        urlInput.isEnabled = !busy
        fieldInputs.values.forEach { it.setEditorEnabled(!busy) }
        if (message.isNotBlank()) showStatus(message, false)
    }

    private fun showStatus(message: String, error: Boolean) {
        statusView.visibility = View.VISIBLE
        statusView.text = message
        statusView.setTextColor(if (error) DarkPalette.errorText else DarkPalette.successText)
        statusView.background = roundedBackground(
            if (error) DarkPalette.errorSurface else DarkPalette.successSurface,
            if (error) DarkPalette.errorText else DarkPalette.successText,
            1,
        )
    }

    private fun settingsComplete(settings: Settings) =
        validHttpUrl(settings.backendBaseUrl) &&
            settings.bookmarkApiKey.isNotBlank() &&
            settings.codaToken.isNotBlank()

    private fun validHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun extractUrl(text: String): String {
        val match = Regex("""https?://[^\s<>"']+""").find(text)?.value.orEmpty()
        return match.trimEnd('.', ',', ')', ']', '}')
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.takeIf(String::isNotBlank) ?: "Something went wrong."

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(DarkPalette.muted)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun actionButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(DarkPalette.primary)
        background = roundedBackground(DarkPalette.surface, DarkPalette.border, 1)
        setOnClickListener { action() }
    }

    private fun dialogInput(hint: String, value: String, secret: Boolean) = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = if (secret) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        setSingleLine(true)
        applyDarkInputStyle()
    }

    private fun verticalPanel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(DarkPalette.surface)
    }

    private fun wrapDialog(content: View) = ScrollView(this).apply {
        setPadding(dp(22), 0, dp(22), 0)
        addView(content)
    }

    private fun spinnerAdapter(items: List<String>) =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    setBackgroundColor(DarkPalette.input)
                    (this as? TextView)?.setTextColor(DarkPalette.text)
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getDropDownView(position, convertView, parent).apply {
                    setBackgroundColor(DarkPalette.surface)
                    (this as? TextView)?.setTextColor(DarkPalette.text)
                }
        }

    private fun EditText.applyDarkInputStyle() {
        setTextColor(DarkPalette.text)
        setHintTextColor(DarkPalette.muted)
        background = roundedBackground(DarkPalette.input, DarkPalette.border, 1)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun roundedBackground(fill: Int, stroke: Int, strokeWidth: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(9).toFloat()
            setColor(fill)
            if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
        }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun topMargin(value: Int) = matchWidth().apply { topMargin = dp(value) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private sealed interface FieldEditor {
    val view: View
    fun value(): Any
    fun setEditorEnabled(enabled: Boolean)
}

private class ScalarEditor(private val input: EditText) : FieldEditor {
    override val view: View = input
    override fun value(): Any = input.text.toString().trim()
    override fun setEditorEnabled(enabled: Boolean) {
        input.isEnabled = enabled
    }
}

private class TokenEditor(private val input: TokenInputView) : FieldEditor {
    override val view: View = input
    override fun value(): Any = input.values()
    override fun setEditorEnabled(enabled: Boolean) {
        input.isEnabled = enabled
    }
}

private class SimpleItemSelectedListener(
    private val onSelected: () -> Unit,
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long,
    ) = onSelected()

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
