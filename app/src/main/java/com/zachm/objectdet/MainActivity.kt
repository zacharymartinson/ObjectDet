package com.zachm.objectdet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.media.ExifInterface
import android.media.ExifInterface.ORIENTATION_NORMAL
import android.media.ExifInterface.ORIENTATION_ROTATE_180
import android.media.ExifInterface.ORIENTATION_ROTATE_270
import android.media.ExifInterface.ORIENTATION_ROTATE_90
import android.media.ExifInterface.TAG_ORIENTATION
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zachm.objectdet.databinding.ActivityMainBinding
import androidx.activity.viewModels
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.mobileNet.observe(this) { optionSelected(it, binding.mobileNet) }
        viewModel.efficientDet.observe(this) { optionSelected(it, binding.efficientDet) }
        viewModel.yolo.observe(this) { optionSelected(it, binding.yolo) }
        viewModel.tracking.observe(this) { optionSelected(it, binding.tracking) }

        binding.camera.setOnClickListener { launchCamera() }
        binding.mobileNet.setOnClickListener { viewModel.mobileNet.value = !viewModel.mobileNet.value!! }
        binding.efficientDet.setOnClickListener { viewModel.efficientDet.value = !viewModel.efficientDet.value!! }
        binding.yolo.setOnClickListener { viewModel.yolo.value = !viewModel.yolo.value!! }
        binding.tracking.setOnClickListener { viewModel.tracking.value = !viewModel.tracking.value!! }

        //We check if we came back from CameraActivity after taking a picture.
        if(intent.extras?.containsKey("Picture") == true) {
            val image = getBitmapFromFile(getImageFile())
            binding.camera.background = BitmapDrawable(resources,image)
        }
    }

    private fun optionSelected(option: Boolean, view: View) {
        val offWhite = ColorDrawable(getColor(R.color.off_white))
        val lightGray = ColorDrawable(getColor(R.color.light_gray))

        when {
            view == binding.mobileNet && option -> {
                viewModel.efficientDet.value = false
                viewModel.yolo.value = false
            }
            view == binding.efficientDet && option -> {
                viewModel.mobileNet.value = false
                viewModel.yolo.value = false
            }
            view == binding.yolo && option -> {
                viewModel.efficientDet.value = false
                viewModel.mobileNet.value = false
            }
            view == binding.tracking && option -> {
                viewModel.tracking.value = true
            }
        }

        if(option) {
            view.background = lightGray
        }
        else {
            view.background = offWhite
        }
    }

    private fun getImageFile(): File {
        val folder = File(externalCacheDir, "data")
        return File(folder, "image.jpg")
    }

    /**
     * Check if we have permission to use the Camera then launches CameraX.
     */
    private fun launchCamera() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(this, CameraActivity::class.java)

            when {
                viewModel.mobileNet.value!! -> intent.putExtra("Model", "MobileNet")
                viewModel.efficientDet.value!! -> intent.putExtra("Model", "EfficientDet")
                viewModel.yolo.value!! -> intent.putExtra("Model", "YOLO")
            }

            startActivity(intent)
        }
        else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    /**
     * Reads an image file (JPG,PNG,JPEG,TIFF) and converts to Bitmap.
     * Pictures taken in different orientations have the orientation metadeta'd as EXIF. We use it to get the right orientation.
     * There a libraries that can do this as well but this is a simple implementation.
     */
    private fun getBitmapFromFile(file: File): Bitmap {
        var bitmap = BitmapFactory.decodeFile(file.absolutePath)

        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL)

        when (orientation) {
            ORIENTATION_ROTATE_90 -> bitmap = rotateBitmap(bitmap, 90f)
            ORIENTATION_ROTATE_180 -> bitmap = rotateBitmap(bitmap, 180f)
            ORIENTATION_ROTATE_270 -> bitmap = rotateBitmap(bitmap, 270f)
        }

        return bitmap
    }

    /**
     * Rotates an image using degrees.
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true);
    }
}