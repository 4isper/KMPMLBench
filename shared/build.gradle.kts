import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    android {
        namespace = "com.m4isper.kmpmlbench.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        androidResources { enable = true }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    cocoapods {
        version = "1.0.0"
        summary = "KMPMLBench shared benchmark module"
        homepage = "https://github.com/4isper/KMPMLBench"
        ios.deploymentTarget = "15.1"
        // Real ONNX Runtime (Objective-C API) for on-device inference on iOS.
        // Pulls the full build (onnxruntime-c), so it loads .onnx models directly.
        pod("onnxruntime-objc") {
            version = "1.26.0"
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        jvmMain {
            // The real engines share the same `ai.onnxruntime` API on Desktop/JVM and
            // Android, so their source (src/androidJvmMain/kotlin) is compiled by both
            // targets instead of being duplicated.
            kotlin.srcDir("src/androidJvmMain/kotlin")
            dependencies {
                implementation(libs.onnxruntime)
            }
        }
        androidMain {
            kotlin.srcDir("src/androidJvmMain/kotlin")
            dependencies {
                implementation(libs.onnxruntime.android)
                implementation(libs.litert)
                implementation(libs.androidx.activity.compose)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    "androidRuntimeClasspath"(libs.compose.ui.tooling)
}

compose.resources {
    packageOfResClass = "com.m4isper.kmpmlbench.generated.resources"
}
