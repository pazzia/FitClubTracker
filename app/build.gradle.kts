import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Bundle verified upstream photos into the APK without committing 30 MB of generated assets.
// No network is used by the installed app. The pinned source ZIP is cached for later builds.
val exercisePhotosDirectory = layout.buildDirectory.dir("generated/exercisePhotos")
val prepareExercisePhotos by tasks.registering {
    val sourceCommit = "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5"
    val manifest = layout.projectDirectory.file("src/main/assets/free_exercise_db/photos.sha256")
    inputs.file(manifest)
    inputs.property("sourceCommit", sourceCommit)
    outputs.dir(exercisePhotosDirectory)
    doLast {
        val wanted = manifest.asFile.readLines().filter { it.isNotBlank() }.associate { line ->
            val fields = line.split(' ', limit = 2)
            require(fields.size == 2 && fields[0].matches(Regex("[0-9a-f]{64}")))
            require(fields[1].matches(Regex("[A-Za-z0-9_\\-]+/[01]\\.jpg")))
            fields[1] to fields[0]
        }
        require(wanted.size == 448) { "Expected 448 unique photos" }
        val cache = rootProject.layout.projectDirectory.dir(".gradle/free-exercise-db").asFile.apply { mkdirs() }
        val archive = File(cache, "$sourceCommit.zip")
        if (!archive.exists()) {
            val temporary = File(cache, "$sourceCommit.download")
            val connection = URI("https://codeload.github.com/yuhonas/free-exercise-db/zip/$sourceCommit").toURL().openConnection()
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            try {
                connection.getInputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(temporary.renameTo(archive)) { "Cannot cache source archive" }
            } finally { temporary.delete() }
        }
        val output = exercisePhotosDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        val prefix = "free-exercise-db-$sourceCommit/exercises/"
        try {
            ZipFile(archive).use { zip ->
                wanted.forEach { (path, expected) ->
                    val entry = zip.getEntry(prefix + path) ?: error("Missing source photo: $path")
                    val target = File(output, "free_exercise_db/$path")
                    target.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                    val hash = MessageDigest.getInstance("SHA-256").digest(target.readBytes())
                        .joinToString("") { "%02x".format(it) }
                    check(hash == expected) { "Photo checksum mismatch: $path" }
                }
            }
        } catch (failure: Exception) {
            output.deleteRecursively()
            archive.delete()
            throw failure
        }
    }
}

tasks.named("preBuild").configure { dependsOn(prepareExercisePhotos) }

val experimentalBuild = providers.gradleProperty("fitclubExperimental").orNull?.toBoolean() ?: false

android {
    namespace = "com.fitclub.tracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fitclub.tracker"
        minSdk = 28
        targetSdk = 34
        versionCode = 28
        versionName = "0.9.5"
        // Full build identity for bug reports; CI fills these, local builds say "local".
        fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
        buildConfigField("String", "BUILD_NUMBER", "\"${env("FITCLUB_BUILD_NUMBER") ?: "local"}\"")
        buildConfigField("String", "GIT_SHA", "\"${env("FITCLUB_GIT_SHA")?.take(7) ?: "local"}\"")
        buildConfigField("String", "GIT_BRANCH", "\"${env("FITCLUB_BRANCH") ?: "local"}\"")
        buildConfigField("String", "BUILD_TIME", "\"${ZonedDateTime.now(ZoneId.of("Europe/Moscow")).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))} МСК\"")
    }

    buildTypes {
        debug {
            if (experimentalBuild) {
                applicationIdSuffix = ".experimental"
                versionNameSuffix = "-test"
                resValue("string", "app_name", "FitClub · Тест")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    System.getenv("FITCLUB_DEBUG_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { path ->
        signingConfigs.getByName("debug").storeFile = file(path)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main").assets.srcDir(exercisePhotosDirectory)
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Robolectric's default Maven host is unavailable in some build environments.
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}

