package cloud.keyspace.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.dhaval2404.colorpicker.MaterialColorPickerDialog
import com.github.dhaval2404.colorpicker.listener.ColorListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.keyspace.keyspacemobile.*
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import kotlin.concurrent.thread


class AddCard : AppCompatActivity() {

    lateinit var utils: MiscUtilities
    lateinit var crypto: CryptoUtilities
    lateinit var misc: MiscUtilities
    lateinit var io: IOUtilities
    lateinit var network: NetworkUtilities

    lateinit var tagButton: ImageView
    private lateinit var tagPicker: AddTag
    var tagId: String? = null
    val tagIdGrabber = Handler(Looper.getMainLooper())

    var favorite: Boolean = false
    private lateinit var favoriteButton: ImageView

    lateinit var doneButton: ImageView
    lateinit var backButton: ImageView
    lateinit var deleteButton: ImageView

    var cardColor: String? = null
    lateinit var colorButton: ImageView

    lateinit var nameInputLayout: TextInputLayout
    lateinit var nameInput: TextInputEditText
    lateinit var nameInputIcon: ImageView
    lateinit var nameIconPicker: ImageView

    lateinit var cardNumberInput: TextInputEditText
    lateinit var cardNumberInputLayout: TextInputLayout

    lateinit var atmPinLayout: TextInputLayout
    lateinit var atmPinInput: TextInputEditText
    lateinit var isAtmCard: MaterialSwitch

    lateinit var hasRfidChip: MaterialSwitch

    lateinit var toDate: TextInputEditText
    lateinit var toDateLayout: TextInputLayout

    lateinit var securityCode: TextInputEditText
    lateinit var securityCodeLayout: TextInputLayout

    lateinit var cardholderNameInput: TextInputEditText
    lateinit var cardholderNameInputLayout: TextInputLayout

    lateinit var notesInput: TextInputEditText

    lateinit var paymentsGatewayIcon: ImageView
    var iconFileName: String? = null

    private var frequencyAccessed = 0L

    lateinit var customFieldsData: MutableList<IOUtilities.CustomField>
    lateinit var customFieldsView: RecyclerView
    lateinit var customFieldsAdapter: CustomFieldsAdapter
    lateinit var addCustomFieldButton: MaterialButton

    lateinit var keyring: CryptoUtilities.Keyring
    private var itemId: String? = null
    private lateinit var vault: IOUtilities.Vault
    private lateinit var card: IOUtilities.Card

