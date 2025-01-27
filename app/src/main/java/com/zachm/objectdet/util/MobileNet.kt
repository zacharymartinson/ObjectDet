package com.zachm.objectdet.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.camera.view.PreviewView
import com.zachm.objectdet.tracking.Detection
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.model.Model.Device
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * This is a class for MobileNet SSD Object Detection. It uses Tflite.
 * It is intended to be used with MobileNetv2 but has support for ObjectDetector even though it doesn't work with newer models.
 * Model is light weight at 320x320 and has a total time of 19ms with an mAP of 20 on COCO.
 * This class is built off of the standard model on COCO but if you add and change the Dense layer with different classes you will have to update it.
 *
 * Zachary Martinson * 2024
 */
class MobileNet(private val ctx: Context, private val modelType: String) {
    private val model: Interpreter
    private var items: MutableList<String> = mutableListOf()
    private var classNum = 91 //Default

    private val detectionAnchor = Array(1) {FloatArray(100)}
    private val detectionBoxes = Array(1) {Array(100){FloatArray(4)}}
    private val detectionClasses = Array(1) {FloatArray(100)}
    private val detectionMulticlassScores = Array(1) {Array(100){FloatArray(classNum)}}
    private val detectionScores = Array(1) {FloatArray(100)}
    private val numDetections = FloatArray(1)
    private val rawDetectionBoxes = Array(1) {Array(1917){FloatArray(4)}}
    private val rawDetectionScores = Array(1) {Array(1917){FloatArray(classNum)}}

    var boxes: MutableList<Rect> = mutableListOf()
    var scores: MutableList<Float> = mutableListOf()
    var item: MutableList<String> = mutableListOf()
    var detections: MutableList<Detection> = mutableListOf()

    init {
        model = Interpreter(loadModelFile(modelType))
        loadPBText("mscoco_complete_label_map.pbtxt")
        classNum = items.size
    }

    /**
     * Casts our outputs to Arrays. This is the order they are loaded into the Interpreter and not by Index.
     */
    private val results: Map<Int, Any> = mutableMapOf(
        0 to rawDetectionBoxes,
        1 to detectionAnchor,
        2 to numDetections,
        3 to rawDetectionScores,
        4 to detectionBoxes,
        5 to detectionClasses,
        6 to detectionScores,
        7 to detectionMulticlassScores
    )

    /**
     * Loads an asset as a ByteBuffer for our Interpreter.
     */
    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val descriptor = ctx.assets.openFd(fileName)
        val input = FileInputStream(descriptor.fileDescriptor)
        val channel = input.channel
        return channel.map(FileChannel.MapMode.READ_ONLY,descriptor.startOffset,descriptor.declaredLength)
    }

    /**
     * Loads all the classes from a selected .pbtxt.
     */
    private fun loadPBText(fileName: String) {
        val text = readFile(fileName)

        //Just a for loop on each line and adding display names to items
        text.lines().forEach { line ->
            if(line.contains("display_name:")) {
                items.add(line.replace("display_name: ","").replace("\"",""))
            }
        }
    }

    /**
     * Reads a file in Assets and returns a String.
     */
    private fun readFile(fileName: String) : String {
        ctx.assets.open(fileName).use {
            return it.bufferedReader().readText()
        }
    }

    /**
     * Sets up Object Detection API.
     * Only works on Tensorflow 1 Models.
     */
    private fun setupObjectDetector(): ObjectDetector {
        val options = ObjectDetectorOptions
            .builder()
            //.setBaseOptions(BaseOptions.builder().useGpu().build()) TODO Create a check for this
            .setMaxResults(1)
            .build()

        //You can create one from a buffer which you need for an Interpreter anyways.
        return ObjectDetector.createFromBufferAndOptions(loadModelFile(modelType),options)
    }

    /**
     * Runs the model off of an bitmap.
     */
    fun detect(image: Bitmap, preview: PreviewView, degrees: Int) {
        val resized = Bitmap.createScaledBitmap(image, 320, 320, true)
        val tensor = bitmapToUINT8Array(resized, degrees) // 1,320,320,3

        //Since we have multiple outputs we use this function
        model.runForMultipleInputsOutputs(arrayOf(tensor), results)

        //We need to clear our result lists.
        boxes.clear()
        scores.clear()
        item.clear()
        detections.clear()

        //To get the resolution of the actual surface (the view the user is seeing)
        val width = preview.measuredWidth
        val height = preview.measuredHeight

        for(i in 0 .. 99) {
            //Quick filter array setup.
            val filter = listOf(items.indexOf("refrigerator"),items.indexOf("banana"),71,68,69,66,82)

            if(detectionScores[0][i] > 0.5f && !filter.contains(detectionClasses[0][i].toInt())) {

                //Typical x1 y1 x2 y2 pattern. We multiple it by the surface's resolution.
                var top = (detectionBoxes[0][i][0] * height)
                var left = (detectionBoxes[0][i][1] * width)
                var bottom = (detectionBoxes[0][i][2] * height)
                var right = (detectionBoxes[0][i][3] * width)

                val boxWidth = right - left
                val boxHeight = bottom - top
                val aspectRatio = (image.width/image.height).toFloat() //Make sure this is image input and not the resized's or view's.

                //Gets the edge of the box that needs to be scaled. Since we need to do it from both sides we divide it by half again.
                if(degrees == 90) {
                    left -= (boxWidth/4) * aspectRatio
                    right += (boxWidth/4) * aspectRatio
                }
                else {
                    top -= (boxHeight/4) * aspectRatio
                    bottom += (boxHeight/4) * aspectRatio
                }

                boxes.add(Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()))
                scores.add(detectionScores[0][i])
                item.add(items[detectionClasses[0][i].toInt()])

                detections.add(Detection(Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()), detectionScores[0][i], items[detectionClasses[0][i].toInt()]))

            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true);
    }


    /**
     * Converts a bitmap to a UINT8 Array designed for MobileNet SSD.
     * Uses IntArray to speed up the process significantly and returns a Byte Array (1,320,320,3)
     */
    private fun bitmapToUINT8Array(image: Bitmap, degrees: Int): Array<Array<Array<ByteArray>>> {
        val img = rotateBitmap(image,degrees.toFloat())
        val tensor = Array(1) { Array(320) { Array(320) { ByteArray(3) } } }

        val pixels = IntArray(320*320)
        img.getPixels(pixels,0,320,0,0,320,320)


        for (x in 0 until 320) {
            for (y in 0 until 320) {
                val pixel = pixels[x*320+y]


                tensor[0][x][y][0] = Color.red(pixel).toByte()
                tensor[0][x][y][1] = Color.green(pixel).toByte()
                tensor[0][x][y][2] = Color.blue(pixel).toByte()
            }
        }

        return tensor
    }

    companion object {
        const val mobileNetV2 = "MobileNetV2.tflite"
        const val mobileNetV1 = "MobileNetV1.tflite"
    }
}