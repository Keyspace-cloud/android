package cloud.keyspace.android

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.annotation.SuppressLint
import android.app.ProgressDialog.show
import android.content.*
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.keyspace.keyspacemobile.NetworkUtilities
import com.nulabinc.zxcvbn.Zxcvbn
import com.yydcdut.markdown.MarkdownConfiguration
import com.yydcdut.markdown.MarkdownProcessor
import com.yydcdut.markdown.callback.OnTodoClickCallback
import com.yydcdut.markdown.syntax.text.TextFactory
import com.yydcdut.markdown.theme.Theme
import com.yydcdut.markdown.theme.ThemeDefault
import com.yydcdut.markdown.theme.ThemeDesert
import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import java.util.*
import kotlin.concurrent.thread

class DeletedItems : AppCompatActivity() {

    lateinit var crypto: CryptoUtilities
    lateinit var network: NetworkUtilities
    lateinit var misc: MiscUtilities
    lateinit var io: IOUtilities
    lateinit var keyring: CryptoUtilities.Keyring
    lateinit var configData: SharedPreferences
    lateinit var zxcvbn: Zxcvbn

    lateinit var vault: IOUtilities.Vault
    lateinit var tags: MutableList<IOUtilities.Tag>
    var deletedLogins: MutableList<IOUtilities.Login> = mutableListOf()
    var deletedNotes: MutableList<IOUtilities.Note> = mutableListOf()
    var deletedCards: MutableList<IOUtilities.Card> = mutableListOf()

    lateinit var loginsAdapter: LoginsAdapter
    lateinit var notesAdapter: NotesAdapter
    lateinit var cardsAdapter: CardsAdapter

