import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.X509TrustManager
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

android {
    compileSdk = rootProject.ext["compileSdk"] as Int

    signingConfigs {
        create("lightsdkDev") {
            storeFile = file("../sdk/keys/lightsdk-dev.jks")
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    defaultConfig {
        minSdk = rootProject.ext["minSdk"] as Int
        targetSdk = rootProject.ext["targetSdk"] as Int

        manifestPlaceholders["sdkVersion"] = property("sdkVersion") as String
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
    }

    lint {
        warningsAsErrors = false
        error += "RestrictedApi"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
        targetCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(rootProject.ext["jvmTarget"] as String))
    }
}

dependencies {
    implementation(project(":sdk:client"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    ksp(libs.androidx.room.compiler)
}

abstract class UploadToolTask : DefaultTask() {

    @get:Internal
    abstract val apkDir: DirectoryProperty

    @get:Internal
    abstract val packageName: Property<String>

    @get:Internal
    abstract val ipAddress: Property<String>

    @get:Internal
    abstract val port: Property<Int>

    @get:Internal
    abstract val token: Property<String>

    @get:Internal
    abstract val timeoutSeconds: Property<Long>

    @get:Internal
    abstract val pollIntervalSeconds: Property<Long>

    private fun insecureHttpClient(): HttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAll), SecureRandom())
        }
        val sslParameters = SSLParameters().apply { endpointIdentificationAlgorithm = "" }
        return HttpClient.newBuilder()
            .sslContext(sslContext)
            .sslParameters(sslParameters)
            .build()
    }

    private fun localIpHost(ip: String) = "${ip.replace(".", "-")}.my.local-ip.co"

    private fun baseUrl() = "https://${localIpHost(ipAddress.get())}:${port.get()}"

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "device.token must be a hex-encoded key (odd length: $length)" }
        return ByteArray(length / 2) { i -> ((this[2 * i].digitToInt(16) shl 4) or this[2 * i + 1].digitToInt(16)).toByte() }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun sign(method: String, path: String, timestampMillis: Long): String {
        val canonical = "$method\n$path\n$timestampMillis"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.get().hexToBytes(), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun authedRequest(uri: URI, method: String): HttpRequest.Builder {
        val timestampMillis = System.currentTimeMillis()
        return HttpRequest.newBuilder(uri)
            .header("X-Tm-Timestamp", timestampMillis.toString())
            .header("X-Tm-Signature", sign(method, uri.rawPath, timestampMillis))
    }

    private fun extractLastUpdateMillis(json: String, packageName: String): Long? {
        val pattern = Regex(
            "\\{\"packageName\":\"${Regex.escape(packageName)}\",\"lastUpdateMillis\":(\\d+)\\}"
        )
        return pattern.find(json)?.groupValues?.get(1)?.toLong()
    }

    @TaskAction
    fun upload() {
        val missing = listOfNotNull(
            "device.ip".takeUnless { ipAddress.isPresent },
            "device.token".takeUnless { token.isPresent },
        )
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing required propert${if (missing.size == 1) "y" else "ies"}: " +
                    missing.joinToString(", ") { "-P$it=..." }
            )
        }

        val apk = apkDir.get().asFileTree.files.filter { it.extension == "apk" }.let { apks ->
            apks.singleOrNull()
                ?: throw GradleException("Expected exactly one .apk in ${apkDir.get()}, found: $apks")
        }
        val pkg = packageName.get()
        val client = insecureHttpClient()
        val toolMetaUri = URI("${baseUrl()}/api/data/developer/toolMeta")

        fun fetchLastUpdateMillis(): Long? {
            val response = client.send(authedRequest(toolMetaUri, "GET").GET().build(), HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == 200) {
                "Failed to query toolMeta: HTTP ${response.statusCode()} - ${response.body()}"
            }
            return extractLastUpdateMillis(response.body(), pkg)
        }

        val before = fetchLastUpdateMillis()
        val uploadFileName = "$pkg.apk"
        val uploadResponse = client.send(
            authedRequest(URI("${baseUrl()}/api/upload/developer/apkInbox/$uploadFileName"), "POST")
                .POST(HttpRequest.BodyPublishers.ofFile(apk.toPath()))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        check(uploadResponse.statusCode() == 200) {
            "Upload failed: HTTP ${uploadResponse.statusCode()} - ${uploadResponse.body()}"
        }
        val notifyResponse = client.send(
            authedRequest(URI("${baseUrl()}/api/notify/developer/apkInbox/$uploadFileName"), "POST")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        check(notifyResponse.statusCode() == 200) {
            "Notify failed: HTTP ${notifyResponse.statusCode()} - ${notifyResponse.body()}"
        }
        val deadlineMs = System.currentTimeMillis() + timeoutSeconds.get() * 1000
        val pollIntervalMs = pollIntervalSeconds.get() * 1000
        while (true) {
            Thread.sleep(pollIntervalMs)
            val after = fetchLastUpdateMillis()
            if (after != null && after != before) return
            if (System.currentTimeMillis() >= deadlineMs) {
                throw GradleException("Timed out waiting for $pkg to install/update.")
            }
        }
    }
}

tasks.register<UploadToolTask>("uploadTool") {
    group = "light sdk"
    description = "Uploads the debug APK to a Light SDK server and waits for it to install/update."
    dependsOn("assembleDebug")

    apkDir.set(layout.buildDirectory.dir("outputs/apk/debug"))
    packageName.set(requireNotNull(android.defaultConfig.applicationId) { "applicationId is not set" })
    ipAddress.set(providers.gradleProperty("device.ip").orElse("127.0.0.1"))
    port.set(providers.gradleProperty("device.port").map { it.toInt() }.orElse(54449))
    token.set(providers.gradleProperty("device.token"))
    timeoutSeconds.set(providers.gradleProperty("device.timeoutSeconds").map { it.toLong() }.orElse(60L))
    pollIntervalSeconds.set(2L)
}
