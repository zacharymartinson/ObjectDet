package com.zachm.objectdet

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
import androidx.camera.view.PreviewView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zachm.objectdet.databinding.ActivityCameraBinding
import com.zachm.objectdet.tracking.Detection
import com.zachm.objectdet.tracking.DetectionBuffer
import com.zachm.objectdet.util.BoundingBox
import com.zachm.objectdet.util.MobileNet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraViewModel : ViewModel() {

    val mobileNet: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(false) }
    val efficientDet: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(false) }
    val yolo: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(false) }
    val resolutionSelector: MutableLiveData<ResolutionSelector> by lazy { MutableLiveData<ResolutionSelector>() }
    val mobileNetModel: MutableLiveData<MobileNet> by lazy { MutableLiveData<MobileNet>() }
    val boxes: MutableLiveData<List<Rect>> by lazy { MutableLiveData<List<Rect>>() }
    val scores: MutableLiveData<List<Float>> by lazy { MutableLiveData<List<Float>>() }
    val items: MutableLiveData<List<String>> by lazy { MutableLiveData<List<String>>() }
    val detections: MutableLiveData<BoundingBox?> by lazy { MutableLiveData<BoundingBox?>(null) }
    val buffer: MutableLiveData<DetectionBuffer> by lazy { MutableLiveData<DetectionBuffer>() }


    fun setResolutionSelector() {
        var size = Size(320,320)

        when {
            mobileNet.value!! -> { size = Size(320,320) }
            efficientDet.value!! -> { size = Size(512,512) }
        }

        resolutionSelector.value = ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy(size, FALLBACK_RULE_CLOSEST_HIGHER))
            .build()
    }

    fun runMobileNetV2Model(proxy: ImageProxy, surface: PreviewView, rotation: Int) {
        viewModelScope.launch {
            try {
                mobileNetModel.value?.let{
                    val image = proxy.toBitmap()

                    mobileNetModel.value!!.detect(image,surface,rotation)
                    image.recycle()

                    boxes.postValue(mobileNetModel.value?.boxes ?: listOf())
                    scores.postValue(mobileNetModel.value?.scores ?: listOf())
                    items.postValue(mobileNetModel.value?.item ?: listOf())

                    buffer.value!!.addDetections(mobileNetModel.value!!.detections)
                    detections.value = buffer.value!!.bboxes
                }

                proxy.close()

            }
            catch(e: Exception) {
                e.message?.let { msg -> Log.e("CameraX", msg) }
                proxy.close()
            }

        }
    }

    fun updateDetections(newDetections: BoundingBox) {
        detections.value = newDetections
    }

    private fun updateBoxes() {

    }

}