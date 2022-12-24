package cloud.keyspace.android

import android.app.ProgressDialog.show
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.github.dhaval2404.colorpicker.MaterialColorPickerDialog
import com.github.dhaval2404.colorpicker.listener.ColorListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keyspace.keyspacemobile.NetworkUtilities
import java.time.Instant
import java.util.*


class AddTag (val context: Context, val appCompatActivity: AppCompatActivity, val keyring: CryptoUtilities.Keyring) {

    private val io: IOUtilities
    private val network: NetworkUtilities

    private val vault: IOUtilities.Vault
    private var decryptedTags: MutableList<IOUtilities.Tag> = mutableListOf()

    init {

        io = IOUtilities (
            applicationContext = context,
            appCompatActivity = appCompatActivity,
            keyring = keyring
        )

        network = NetworkUtilities (
            applicationContext = context,
            appCompatActivity = appCompatActivity,
            keyring = keyring
        )

        vault = io.getVault()
        io.getTags(vault).forEach { decryptedTags.add(io.decryptTag(it)!!) }

        // Log.d("ASAS", decryptedTags.toString())

    }

    fun showPicker () {

        val inflater = appCompatActivity.layoutInflater
        val dialogView: View = inflater.inflate (R.layout.pick_tag, null)

        val dialogBuilder = MaterialAlertDialogBuilder(appCompatActivity)
        dialogBuilder
            .setView(dialogView)
            .setCancelable(true)
            .setTitle("Pick tag")

        var tagDialog: AlertDialog = dialogBuilder.show()

        val tagCollection = dialogView.findViewById<View>(R.id.tagCollection) as ChipGroup
        val tapBlurb = dialogView.findViewById<View>(R.id.tapBlurb) as TextView

        val backButton = dialogView.findViewById<View>(R.id.backButton) as MaterialButton

        var tagColor: String? = null

        tagCollection.isSelectionRequired = true
        tagCollection.isSingleSelection = true

        val addTagButton = dialogView.findViewById<View>(R.id.addTagButton) as Chip

        backButton.setOnClickListener {
            tagDialog.dismiss()
        }

        addTagButton.setOnClickListener {

            val colorArray = context.resources.getStringArray(R.array.vault_item_colors)
            var color = colorArray.random()

            val editTagDialogView: View = inflater.inflate (R.layout.edit_tag, null)
            val editTagDialogBuilder = MaterialAlertDialogBuilder(appCompatActivity)
            editTagDialogBuilder
                .setView(editTagDialogView)
                .setCancelable(false)
                .setTitle("Edit tag")

            val editTag = editTagDialogView.findViewById<View>(R.id.editTag) as EditText
            val editTagBackButton = editTagDialogView.findViewById<View>(R.id.backButton) as MaterialButton
            val saveTagButton = editTagDialogView.findViewById<View>(R.id.saveTagButton) as MaterialButton
            val addColorButton = editTagDialogView.findViewById<View>(R.id.addColorButton) as MaterialButton
            val tagColorIcon = editTagDialogView.findViewById<View>(R.id.tagColor) as ImageView

            val editTagDialog: AlertDialog = editTagDialogBuilder.show()

            editTag.requestFocus()
            editTagDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

            tagColorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(color))

            addColorButton.setOnClickListener {
                MaterialColorPickerDialog.Builder(appCompatActivity)
                    .setColors(colorArray)
                    .setTickColorPerCard(true)
                    .setDefaultColor(color)
                    .setColorListener(object : ColorListener {
                        override fun onColorSelected(colorInt: Int, colorHex: String) {
                            color = colorHex
                            tagColorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(color))
                        }
                    })
                    .setPositiveButton("Select color")
                    .setNegativeButton("Go back")
                    .show()
            }

