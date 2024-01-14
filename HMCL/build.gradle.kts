import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("com.google.code.gson:gson:2.10.1")
    }
}

plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

val isOfficial = System.getenv("HMCL_SIGNATURE_KEY") != null
        || (System.getenv("GITHUB_REPOSITORY_OWNER") == "HMCL-dev" && System.getenv("GITHUB_BASE_REF")
    .isNullOrEmpty())

val buildNumber = System.getenv("BUILD_NUMBER")?.toInt().let { number ->
    val offset = System.getenv("BUILD_NUMBER_OFFSET")?.toInt() ?: 0
    if (number != null) {
        (number - offset).toString()
    } else {
        val shortCommit = System.getenv("GITHUB_SHA")?.lowercase()?.substring(0, 7)
        val prefix = if (isOfficial) "dev" else "unofficial"
        if (!shortCommit.isNullOrEmpty()) "$prefix-$shortCommit" else "SNAPSHOT"
    }
}
val versionRoot = System.getenv("VERSION_ROOT") ?: "3.5"
val versionType = System.getenv("VERSION_TYPE") ?: if (isOfficial) "nightly" else "unofficial"

val microsoftAuthId = System.getenv("MICROSOFT_AUTH_ID") ?: ""
val microsoftAuthSecret = System.getenv("MICROSOFT_AUTH_SECRET") ?: ""
val curseForgeApiKey = System.getenv("CURSEFORGE_API_KEY") ?: ""

version = "$versionRoot.$buildNumber"

dependencies {
    implementation(project(":HMCLCore"))
    implementation("libs:JFoenix")
}

fun digest(algorithm: String, bytes: ByteArray): ByteArray = MessageDigest.getInstance(algorithm).digest(bytes)

tasks.getByName<JavaCompile>("compileJava") {
    dependsOn(tasks.create("computeDynamicResources") {
        this@create.inputs.file(rootProject.rootDir.toPath().resolve("data-json/dynamic-remote-resources-raw.json"))
        this@create.outputs.file(rootProject.rootDir.toPath().resolve("data-json/dynamic-remote-resources.json"))

        doLast {
            Gson().also { gsonInstance ->
                Files.newBufferedReader(
                    rootProject.rootDir.toPath().resolve("data-json/dynamic-remote-resources-raw.json"),
                    Charsets.UTF_8
                ).use { br ->
                    (gsonInstance.fromJson(br, JsonElement::class.java) as JsonObject)
                }.also { data ->
                    data.asMap().forEach { (namespace, namespaceData) ->
                        (namespaceData as JsonObject).asMap().forEach { (name, nameData) ->
                            (nameData as JsonObject).asMap().forEach { (version, versionData) ->
                                require(versionData is JsonObject)
                                val localPath =
                                    (versionData.get("local_path") as com.google.gson.JsonPrimitive).asString
                                val sha1 = (versionData.get("sha1") as com.google.gson.JsonPrimitive).asString

                                val currentSha1 = digest(
                                    "SHA-1",
                                    Files.readAllBytes(rootProject.rootDir.toPath().resolve(localPath))
                                ).joinToString(separator = "") { "%02x".format(it) }

                                if (!sha1.equals(currentSha1, ignoreCase = true)) {
                                    throw IllegalStateException("Mismatched SHA-1 in $.${namespace}.${name}.${version} of dynamic remote resources detected. Require ${currentSha1}, but found $sha1")
                                }
                            }
                        }
                    }

                    rootProject.rootDir.toPath().resolve("data-json/dynamic-remote-resources.json").also { zippedPath ->
                        gsonInstance.toJson(data).also { expectedData ->
                            if (Files.exists(zippedPath)) {
                                Files.readString(zippedPath, Charsets.UTF_8).also { rawData ->
                                    if (!rawData.equals(expectedData)) {
                                        if (System.getenv("GITHUB_SHA") == null) {
                                            Files.writeString(zippedPath, expectedData, Charsets.UTF_8)
                                        } else {
                                            throw IllegalStateException("Mismatched zipped dynamic-remote-resources json file!")
                                        }
                                    }
                                }
                            } else {
                                Files.writeString(zippedPath, expectedData, Charsets.UTF_8)
                            }
                        }
                    }
                }
            }
        }
    })
}

val java11 = sourceSets.create("java11") {
    java {
        srcDir("src/main/java11")
    }
}

tasks.getByName<JavaCompile>(java11.compileJavaTaskName) {
    if (JavaVersion.current() < JavaVersion.VERSION_11) {
        javaCompiler.set(javaToolchains.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(11))
        })
    }
    options.compilerArgs.add("--add-exports=java.base/jdk.internal.loader=ALL-UNNAMED")
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

tasks.jar {
    enabled = false
    dependsOn(tasks["shadowJar"])
}

val jarPath = tasks.jar.get().archiveFile.get().asFile

