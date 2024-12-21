package com.zachm.objectdet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.media.ExifInterface
import android.media.ExifInterface.ORIENTATION_NORMAL
import android.media.ExifInterface.ORIENTATION_ROTATE_180
import android.media.ExifInterface.ORIENTATION_ROTATE_270
import android.media.ExifInterface.ORIENTATION_ROTATE_90
import android.media.ExifInterface.TAG_ORIENTATION
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zachm.objectdet.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val camera = binding.camera
        camera.setOnClickListener { launchCamera() }

        //We check if we came back from CameraActivity after taking a picture.
        if(intent.extras?.containsKey("Picture") == true) {
            val image = getBitmapFromFile(getImageFile())
            camera.background = BitmapDrawable(resources,image)
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
            startActivity(Intent(this, CameraActivity::class.java))
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