            editTagBackButton.setOnClickListener {
                if (editTag.text.toString().replace(" ", "").isNotBlank()) {
                    val discardDialogBuilder = MaterialAlertDialogBuilder(appCompatActivity)
                    discardDialogBuilder
                        .setTitle("")
                        .setMessage("Discard changes and go back?")
                        .setPositiveButton("Discard changes"){ _, _ ->
                            val vGroup: ViewGroup = dialogView.parent as ViewGroup
                            vGroup.removeView(dialogView)
                            tagDialog.dismiss()
                            tagDialog = dialogBuilder.show()
                            editTagDialog.dismiss()
                        }
                        .setNegativeButton("Continue editing"){ _, _ -> }
                        .show()
                } else {
                    val vGroup: ViewGroup = dialogView.parent as ViewGroup
                    vGroup.removeView(dialogView)
                    tagDialog.dismiss()
                    tagDialog = dialogBuilder.show()
                    editTagDialog.dismiss()
                }
            }

            saveTagButton.setOnClickListener {

                if (editTag.text.toString().replace(" ", "").isBlank()) {
                    val discardDialogBuilder = MaterialAlertDialogBuilder(appCompatActivity)
                    discardDialogBuilder
                        .setTitle("Empty tag")
                        .setMessage("Tag name cannot be blank")
                        .setNegativeButton("Go back"){ _, _ -> }
                        .show()
                } else {
                    val name = editTag.text.toString()
                    val tagId = UUID.randomUUID().toString()

                    val encryptedTag = io.encryptTag(
                        IOUtilities.Tag (
                            id = tagId,
                            name = name,
                            color = color,
                            dateCreated = Instant.now().epochSecond,
                            type = io.TYPE_TAG
                        )
                    )

                    network.writeQueueTask (encryptedTag!!, mode = network.MODE_POST)
                    vault.tag?.add (
                        encryptedTag
                    )

                    io.writeVault(vault)

                    Toast.makeText(context, "Added $name", Toast.LENGTH_LONG).show()

                    val vGroup: ViewGroup = dialogView.parent as ViewGroup
                    vGroup.removeView(dialogView)
                    tagDialog.dismiss()
                    editTagDialog.dismiss()

                    tagDialog = dialogBuilder.show()
                }

            }

        }

        if (decryptedTags.isEmpty()) tapBlurb.text = "Tap the add button below to add a tag." else {
            for (tag in decryptedTags) {
                var tagChip = Chip(appCompatActivity)
                tagChip.id = ViewCompat.generateViewId()
                tagChip.text = tag.name
                tagColor = if (!tag.color.isNullOrBlank()) tag.color else null
                try { tagChip.chipIconTint = ColorStateList.valueOf(Color.parseColor(tagColor)) } catch (_: Exception) { }
                tagChip.isCloseIconVisible = true
                tagChip.isChipIconVisible = true
                tagChip.isCheckable = true
                tagChip.isEnabled = true
                tagChip.chipIcon = AppCompatResources.getDrawable(context, R.drawable.ic_baseline_circle_24)

                tagCollection.addView(tagChip)

                tagChip.setCloseIconResource(R.drawable.ic_baseline_close_24)
                tagChip.setOnCloseIconClickListener {

                    val builder = MaterialAlertDialogBuilder(appCompatActivity)
                    builder.setTitle("Delete tag")
                    builder.setMessage("Would you like to delete \"${tag.name}\"? This will untag all items containing this tag.")
                    builder.setPositiveButton("Delete"){ _, _ ->

                        network.writeQueueTask (tag.id, mode = network.MODE_DELETE)
                        vault.tag?.forEach { if (it.id == tag.id) vault.tag.remove(it) }
                        io.writeVault(vault)

                        tagCollection.removeView(tagChip)

                        (tagChip.parent as? ViewGroup)?.removeView(tagChip)

                        Toast.makeText(context, "Deleted ${tag.name}", Toast.LENGTH_LONG).show()

                        tagDialog.dismiss()

                    }

                    builder.setNegativeButton("Go back"){ _, _ -> }
                    val alertDialog: AlertDialog = builder.create()
                    alertDialog.setCancelable(false)
                    alertDialog.show()

                }

                (tagChip.parent as? ViewGroup)?.removeView(tagChip)
                tagCollection.addView(tagChip)

            }

        }

    }

    private fun initializeTag (vault: IOUtilities.Vault): Boolean {
        /*val inflater = layoutInflater
        val dialogView: View = inflater.inflate (R.layout.pick_tag, null)
        val dialogBuilder = MaterialAlertDialogBuilder(this)
        dialogBuilder
            .setView(dialogView)
            .setCancelable(true)

        val tagDialog: AlertDialog = dialogBuilder.show()

        val tagCollection = dialogView.findViewById<View>(R.id.tagCollection) as ChipGroup
        val tagNameInputLayout = dialogView.findViewById<View>(R.id.tagNameInputLayout) as TextInputLayout
        val tagName = dialogView.findViewById<View>(R.id.tagNameInput) as TextInputEditText
        val tagColorCircle = dialogView.findViewById<View>(R.id.tagColor) as ImageView
        val addTagButton = dialogView.findViewById<View>(R.id.addTagButton) as MaterialButton
        val tagColorButton = dialogView.findViewById<View>(R.id.tagColorButton) as MaterialButton
        val backButton = dialogView.findViewById<View>(R.id.backButton) as TextView
        var tagColor: String? = null

        tagCollection.isSelectionRequired = true
        tagCollection.isSingleSelection = true

        if (vault.tag?.size!! > 0) {
            for (tag in vault.tag) {
                val decryptedTag = io.decryptTag (tag)!!
                var tagChip = Chip(this@AddNote)
                tagChip.id = ViewCompat.generateViewId()
                tagChip.text = decryptedTag.name
                tagChip.chipCornerRadius = 50f
                tagChip.chipMinHeight = 90f
                tagChip.textSize = 20f
                tagColor = if (!decryptedTag.color.isNullOrBlank()) decryptedTag.color else null
                try {tagChip.chipIconTint = ColorStateList.valueOf(Color.parseColor(tagColor))} catch (_: Exception) {}
                tagChip.isCloseIconVisible = true
                tagChip.isChipIconVisible = true
                tagChip.chipIcon = getDrawable(R.drawable.ic_baseline_circle_24)
                tagChip.setOnClickListener {
                    tagName.setText(decryptedTag.name)
                    try { tagColorCircle.imageTintList = ColorStateList.valueOf(Color.parseColor(tagColor)) } catch (noSuchTag: IllegalArgumentException) { tagColorCircle.imageTintList = tagName.textColors }
                }

                tagCollection.addView(tagChip)

                tagChip.setCloseIconResource(R.drawable.ic_baseline_close_24)
                tagChip.setOnCloseIconClickListener {

                    val builder = MaterialAlertDialogBuilder(this@AddNote)
                    builder.setTitle("Delete tag")
                    builder.setMessage("Would you like to delete \"${decryptedTag.name}\"?")
                    builder.setPositiveButton("Delete"){ _, _ ->

                        for (existingTag in vault.tag) {

                            val decryptedTag = io.decryptTag (tag)!!

                            if (tagChip.text.toString().trim().lowercase() == decryptedTag.name.trim().lowercase()) {

                                tagCollection.removeView(tagChip)
                                (tagChip.parent as? ViewGroup)?.removeView(tagChip)
                                vault.tag.remove(existingTag)
                                io.writeVault(vault)

                                Toast.makeText(applicationContext, "Deleted ${decryptedTag.name}", Toast.LENGTH_LONG).show()

                                network.writeQueueTask (itemId!!, mode = network.MODE_DELETE)
                                break
                            }
                        }

                    }
                    builder.setNegativeButton("Go back"){ _, _ -> }
                    val alertDialog: AlertDialog = builder.create()
                    alertDialog.setCancelable(false)
                    alertDialog.show()

                }

                (tagChip.parent as? ViewGroup)?.removeView(tagChip)
                tagCollection.addView(tagChip)

            }
        } else {
            tagCollection.visibility = View.GONE
        }

        if (!tagId.isNullOrBlank()) {
            try {
                tagName.setText(io.decryptTag(io.getTag(tagId!!, vault)!!)?.name)
                tagColor = io.decryptTag(io.getTag(tagId!!, vault)!!)?.color
                try { tagColorCircle.imageTintList = ColorStateList.valueOf(Color.parseColor(tagColor))  } catch (noSuchTag: IllegalArgumentException) { tagColorCircle.imageTintList = tagName.textColors }
            } catch (noSuchTag: NullPointerException) {
                try {
                    val data = IOUtilities.Note (
                        id = note.id,
                        organizationId = null,
                        type = note.type,
                        notes = note.notes,
                        color = note.color,
                        favorite = note.favorite,
                        tagId = null,
                        dateCreated = note.dateCreated,
                        dateModified = note.dateModified,
                        frequencyAccessed = note.frequencyAccessed
                    )

                    vault.note?.remove(io.getNote(itemId!!, vault))
                    vault.note?.add (io.encryptNote(data))
                    io.writeVault(vault)
                } catch (noSuchItem: UninitializedPropertyAccessException) {

                }

            }
        }

        tagColorButton.setOnClickListener {
            MaterialColorPickerDialog.Builder(this@AddNote)
                .setColors(resources.getStringArray(R.array.vault_item_colors))
                .setTickColorPerCard(true)
                .setDefaultColor(tagColor.toString())
                .setColorListener(object : ColorListener {
                    @SuppressLint("UseCompatTextViewDrawableApis")
                    override fun onColorSelected(color: Int, colorHex: String) {
                        tagColor = colorHex
                        tagColorCircle.imageTintList = ColorStateList.valueOf(color)
                    }
                })
                .show()
        }

        if (!tagName.text.isNullOrEmpty()) tagNameInputLayout.isEndIconVisible = true
        tagNameInputLayout.setEndIconOnClickListener {
            tagColorCircle.imageTintList = tagName.textColors

            val builder = MaterialAlertDialogBuilder(this@AddNote)
            builder.setTitle("Delete tag")
            builder.setMessage("Would you like to delete \"${tagName.text.toString()}\"?")
            builder.setPositiveButton("Delete"){ _, _ ->

                for (existingTag in vault.tag) {

                    val decryptedTag = io.decryptTag (existingTag)!!

                    for (index in 0 until tagCollection.childCount) {
                        try { val chip = tagCollection.getChildAt(index) as Chip
                            if (chip.text.toString().trim().lowercase().contains(tagName.text.toString().trim().lowercase())) {
                                (chip.parent as? ViewGroup)?.removeView(chip)
                                break
                            } } catch (_: NullPointerException) {} catch (_: ClassCastException) {}
                    }

                    if (tagName.text.toString().trim().lowercase() == decryptedTag.name.trim().lowercase()) {

                        tagName.text?.clear()
                        vault.tag.remove(existingTag)
                        io.writeVault(vault)

                        Toast.makeText(applicationContext, "Deleted ${decryptedTag.name}", Toast.LENGTH_LONG).show()

                        network.writeQueueTask (existingTag.id, mode = network.MODE_DELETE)
                        break
                    }
                }

            }
            builder.setNegativeButton("Go back"){ _, _ -> }
            val alertDialog: AlertDialog = builder.create()
            alertDialog.setCancelable(false)
            alertDialog.show()
        }

        addTagButton.setOnClickListener {  // save data
            if (tagName.text.toString().length < 2) {
                tagName.error = "Invalid tag name"
            } else {
                for (tag in vault.tag)  {
                    val decryptedTag = io.decryptTag (tag)!!
                    if (tagName.text.toString().trim().lowercase() == decryptedTag.name.trim().lowercase()) {
                        tagId = decryptedTag.id
                        tagName.setText(decryptedTag.name)
                        break
                    } else tagId = null
                }

                val tag = io.encryptTag(IOUtilities.Tag(
                    id = tagId ?: UUID.randomUUID().toString(),
                    name = tagName.text.toString(),
                    type = io.TYPE_TAG,
                    dateCreated = Instant.now().epochSecond,
                    color = tagColor.toString()
                ))!!

                vault.tag.add (tag)

                io.writeVault(vault)
                Toast.makeText(applicationContext, "Added tag!", Toast.LENGTH_LONG).show()
                network.writeQueueTask (tag, mode = network.MODE_POST)

                tagDialog.dismiss()
            }
        }

        backButton.setOnClickListener {
            tagDialog.dismiss()
        }
        */
        return true
    }

}