    lateinit var deletedLoginsRecycler: RecyclerView
    lateinit var deletedLoginsLabel: TextView
    lateinit var deletedNotesRecycler: RecyclerView
    lateinit var deletedNotesLabel: TextView
    lateinit var deletedCardsRecycler: RecyclerView
    lateinit var deletedCardsLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.deleted_items)

        crypto = CryptoUtilities(applicationContext, this)
        misc = MiscUtilities (applicationContext)

        configData = getSharedPreferences (applicationContext.packageName + "_configuration_data", MODE_PRIVATE)

        val intentData = crypto.receiveKeyringFromSecureIntent (
            currentActivityClassNameAsString = getString(R.string.title_activity_deleted_items),
            intent = intent
        )

        keyring = intentData.first

        network = NetworkUtilities(applicationContext, this, keyring)
        io = IOUtilities (applicationContext, this, keyring)

        val allowScreenshots = configData.getBoolean("allowScreenshots", false)
        if (!allowScreenshots) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener { onBackPressed() }

        zxcvbn = Zxcvbn()

        vault = io.getVault()
        vault = io.vaultSorter(vault, io.SORT_LAST_EDITED)

        tags = mutableListOf()

        vault.tag?.forEach { tags.add(io.decryptTag(it)!!) }
        vault.login?.forEach { if (it.deleted) deletedLogins.add(io.decryptLogin(it)) }
        vault.note?.forEach { if (it.deleted) deletedNotes.add(io.decryptNote(it)) }
        vault.card?.forEach { if (it.deleted) deletedCards.add(io.decryptCard(it)) }

        val dangerZoneLayout: LinearLayout = findViewById(R.id.dangerZoneLayout)
        val deletedItemsGraphic: LinearLayout = findViewById(R.id.deletedItemsGraphic)


        deletedLoginsRecycler = findViewById(R.id.deletedLoginsRecycler)
        deletedLoginsLabel = findViewById(R.id.deletedLoginsLabel)

        if (deletedLogins.isEmpty()) {
            deletedLoginsRecycler.visibility = View.GONE
            deletedLoginsLabel.visibility = View.GONE
        } else {
            deletedLoginsRecycler.visibility = View.VISIBLE
            deletedLoginsLabel.visibility = View.VISIBLE
            deletedLoginsRecycler.layoutManager = LinearLayoutManager(this@DeletedItems)
            deletedLoginsLabel.text = deletedLoginsLabel.text.toString() + " (" + deletedLogins.size + ")"
            loginsAdapter = LoginsAdapter(deletedLogins)
            loginsAdapter.setHasStableIds(true)
            deletedLoginsRecycler.adapter = loginsAdapter
            deletedLoginsRecycler.setItemViewCacheSize(50)
            LinearLayoutManager(applicationContext).apply { isAutoMeasureEnabled = false }
            deletedLoginsRecycler.recycledViewPool.setMaxRecycledViews(0, 0)
            loginsAdapter.notifyItemInserted(deletedLogins.size)
            deletedLoginsRecycler.isNestedScrollingEnabled = false
        }


        deletedNotesRecycler = findViewById(R.id.deletedNotesRecycler)
        deletedNotesLabel = findViewById(R.id.deletedNotesLabel)

        if (deletedNotes.isEmpty()) {
            deletedNotesRecycler.visibility = View.GONE
            deletedNotesLabel.visibility = View.GONE
        } else {
            deletedNotesRecycler.visibility = View.VISIBLE
            deletedNotesLabel.visibility = View.VISIBLE
            deletedNotesRecycler.layoutManager = LinearLayoutManager(this@DeletedItems)
            deletedNotesLabel.text = deletedNotesLabel.text.toString() + " (" + deletedNotes.size + ")"

            notesAdapter = NotesAdapter(deletedNotes)
            notesAdapter.setHasStableIds(true)
            deletedNotesRecycler.adapter = notesAdapter
            deletedNotesRecycler.setItemViewCacheSize(50)
            LinearLayoutManager(applicationContext).apply { isAutoMeasureEnabled = false }
            deletedNotesRecycler.recycledViewPool.setMaxRecycledViews(0, 0)
            notesAdapter.notifyItemInserted(deletedNotes.size)
            deletedNotesRecycler.isNestedScrollingEnabled = false
        }


        deletedCardsRecycler = findViewById(R.id.deletedCardsRecycler)
        deletedCardsLabel = findViewById(R.id.deletedCardsLabel)

        if (deletedCards.isEmpty()) {
            deletedCardsRecycler.visibility = View.GONE
            deletedCardsLabel.visibility = View.GONE
        } else {
            deletedCardsRecycler.visibility = View.VISIBLE
            deletedCardsLabel.visibility = View.VISIBLE
            deletedCardsRecycler.layoutManager = LinearLayoutManager(this@DeletedItems)
            deletedCardsLabel.text = deletedCardsLabel.text.toString() + " (" + deletedCards.size + ")"

            cardsAdapter = CardsAdapter(deletedCards)
            cardsAdapter.setHasStableIds(true)
            deletedCardsRecycler.adapter = cardsAdapter
            deletedCardsRecycler.setItemViewCacheSize(50)
            LinearLayoutManager(applicationContext).apply { isAutoMeasureEnabled = false }
            deletedCardsRecycler.recycledViewPool.setMaxRecycledViews(0, 0)
            cardsAdapter.notifyItemInserted(deletedCards.size)
            deletedCardsRecycler.isNestedScrollingEnabled = false
        }

        val deletePermanentlyButton: LinearLayout = findViewById(R.id.deletePermanentlyButton)
        deletePermanentlyButton.setOnClickListener {
            val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
            alertDialog.setTitle("Delete permanently")
            alertDialog.setMessage("Would you like to permanently delete these items? This action is irreversible.")

            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Delete") { dialog, _ ->
                deletePermanently()
                onBackPressed()
            }
            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Go back") { dialog, _ -> dialog.dismiss() }
            alertDialog.show()
        }

        val restoreAllButton: LinearLayout = findViewById(R.id.restoreAllButton)
        restoreAllButton.setOnClickListener {
            val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
            alertDialog.setTitle("Restore all")
            alertDialog.setMessage("Would you like to restore all of these items?")

            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Restore") { dialog, _ ->
                restorePermanently()
                onBackPressed()
            }
            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Go back") { dialog, _ -> dialog.dismiss() }
            alertDialog.show()
        }

        if (deletedLogins.isEmpty() && deletedNotes.isEmpty() && deletedCards.isEmpty()) {
            dangerZoneLayout.visibility = View.GONE
            deletedItemsGraphic.visibility = View.VISIBLE

        } else {
            dangerZoneLayout.visibility = View.VISIBLE
            deletedItemsGraphic.visibility = View.GONE

        }

    }

    override fun onBackPressed () {
        crypto.secureStartActivity (
            nextActivity = Dashboard(),
            nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
            keyring = keyring,
            itemId = null
        )
        super.onBackPressed()

    }

    private fun deletePermanently () {

        val newLogins = mutableListOf<IOUtilities.Login>()
        val newNotes = mutableListOf<IOUtilities.Note>()
        val newCards = mutableListOf<IOUtilities.Card>()

        vault.login?.forEach { if (!it.deleted) newLogins.add(it) }
        vault.note?.forEach { if (!it.deleted) newNotes.add(it) }
        vault.card?.forEach { if (!it.deleted) newCards.add(it) }

        if (deletedLogins.isNotEmpty()) deletedLogins.forEach { network.writeQueueTask (it.id!!, mode = network.MODE_DELETE) }
        if (deletedNotes.isNotEmpty()) deletedNotes.forEach { network.writeQueueTask (it.id!!, mode = network.MODE_DELETE) }
        if (deletedCards.isNotEmpty()) deletedCards.forEach { network.writeQueueTask (it.id!!, mode = network.MODE_DELETE) }

        io.writeVault (
            IOUtilities.Vault(
                version = vault.version,
                tag = vault.tag,
                login = newLogins,
                note = newNotes,
                card = newCards
            )
        )

    }

    private fun restorePermanently () {

        val newLogins = mutableListOf<IOUtilities.Login>()
        val newNotes = mutableListOf<IOUtilities.Note>()
        val newCards = mutableListOf<IOUtilities.Card>()

        vault.login?.forEach {
            if (it.deleted) it.deleted = false
            newLogins.add(it)
        }

        vault.note?.forEach {
            if (it.deleted) it.deleted = false
            newNotes.add(it)
        }

        vault.card?.forEach {
            if (it.deleted) it.deleted = false
            newCards.add(it)
        }

        newLogins.forEach { network.writeQueueTask (it, mode = network.MODE_PUT) }
        newNotes.forEach { network.writeQueueTask (it, mode = network.MODE_PUT) }
        newCards.forEach { network.writeQueueTask (it, mode = network.MODE_PUT) }

        io.writeVault (
            IOUtilities.Vault(
                version = vault.version,
                tag = vault.tag,
                login = newLogins,
                note = newNotes,
                card = newCards
            )
        )

    }

    inner class LoginsAdapter (private val logins: MutableList<IOUtilities.Login>) : RecyclerView.Adapter<LoginsAdapter.ViewHolder>() {

        lateinit var star: Drawable
        lateinit var warning: Drawable
        lateinit var mfa: Drawable
        lateinit var circle: Drawable
        lateinit var emailIcon: Drawable
        lateinit var loginIcon: Drawable
        lateinit var website: Drawable
        lateinit var clipboard: ClipboardManager

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {  // create new views
            val loginCard: View = LayoutInflater.from(parent.context).inflate(R.layout.login, parent, false)
            loginCard.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)

            return ViewHolder(loginCard)
        }

        override fun onBindViewHolder(loginCard: ViewHolder, position: Int) {  // binds the list items to a view
            bindData (loginCard)
        }

        override fun getItemId (position: Int): Long {
            return logins[position].id.hashCode().toLong()
        }

        override fun getItemCount(): Int {  // return the number of the items in the list
            return logins.size
        }

        inner class ViewHolder(ItemView: View) : RecyclerView.ViewHolder(ItemView) {  // Holds the views for adding it to image and text
            val siteIcon: ImageView = itemView.findViewById(R.id.SiteIcon)
            val siteName: TextView = itemView.findViewById(R.id.SiteName)

            val tagText: TextView = itemView.findViewById(R.id.TagText)

            val usernameText: TextView = itemView.findViewById(R.id.usernameText)

            val mfaProgress: LinearProgressIndicator = itemView.findViewById(R.id.mfaProgress)
            val mfaText: TextView = itemView.findViewById(R.id.mfaText)

            val miscText: TextView = itemView.findViewById(R.id.MiscText)

            val loginInformation: LinearLayout = itemView.findViewById(R.id.LoginInformation)

            init {
                mfaProgress.visibility = View.INVISIBLE
                mfaText.text = "••• •••"

                siteIcon.invalidate()
                siteIcon.refreshDrawableState()

                mfaText.invalidate()
                mfaText.refreshDrawableState()

                mfaProgress.invalidate()
                mfaProgress.refreshDrawableState()

                siteIcon.setColorFilter(siteName.currentTextColor)

                star = ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_24)!!
                warning = ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_warning_24)!!
                mfa = ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_time_24)!!
                website = ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_website_24)!!
                circle = ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_circle_24)!!
                loginIcon = ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_account_circle_24)!!
                emailIcon = ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_email_24)!!

                clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            }
        }

        private fun bindData (loginCard: ViewHolder) {
            val login = logins[loginCard.adapterPosition]

            loginCard.siteName.text = login.name

            loginCard.tagText.visibility = View.GONE
            if (tags.size > 0) {
                for (tag in tags) {
                    if (login.tagId == tag.id) {
                        loginCard.tagText.visibility = View.VISIBLE
                        loginCard.tagText.text = tag.name
                        try {
                            if (!tag.color.isNullOrEmpty()) {
                                DrawableCompat.setTint (circle, Color.parseColor(tag.color))
                                DrawableCompat.setTintMode (circle, PorterDuff.Mode.SRC_IN)
                                loginCard.tagText.setCompoundDrawablesWithIntrinsicBounds (null, null, circle, null)
                            }
                        } catch (_: StringIndexOutOfBoundsException) { } catch (_: IllegalArgumentException) { }
                        break
                    }
                }
            }

            if (!login.loginData?.email.isNullOrEmpty()) {
                loginCard.usernameText.text = login.loginData!!.email
                loginCard.usernameText.setCompoundDrawablesRelativeWithIntrinsicBounds (emailIcon, null, null, null)
            } else if (!login.loginData?.username.isNullOrEmpty()) {
                loginCard.usernameText.text = login.loginData!!.username
                loginCard.usernameText.setCompoundDrawablesRelativeWithIntrinsicBounds (loginIcon, null, null, null)
            } else loginCard.usernameText.visibility = View.GONE

            loginCard.miscText.visibility = View.GONE

            if (login.loginData?.password.isNullOrEmpty()) {
                loginCard.miscText.visibility = View.VISIBLE
                loginCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds (null, null, warning, null)
                loginCard.miscText.text = "No password"
            }

            if (login.loginData?.password.isNullOrEmpty() && !login.loginData?.totp?.secret.isNullOrEmpty()) {
                loginCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds (null, null, mfa, null)
                if (login.loginData?.email == login.name) loginCard.usernameText.visibility = View.GONE
                loginCard.miscText.text = "2FA only"
            }

            if (login.favorite) {
                loginCard.miscText.visibility = View.VISIBLE
                loginCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds (null, null, star, null)
                loginCard.miscText.text = ""
            }

            if (!login.loginData?.password.isNullOrEmpty()) {
                thread {
                    val passwordStrength = zxcvbn.measure(login.loginData?.password.toString())
                    if (passwordStrength.score <= 2) {
                        runOnUiThread {
                            loginCard.miscText.visibility = View.VISIBLE
                            loginCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds (null, null, warning, null)
                            loginCard.miscText.text = "Weak password"
                        }
                    }
                }
            }

            if (login.name!!.lowercase().contains("keyspace")) { // Easter egg
                if (login.loginData?.password!!.split("-").size == 12 || login.loginData.password.split(" ").size == 12) {
                    loginCard.miscText.visibility = View.VISIBLE
                    loginCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds (null, null, null, null)
                    loginCard.miscText.text = "You were supposed to write them down! "
                }
                login.customFields!!.forEach { field ->
                    if (field.value.split(" ").size == 12 || field.value.split("-").size == 12) {
                        loginCard.miscText.visibility = View.VISIBLE
                        loginCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds (null, null, null, null)
                        loginCard.miscText.text = "You were supposed to write them down! "
                    }
                }
            }

            if (login.loginData?.totp?.secret.isNullOrEmpty()) {
                loginCard.mfaText.visibility = View.GONE
                loginCard.mfaProgress.visibility = View.GONE
            }

            if (login.iconFile != null) loginCard.siteIcon.setImageDrawable(misc.getSiteIcon(login.iconFile, loginCard.siteName.currentTextColor))
            else loginCard.siteIcon.setImageDrawable(DrawableCompat.wrap(website))

            loginCard.loginInformation.setOnClickListener {
                MaterialAlertDialogBuilder(this@DeletedItems)
                    .setTitle("Delete login")
                    .setCancelable(true)
                    .setMessage("Would you like to restore or permanently delete \"${loginCard.siteName.text}\"?")
                    .setNeutralButton("Go back"){ _, _ -> }
                    .setPositiveButton("Delete"){ _, _ ->
                        val newLogins = mutableListOf<IOUtilities.Login>()
                        vault.login?.forEach { if (it.id != login.id) newLogins.add(it) }
                        network.writeQueueTask (login.id!!, mode = network.MODE_DELETE)
                        io.writeVault (
                            IOUtilities.Vault(
                                version = vault.version,
                                tag = vault.tag,
                                login = newLogins,
                                note = vault.note,
                                card = vault.card
                            )
                        )
                        deletedLogins.remove(login)
                        loginsAdapter.notifyDataSetChanged()
                        deletedLoginsLabel.text = "Deleted logins" + " (" + deletedLogins.size + ")"

                        if (deletedLogins.isEmpty()) {
                            deletedLoginsRecycler.visibility = View.GONE
                            deletedLoginsLabel.visibility = View.GONE
                        }

                    }
                    .setNegativeButton("Restore") {_, _ ->
                        val newLogins = mutableListOf<IOUtilities.Login>()
                        vault.login?.forEach {
                            if (it.id == login.id)
                                if (it.deleted) {
                                    it.deleted = false
                                }
                            newLogins.add(it)
                        }
                        network.writeQueueTask (login.id!!, mode = network.MODE_PUT)
                        io.writeVault (
                            IOUtilities.Vault(
                                version = vault.version,
                                tag = vault.tag,
                                login = newLogins,
                                note = vault.note,
                                card = vault.card
                            )
                        )
                        deletedLogins.remove(login)
                        loginsAdapter.notifyDataSetChanged()
                        deletedLoginsLabel.text = "Deleted logins" + " (" + deletedLogins.size + ")"

                        if (deletedLogins.isEmpty()) {
                            deletedLoginsRecycler.visibility = View.GONE
                            deletedLoginsLabel.visibility = View.GONE
                        }

                    }
                    .show()
            }

        }

    }

    inner class NotesAdapter (private val notes: MutableList<IOUtilities.Note>) : RecyclerView.Adapter<NotesAdapter.ViewHolder>() {

        lateinit var markdownProcessor: MarkdownProcessor
        lateinit var calendar: Calendar

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {  // create new views
            val noteCard: View = LayoutInflater.from(parent.context).inflate(R.layout.note, parent, false)
            noteCard.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)

            return ViewHolder(noteCard)
        }

        @SuppressLint("UseCompatLoadingForDrawables")
        override fun onBindViewHolder(noteCard: ViewHolder, position: Int) {  // binds the list items to a view
            bindData(noteCard)
        }

        override fun getItemCount(): Int {  // return the number of the items in the list
            return notes.size
        }

        override fun getItemId (position: Int): Long {
            return notes[position].id.hashCode().toLong()
        }

        inner class ViewHolder(ItemView: View) : RecyclerView.ViewHolder(ItemView) {  // Holds the views for adding it to image and text
            val note: com.yydcdut.markdown.MarkdownTextView = itemView.findViewById(R.id.Note)
            val noteCardLayout: LinearLayout = itemView.findViewById(R.id.NoteCardLayout)
            val date: TextView = itemView.findViewById(R.id.Date)
            val line: View = itemView.findViewById(R.id.line)

            val tagText: TextView = itemView.findViewById(R.id.TagText)
            val miscText: TextView = itemView.findViewById(R.id.MiscText)

            init {

                note.refreshDrawableState()
                note.invalidate()

                noteCardLayout.refreshDrawableState()
                noteCardLayout.invalidate()

                val theme: Theme = when (applicationContext.resources?.configuration?.uiMode?.and(
                    Configuration.UI_MODE_NIGHT_MASK)) {
                    Configuration.UI_MODE_NIGHT_YES -> ThemeDesert()
                    Configuration.UI_MODE_NIGHT_NO -> ThemeDefault()
                    Configuration.UI_MODE_NIGHT_UNDEFINED -> ThemeDefault()
                    else -> ThemeDefault()
                }

                calendar = Calendar.getInstance(Locale.getDefault())

                val markdownConfig = MarkdownConfiguration.Builder(applicationContext)
                    .setTheme(theme)
                    .showLinkUnderline(true)
                    .setOnLinkClickCallback { _, link ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    }
                    .setLinkFontColor(note.currentTextColor)
                    .setOnTodoClickCallback(object : OnTodoClickCallback {
                        override fun onTodoClicked(view: View?, line: String?, lineNumber: Int): CharSequence {
                            return ""
                        }
                    })
                    .setDefaultImageSize(480, 240)
                    .build()

                markdownProcessor = MarkdownProcessor(this@DeletedItems)
                markdownProcessor.factory(TextFactory.create())
                markdownProcessor.config(markdownConfig)
            }

        }

        private fun bindData (noteCard: ViewHolder) {
            val note = notes[noteCard.adapterPosition]

            noteCard.noteCardLayout.setOnClickListener {
                crypto.secureStartActivity (
                    nextActivity = AddNote(),
                    nextActivityClassNameAsString = getString(R.string.title_activity_add_note),
                    keyring = keyring,
                    itemId = note.id
                )
            }

            noteCard.note.text = note.notes
            noteCard.noteCardLayout.setBackgroundColor(0)

            if (!note.notes.isNullOrEmpty()) {
                thread {
                    val parsedText = markdownProcessor.parse(note.notes)
                    runOnUiThread {
                        noteCard.note.text = parsedText
                    }
                }
            }

            if (note.favorite) {
                noteCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null, ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_24),null)
                noteCard.miscText.text = null
            } else noteCard.miscText.visibility = View.GONE

            calendar.timeInMillis = note.dateModified?.times(1000L)!!
            val dateAndTime = DateFormat.format("MMM dd, yyyy ⋅  hh:mm a", calendar).toString()
            noteCard.date.text = dateAndTime

            noteCard.note.setBackgroundColor(0x00000000)
            if (!note.color.isNullOrEmpty()) {
                val noteColor = note.color
                noteCard.noteCardLayout.setBackgroundColor(Color.parseColor(noteColor))
                val intColor: Int = noteColor!!.replace("#", "").toInt(16)
                val r = intColor shr 16 and 0xFF; val g = intColor shr 8 and 0xFF; val b = intColor shr 0 and 0xFF
                if (g >= 200 || b >= 200) {
                    noteCard.note.setTextColor (Color.BLACK)
                    noteCard.date .setTextColor (Color.BLACK)
                    noteCard.miscText.setTextColor (Color.BLACK)
                    noteCard.tagText.setTextColor (Color.BLACK)
                    noteCard.line.setBackgroundColor (Color.BLACK)

                    val starIcon = DrawableCompat.wrap(getDrawable(R.drawable.ic_baseline_star_24)!!)
                    DrawableCompat.setTint(starIcon, Color.BLACK)
                    DrawableCompat.setTintMode(starIcon, PorterDuff.Mode.MULTIPLY)
                    noteCard.miscText.setCompoundDrawablesWithIntrinsicBounds(starIcon, null, null, null)

                    val tagIcon = DrawableCompat.wrap(getDrawable(R.drawable.ic_baseline_circle_24)!!)
                    DrawableCompat.setTint(tagIcon, Color.BLACK)
                    DrawableCompat.setTintMode(tagIcon, PorterDuff.Mode.SRC_IN)
                    noteCard.tagText.setCompoundDrawablesWithIntrinsicBounds(null, null, tagIcon, null)

                } else {
                    noteCard.note.setTextColor(Color.WHITE)
                    noteCard.note.setTextColor(Color.WHITE)
                    noteCard.date.setTextColor(Color.WHITE)
                    noteCard.miscText.setTextColor(Color.WHITE)
                    noteCard.tagText.setTextColor(Color.WHITE)
                    noteCard.line.setBackgroundColor (Color.WHITE)

                    val starIcon = DrawableCompat.wrap(getDrawable(R.drawable.ic_baseline_star_24)!!)
                    DrawableCompat.setTint(starIcon, Color.WHITE)
                    DrawableCompat.setTintMode(starIcon, PorterDuff.Mode.MULTIPLY)
                    noteCard.miscText.setCompoundDrawablesWithIntrinsicBounds(starIcon, null, null, null)

                    val tagIcon = DrawableCompat.wrap(getDrawable(R.drawable.ic_baseline_circle_24)!!)
                    DrawableCompat.setTint(tagIcon, Color.WHITE)
                    DrawableCompat.setTintMode(tagIcon, PorterDuff.Mode.SRC_IN)
                    noteCard.tagText.setCompoundDrawablesWithIntrinsicBounds(null, null, tagIcon, null)
                }
            }

            noteCard.tagText.visibility = View.GONE
            if (tags.size > 0) {
                for (tag in tags) {
                    if (note.tagId == tag.id) {
                        noteCard.tagText.visibility = View.VISIBLE
                        noteCard.tagText.text = tag.name
                        try {
                            if (!tag.color.isNullOrEmpty()) {
                                val tagIcon = DrawableCompat.wrap(getDrawable(R.drawable.ic_baseline_circle_24)!!)
                                DrawableCompat.setTint(tagIcon, Color.parseColor(tag.color))
                                DrawableCompat.setTintMode(tagIcon, PorterDuff.Mode.SRC_IN)
                                noteCard.tagText.setCompoundDrawablesWithIntrinsicBounds(null, null, tagIcon, null)
                            }
                        } catch (noColor: StringIndexOutOfBoundsException) { } catch (noColor: IllegalArgumentException) { }
                        break
                    }
                }
            }

            noteCard.noteCardLayout.setOnClickListener {
                MaterialAlertDialogBuilder(this@DeletedItems)
                    .setTitle("Delete login")
                    .setCancelable(true)
                    .setMessage("Would you like to restore or permanently delete this note?")
                    .setNeutralButton("Go back"){ _, _ -> }
                    .setPositiveButton("Delete"){ _, _ ->
                        deletedNotes.remove(note)
                        notesAdapter.notifyDataSetChanged()
                    }
                    .setNegativeButton("Restore") {_, _ ->
                        deletedNotes.remove(note)
                        notesAdapter.notifyDataSetChanged()
                    }
                    .show()
            }

        }
    }

    inner class CardsAdapter (private val cards: MutableList<IOUtilities.Card>) : RecyclerView.Adapter<CardsAdapter.ViewHolder>() {

        lateinit var setRightOut: AnimatorSet
        lateinit var setLeftIn: AnimatorSet
        lateinit var animatedContactless: AnimatedVectorDrawable

        lateinit var clipboard: ClipboardManager

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {  // create new views
            val cardCard: View = LayoutInflater.from(parent.context).inflate(R.layout.card, parent, false)
            cardCard.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            return ViewHolder(cardCard)
        }

        @SuppressLint("ResourceType")
        override fun onBindViewHolder(cardCard: ViewHolder, position: Int) {  // binds the list items to a view
            bindData (cardCard)
        }

        override fun getItemId (position: Int): Long {
            return cards[position].id.hashCode().toLong()
        }

        override fun getItemCount(): Int {  // return the number of the items in the list
            return cards.size
        }

        @SuppressLint("ResourceType", "ClickableViewAccessibility")
        inner class ViewHolder(ItemView: View) : RecyclerView.ViewHolder(ItemView) {  // Holds the views for adding it to image and text

            val cardsCardFrontLayout: ConstraintLayout = itemView.findViewById(R.id.CardsCardFrontLayout)
            val cardsCardLayout: ConstraintLayout = itemView.findViewById(R.id.CardsCardLayout)

            val bankNameFront: TextView = itemView.findViewById(R.id.bankNameFront)
            val bankLogoFront: ImageView = itemView.findViewById(R.id.bankLogoFront)
            val rfidIcon: ImageView = itemView.findViewById(R.id.RfidIcon)
            val cardNumber: TextView = itemView.findViewById(R.id.CardNumber)
            val toDate: TextView = itemView.findViewById(R.id.toDate)
            val toLabel: TextView = itemView.findViewById(R.id.toLabel)
            val cardHolder: TextView = itemView.findViewById(R.id.CardHolder)
            val paymentGateway: ImageView = itemView.findViewById(R.id.PaymentGateway)

            init {

                rfidIcon.invalidate()
                rfidIcon.refreshDrawableState()

                setRightOut = AnimatorInflater.loadAnimator(applicationContext, R.anim.flip2) as AnimatorSet
                setLeftIn = AnimatorInflater.loadAnimator(applicationContext, R.anim.flip1) as AnimatorSet

                clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

                animatedContactless = getDrawable(R.drawable.lessdistractingflickercontactless)?.mutate() as AnimatedVectorDrawable

                var isBackVisible = false

            }

        }

        @SuppressLint("ClickableViewAccessibility", "ResourceType", "UseCompatLoadingForDrawables")
        fun bindData (cardCard: ViewHolder) {
            val card = cards[cardCard.adapterPosition]

            if (card.cardNumber?.length == 16) cardCard.cardNumber.text = card.cardNumber.replace("....".toRegex(), "$0 ")
            else cardCard.cardNumber.text = card.cardNumber

            cardCard.toDate.text = card.expiry
            cardCard.cardHolder.text = card.cardholderName

            cardCard.bankNameFront.text = card.name
            cardCard.bankLogoFront.visibility = View.GONE


            val cardColor = card.color
            if (!card.color.isNullOrEmpty()) cardCard.cardsCardFrontLayout.backgroundTintList = ColorStateList.valueOf(Color.parseColor(cardColor))
            else cardCard.cardsCardFrontLayout.backgroundTintList = ColorStateList.valueOf(Color.DKGRAY)

            val intColor: Int = try { cardCard.cardsCardFrontLayout.backgroundTintList?.defaultColor!! } catch (_: NullPointerException) { 0 }

            val r = intColor shr 16 and 0xFF; val g = intColor shr 8 and 0xFF; val b = intColor shr 0 and 0xFF
            if (g >= 200 || b >= 200) {
                cardCard.rfidIcon.setColorFilter(Color.BLACK)
                cardCard.bankNameFront.setTextColor (Color.BLACK)
                cardCard.cardHolder.setTextColor (Color.BLACK)
                cardCard.toDate.setTextColor (Color.BLACK)
                cardCard.toLabel.setTextColor (Color.BLACK)
                cardCard.cardNumber.setTextColor (Color.BLACK)
                cardCard.cardNumber.setTextColor (Color.BLACK)
                cardCard.bankLogoFront.setColorFilter(Color.BLACK)
                cardCard.paymentGateway.setColorFilter(Color.BLACK)
            } else {
                cardCard.rfidIcon.setColorFilter(Color.WHITE)
                cardCard.cardHolder .setTextColor (Color.WHITE)
                cardCard.toDate .setTextColor (Color.WHITE)
                cardCard.toLabel.setTextColor (Color.WHITE)
                cardCard.cardNumber.setTextColor (Color.WHITE)
                cardCard.cardNumber.setTextColor (Color.WHITE)
                cardCard.rfidIcon.foregroundTintList = ColorStateList.valueOf(Color.WHITE)
                cardCard.bankLogoFront.setColorFilter(Color.WHITE)
                cardCard.paymentGateway.setColorFilter(Color.WHITE)
            }

            val paymentGateway = misc.getPaymentGateway(card.cardNumber.toString())
            var bankLogo = if (card.iconFile != null) misc.getSiteIcon(card.iconFile, cardCard.cardNumber.currentTextColor) else null

            var gatewayLogo = if (paymentGateway != null) misc.getSiteIcon(paymentGateway, cardCard.cardNumber.currentTextColor) else null

            thread {
                if (bankLogo != null && card.iconFile != "bank") {
                    runOnUiThread {
                        cardCard.bankLogoFront.visibility = View.VISIBLE
                        cardCard.bankLogoFront.setImageDrawable(DrawableCompat.wrap(bankLogo))
                    }
                } else cardCard.bankLogoFront.visibility = View.GONE

                if (paymentGateway != null) {
                    if (gatewayLogo != null) runOnUiThread { cardCard.paymentGateway.setImageDrawable(gatewayLogo) }
                    else cardCard.paymentGateway.visibility = View.GONE
                } else cardCard.paymentGateway.visibility = View.GONE

            }

            cardCard.rfidIcon.visibility = View.GONE
            if (card.rfid == true) {
                cardCard.rfidIcon.visibility = View.VISIBLE
                cardCard.rfidIcon.setImageDrawable(animatedContactless)
                animatedContactless.start()
            }

            cardCard.cardsCardLayout.setOnClickListener {
                MaterialAlertDialogBuilder(this@DeletedItems)
                    .setTitle("Delete card")
                    .setCancelable(true)
                    .setMessage("Would you like to restore or permanently delete \"${cardCard.bankNameFront.text}\"?")
                    .setNeutralButton("Go back"){ _, _ -> }
                    .setPositiveButton("Delete"){ _, _ ->
                        deletedCards.remove(card)
                        cardsAdapter.notifyDataSetChanged()
                    }
                    .setNegativeButton("Restore") {_, _ ->
                        deletedCards.remove(card)
                        cardsAdapter.notifyDataSetChanged()
                    }
                    .show()
            }

        }

    }

}