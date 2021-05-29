plugins {
    id 'com.android.application'
    id 'org.sonarqube' version '3.1.1'
    id 'com.github.triplet.play' version '2.7.5'
}
apply plugin: 'kotlin-android'

android {
    compileSdkVersion rootProject.compileSdkVersion
    buildToolsVersion rootProject.buildToolsVersion

    defaultConfig {
        versionCode 344
        versionName "59.00"

        applicationId "org.andstatus.app"
        minSdkVersion rootProject.minSdkVersion
        targetSdkVersion rootProject.targetSdkVersion

        testApplicationId "org.andstatus.app.tests"
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        // To test arguments:
        // testInstrumentationRunnerArgument "executionMode", "travisTest"
        archivesBaseName = "AndStatus-$versionName"
    }

    buildTypes {
        release {
            minifyEnabled false
            lintOptions {
                warning 'MissingTranslation','InvalidPackage'
            }
        }
        debug {
            testCoverageEnabled = project.hasProperty('testCoverageEnabled') ? project.testCoverageEnabled as boolean : false
        }
    }

    kotlinOptions {
        jvmTarget = '1.8'
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    packagingOptions {
        exclude 'META-INF/NOTICE'
        exclude 'META-INF/LICENSE'
    }
}

sonarqube {
    // See https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle
    // and https://sonarcloud.io/documentation/analysis/scan/sonarscanner-for-gradle/
    properties {
        property "sonar.organization", "default"
        property "sonar.projectName", "AndStatus"
        property "sonar.projectKey", "andstatus"
        property "sonar.projectVersion", project.android.defaultConfig.versionName

        property "sonar.sourceEncoding","UTF-8"
        // See http://docs.sonarqube.org/display/SONAR/Narrowing+the+Focus
        property "sonar.exclusions","build/**,libs/**,**/*.png,**/*.json,**/*.iml,**/*Secret.*"

        property "sonar.import_unknown_files", true

        property "sonar.android.lint.report", "./build/outputs/lint-results.xml"
    }
}

if (project.hasProperty("andstatus.google-play-publisher")
        && new File(project.property("andstatus.google-play-publisher").toString() + ".gradle").exists()) {
    apply from: project.property("andstatus.google-play-publisher") + ".gradle";
} else {
    play {
        enabled = false
    }
}

configurations {
    all {
        exclude group: 'org.apache.httpcomponents', module: 'httpclient'
    }
}

dependencies {
    implementation "androidx.annotation:annotation:$annotationVersion"
    implementation "androidx.appcompat:appcompat:$appCompatVersion"
    implementation "androidx.documentfile:documentfile:$documentFileVersion"
    implementation "androidx.preference:preference-ktx:$preferenceVersion"
    implementation "androidx.recyclerview:recyclerview:$recyclerViewVersion"
    implementation "androidx.swiperefreshlayout:swiperefreshlayout:$swipeRefreshLayoutVersion"
    implementation "ch.acra:acra-dialog:$acraVersion"
    implementation "ch.acra:acra-mail:$acraVersion"
    implementation "com.github.scribejava:scribejava-core:$scribeJavaCoreVersion"
    implementation "com.google.android.material:material:$materialVersion"
    implementation "commons-codec:commons-codec:$commonsCodecVersion"
    implementation "cz.msebera.android:httpclient:$httpClientVersion"
    implementation "io.vavr:vavr:$vavrVersion"
    implementation "junit:junit:$junitVersion"
    implementation "oauth.signpost:signpost-core:$signPostVersion"
    implementation "org.apache.commons:commons-lang3:$commonsLangVersion"
    implementation "org.apache.httpcomponents:httpmime:$httpMimeVersion"
    implementation "org.hamcrest:hamcrest:$hamcrestVersion"
    implementation "org.hamcrest:hamcrest-library:$hamcrestVersion"

    androidTestImplementation "androidx.test:runner:$testRunnerVersion"
    androidTestImplementation "androidx.test:rules:$testRulesVersion"
    androidTestImplementation "androidx.test.espresso:espresso-core:$espressoCoreVersion"
    androidTestImplementation "eu.bolt:screenshotty:$screenshottyVersion"
    implementation "androidx.core:core-ktx:$ktxVersion"
    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion"
}