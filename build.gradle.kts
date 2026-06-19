import arc.files.*
import arc.util.*
import arc.util.serialization.*
import ent.*
import java.io.*

buildscript{
    val arcVersion: String by project
    val useJitpack = property("mindustryBE").toString().toBooleanStrict()

    dependencies{
        classpath("com.github.Anuken.Arc:arc-core:$arcVersion")
    }

    repositories{
        if(!useJitpack) maven("https://maven.xpdustry.com/mindustry")
        maven("https://jitpack.io")
    }
}

plugins{
    java
    id("com.github.GglLfr.EntityAnno") apply false
}

val arcVersion: String by project
val mindustryVersion: String by project
val mindustryBEVersion: String by project
val entVersion: String by project

val modName: String by project
val modArtifact: String by project
val modFetch: String by project
val modGenSrc: String by project
val modGen: String by project

val androidSdkVersion: String by project
val androidBuildVersion: String by project
val androidMinVersion: String by project

val useJitpack = property("mindustryBE").toString().toBooleanStrict()

fun arc(module: String): String{
    return "com.github.Anuken.Arc$module:$arcVersion"
}

fun mindustry(module: String): String{
    return "com.github.Anuken.Mindustry$module:$mindustryVersion"
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
        srcDir(layout.buildDirectory.dir("generated/source/kapt/main"))
    }
    configurations.configureEach{
        // Resolve the correct Mindustry dependency, and force Arc version.
        resolutionStrategy.eachDependency{
            if(useJitpack && requested.group == "com.github.Anuken.Mindustry"){
                useTarget("com.github.Anuken.MindustryJitpack:${requested.module.name}:$mindustryBEVersion")
            }else if(requested.group == "com.github.Anuken.Arc"){
                useVersion(arcVersion)
            }
        }
    }

    dependencies{
        // Downgrade Java 9+ syntax into being available in Java 8.
        annotationProcessor(entity(":downgrader"))
    }

    repositories{
        // Necessary Maven repositories to pull dependencies from.
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/releases/")
        maven("https://raw.githubusercontent.com/GglLfr/EntityAnnoMaven/main")

        // Use xpdustry's non-buggy repository for release Mindustry and Arc builds.
        if(!useJitpack) maven("https://maven.xpdustry.com/mindustry")
        maven("https://jitpack.io")
    }

    tasks.withType<JavaCompile>().configureEach{
        // Use Java 17+ syntax, but target Java 8 bytecode version.
        sourceCompatibility = "17"
        options.apply{
            release = 8
            compilerArgs.add("-Xlint:-options")

            isIncremental = true
            encoding = "UTF-8"
        }
    }
}

