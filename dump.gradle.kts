apply plugin: 'java'

// With a workaround to include .git files in the dump
// See https://issues.gradle.org/browse/GRADLE-1883
import org.apache.tools.ant.DirectoryScanner
task dumpAll(type: Zip) {
    doFirst {
        println 'dumpAll started'

        Project app = rootProject.subprojects.find() { it.name == "app" }
        println "app archivesBaseName: " + app.archivesBaseName
        baseName = app.archivesBaseName
        version = ""

        DirectoryScanner.defaultExcludes.each { DirectoryScanner.removeDefaultExclude it }
        DirectoryScanner.addDefaultExclude 'something has to be in here or everything gets excluded'

        destinationDir = new File("../Archives")

        for (int i = 1; i < 1000; i++) {
            classifier = String.format("%03d%s", i, "-dump")
            File file = archivePath
            if (!file.exists()) {
                println file.getName() + " does not exist"
                break
            }
            println file.getName() + " exists"
        }

        println "On creating " + archiveName + " in " + relativePath(destinationDir)
    }

    from projectDir
    include "*/**"
    exclude "**/.gradle/**"
    exclude "**/build/**"
    exclude "**/temp/**"

    doLast {
        DirectoryScanner.resetDefaultExcludes()

        if (archivePath.exists()) {
            println "Successfully created '" + archiveName + "'"
        } else {
            println "ERROR: No dump created '" + archiveName + "'"
        }
    }
}

/**
 * This task is for experiments and learning...
 *
 * We could define dependency this way, but we don't need this yet
 * task printInfo(dependsOn: rootProject.subprojects.find() { it.name == "app" }.tasks.find() { it.name == "myDummy"}) {
 */
task printInfo() {
    doFirst {
        println "printInfo.doFist"
        println "Projects (" + rootProject.getAllprojects().size() + "): " + rootProject.getAllprojects()
        println "Project 0: \"" + rootProject.getAllprojects().getAt(0) + "\"; " + rootProject.getAllprojects().getAt(0).getProperties()
        println "Subprojects (" + rootProject.getSubprojects().size() + "): " + rootProject.getSubprojects()
        println "Configurations (" + configurations.size() + "): " + configurations

        Project app0 = rootProject.subprojects.getAt(0);
        println "Project 0 \"" + app0 + "\"; " + app0.getProperties()

        Project app = rootProject.subprojects.find() { it.name == "app" }
        println "Project \"" + app + "\"; " + app.getProperties()

        println "Ext: " + app.ext
        println "archivesBaseName: " + app.ext.get("archivesBaseName")
        println "version: " + app.ext.get("myVersionName")

        println "Tasks \"" + app + "\"; " + app.tasks
        println "Task 0 \"" + app.tasks.getAt(0) + "\"; " + app.tasks.getAt(0).properties

        Task task = app.tasks.find() { it.name == "assembleRelease"}
        println "Task \"" + task + "\"; " + (( task != null) ? task.properties : "")
    }

    doLast {
        println "printInfo.doLast"
    }
}