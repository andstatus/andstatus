import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    val androidGradlePluginVersion = "7.0.2"      // https://maven.google.com/web/index.html#com.android.tools.build:gradle
                                                  // https://developer.android.com/studio/releases/gradle-plugin
    val kotlinVersion = "1.5.20"                  // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
    extra["kotlinVersion"] = kotlinVersion
    val kotlinGradlePluginVersion = kotlinVersion // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-gradle-plugin

    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.google.com")
        }
        google()
    }

    extra["compileSdk"] = 30
    extra["buildToolsVersion"] = "30.0.3"
    extra["minSdk"] = 24
    extra["targetSdk"] = 30

    // Lookup the latest here: https://mvnrepository.com/
    extra["acraVersion"] = "5.8.3"                // https://github.com/ACRA/acra/wiki/AdvancedUsage
    extra["annotationVersion"] = "1.2.0"          // https://mvnrepository.com/artifact/androidx.annotation/annotation
    extra["appCompatVersion"] = "1.2.0"           // https://mvnrepository.com/artifact/androidx.appcompat/appcompat
    extra["commonsLangVersion"] = "3.12.0"        // https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
    extra["documentFileVersion"] = "1.0.1"        // https://developer.android.com/jetpack/androidx/releases/documentfile
    extra["espressoCoreVersion"] = "3.3.0"        // https://developer.android.com/jetpack/androidx/releases/test
    extra["hamcrestVersion"] = "2.2"              // http://hamcrest.org/JavaHamcrest/distributables#using-hamcrest-in-a-gradle-project
    extra["httpClientVersion"] = "4.5.8"          // https://github.com/smarek/httpclient-android
    extra["httpMimeVersion"] = "4.5.13"           // https://mvnrepository.com/artifact/org.apache.httpcomponents/httpmime
    extra["jacocoToolVersion"] = "0.8.7"          // https://mvnrepository.com/artifact/org.jacoco/org.jacoco.agent
    extra["junitVersion"] = "4.13.2"              // https://mvnrepository.com/artifact/junit/junit
    extra["ktxVersion"] = "1.3.2"                 // https://mvnrepository.com/artifact/androidx.core/core-ktx
    extra["materialVersion"] = "1.3.0"            // https://mvnrepository.com/artifact/com.google.android.material/material
    extra["preferenceVersion"] = "1.1.1"          // https://mvnrepository.com/artifact/androidx.preference/preference-ktx
    extra["recyclerViewVersion"] = "1.2.0"        // https://mvnrepository.com/artifact/androidx.recyclerview/recyclerview
    extra["screenshottyVersion"] = "1.0.3"        // https://github.com/bolteu/screenshotty
    extra["scribeJavaCoreVersion"] = "8.1.0"      // https://github.com/scribejava/scribejava
    extra["signPostVersion"] = "2.1.1"            // https://mvnrepository.com/artifact/oauth.signpost/signpost-core
    extra["swipeRefreshLayoutVersion"] = "1.1.0"  // https://developer.android.com/jetpack/androidx/releases/swiperefreshlayout
    extra["testRulesVersion"] = "1.3.0"           // https://developer.android.com/jetpack/androidx/releases/test
    extra["testRunnerVersion"] = "1.3.0"          // https://developer.android.com/jetpack/androidx/releases/test
    extra["vavrVersion"] = "1.0.0-alpha-2"        // https://github.com/vavr-io/vavr

    dependencies {
        classpath("com.android.tools.build:gradle:${androidGradlePluginVersion}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinGradlePluginVersion")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle.kts files
    }

}

allprojects {
    repositories {
        mavenCentral()
        maven(url = "https://maven.google.com")
    }
}

tasks.register<Zip>("dumpAll") {
    destinationDirectory.set(File("../Archives"))
    archiveFileName.set("initial.zip")

    doFirst {
        val app: Project = subprojects.find { it.name == "app" } ?: throw kotlin.IllegalStateException("No 'app' subproject")
        val prefix: String = app.property("archivesBaseName").toString()

        println("dumpAll started, archivesBaseName: " + prefix)

        for (i in 1 .. 999) {
            val suffix = String.format("-%03d%s", i, "-dump.zip")
            val file = File(destinationDirectory.asFile.orNull, prefix + suffix)
            if (!file.exists()) {
                println(file.getName() + " does not exist")
                archiveFileName.set(file.absolutePath)
                break
            }
            println(file.getName() + " exists")
        }

        println("On creating " + archiveFileName.get())
    }

    from(projectDir)

    include("*/**")
    exclude("**/.gradle/**")
    exclude("**/build/**")
    exclude("**/temp/**")

    doLast {
        if (archiveFile.get().asFile.exists()) {
            println("Successfully created " + archiveFileName.get())
        } else {
            println("ERROR: No dump created " + archiveFileName.get())
        }
    }
}

/**
 * Task for experiments and learning...
 */
tasks.register("printInfo") {

    fun printProjectInfo(project: Project) {
        println("\nProject '${project.name}' ----------------")

        project.subprojects.ifNotEmpty {
            println("Subprojects ($size): $this")
        }

        "archivesBaseName".let { name ->
            if (project.hasProperty(name)) {
                println("$name property: " + project.property(name))
            }
        }

        println("Properties (" + project.properties.entries.size + ")" +
            project.properties.entries
                .map {
                    it.key + ": " +
                        (if (it.key == "properties") "(not shown)" else it.value)
                }
                .sorted()
                .joinToString("\n  ", ":\n  ")
        )

        println(
            "Configurations (" + project.configurations.size + ")" +
                project.configurations.names
                    .joinToString("\n  ", ":\n  ")
        )

        println("First task: '" + project.tasks.firstOrNull() + "'")
        println(
            "Tasks (" + project.tasks.size + ")" +
                project.tasks.names
                    .joinToString("\n  ", ":\n  ")
        )

        project.tasks.find() { it.name == "assembleRelease" }?.let { task: Task ->
            println("Task '" + task + "' inputs:\n  " + task.inputs)
        }
    }

    doFirst {
        println("printInfo.doFist")
        rootProject.allprojects.forEach(::printProjectInfo)
    }

    doLast {
        println("printInfo.doLast")
    }
}
