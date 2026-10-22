import arc.util.*
import ent.*
import mindustry.client.*
import mindustry.client.service.*
import mindustry.client.task.*
import java.net.http.*
import java.net.URI
import java.time.Duration
import java.util.*
import java.util.jar.*
import java.util.zip.*
import crystal.plot.PlotObfuscator

buildscript{
    val (mindustry, mindustryVersion, mindustrySource) = when(val version = providers.gradleProperty("mindustryVersion").get()){
        "latest" -> Triple("Mindustry", "latest", "Anuken/Mindustry/releases/latest/download/dependencies.jar")
        "be" -> Triple("MindustryBuilds", "latest", "Anuken/MindustryBuilds/releases/download/master/latest.jar")
        else -> Triple("Mindustry", version, "Anuken/Mindustry/releases/download/[revision]/dependencies.jar")
    }

    dependencies{
        classpath("Anuken:$mindustry:$mindustryVersion")
    }

    configurations.configureEach{
        // Resolve the correct Mindustry dependency.
        resolutionStrategy.eachDependency{
            if(requested.group == "Anuken" && requested.name.startsWith("Mindustry")){
                useTarget("Anuken:$mindustry:$mindustryVersion")
            }
        }
    }

    repositories{
        maven("https://jitpack.io")
        ivy{
            url = uri("https://github.com/")
            patternLayout{
                artifact(mindustrySource)
                metadataSources{artifact()}
            }
            content{
                includeVersion("Anuken", mindustry, mindustryVersion)
            }
        }
    }
}

plugins{
    java
    id("com.github.GglLfr.EntityAnno") apply false
    id("com.github.GglLfr.MindustryClient") apply false
}

val (mindustry, mindustryVersion, mindustrySource) = when(val version = providers.gradleProperty("mindustryVersion").get()){
    "latest" -> Triple("Mindustry", "latest", "Anuken/Mindustry/releases/latest/download/dependencies.jar")
    "be" -> Triple("MindustryBuilds", "latest", "Anuken/MindustryBuilds/releases/download/master/latest.jar")
    else -> Triple("Mindustry", version, "Anuken/Mindustry/releases/download/[revision]/dependencies.jar")
}
val entVersion = providers.gradleProperty("entVersion").get()

val modArtifact = providers.gradleProperty("modArtifact").get()
val modFetch = providers.gradleProperty("modFetch").get()
val modGenSrc = providers.gradleProperty("modGenSrc").get()
val modGen = providers.gradleProperty("modGen").get()

