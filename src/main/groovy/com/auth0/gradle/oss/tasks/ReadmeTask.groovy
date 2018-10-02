package com.auth0.gradle.oss.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class ReadmeTask extends DefaultTask {

    def current
    def next

    final filename = 'README.md'

    @TaskAction
    void update() {
        def file = new File(filename)
        def gradleUpdated = "implementation '${project.group}:${project.name}:${next}'"
        def oldSingleQuote = "implementation '${project.group}:${project.name}:${current}'"
        def oldDoubleQuote = "implementation \"${project.group}:${project.name}:${current}\""
        def mavenUpdated = "<version>${next}</version>"
        def mavenOld = "<version>${current}</version>"
        def contents = file.getText('UTF-8')
        contents = contents.replace(oldSingleQuote, gradleUpdated).replace(oldDoubleQuote, gradleUpdated).replace(mavenOld, mavenUpdated)
        file.write(contents, 'UTF-8')
    }

}
