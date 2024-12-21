package com.zachm.objectdet.util

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.camera.view.PreviewView
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class GoogleObjectDetection {

    var boxes: MutableList<Rect> = mutableListOf()
    var scores: MutableList<Float> = mutableListOf()
    var item: MutableList<String> = mutableListOf()


    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification()
        .build()

    private val detector = ObjectDetection.getClient(options)

    /**
     * Uses ObjectDetector from GoogleMLKit to run Inference
     */
    fun detect(image: Bitmap, preview: PreviewView, degrees: Int) {
        val input = InputImage.fromBitmap(image, degrees)

        val width = preview.measuredWidth
        val height = preview.measuredHeight

        boxes.clear()
        scores.clear()
        item.clear()

        detector.process(input)
            .addOnFailureListener { Log.e("GoogleObjectDetection", it.message!!) }
            .addOnCompleteListener {
                for(obj in it.result) {
                    val box = obj.boundingBox
                    boxes.add(box)
                    Log.d("ObjectDetector","Boxes:${box}")

                    for(label in obj.labels) {
                        item.add(label.text)
                        scores.add(label.confidence)
                    }
                }
            }
    }
}