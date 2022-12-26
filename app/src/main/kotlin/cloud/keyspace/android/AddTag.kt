package cloud.keyspace.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
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
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.size
import com.github.dhaval2404.colorpicker.MaterialColorPickerDialog
import com.github.dhaval2404.colorpicker.listener.ColorListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keyspace.keyspacemobile.NetworkUtilities
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.children
import java.time.Instant
import java.util.*


class AddTag (private val tagId: String?, val context: Context, val appCompatActivity: AppCompatActivity, val keyring: CryptoUtilities.Keyring) {

    private val io: IOUtilities
    private val network: NetworkUtilities

    private var vault: IOUtilities.Vault
    private var decryptedTags: MutableList<IOUtilities.Tag> = mutableListOf()

    private var inflater: LayoutInflater = appCompatActivity.layoutInflater

    lateinit var tagDialog: AlertDialog

    var finalizedTagId: String? = null

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
        decryptedTags.clear()

    }

    private fun editTag (tagToEdit: IOUtilities.Tag?) {
        val colorArray = context.resources.getStringArray(R.array.vault_item_colors)

        var color = colorArray.random()

        var tagId: String = UUID.randomUUID().toString()

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
                        editTagDialog.dismiss()
                        tagDialog!!.show()
                    }
                    .setNegativeButton("Continue editing"){ _, _ -> }
                    .show()
            } else {
                editTagDialog.dismiss()
                tagDialog!!.show()
            }
        }

        if (tagToEdit != null) {
            tagId = tagToEdit.id
            editTag.setText(tagToEdit.name)
            try { tagColorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(tagToEdit.color)) } catch (_: Exception) { color = null }
        } else tagColorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(color))

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

                val encryptedTag = io.encryptTag (
                    IOUtilities.Tag (
                        id = tagId,
                        name = name,
                        color = color,
                        dateCreated = Instant.now().epochSecond,
                        type = io.TYPE_TAG
                    )
                )
                if (tagToEdit != null) {
                    network.writeQueueTask (encryptedTag!!, mode = network.MODE_PUT)
                    for (tagToDelete in vault.tag!!) {
                        if (tagToDelete.id == tagId) {
                            vault.tag?.remove (tagToDelete)
                            break
                        }
                    }
                    vault.tag?.add (encryptedTag)

                    io.writeVault(vault)
                } else {
                    network.writeQueueTask (encryptedTag!!, mode = network.MODE_POST)
                    vault.tag?.add (encryptedTag)

                    io.writeVault(vault)
                }

                vault = io.getVault()
                decryptedTags.clear()
                io.getTags(vault).forEach { decryptedTags.add(io.decryptTag(it)!!) }

                Toast.makeText(context, "Added $name", Toast.LENGTH_LONG).show()

                tagDialog!!.dismiss()
            }

        }
    }

    fun showPicker () {

        val dialogView: View = inflater.inflate (R.layout.pick_tag, null)

        val dialogBuilder = MaterialAlertDialogBuilder(appCompatActivity)

        io.getTags(vault).forEach { decryptedTags.add(io.decryptTag(it)!!) }

        dialogBuilder
            .setView(dialogView)
            .setCancelable(true)
            .setTitle("Pick tag")
            .setNegativeButton("Go back"){ _, _ -> }

        dialogBuilder.create()

        if (dialogView.parent != null) {
            val vGroup: ViewGroup = dialogView.parent as ViewGroup
            vGroup.removeView(dialogView)
        }

        tagDialog = dialogBuilder.show()

        val tagCollection = dialogView.findViewById<View>(R.id.tagCollection) as ChipGroup
        val tapBlurb = dialogView.findViewById<View>(R.id.tapBlurb) as TextView

        var tagColor: String?

        tagCollection.isSelectionRequired = true
        tagCollection.isSingleSelection = true

        val addTagButton = dialogView.findViewById<View>(R.id.addTagButton) as Chip
        val noneButton = dialogView.findViewById<View>(R.id.noneButton) as Chip

        addTagButton.setOnClickListener {
            tagDialog!!.dismiss()
            editTag (null)
        }

        noneButton.setOnClickListener {
            // Todo return null
            finalizedTagId = null
            tagDialog!!.dismiss()
        }

        if (tagId.isNullOrBlank()) noneButton.isChecked = true

        if (decryptedTags.isEmpty()) tapBlurb.text = "Tap the add button below to add a tag." else {
            for (tag in decryptedTags) {

                val tagChip = Chip(appCompatActivity)
                tagChip.id = ViewCompat.generateViewId()
                tagChip.text = tag.name
                tagColor = if (!tag.color.isNullOrBlank()) tag.color else null
                try { tagChip.chipIconTint = ColorStateList.valueOf(Color.parseColor(tagColor)) } catch (_: Exception) { }
                tagChip.isCloseIconVisible = true
                tagChip.isChipIconVisible = true
                tagChip.isCheckable = true
                tagChip.isEnabled = true

                if (!tagId.isNullOrBlank()) {
                        if (tagId == tag.id) {
                            noneButton.isChecked = false
                            tagChip.isChecked = true
                        }
                    }

                tagChip.chipIcon = AppCompatResources.getDrawable(context, R.drawable.ic_baseline_circle_24)

                tagChip.setCloseIconResource(R.drawable.ic_baseline_close_24)
                tagChip.setOnCloseIconClickListener {

                        val builder = MaterialAlertDialogBuilder(appCompatActivity)
                            .setTitle("Delete tag")
                            .setCancelable(true)
                            .setMessage("Would you like to delete \"${tag.name}\"? This will untag all items containing this tag.")
                            .setPositiveButton("Delete"){ _, _ ->
                                network.writeQueueTask (tag.id, mode = network.MODE_DELETE)

                                for (tagToDelete in vault.tag!!) {
                                    if (tagToDelete.id == tag.id) {
                                        try {
                                            vault.tag!!.remove(tagToDelete)
                                        } catch (_: NullPointerException) { }
                                        break
                                    }
                                }

                                io.writeVault(vault)
                                tagCollection.removeView(tagChip)
                                (tagChip.parent as? ViewGroup)?.removeView(tagChip)
                                Toast.makeText(context, "Deleted ${tag.name}", Toast.LENGTH_LONG).show()
                            }

                        builder.setNegativeButton("Go back"){ _, _ -> }
                        val alertDialog: AlertDialog = builder.create()
                        alertDialog.show()

                    }

                tagChip.setOnClickListener {
                        // Todo return tag id
                        finalizedTagId = tag.id
                        tagDialog.dismiss()
                    }

                tagChip.setOnLongClickListener {
                        editTag (tag)
                        true
                    }

                // Prevents creation of duplicate views by checking if a chip containing the tag's text alreay exists. If it doesn't, the chip is created.
                var trimmedChips: MutableList<*> = tagCollection.children.toMutableList()
                trimmedChips.removeAt(0)
                trimmedChips.removeAt(1)

                val chips = mutableListOf<Chip>()
                for (chip in trimmedChips) try { chips.add(chip as Chip) } catch (_: java.lang.ClassCastException) { }

                val chipNames = mutableListOf<String>()
                for (chip in chips) chipNames.add(chip.text.toString())

                if (!chipNames.contains(tag.name)) {
                    (tagChip.parent as? ViewGroup)?.removeView(tagChip)
                    tagCollection.addView(tagChip)
                }

            }
        }
    }

    fun getSelectedTagId (): String? {
        return finalizedTagId
    }

}