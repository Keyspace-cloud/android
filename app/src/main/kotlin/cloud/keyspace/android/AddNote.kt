package cloud.keyspace.android

import android.annotation.SuppressLint
import android.content.*
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils.replace
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils.loadAnimation
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.dhaval2404.colorpicker.MaterialColorPickerDialog
import com.github.dhaval2404.colorpicker.listener.ColorListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.keyspace.keyspacemobile.*
import com.yahiaangelo.markdownedittext.MarkdownEditText
import com.yydcdut.markdown.MarkdownConfiguration
import com.yydcdut.markdown.MarkdownProcessor
import com.yydcdut.markdown.callback.OnTodoClickCallback
import com.yydcdut.markdown.loader.DefaultLoader
import com.yydcdut.markdown.syntax.edit.EditFactory
import com.yydcdut.markdown.syntax.text.TextFactory
import com.yydcdut.markdown.theme.Theme
import com.yydcdut.markdown.theme.ThemeDefault
import com.yydcdut.markdown.theme.ThemeDesert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.*
import kotlin.properties.Delegates


class AddNote : AppCompatActivity() {

    lateinit var utils: MiscUtilities
    lateinit var crypto: CryptoUtilities
    lateinit var io: IOUtilities
    lateinit var network: NetworkUtilities

    lateinit var dateAndTime: TextView
    var timestamp by Delegates.notNull<Long>()

    lateinit var noteViewer: com.yydcdut.markdown.MarkdownEditText
    lateinit var noteEditorScrollView: MarkdownEditText
    lateinit var noteViewerScrollView: MarkdownEditText

    lateinit var tagButton: ImageView
    var tagId: String? = null

    var favorite: Boolean = false
    lateinit var favoriteButton: ImageView

    var noteColor: String? = null
    lateinit var colorButton: ImageView

    private var frequencyAccessed = 0L
    private var previousTimestamp = 0L

    lateinit var doneButton: ImageView
    lateinit var backButton: ImageView
    lateinit var deleteButton: ImageView

    var unrenderedText = ""
    var markdownToolbar = true
    lateinit var theme: Theme

    lateinit var keyring: CryptoUtilities.Keyring
    private var itemId: String? = null
    private lateinit var vault: IOUtilities.Vault
    private lateinit var note: IOUtilities.Note

