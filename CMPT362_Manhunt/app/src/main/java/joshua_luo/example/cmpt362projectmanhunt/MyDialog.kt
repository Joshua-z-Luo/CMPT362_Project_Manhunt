package joshua_luo.example.cmpt362projectmanhunt

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment

class MyDialog: DialogFragment() {
    companion object{
        const val DIALOG_KEY = "dialog"
        const val PHOTO_DIALOG = 1
    }
    interface PhotoDialogListener {
        fun onTakePhotoSelected()
        fun onTakeGallerySelected()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bundle = arguments
        val dialogID = bundle?.getInt(DIALOG_KEY)

        if (dialogID == PHOTO_DIALOG) {
            val options = arrayOf("Take from Camera", "Select from Gallery")

            val builder = AlertDialog.Builder(requireActivity())
            builder.setTitle("Select Profile Image")
                .setItems(options) {_, which ->
                    val listener = activity as? PhotoDialogListener
                    when (which) {
                        0 -> listener?.onTakePhotoSelected()
                        1 -> listener?.onTakeGallerySelected()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}

            return builder.create()
        }

        return super.onCreateDialog(savedInstanceState)
    }
}