package cloud.keyspace.android

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.annotation.SuppressLint
import android.content.*
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextUtils.split
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.Base64
import android.util.Log
import android.view.*
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
import android.view.animation.Animation
import android.view.animation.AnimationUtils.loadAnimation
import android.view.animation.AnimationUtils.loadLayoutAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.android.volley.NetworkError
import com.budiyev.android.codescanner.*
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.goterl.lazysodium.utils.HexMessageEncoder
import com.keyspace.keyspacemobile.NetworkUtilities
import com.neovisionaries.ws.client.*
import com.nulabinc.zxcvbn.Zxcvbn
import com.permissionx.guolindev.PermissionX
import com.tsuryo.swipeablerv.SwipeLeftRightCallback
import com.tsuryo.swipeablerv.SwipeableRecyclerView
import com.yydcdut.markdown.MarkdownConfiguration
import com.yydcdut.markdown.MarkdownProcessor
import com.yydcdut.markdown.callback.OnTodoClickCallback
import com.yydcdut.markdown.syntax.text.TextFactory
import com.yydcdut.markdown.theme.Theme
import com.yydcdut.markdown.theme.ThemeDefault
import com.yydcdut.markdown.theme.ThemeDesert
import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.cancel
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.abs


class Dashboard : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    var KEYROUTE = "kr"
    var KEYROUTE_CLOSED = "kr-closed"
    var KEYROUTE_ERROR = "kr-error"
    var SINGLE_SIGN_ON = "sso"

    private lateinit var fragmentView: View
    private lateinit var fragmentRoot: FrameLayout
    private lateinit var inflater: LayoutInflater
    var coldStart = true

    private lateinit var sortBy: String
    private lateinit var lastFragment: String

    private lateinit var scannerView: CodeScannerView
    private lateinit var afterScanLayout: LinearLayout
    private lateinit var contractPage: ConstraintLayout
    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var bottomSheet: LinearLayout
    private lateinit var bottomNavbar: BottomNavigationView
    private lateinit var bottomSheetQrViewfinder: CardView
    private lateinit var swipeText: TextView
    private lateinit var swipeIcon: ImageView
    private lateinit var swipeHint: LinearLayout


    private lateinit var topBar: AppBarLayout
    private lateinit var input: InputMethodManager
    private lateinit var searchBar: EditText
    private lateinit var searchButton: ImageView
    private lateinit var root: CoordinatorLayout

    private lateinit var loginsRecycler: SwipeableRecyclerView
    private lateinit var loginsScrollView: NestedScrollView
    private lateinit var notesRecycler: SwipeableRecyclerView
    private lateinit var notesScrollView: NestedScrollView
    private lateinit var cardsRecycler: RecyclerView
    private lateinit var cardsScrollView: NestedScrollView
    var flipDistance: Float = 65535f

    private lateinit var filterButton: ImageView
    private lateinit var accountInfoButton: CardView
    private lateinit var keyspaceAccountPicture: ImageView
    private lateinit var connectionStatusDot: ImageView
    private var networkStatus: String = "alive"

    private lateinit var fab: FloatingActionButton

    private lateinit var codeScanner: CodeScanner

    lateinit var crypto: CryptoUtilities
    lateinit var network: NetworkUtilities
    lateinit var misc: MiscUtilities
    lateinit var io: IOUtilities
    private lateinit var configData: SharedPreferences

    lateinit var keyring: CryptoUtilities.Keyring

    lateinit var vault: IOUtilities.Vault
    lateinit var tags: MutableList<IOUtilities.Tag>
    lateinit var logins: MutableList<IOUtilities.Login>
    lateinit var notes: MutableList<IOUtilities.Note>
    lateinit var cards: MutableList<IOUtilities.Card>

    val mfaCodesTimer = Timer()
    val vaultSyncTimer = Timer()

    lateinit var zxcvbn: Zxcvbn

    fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        crypto = CryptoUtilities(applicationContext, this)

        try {
            if (!this::keyring.isInitialized) {
                keyring = crypto.receiveKeyringFromSecureIntent (
                    currentActivityClassNameAsString = getString(R.string.title_activity_dashboard),
                    intent = intent
                ).first

            }
        } catch (themeSwitched: Exception) {
            Toast.makeText(applicationContext, "Restarting app to apply theme", Toast.LENGTH_LONG).show()
            startActivity(Intent(applicationContext, StartHere::class.java))
            finishAffinity()
            finish()
            return
        }

        setContentView(R.layout.dashboard)
        flipDistance = 7500 * applicationContext.resources.displayMetrics.density

        configData = getSharedPreferences (applicationContext.packageName + "_configuration_data", MODE_PRIVATE)

        zxcvbn = Zxcvbn()

        val allowScreenshots = configData.getBoolean("allowScreenshots", false)
        if (!allowScreenshots) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        root = findViewById(R.id.dashboardRoot)
        input = this.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        fragmentRoot = findViewById(R.id.fragmentRoot)
        fab = findViewById(R.id.floatingActionButton)

        network = NetworkUtilities (applicationContext, this, keyring)
        io = IOUtilities (applicationContext, this, keyring)

        sortBy = configData.getString("sort_by", io.SORT_LAST_EDITED)!!
        lastFragment = configData.getString("lastFragment", io.TYPE_LOGIN)!!

        // To make it quick
        val loadingScreen = ShowLoadingScreen()
        loadingScreen.showScreen (lastFragment)
        misc = MiscUtilities (applicationContext)
        keyspaceAccountPicture = findViewById(R.id.keyspaceAccountPicture)
        keyspaceAccountPicture.setImageDrawable(misc.generateProfilePicture(configData.getString("userEmail", null)!!))
        connectionStatusDot = findViewById(R.id.connectionStatusDot)
        connectionStatusDot.visibility = View.GONE

        thread {
            vault = io.getVault()
            vault = io.vaultSorter(vault, sortBy)

            tags = mutableListOf()
            vault.tag?.forEach { tags.add(io.decryptTag(it)!!) }

            logins = mutableListOf()
            notes = mutableListOf()
            cards = mutableListOf()

            runOnUiThread {
                vaultSynchronizer()
                renderBottomNavBar()
                renderBottomSheet()

                when (lastFragment) {
                    io.TYPE_LOGIN -> {
                        renderLoginsFragment()
                        renderTopBar(io.TYPE_LOGIN)
                        bottomNavbar.selectedItemId = R.id.logins
                    }
                    io.TYPE_NOTE -> {
                        renderNotesFragment()
                        renderTopBar(io.TYPE_NOTE)
                        bottomNavbar.selectedItemId = R.id.notes
                    }
                    io.TYPE_CARD -> {
                        renderCardsFragment()
                        renderTopBar(io.TYPE_CARD)
                        bottomNavbar.selectedItemId = R.id.payments
                    }
                }
                loadingScreen.killScreen()

                coldStart = false
            }
        }

    }

    inner class ShowLoadingScreen {

        var builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(this@Dashboard)
        private lateinit var dialog: AlertDialog
        private lateinit var iconography: ImageView
        private lateinit var loadingText: TextView
        private lateinit var loadingSubtitle: TextView
        private lateinit var loadingBar: ProgressBar

        fun showScreen (loadingType: String) {
            builder.setCancelable(true)

            val loginInfoBox: View = layoutInflater.inflate(R.layout.loading_screen, null)
            builder.setView(loginInfoBox)

            iconography = loginInfoBox.findViewById(R.id.iconography)
            // iconography.setImageDrawable(getDrawable(R.drawable.keyspace))
            iconography.visibility = View.GONE
            iconography.scaleX = 0.75f
            iconography.scaleY = 0.75f

            loadingText = loginInfoBox.findViewById(R.id.loadingText)
            loadingSubtitle = loginInfoBox.findViewById(R.id.loadingSubtitle)
            loadingBar = loginInfoBox.findViewById(R.id.loadingBar)

            loadingText.text = "Loading"
            loginInfoBox.startAnimation(loadAnimation(applicationContext, R.anim.from_bottom))

            val loadingSubtitleString = if (loadingType.contains(io.TYPE_LOGIN)) "logins"
            else if  (loadingType.contains(io.TYPE_NOTE)) "notes"
            else if  (loadingType.contains(io.TYPE_CARD)) "cards"
            else "data"

            loadingSubtitle.text = "Decrypting $loadingSubtitleString"

            dialog = builder.create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialog.show()
        }

        fun killScreen () {
            try {
                dialog.dismiss()
            } catch (promptNotStarted: UninitializedPropertyAccessException) { }
        }

    }

    private fun filterDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("Sort by")
        builder.setCancelable(true)

        val filterBox: View = inflater.inflate(R.layout.filter, null)
        builder.setView(filterBox)

        filterBox.startAnimation(loadAnimation(applicationContext, R.anim.from_top))

        val dialog = builder.create()
        dialog.show()

        val tag: Chip = dialog.findViewById(R.id.tagText)!!
        val name: Chip = dialog.findViewById(R.id.name)!!
        val oldest: Chip = dialog.findViewById(R.id.oldest)!!
        val recentlyEdited: Chip = dialog.findViewById(R.id.recentlyEdited)!!
        val favorite: Chip = dialog.findViewById(R.id.favorite)!!
        val username: Chip = dialog.findViewById(R.id.username)!!
        val email: Chip = dialog.findViewById(R.id.email)!!
        val authenticator: Chip = dialog.findViewById(R.id.authenticator)!!
        val notes: Chip = dialog.findViewById(R.id.notes)!!
        val weakest: Chip = dialog.findViewById(R.id.weakest)!!
        val color: Chip = dialog.findViewById(R.id.color)!!

        when (configData.getString("sort_by", io.SORT_COLOR_ASCENDING)) {
            io.SORT_COLOR_ASCENDING -> color.isChecked = true
            io.SORT_NAME_ASCENDING -> name.isChecked = true
            io.SORT_WEAKEST -> weakest.isChecked = true
            io.SORT_OLDEST -> oldest.isChecked = true
            io.SORT_TAG_CREATED -> tag.isChecked = true
            io.SORT_LAST_EDITED -> recentlyEdited.isChecked = true
            io.SORT_NOTES_ASCENDING -> notes.isChecked = true
            io.SORT_AUTHENTICATOR_ASCENDING -> authenticator.isChecked = true
            io.SORT_EMAIL_ASCENDING -> email.isChecked = true
            io.SORT_FAVORITES -> favorite.isChecked = true
            io.SORT_USERNAME_ASCENDING -> username.isChecked = true
            else -> name.isChecked = true
        }

        val currentFragment = if (lastFragment == io.TYPE_LOGIN) io.TYPE_LOGIN
        else if (configData.getString("lastFragment", io.TYPE_NOTE) == io.TYPE_NOTE) io.TYPE_NOTE
        else if (configData.getString("lastFragment", io.TYPE_CARD) == io.TYPE_CARD) io.TYPE_CARD
        else io.TYPE_LOGIN

        if (currentFragment == io.TYPE_LOGIN) {

            when (configData.getString("sort_by", io.SORT_COLOR_ASCENDING)) {
                io.SORT_COLOR_ASCENDING -> color.isChecked = true
                io.SORT_NAME_ASCENDING -> name.isChecked = true
                io.SORT_WEAKEST -> weakest.isChecked = true
                io.SORT_OLDEST -> oldest.isChecked = true
                io.SORT_TAG_CREATED -> tag.isChecked = true
                io.SORT_LAST_EDITED -> recentlyEdited.isChecked = true
                io.SORT_NOTES_ASCENDING -> notes.isChecked = true
                io.SORT_AUTHENTICATOR_ASCENDING -> authenticator.isChecked = true
                io.SORT_EMAIL_ASCENDING -> email.isChecked = true
                io.SORT_FAVORITES -> favorite.isChecked = true
                io.SORT_USERNAME_ASCENDING -> username.isChecked = true
                else -> name.isChecked = true
            }

            username.isVisible = true
            color.isVisible = false
            email.isVisible = true
            authenticator.isVisible = true
            weakest.isVisible = true

            tag.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_TAG_CREATED).apply()
                dialog.cancel()
            }

            oldest.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_OLDEST).apply()
                dialog.cancel()
            }

            name.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_NAME_ASCENDING).apply()
                dialog.cancel()
            }

            username.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_USERNAME_ASCENDING).apply()
                dialog.cancel()
            }

            email.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_EMAIL_ASCENDING).apply()
                dialog.cancel()
            }

            authenticator.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_AUTHENTICATOR_ASCENDING).apply()
                dialog.cancel()
            }

            weakest.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_WEAKEST).apply()
                dialog.cancel()
            }

            favorite.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_FAVORITES).apply()
                dialog.cancel()
            }

            notes.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_NOTES_ASCENDING).apply()
                dialog.cancel()
            }

            recentlyEdited.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_LAST_EDITED).apply()
                dialog.cancel()
            }

            dialog.setOnCancelListener {
                vault = io.vaultSorter(vault, sortBy)
                renderLoginsFragment()
            }

        } else if (currentFragment == io.TYPE_NOTE) {
            username.isVisible = false
            email.isVisible = false
            authenticator.isVisible = false
            notes.isVisible = false
            weakest.isVisible = false
            notes.text = "Title (ascending)"

            when (configData.getString("sort_by", io.SORT_COLOR_ASCENDING)) {
                io.SORT_COLOR_ASCENDING -> color.isChecked = true
                io.SORT_NAME_ASCENDING -> name.isChecked = true
                io.SORT_WEAKEST -> name.isChecked = true
                io.SORT_OLDEST -> oldest.isChecked = true
                io.SORT_TAG_CREATED -> tag.isChecked = true
                io.SORT_LAST_EDITED -> recentlyEdited.isChecked = true
                io.SORT_NOTES_ASCENDING -> name.isChecked = true
                io.SORT_AUTHENTICATOR_ASCENDING -> name.isChecked = true
                io.SORT_EMAIL_ASCENDING -> name.isChecked = true
                io.SORT_FAVORITES -> favorite.isChecked = true
                io.SORT_USERNAME_ASCENDING -> name.isChecked = true
                else -> name.isChecked = true
            }

            tag.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_TAG_CREATED).apply()
                dialog.cancel()
            }

            name.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_NAME_ASCENDING).apply()
                dialog.cancel()
            }

            oldest.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_OLDEST).apply()
                dialog.cancel()
            }

            favorite.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_FAVORITES).apply()
                dialog.cancel()
            }

            color.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_COLOR_ASCENDING).apply()
                dialog.cancel()
            }

            notes.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_NOTES_ASCENDING).apply()
                dialog.cancel()
            }

            recentlyEdited.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_LAST_EDITED).apply()
                dialog.cancel()
            }

            dialog.setOnCancelListener {
                vault = io.vaultSorter(vault, sortBy)
                renderNotesFragment()
            }

        } else if (currentFragment == io.TYPE_CARD) {

            when (configData.getString("sort_by", io.SORT_COLOR_ASCENDING)) {
                io.SORT_COLOR_ASCENDING -> color.isChecked = true
                io.SORT_NAME_ASCENDING -> name.isChecked = true
                io.SORT_WEAKEST -> name.isChecked = true
                io.SORT_OLDEST -> oldest.isChecked = true
                io.SORT_TAG_CREATED -> tag.isChecked = true
                io.SORT_LAST_EDITED -> recentlyEdited.isChecked = true
                io.SORT_NOTES_ASCENDING -> notes.isChecked = true
                io.SORT_AUTHENTICATOR_ASCENDING -> name.isChecked = true
                io.SORT_EMAIL_ASCENDING -> name.isChecked = true
                io.SORT_FAVORITES -> favorite.isChecked = true
                io.SORT_USERNAME_ASCENDING -> name.isChecked = true
                else -> name.isChecked = true
            }

            username.isVisible = false
            email.isVisible = false
            authenticator.isVisible = false
            weakest.isVisible = false

            tag.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_TAG_CREATED).apply()
                dialog.cancel()
            }

            oldest.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_OLDEST).apply()
                dialog.cancel()
            }

            color.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_COLOR_ASCENDING).apply()
                dialog.cancel()
            }

            name.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_NAME_ASCENDING).apply()
                dialog.cancel()
            }

            recentlyEdited.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_LAST_EDITED).apply()
                dialog.cancel()
            }

            favorite.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_FAVORITES).apply()
                dialog.cancel()
            }

            notes.setOnClickListener {
                configData.edit().putString("sort_by", io.SORT_NOTES_ASCENDING).apply()
                dialog.cancel()
            }

            dialog.setOnCancelListener {
                renderCardsFragment()
            }

        }

        dialog.setOnDismissListener(object : PopupMenu.OnDismissListener, DialogInterface.OnDismissListener {
            override fun onDismiss(menu: PopupMenu?) {
                filterButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_sort_24))
                val filterToArrow = ContextCompat.getDrawable(applicationContext, R.drawable.arrowtofilter) as AnimatedVectorDrawable
                filterButton.setImageDrawable(filterToArrow)
                filterToArrow.start()
            }

            override fun onDismiss(p0: DialogInterface?) {
                filterButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_sort_24))
                val filterToArrow = ContextCompat.getDrawable(applicationContext, R.drawable.arrowtofilter) as AnimatedVectorDrawable
                filterButton.setImageDrawable(filterToArrow)
                filterToArrow.start()
            }
        })
    }

    var alreadyAnimated = false
    @SuppressLint("ClickableViewAccessibility")
    private fun renderTopBar (searchType: String) {
        topBar = findViewById(R.id.topBar)
        searchButton = findViewById(R.id.searchButton)
        filterButton = findViewById(R.id.filterButton)
        accountInfoButton = findViewById(R.id.accountInfoButton)

        accountInfoButton.setOnClickListener {
            loginInfoDialog()
        }

        filterButton.setOnClickListener {
            filterButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_sort_24))
            val filterToArrow = ContextCompat.getDrawable(applicationContext, R.drawable.filtertoarrow) as AnimatedVectorDrawable
            filterButton.setImageDrawable(filterToArrow)
            filterToArrow.start()
            filterDialog()
        }

        val closeIcon = getDrawable(R.drawable.searchtoclose) as AnimatedVectorDrawable?
        val searchIcon = getDrawable(R.drawable.closetosearch) as AnimatedVectorDrawable?

        searchBar = findViewById(R.id.searchBar)
        searchButton = findViewById(R.id.searchButton)

        searchBar.text.clear()
        searchBar.clearFocus()

        try {

            searchBar.setOnTouchListener { _, _ ->
                if (!alreadyAnimated) {
                    searchButton.setImageDrawable(closeIcon)
                    closeIcon!!.start()
                    alreadyAnimated = true
                }

                searchBar.isFocusableInTouchMode = true
                searchBar.requestFocusFromTouch()
                input.showSoftInput(searchBar, 0)
                true
            }

            when {
                searchType.contains(io.TYPE_LOGIN) -> searchBar.hint = "Search logins"
                searchType.contains(io.TYPE_NOTE) -> searchBar.hint = "Search notes"
                searchType.contains(io.TYPE_CARD) -> searchBar.hint = "Search cards"
            }

            searchBar.addTextChangedListener (object : TextWatcher {
                override fun beforeTextChanged (charSequence: CharSequence, i: Int, i1: Int, i2: Int) { }

                @SuppressLint("NotifyDataSetChanged", "SuspiciousIndentation")
                override fun onTextChanged (searchTerms: CharSequence, i: Int, i1: Int, i2: Int) {

                        if (searchType.contains(io.TYPE_LOGIN)) {

                            val searchTermsList: MutableList<IOUtilities.Login> = mutableListOf()

                            for (login in logins) {
                                val loginSearchableData = mutableListOf<String?>()
                                loginSearchableData.add(login.name)
                                loginSearchableData.add(login.notes)
                                loginSearchableData.add(login.iconFile)
                                loginSearchableData.add(login.customFields.toString())
                                loginSearchableData.add(login.loginData?.email)
                                loginSearchableData.add(login.loginData?.username)
                                loginSearchableData.add(login.loginData?.siteUrls.toString())
                                val loginSearchableDataString = loginSearchableData.filterNotNull().joinToString("").lowercase(Locale.getDefault()).filter { it.isLetterOrDigit() }

                                if (loginSearchableDataString.contains(searchTerms.toString().lowercase(Locale.getDefault()).filter { it.isLetterOrDigit() })) {  // other search
                                    searchTermsList.add(login)
                                }

                                for (tag in tags) {
                                    if (searchTerms.toString().lowercase(Locale.getDefault()) in tag.name?.lowercase(Locale.getDefault())!!) {
                                        if (login.tagId == tag.id) searchTermsList.add (login)
                                    }
                                }

                            }

                            try {
                                if (searchTermsList.isNotEmpty()) {
                                    fragmentRoot.removeAllViews()
                                    fragmentView = inflater.inflate(R.layout.dashboard_fragment_logins, null)
                                    fragmentRoot.addView(fragmentView)
                                    loginsRecycler = fragmentView.findViewById(R.id.logins_recycler)
                                    loginsRecycler.layoutManager = LinearLayoutManager(this@Dashboard)
                                    val adapter = LoginsAdapter(searchTermsList)
                                    adapter.setHasStableIds(true)
                                    loginsRecycler.adapter = adapter
                                    loginsRecycler.setItemViewCacheSize(50);
                                    if (coldStart) loginsRecycler.layoutAnimation = loadLayoutAnimation(applicationContext, R.anim.slide_right_anim_controller)
                                    LinearLayoutManager(applicationContext).apply { isAutoMeasureEnabled = false }
                                    loginsRecycler.recycledViewPool.setMaxRecycledViews(0, 0)
                                    adapter.notifyItemInserted(notes.size)
                                    loginsRecycler.isNestedScrollingEnabled = false
                                    loginsRecycler.scheduleLayoutAnimation()

                                    loginsRecycler.setListener(object : SwipeLeftRightCallback.Listener {
                                        override fun onSwipedLeft(position: Int) {  // Edit login
                                            crypto.secureStartActivity (
                                                nextActivity = AddLogin(),
                                                nextActivityClassNameAsString = getString(R.string.title_activity_add_login),
                                                keyring = keyring,
                                                itemId = logins.elementAt(position).id
                                            )
                                        }

                                        override fun onSwipedRight(position: Int) {  // Copy password
                                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                            if (logins.elementAt(position).loginData?.password.isNullOrEmpty()) {
                                                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this@Dashboard).create()
                                                alertDialog.setTitle("No password")
                                                alertDialog.setMessage("This login has no password.")
                                                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Got it") { _, _ -> alertDialog.dismiss() }
                                                alertDialog.show()
                                            } else {
                                                val clip = ClipData.newPlainText("Keyspace", logins.elementAt(position).loginData?.password)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(applicationContext, "Password copied!", Toast.LENGTH_LONG).show()
                                            }
                                            adapter.notifyItemChanged(position)
                                        }
                                    })

                                    loginsScrollView = fragmentView.findViewById(R.id.logins_scrollview)

                                    loginsScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                                        if (scrollY > oldScrollY + 12) {
                                            fab.hide()
                                            bottomSheet.visibility = View.GONE
                                            bottomSheet.animate().scaleY(0.0f)
                                            topBar.animate().translationY(-350f)
                                        }

                                        if (scrollY < oldScrollY - 5) {
                                            fab.show()
                                            bottomSheet.visibility = View.VISIBLE
                                            bottomSheet.animate().scaleY(1.0f)
                                            topBar.animate().translationY(0f)
                                        }

                                        if (scrollY == 0) {
                                            fab.show()
                                            bottomSheet.visibility = View.VISIBLE
                                            bottomSheet.animate().scaleY(1.0f)
                                            topBar.animate().translationY(0f)
                                        }
                                    })

                                } else {
                                    fragmentRoot.removeAllViews()
                                    fragmentView = inflater.inflate(R.layout.no_search_results, null)
                                    fragmentRoot.addView(fragmentView)
                                }
                            } catch (_: UninitializedPropertyAccessException) { }

                        } else if (searchType.contains(io.TYPE_NOTE)) {

                            val searchTermsList: MutableList<IOUtilities.Note> = mutableListOf()

                            for (note in notes) {
                                val noteSearchableData = mutableListOf<String?>()
                                noteSearchableData.add(note.notes)
                                noteSearchableData.add(note.color)
                                val noteSearchableDataString = noteSearchableData.filterNotNull().joinToString("").lowercase(Locale.getDefault()).filter { it.isLetterOrDigit() }

                                if (noteSearchableDataString.contains(searchTerms.toString().lowercase(Locale.getDefault()).filter { it.isLetterOrDigit() })) {  // other search
                                    searchTermsList.add(note)
                                }

                                for (tag in tags) {
                                    if (searchTerms.toString().lowercase(Locale.getDefault()) in tag.name.lowercase(Locale.getDefault())) {
                                        if (note.tagId == tag.id) searchTermsList.add (note)
                                    }
                                }

                            }

                            try {
                                if (searchTermsList.isNotEmpty()) {
                                    fragmentRoot.removeAllViews()
                                    fragmentView = inflater.inflate(R.layout.dashboard_fragment_notes, null)
                                    fragmentRoot.addView(fragmentView)
                                    notesRecycler = fragmentView.findViewById(R.id.notes_recycler)

                                    if (configData.getBoolean("notesGrid", true)) notesRecycler.layoutManager = StaggeredGridLayoutManager(2, LinearLayoutManager.VERTICAL)
                                    else notesRecycler.layoutManager = LinearLayoutManager(this@Dashboard)

                                    val adapter = NotesAdapter(searchTermsList)
                                    adapter.setHasStableIds(true)
                                    notesRecycler.adapter = adapter
                                    notesRecycler.setItemViewCacheSize(50)
                                    if (coldStart) notesRecycler.layoutAnimation = loadLayoutAnimation(applicationContext, R.anim.slide_right_anim_controller)
                                    LinearLayoutManager(applicationContext).apply { isAutoMeasureEnabled = false }
                                    notesRecycler.recycledViewPool.setMaxRecycledViews(0, 0)
                                    adapter.notifyItemInserted(notes.size)
                                    notesRecycler.isNestedScrollingEnabled = false
                                    notesRecycler.scheduleLayoutAnimation()

                                    notesRecycler.setListener(object : SwipeLeftRightCallback.Listener {
                                        override fun onSwipedLeft(position: Int) {  // Edit login
                                            crypto.secureStartActivity (
                                                nextActivity = AddNote(),
                                                nextActivityClassNameAsString = getString(R.string.title_activity_add_note),
                                                keyring = keyring,
                                                itemId = notes.elementAt(position).id
                                            )
                                        }

                                        override fun onSwipedRight(position: Int) {  // Copy password
                                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                            if (notes.elementAt(position).notes.isNullOrEmpty()) {
                                                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this@Dashboard).create()
                                                alertDialog.setTitle("No text")
                                                alertDialog.setMessage("This note has no text.")
                                                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Got it") { _, _ -> alertDialog.dismiss() }
                                                alertDialog.show()
                                            } else {
                                                val clip = ClipData.newPlainText("Keyspace", notes.elementAt(position).notes)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(applicationContext, "Note copied!", Toast.LENGTH_LONG).show()
                                            }
                                            adapter.notifyItemChanged(position)
                                        }

                                    })

                                    notesScrollView = fragmentView.findViewById(R.id.notes_scrollview)

                                    notesScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                                        if (scrollY > oldScrollY + 5) {
                                            fab.hide()
                                            bottomSheet.visibility = View.GONE
                                            bottomSheet.animate().scaleY(0.0f)
                                            topBar.animate().translationY(-350f)
                                        }

                                        if (scrollY < oldScrollY - 5) {
                                            fab.show()
                                            bottomSheet.visibility = View.VISIBLE
                                            bottomSheet.animate().scaleY(1.0f)
                                            topBar.animate().translationY(0f)
                                        }

                                        if (scrollY == 0) {
                                            fab.show()
                                            bottomSheet.visibility = View.VISIBLE
                                            bottomSheet.animate().scaleY(1.0f)
                                            topBar.animate().translationY(0f)
                                        }
                                    })

                                } else {
                                    fragmentRoot.removeAllViews()
                                    fragmentView = inflater.inflate(R.layout.no_search_results, null)
                                    fragmentRoot.addView(fragmentView)
                                }
                            } catch (_: UninitializedPropertyAccessException) { }

                        } else if (searchType.contains(io.TYPE_CARD)) {

                            val searchTermsList: MutableList<IOUtilities.Card> = mutableListOf()

                            for (card in cards) {
                                val cardSearchableData = mutableListOf<String?>()
                                cardSearchableData.add(card.notes)
                                cardSearchableData.add(card.color)
                                cardSearchableData.add(card.cardNumber)
                                cardSearchableData.add(card.iconFile)
                                cardSearchableData.add(card.cardholderName)
                                cardSearchableData.add(card.name)
                                cardSearchableData.add(card.expiry)
                                val cardSearchableDataString = cardSearchableData.filterNotNull().joinToString("").lowercase(Locale.getDefault()).filter { it.isLetterOrDigit() }

                                if (cardSearchableDataString.contains(searchTerms.toString().lowercase(Locale.getDefault()).filter { it.isLetterOrDigit() })) { // other search
                                    searchTermsList.add(card)
                                }

                                for (tag in tags) {
                                    if (searchTerms.toString().lowercase(Locale.getDefault()) in tag.name.lowercase(Locale.getDefault())) {
                                        if (card.tagId == tag.id) searchTermsList.add (card)
                                    }
                                }

                            }

                            try {
                                if (searchTermsList.isNotEmpty()) {
                                    fragmentRoot.removeAllViews()
                                    fragmentView = inflater.inflate(R.layout.dashboard_fragment_cards, null)
                                    fragmentRoot.addView(fragmentView)
                                    cardsRecycler = fragmentView.findViewById(R.id.cards_recycler)
                                    cardsRecycler.layoutManager = LinearLayoutManager(this@Dashboard)
                                    val adapter = CardsAdapter(searchTermsList)
                                    adapter.setHasStableIds(true)
                                    cardsRecycler.adapter = adapter
                                    cardsRecycler.setItemViewCacheSize(50)
                                    if (coldStart) cardsRecycler.layoutAnimation = loadLayoutAnimation(applicationContext, R.anim.slide_right_anim_controller)
                                    LinearLayoutManager(applicationContext).apply { isAutoMeasureEnabled = false }
                                    cardsRecycler.recycledViewPool.setMaxRecycledViews(0, 0)
                                    adapter.notifyItemInserted(cards.size)
                                    cardsRecycler.isNestedScrollingEnabled = false
                                    cardsRecycler.scheduleLayoutAnimation()

                                    cardsScrollView = fragmentView.findViewById(R.id.cards_scrollview)

                                    cardsScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                                        if (scrollY > oldScrollY + 5) {
                                            fab.hide()
                                            bottomSheet.visibility = View.GONE
                                            bottomSheet.animate().scaleY(0.0f)
                                            topBar.animate().translationY(-350f)
                                        }

                                        if (scrollY < oldScrollY - 5) {
                                            fab.show()
                                            bottomSheet.visibility = View.VISIBLE
                                            bottomSheet.animate().scaleY(1.0f)
                                            topBar.animate().translationY(0f)
                                        }

                                        if (scrollY == 0) {
                                            fab.show()
                                            bottomSheet.visibility = View.VISIBLE
                                            bottomSheet.animate().scaleY(1.0f)
                                            topBar.animate().translationY(0f)
                                        }
                                    })

                                } else {
                                    fragmentRoot.removeAllViews()
                                    fragmentView = inflater.inflate(R.layout.no_search_results, null)
                                    fragmentRoot.addView(fragmentView)

                                }
                            } catch (_: UninitializedPropertyAccessException) { }

                        }

                }

                override fun afterTextChanged (editable: Editable) { }

            })

            searchButton.setOnClickListener {
                if (!alreadyAnimated) {
                    searchButton.setImageDrawable(closeIcon)
                    closeIcon!!.start()
                    searchBar.isFocusableInTouchMode = true
                    searchBar.requestFocusFromTouch()
                    input.showSoftInput(searchBar, 0)
                    alreadyAnimated = true

                } else {

                    searchButton.setImageDrawable(searchIcon)
                    searchIcon!!.start()
                    searchBar.setText("")
                    searchBar.clearFocus()
                    input.hideSoftInputFromWindow(searchBar.windowToken, 0)
                    alreadyAnimated = false

                }
            }

            root.setOnClickListener {
                searchBar.clearFocus()
                input.hideSoftInputFromWindow(searchBar.windowToken, 0)
            }

        } catch (noRecyclerSet: UninitializedPropertyAccessException) { }

    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (searchBar.isFocused) {
                val outRect = Rect()
                searchBar.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {

                    Handler(Looper.getMainLooper()).postDelayed({ runOnUiThread {
                        searchBar.clearFocus()
                        input.hideSoftInputFromWindow(searchBar.windowToken, 0)

                        if (searchBar.text.isEmpty()) {
                            val searchIcon = getDrawable(R.drawable.closetosearch) as AnimatedVectorDrawable?
                            searchButton.setImageDrawable(searchIcon)
                            searchIcon!!.start()
                            alreadyAnimated = false
                            fab.show()

                            when (lastFragment) {
                                io.TYPE_LOGIN -> {
                                    renderLoginsFragment()
                                }
                                io.TYPE_NOTE -> {
                                    renderNotesFragment()
                                }
                                io.TYPE_CARD -> {
                                    renderCardsFragment()
                                }
                            }
                        }
                    } }, 100)

                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    /*
    * logins fragment with view rendering and swipeable cards
    * */

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
            }

            if (!login.loginData?.username.isNullOrEmpty()) {
                loginCard.usernameText.text = login.loginData!!.username
                loginCard.usernameText.setCompoundDrawablesRelativeWithIntrinsicBounds (loginIcon, null, null, null)
            }

            if (login.loginData?.username.isNullOrEmpty() && login.loginData?.email.isNullOrEmpty()) loginCard.usernameText.visibility = View.GONE

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
                        runOnUiThread { loginCard.mfaText.text = otpCode!!.replace("...".toRegex(), "$0 ") }
                        mfaCodesTimer.scheduleAtFixedRate(object : TimerTask() {
                            override fun run() {
                                val currentSeconds = SimpleDateFormat("s", Locale.getDefault()).format(Date()).toInt()
                                var halfMinuteElapsed = abs((60-currentSeconds))
                                if (halfMinuteElapsed >= 30) halfMinuteElapsed -= 30
                                try { loginCard.mfaProgress.progress = halfMinuteElapsed } catch (_: Exception) {  }
                                if (halfMinuteElapsed == 29) {
                                    otpCode = GoogleAuthenticator(base32secret = login.loginData.totp.secret.toString()).generate()
                                    runOnUiThread { loginCard.mfaText.text = otpCode!!.replace("...".toRegex(), "$0 ") }
                                }
                            }
                        }, 0, 1000) // 1000 milliseconds = 1 second
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

            // tap on card
            loginCard.loginInformation.setOnClickListener {
                renderMiniLoginDialog (login)
            }

            if (login.iconFile != null) loginCard.siteIcon.setImageDrawable(misc.getSiteIcon(login.iconFile, loginCard.siteName.currentTextColor))
            else loginCard.siteIcon.setImageDrawable(DrawableCompat.wrap(website))

        }

    }

    private fun renderLoginsFragment () {

        sortBy = configData.getString("sort_by", io.SORT_LAST_EDITED)!!
        vault = io.vaultSorter(vault, sortBy)

        renderTopBar(io.TYPE_LOGIN)
        fab.setOnClickListener {
            crypto.secureStartActivity (
                nextActivity = AddLogin(),
                nextActivityClassNameAsString = getString(R.string.title_activity_add_login),
                keyring = keyring,
                itemId = null
            )
        }

        killBottomSheet()

        if (vault.login.isNullOrEmpty()) {
            try { fragmentRoot.removeView(fragmentView) } catch (uninflated: UninitializedPropertyAccessException) { }
            fragmentView = inflater.inflate(R.layout.no_vault_data, null)
            fragmentRoot.addView(fragmentView)
            if (coldStart) fragmentView.startAnimation(loadAnimation(applicationContext, android.R.anim.fade_in));

        } else {
            try { fragmentRoot.removeView(fragmentView) } catch (uninflated: UninitializedPropertyAccessException) { }
            fragmentView = inflater.inflate(R.layout.dashboard_fragment_logins, null)
            fragmentRoot.addView(fragmentView)

            logins.clear()
            for (encryptedLogin in io.getLogins(vault))
                logins.add(io.decryptLogin(encryptedLogin))

            loginsRecycler = fragmentView.findViewById(R.id.logins_recycler)
            loginsRecycler.layoutManager = LinearLayoutManager(this@Dashboard)

            val adapter = LoginsAdapter(logins)
            adapter.setHasStableIds(true)
            loginsRecycler.adapter = adapter
            loginsRecycler.setItemViewCacheSize(50);
            if (coldStart) loginsRecycler.layoutAnimation = loadLayoutAnimation(applicationContext, R.anim.slide_right_anim_controller)
            LinearLayoutManager(applicationContext).apply { isAutoMeasureEnabled = false }
            loginsRecycler.recycledViewPool.setMaxRecycledViews(0, 0)
            adapter.notifyItemInserted(notes.size)
            loginsRecycler.isNestedScrollingEnabled = false
            loginsRecycler.scheduleLayoutAnimation()

            loginsRecycler.setListener(object : SwipeLeftRightCallback.Listener {
                override fun onSwipedLeft(position: Int) {  // Edit login
                    crypto.secureStartActivity (
                        nextActivity = AddLogin(),
                        nextActivityClassNameAsString = getString(R.string.title_activity_add_login),
                        keyring = keyring,
                        itemId = logins.elementAt(position).id
                    )
                }

                override fun onSwipedRight(position: Int) {  // Copy password
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    if (logins.elementAt(position).loginData?.password.isNullOrEmpty()) {
                        val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this@Dashboard).create()
                        alertDialog.setTitle("No password")
                        alertDialog.setMessage("This login has no password.")
                        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Got it") { _, _ -> alertDialog.dismiss() }
                        alertDialog.show()
                    } else {
                        val clip = ClipData.newPlainText("Keyspace", logins.elementAt(position).loginData?.password)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(applicationContext, "Password copied!", Toast.LENGTH_LONG).show()
                    }
                    adapter.notifyItemChanged(position)
                }
            })

            loginsScrollView = fragmentView.findViewById(R.id.logins_scrollview)

            loginsScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                if (scrollY > oldScrollY + 5) {
                    fab.hide()
                    bottomSheet.visibility = View.GONE
                    bottomSheet.animate().scaleY(0.0f)
                    topBar.animate().translationY(-350f)
                }

                if (scrollY < oldScrollY - 5) {
                    fab.show()
                    bottomSheet.visibility = View.VISIBLE
                    bottomSheet.animate().scaleY(1.0f)
                    topBar.animate().translationY(0f)
                }

                if (scrollY == 0) {
                    fab.show()
                    bottomSheet.visibility = View.VISIBLE
                    bottomSheet.animate().scaleY(1.0f)
                    topBar.animate().translationY(0f)
                }
            })

        }
    }

    private fun renderMiniLoginDialog (login: IOUtilities.Login) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val dialogView: View = layoutInflater.inflate (R.layout.mini_login_dialog, null)

        dialogView.startAnimation(loadAnimation(applicationContext, R.anim.from_bottom))

        val builder = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)

        val miniLoginDialog = builder.create()
        miniLoginDialog.show()

        val closeButton = dialogView.findViewById<View>(R.id.closeButton) as ImageView

        val favoriteBadge = dialogView.findViewById<View>(R.id.favoriteBadge) as ImageView
        val siteLogo = dialogView.findViewById<View>(R.id.siteLogo) as ImageView
        val loginName = dialogView.findViewById<View>(R.id.loginName) as TextView
        val loginEmail = dialogView.findViewById<View>(R.id.loginEmail) as TextView
        val loginUsername = dialogView.findViewById<View>(R.id.loginUsername) as TextView
        val loginPassword = dialogView.findViewById<View>(R.id.loginPassword) as TextView
        val loginPasswordLayout = dialogView.findViewById<LinearLayout>(R.id.loginPasswordLayout) as LinearLayout
        val copyloginPassword = dialogView.findViewById<ImageView>(R.id.copyloginPassword) as MaterialButton
        val hideloginPassword = dialogView.findViewById<ImageView>(R.id.hideloginPassword) as ImageView

        val mfaLayout = dialogView.findViewById(R.id.mfa) as LinearLayout
        val mfaProgress = dialogView.findViewById(R.id.mfaProgress) as CircularProgressIndicator
        val mfaText = dialogView.findViewById(R.id.mfaText) as TextView

        val loginNotes = dialogView.findViewById<View>(R.id.loginNotes) as TextView
        val loginTag = dialogView.findViewById<View>(R.id.loginTag) as TextView
        val loginUsageCount = dialogView.findViewById<View>(R.id.usageCount) as TextView
        val dateCreated = dialogView.findViewById<View>(R.id.dateCreated) as TextView

        val passwordHistoryButton = dialogView.findViewById<View>(R.id.passwordHistoryButton) as Button
        val editButton = dialogView.findViewById<View>(R.id.editButton) as Button
        val backupCodesButton = dialogView.findViewById<View>(R.id.backupCodesButton) as Button

        closeButton.setOnClickListener {
            miniLoginDialog.dismiss()
        }

        closeButton.setColorFilter(loginEmail.currentTextColor)

        loginName.isSelected = true
        loginEmail.isSelected = true
        loginUsername.isSelected = true

        if (login.favorite) {
            favoriteBadge.visibility = View.VISIBLE
            favoriteBadge.setColorFilter(loginEmail.currentTextColor)
        } else favoriteBadge.visibility = View.INVISIBLE

        if (login.iconFile != null) siteLogo.setImageDrawable(misc.getSiteIcon(login.iconFile, loginName.currentTextColor))
        else siteLogo.setImageDrawable(getDrawable(R.drawable.ic_baseline_website_24))

        siteLogo.setColorFilter(loginName.currentTextColor)
        if (!login.name.isNullOrEmpty()) loginName.text = login.name else loginName.visibility = View.GONE
        loginName.setOnClickListener {
            val clip = ClipData.newPlainText("Keyspace", login.name)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext, "Copied login name!", Toast.LENGTH_SHORT).show()
        }

        if (!login.loginData?.email.isNullOrEmpty())  loginEmail.text = login.loginData?.email else loginEmail.visibility = View.GONE
        loginEmail.setOnClickListener {
           val clip = ClipData.newPlainText("Keyspace", login.loginData?.email)
           clipboard.setPrimaryClip(clip)
           Toast.makeText(applicationContext, "Copied email!", Toast.LENGTH_SHORT).show()
        }

        if (!login.loginData?.username.isNullOrEmpty()) loginUsername.text = login.loginData?.username else loginUsername.visibility = View.GONE
        loginUsername.setOnClickListener {
            val clip = ClipData.newPlainText("Keyspace", login.loginData?.username)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext, "Copied username!", Toast.LENGTH_SHORT).show()
        }

        if (!login.loginData?.password.isNullOrEmpty()) {
            var visible = false
            loginPassword.text = "●●●●●●●●●●●● "
            hideloginPassword.setOnClickListener {
                if (!visible) {
                    visible = true
                    loginPassword.text = login.loginData?.password
                    hideloginPassword.setImageDrawable (ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_visibility_24))
                } else {
                    visible = false
                    loginPassword.text = "●●●●●●●●●●●● "
                    hideloginPassword.setImageDrawable (ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_visibility_off_24))
                }
            }

            copyloginPassword.setOnClickListener {
                val clip = ClipData.newPlainText("Keyspace", login.loginData?.password)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(applicationContext, "Copied password!", Toast.LENGTH_SHORT).show()
            }

        } else {
            copyloginPassword.visibility = View.GONE
            loginPasswordLayout.visibility = View.GONE
        }

        var otpCode: String? = null
        if (!login.loginData?.totp?.secret.isNullOrEmpty()) {
            if (login.loginData?.totp?.secret?.length!! > 6) {
                try {
                    otpCode = GoogleAuthenticator(base32secret = login.loginData.totp.secret).generate()
                    runOnUiThread { mfaText.text = otpCode!!.replace("...".toRegex(), "$0 ") }
                    mfaCodesTimer.scheduleAtFixedRate(object : TimerTask() {
                        override fun run() {
                            val currentSeconds = SimpleDateFormat("s", Locale.getDefault()).format(Date()).toInt()
                            try {
                                val currentSeconds = SimpleDateFormat("s", Locale.getDefault()).format(Date()).toInt()
                                var halfMinuteElapsed = abs((60-currentSeconds))
                                if (halfMinuteElapsed >= 30) halfMinuteElapsed -= 30
                                try { mfaProgress.progress = halfMinuteElapsed } catch (_: Exception) {  }
                                if (halfMinuteElapsed == 29) {
                                    otpCode = GoogleAuthenticator(base32secret = login.loginData.totp.secret.toString()).generate()
                                    runOnUiThread { mfaText.text = otpCode!!.replace("...".toRegex(), "$0 ") }
                                }
                            } catch (no2faData: Exception) { when (no2faData) {is IllegalArgumentException, is NullPointerException -> {} } }
                        }
                    }, 0, 1000) // 1000 milliseconds = 1 second
                } catch (timerError: IllegalStateException) { }

                mfaText.setOnClickListener {
                    val clip = ClipData.newPlainText("Keyspace", otpCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(applicationContext, "Copied code!", Toast.LENGTH_SHORT).show()
                }
            } else { mfaLayout.visibility = View.GONE }
        } else { mfaLayout.visibility = View.GONE }

        if (!login.notes.isNullOrEmpty()) {
            loginNotes.text = login.notes
            loginNotes.setOnClickListener {
                val clip = ClipData.newPlainText("Keyspace", login.notes)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(applicationContext, "Copied note!", Toast.LENGTH_SHORT).show()
            }
        } else loginNotes.visibility = View.GONE

        if (login.dateCreated != null || login.dateCreated != 0L) {
            val calendar = Calendar.getInstance(Locale.getDefault())
            calendar.timeInMillis = login.dateCreated?.times(1000L)!!

            dateCreated.text = "Created on\n" + DateFormat.format("MMM dd, yyyy ⋅ hh:mm a", calendar).toString()

        } else dateCreated.visibility = View.GONE

        loginTag.visibility = View.GONE
        if (tags.size > 0) {
            for (tag in tags) {
                if (login.tagId == tag.id) {
                    loginTag.visibility = View.VISIBLE
                    loginTag.text = tag.name
                    try {
                        if (!tag.color.isNullOrEmpty()) {
                            val tagIcon = DrawableCompat.wrap(getDrawable(R.drawable.ic_baseline_circle_24)!!)
                            DrawableCompat.setTint (tagIcon, Color.parseColor(tag.color))
                            DrawableCompat.setTintMode (tagIcon, PorterDuff.Mode.SRC_IN)
                            loginTag.setCompoundDrawablesWithIntrinsicBounds (tagIcon, null, null, null)
                        }
                    } catch (_: StringIndexOutOfBoundsException) {} catch (_: IllegalArgumentException) { }
                    break
                }
            }
        }

        editButton.setOnClickListener {
            crypto.secureStartActivity (
                nextActivity = AddLogin(),
                nextActivityClassNameAsString = getString(R.string.title_activity_add_login),
                keyring = keyring,
                itemId = login.id
            )
            miniLoginDialog.dismiss()
        }

        if (login.loginData?.totp?.backupCodes != null) {
            if (login.loginData.totp.backupCodes.isNotEmpty()) {
                if (login.loginData.totp.backupCodes.toString().length > 2) {
                    backupCodesButton.setOnClickListener {
                        val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
                        alertDialog.setTitle("Backup codes")
                        alertDialog.setMessage(login.loginData!!.totp!!.backupCodes!!.joinToString("\n\n"))

                        alertDialog.setCancelable(true)
                        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Go back") { _, _ -> alertDialog.dismiss() }
                        alertDialog.show()

                        val codesTextView: TextView = alertDialog.findViewById(android.R.id.message)!!
                        codesTextView.setTextIsSelectable(true)
                        codesTextView.textSize = 22F
                    }
                } else backupCodesButton.visibility = View.GONE
            } else backupCodesButton.visibility = View.GONE
        } else backupCodesButton.visibility = View.GONE


        val passwordHistoryData = login.loginData?.passwordHistory
        if (passwordHistoryData != null) {
            class PasswordHistoryAdapter (private val oldPasswords: MutableList<IOUtilities.Password>) : RecyclerView.Adapter<PasswordHistoryAdapter.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {  // create new views
                    val passwordHistoryView: View = LayoutInflater.from(parent.context).inflate(R.layout.password_history_card, parent, false)
                    passwordHistoryView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
                    return ViewHolder(passwordHistoryView)
                }

                override fun onBindViewHolder(passwordHistoryView: ViewHolder, position: Int) {
                    val passwordHistory = oldPasswords[passwordHistoryView.adapterPosition]

                    val calendar = Calendar.getInstance(Locale.getDefault())
                    calendar.timeInMillis = passwordHistory.created * 1000L
                    val date = DateFormat.format("MMM dd, yyyy",calendar).toString()
                    val time = DateFormat.format("HH:mm",calendar).toString()

                    passwordHistoryView.oldPassword.text = passwordHistory.password
                    passwordHistoryView.createdDate.text = date
                    passwordHistoryView.createdTime.text = time

                    passwordHistoryView.copyOldPasswordButton.setOnClickListener { view ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Keyspace", passwordHistoryView.oldPassword.text.toString())
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(applicationContext, "Copied!", Toast.LENGTH_SHORT).show()
                    }

                }

                override fun getItemCount(): Int {
                    return oldPasswords.size
                }

                inner class ViewHolder (itemLayoutView: View) : RecyclerView.ViewHolder(itemLayoutView) {
                    var oldPassword: TextView = itemLayoutView.findViewById<View>(R.id.oldPassword) as TextView
                    var copyOldPasswordButton: Button = itemLayoutView.findViewById<View>(R.id.copyOldPasswordButton) as Button
                    var createdDate: TextView = itemLayoutView.findViewById<View>(R.id.createdDate) as TextView
                    var createdTime: TextView = itemLayoutView.findViewById<View>(R.id.createdTime) as TextView
                }
            }

            passwordHistoryButton.visibility = View.VISIBLE

            passwordHistoryButton.setOnClickListener {
                val inflater = layoutInflater
                val builder = MaterialAlertDialogBuilder(this)
                builder.setTitle("Password history")
                builder.setCancelable(true)

                val passwordHistoryBox: View = inflater.inflate(R.layout.password_history, null)
                builder.setView(passwordHistoryBox)

                passwordHistoryBox.startAnimation(loadAnimation(applicationContext, R.anim.from_top))

                val passwordHistoryView: RecyclerView = passwordHistoryBox.findViewById(R.id.passwordHistoryRecycler) as RecyclerView
                passwordHistoryView.layoutManager = LinearLayoutManager(this)

                val passwordHistoryAdapter = PasswordHistoryAdapter (passwordHistoryData)
                passwordHistoryView.adapter = passwordHistoryAdapter
                //passwordHistoryView.layoutAnimation = loadLayoutAnimation(applicationContext, R.anim.slide_right_anim_controller)
                passwordHistoryAdapter.notifyItemInserted(passwordHistoryData.size)
                passwordHistoryView.invalidate()
                passwordHistoryView.refreshDrawableState()
                passwordHistoryView.scheduleLayoutAnimation()

                val dialog = builder.create()
                dialog.show()
            }

        } else passwordHistoryButton.visibility = View.GONE

        if (login.frequencyAccessed!! > 0) loginUsageCount.text = "Used ${login.frequencyAccessed} times" else loginUsageCount.visibility = View.GONE

    }

    /*
    * Notes fragment with view rendering and swipeable cards
    * */

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

                val theme: Theme = when (applicationContext.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
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

                markdownProcessor = MarkdownProcessor(this@Dashboard)
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

            // misc icon data
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
            if (tags?.size!! > 0) {
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
        }
    }

   /*
    * Cards fragment with view rendering and swipeable cards
    * */

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

            val cardsCardFront: CardView = itemView.findViewById(R.id.CardsCardFront)
            val cardsCardBack: CardView = itemView.findViewById(R.id.CardsCardBack)
            val cardsCardFrontLayout: ConstraintLayout = itemView.findViewById(R.id.CardsCardFrontLayout)
            val cardsCardBackLayout: ConstraintLayout = itemView.findViewById(R.id.CardsCardBackLayout)
            val cardsCardLayout: ConstraintLayout = itemView.findViewById(R.id.CardsCardLayout)

            val bankNameFront: TextView = itemView.findViewById(R.id.bankNameFront)
            val bankLogoFront: ImageView = itemView.findViewById(R.id.bankLogoFront)
            val rfidIcon: ImageView = itemView.findViewById(R.id.RfidIcon)
            val cardNumber: TextView = itemView.findViewById(R.id.CardNumber)
            val toDate: TextView = itemView.findViewById(R.id.toDate)
            val toLabel: TextView = itemView.findViewById(R.id.toLabel)
            val cardHolder: TextView = itemView.findViewById(R.id.CardHolder)
            val paymentGateway: ImageView = itemView.findViewById(R.id.PaymentGateway)

            val securityCode: TextView = itemView.findViewById(R.id.SecurityCode)
            val securityCodeLabel: TextView = itemView.findViewById(R.id.SecurityCodeLabel)
            val pin: TextView = itemView.findViewById(R.id.Pin)
            val pinLabel: TextView = itemView.findViewById(R.id.PinLabel)
            val pinLayout: LinearLayout = itemView.findViewById(R.id.PinLayout)
            val hideCodes: ImageView = itemView.findViewById(R.id.hideCodes)
            val cardNotes: TextView = itemView.findViewById(R.id.CardObverseNotes)
            val bankNameBack: TextView = itemView.findViewById(R.id.bankNameBack)
            val bankLogoBack: ImageView = itemView.findViewById(R.id.bankLogoBack)
            val magstripe: View = itemView.findViewById(R.id.magstripe)

            val editButton: MaterialButton = itemView.findViewById(R.id.editButton)

            val tagText: TextView = itemView.findViewById(R.id.tagText)
            val miscText: TextView = itemView.findViewById(R.id.miscText)

            init {

                rfidIcon.invalidate()
                rfidIcon.refreshDrawableState()

                cardsCardFront.cameraDistance = flipDistance
                cardsCardBack.cameraDistance = flipDistance

                setRightOut = AnimatorInflater.loadAnimator(applicationContext, R.anim.flip2) as AnimatorSet
                setLeftIn = AnimatorInflater.loadAnimator(applicationContext, R.anim.flip1) as AnimatorSet

                clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

                animatedContactless = getDrawable(R.drawable.lessdistractingflickercontactless)?.mutate() as AnimatedVectorDrawable

                val currentCardElevation = cardsCardBack.cardElevation
                var isBackVisible = false

                fun cardFlip () {
                    setLeftIn.addListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {
                            cardsCardLayout.isEnabled = false
                            cardsCardFront.cardElevation = 0f
                            cardsCardBack.cardElevation = 0f
                        }
                        override fun onAnimationRepeat(animation: Animator) {}
                        override fun onAnimationEnd(animation: Animator) {
                            cardsCardLayout.isEnabled = true
                            cardsCardFront.cardElevation = currentCardElevation
                            cardsCardBack.cardElevation = currentCardElevation
                        }
                        override fun onAnimationCancel(animation: Animator) { }
                    })

                    setRightOut.addListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {
                            cardsCardLayout.isEnabled = false
                            cardsCardFront.cardElevation = 0f
                            cardsCardBack.cardElevation = 0f
                        }
                        override fun onAnimationRepeat(animation: Animator) {}
                        override fun onAnimationEnd(animation: Animator) {
                            cardsCardLayout.isEnabled = true
                            cardsCardFront.cardElevation = currentCardElevation
                            cardsCardBack.cardElevation = currentCardElevation
                        }
                        override fun onAnimationCancel(animation: Animator) { }
                    })

                    if (!isBackVisible) {
                        editButton.isEnabled = true
                        hideCodes.isEnabled = true
                        securityCode.isEnabled = true
                        pin.isEnabled = true
                        bankNameBack.isEnabled = true
                        cardNotes.isEnabled = true

                        cardNumber.isEnabled = false
                        cardHolder.isEnabled = false
                        toDate.isEnabled = false
                        bankNameFront.isEnabled = false
                        editButton.isClickable = true
                        hideCodes.isClickable = true
                        securityCode.isClickable = true
                        pin.isClickable = true
                        bankNameBack.isClickable = true
                        cardNotes.isClickable = true
                        cardNumber.isClickable = false
                        cardHolder.isClickable = false
                        toDate.isClickable = false
                        bankNameFront.isClickable = false

                        setRightOut.setTarget(cardsCardFront)
                        setLeftIn.setTarget(cardsCardBack)
                        setRightOut.start()
                        setLeftIn.start()
                        isBackVisible = true

                    } else {

                        editButton.isEnabled = false
                        hideCodes.isEnabled = false
                        securityCode.isEnabled = false
                        pin.isEnabled = false
                        bankNameBack.isEnabled = false
                        cardNotes.isEnabled = false

                        cardNumber.isEnabled = true
                        cardHolder.isEnabled = true
                        toDate.isEnabled = true
                        bankNameFront.isEnabled = true

                        editButton.isClickable = false
                        securityCode.isClickable = false
                        hideCodes.isClickable = false
                        pin.isClickable = false
                        bankNameBack.isClickable = false
                        cardNotes.isClickable = false

                        cardNumber.isClickable = true
                        cardHolder.isClickable = true
                        toDate.isClickable = true
                        bankNameFront.isClickable = true

                        rfidIcon.setImageDrawable(animatedContactless)
                        animatedContactless.start()

                        setRightOut.setTarget(cardsCardBack)
                        setLeftIn.setTarget(cardsCardFront)
                        setRightOut.start()
                        setLeftIn.start()
                        isBackVisible = false

                    }
                }

                cardsCardLayout.setOnTouchListener(object : MiscUtilities.OnSwipeTouchListener(this@Dashboard) {
                    override fun onClick() {
                        cardFlip()
                        super.onClick()
                    }

                    override fun onSwipeRight() {
                        cardFlip()
                        super.onSwipeRight()
                    }

                    override fun onLongClick() {
                        cardFlip()
                        super.onSwipeRight()
                    }

                })

            }

        }

        @SuppressLint("ClickableViewAccessibility", "ResourceType", "UseCompatLoadingForDrawables")
        fun bindData (cardCard: ViewHolder) {
            val card = cards[cardCard.adapterPosition]

            cardCard.tagText.visibility = View.GONE

            thread {
                val expired = misc.checkIfCardExpired(card.expiry!!)
                runOnUiThread {
                    cardCard.miscText.visibility = View.VISIBLE
                    if (card.favorite) {
                        cardCard.miscText.visibility = View.VISIBLE
                        cardCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, getDrawable(R.drawable.ic_baseline_star_24), null)
                        cardCard.miscText.text = "Favorite"
                    } else if (expired != null) {
                        cardCard.miscText.visibility = View.VISIBLE
                        cardCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, getDrawable(R.drawable.ic_baseline_warning_24), null)
                        if (expired.contains("expired")) cardCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, getDrawable(R.drawable.ic_baseline_credit_card_off_24), null)
                        cardCard.miscText.text = misc.checkIfCardExpired(card.expiry!!)
                    } else {
                        cardCard.miscText.visibility = View.GONE
                    }
                }
            }

            cardCard.cardNumber.text = card.cardNumber?.replace("....".toRegex(), "$0 ")
            cardCard.toDate.text = card.expiry
            cardCard.cardHolder.text = card.cardholderName

            cardCard.bankNameFront.text = card.name
            cardCard.bankNameBack.text = card.name

            cardCard.bankLogoFront.visibility = View.GONE
            cardCard.bankLogoBack.visibility = View.GONE

            if (!card.notes.isNullOrBlank()) cardCard.cardNotes.text = card.notes else cardCard.cardNotes.visibility = View.GONE


            val cardColor = card.color
            if (!card.color.isNullOrEmpty()) {
                cardCard.cardsCardFrontLayout.backgroundTintList = ColorStateList.valueOf(Color.parseColor(cardColor))
                cardCard.cardsCardBackLayout.backgroundTintList = ColorStateList.valueOf(Color.parseColor(cardColor))
            } else {
                cardCard.cardsCardFrontLayout.backgroundTintList = ColorStateList.valueOf(Color.DKGRAY)
                cardCard.cardsCardBackLayout.backgroundTintList = ColorStateList.valueOf(Color.DKGRAY)
            }

            val intColor: Int = try { cardCard.cardsCardFrontLayout.backgroundTintList?.defaultColor!! } catch (_: NullPointerException) { 0 }

            val r = intColor shr 16 and 0xFF; val g = intColor shr 8 and 0xFF; val b = intColor shr 0 and 0xFF
            if (g >= 200 || b >= 200) {
                cardCard.rfidIcon.setColorFilter(Color.BLACK)
                cardCard.cardNotes.setTextColor (Color.BLACK)
                cardCard.hideCodes.imageTintList = ColorStateList.valueOf(Color.BLACK)
                cardCard.pin.setTextColor (Color.BLACK)
                cardCard.pinLabel.setTextColor (Color.BLACK)
                cardCard.securityCode.setTextColor (Color.BLACK)
                cardCard.securityCodeLabel.setTextColor (Color.BLACK)
                cardCard.bankNameFront.setTextColor (Color.BLACK)
                cardCard.bankNameBack.setTextColor (Color.BLACK)
                cardCard.cardNotes.setTextColor (Color.BLACK)
                cardCard.cardHolder.setTextColor (Color.BLACK)
                cardCard.toDate.setTextColor (Color.BLACK)
                cardCard.toLabel.setTextColor (Color.BLACK)
                cardCard.cardNumber.setTextColor (Color.BLACK)
                cardCard.cardNumber.setTextColor (Color.BLACK)
                cardCard.miscText.setTextColor (Color.BLACK)
                cardCard.tagText.setTextColor (Color.BLACK)
                cardCard.tagText.foregroundTintList = ColorStateList.valueOf(Color.BLACK)
                cardCard.tagText.compoundDrawableTintList = ColorStateList.valueOf(Color.BLACK)
                cardCard.miscText.foregroundTintList = ColorStateList.valueOf(Color.BLACK)
                cardCard.miscText.compoundDrawableTintList = ColorStateList.valueOf(Color.BLACK)
                cardCard.rfidIcon.foregroundTintList = ColorStateList.valueOf(Color.BLACK)
                cardCard.cardNotes.foregroundTintList = ColorStateList.valueOf(Color.BLACK)
                cardCard.cardNotes.compoundDrawableTintList = ColorStateList.valueOf(Color.BLACK)
                cardCard.magstripe.backgroundTintList = ColorStateList.valueOf(Color.BLACK)
                cardCard.bankLogoBack.setColorFilter(Color.BLACK)
                cardCard.bankLogoFront.setColorFilter(Color.BLACK)
                cardCard.paymentGateway.setColorFilter(Color.BLACK)
                cardCard.editButton.backgroundTintList = ColorStateList.valueOf(Color.BLACK)
                cardCard.editButton.setTextColor(Color.WHITE)
                cardCard.editButton.setIconTintResource (R.color.white)
            } else {
                cardCard.rfidIcon.setColorFilter(Color.WHITE)
                cardCard.cardNotes.setTextColor (Color.WHITE)
                cardCard.hideCodes.imageTintList = ColorStateList.valueOf(Color.WHITE)
                cardCard.pin.setTextColor (Color.WHITE)
                cardCard.pinLabel.setTextColor (Color.WHITE)
                cardCard.securityCode.setTextColor (Color.WHITE)
                cardCard.securityCodeLabel.setTextColor (Color.WHITE)
                cardCard.bankNameFront.setTextColor (Color.WHITE)
                cardCard.bankNameBack.setTextColor (Color.WHITE)
                cardCard.cardNotes.setTextColor (Color.WHITE)
                cardCard.cardHolder .setTextColor (Color.WHITE)
                cardCard.toDate .setTextColor (Color.WHITE)
                cardCard.toLabel.setTextColor (Color.WHITE)
                cardCard.cardNumber.setTextColor (Color.WHITE)
                cardCard.cardNumber.setTextColor (Color.WHITE)
                cardCard.miscText.setTextColor (Color.WHITE)
                cardCard.tagText.setTextColor (Color.WHITE)
                cardCard.tagText.foregroundTintList = ColorStateList.valueOf(Color.WHITE)
                cardCard.tagText.compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
                cardCard.miscText.foregroundTintList = ColorStateList.valueOf(Color.WHITE)
                cardCard.miscText.compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
                cardCard.rfidIcon.foregroundTintList = ColorStateList.valueOf(Color.WHITE)
                cardCard.cardNotes.foregroundTintList = ColorStateList.valueOf(Color.WHITE)
                cardCard.cardNotes.compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
                cardCard.magstripe.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                cardCard.bankLogoBack.setColorFilter(Color.WHITE)
                cardCard.bankLogoFront.setColorFilter(Color.WHITE)
                cardCard.paymentGateway.setColorFilter(Color.WHITE)
                cardCard.editButton.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                cardCard.editButton.setTextColor(ColorStateList.valueOf(Color.BLACK))
                cardCard.editButton.setIconTintResource (R.color.black)
            }

            val paymentGateway = misc.getPaymentGateway(card.cardNumber.toString())
            var bankLogo = if (card.iconFile != null) misc.getSiteIcon(card.iconFile, cardCard.cardNumber.currentTextColor) else null

            var gatewayLogo = if (paymentGateway != null) misc.getSiteIcon(paymentGateway, cardCard.cardNumber.currentTextColor) else null

            thread {
                if (bankLogo != null && card.iconFile != "bank") {
                    runOnUiThread {
                        cardCard.bankLogoFront.visibility = View.VISIBLE
                        cardCard.bankLogoBack.visibility = View.VISIBLE
                        cardCard.bankLogoBack.setImageDrawable(DrawableCompat.wrap(bankLogo))
                        cardCard.bankLogoFront.setImageDrawable(DrawableCompat.wrap(bankLogo))
                    }
                } else {
                    cardCard.bankLogoFront.visibility = View.GONE
                    cardCard.bankLogoBack.visibility = View.GONE
                }

                if (paymentGateway != null) {
                    if (gatewayLogo != null) runOnUiThread { cardCard.paymentGateway.setImageDrawable(gatewayLogo) }
                    else cardCard.paymentGateway.visibility = View.GONE
                } else cardCard.paymentGateway.visibility = View.GONE

            }

            cardCard.tagText.visibility = View.GONE

            if (tags?.size!! > 0) {
                for (tag in tags) {
                    if (card.tagId == tag.id) {
                        runOnUiThread {
                            cardCard.tagText.visibility = View.VISIBLE
                            cardCard.tagText.text = tag?.name
                            try {
                                if (!tag!!.color.isNullOrEmpty()) cardCard.tagText.compoundDrawableTintList = ColorStateList.valueOf(Color.parseColor(tag.color))
                            } catch (_: StringIndexOutOfBoundsException) {} catch (_: IllegalArgumentException) {}
                        }
                        break
                    }
                }
            }

            cardCard.rfidIcon.visibility = View.GONE
            if (card.rfid == true) {
                cardCard.rfidIcon.visibility = View.VISIBLE
                cardCard.rfidIcon.setImageDrawable(animatedContactless)
                animatedContactless.start()
            }

            cardCard.editButton.isEnabled = false
            cardCard.securityCode.isEnabled = false
            cardCard.pin.isEnabled = false
            cardCard.bankNameBack.isEnabled = false
            cardCard.cardNotes.isEnabled = false

            cardCard.cardNumber.isEnabled = true
            cardCard.cardHolder.isEnabled = true
            cardCard.toDate.isEnabled = true
            cardCard.bankNameFront.isEnabled = true

            cardCard.magstripe.isClickable = false

            cardCard.editButton.setOnClickListener {
                crypto.secureStartActivity (
                    nextActivity = AddLogin(),
                    nextActivityClassNameAsString = getString(R.string.title_activity_add_card),
                    keyring = keyring,
                    itemId = card.id
                )
            }

            fun hideCodes () {
                cardCard.pin.text = "●●●●"
                cardCard.securityCode.text = "●●●"
                cardCard.hideCodes.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_off_24))
                cardCard.pin.setOnClickListener(null)
                cardCard.securityCode.setOnClickListener(null)
            }

            var codesHidden = true
            hideCodes()
            cardCard.hideCodes.setOnClickListener {
                codesHidden = !codesHidden
                if (codesHidden) {
                    hideCodes()
                } else {
                    cardCard.hideCodes.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_24))
                    cardCard.securityCode.text = card.securityCode
                    if (!card.pin.isNullOrBlank()) cardCard.pin.text = card.pin else cardCard.pinLayout.visibility = View.GONE

                    cardCard.securityCode.setOnClickListener {
                        val clip = ClipData.newPlainText("Keyspace", card.securityCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(applicationContext, "Card security code copied!", Toast.LENGTH_LONG).show()
                    }

                    cardCard.pin.setOnClickListener {
                        val clip = ClipData.newPlainText("Keyspace", card.pin)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(applicationContext, "Card PIN copied!", Toast.LENGTH_LONG).show()
                    }

                    Handler().postDelayed({
                        hideCodes()
                    }, 3000)

                }
            }

            cardCard.cardNumber.setOnClickListener {
                val clip = ClipData.newPlainText("Keyspace", card.cardNumber)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(applicationContext, "Card number copied!", Toast.LENGTH_LONG).show()
            }

            cardCard.toDate.setOnClickListener {
                val clip = ClipData.newPlainText("Keyspace", card.expiry)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(applicationContext, "Expiry date copied!", Toast.LENGTH_LONG).show()
            }

            cardCard.cardHolder.setOnClickListener {
                val clip = ClipData.newPlainText("Keyspace", card.cardholderName)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(applicationContext, "Cardholder name copied!", Toast.LENGTH_LONG).show()
            }

        }

    }

    private fun renderNotesFragment () {

        sortBy = configData.getString("sort_by", io.SORT_LAST_EDITED)!!
        vault = io.vaultSorter(vault, sortBy)

        renderTopBar(io.TYPE_NOTE)
        fab.setOnClickListener {
            crypto.secureStartActivity (
                nextActivity = AddNote(),
                nextActivityClassNameAsString = getString(R.string.title_activity_add_note),
                keyring = keyring,
                itemId = null
            )
        }

        killBottomSheet()

        if (io.getNotes(io.getVault()).size == 0) {
            try { fragmentRoot.removeView(fragmentView) } catch (uninflated: UninitializedPropertyAccessException) { }
            fragmentView = inflater.inflate(R.layout.no_vault_data, null)
            fragmentRoot.addView(fragmentView)
            if (coldStart) fragmentView.startAnimation(loadAnimation(applicationContext, android.R.anim.fade_in));

        } else {
            try {
                fragmentRoot.removeView(fragmentView)
            } catch (uninflated: UninitializedPropertyAccessException) {
            }
            fragmentView = inflater.inflate(R.layout.dashboard_fragment_notes, null)
            fragmentRoot.addView(fragmentView)

            notes.clear()
            for (encryptedNote in io.getNotes(vault))
                notes.add(io.decryptNote(encryptedNote))

            notesRecycler = fragmentView.findViewById(R.id.notes_recycler)

            if (configData.getBoolean("notesGrid", true)) notesRecycler.layoutManager = StaggeredGridLayoutManager(2, LinearLayoutManager.VERTICAL)
            else notesRecycler.layoutManager = LinearLayoutManager(this)

            val adapter = NotesAdapter(notes)
            adapter.setHasStableIds(true)
            notesRecycler.adapter = adapter
            if (coldStart) notesRecycler.layoutAnimation = loadLayoutAnimation(applicationContext, R.anim.slide_right_anim_controller)
            adapter.notifyItemInserted(notes.size)
            LinearLayoutManager(applicationContext).apply { isAutoMeasureEnabled = false }
            notesRecycler.isNestedScrollingEnabled = false
            notesRecycler.setItemViewCacheSize(50)
            notesRecycler.scheduleLayoutAnimation()

            notesRecycler.setListener(object : SwipeLeftRightCallback.Listener {
                override fun onSwipedLeft(position: Int) { }

                override fun onSwipedRight(position: Int) {  // Copy password
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    if (notes.elementAt(position).notes.isNullOrEmpty()) {
                        val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this@Dashboard).create()
                        alertDialog.setTitle("No text")
                        alertDialog.setMessage("This note has no text.")
                        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Got it") { _, _ -> alertDialog.dismiss() }
                        alertDialog.show()
                    } else {
                        val clip = ClipData.newPlainText("Keyspace", notes.elementAt(position).notes)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(applicationContext, "Note copied!", Toast.LENGTH_LONG).show()
                    }
                    adapter.notifyItemChanged(position)
                }

            })

            notesScrollView = fragmentView.findViewById(R.id.notes_scrollview)

            notesScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                if (scrollY > oldScrollY + 5) {
                    fab.hide()
                    bottomSheet.visibility = View.GONE
                    bottomSheet.animate().scaleY(0.0f)
                    topBar.animate().translationY(-350f)
                }

                if (scrollY < oldScrollY - 5) {
                    fab.show()
                    bottomSheet.visibility = View.VISIBLE
                    bottomSheet.animate().scaleY(1.0f)
                    topBar.animate().translationY(0f)
                }

                if (scrollY == 0) {
                    fab.show()
                    bottomSheet.visibility = View.VISIBLE
                    bottomSheet.animate().scaleY(1.0f)
                    topBar.animate().translationY(0f)
                }
            })
        }

    }

    private fun renderCardsFragment () {

        sortBy = configData.getString("sort_by", io.SORT_LAST_EDITED)!!
        vault = io.vaultSorter(vault, sortBy)

        renderTopBar(io.TYPE_CARD)
        fab.setOnClickListener {
            crypto.secureStartActivity (
                nextActivity = AddNote(),
                nextActivityClassNameAsString = getString(R.string.title_activity_add_card),
                keyring = keyring,
                itemId = null
            )
        }
        killBottomSheet()

        if (vault.card.isNullOrEmpty()) {
            try { fragmentRoot.removeView(fragmentView) } catch (uninflated: UninitializedPropertyAccessException) { }
            fragmentView = inflater.inflate(R.layout.no_vault_data, null)
            fragmentRoot.addView(fragmentView)
            if (coldStart) fragmentView.startAnimation(loadAnimation(applicationContext, android.R.anim.fade_in));

        } else {
            try { fragmentRoot.removeView(fragmentView) } catch (uninflated: UninitializedPropertyAccessException) { }
            fragmentView = inflater.inflate(R.layout.dashboard_fragment_cards, null)
            fragmentRoot.addView(fragmentView)

            cards.clear()
            for (encryptedCard in io.getCards(vault))
                cards.add(io.decryptCard(encryptedCard))

            cardsRecycler = fragmentView.findViewById(R.id.cards_recycler)
            cardsRecycler.layoutManager = LinearLayoutManager(this)
            val adapter = CardsAdapter(cards)
            adapter.setHasStableIds(true)
            cardsRecycler.adapter = adapter
            cardsRecycler.setItemViewCacheSize(20);
            if (coldStart) cardsRecycler.layoutAnimation = loadLayoutAnimation(applicationContext, R.anim.slide_right_anim_controller)
            LinearLayoutManager(applicationContext).apply { isAutoMeasureEnabled = false }
            cardsRecycler.recycledViewPool.setMaxRecycledViews(0, 0)
            adapter.notifyItemInserted(cards.size)
            cardsRecycler.isNestedScrollingEnabled = false
            cardsRecycler.scheduleLayoutAnimation()

            cardsScrollView = fragmentView.findViewById(R.id.cards_scrollview)

            cardsScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                if (scrollY > oldScrollY + 5) {
                    fab.hide()
                    bottomSheet.visibility = View.GONE
                    bottomSheet.animate().scaleY(0.0f)
                    topBar.animate().translationY(-350f)
                }

                if (scrollY < oldScrollY - 5) {
                    fab.show()
                    bottomSheet.visibility = View.VISIBLE
                    bottomSheet.animate().scaleY(1.0f)
                    topBar.animate().translationY(0f)
                }

                if (scrollY == 0) {
                    fab.show()
                    bottomSheet.visibility = View.VISIBLE
                    bottomSheet.animate().scaleY(1.0f)
                    topBar.animate().translationY(0f)
                }
            })

        }

    }

    inner class KeyrouteLoadingScreen {

        val dismissDelay = 2000L

        var builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(this@Dashboard)
        private lateinit var dialog: AlertDialog
        private lateinit var doneBox: View
        private lateinit var iconography: ImageView
        private lateinit var loadingText: TextView
        private lateinit var loadingSubtitle: TextView
        private lateinit var loadingBar: ProgressBar

        fun showConnectingScreen () {
            doneBox = layoutInflater.inflate(R.layout.keyroute_done_screen, null)
            builder.setView(doneBox)

            builder.setCancelable(false)

            killBottomSheet()

            doneBox.findViewById<ImageView>(R.id.bottomSheetDoneIcon).visibility = View.GONE
            doneBox.findViewById<ProgressBar>(R.id.bottomSheetProgress).visibility = View.VISIBLE
            doneBox.findViewById<TextView>(R.id.bottomSheetLoadingText).visibility = View.VISIBLE

            dialog = builder.create()
            dialog.show()

        }

        fun showConnectedScreen () {
            killBottomSheet()
            doneBox.performHapticFeedback(HapticFeedbackConstants.REJECT)
            dialog.dismiss()

            doneBox.findViewById<ImageView>(R.id.bottomSheetDoneIcon).visibility = View.GONE
            doneBox.findViewById<ProgressBar>(R.id.bottomSheetProgress).visibility = View.VISIBLE
            doneBox.findViewById<TextView>(R.id.bottomSheetLoadingText).visibility = View.VISIBLE

            doneBox.findViewById<ImageView>(R.id.bottomSheetDoneIcon).visibility = View.VISIBLE
            doneBox.findViewById<ImageView>(R.id.bottomSheetDoneIcon).startAnimation(loadAnimation(applicationContext, R.anim.zoom_spin))
            doneBox.findViewById<ProgressBar>(R.id.bottomSheetProgress).visibility = View.GONE

            doneBox.findViewById<TextView>(R.id.bottomSheetLoadingText).text = "Signed in!"

            dialog.show()
            Handler().postDelayed({ dialog.dismiss() }, dismissDelay)

        }

        fun showErrorScreen () {
            killBottomSheet()
            dialog.dismiss()

            doneBox.findViewById<ImageView>(R.id.bottomSheetDoneIcon).visibility = View.VISIBLE
            doneBox.findViewById<ProgressBar>(R.id.bottomSheetProgress).visibility = View.GONE
            doneBox.findViewById<TextView>(R.id.bottomSheetLoadingText).visibility = View.VISIBLE
            doneBox.findViewById<ImageView>(R.id.bottomSheetDoneIcon).setImageDrawable(getDrawable(R.drawable.ic_baseline_close_24))

            doneBox.findViewById<TextView>(R.id.bottomSheetLoadingText).text = "Couldn't sign in"

            doneBox.findViewById<TextView>(R.id.bottomSheetLoadingText).startAnimation(loadAnimation(applicationContext, R.anim.wiggle))
            doneBox.findViewById<ImageView>(R.id.bottomSheetDoneIcon).startAnimation(loadAnimation(applicationContext, R.anim.wiggle))
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(150) // Vibrate for a second

            dialog.show()
            Handler().postDelayed({ dialog.dismiss() }, dismissDelay)

        }

        fun showUnsupportedScreen () {
            killBottomSheet()

            doneBox.findViewById<ImageView>(R.id.bottomSheetDoneIcon).visibility = View.VISIBLE
            doneBox.findViewById<ProgressBar>(R.id.bottomSheetProgress).visibility = View.GONE
            doneBox.findViewById<TextView>(R.id.bottomSheetLoadingText).visibility = View.VISIBLE
            doneBox.findViewById<ImageView>(R.id.bottomSheetDoneIcon).setImageDrawable(getDrawable(R.drawable.ic_baseline_close_24))

            doneBox.findViewById<TextView>(R.id.bottomSheetLoadingText).text = "Unsupported QR code"

            doneBox.findViewById<TextView>(R.id.bottomSheetLoadingText).startAnimation(loadAnimation(applicationContext, R.anim.wiggle))
            doneBox.findViewById<ImageView>(R.id.bottomSheetDoneIcon).startAnimation(loadAnimation(applicationContext, R.anim.wiggle))
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(150) // Vibrate for a second

            dialog.show()
            Handler().postDelayed({ dialog.dismiss() }, dismissDelay)

        }

    }

    @SuppressLint("RestrictedApi")
    private fun renderBottomSheet () {
        bottomSheet = findViewById(R.id.bottom_sheet)
        bottomSheetQrViewfinder = findViewById(R.id.bottomSheetQrViewfinder)
        swipeText = findViewById(R.id.swipeText)
        swipeIcon = findViewById(R.id.swipeIcon)
        swipeHint = findViewById(R.id.swipeHint)

        val slideDownAnimation: Animation = loadAnimation(applicationContext, R.anim.from_top)
        Handler(Looper.getMainLooper()).postDelayed({ runOnUiThread {
            findViewById<CardView>(R.id.swiper).startAnimation(loadAnimation(applicationContext, R.anim.from_bottom_slower))
        }}, 250)

        scannerView = findViewById(R.id.code_scanner)
        scannerView.visibility = View.VISIBLE
        codeScanner = CodeScanner(applicationContext, scannerView)
        codeScanner.scanMode = ScanMode.SINGLE
        codeScanner.autoFocusMode = AutoFocusMode.SAFE

        sheetBehavior = BottomSheetBehavior.from(bottomSheet)

        sheetBehavior.disableShapeAnimations()
        sheetBehavior.shouldSkipHalfExpandedStateWhenDragging()
        sheetBehavior.shouldSkipSmoothAnimation()

        swipeHint.setOnClickListener {
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(1)  // light tap
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            startCodeScanner()
        }

        sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        swipeHint.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                        Handler(Looper.getMainLooper()).postDelayed({ runOnUiThread {
                            swipeText.text = "Swipe down to collapse"
                            fab.isEnabled = false

                            fab.hide()

                            swipeIcon.setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_keyboard_arrow_down_24))
                            swipeHint.setOnClickListener {
                                swipeHint.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                            }
                            loadBottomsheet ()
                            findViewById<CardView>(R.id.swiper).startAnimation(loadAnimation(applicationContext, R.anim.from_top_slower))
                        }}, 500)

                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        killBottomSheet()
                        swipeText.text = "Swipe to scan code"

                        CoroutineScope(Dispatchers.IO).launch {
                            withContext(Dispatchers.Main) {
                                findViewById<CardView>(R.id.swiper).startAnimation(loadAnimation(applicationContext, R.anim.from_bottom_slower))
                            }
                        }

                        sheetBehavior.isDraggable = true
                        fab.isEnabled = true

                        fab.show()

                        swipeIcon.setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_keyboard_arrow_up_24))

                        swipeHint.setOnClickListener {
                            swipeHint.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                            startCodeScanner()
                        }

                    }

                    BottomSheetBehavior.STATE_DRAGGING -> {
                        fab.hide()
                        fab.isEnabled = false
                        swipeText.text = "Continue swiping..."
                        startCodeScanner()
                    }

                    BottomSheetBehavior.STATE_HIDDEN -> { }
                    BottomSheetBehavior.STATE_SETTLING -> { }
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> { }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) { }

        })
    }

    fun keyroute (email: String, token: String, signedToken: String, serverEphemeralPublicKey: ByteArray) {
        val network = NetworkUtilities(applicationContext, this, keyring)
        val timeout = 60000
        val ephemeralKeypair = crypto.generateEphemeralKeys()
        val clientPublicKey = ephemeralKeypair.publicKey
        val clientPrivateKey = ephemeralKeypair.privateKey
        val connectingScreen = KeyrouteLoadingScreen()

        runOnUiThread { connectingScreen.showConnectingScreen() }

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {  // used to run synchronous Kotlin functions like `suspend fun foo()`
                kotlin.runCatching {
                    try {
                        val routeId = Base64.encodeToString (token.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                        val endpoint = network.keyroute_endpoint + "/" + routeId

                        // Log.d("Keyspace-KR", "Endpoint:\n$endpoint")
                        // Log.d("Keyspace-KR", "Tester:\n${"https://api.keyspace.cloud/keyroute-test/$routeId"}")

                        val socket = WebSocketFactory()
                            .setConnectionTimeout(timeout)
                            .createSocket(endpoint)

                        socket.addListener (object : WebSocketAdapter() {
                            override fun onTextMessage(websocket: WebSocket, message: String) {
                                Log.d("Keyspace-KR", "RECEIVED: $message")
                            }

                            override fun onDisconnected(websocket: WebSocket?, serverCloseFrame: WebSocketFrame?, clientCloseFrame: WebSocketFrame?, closedByServer: Boolean) {
                                Log.d("Keyspace-KR", "WEBSOCKET CLOSED")
                                runOnUiThread { connectingScreen.showConnectedScreen() }
                            }

                            override fun onConnected(websocket: WebSocket?, headers: MutableMap<String, MutableList<String>>?) {
                                Log.d("Keyspace-KR", "WEBSOCKET OPENED")

                                val payload = crypto.generateKeyroutePayload (
                                    email,
                                    signedToken,
                                    clientPublicKey,
                                    clientPrivateKey,
                                    serverEphemeralPublicKey,
                                    keyring
                                )

                                socket.sendText (payload)
                                socket.sendClose()

                                // Log.d("Keyspace-KR", "SENT: $payload")
                            }

                            override fun handleCallbackError(websocket: WebSocket?, cause: Throwable?) {
                                super.handleCallbackError(websocket, cause)
                                Log.d("Keyspace-KR", "CALLBACK ERROR: ${cause?.stackTraceToString()}")
                                runOnUiThread { connectingScreen.showErrorScreen() }
                            }

                            override fun onConnectError(websocket: WebSocket?, exception: WebSocketException?) {
                                super.onConnectError(websocket, exception)
                                Log.d("Keyspace-KR", "CONNECTION ERROR: ${exception?.stackTraceToString()}")
                                runOnUiThread { connectingScreen.showErrorScreen() }
                            }

                        })

                        socket.connectAsynchronously()

                    } catch (noInternet: NetworkError) {
                        Log.d("Keyspace-KR", "Error: Could not connect to ${network.keyauth_endpoint}\n${noInternet.stackTrace}")
                        Toast.makeText(applicationContext, "FAIL :(", Toast.LENGTH_LONG).show()
                        runOnUiThread { connectingScreen.showErrorScreen() }
                    } catch (webSocketError: IOException) {
                        Log.d("Keyspace-KR", "Error: Problems occurred while talking to Keyroute backend\n${webSocketError.stackTrace}")
                        Toast.makeText(applicationContext, "FAIL :(", Toast.LENGTH_LONG).show()
                        runOnUiThread { connectingScreen.showErrorScreen() }
                    }
                }
            }
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    private fun loadBottomsheet () {
        fun contractDialog(codeResult: String) {
            afterScanLayout = findViewById<View>(R.id.afterScanLayout) as LinearLayout
            val menuLayout: View = inflater.inflate(R.layout.contract, afterScanLayout, true)
            val contractBlurb = findViewById<TextView>(R.id.contractBlurb)
            val clause = findViewById<TextView>(R.id.clause)
            clause.text = "By tapping done, you agree to give the above information to $codeResult. Keyspace is not responsible for how $codeResult handles your data."
            contractBlurb.text = "Allow $codeResult to access your"
            sheetBehavior.isDraggable = false
            contractPage.visibility = View.VISIBLE
            scannerView.visibility = View.GONE

            try {
                codeScanner.releaseResources()
            } catch (firstSwipeOnBottomSheet: UninitializedPropertyAccessException) { /* do nothing */ }
            swipeText.text = "Tap on data to select"
            swipeIcon.visibility = View.GONE

            val cancel: FloatingActionButton = findViewById(R.id.cancel)
            val done: FloatingActionButton = findViewById(R.id.done)

            done.setOnClickListener {
                contractPage.visibility = View.GONE
                scannerView.visibility = View.VISIBLE
                Toast.makeText(applicationContext, "Signed into $codeResult successfully", Toast.LENGTH_LONG).show()
                sheetBehavior.isDraggable = true
                killBottomSheet()
            }

            cancel.setOnClickListener {
                contractPage.visibility = View.GONE
                contractPage.visibility = View.VISIBLE
                sheetBehavior.isDraggable = true
                killBottomSheet()
            }

        }

        try {
            codeScanner.decodeCallback = DecodeCallback {
                runOnUiThread {

                    val scannedData = it.text
                    if (scannedData.contains("\"intent\":\"login\"")) {
                        if (JSONObject(scannedData)["intent"].equals("login")) {
                            val serverEphemeralPublicKey = HexMessageEncoder().decode(JSONObject(scannedData)["publicKey"].toString())
                            val token = JSONObject(scannedData)["token"].toString()
                            val signedToken = JSONObject(token)["signedToken"].toString()
                            val email = configData.getString("userEmail", null)!!
                            keyroute (email, token, signedToken, serverEphemeralPublicKey)
                        }

                    } else if (scannedData.contains("otpauth://")) {
                        killBottomSheet()

                        val decoded2faData = misc.decodeOTPAuthURL(scannedData.toString())

                        try {

                            if (decoded2faData?.secret.isNullOrBlank()) throw java.lang.IllegalArgumentException("No secret")

                            val data = IOUtilities.Login(
                                id = UUID.randomUUID().toString(),
                                organizationId = null,
                                type = io.TYPE_LOGIN,
                                name = decoded2faData?.label ?: decoded2faData?.issuer ?: decoded2faData?.account,
                                notes = null,
                                favorite = false,
                                tagId = null,
                                loginData = IOUtilities.LoginData(
                                    username = decoded2faData?.account ?: decoded2faData?.label,
                                    password = null,
                                    passwordHistory = null,
                                    email = if (misc.isValidEmail(decoded2faData?.label)) decoded2faData?.label else decoded2faData?.account,
                                    totp = IOUtilities.Totp(
                                        secret = decoded2faData!!.secret,
                                        backupCodes = null
                                    ),
                                    siteUrls = null
                                ),
                                dateCreated = Instant.now().epochSecond,
                                dateModified = Instant.now().epochSecond,
                                frequencyAccessed = 0,
                                customFields = null,
                                iconFile = null
                            )

                            val encryptedLogin = io.encryptLogin(data)

                            vault.login?.add (encryptedLogin)
                            io.writeVault(vault)

                            network.writeQueueTask (encryptedLogin, mode = network.MODE_POST)

                            renderLoginsFragment()

                            Toast.makeText(applicationContext, "Added 2FA login!", Toast.LENGTH_LONG).show()

                            val savingBoxBuilder = MaterialAlertDialogBuilder(this@Dashboard)
                            savingBoxBuilder.setCancelable(false)
                            val savingBox: View = layoutInflater.inflate(R.layout.loading_screen, null)
                            savingBoxBuilder.setView(savingBox)
                            savingBox.findViewById<TextView>(R.id.loadingText).text = "Saving 2FA login..."
                            savingBox.findViewById<TextView>(R.id.loadingSubtitle).text = "Syncing with servers"
                            savingBox.findViewById<ImageView>(R.id.iconography).visibility = View.GONE
                            savingBox.startAnimation(loadAnimation(applicationContext, R.anim.from_bottom))

                            val savingBoxDialog = savingBoxBuilder.create()
                            savingBoxDialog.show()

                            Handler().postDelayed({
                                savingBoxDialog.dismiss()
                            }, 3000)

                        } catch (invalidObject: java.lang.IllegalArgumentException) {
                            val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
                            alertDialog.setTitle("Invalid code")
                            alertDialog.setMessage("This QR Code does not contain valid two-factor authentication data.")
                            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Go back") { _, _ -> alertDialog.dismiss() }
                            alertDialog.show()
                        }

                    } else if (scannedData.contains(SINGLE_SIGN_ON)) {
                        contractDialog(scannedData)

                    } else {
                        val connectingScreen = KeyrouteLoadingScreen()
                        connectingScreen.showConnectingScreen()
                        connectingScreen.showUnsupportedScreen()
                    }

                }
            }
        } catch (objectExists: IllegalStateException) { }

    }

    private fun renderBottomNavBar () {
        bottomNavbar = findViewById(R.id.bottom_navigation)

        fab.setOnClickListener {
            crypto.secureStartActivity (
                nextActivity = AddLogin(),
                nextActivityClassNameAsString = getString(R.string.title_activity_add_login),
                keyring = keyring,
                itemId = null
            )
        }

        bottomNavbar.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.logins -> {
                    if (lastFragment == io.TYPE_LOGIN) coldStart = false
                    renderLoginsFragment()
                    configData.edit().putString("lastFragment", io.TYPE_LOGIN).apply()
                    lastFragment = configData.getString("lastFragment", io.TYPE_LOGIN)!!
                    coldStart = true
                }

                R.id.notes -> {
                    if (lastFragment == io.TYPE_NOTE) coldStart = false
                    renderNotesFragment()
                    configData.edit().putString("lastFragment", io.TYPE_NOTE).apply()
                    lastFragment = configData.getString("lastFragment", io.TYPE_LOGIN)!!
                    coldStart = true
                }

                R.id.payments -> {
                    if (lastFragment == io.TYPE_CARD) coldStart = false
                    renderCardsFragment()
                    configData.edit().putString("lastFragment", io.TYPE_CARD).apply()
                    lastFragment = configData.getString("lastFragment", io.TYPE_LOGIN)!!
                    coldStart = true
                }
            }
            true
        }
    }

    private fun openSettings () {
        val intent = Intent(this, Settings::class.java)
        startActivity(intent)
        finish()
    }

    private fun loginInfoDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setCancelable(true)
        val loginInfoBox: View = layoutInflater.inflate(R.layout.keyspace_account_info, null)
        builder.setView(loginInfoBox)

        loginInfoBox.startAnimation(loadAnimation(applicationContext, R.anim.from_top))

        val dialog = builder.create()

        dialog.show()

        val keyspaceEmail = loginInfoBox.findViewById<TextView>(R.id.keyspaceEmail)
        val keyspaceUsername = loginInfoBox.findViewById<TextView>(R.id.keyspaceUsername)
        val keyspaceAccountPicture = loginInfoBox.findViewById<ImageView>(R.id.keyspaceAccountPicture)
        keyspaceEmail.text = configData.getString("userEmail", null)
        keyspaceUsername.text = configData.getString("userEmail", null)?.substringBefore("@")
        keyspaceAccountPicture.setImageDrawable(misc.generateProfilePicture(configData.getString("userEmail", null)!!))

        val signOutButton = loginInfoBox.findViewById<TextView>(R.id.signOutButton)
        val syncButton = loginInfoBox.findViewById<TextView>(R.id.syncButton)
        val sendFeedbackButton = loginInfoBox.findViewById<TextView>(R.id.sendFeedbackButton)
        val keyspaceLogoHeader = loginInfoBox.findViewById<ConstraintLayout>(R.id.keyspaceLogoHeader)
        val settingsButton = loginInfoBox.findViewById<TextView>(R.id.settingsButton)
        val closeButton = loginInfoBox.findViewById<ImageView>(R.id.closeButton)
        val privacyPolicyButton = loginInfoBox.findViewById<TextView>(R.id.privacyPolicyButton)
        val termsOfServiceButton = loginInfoBox.findViewById<TextView>(R.id.termsOfServiceButton)

        syncButton.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(applicationContext, "Syncing vault", Toast.LENGTH_SHORT).show()
            vaultSynchronizer()
        }

        keyspaceLogoHeader.setOnClickListener {
            dialog.dismiss()
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://keyspace.cloud/"))
            startActivity(browserIntent)
        }

        sendFeedbackButton.setOnClickListener {
            dialog.dismiss()
            feedbackDialog()
        }

        settingsButton.setOnClickListener {
            dialog.dismiss()
            openSettings()
        }

        signOutButton.setOnClickListener {
            dialog.dismiss()
            val dialogBuilder = MaterialAlertDialogBuilder(this)
                .setTitle("Sign out")
                .setIcon(R.drawable.ic_baseline_exit_to_app_24)
                .setMessage("Would you like to sign out of Keyspace?")
                .setCancelable(true)
                .setPositiveButton("Sign out") { _, _ ->
                    if (network.getDeleteTaskCount() + network.getEditTaskCount() + network.getSaveTaskCount() != 0) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Sync pending")
                            .setIcon(R.drawable.ic_baseline_exit_to_app_24)
                            .setMessage("Some items haven't been uplodaded to the backend. Sign out anyway?")
                            .setCancelable(true)
                            .setPositiveButton("Sign out anyway") { _, _ -> signOut() }
                            .setNegativeButton("Go back") { dialog, _ -> dialog.cancel() }
                    } else { signOut() }
                }
                .setNegativeButton("Go back") { dialog, _ ->
                    dialog.cancel()
                }
            val alert = dialogBuilder.create()
            alert.show()
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        privacyPolicyButton.setOnClickListener {
            dialog.dismiss()
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://keyspace.cloud/privacy"))
            startActivity(browserIntent)
        }

        termsOfServiceButton.setOnClickListener {
            dialog.dismiss()
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://keyspace.cloud/terms-conditions"))
            startActivity(browserIntent)
        }

        dialog.setOnDismissListener(object : PopupMenu.OnDismissListener, DialogInterface.OnDismissListener {
            override fun onDismiss(menu: PopupMenu?) {
                //loginInfoButton.startAnimation(loadAnimation(applicationContext, R.anim.zoom_spin))
            }

            override fun onDismiss(p0: DialogInterface?) {
                //loginInfoButton.startAnimation(loadAnimation(applicationContext, R.anim.zoom_spin))
            }
        })

        val queueStatus = loginInfoBox.findViewById<LinearLayout>(R.id.queueStatus)
        val queueIcon = loginInfoBox.findViewById<ImageView>(R.id.queueIcon)
        val queueTitle = loginInfoBox.findViewById<TextView>(R.id.queueTitle)
        val queueSubtitle = loginInfoBox.findViewById<TextView>(R.id.queueSubtitle)
        val queueProgress = loginInfoBox.findViewById<ProgressBar>(R.id.queueProgress)

        val deleteQueue = network.getDeleteTaskCount()
        val editQueue = network.getEditTaskCount()
        val saveQueue = network.getSaveTaskCount()

        if (networkStatus.contains("alive")) {

            queueIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_cloud_upload_24))
            queueTitle.text = "Uploading"
            queueSubtitle.text = "Uploading changes"

            if (deleteQueue > 0) {
                queueStatus.visibility = View.VISIBLE
                queueProgress.visibility = View.VISIBLE
                queueSubtitle.text = "Deleting $deleteQueue item(s)"
            }

            if (editQueue > 0) {
                queueStatus.visibility = View.VISIBLE
                queueProgress.visibility = View.VISIBLE
                queueSubtitle.text = "Syncing $editQueue edit(s)"
            }

            if (saveQueue > 0) {
                queueStatus.visibility = View.VISIBLE
                queueProgress.visibility = View.VISIBLE
                queueSubtitle.text = "Syncing $saveQueue item(s)"
            }

            if (saveQueue == 0 && editQueue == 0 && deleteQueue == 0) {
                queueStatus.visibility = View.GONE
            }

        }

        if (!networkStatus.contains("alive")) {
            queueStatus.visibility = View.VISIBLE
            queueIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_cloud_off_24))
            queueTitle.text = "Offline mode"
            queueSubtitle.text = "Any changes you make will be saved when you're back online."
            queueProgress.visibility = View.GONE

            if (deleteQueue > 0) {
                queueProgress.visibility = View.GONE
                queueSubtitle.text = "Will delete $deleteQueue item(s) when online"
            }

            if (editQueue > 0) {
                queueProgress.visibility = View.GONE
                queueSubtitle.text = "Will make $editQueue edit(s) when online"
            }

            if (saveQueue > 0) {
                queueProgress.visibility = View.GONE
                queueSubtitle.text = "Will sync $saveQueue item(s) when online"
            }

        }

    }

    private fun feedbackDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("Send feedback")
        builder.setCancelable(false)
        val feedbackBox: View = inflater.inflate(R.layout.send_feedback, null)
        builder.setView(feedbackBox)
        builder.setPositiveButton("Submit") { _, _ ->
            val subjectText = feedbackBox.findViewById<EditText>(R.id.subject)
            val commentsText = feedbackBox.findViewById<EditText>(R.id.comments)
        }
        builder.setNegativeButton("Cancel") { _, _ -> }
        val dialog = builder.create()
        dialog.show()
    }

    private fun killBottomSheet() {
        try {
            codeScanner.stopPreview()
            codeScanner.releaseResources()
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } catch (firstSwipeOnBottomSheet: UninitializedPropertyAccessException) { /* do nothing */ }
    }

    private fun startCodeScanner() {
        PermissionX
            .init(this@Dashboard)
            .permissions (Manifest.permission.CAMERA)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(deniedList, "Keyspace needs these permissions in order to work as intended", "Enable", "Go back")
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(deniedList, "Please enable the following to proceed", "Enable", "Go back")
            }
            .request { allGranted, _, _ ->
                if (allGranted) {
                    Handler (
                    Looper.getMainLooper()).postDelayed ({
                        thread {codeScanner.startPreview()}
                    }, 250
                )
                } else killBottomSheet()
            }

    }

    private fun signOut() {
        finish()
        crypto.wipeAllKeys()
        configData.edit().clear().commit()
        io.wipeAllKeyspaceFSFiles()
        network.wipeAllQueues()
        val intent = Intent(applicationContext, StartHere::class.java)
        startActivity(intent)
    }

    override fun onStop() {
        super.onStop()

        // force wipe keyring
        keyring.XCHACHA_POLY1305_KEY?.fill(0)
        keyring.ED25519_PUBLIC_KEY?.fill(0)
        keyring.ED25519_PRIVATE_KEY?.fill(0)

        // force gc to clear keyring
        System.gc()

        mfaCodesTimer.cancel()
        mfaCodesTimer.purge()

        vaultSyncTimer.cancel()
        vaultSyncTimer.purge()

        killBottomSheet()
        finish()
        finishAffinity()
    }

    private fun vaultSynchronizer () {
        var refreshInterval: Long = configData.getLong ("refreshInterval", 0L)

        suspend fun grabVault () {

                    networkStatus = network.keyspaceStatus().status
                    try {
                        if (networkStatus != "alive") {
                            withContext(Dispatchers.Main) {
                                connectionStatusDot.visibility = View.VISIBLE
                                connectionStatusDot.imageTintList = ColorStateList.valueOf(Color.RED)
                            }
                        } else {

                            lateinit var signedToken: String

                            try {
                                signedToken = network.generateSignedToken()
                            }  catch (_: Exception) {
                                 cancel()
                            }

                            val serverVault = network.grabLatestVaultFromBackend (signedToken)

                            withContext(Dispatchers.Main) {
                                connectionStatusDot.visibility = View.GONE
                            }

                            if (io.vaultsDiffer(vault, serverVault)) {
                                io.writeVault(serverVault)
                                vault = serverVault

                                withContext(Dispatchers.Main) {
                                    when {
                                        (lastFragment == io.TYPE_LOGIN) -> {
                                            renderLoginsFragment()
                                            bottomNavbar.selectedItemId = R.id.logins
                                        }
                                        (lastFragment == io.TYPE_NOTE) -> {
                                            renderNotesFragment()
                                            bottomNavbar.selectedItemId = R.id.notes
                                        }
                                        (lastFragment == io.TYPE_CARD) -> {
                                            renderCardsFragment()
                                            bottomNavbar.selectedItemId = R.id.payments
                                        }
                                    }
                                }
                            }

                        }

                    } catch (noInternet: NetworkError) {
                        cancel()
                    } catch (noInternet: NullPointerException) {
                        cancel()
                    }

        }

        fun syncVault () {

            try {
                CoroutineScope(Dispatchers.IO).launch {
                    kotlin.runCatching {
                        network.completeQueueTasks(network.generateSignedToken())
                        grabVault()
                    }.onFailure {
                        when (it) {
                            is NetworkUtilities.IncorrectCredentialsException -> {
                                withContext(Dispatchers.Main) {
                                    // showIncorrectCredentialsDialog()
                                }
                            }
                            else -> throw it
                        }
                    }
                }

            } catch (_: Exception) { }
        }

        if (refreshInterval != -1L) {
            if (refreshInterval == 0L) refreshInterval = 3000L
            vaultSyncTimer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        syncVault ()
                    }
                }
            }, 0, refreshInterval) // 1000 milliseconds = 1 second
        }
    }

    override fun onUserLeaveHint() {

        vaultSyncTimer.cancel()
        vaultSyncTimer.purge()

        Handler().postDelayed({ killApp() }, 500)
        super.onUserLeaveHint()
    }

    override fun onPause() {

        vaultSyncTimer.cancel()
        vaultSyncTimer.purge()

        Handler().postDelayed({ killApp() }, 500)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return true
    }

    private fun killApp () {
        if (configData.getBoolean("lockApp", true)) {
            finish()
            finishAffinity()

            mfaCodesTimer.cancel()
            mfaCodesTimer.purge()

            // force wipe keyring
            keyring.XCHACHA_POLY1305_KEY?.fill(0)
            keyring.ED25519_PUBLIC_KEY?.fill(0)
            keyring.ED25519_PRIVATE_KEY?.fill(0)

            // force gc to clear keyring
            System.gc()

            killBottomSheet()
        }
    }

    private fun showIncorrectCredentialsDialog () {
        val dialogBuilder = MaterialAlertDialogBuilder(this@Dashboard)
        dialogBuilder
            .setCancelable(false)
            .setTitle("Key mismatch")
            .setIcon(R.drawable.ic_baseline_error_24)
            .setMessage("Your Keyring may be corrupt. Please sign out and sign in again.")
            .setPositiveButton("Sign out") { dialog, _ ->
                signOut()
            }

        val alert = dialogBuilder.create()
        alert.show()

    }

}
