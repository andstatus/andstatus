plugins {
    id("com.android.application")
    id("jacoco")
    id("org.sonarqube").version("3.2.0")
// The plugin doesn't work now https://github.com/Triple-T/gradle-play-publisher
//    id("com.github.triplet.play").version("2.7.5")
    id("kotlin-android")
}

val HAS_TEST_COVERAGE: String = "hasTestCoverage"
val hasTestCoverage: Boolean get() = project.hasProperty(HAS_TEST_COVERAGE)
fun cancelIfNoCoverage() {
    if (!hasTestCoverage) {
        throw BuildCancelledException("Project property '$HAS_TEST_COVERAGE' should be defined")
    }
}

android {
    namespace = "org.andstatus.app"
    compileSdk = rootProject.extra["compileSdk"] as Int
    buildToolsVersion = rootProject.extra["buildToolsVersion"] as String

    defaultConfig {
        versionCode = 354
        versionName = "59.09"

        applicationId = "org.andstatus.app"
        minSdk = rootProject.extra["minSdk"] as Int
        targetSdk = rootProject.extra["targetSdk"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // To test arguments:
        // testInstrumentationRunnerArgument "executionMode", "travisTest"
        setProperty("archivesBaseName", "AndStatus-$versionName")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            lint {
                warning.add("MissingTranslation")
                warning.add("InvalidPackage")
            }
        }

        getByName("debug") {
            enableAndroidTestCoverage = hasTestCoverage
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
        resources {
            excludes += setOf("META-INF/NOTICE", "META-INF/LICENSE")
        }
    }

    testOptions {
        testCoverage {
            jacocoVersion = rootProject.extra["jacocoToolVersion"] as String
        }
    }

}

jacoco {
    toolVersion = rootProject.extra["jacocoToolVersion"] as String
}

tasks.register<JacocoReport>("jacocoUnitTestReport") {
    group = "verification"

    if (hasTestCoverage) {
        dependsOn("testDebugUnitTest")
    }

    sourceDirectories.setFrom(fileTree(projectDir) {
        include("/src/main/kotlin/**")
    })
    classDirectories.setFrom(fileTree(buildDir) {
        include(
            "**/intermediates/app_classes/debug/**",
            "**/intermediates/javac/debug/*/classes/**",
            "**/tmp/kotlin-classes/debug/**",
            "**/tmp/kotlin-classes/debugUnitTest/**"
        )

        exclude(
            "**/R.class",
            "**/R\$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/*\$Lambda$*.*", // Jacoco can not handle several "$" in class name?
            "**/*\$inlined$*.*"
        )
    })
    executionData.setFrom(fileTree(buildDir) {
        include("**/testDebugUnitTest.exec")
    })

    reports {
        xml.isEnabled = true
        xml.destination = file("$buildDir/reports/coverage/debugUnitTest/report.xml")
        html.isEnabled = true
        html.destination = file("$buildDir/reports/coverage/debugUnitTest/html")
    }

    doFirst {
        cancelIfNoCoverage()
    }
}

tasks.register("testTravis") {
    group = "verification"

    if (hasTestCoverage) {
        project.extra["android.testInstrumentationRunnerArguments.executionMode"] = "travisTest"
        println("Starting testing with Coverage")
        dependsOn(
            "createDebugCoverageReport",
            "jacocoUnitTestReport"
        )
        finalizedBy("sonarqube")
    }

    doFirst {
        cancelIfNoCoverage()
    }
}

// We cannot use built-in Result<T> here ?!
// https://stackoverflow.com/questions/52631827/why-cant-kotlin-result-be-used-as-a-return-type
class Result(val value: String?, val message: String?) {
    val isFailure: Boolean = message != null
}

val sonarQubeToken: Result = "org.andstatus.sonar.token".let { propertyName ->
    if (project.hasProperty(propertyName)) {
        project.property(propertyName).toString().let { str ->
            if (str.length < 40) {
                Result(null, "Ignoring too short SonarQube token: ${str.length} chars")
            } else Result(str, null)
        }
    } else {
        Result(null, "No '$propertyName' project.property defined for SonarQube")
    }
}

tasks.named("sonarqube") {
    doFirst {
        if (sonarQubeToken.isFailure) {
            val msg = "SonarQube skipped: " + sonarQubeToken.message
            println(msg)
            throw StopExecutionException(msg)
        }
        println("Starting SonarQube.")
    }
}

sonarqube {
    // See https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle
    // and https://sonarcloud.io/documentation/analysis/scan/sonarscanner-for-gradle/
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.login", sonarQubeToken.value ?: "")
        property("sonar.verbose", "true")

        property("sonar.organization", "default")
        property("sonar.projectName", "AndStatus")
        property("sonar.projectKey", "andstatus")
        property("sonar.projectVersion", project.android.defaultConfig.versionName!!)

        property("sonar.sourceEncoding", "UTF-8")
        // See http://docs.sonarqube.org/display/SONAR/Narrowing+the+Focus
        property("sonar.exclusions", "build/**,libs/**,**/*.png,**/*.json,**/*.iml,**/*Secret.*")

        property("sonar.import_unknown_files", true)

        property("sonar.android.lint.report", "build/reports/lint-results.xml")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/coverage/androidTest/debug/report.xml")
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

// This is needed until AndroidSourceSet starts supporting Kotlin directly
// See https://stackoverflow.com/a/61162647/297710
android.sourceSets.all {
    java.srcDir("src/$name/kotlin")
}
