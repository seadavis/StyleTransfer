package com.sdavis.styletransfer

import android.app.Activity
import android.os.Bundle
import android.view.Choreographer
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sdavis.styletransfer.ui.theme.StyletransferTheme
import com.google.android.filament.utils.*
import java.nio.ByteBuffer
import kotlin.math.sin

class MainActivity : Activity() {

    companion object {
        init { Utils.init() }
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var choreographer: Choreographer
    private lateinit var modelViewer: ModelViewer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple_layout)
        surfaceView = findViewById(R.id.main_sv)
        choreographer = Choreographer.getInstance()
        modelViewer = ModelViewer(surfaceView)
        surfaceView.setOnTouchListener(modelViewer)

        loadGltf("BusterDrone")
        loadEnvironment("venetian_crossroads_2k")
    }

    private fun loadGltf(name: String) {
        val tcm = modelViewer.engine.transformManager

        val buffer = readAsset("models/${name}.gltf")
        modelViewer.loadModelGltf(buffer) { uri -> readAsset("models/$uri") }
        modelViewer.transformToUnitCube()

    }

    private fun loadGlb(name: String) {
        val buffer = readAsset("models/${name}.glb")
        modelViewer.loadModelGlb(buffer)
        modelViewer.transformToUnitCube()
    }

    private fun loadEnvironment(ibl: String) {
        // Create the indirect light source and add it to the scene.
        var buffer = readAsset("envs/$ibl/${ibl}_ibl.ktx")
        KtxLoader.createIndirectLight(modelViewer.engine, buffer).apply {
            intensity = 50_000f
            modelViewer.scene.indirectLight = this
        }

        // Create the sky box and add it to the scene.
        buffer = readAsset("envs/$ibl/${ibl}_skybox.ktx")
        KtxLoader.createSkybox(modelViewer.engine, buffer).apply {
            modelViewer.scene.skybox = this
        }
    }

    private fun readAsset(assetName: String): ByteBuffer {
        val input = assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        return ByteBuffer.wrap(bytes)
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        private val startTime = System.nanoTime()
        override fun doFrame(frameTimeNanos : Long) {
            choreographer.postFrameCallback(this)

            val elapsedTimeSeconds = (frameTimeNanos - startTime).toDouble() / 1_000_000_000
            val tcm = modelViewer.engine.transformManager

            val asset = modelViewer.asset!!
            for(entityId in asset.entities)
            {
               val name =  asset.getName(entityId)
                if(name == "Drone_Turb_M_L_body_0")
                {
                    val boneTransformComponent = tcm.getInstance(entityId)
                    val transform = rotation(Float3(1.0f,0.0f,0.0f), sin
                        (elapsedTimeSeconds.toFloat()) * 20)
                    tcm.setTransform(boneTransformComponent, transpose(transform).toFloatArray())
                }

                if(name == "Drone_Turb_M_R_body_0")
                {
                    val boneTransformComponent = tcm.getInstance(entityId)
                    val transform = rotation(Float3(0.0f,1.0f,0.0f), sin
                        (elapsedTimeSeconds.toFloat()) * 20)
                    tcm.setTransform(boneTransformComponent, transpose(transform).toFloatArray())
                }
            }

            modelViewer.animator?.updateBoneMatrices()

            modelViewer.render(frameTimeNanos)
        }
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameCallback)
    }
}



@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    StyletransferTheme {
        Greeting("Android")
    }
}