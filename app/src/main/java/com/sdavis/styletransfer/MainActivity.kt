package com.sdavis.styletransfer

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.FileUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.sdavis.styletransfer.ui.theme.StyletransferTheme
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.DequantizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.concurrent.Executors
import kotlin.math.min
import android.Manifest
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import java.util.concurrent.ExecutorService
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.PermissionChecker
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale


typealias LumaListener = (luma: Double) -> Unit

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    private val isComplete = mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permissions
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        // Set up the listeners for take photo and video capture buttons
        cameraExecutor = Executors.newSingleThreadExecutor()

        val launcher =
            registerForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
               isComplete.value = isGranted
            }

        launcher.launch(Manifest.permission.CAMERA)

        setContent{
            Palette(isCameraReady = isComplete.value, cameraProviderFuture =
            cameraProviderFuture)
        }
    }


    fun bindPreview(
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        val preview: Preview = Preview.Builder()
            .build()

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.setSurfaceProvider(previewView.surfaceProvider)
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
    }

    @Composable
    fun Palette(isCameraReady : Boolean?,
                cameraProviderFuture: ListenableFuture<ProcessCameraProvider>){
        when(isCameraReady)
        {
            true -> CameraPreview(cameraProviderFuture = cameraProviderFuture)
            false -> Text(text = "I need camera permission please")
            null -> Text(text = "Waiting for camera")
        }
    }

    @Composable
    fun CameraPreview(cameraProviderFuture: ListenableFuture<ProcessCameraProvider>) {

        val lifecycleOwner = LocalLifecycleOwner.current
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {

                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    scaleType = PreviewView.ScaleType.FILL_START
                    post {
                        cameraProviderFuture.addListener(Runnable {
                            val cameraProvider = cameraProviderFuture.get()
                            bindPreview(
                                cameraProvider,
                                lifecycleOwner,
                                this,
                            )
                        }, ContextCompat.getMainExecutor(context))
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }


    private fun processInputImage(
        image: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): TensorImage? {
        val height = image.height
        val width = image.width
        val cropSize = min(height, width)
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeWithCropOrPadOp(cropSize, cropSize))
            .add(
                ResizeOp(
                    targetHeight,
                    targetWidth,
                    ResizeOp.ResizeMethod.BILINEAR
                )
            )
            .add(NormalizeOp(0f, 255f))
            .build()
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(image)
        return imageProcessor.process(tensorImage)
    }

    private fun getOutputImage(output: TensorBuffer): Bitmap? {
        val imagePostProcessor = ImageProcessor.Builder()
            .add(DequantizeOp(0f, 255f)).build()
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(output)
        return imagePostProcessor.process(tensorImage).bitmap
    }

    @Composable
    fun PreviewCamera() {
        PreviewView(LocalContext.current)
    }

    @Composable
    fun TransferPicture() {

        val tfliteOption = Interpreter.Options()
        tfliteOption.numThreads = 2

        val predictionInterpreter = Interpreter(
            FileUtil.loadMappedFile(
                LocalContext
                    .current,
                "prediction.tflite"
            ), tfliteOption
        )

        val transferInterpreter = Interpreter(
            FileUtil.loadMappedFile(
                LocalContext
                    .current,
                "transfer.tflite"
            ), tfliteOption
        )

        val outputPredictShape =
            predictionInterpreter.getOutputTensor(0).shape()
        val outputTransformShape =
            transferInterpreter.getOutputTensor(0).shape()
        val predictOutput = TensorBuffer.createFixedSize(
            outputPredictShape, DataType.FLOAT32
        )

        val transferImageBitmap = BitmapFactory.decodeResource(
            LocalContext.current
                .resources,
            R.drawable.dsc00734
        )

        val styleImageBitmap = BitmapFactory.decodeResource(
            LocalContext.current
                .resources,
            R.drawable.style0
        )

        val transferTensor = processInputImage(transferImageBitmap, 384, 384)
        val styleTensor = processInputImage(styleImageBitmap, 256, 256)

        predictionInterpreter.run(styleTensor?.buffer, predictOutput.buffer)

        val transformInput =
            arrayOf(transferTensor?.buffer, predictOutput.buffer)
        val outputImage = TensorBuffer.createFixedSize(
            outputTransformShape, DataType.FLOAT32
        )
        transferInterpreter.runForMultipleInputsOutputs(
            transformInput,
            mapOf(Pair(0, outputImage.buffer))
        )
        val outputBitmap = getOutputImage(outputImage)!!
        val outputBitmapResized = Bitmap.createScaledBitmap(
            outputBitmap, 400,
            400, false
        )

        Image(
            bitmap = outputBitmapResized.asImageBitmap(),
            contentDescription = "description"
        )
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

}
