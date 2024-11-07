package com.sdavis.styletransfer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.FileUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StyletransferTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                   TransferPicture()
                }
            }
        }
    }
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
fun TransferPicture() {

    val tfliteOption = Interpreter.Options()
    tfliteOption.numThreads = 2

    val predictionInterpreter =Interpreter(FileUtil.loadMappedFile(LocalContext
        .current,
    "prediction.tflite"), tfliteOption)

    val transferInterpreter =Interpreter(FileUtil.loadMappedFile(LocalContext
        .current,
        "transfer.tflite"), tfliteOption)

    val outputPredictShape =  predictionInterpreter.getOutputTensor(0).shape()
    val outputTransformShape = transferInterpreter.getOutputTensor(0).shape()
    val predictOutput = TensorBuffer.createFixedSize(
        outputPredictShape, DataType.FLOAT32
    )

    val transferImageBitmap =BitmapFactory.decodeResource(LocalContext.current
        .resources,
                                            R.drawable.dsc00734)

    val styleImageBitmap =BitmapFactory.decodeResource(LocalContext.current
        .resources,
        R.drawable.style0)

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
    val outputBitmapResized = Bitmap.createScaledBitmap(outputBitmap, 400,
        400, false)

    Image(bitmap = outputBitmapResized.asImageBitmap(),
        contentDescription = "description")
}
