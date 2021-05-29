// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        mavenCentral()
        maven {
            url "https://maven.google.com"
        }
        google()
    }

    ext {
        compileSdkVersion = 29 as int
        buildToolsVersion = '30.0.3'
        minSdkVersion = 24 as int
        targetSdkVersion = 29 as int

        // Lookup the latest here: https://mvnrepository.com/
        acraVersion = '5.5.0'                // https://github.com/ACRA/acra/wiki/AdvancedUsage
        androidGradlePluginVersion = '4.2.1' // https://maven.google.com/web/index.html#com.android.tools.build:gradle
                                             // https://developer.android.com/studio/releases/gradle-plugin
        annotationVersion = '1.2.0'          // https://mvnrepository.com/artifact/androidx.annotation/annotation
        appCompatVersion = '1.2.0'           // https://mvnrepository.com/artifact/androidx.appcompat/appcompat
        commonsCodecVersion = '1.15'         // https://mvnrepository.com/artifact/commons-codec/commons-codec
        commonsLangVersion = '3.12.0'        // https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
        documentFileVersion = '1.0.1'        // https://developer.android.com/jetpack/androidx/releases/documentfile
        espressoCoreVersion = '3.3.0'        // https://developer.android.com/jetpack/androidx/releases/test
        hamcrestVersion = '2.2'              // http://hamcrest.org/JavaHamcrest/distributables#using-hamcrest-in-a-gradle-project
        httpClientVersion = '4.5.8'          // https://github.com/smarek/httpclient-android
        httpMimeVersion = '4.5.13'           // https://mvnrepository.com/artifact/org.apache.httpcomponents/httpmime
        junitVersion = '4.13.2'              // https://mvnrepository.com/artifact/junit/junit
        kotlinVersion = '1.5.0'              // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
        kotlinGradlePluginVersion = kotlinVersion  // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-gradle-plugin
        ktxVersion='1.3.2'                   // https://mvnrepository.com/artifact/androidx.core/core-ktx
        materialVersion = '1.3.0'            // https://mvnrepository.com/artifact/com.google.android.material/material
        preferenceVersion = '1.1.1'          // https://mvnrepository.com/artifact/androidx.preference/preference-ktx
        recyclerViewVersion = '1.2.0'        // https://mvnrepository.com/artifact/androidx.recyclerview/recyclerview
        screenshottyVersion = '1.0.3'        // https://github.com/bolteu/screenshotty
        scribeJavaCoreVersion = '8.3.1'      // https://github.com/scribejava/scribejava
        signPostVersion = '2.1.1'            // https://mvnrepository.com/artifact/oauth.signpost/signpost-core
        swipeRefreshLayoutVersion = '1.1.0'  // https://developer.android.com/jetpack/androidx/releases/swiperefreshlayout
        testRulesVersion = '1.3.0'           // https://developer.android.com/jetpack/androidx/releases/test
        testRunnerVersion = '1.3.0'          // https://developer.android.com/jetpack/androidx/releases/test
        vavrVersion = '1.0.0-alpha-2'        // https://github.com/vavr-io/vavr
    }

    dependencies {
        classpath "com.android.tools.build:gradle:$androidGradlePluginVersion"
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinGradlePluginVersion"

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }

}

allprojects {
    repositories {
        mavenCentral()
        maven {
            url "https://maven.google.com"
        }
    }
}

apply from: 'dump.gradle'