allprojects{
    apply(plugin = "java")
    sourceSets["main"].java.setSrcDirs(listOf(layout.projectDirectory.dir("src")))

    dependencies{
        registerTransform(TrimSources::class){
            from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
            to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar-stripped")
        }
    }

    configurations.configureEach{
        // Resolve the correct Mindustry dependency.
        resolutionStrategy.eachDependency{
            if(requested.group == "Anuken" && requested.name.startsWith("Mindustry")){
                useTarget("Anuken:$mindustry:$mindustryVersion")
            }
        }

        if(isCanBeResolved) attributes{
            attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar-stripped")
        }
    }

    repositories{
        // Use Ivy repository for Mindustry builds.
        ivy{
            url = uri("https://github.com")
            patternLayout{
                artifact(mindustrySource)
                metadataSources{artifact()}
            }
            content{
                includeVersion("Anuken", mindustry, mindustryVersion)
            }
        }

        // Necessary Maven repositories to pull dependencies from.
        mavenLocal()
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/releases/")
        maven("https://raw.githubusercontent.com/GglLfr/EntityAnnoMaven/main")
    }

    tasks.withType<JavaCompile>().configureEach{
        options.apply{
            compilerArgs.add("-Xlint:-options")
            compilerArgs.add("-implicit:none")
            compilerArgs.addAll(providers.gradleProperty("org.gradle.jvmargs").get()
                .split(Regex("\\s+"))
                .filter{it.startsWith("--add-opens")}
                .map{"--add-exports=${it.substring("--add-opens=".length)}"}
            )

            isIncremental = true
            isFork = false
            encoding = "UTF-8"
        }

        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}

project(":"){
    apply(plugin = "com.github.GglLfr.EntityAnno")
    apply(plugin = "com.github.GglLfr.MindustryClient")

    configure<EntityAnnoExtension>{
        revisionDir = layout.projectDirectory.dir("revisions")
        fetchPackage = modFetch
        genSrcPackage = modGenSrc
        genPackage = modGen
    }

    tasks.named("fetchComps") {
        // EntityAnno v2.4.0 requests GitHub without a User-Agent. GitHub rejects
        // that request with HTTP 403, which breaks fetchComps before any source
        // can be downloaded. Use the release archive instead, with explicit
        // headers and a mirror fallback, then apply the plugin's own transform.
        actions.clear()

        doLast {
            val entityAnno = project.extensions.getByType<EntityAnnoExtension>()
            val fetchPackage = entityAnno.fetchPackage.get()
            val outputRoot = project.layout.buildDirectory.dir("fetched").get().asFile
            val outputDir = outputRoot.resolve(fetchPackage.replace('.', File.separatorChar))

            val pluginProperties = Properties()
            val versionStream = EntityAnnoPlugin::class.java.classLoader
                .getResourceAsStream("version.properties")
                ?: throw GradleException("EntityAnno is missing version.properties")
            versionStream.use(pluginProperties::load)

            val build = pluginProperties.getProperty("build")
                ?: throw GradleException("EntityAnno version.properties is missing 'build'")
            val type = pluginProperties.getProperty("type")
                ?: throw GradleException("EntityAnno version.properties is missing 'type'")
            if(type != "official"){
                throw GradleException("fetchComps archive fallback only supports official Mindustry builds, got '$type'")
            }

            val tag = "v$build"
            val archiveUrls = listOf(
                "https://codeload.github.com/Anuken/Mindustry/zip/refs/tags/$tag",
                "https://ghproxy.net/https://github.com/Anuken/Mindustry/archive/refs/tags/$tag.zip"
            )
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()

            var archive: ByteArray? = null
            var lastFailure: Exception? = null
            for(url in archiveUrls){
                try{
                    val request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(120))
                        .header("User-Agent", "crystal-fetchComps")
                        .header("Accept", "application/zip")
                        .GET()
                        .build()
                    val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
                    if(response.statusCode() !in 200..299){
                        throw GradleException("HTTP ${response.statusCode()} from $url")
                    }
                    archive = response.body()
                    break
                }catch(e: Exception){
                    lastFailure = e
                    logger.warn("fetchComps: download failed from {}: {}", url, e.message)
                }
            }
            val bytes = archive ?: throw GradleException("Unable to download Mindustry $tag", lastFailure)

            project.delete(outputRoot)
            outputDir.mkdirs()

            val marker = "/core/src/mindustry/entities/comp/"
            var written = 0
            ZipInputStream(bytes.inputStream()).use{zip ->
                while(true){
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    val markerIndex = name.indexOf(marker)
                    if(entry.isDirectory || markerIndex < 0) continue

                    val componentName = name.substring(markerIndex + marker.length)
                    if(componentName.contains('/') || !componentName.endsWith(".java")) continue

                    val source = zip.readBytes().toString(Charsets.UTF_8)
                    if(source.isBlank()){
                        throw IllegalStateException("Downloaded an empty component: $componentName")
                    }
                    outputDir.resolve(componentName).writeText(
                        EntityAnnoPlugin.procComp(source, fetchPackage),
                        Charsets.UTF_8
                    )
                    written++
                }
            }

            if(written == 0){
                throw GradleException("Mindustry $tag archive did not contain any entity components")
            }
            logger.lifecycle("Wrote {} components from Mindustry {}.", written, tag)
        }
    }

    dependencies{
        // Use the entity generation annotation processor.
        compileOnly("com.github.GglLfr.EntityAnno:entity:$entVersion")
        annotationProcessor("com.github.GglLfr.EntityAnno:entity:$entVersion")

        compileOnly("Anuken:$mindustry:$mindustryVersion")
    }

    val client = gradle.sharedServices.registerIfAbsent(MindustryClientPlugin.serviceName, MindustryClientService::class.java){}
    val jar = tasks.named<Jar>("jar"){
        archiveFileName = "${modArtifact}Desktop.jar"
        from(
            files(sourceSets["main"].output.classesDirs),
            files(sourceSets["main"].output.resourcesDir),
            configurations.runtimeClasspath.map{conf -> conf.map{if(it.isDirectory) it else zipTree(it)}},

            layout.projectDirectory.file("icon.png"),
            // Check both JSON and HJSON.
            layout.projectDirectory.file("mod.json"),
            layout.projectDirectory.file("mod.hjson")
        )

        // plot 源文件保持明文方便维护；只在打包进 jar 时对 value 做 CJK 混淆，
        // 运行时由 PlotBundle 用同一个密钥解回明文。
        from(layout.projectDirectory.dir("assets")) {
            filteringCharset = "UTF-8"
            exclude("sprites/units/upgrade/layers/**")
            filesMatching("plot/**/*.properties") {
                filter { line: String ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        line
                    } else {
                        val eq = line.indexOf('=')
                        if (eq == -1) {
                            line
                        } else {
                            line.substring(0, eq + 1) + PlotObfuscator.obfuscate(line.substring(eq + 1))
                        }
                    }
                }
            }
        }

        metaInf.from(layout.projectDirectory.file("LICENSE"))
    }

    val dex = tasks.register<Jar>("dex"){
        description = "Builds an Android-compatible JAR from the desktop-only JAR. Use this file for GitHub release."

        val androidSdkVersion = providers.gradleProperty("androidSdkVersion").get()
        val androidBuildVersion = providers.gradleProperty("androidBuildVersion").get()
        val androidMinVersion = providers.gradleProperty("androidMinVersion").get()

        val classpaths = files(configurations.compileClasspath, configurations.runtimeClasspath)
        inputs.files(jar)
        inputs.files(classpaths)
        inputs.property("androidSdk", "$androidSdkVersion+$androidBuildVersion+$androidMinVersion")

        archiveFileName = "$modArtifact.jar"

        val desktopJar = jar.flatMap{it.archiveFile}
        val dexJar = File(temporaryDir, "Dex.jar")
        val providers = project.providers

        from(zipTree(desktopJar), zipTree(dexJar))
        doFirst{
            // Find Android SDK root.
            val sdkRoot = File(
                OS.env("ANDROID_HOME") ?: OS.env("ANDROID_SDK_ROOT")
                ?: throw GradleException("Neither `ANDROID_HOME` nor `ANDROID_SDK_ROOT` are set")
            )

            // Run d8.jar directly. Going through d8.bat/cmd makes the Java runtime
            // depend on the environment of the process that launched Gradle, which
            // differs between a terminal and IntelliJ IDEA.
            val d8Jar = File(sdkRoot, "build-tools/$androidBuildVersion/lib/d8.jar")
            if(!d8Jar.exists()) throw GradleException("Android SDK `build-tools;$androidBuildVersion` isn't installed or is corrupted")
            val javaExecutable = File(
                System.getProperty("java.home"),
                "bin/${if(OS.isWindows) "java.exe" else "java"}"
            )
            if(!javaExecutable.exists()) throw GradleException("Unable to find the Java runtime used by Gradle: $javaExecutable")

            // Initialize a release build.
            val input = desktopJar.get().asFile
            val command = arrayListOf(
                "$javaExecutable",
                "-Xmx2g",
                "-cp", "$d8Jar",
                "com.android.tools.r8.D8",
                "--release", "--min-api", androidMinVersion, "--output", "$dexJar", "$input"
            )

            // Include all compile and runtime classpath.
            classpaths.forEach{
                if(it.exists()) command.addAll(arrayOf("--classpath", it.path))
            }

            // Include Android platform as library.
            val androidJar = File(sdkRoot, "platforms/android-$androidSdkVersion/android.jar")
            if(!androidJar.exists()) throw GradleException("Android SDK `platforms;android-$androidSdkVersion` isn't installed or is corrupted")

            command.addAll(arrayOf("--lib", "$androidJar"))

            // Run `d8`.
            providers.exec{commandLine(command)}.result.get().rethrowFailure()
        }
    }

    val install = tasks.register<DefaultTask>("install"){
        description = "Installs the desktop JAR to your `mods/` folder."
        usesService(client)

        val desktopJar = jar.flatMap{it.archiveFile}
        val dexJar = dex.flatMap{it.archiveFileName}

        inputs.files(desktopJar)
        doLast{
            val mods = client.get().detected.modsDirectory
            mods.mkdirs()
            mods.resolve(dexJar.get()).delete()

            val input = desktopJar.get().asFile
            val output = mods.resolve(input.name)
            input.copyTo(output, overwrite = true)

            logger.lifecycle("Copied :jar output to ${mods}.")
        }
    }

    tasks.withType<RunClientTask>().configureEach{
        dependsOn(install)
    }
}

abstract class TrimSources : TransformAction<TransformParameters.None>{
    @get:InputArtifact
    abstract val file: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs){
        val input = file.get().asFile
        val classes = outputs.file(input.name)

        JarFile(input).use{jar ->
            val entries = jar.entries()
            classes.outputStream().use{JarOutputStream(it).use{classes ->
                for(entry in entries){
                    if(entry.name.endsWith(".java")) continue

                    classes.putNextEntry(JarEntry(entry.name))
                    jar.getInputStream(entry).use{it.copyTo(classes)}
                    classes.closeEntry()
                }
            }}
        }
    }
}
