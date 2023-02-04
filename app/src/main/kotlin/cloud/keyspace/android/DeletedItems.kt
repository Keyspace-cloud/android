package cloud.keyspace.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.nulabinc.zxcvbn.Zxcvbn
import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import kotlin.concurrent.thread

class DeletedItems : AppCompatActivity() {

    lateinit var crypto: CryptoUtilities
    lateinit var misc: MiscUtilities
    lateinit var io: IOUtilities
    lateinit var keyring: CryptoUtilities.Keyring
    lateinit var configData: SharedPreferences
    lateinit var zxcvbn: Zxcvbn
    lateinit var vault: IOUtilities.Vault
    lateinit var tags: MutableList<IOUtilities.Tag>
    lateinit var logins: MutableList<IOUtilities.Login>
    lateinit var notes: MutableList<IOUtilities.Note>
    lateinit var cards: MutableList<IOUtilities.Card>

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

        logins = mutableListOf()
        for (encryptedLogin in io.getLogins(vault)) if (encryptedLogin.deleted) logins.add(io.decryptLogin(encryptedLogin))

        notes = mutableListOf()
        for (encryptedNote in io.getNotes(vault)) if (encryptedNote.deleted) notes.add(io.decryptNote(encryptedNote))

        cards = mutableListOf()
        for (encryptedCard in io.getCards(vault)) if (encryptedCard.deleted) cards.add(io.decryptCard(encryptedCard))

        val deletedItemsRecycler: RecyclerView = findViewById(R.id.deletedItemsRecycler)
        deletedItemsRecycler.layoutManager = LinearLayoutManager(this@DeletedItems)

        val adapter = LoginsAdapter(logins)
        adapter.setHasStableIds(true)
        deletedItemsRecycler.adapter = adapter
        deletedItemsRecycler.setItemViewCacheSize(50);
        LinearLayoutManager(applicationContext).apply { isAutoMeasureEnabled = false }
        deletedItemsRecycler.recycledViewPool.setMaxRecycledViews(0, 0)
        adapter.notifyItemInserted(logins.size)
        deletedItemsRecycler.isNestedScrollingEnabled = false

        val deletePermanentlyButton: LinearLayout = findViewById(R.id.deletePermanentlyButton)
        deletePermanentlyButton.setOnClickListener {
            val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
            alertDialog.setTitle("Delete permanently")
            alertDialog.setMessage("Would you like to permanently delete these items? This action is irreversible")

            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Delete permanently") { dialog, _ ->
                onBackPressed()
            }
            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Go back") { dialog, _ -> dialog.dismiss() }
            alertDialog.show()
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
                usernameText.isSelected = true
                tagText.isSelected = true
                miscText.isSelected = true
                usernameText.isSelected = true
                siteName.isSelected = true
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
            if (tags?.size!! > 0) {
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

            // misc icon data
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

            var otpCode: String? = null
            if (!login.loginData?.totp?.secret.isNullOrEmpty()) {
                try {
                    thread {
                        otpCode = GoogleAuthenticator(base32secret = login.loginData?.totp!!.secret!!).generate()
                    }
                } catch (_: IllegalStateException) {}
            } else {
                loginCard.mfaText.visibility = View.GONE
                loginCard.mfaProgress.visibility = View.GONE
            }

            // tap on totp / mfa / 2fa
            loginCard.mfaText.setOnClickListener {
                val clip = ClipData.newPlainText("Keyspace", otpCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(applicationContext, "Code copied!", Toast.LENGTH_LONG).show()
            }

            if (login.iconFile != null) loginCard.siteIcon.setImageDrawable(misc.getSiteIcon(login.iconFile, loginCard.siteName.currentTextColor))
            else loginCard.siteIcon.setImageDrawable(DrawableCompat.wrap(website))

        }

    }

}