import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    // A host target executes the same core and real bundled SQLite DB tests in CI.
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        val mainCompilation = compilations.getByName("main")
        val enospcTest by compilations.creating {
            associateWith(mainCompilation)
            defaultSourceSet.dependencies { implementation(kotlin("test-junit")) }
        }
        tasks.register<Test>("jvmEnospcTest") {
            description = "Real ENOSPC on an isolated 8 MiB Linux tmpfs (scripts/enospc-check.sh)."
            group = "verification"
            testClassesDirs = enospcTest.output.classesDirs
            classpath = enospcTest.output.allOutputs + enospcTest.runtimeDependencyFiles
            systemProperty("bookreader.enospc.root", providers.environmentVariable("BOOKREADER_ENOSPC_ROOT").getOrElse(""))
            systemProperty("bookreader.enospc.helper", rootProject.file("scripts/fill-enospc-fixture.py").absolutePath)
            // Every invocation needs a fresh real filesystem failure, never cached XML.
            outputs.upToDateWhen { false }
            outputs.cacheIf { false }
        }
    }
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies { implementation(kotlin("test-junit")) }
    }
}
android {
    namespace = "org.bookreader.mobile.shared"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}
room { schemaDirectory("$projectDir/schemas") }

