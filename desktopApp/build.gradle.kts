import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.compose.desktop)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutinesSwing)

    // Compose 1.11.1 on Gradle 9.x does not always attach the host-native Skiko
    // runtime to the desktop runtime classpath, so the launcher fails with
    // "Cannot find libskiko-macos-arm64.dylib". Pin it explicitly for the host OS;
    // keep the version in sync with the skiko pulled by Compose (currently 0.144.6).
    val skikoVersion = "0.144.6"
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val skikoNative = when {
        os.startsWith("mac") && arch.contains("aarch64") -> "macos-arm64"
        os.startsWith("mac") -> "macos-x64"
        os.startsWith("win") -> "windows-x64"
        else -> "linux-x64"
    }
    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$skikoNative:$skikoVersion")
}

compose.desktop {
    application {
        mainClass = "com.m4isper.kmpmlbench.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.m4isper.kmpmlbench"
            packageVersion = "1.0.0"
        }
    }
}
