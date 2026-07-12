package com.m4isper.kmpmlbench

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m4isper.kmpmlbench.benchmark.data.engine.LiteRtClassificationEngine
import com.m4isper.kmpmlbench.benchmark.data.engine.OnnxClassificationEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device regression tests for the real Android classification engines.
 *
 * Each test loads the bundled model straight from the app's packed assets
 * (the same path the production UI uses) and runs a single inference on the
 * real bundled photo, asserting the engine returns a valid, well-formed
 * classification result and that the prediction matches the photo's known
 * label (top-5 accuracy == 1.0). This replaces the manual UI-driven
 * verification of the benchmark screen.
 */
@RunWith(AndroidJUnit4::class)
class EngineBenchmarkInstrumentationTest {

    @Test
    fun liteRtClassificationRunsOnDevice() {
        val task = ClassificationTask()
        val engine = LiteRtClassificationEngine(task)
        try {
            engine.initialize()
            val output = engine.infer(task.createInput())
            val q = output.quality as ClassificationQualityMetrics
            Log.i("BENCH", "LiteRT predicted=${q.predictedClass} conf=${q.confidence} top5=${q.topK}")

            assertTrue("confidence must be in (0, 1]", q.confidence > 0.0 && q.confidence <= 1.0)
            assertEquals("top-5 expected", 5, q.topK.size)
            assertTrue("predicted class must be named", q.predictedClass.isNotBlank())
            assertTrue("softmax must be sorted descending", q.topK[0].second >= q.topK[1].second)
            assertEquals("top-5 accuracy must be 1.0", 1.0, q.accuracy, 0.0)
        } finally {
            engine.close()
        }
    }

    @Test
    fun onnxClassificationRunsOnDevice() {
        val task = ClassificationTask()
        val engine = OnnxClassificationEngine(task)
        try {
            engine.initialize()
            val output = engine.infer(task.createInput())
            val q = output.quality as ClassificationQualityMetrics
            Log.i("BENCH", "ONNX predicted=${q.predictedClass} conf=${q.confidence} top5=${q.topK}")

            assertTrue("confidence must be in (0, 1]", q.confidence > 0.0 && q.confidence <= 1.0)
            assertEquals("top-5 expected", 5, q.topK.size)
            assertTrue("predicted class must be named", q.predictedClass.isNotBlank())
            assertTrue("softmax must be sorted descending", q.topK[0].second >= q.topK[1].second)
            assertEquals("top-5 accuracy must be 1.0", 1.0, q.accuracy, 0.0)
        } finally {
            engine.close()
        }
    }
}