project(":"){
    apply(plugin = "com.github.GglLfr.EntityAnno")
    configure<EntityAnnoExtension>{
        modName = project.properties["modName"].toString()
        mindustryVersion = project.properties[if(useJitpack) "mindustryBEVersion" else "mindustryVersion"].toString()
        isJitpack = useJitpack
        revisionDir = layout.projectDirectory.dir("revisions").asFile
        fetchPackage = modFetch
        genSrcPackage = modGenSrc
        genPackage = modGen
    }

    dependencies{
        // Use the entity generation annotation processor.
        compileOnly(entity(":entity"))
        add("kapt", entity(":entity"))
	//implementation("org.luaj:luaj-jse:3.0.1")

        compileOnly(mindustry(":core"))
        compileOnly(arc(":arc-core"))
    }

    val jar = tasks.named<Jar>("jar"){
        archiveFileName = "${modArtifact}Desktop.jar"

        val meta = layout.projectDirectory.file("$temporaryDir/mod.json")
        from(
            files(sourceSets["main"].output.classesDirs),
            files(sourceSets["main"].output.resourcesDir),
            configurations.runtimeClasspath.map{conf -> conf.map{if(it.isDirectory) it else zipTree(it)}},

            files(layout.projectDirectory.dir("assets")),
            layout.projectDirectory.file("icon.png"),
            meta
        )

        metaInf.from(layout.projectDirectory.file("LICENSE"))
        doFirst{
            // Deliberately check if the mod meta is actually written in HJSON, since, well, some people actually use
            // it. But this is also not mentioned in the `README.md`, for the mischievous reason of driving beginners
            // into using JSON instead.
            val metaJson = layout.projectDirectory.file("mod.json")
            val metaHjson = layout.projectDirectory.file("mod.hjson")

            if(metaJson.asFile.exists() && metaHjson.asFile.exists()){
                throw IllegalStateException("Ambiguous mod meta: both `mod.json` and `mod.hjson` exist.")
            }else if(!metaJson.asFile.exists() && !metaHjson.asFile.exists()){
                throw IllegalStateException("Missing mod meta: neither `mod.json` nor `mod.hjson` exist.")
            }

            val isJson = metaJson.asFile.exists()
            val map = (if(isJson) metaJson else metaHjson).asFile
                .reader(Charsets.UTF_8)
                .use{Jval.read(it)}

            map.put("name", modName)
            meta.asFile.writer(Charsets.UTF_8).use{file -> BufferedWriter(file).use{map.writeTo(it, Jval.Jformat.formatted)}}
        }
    }

val dex = tasks.register<Jar>("dex") {
    inputs.files(jar)
    archiveFileName = "$modArtifact.jar"
    val desktopJar = jar.flatMap { it.archiveFile }
    val dexJar = File(temporaryDir, "Dex.jar")
    from(zipTree(desktopJar), zipTree(dexJar))
    
    doFirst {
        logger.lifecycle("Running `d8` (稳定版 34.0.0)...")
        val inputJar = desktopJar.get().asFile
        logger.lifecycle("Input Jar: ${inputJar.absolutePath}")
        
        // 1. 验证Android SDK和稳定版d8路径
        val sdkRoot = File(
            OS.env("ANDROID_SDK_ROOT") ?: OS.env("ANDROID_HOME") ?:
            throw IllegalStateException("Neither `ANDROID_SDK_ROOT` nor `ANDROID_HOME` is set.")
        )
        logger.lifecycle("Android SDK Root: ${sdkRoot.absolutePath}")
        
        // 强制使用稳定版d8路径
        val d8Path = File(sdkRoot, "build-tools/$androidBuildVersion/${if (OS.isWindows) "d8.bat" else "d8"}")
        if (!d8Path.exists()) {
            throw IllegalStateException("稳定版d8未找到：${d8Path.absolutePath}\n请执行`sdkmanager \"build-tools;$androidBuildVersion\"`安装")
        }
        logger.lifecycle("Using d8 (稳定版): ${d8Path.absolutePath}")
        
        // 2. 构建d8命令（基础参数）
        val command = mutableListOf<String>()
        if (OS.isWindows) {
            command.addAll(listOf("cmd", "/c"))
        }
        command.add(d8Path.absolutePath)
        command.addAll(listOf(
            "--release",
            "--min-api", androidMinVersion,
            "--output", dexJar.absolutePath,
            inputJar.absolutePath
        ))
        
        // 3. 逐个添加classpath（避免复合路径解析bug）
        val classpathJars = listOf(
            file("res/entity.jar"),
            file("res/EntityAnno.jar")
        ).filter { it.exists() }
        
        if (classpathJars.isEmpty()) {
            logger.warn("未找到任何classpath依赖Jar，可能导致编译失败！")
        } else {
            classpathJars.forEach { jar ->
                command.add("--classpath")
                command.add(jar.absolutePath)
                logger.lifecycle("Added classpath: ${jar.absolutePath}")
            }
        }
        
        // 4. 验证并添加稳定版平台包（android-28）
        val androidJar = File(sdkRoot, "platforms/android-$androidSdkVersion/android.jar")
        if (!androidJar.exists()) {
            throw IllegalStateException("Android平台包未找到：${androidJar.absolutePath}\n请执行`sdkmanager \"platforms;android-$androidSdkVersion\"`安装")
        }
        command.add("--lib")
        command.add(androidJar.absolutePath)
        logger.lifecycle("Android Platform Jar (稳定版): ${androidJar.absolutePath}")
        
        // 5. 打印最终命令（方便排查）
        logger.lifecycle("d8 Command: ${command.joinToString(" ")}")
        
        // 6. 执行d8并捕获错误日志
        val errorLogFile = File(temporaryDir, "d8-error.log")
        val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.to(errorLogFile))
            .start()
        val exitCode = process.waitFor()
        
        // 7. 处理执行结果
        logger.lifecycle("d8错误日志路径：${errorLogFile.absolutePath}")
        if (exitCode != 0) {
            val errorLog = errorLogFile.readText(Charsets.UTF_8)
            logger.error("d8执行失败（稳定版），错误日志：\n$errorLog")
            throw IllegalStateException("d8 execution failed with exit code $exitCode (稳定版)")
        }
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

