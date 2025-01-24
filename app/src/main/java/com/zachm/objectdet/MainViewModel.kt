package com.zachm.objectdet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File

class MainViewModel : ViewModel() {

    val imageFile: MutableLiveData<File> by lazy { MutableLiveData<File>() }
    val mobileNet: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(true) }
    val efficientDet: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(false) }
    val yolo: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(false) }
    val tracking: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(false) }
}