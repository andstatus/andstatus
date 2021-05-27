import org.apache.tools.ant.DirectoryScanner

tasks {
    // With a workaround to include .git files in the dump, see https://issues.gradle.org/browse/GRADLE-1883
    // See https://docs.gradle.org/current/userguide/working_with_files.html
    DirectoryScanner.getDefaultExcludes().forEach { DirectoryScanner.removeDefaultExclude(it) }
    DirectoryScanner.addDefaultExclude("something has to be in here or everything gets excluded")

    register<Zip>("dumpAll") {
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
     * This task is for experiments and learning...
     *
     * We could define dependency this way, but we don't need this yet
     * task printInfo(dependsOn: rootProject.subprojects.find() { it.name == "app" }.tasks.find() { it.name == "myDummy"}) {
     */
    register("printInfo") {
        doFirst {
            println("printInfo.doFist")
            val root: Project = rootProject
            println("Projects (" + root.allprojects.size + "): " + rootProject.allprojects)
            println("Subprojects (" + rootProject.subprojects.size + "): " + rootProject.subprojects)
            println("Configurations (" + configurations.size + "): " + configurations)

            println("Root project tasks " + rootProject.tasks.size + ":\n  " +
                    rootProject.tasks.names.joinToString("\n  "))

            rootProject.subprojects.find() { it.name == "app" }?.let { app: Project ->
                println("Project \"" + app + "\"; Properties:" +
                        app.properties.entries
                            .map { "\n  " + it.key + ": " + it.value }
                            .sorted()
                            .joinToString("")
                )

                println("archivesBaseName: " + app.property("archivesBaseName"))

                println("Tasks \"" + app + "\", " + app.tasks.size + ":\n  " +
                        app.tasks.names.joinToString("\n  "))
                println("Task 0 \"" + app.tasks.firstOrNull())

                app.tasks.find() { it.name == "assembleRelease"}?.let { task: Task ->
                    println("Task \"" + task + "\"; " + task.inputs)
                }
            }
        }

        doLast {
            println("printInfo.doLast")
        }
    }
}
