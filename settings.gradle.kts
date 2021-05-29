import org.apache.tools.ant.DirectoryScanner

// With a workaround to include .git files in the dump, see https://issues.gradle.org/browse/GRADLE-1883
// See https://docs.gradle.org/current/userguide/working_with_files.html
DirectoryScanner.getDefaultExcludes().forEach { DirectoryScanner.removeDefaultExclude(it) }
DirectoryScanner.addDefaultExclude("something has to be in here or everything gets excluded")

rootProject.buildFileName = "build.gradle.kts"
include(":app")
