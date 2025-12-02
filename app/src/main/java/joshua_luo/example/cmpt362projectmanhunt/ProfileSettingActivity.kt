package joshua_luo.example.cmpt362projectmanhunt

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import java.io.File
import androidx.core.content.edit
import java.io.FileOutputStream

class ProfileSettingActivity: AppCompatActivity(), MyDialog.PhotoDialogListener {
    private lateinit var tempImgUri: Uri
    private lateinit var permImgUri: Uri
    private lateinit var cameraResult: ActivityResultLauncher<Intent>
    private lateinit var galleryResult: ActivityResultLauncher<Intent>
    private lateinit var profileViewModel: ProfileSettingsViewModel

    private lateinit var profilePhoto: ImageView
    private lateinit var changeBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var username: TextView
    private lateinit var notification: SwitchCompat
    private lateinit var volumeSlide: SeekBar


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_settings)

        profilePhoto = findViewById(R.id.profile_photo)
        changeBtn = findViewById(R.id.change_photo)
        saveBtn = findViewById(R.id.saveButton)
        cancelBtn = findViewById(R.id.cancelButton)
        username = findViewById(R.id.username)
        notification = findViewById(R.id.notificationSwitch)
        volumeSlide = findViewById(R.id.volumeBar)



        // Permissions
        Util.checkPermissions(this)

        val profilePrefs = getSharedPreferences("ProfilePrefs",MODE_PRIVATE)
        val tempImgFile = File(getExternalFilesDir(null), "temp_profile_photo.jpg")
        val permImgFile = File(getExternalFilesDir(null),"profile_photo.jpg")
        tempImgUri = FileProvider.getUriForFile(this, "joshua_luo.example.cmpt362projectmanhunt", tempImgFile)
        permImgUri = FileProvider.getUriForFile(this, "joshua_luo.example.cmpt362projectmanhunt", permImgFile)

        cameraResult = registerForActivityResult(StartActivityForResult()) {
            result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    val picture = Util.getBitmap(this, tempImgUri)
                    profileViewModel.userImage.value = picture
                }
        }
        galleryResult = registerForActivityResult(StartActivityForResult()) {
            result: ActivityResult ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    val uri = result.data!!.data

                    val picture = Util.getBitmap(this, uri!!)
                    profileViewModel.userImage.value = picture

                    val tempImgFile = File(getExternalFilesDir(null), "temp_profile_photo.jpg")
                    val output = FileOutputStream(tempImgFile)
                    picture.compress(Bitmap.CompressFormat.JPEG, 90, output)
                    output.close()
                }
        }

        changeBtn.setOnClickListener {
            val dialog = MyDialog()
            val bundle = Bundle()
            bundle.putInt(MyDialog.DIALOG_KEY, MyDialog.PHOTO_DIALOG)
            dialog.arguments = bundle
            dialog.show(supportFragmentManager, "PhotoDialog")
        }

        profileViewModel = ViewModelProvider(this)[ProfileSettingsViewModel::class.java]
        profileViewModel.userImage.observe(this) {
            profilePhoto.setImageBitmap(it)
        }
        if (permImgFile.exists()) {
            val bitmap = Util.getBitmap(this, permImgUri)
            profilePhoto.setImageBitmap(bitmap)
        }

        // Username
        username.text = profilePrefs.getString("username_key", "")

        // Notification
        notification.isChecked = profilePrefs.getBoolean("notifications_enabled", true)
        notification.setOnCheckedChangeListener { _, isChecked ->
            profilePrefs.edit { putBoolean("notifications_enabled", isChecked) }
        }

        // Volume
        val savedVolume = profilePrefs.getInt("volume_level", 50)
        volumeSlide.progress = savedVolume

        volumeSlide.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                profilePrefs.edit {
                    putInt("volume_level", progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        saveBtn.setOnClickListener {
            profilePrefs.edit {
                putString("username_key", username.text.toString())
            }

            if (tempImgFile.exists()) {
                tempImgFile.copyTo(permImgFile, overwrite = true)
                tempImgFile.delete()
            }
            // TODO: save username and photo to database later
            finish()
        }
        cancelBtn.setOnClickListener {
            finish()
        }

        if (savedInstanceState != null) {
            username.text = savedInstanceState.getString("username_key")
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("username_key", username.text.toString())

    }
    override fun onTakePhotoSelected() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, tempImgUri)
        cameraResult.launch(intent)
    }
    override fun onTakeGallerySelected() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryResult.launch(intent)
    }

}