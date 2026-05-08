package info.git.versionHelper

import info.shell.runCommand

fun getGitCommitCount(offset: Int = 0): Int {
    return try {
        val process = "git rev-list HEAD --count".runCommand()
        process.toInt() + offset
    } catch (e: Exception) {
        println("Warning: Git not available or command failed: ${e.message}")
        offset
    }
}

fun getVersionText(): String {
    return try {
        val processChanges = "git diff-index --name-only HEAD --".runCommand()
        //    val processChanges = "git status --porcelain".runCommand()
        val dirty = if (processChanges.trim().isNotEmpty()) {
            // split lines by TAB (10) and add a leading space for better visibility
            println { "git status".italic+" is not clean, changes are:" }
            processChanges.split(10.toChar()).forEach { println { " $it".red.bold } }
            "-DIRTY"
        } else ""

        val givenVersion = "git describe --tags".runCommand().trim()
        if (givenVersion.isBlank()) {
            println("Warning: Git describe returned empty output")
            "UNKNOWN"
        } else {
            givenVersion + dirty
        }
    } catch (e: Exception) {
        println("Warning: Git not available or command failed: ${e.message}")
        "UNKNOWN"
    }
}
