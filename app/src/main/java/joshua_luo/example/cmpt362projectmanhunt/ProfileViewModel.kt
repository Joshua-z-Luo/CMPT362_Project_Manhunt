package joshua_luo.example.cmpt362projectmanhunt

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileViewModel: ViewModel() {
    val userImage = MutableLiveData<Bitmap>()
}