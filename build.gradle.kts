// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    val androidGradlePluginVersion = "4.2.1"      // https://maven.google.com/web/index.html#com.android.tools.build:gradle
                                                  // https://developer.android.com/studio/releases/gradle-plugin
    val kotlinVersion = "1.5.10"                  // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
    extra["kotlinVersion"] = kotlinVersion
    val kotlinGradlePluginVersion = kotlinVersion // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-gradle-plugin

    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.google.com")
        }
        google()
    }

    extra["compileSdkVersion"] = 29
    extra["buildToolsVersion"] = "30.0.3"
    extra["minSdkVersion"] = 24
    extra["targetSdkVersion"] = 29

    // Lookup the latest here: https://mvnrepository.com/
    extra["acraVersion"] = "5.8.1"                // https://github.com/ACRA/acra/wiki/AdvancedUsage
    extra["annotationVersion"] = "1.2.0"          // https://mvnrepository.com/artifact/androidx.annotation/annotation
    extra["appCompatVersion"] = "1.2.0"           // https://mvnrepository.com/artifact/androidx.appcompat/appcompat
    extra["commonsCodecVersion"] = "1.15"         // https://mvnrepository.com/artifact/commons-codec/commons-codec
    extra["commonsLangVersion"] = "3.12.0"        // https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
    extra["documentFileVersion"] = "1.0.1"        // https://developer.android.com/jetpack/androidx/releases/documentfile
    extra["espressoCoreVersion"] = "3.3.0"        // https://developer.android.com/jetpack/androidx/releases/test
    extra["hamcrestVersion"] = "2.2"              // http://hamcrest.org/JavaHamcrest/distributables#using-hamcrest-in-a-gradle-project
    extra["httpClientVersion"] = "4.5.8"          // https://github.com/smarek/httpclient-android
    extra["httpMimeVersion"] = "4.5.13"           // https://mvnrepository.com/artifact/org.apache.httpcomponents/httpmime
    extra["junitVersion"] = "4.13.2"              // https://mvnrepository.com/artifact/junit/junit
    extra["ktxVersion"] = "1.3.2"                 // https://mvnrepository.com/artifact/androidx.core/core-ktx
    extra["materialVersion"] = "1.3.0"            // https://mvnrepository.com/artifact/com.google.android.material/material
    extra["preferenceVersion"] = "1.1.1"          // https://mvnrepository.com/artifact/androidx.preference/preference-ktx
    extra["recyclerViewVersion"] = "1.2.0"        // https://mvnrepository.com/artifact/androidx.recyclerview/recyclerview
    extra["screenshottyVersion"] = "1.0.3"        // https://github.com/bolteu/screenshotty
    extra["scribeJavaCoreVersion"] = "8.3.1"      // https://github.com/scribejava/scribejava
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

apply("dump.gradle.kts")
