import arc.files.*
import arc.files.Fi
import arc.util.OS
import arc.util.serialization.Jval
import ent.EntityAnnoExtension
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import crystal.plot.PlotObfuscator

buildscript{
    val mindustryVersion = providers.gradleProperty("mindustryVersion").get()
    val mindustry = if(mindustryVersion == "be") "MindustryBuilds" else "Mindustry"

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
        ivy{
            url = uri("https://github.com")
            patternLayout{
                artifact(when(mindustryVersion){
                    "latest" -> "Anuken/Mindustry/releases/latest/download/dependencies.jar"
                    "be" -> "Anuken/MindustryBuilds/releases/download/master/latest.jar"
                    else -> "Anuken/Mindustry/releases/download/[revision]/dependencies.jar"
                })
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
}

val mindustryVersion = providers.gradleProperty("mindustryVersion").get()
val entVersion = providers.gradleProperty("entVersion").get()

val mindustry = if(mindustryVersion == "be") "MindustryBuilds" else "Mindustry"
val modName = providers.gradleProperty("modName").get()
val modArtifact = providers.gradleProperty("modArtifact").get()
val modFetch = providers.gradleProperty("modFetch").get()
val modGenSrc = providers.gradleProperty("modGenSrc").get()
val modGen = providers.gradleProperty("modGen").get()

fun mindustry(): String{
    return "Anuken:$mindustry:$mindustryVersion"
}

fun entity(module: String): String{
    return "com.github.GglLfr.EntityAnno$module:$entVersion"
}

allprojects{
    apply(plugin = "java")
    tasks.withType<AbstractArchiveTask>().configureEach {
        isReproducibleFileOrder = false
        isPreserveFileTimestamps = true
    }
    sourceSets["main"].java {
        srcDir(layout.projectDirectory.dir("src"))
        srcDir(layout.buildDirectory.dir("generated/sources/annotationProcessor/java/main"))
    }
    dependencies{
        abstract class TrimSources : TransformAction<TransformParameters.None>{
            @get:InputArtifact
            abstract val file: Provider<FileSystemLocation>

            override fun transform(outputs: TransformOutputs){
                val input = file.get().asFile
                val classes = outputs.file(input.name)

                JarFile(input).use{jar ->
                    val entries = jar.entries()
                    JarOutputStream(FileOutputStream(classes)).use{classes ->
                        for(entry in entries){
                            if(entry.name.endsWith(".java")) continue

                            classes.putNextEntry(JarEntry(entry.name))
                            jar.getInputStream(entry).use{it.copyTo(classes)}
                            classes.closeEntry()
                        }
                    }
                }
            }
        }

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
    }

    configurations.matching{it.isCanBeResolved}.configureEach{
        attributes{
            attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar-stripped")
        }
    }

    repositories{
        // Use Ivy repository for Mindustry builds.
        ivy{
            url = uri("https://github.com")
            patternLayout{
                artifact(when(mindustryVersion){
                    "latest" -> "Anuken/Mindustry/releases/latest/download/dependencies.jar"
                    "be" -> "Anuken/MindustryBuilds/releases/download/master/latest.jar"
                    else -> "Anuken/Mindustry/releases/download/[revision]/dependencies.jar"
                })
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

    val localModName = modName
    val localMindustryVersion = mindustryVersion
    configure<EntityAnnoExtension>{
        modName = localModName
        mindustryVersion = localMindustryVersion
        revisionDir = layout.projectDirectory.dir("revisions").asFile
        fetchPackage = modFetch
        genSrcPackage = modGenSrc
        genPackage = modGen
    }

    dependencies{
        // Use the entity generation annotation processor.
        compileOnly(entity(":entity"))
        annotationProcessor(entity(":entity"))

        compileOnly(mindustry())
    }

val jar = tasks.named<Jar>("jar"){
    archiveFileName = "${modArtifact}Desktop.jar"

    val metaJson = layout.projectDirectory.file("mod.json")
    val metaHjson = layout.projectDirectory.file("mod.hjson")

    if(metaJson.asFile.exists() && metaHjson.asFile.exists()){
        throw IllegalStateException("Ambiguous mod meta: both `mod.json` and `mod.hjson` exist.")
    }else if(!metaJson.asFile.exists() && !metaHjson.asFile.exists()){
        throw IllegalStateException("Missing mod meta: neither `mod.json` nor `mod.hjson` exist.")
    }

    val isJson = metaJson.asFile.exists()
    val usedMeta = if(isJson) metaJson else metaHjson

    // 1) 普通资源直接复制
    from(
        files(sourceSets["main"].output.classesDirs),
        files(sourceSets["main"].output.resourcesDir),
        configurations.runtimeClasspath.map{conf -> conf.map{if(it.isDirectory) it else zipTree(it)}},
        layout.projectDirectory.file("icon.png"),
        usedMeta
    )

    // 2) assets 单独处理：plot 目录下的 properties 在打包时自动混淆 value，不碰原文件
    from(layout.projectDirectory.dir("assets")) {
        filteringCharset = "UTF-8"
        filesMatching("plot/**/*.properties") {
            filter { line: String ->
                val trimmed = line.trim()
                // 保留空行与注释
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    line
                } else {
                    val eq = line.indexOf('=')
                    if (eq == -1) {
                        line
                    } else {
                        // key= 部分保持原样，只对 value 做 CJK 混淆
                        line.substring(0, eq + 1) + PlotObfuscator.obfuscate(line.substring(eq + 1))
                    }
                }
            }
        }
    }

    metaInf.from(layout.projectDirectory.file("LICENSE"))

    val localModName = modName
    doFirst{
        if(usedMeta.asFile.reader(Charsets.UTF_8).use{Jval.read(it)}.getString("name") != localModName) {
            throw GradleException("Mod name mismatch in `${usedMeta.asFile.name}`; please synchronize with `gradle.properties`")
        }
    }
}

    val dex = tasks.register<Jar>("dex"){
        inputs.files(jar)
        archiveFileName = "$modArtifact.jar"

        val desktopJar = jar.flatMap{it.archiveFile}
        val dexJar = File(temporaryDir, "Dex.jar")

        val androidSdkVersion = providers.gradleProperty("androidSdkVersion").get()
        val androidBuildVersion = providers.gradleProperty("androidBuildVersion").get()
        val androidMinVersion = providers.gradleProperty("androidMinVersion").get()

        val classpaths = configurations.compileClasspath.get().toList() + configurations.runtimeClasspath.get().toList()
        val providers = project.providers

        from(zipTree(desktopJar), zipTree(dexJar))
        doFirst{
            // Find Android SDK root.
            val sdkRoot = File(
                OS.env("ANDROID_SDK_ROOT") ?: OS.env("ANDROID_HOME")
                ?: throw IllegalStateException("Neither `ANDROID_SDK_ROOT` nor `ANDROID_HOME` is set.")
            )

            // Find `d8`.
            val d8 = File(sdkRoot, "build-tools/$androidBuildVersion/${if(OS.isWindows) "d8.bat" else "d8"}")
            if(!d8.exists()) throw IllegalStateException("Android SDK `build-tools;$androidBuildVersion` isn't installed or is corrupted")

            // Initialize a release build.
            val input = desktopJar.get().asFile
            val command = arrayListOf("$d8", "--release", "--min-api", androidMinVersion, "--output", "$dexJar", "$input")

            // Include all compile and runtime classpath.
            classpaths.forEach{
                if(it.exists()) command.addAll(arrayOf("--classpath", it.path))
            }

            // Include Android platform as library.
            val androidJar = File(sdkRoot, "platforms/android-$androidSdkVersion/android.jar")
            if(!androidJar.exists()) throw IllegalStateException("Android SDK `platforms;android-$androidSdkVersion` isn't installed or is corrupted")

            command.addAll(arrayOf("--lib", "$androidJar"))
            if(OS.isWindows) command.addAll(0, arrayOf("cmd", "/c").toList())

            // Run `d8`.
            providers.exec{commandLine(command)}.result.get().rethrowFailure()
        }
    }

    tasks.register<DefaultTask>("install"){
        inputs.files(jar)

        val desktopJar = jar.flatMap{it.archiveFile}
        val dexJar = dex.flatMap{it.archiveFileName}
        doLast{
            val folder = Fi.get(OS.getAppDataDirectoryString("Mindustry")).child("mods")
            folder.mkdirs()

            val input = desktopJar.get().asFile
            folder.child(input.name).delete()
            folder.child(dexJar.get()).delete()
            Fi(input).copyTo(folder)

            logger.lifecycle("Copied :jar output to $folder.")
        }
    }
}