    lateinit var configData: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_note)

        configData = getSharedPreferences(applicationContext.packageName + "_configuration_data", MODE_PRIVATE)

        val allowScreenshots = configData.getBoolean("allowScreenshots", false)
        if (!allowScreenshots) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        utils = MiscUtilities (applicationContext)
        crypto = CryptoUtilities(applicationContext, this)

        utils = MiscUtilities (applicationContext)
        crypto = CryptoUtilities(applicationContext, this)

        val intentData = crypto.receiveKeyringFromSecureIntent (
            currentActivityClassNameAsString = getString(R.string.title_activity_add_note),
            intent = intent
        )

        keyring = intentData.first
        network = NetworkUtilities(applicationContext, this, keyring)
        itemId = intentData.second

        network = NetworkUtilities(applicationContext, this, keyring)

        io = IOUtilities(applicationContext, this, keyring)

        initializeUI()


        vault = io.getVault()
        if (itemId != null) {
            note = io.decryptNote(io.getNote(itemId!!, vault)!!)
            loadNote (note)

            frequencyAccessed = note.frequencyAccessed!!

            val data = IOUtilities.Note(
                id = note.id,
                organizationId = null,
                type = note.type,
                notes = note.notes,
                color = note.color,
                favorite = note.favorite,
                tagId = null,
                dateCreated = note.dateCreated,
                dateModified = note.dateModified,
                frequencyAccessed = frequencyAccessed + 1
            )
        }

    }

    @SuppressLint("UseCompatLoadingForDrawables", "ClickableViewAccessibility", "SetTextI18n")
    private fun initializeUI (): Boolean {
        theme = when (applicationContext.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> ThemeDesert()
            Configuration.UI_MODE_NIGHT_NO -> ThemeDefault()
            Configuration.UI_MODE_NIGHT_UNDEFINED -> ThemeDefault()
            else -> ThemeDefault()
        }

        noteViewer = findViewById(R.id.noteViewer)
        noteViewer.isActivated = true
        noteViewer.isPressed = true

        val markdownConfig = MarkdownConfiguration.Builder(applicationContext)
            .setTheme(theme)
            .showLinkUnderline(true)
            .setOnLinkClickCallback { _, link ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
            .setLinkFontColor(R.attr.colorControlActivated)
            .setOnTodoClickCallback(object : OnTodoClickCallback {
                override fun onTodoClicked(view: View?, line: String?, lineNumber: Int): CharSequence { return "" }
            })
            .setRxMDImageLoader(DefaultLoader(applicationContext))
            .build()

        val markdownProcessor = MarkdownProcessor(this)
        markdownProcessor.config(markdownConfig)
        markdownProcessor.factory(EditFactory.create())
        markdownProcessor.live(noteViewer)

        timestamp = Instant.now().epochSecond

        dateAndTime = findViewById(R.id.dateAndTime)
        dateAndTime.visibility = View.GONE

        // Load toolbar
        val noteToolbar = findViewById<HorizontalScrollView>(R.id.noteToolbar)

        noteToolbar.visibility = View.VISIBLE

        findViewById<ImageView>(R.id.helpButton).setOnClickListener {
            val inflater = layoutInflater
            val dialogView: View = inflater.inflate (R.layout.markdown_help, null)
            val dialogBuilder = MaterialAlertDialogBuilder(this)
            dialogBuilder
                .setView(dialogView)
                .setTitle("Markdown guide")
                .setIcon(getDrawable(R.drawable.markdown))
                .setCancelable(true)
            val markdownDialog = dialogBuilder.show()
            val markdownUnrendered = markdownDialog.findViewById<View>(R.id.guide) as TextView
            val markdownRendered = markdownDialog.findViewById<View>(R.id.guideRendered) as com.yydcdut.markdown.MarkdownEditText
            val markdownConfig = MarkdownConfiguration.Builder(applicationContext)
                .setTheme(theme)
                .showLinkUnderline(true)
                .setOnLinkClickCallback { view, link ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                }
                .setOnLinkClickCallback { _, link ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                }
                .setLinkFontColor(R.attr.colorControlActivated)
                .setOnTodoClickCallback(object : OnTodoClickCallback {
                    override fun onTodoClicked(view: View?, text: String?, lineNumber: Int): CharSequence {
                        return text.toString()
                    }
                })
                .setRxMDImageLoader(DefaultLoader(applicationContext))
                .build()
            var markdownProcessor = MarkdownProcessor(this)
            markdownProcessor.config(markdownConfig)
            markdownProcessor.factory(TextFactory.create())
            markdownProcessor.live(markdownRendered)
            markdownUnrendered.visibility = View.VISIBLE
            markdownRendered.visibility = View.GONE
            markdownUnrendered.startAnimation(loadAnimation(applicationContext, R.anim.from_top))
            val renderButton = markdownDialog.findViewById<View>(R.id.renderButton) as MaterialButton
            var rendered = false
            renderButton.setOnClickListener {
                if (!rendered) {
                    rendered = true
                    markdownUnrendered.visibility = View.GONE
                    markdownRendered.visibility = View.VISIBLE
                    renderButton.text = "Tap to view raw"
                    renderButton.icon = getDrawable(R.drawable.ic_baseline_visibility_off_24)
                } else {
                    rendered = false
                    markdownUnrendered.visibility = View.VISIBLE
                    markdownRendered.visibility = View.GONE
                    renderButton.text = "Tap to render"
                    renderButton.icon = getDrawable(R.drawable.ic_baseline_visibility_24)
                }
            }
            val backButton =  markdownDialog.findViewById<View>(R.id.backButton) as MaterialButton
            backButton.setOnClickListener { markdownDialog.dismiss() }
            dialogBuilder.create()
        }

        findViewById<ImageView>(R.id.numberListButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                var lineBreakCounter = 1
                if (selectedText.contains("\n")) {
                    val string = mutableListOf<Char>()
                    string.add(lineBreakCounter.toString().single())
                    string.add('.')
                    string.add(' ')
                    lineBreakCounter += 1
                    for (c in selectedText) {
                        string.add(c)
                        if (c == '\n') {
                            string.add(lineBreakCounter.toString().single())
                            string.add('.')
                            string.add(' ')
                            lineBreakCounter += 1
                        }
                    }
                    noteViewer.setText(String(string.toCharArray()))
                    noteViewer.setSelection(noteViewer.text.toString().length)
                } else {
                    noteViewer.setText(selectedText.replace(selectedText, "1. $selectedText"))
                    noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
                }

            } else {
                val markdown = "\n1. "
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), "1. ", 0, "1. ".length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.bulletListButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                if (selectedText.contains("\n")) {
                    noteViewer.setText("\n\n- ${selectedText.replace("\n", "\n- ")}")
                    noteViewer.setSelection(noteViewer.text.toString().length)
                } else {
                    noteViewer.setText(selectedText.replace(selectedText, "- $selectedText"))
                    noteViewer.setSelection(noteViewer.text.toString().length)
                }
            } else {
                val markdown = "\n- "
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), "- ", 0, "- ".length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.linkButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "[${selectedText}]()"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length + 2)
            } else {
                val markdown = "\n[text](url)"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.italicButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "_${selectedText}_"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "\n_text_"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.checkedButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                if (selectedText.contains("\n")) {
                    noteViewer.setText("\n\n- [x] ${selectedText.replace("\n", "\n- [x] ")}")
                    noteViewer.setSelection(noteViewer.text.toString().length)
                } else {
                    noteViewer.setText(selectedText.replace(selectedText, "- [x] $selectedText"))
                    noteViewer.setSelection(noteViewer.text.toString().length)
                }
            } else {
                val markdown = "\n- [x] "
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), "- [x] ", 0, "- [x] ".length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.uncheckedButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                if (selectedText.contains("\n")) {
                    noteViewer.setText("\n\n- [ ] ${selectedText.replace("\n", "\n- [ ] ")}")
                    noteViewer.setSelection(noteViewer.text.toString().length)
                } else {
                    noteViewer.setText(selectedText.replace(selectedText, "- [x] $selectedText"))
                    noteViewer.setSelection(noteViewer.text.toString().length)
                }
            } else {
                val markdown = "\n- [ ] "
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), "- [ ] ", 0, "- [ ] ".length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.imageButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "![${selectedText}]()"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length + 2)
            } else {
                val markdown = "\n![caption](url)"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.lineButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "$selectedText\n****"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "\n****"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.quoteButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "> $selectedText"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "\n> "
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), "> ", 0, "> ".length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.strikethroughButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "~$selectedText~"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "\n~text~"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.codeButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "\n```\n$selectedText\n```"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "\n```\ntext\n```"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), "```\ntext\n```", 0, "```\ntext\n```".length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.boldButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "**$selectedText**"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "\n**text**"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.titleButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "# $selectedText"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "\n# "
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), "# ", 0, "# ".length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        doneButton = findViewById (R.id.done)
        doneButton.setOnClickListener {
            if (noteViewer.text.isNullOrBlank() || noteViewer.text.toString().length <= 1) {
                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
                alertDialog.setTitle("Blank Note")
                alertDialog.setMessage("Note can't be blank")
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Go back") { dialog, _ -> dialog.dismiss() }
                alertDialog.show()
            } else saveNote()

        }

        findViewById<TextView>(R.id.toolbarTitle).visibility = View.VISIBLE

        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        deleteButton = findViewById (R.id.delete)
        if (itemId != null) {
            deleteButton.setOnClickListener {
                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
                alertDialog.setTitle("Delete")
                alertDialog.setMessage("Would you like to delete this note?")
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Delete") { dialog, _ ->

                    vault.note!!.remove(io.getNote(itemId!!, vault))
                    io.writeVault(vault)

                    network.writeQueueTask (itemId!!, mode = network.MODE_DELETE)
                    crypto.secureStartActivity (
                        nextActivity = Dashboard(),
                        nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
                        keyring = keyring,
                        itemId = null
                    )

                }
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Go back") { dialog, _ -> dialog.dismiss() }
                alertDialog.show()

            }
        } else {
            deleteButton.visibility = View.GONE
        }

        tagButton = findViewById (R.id.tag)
        tagButton.setOnClickListener {
            val tagPicker = AddTag (applicationContext, this@AddNote, keyring)
            tagPicker.showPicker()
        }

        favoriteButton = findViewById(R.id.favoriteButton)
        favoriteButton.setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_border_24))
        favoriteButton.setOnClickListener {
            favorite = if (!favorite) {
                favoriteButton.setImageDrawable (ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_24))
                favoriteButton.startAnimation(loadAnimation(applicationContext, R.anim.heartbeat))
                true
            } else {
                favoriteButton.setImageDrawable (ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_border_24))
                false
            }
        }

        colorButton = findViewById(R.id.colorButton)
        colorButton.setOnClickListener {
            MaterialColorPickerDialog.Builder(this@AddNote)
                .setColors(resources.getStringArray(R.array.vault_item_colors))
                .setTickColorPerCard(true)
                .setDefaultColor(noteColor.toString())
                .setColorListener(object : ColorListener {
                    override fun onColorSelected(color: Int, colorHex: String) {
                        noteColor = colorHex
                        noteViewer.setBackgroundColor(Color.parseColor(noteColor))
                        if (noteColor != null) {
                            val intColor: Int = noteColor!!.replace("#", "").toInt(16)
                            val r = intColor shr 16 and 0xFF; val g = intColor shr 8 and 0xFF; val b = intColor shr 0 and 0xFF
                            if (g >= 200 || b >= 200) {
                                noteViewer.setTextColor (Color.BLACK)
                                noteViewer.setHintTextColor(Color.BLACK)
                            } else {
                                noteViewer.setTextColor(Color.WHITE)
                                noteViewer.setHintTextColor(Color.WHITE)
                            }
                        }
                    }
                })
                .show()
        }

        return true
    }

    private fun loadNote (note: IOUtilities.Note): Boolean {
        favorite = if (note.favorite) {
            favoriteButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_star_24)); true
        } else {
            favoriteButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_star_border_24)); false
        }

        tagId = note.tagId

        dateAndTime.visibility = View.VISIBLE

        previousTimestamp = note.dateModified!!

        val time = Calendar.getInstance(Locale.ENGLISH)
        time.timeInMillis = previousTimestamp?.times(1000L)!!
        dateAndTime.text = "Last edited on " + DateFormat.format("MMM dd, yyyy ⋅  hh:mm a", time).toString()

        if (!note.notes.isNullOrEmpty()) {
            noteViewer.setText (note.notes)
        }

        if (!note.color.isNullOrEmpty()) {
                noteColor = note.color
                noteViewer.setBackgroundColor(Color.parseColor(noteColor))
                val intColor: Int = noteColor!!.replace("#", "").toInt(16)
                val r = intColor shr 16 and 0xFF; val g = intColor shr 8 and 0xFF; val b = intColor shr 0 and 0xFF
                if (g >= 200 || b >= 200) {
                    noteViewer.setTextColor (Color.BLACK)
                    noteViewer.setHintTextColor(Color.BLACK)
                } else {
                    noteViewer.setTextColor(Color.WHITE)
                    noteViewer.setHintTextColor(Color.WHITE)
                }

        }

        return true
    }

    private fun saveNote () {
        var dateCreated = Instant.now().epochSecond

        if (itemId != null) {
            dateCreated = note.dateCreated!!
            vault.note?.remove(io.getNote(itemId!!, vault))
        }

        val data = IOUtilities.Note(
            id = itemId ?: UUID.randomUUID().toString(),
            organizationId = null,
            type = io.TYPE_NOTE,
            notes = noteViewer.text.toString(),
            color = noteColor,
            favorite = favorite,
            tagId = tagId,
            dateCreated = dateCreated,
            dateModified = timestamp,
            frequencyAccessed = frequencyAccessed
        )

        val encryptedNote = io.encryptNote(data)

        vault.note?.add (encryptedNote)
        io.writeVault(vault)

        if (itemId != null) network.writeQueueTask (encryptedNote, mode = network.MODE_PUT)
        else network.writeQueueTask (encryptedNote, mode = network.MODE_POST)

        crypto.secureStartActivity (
            nextActivity = Dashboard(),
            nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
            keyring = keyring,
            itemId = null
        )

    }

    override fun onBackPressed () {
        val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
        alertDialog.setTitle("Confirm exit")
        alertDialog.setMessage("Would you like to go back to the Dashboard?")
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Exit") { dialog, _ ->
            crypto.secureStartActivity (
                nextActivity = Dashboard(),
                nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
                keyring = keyring,
                itemId = null
            )
            super.onBackPressed()
        }
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { dialog, _ -> dialog.dismiss() }
        alertDialog.show()
    }

    override fun onUserLeaveHint() {
        if (configData.getBoolean("lockApp", true)) {
            finish()
            finishAffinity()
        }
        super.onUserLeaveHint()

    }
}