tasks.shadowJar {
    exclude("**/package-info.class")
    exclude("META-INF/maven/**")

    minimize {
        exclude(dependency("com.google.code.gson:.*:.*"))
        exclude(dependency("libs:JFoenix:.*"))
    }

    manifest {
        attributes(
            "Created-By" to "Copyright(c) 2013-2023 huangyuhui.",
            "Main-Class" to "org.jackhuang.hmcl.Main",
            "Multi-Release" to "true",
            "Implementation-Version" to project.version,
            "Microsoft-Auth-Id" to microsoftAuthId,
            "Microsoft-Auth-Secret" to microsoftAuthSecret,
            "CurseForge-Api-Key" to curseForgeApiKey,
            "Build-Channel" to versionType,
            "Class-Path" to "pack200.jar",
            "Add-Opens" to listOf(
                "java.base/java.lang",
                "java.base/java.lang.reflect",
                "java.base/jdk.internal.loader",
                "javafx.base/com.sun.javafx.binding",
                "javafx.base/com.sun.javafx.event",
                "javafx.base/com.sun.javafx.runtime",
                "javafx.graphics/javafx.css",
                "javafx.graphics/com.sun.javafx.stage",
                "javafx.graphics/com.sun.prism",
                "javafx.controls/com.sun.javafx.scene.control",
                "javafx.controls/com.sun.javafx.scene.control.behavior",
                "javafx.controls/javafx.scene.control.skin",
                "jdk.attach/sun.tools.attach"
            ).joinToString(" ")
        )

        System.getenv("GITHUB_SHA")?.also {
            attributes("GitHub-SHA" to it)
        }
    }
}

tasks.processResources {
    fun convertToBSS(resource: String) {
        doFirst {
            val cssFile = File(projectDir, "src/main/resources/$resource")
            val bssFile = File(projectDir, "build/compiled-resources/${resource.substring(0, resource.length - 4)}.bss")
            bssFile.parentFile.mkdirs()
            javaexec {
                classpath = sourceSets["main"].compileClasspath
                mainClass.set("com.sun.javafx.css.parser.Css2Bin")
                args(cssFile, bssFile)
            }
        }
    }

    from("build/compiled-resources")

    convertToBSS("assets/css/root.css")
    convertToBSS("assets/css/blue.css")

    into("META-INF/versions/11") {
        from(sourceSets["java11"].output)
    }
    dependsOn(tasks["java11Classes"])

    into("assets") {
        from(project.layout.buildDirectory.file("openjfx-dependencies.json"))
    }
    dependsOn(rootProject.tasks["generateOpenJFXDependencies"])
}

val makeExecutables = tasks.create("makeExecutables") {
    dependsOn(tasks.shadowJar)

    outputs.file(jarPath)

    fun executablePath(ext: String) = File(jarPath.parentFile, jarPath.nameWithoutExtension + '.' + ext)

    val executables = mapOf(
        "exe" to "src/main/resources/assets/HMCLauncher.exe",
        "sh" to "src/main/resources/assets/HMCLauncher.sh"
    )

    for ((ext, _) in executables) {
        outputs.file(executablePath(ext))
    }

    fun createChecksum(file: File) {
        val algorithms = arrayOf(
            "MD5" to "md5",
            "SHA-1" to "sha1",
            "SHA-256" to "sha256",
            "SHA-512" to "sha512"
        )

        algorithms.forEach { (algorithm, ext) ->
            File(file.parentFile, "${file.name}.$ext").writeText(
                digest(algorithm, file.readBytes()).joinToString(separator = "", postfix = "\n") { "%02x".format(it) }
            )
        }
    }

    class SignatureBuilder(keyLocation: String) {
        private val signer = Signature.getInstance("SHA512withRSA").apply {
            initSign(KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(File(keyLocation).readBytes())))
        }

        private val sha512: TreeMap<String, ByteArray> = TreeMap()
        private val md = MessageDigest.getInstance("SHA-512")

        fun add(name: String, content: ByteArray) {
            md.reset()
            md.update(content)
            sha512[name] = md.digest()
        }

        fun sign(): ByteArray {
            for ((name, content) in sha512) {
                md.reset()
                md.update(name.toByteArray())

                signer.update(md.digest())
                signer.update(content)
            }

            return signer.sign()
        }
    }

    doLast {
        val signatureBuilder = System.getenv("HMCL_SIGNATURE_KEY")?.let { SignatureBuilder(it) }
        if (signatureBuilder == null) {
            logger.warn("Missing signature key")
        }

        val zipBytes = ByteArrayOutputStream()

        ZipInputStream(tasks.shadowJar.get().archiveFile.get().asFile.inputStream()).use { zipIn ->
            ZipOutputStream(zipBytes).use { zipOut ->
                while (true) {
                    val entry = zipIn.nextEntry ?: break
                    val entryBytes = zipIn.readAllBytes()

                    if (entry.compressedSize >= entry.size) {
                        entry.method = ZipEntry.STORED
                        entry.size = entryBytes.size.toLong()
                        entry.compressedSize = entryBytes.size.toLong()
                    }

                    zipOut.putNextEntry(entry)
                    zipOut.write(entryBytes)

                    if (entry.name != "META-INF/hmcl_signature")
                        signatureBuilder?.add(entry.name, entryBytes)
                }

                if (signatureBuilder != null) {
                    zipOut.putNextEntry(ZipEntry("META-INF/hmcl_signature"))
                    zipOut.write(signatureBuilder.sign())
                }
            }
        }

        jarPath.outputStream().use { zipBytes.writeTo(it) }
        createChecksum(jarPath)

        for ((ext, header) in executables) {
            val executable = executablePath(ext)
            executable.outputStream().use { out ->
                out.write(File(project.projectDir, header).readBytes())
                zipBytes.writeTo(out)
            }

            createChecksum(executable)
        }
    }

}

tasks.jar {

}

tasks.build {
    dependsOn(makeExecutables)
}

tasks.create<JavaExec>("run") {
    dependsOn(tasks.jar)

    group = "application"

    classpath = files(jarPath)
    workingDir = rootProject.rootDir
}
