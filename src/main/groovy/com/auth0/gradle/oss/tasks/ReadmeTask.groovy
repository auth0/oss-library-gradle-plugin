package com.auth0.gradle.oss.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

class ReadmeTask extends DefaultTask {

    @Input
    def current
    @Input
    def next
    @Internal
    final filename = 'README.md'

    @TaskAction
    void update() {
        def file = new File(filename)
        def gradleUpdated = "implementation '${project.group}:${project.name}:${next}'"
        def oldLegacySingleQuote = "compile '${project.group}:${project.name}:${current}'"
        def oldLegacyDoubleQuote = "compile \"${project.group}:${project.name}:${current}\""
        def oldSingleQuote = "implementation '${project.group}:${project.name}:${current}'"
        def oldDoubleQuote = "implementation \"${project.group}:${project.name}:${current}\""
        def mavenUpdated = "<version>${next}</version>"
        def mavenOld = "<version>${current}</version>"
        def contents = file.getText('UTF-8')
        contents = contents.replace(mavenOld, mavenUpdated)
                .replace(oldSingleQuote, gradleUpdated)
                .replace(oldLegacySingleQuote, gradleUpdated)
                .replace(oldDoubleQuote, gradleUpdated)
                .replace(oldLegacyDoubleQuote, gradleUpdated)
        file.write(contents, 'UTF-8')
    }

}