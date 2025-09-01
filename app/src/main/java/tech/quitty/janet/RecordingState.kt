package tech.quitty.janet

import androidx.lifecycle.MutableLiveData
import okhttp3.Call

object RecordingState {
    val isRecording = MutableLiveData<Boolean>(false)
    val isUploading = MutableLiveData<Boolean>(false)
    val uploadResponse = MutableLiveData<String>()
    var activeCall: Call? = null
}
