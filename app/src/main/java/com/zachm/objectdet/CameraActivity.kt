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
import androidx.activity.viewModels
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
    private val viewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bbox.trackFPS = true

        viewModel.items.observe(this) {
            binding.bbox.update(viewModel.boxes.value ?: listOf(),viewModel.scores.value ?: listOf(),viewModel.items.value ?: listOf())
        }

        viewModel.mobileNetModel.observe(this) {
            Log.d("CameraX", "MobileNet: $it")
        }

        if(intent.extras?.containsKey("Model") == true) {
            when(intent.getStringExtra("Model")) {
                "MobileNet" -> {
                    viewModel.mobileNet.value = true
                    viewModel.mobileNetModel.value = MobileNet(this, MobileNet.mobileNetV2)
                }
                "EfficientDet" -> viewModel.efficientDet.value = true
                "YOLO" -> viewModel.yolo.value = true
            }
        }

        //launchCamera() //For Image saving if you are interested.
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

            viewModel.setResolutionSelector()

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(viewModel.resolutionSelector.value!!)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            preview.surfaceProvider = binding.surface.surfaceProvider
            binding.button.setImageDrawable(ResourcesCompat.getDrawable(resources,R.drawable.baseline_done_white_48,null))
            binding.button.setOnClickListener{startActivity(Intent(this,MainActivity::class.java))}

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) {
                val rotation = preview.resolutionInfo!!.rotationDegrees

                when {
                    viewModel.mobileNet.value!! -> {
                        viewModel.runMobileNetV2Model(it,binding.surface,rotation)
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