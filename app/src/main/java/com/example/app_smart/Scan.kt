@file:Suppress("DEPRECATION")

package com.example.app_smart

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.vision.barcode.Barcode
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@Suppress("DEPRECATION")
class Scan : Fragment() {

    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var btnScan: Button
    private lateinit var barcodeTextView: TextView
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        (requireActivity() as AppCompatActivity).supportActionBar?.hide()
        val view = inflater.inflate(R.layout.fragment_scan, container, false)

        btnScan = view.findViewById(R.id.btnScan)
        previewView = view.findViewById(R.id.previewView)
        barcodeTextView = view.findViewById(R.id.barcodeTextView)



        btnScan.setOnClickListener {
            scanBarcode()
        }

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.ALL_FORMATS
            )
            .build()

        barcodeScanner = BarcodeScanning.getClient(options)
        cameraExecutor = Executors.newSingleThreadExecutor()

        return view
    }

    private fun scanBarcode() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        } else {
            startCamera()
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val mediaImage = imageProxy.image
                        val image = mediaImage?.let { InputImage.fromMediaImage(it, rotationDegrees) }
                        if (image != null) {
                            processImage(image)
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageAnalysis
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Error: ${ex.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    data class Product(
        val name: String ,
        val price: Double ,
        val barcode: String ,

    )

    @SuppressLint("SetTextI18n")
    private fun processImage(image: InputImage) {
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val barcodeValue = barcode.rawValue

                    barcodeTextView.text ="Barcode number is: $barcodeValue"

                    val databaseReference = FirebaseDatabase.getInstance().reference
                    val productsReference = databaseReference.child("products")

                    if (barcodeValue != null) {
                        productsReference.child(barcodeValue).addListenerForSingleValueEvent(object :
                            ValueEventListener {
                            override fun onDataChange(dataSnapshot: DataSnapshot) {
                                val product = dataSnapshot.getValue(Product::class.java)

                                // Call displayProductInfo to show product information based on the comparison
                                displayProductInfo(product, barcodeValue)
                            }

                            override fun onCancelled(databaseError: DatabaseError) {
                                // Handle the error if the query is canceled
                                Log.e(TAG, "Database query canceled: ${databaseError.message}")
                            }
                        })
                    }

                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Barcode scanning failed: ${exception.message}")
            }
    }

    @SuppressLint("SetTextI18n")
    private fun displayProductInfo(product: Product?, barcodeValue: String) {
        val productNameTextView = view?.findViewById<TextView>(R.id.tvProductName)
        val productPriceTextView = view?.findViewById<TextView>(R.id.tvPrice)
        val productBarcodeTextView = view?.findViewById<TextView>(R.id.tvBarcode)

        val trimmedBarcodeValue = barcodeValue.trim()

        if (product != null && product.barcode.trim() == trimmedBarcodeValue) {
            // Update the UI to display product information
            val productName = product.name
            val productPrice = product.price
            val productBarcode = product.barcode

            productNameTextView?.text = "Product Name: $productName"
            productPriceTextView?.text = "Price: $productPrice"
            productBarcodeTextView?.text = "Barcode: $productBarcode"

            // Additional UI updates specific to matching barcode if needed
        }  else {
            // Clear the UI or display an error message if the product is not found
            barcodeTextView.text = "Product with barcode $barcodeValue not found."
            productNameTextView?.text = ""
            productPriceTextView?.text = ""
            productBarcodeTextView?.text = ""
            // Clear other product info fields if necessary
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            }
        }
    }

    companion object {
        private const val TAG = "ScanFragment"
        private const val CAMERA_PERMISSION_REQUEST = 1001
    }
}

    


