package com.zachm.objectdet

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface.ROTATION_0
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toRectF
import com.zachm.objectdet.databinding.ActivityCameraBinding
import com.zachm.objectdet.util.EfficientDet
import com.zachm.objectdet.util.GoogleObjectDetection
import com.zachm.objectdet.util.MobileNet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CameraX activity with functionality for Image Capture and Object Detection Tasks
 *
 * Zachary Martinson * 2024
 */
class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //launchCamera()
        launchVideoCamera()
    }

    /**
     * Launches a Camera with the intention of taking a picture.
     */
    private fun launchCamera() {
        val processCamera = ProcessCameraProvider.getInstance(this)

        processCamera.addListener({
            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
            val imageCapture = ImageCapture.Builder().build()
            val imageFuture = processCamera.get()
            val button = binding.button
            val preview = Preview.Builder().build()
            preview.surfaceProvider = binding.surface.surfaceProvider

            try {
                imageFuture.unbindAll()
                imageFuture.bindToLifecycle(this,cameraSelector,preview,imageCapture)

                button.setOnClickListener { takePicture(imageCapture) }
            }
            catch(e:Exception) {
                e.message?.let { Log.e("CameraX", it) }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Launches a Video Camera with the intention of Object Detection.
     */
    private fun launchVideoCamera() {
        val processCamera = ProcessCameraProvider.getInstance(this)

        processCamera.addListener({
            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
            val imageFuture = processCamera.get()
            val preview = Preview.Builder().build()

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(320,320), FALLBACK_RULE_CLOSEST_HIGHER))
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            preview.surfaceProvider = binding.surface.surfaceProvider
            binding.button.setImageDrawable(ResourcesCompat.getDrawable(resources,R.drawable.baseline_done_white_48,null))
            binding.button.setOnClickListener{startActivity(Intent(this,MainActivity::class.java))}

            val model = MobileNet(this, MobileNet.mobileNetV2)

            //val model = EfficientDet(this)
            //val model = GoogleObjectDetection()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) {
                val image = it.toBitmap() //TODO This can be improved, it may be better to instantly create a ScaleBitmap here.
                val rotation = preview.resolutionInfo!!.rotationDegrees

                //We do not want to do this on the main thread. It will lag and be unresponsive on top of sharing resources with everything else.
                CoroutineScope(Dispatchers.IO).launch {

                    try {
                        //Run the model and put boxes into memory.
                        model.detect(image,binding.surface,rotation)
                        image.recycle()
                        val boxes = model.boxes
                        val scores = model.scores
                        val items = model.item

                        withContext(Dispatchers.Main) {
                            binding.bbox.update(boxes, scores, items) //Send to bbox view on the Main thread.
                            binding.bbox.trackFPS = true
                            it.close()
                        }

                    }
                    catch(e: Exception) {
                        e.message?.let { msg -> Log.e("CameraX", msg) }
                        it.close()
                    }
                }
            }

            try {
                imageFuture.unbindAll()
                imageFuture.bindToLifecycle(this,cameraSelector,preview,imageAnalysis)
            }
            catch(e:Exception) {
                e.message?.let { Log.e("CameraX", it) }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePicture(imageCapture: ImageCapture) {
        val outputOptions = ImageCapture.OutputFileOptions.Builder(getImageFile()).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i("CameraX", "Saved Image")
                    intent = Intent(applicationContext, MainActivity::class.java)
                    intent.putExtra("Picture",true)
                    startActivity(intent)
                }
                override fun onError(exception: ImageCaptureException) {
                    exception.message?.let { Log.e("CameraX", it) }
                    intent = Intent(applicationContext, MainActivity::class.java)
                    intent.putExtra("Picture",false)
                    startActivity(intent)
                }
            }
        )
    }

    /**
     * CameraX pipeline needs to know when to stop.
     */
    override fun onPause() {
        super.onPause()
        ProcessCameraProvider.getInstance(this).get().unbindAll()

    }

    /**
     * @return Returns a file the is used as the host for taken images.
     */
    private fun getImageFile(): File {
        val folder = File(externalCacheDir, "data")
        if(!folder.isDirectory) {
            folder.mkdirs()
        }
        return File(folder, "image.jpg")
    }
}