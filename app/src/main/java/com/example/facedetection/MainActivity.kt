package com.example.facedetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.facedetection.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val REQUEST_CAMERA_PERMISSION=100
    lateinit var binding: ActivityMainBinding
    private var imageUri: Uri?=null
    private var pickImageLauncher=registerForActivityResult(ActivityResultContracts.GetContent()){ uri->
        uri?.let {
            imageUri=it
            binding.ivPost.setImageURI(it)
            analyzeImageFromGallery()
        }
    }

    private val cameraLauncher=registerForActivityResult(ActivityResultContracts.TakePicture()){ success->
        if (success){
            imageUri?.let {
                val bitmap=BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                binding.ivPost.setImageBitmap(bitmap)
                analyzeImageFromCamera(bitmap)
            }
        }
    }
    private lateinit var faceDetector: FaceDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPickImage.setOnClickListener(){
            pickImageLauncher.launch("image/*")
        }
        binding.btnStartCamera.setOnClickListener(){
            val filename=createImageFile()
            filename?.also {
                imageUri=FileProvider.getUriForFile(this,"${applicationContext.packageName}.fileprovider",it)
                cameraLauncher.launch(imageUri)
            }
        }
        val options=FaceDetectorOptions.Builder()
            .enableTracking()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        faceDetector=FaceDetection.getClient(options)

        checkPermissions()
    }

    private fun analyzeImageFromGallery(){
        imageUri?.let {
            val bitmap=BitmapFactory.decodeStream(contentResolver.openInputStream(it))
            if (bitmap!=null){
                val mutableBitmap=bitmap.copy(Bitmap.Config.ARGB_8888,true)
                val canvas=Canvas(mutableBitmap)
                val paint= Paint()
                paint.color=Color.RED
                paint.style=Paint.Style.STROKE
                paint.strokeWidth=5f

                val image=InputImage.fromFilePath(this,it)
                faceDetector.process(image)
                    .addOnSuccessListener { faces->
                        for (face in faces){
                            val bound=face.boundingBox
                            canvas.drawRect(bound,paint)

                        }
                        binding.ivBoundingBox.setImageBitmap(bitmap)
                        binding.ivBoundingBox.visibility=View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Log.e("FaceDetection", "Error: ${e.message}")
                    }
            }
            else{
                Log.e("FaceDetection", "Failed to open input stream for URI: $it")
            }
        }
    }

    private fun analyzeImageFromCamera(bitmap: Bitmap) {
        val fixedBitmap = fixOrientation(bitmap, imageUri!!)
        val image = InputImage.fromBitmap(fixedBitmap, 0)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                val bitmapWithBoundingBox = fixedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(bitmapWithBoundingBox)
                val paint = Paint()
                paint.color = Color.RED
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f

                for (face in faces) {
                    val bounds = face.boundingBox
                    canvas.drawRect(bounds, paint)
                }
                binding.ivBoundingBox.setImageBitmap(bitmapWithBoundingBox)
                binding.ivBoundingBox.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Error: ${e.message}")
            }
    }

    private fun fixOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val exif = inputStream?.let { ExifInterface(it) }
            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: IOException) {
            Log.e("FaceDetection", "Failed to fix orientation: ${e.message}")
            bitmap
        }
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_$timeStamp"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return try {
            File.createTempFile(imageFileName, ".jpg", storageDir)
        } catch (e: Exception) {
            Log.e("FaceDetection", "Failed to create image file: ${e.message}")
            null
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("FaceDetection", "Permissions granted")
            } else {
                Log.e("FaceDetection", "Permission denied")
            }
        }
    }
}