    lateinit var configData: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_card)

        configData = getSharedPreferences(applicationContext.packageName + "_configuration_data", MODE_PRIVATE)

        val allowScreenshots = configData.getBoolean("allowScreenshots", false)
        if (!allowScreenshots) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        utils = MiscUtilities (applicationContext)
        crypto = CryptoUtilities(applicationContext, this)
        misc = MiscUtilities(applicationContext)

        val intentData = crypto.receiveKeyringFromSecureIntent (
            currentActivityClassNameAsString = getString(R.string.title_activity_add_card),
            intent = intent
        )

        keyring = intentData.first
        network = NetworkUtilities(applicationContext, this, keyring)
        itemId = intentData.second

        io = IOUtilities(applicationContext, this, keyring)

        initializeUI()

        vault = io.getVault()
        if (itemId != null) {
            card = io.decryptCard(io.getCard(itemId!!, vault)!!)
            loadCard (card)
        }

    }

    private fun initializeUI (): Boolean {

        doneButton = findViewById (R.id.done)
        doneButton.setOnClickListener {
            saveItem()
        }

        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        deleteButton = findViewById (R.id.delete)
        if (itemId != null) {
            deleteButton.setOnClickListener {
                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
                alertDialog.setTitle("Delete")
                alertDialog.setMessage("Would you like to delete \"${card.name}\"")
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Delete") { dialog, _ ->

                    vault.card!!.remove(io.getCard(itemId!!, vault))
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
        tagPicker = AddTag (tagId, applicationContext, this@AddCard, keyring)
        tagButton.setOnClickListener {
            tagPicker.showPicker(tagId)
            tagPicker.showPicker(tagId)
            tagIdGrabber.post(object : Runnable {
                override fun run() {
                    tagId = tagPicker.getSelectedTagId()
                    tagIdGrabber.postDelayed(this, 100)
                }
            })
        }

        favoriteButton = findViewById(R.id.favoriteButton)
        favoriteButton.setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_border_24))
        favoriteButton.setOnClickListener {
            favorite = if (!favorite) {
                favoriteButton.setImageDrawable (ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_24))
                favoriteButton.startAnimation(AnimationUtils.loadAnimation(applicationContext, R.anim.heartbeat))
                true
            } else {
                favoriteButton.setImageDrawable (ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_border_24))
                false
            }
        }

        colorButton = findViewById(R.id.colorButton)
        colorButton.setOnClickListener {
            MaterialColorPickerDialog.Builder(this@AddCard)
                .setColors(resources.getStringArray(R.array.vault_item_colors))
                .setTickColorPerCard(true)
                .setDefaultColor(cardColor.toString())
                .setPositiveButton("Set color")
                .setNegativeButton("Go back")
                .setColorListener(object : ColorListener {
                    override fun onColorSelected(color: Int, colorHex: String) {
                        cardColor = colorHex
                    }
                })
                .show()
        }

        nameInput = findViewById (R.id.nameInput)
        nameInput.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
        nameInputLayout = findViewById (R.id.nameInputLayout)

        nameInputIcon = findViewById (R.id.nameInputIcon)
        nameInputIcon.setOnClickListener {
            iconFilePicker()
        }

        nameIconPicker = findViewById (R.id.pickIcon)
        nameIconPicker.setOnClickListener {
            iconFilePicker()
        }

        nameInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) { }
            override fun beforeTextChanged(bankName: CharSequence, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(bankName: CharSequence, start: Int, before: Int, count: Int) {
                thread {
                    val bankLogo = misc.getSiteIcon(bankName.toString(), nameInput.currentTextColor)
                    if (bankLogo != null/* && iconFileName == null*/) {
                        iconFileName = bankName.toString()
                        runOnUiThread {
                            nameInputIcon.setImageDrawable(bankLogo)
                        }
                    }
                }
            }
        })

        cardNumberInput = findViewById (R.id.CardNumberInput)
        cardNumberInput.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
        cardNumberInput.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING

        paymentsGatewayIcon = findViewById(R.id.PaymentGateway)

        cardNumberInput.addTextChangedListener(object : TextWatcher {
            private val space = ' '
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty() && s.length % 5 == 0) {
                    val c = s[s.length - 1]
                    if (space == c) s.delete(s.length - 1, s.length)
                    if (Character.isDigit(c) && TextUtils.split(s.toString(), space.toString()).size <= 3) s.insert(s.length - 1, space.toString())
                }
                if (s.toString().replace(" ", "").length in 0..16) {
                    cardNumberInput.removeTextChangedListener(this)
                    cardNumberInput.setText(s.toString().replace(" ", "").replace("....".toRegex(), "$0 ")?.trim())
                    cardNumberInput.addTextChangedListener(this)
                    cardNumberInput.setSelection(cardNumberInput.text.toString().length)
                }
                if (s.toString().replace(" ", "").length in 17..18) {
                    for (c in s) {
                        if (c == ' ') {
                            s.delete(s.indexOf(c), s.indexOf(c)+1)
                        }
                    }
                }
                if (s.toString().replace(" ", "").length > 19) {
                    s.delete(s.length - 1, s.length)
                }
            }
            override fun beforeTextChanged(cardNumber: CharSequence, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(cardNumber: CharSequence, start: Int, before: Int, count: Int) {
                val paymentGateway = misc.getPaymentGateway(cardNumber.toString())
                if (paymentGateway != null) {
                    val gatewayLogo = misc.getSiteIcon(paymentGateway, nameInput.currentTextColor)
                    if (gatewayLogo != null) {
                        paymentsGatewayIcon.setImageDrawable(gatewayLogo)
                        paymentsGatewayIcon.visibility = View.VISIBLE
                    } else paymentsGatewayIcon.visibility = View.GONE
                } else paymentsGatewayIcon.visibility = View.GONE
            }
        })

        atmPinLayout = findViewById (R.id.AtmPinLayout)
        atmPinLayout.visibility = View.GONE
        isAtmCard = findViewById (R.id.isAtmCard)
        isAtmCard.isChecked = false

        isAtmCard.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) atmPinLayout.visibility = View.GONE
            else atmPinLayout.visibility = View.VISIBLE
        }

        hasRfidChip = findViewById (R.id.hasRfidChip)

        atmPinInput = findViewById (R.id.AtmPinInput)
        atmPinInput.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
        atmPinInput.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING

        toDate = findViewById (R.id.ToDateInput)
        toDate.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
        toDate.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (toDate.text?.length == 5) {
                    val yearFormat = SimpleDateFormat("yy", Locale.US)
                    val monthFormat = SimpleDateFormat("M", Locale.US)
                    val year = yearFormat.format(Date()).toString().toInt()
                    val month = monthFormat.format(Date()).toString().toInt()
                    if ( (toDate.text?.take(2).toString().toInt() <= month && toDate.text?.takeLast(2).toString().toInt() == year) || toDate.text?.takeLast(2).toString().toInt() < year) {
                        toDate.text!!.clear()
                        toDate.error = "This card has expired"
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (toDate.text?.length == 2) {
                    try {
                        toDate.text!!.append("/")
                        if (toDate.text?.take(2).toString().toInt() !in 1..12) {
                            toDate.text!!.clear()
                            toDate.error = "Please enter a valid expiry month"
                        }
                    } catch (backspace: java.lang.NumberFormatException) {
                        toDate.text!!.clear()
                    }
                }
            }
        })

        securityCode = findViewById (R.id.CVVInput)
        cardholderNameInput = findViewById (R.id.CardholderInput)
        cardholderNameInput.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING

        notesInput = findViewById (R.id.notesInput)
        notesInput.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING


        customFieldsView = findViewById(R.id.custom_fields)
        customFieldsView.layoutManager = LinearLayoutManager(this)

        customFieldsData = mutableListOf()
        customFieldsAdapter = CustomFieldsAdapter (customFieldsData)
        customFieldsView.adapter = customFieldsAdapter

        addCustomFieldButton = findViewById(R.id.addCustomFieldButton)
        addCustomFieldButton.isEnabled = true
        addCustomFieldButton.setOnClickListener {
            customFieldsData.add(IOUtilities.CustomField("", "", false))
            customFieldsAdapter.notifyItemInserted(customFieldsData.size)
            customFieldsView.invalidate()
            customFieldsView.refreshDrawableState()
            customFieldsView.scheduleLayoutAnimation()
        }

        return true
    }

    private fun saveItem () {
        var dateCreated = Instant.now().epochSecond

        if (itemId != null) {
            dateCreated = card.dateCreated!!
            vault.card?.remove(io.getCard(itemId!!, vault))
        }

        if (cardNumberInput.text.toString().replace(" ", "").length < 16) cardNumberInput.error = "Enter a valid 16 digit card number"
        else if (cardNumberInput.text.toString().replace(" ", "").length in 17..18
            || cardNumberInput.text.toString().replace(" ", "").length > 19) cardNumberInput.error = "Enter a valid 19 digit card number"
        else if (securityCode.text.toString().length !in 3..4) securityCode.error = "Enter a valid security code"
        else if (toDate.text.toString().isEmpty()) toDate.error = "Enter an expiry date"
        else if (cardholderNameInput.text.toString().isEmpty()) cardholderNameInput.error = "Enter card holder's name"
        else if (nameInput.text.toString().isEmpty()) nameInput.error = "Enter a name. This can be your bank's name."
        else if (isAtmCard.isChecked && atmPinInput.text.toString().length < 4) atmPinInput.error = "Enter a valid Personal Identification Number"

        else {

            val data = IOUtilities.Card(
                id = itemId ?: UUID.randomUUID().toString(),
                organizationId = null,
                type = io.TYPE_CARD,
                name = nameInput.text.toString(),
                color = cardColor,
                favorite = favorite,
                tagId = tagPicker.getSelectedTagId() ?: tagId,
                dateCreated = dateCreated,
                dateModified = Instant.now().epochSecond,
                frequencyAccessed = frequencyAccessed + 1,
                cardNumber = cardNumberInput.text.toString().filter { !it.isWhitespace() },
                cardholderName = cardholderNameInput.text.toString(),
                expiry = toDate.text.toString(),
                notes = notesInput.text.toString(),
                pin = if (atmPinInput.text.toString().length == 4 && isAtmCard.isChecked) atmPinInput.text.toString() else "",
                securityCode = securityCode.text.toString(),
                customFields = customFieldsData,
                rfid = hasRfidChip.isChecked,
                iconFile = iconFileName
            )

            val encryptedCard = io.encryptCard(data)

            vault.card?.add (encryptedCard)
            io.writeVault(vault)

            if (itemId != null) network.writeQueueTask (encryptedCard, mode = network.MODE_PUT)
            else network.writeQueueTask (encryptedCard, mode = network.MODE_POST)

            crypto.secureStartActivity (
                nextActivity = Dashboard(),
                nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
                keyring = keyring,
                itemId = null
            )

        }

    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadCard(card: IOUtilities.Card): Boolean {

        favorite = if (card.favorite) {
            favoriteButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_star_24)); true
        } else {
            favoriteButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_star_border_24)); false
        }

        tagId = card.tagId
        tagPicker = AddTag (tagId, applicationContext, this@AddCard, keyring)

        nameInput.setText(card.name)

        notesInput.setText(card.notes)

        if (card.cardNumber?.length!! == 16) cardNumberInput.setText(card.cardNumber.replace("....".toRegex(), "$0 "))
        else cardNumberInput.setText(card.cardNumber)

        toDate.setText(card.expiry)
        securityCode.setText(card.securityCode)
        cardholderNameInput.setText(card.cardholderName)

        if (!card.pin.isNullOrEmpty()) {
            isAtmCard.isChecked = true
            atmPinInput.setText(card.pin)
        } else isAtmCard.isChecked = false

        hasRfidChip.isChecked = card.rfid == true

        if (card.customFields != null) {
            customFieldsData = card.customFields
            customFieldsAdapter = CustomFieldsAdapter (customFieldsData)
            customFieldsView.adapter = customFieldsAdapter
            customFieldsAdapter.notifyItemInserted(customFieldsData.size)
            customFieldsView.invalidate()
            customFieldsView.refreshDrawableState()
            customFieldsView.scheduleLayoutAnimation()
        }

        cardColor = card.color

        Handler().postDelayed({ runOnUiThread {
            iconFileName = card.iconFile
            if (iconFileName != null) nameInputIcon.setImageDrawable(misc.getSiteIcon(iconFileName!!, nameInput.currentTextColor))
            else nameInputIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_website_24))
        } }, 100)

        return true
    }

    inner class CustomFieldsAdapter (private val customFields: MutableList<IOUtilities.CustomField>) : RecyclerView.Adapter<CustomFieldsAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {  // create new views
            val customFieldsView: View = LayoutInflater.from(parent.context).inflate(R.layout.custom_field, parent, false)
            customFieldsView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            return ViewHolder(customFieldsView)
        }

        override fun onBindViewHolder(customFieldView: ViewHolder, position: Int) {
            val customField = customFieldsData[customFieldView.adapterPosition]

            var hidden = false

            customFieldView.fieldName.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            customFieldView.fieldValue.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING

            customFieldView.fieldName.setText (customField.name)
            customFieldView.fieldValue.setText (customField.value)

            if (customField.hidden) {
                customFieldView.fieldValue.transformationMethod = PasswordTransformationMethod()
                customFieldView.hideIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_24))
                hidden = true
            } else {
                customFieldView.fieldValue.transformationMethod = null
                customFieldView.hideIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_off_24))
                hidden = false
            }

            customFieldView.fieldName.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) { }
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(data: CharSequence, start: Int, before: Int, count: Int) {
                    addCustomFieldButton.isEnabled = data.isNotEmpty()
                    customFieldsData[customFieldView.adapterPosition].name = data.toString()
                }
            })

            customFieldView.fieldValue.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) { }
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(data: CharSequence, start: Int, before: Int, count: Int) {
                    addCustomFieldButton.isEnabled = data.isNotEmpty()
                    customFieldsData[customFieldView.adapterPosition].value = data.toString()
                }
            })

            customFieldView.deleteIcon.setOnClickListener { view ->
                addCustomFieldButton.isEnabled = true
                Toast.makeText(applicationContext, "Deleted \"${customFieldView.fieldName.text}\"", Toast.LENGTH_SHORT).show()
                customFieldView.fieldName.clearFocus()
                customFieldView.fieldValue.clearFocus()
                try {
                    customFieldsData.remove(customFieldsData[customFieldView.adapterPosition])
                } catch (noItemsLeft: IndexOutOfBoundsException) { }
                customFieldsAdapter.notifyItemRemoved(position)
                customFieldsView.invalidate()
                customFieldsView.refreshDrawableState()
            }

            customFieldView.hideIcon.setOnClickListener { view ->
                if (!hidden) {
                    customFieldView.fieldValue.transformationMethod = PasswordTransformationMethod()
                    customFieldView.hideIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_24))
                    hidden = true
                } else {
                    customFieldView.fieldValue.transformationMethod = null
                    customFieldView.hideIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_off_24))
                    hidden = false
                }
                val tempName = customFieldsData[customFieldView.adapterPosition].name
                val tempValue = customFieldsData[customFieldView.adapterPosition].value
                customFieldsData.remove(customFieldsData[customFieldView.adapterPosition])
                customFieldsData.add(IOUtilities.CustomField(tempName, tempValue, hidden))
            }

        }

        override fun getItemCount(): Int {
            return customFields.size
        }

        inner class ViewHolder (itemLayoutView: View) : RecyclerView.ViewHolder(itemLayoutView) {
            var fieldName: EditText = itemLayoutView.findViewById<View>(R.id.field_name) as EditText
            var fieldValue: EditText = itemLayoutView.findViewById<View>(R.id.field_value) as EditText
            var deleteIcon: ImageView = itemLayoutView.findViewById<View>(R.id.deleteCustomFieldButton) as ImageView
            var hideIcon: ImageView = itemLayoutView.findViewById<View>(R.id.hideCustomFieldButton) as ImageView
        }
    }

    override fun onStop() {
        super.onStop()

        // force wipe keyring
        keyring.XCHACHA_POLY1305_KEY?.fill(0)
        keyring.ED25519_PUBLIC_KEY?.fill(0)
        keyring.ED25519_PRIVATE_KEY?.fill(0)

        // force gc to clear keyring
        System.gc()
        finish()
        finishAffinity()
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
            tagIdGrabber.removeCallbacksAndMessages(null)
        }
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { dialog, _ -> dialog.dismiss() }
        alertDialog.show()
    }


    override fun onPause() {
        if (configData.getBoolean("lockApp", true)) {
            finish()
            finishAffinity()

            // force wipe keyring
            keyring.XCHACHA_POLY1305_KEY?.fill(0)
            keyring.ED25519_PUBLIC_KEY?.fill(0)
            keyring.ED25519_PRIVATE_KEY?.fill(0)

            // force gc to clear keyring
            System.gc()
        }
        super.onPause()
    }

    override fun onUserLeaveHint() {
        if (configData.getBoolean("lockApp", true)) {
            finish()
            finishAffinity()

            // force wipe keyring
            keyring.XCHACHA_POLY1305_KEY?.fill(0)
            keyring.ED25519_PUBLIC_KEY?.fill(0)
            keyring.ED25519_PRIVATE_KEY?.fill(0)

            // force gc to clear keyring
            System.gc()
        }
        super.onPause()
    }

    private fun iconFilePicker () {

        val builder = MaterialAlertDialogBuilder(this@AddCard)
        builder.setCancelable(true)
        val iconsBox: View = layoutInflater.inflate(R.layout.icon_picker_dialog, null)
        builder.setView(iconsBox)
        iconsBox.startAnimation(AnimationUtils.loadAnimation(applicationContext, R.anim.from_bottom))

        val dialog = builder.create()
        dialog.show()

        val iconFileNames = misc.getSiteIconFilenames()
        class GridAdapter(var context: Context, filenames: ArrayList<String>) : BaseAdapter() {
            var listFiles: ArrayList<String>
            init { listFiles = filenames }
            override fun getCount(): Int { return listFiles.size }
            override fun getItem(position: Int): Any { return listFiles[position] }
            override fun getItemId(position: Int): Long { return position.toLong() }
            @SuppressLint("ViewHolder")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = (context.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.site_icon, null)

                val icon = view.findViewById<ImageView>(R.id.icon)
                icon.setImageDrawable(misc.getSiteIcon(listFiles[position], nameInput.currentTextColor))

                val iconName = view.findViewById<TextView>(R.id.iconName)
                iconName.text = listFiles[position].replace("_", "")

                icon.setOnClickListener {
                    iconFileName = listFiles[position]
                    nameInputIcon.setImageDrawable(misc.getSiteIcon(listFiles[position], nameInput.currentTextColor))
                    dialog.dismiss()
                }

                return view!!
            }
        }

        val icons = dialog.findViewById<GridView>(R.id.icons) as GridView
        icons.adapter = GridAdapter(this@AddCard, iconFileNames)

        val searchResults = arrayListOf<String>()
        val searchBar = iconsBox.findViewById<EditText>(R.id.searchBar)
        searchBar.doOnTextChanged { searchTerm, start, count, after ->
            searchResults.clear()
            for (filename in iconFileNames) {
                if (filename.contains(searchTerm.toString().lowercase().replace(" ", ""))) {
                    searchResults.add(filename)
                    icons.adapter = GridAdapter(this@AddCard, searchResults)
                }
            }
        }

        val resetButton = iconsBox.findViewById<MaterialButton>(R.id.resetButton)
        resetButton.setOnClickListener {
            iconFileName = "bank"
            nameInputIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_account_balance_24))
            dialog.dismiss()
        }

    }

}