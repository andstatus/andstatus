plugins {
    id("com.android.application")
    id("jacoco")
    id("org.sonarqube").version("3.2.0")
// The plugin doesn't work now https://github.com/Triple-T/gradle-play-publisher
//    id("com.github.triplet.play").version("2.7.5")
    id("kotlin-android")
}

android {
    compileSdkVersion(rootProject.extra["compileSdkVersion"] as Int)
    buildToolsVersion(rootProject.extra["buildToolsVersion"] as String)

    defaultConfig {
        versionCode = 345
        versionName = "59.01"

        applicationId = "org.andstatus.app"
        minSdkVersion(rootProject.extra["minSdkVersion"] as Int)
        targetSdkVersion(rootProject.extra["targetSdkVersion"] as Int)

        testApplicationId = "org.andstatus.app.tests"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // To test arguments:
        // testInstrumentationRunnerArgument "executionMode", "travisTest"
        setProperty("archivesBaseName", "AndStatus-$versionName")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            lintOptions {
                warning("MissingTranslation", "InvalidPackage")
            }
        }
        getByName("debug") {
            isTestCoverageEnabled = true
//            if ( rootProject.hasProperty("testCoverageEnabled")) {
//                rootProject.property("testCoverageEnabled") == "true"
//            } else false
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions {
        exclude("META-INF/NOTICE")
        exclude("META-INF/LICENSE")
    }

    testOptions {
        jacoco {
            version = "0.8.7"
        }
    }

}

// This doesn't work yet...
task<JacocoReport>("myJacocoTestReport") {
    group = "verification"

    val classDirsTree = fileTree(buildDir) {
        include(
            "**/classes/**/main/**",
            "**/intermediates/classes/debug/**",
            "**/intermediates/javac/debug/*/classes/**",
            "**/tmp/kotlin-classes/debug/**"
        )

        exclude(
            "**/R.class",
            "**/R\$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/*\$Lambda$*.*", // Jacoco can not handle several "$" in class name.
            "**/*\$inlined$*.*"
        )
    }
    val mainSrc = fileTree(project.buildDir) {
        include("/src/main/java/**")
    }

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(classDirsTree))
    executionData.setFrom(fileTree(buildDir) {
        include("jacoco/testDebugUnitTest.exec", "outputs/code-coverage/connected/*coverage.ec")
    })

    reports {
        xml.isEnabled = true
        html.isEnabled = true
    }
}

sonarqube {
    // See https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle
    // and https://sonarcloud.io/documentation/analysis/scan/sonarscanner-for-gradle/
    properties {
        property("sonar.organization", "default")
        property("sonar.projectName", "AndStatus")
        property("sonar.projectKey", "andstatus")
        property("sonar.projectVersion", project.android.defaultConfig.versionName!!)

        property("sonar.sourceEncoding", "UTF-8")
        // See http://docs.sonarqube.org/display/SONAR/Narrowing+the+Focus
        property("sonar.exclusions", "build/**,libs/**,**/*.png,**/*.json,**/*.iml,**/*Secret.*")

        property("sonar.import_unknown_files", true)

        property("sonar.android.lint.report", "build/reports/lint-results.xml")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/coverage/debug/report.xml")
    }
}

//play {
//    enabled.set(false)
//}

configurations {
    all {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
}

dependencies {
    implementation("androidx.annotation:annotation:${rootProject.extra["annotationVersion"]}")
    implementation("androidx.appcompat:appcompat:${rootProject.extra["appCompatVersion"]}")
    implementation("androidx.documentfile:documentfile:${rootProject.extra["documentFileVersion"]}")
    implementation("androidx.preference:preference-ktx:${rootProject.extra["preferenceVersion"]}")
    implementation("androidx.recyclerview:recyclerview:${rootProject.extra["recyclerViewVersion"]}")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:${rootProject.extra["swipeRefreshLayoutVersion"]}")
    implementation("ch.acra:acra-dialog:${rootProject.extra["acraVersion"]}")
    implementation("ch.acra:acra-mail:${rootProject.extra["acraVersion"]}")
    implementation("com.github.scribejava:scribejava-core:${rootProject.extra["scribeJavaCoreVersion"]}")
    implementation("com.google.android.material:material:${rootProject.extra["materialVersion"]}")
    implementation("commons-codec:commons-codec:${rootProject.extra["commonsCodecVersion"]}")
    implementation("cz.msebera.android:httpclient:${rootProject.extra["httpClientVersion"]}")
    implementation("io.vavr:vavr:${rootProject.extra["vavrVersion"]}")
    implementation("junit:junit:${rootProject.extra["junitVersion"]}")
    implementation("oauth.signpost:signpost-core:${rootProject.extra["signPostVersion"]}")
    implementation("org.apache.commons:commons-lang3:${rootProject.extra["commonsLangVersion"]}")
    implementation("org.apache.httpcomponents:httpmime:${rootProject.extra["httpMimeVersion"]}")
    implementation("org.hamcrest:hamcrest:${rootProject.extra["hamcrestVersion"]}")
    implementation("org.hamcrest:hamcrest-library:${rootProject.extra["hamcrestVersion"]}")

    androidTestImplementation("androidx.test:runner:${rootProject.extra["testRunnerVersion"]}")
    androidTestImplementation("androidx.test:rules:${rootProject.extra["testRulesVersion"]}")
    androidTestImplementation("androidx.test.espresso:espresso-core:${rootProject.extra["espressoCoreVersion"]}")
    androidTestImplementation("eu.bolt:screenshotty:${rootProject.extra["screenshottyVersion"]}")
    implementation("androidx.core:core-ktx:${rootProject.extra["ktxVersion"]}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${rootProject.extra["kotlinVersion"]}")
}
