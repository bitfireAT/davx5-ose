package info.git.versionHelper

import info.shell.runCommand

fun getGitCommitCount(offset: Int = 0): Int {
    val process = "git rev-list HEAD --count".runCommand()
    return process.toInt() + offset
}

fun getVersionText(): String {
    val processChanges = "git diff-index --name-only HEAD --".runCommand()
//    val processChanges = "git status --porcelain".runCommand()
    val dirty = if (processChanges.trim().isNotEmpty()) {
        // split lines by TAB (10) and add a leading space for better visibility
        println { "git status".italic+" is not clean, changes are:" }
        processChanges.split(10.toChar()).forEach { println { " $it".red.bold } }
        "-DIRTY"
    } else ""

    val givenVersion = System.getenv("tag") ?: "git describe --tags".runCommand().trim()
    return givenVersion + dirty
}
