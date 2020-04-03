package com.auth0.gradle.oss.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class ReleaseTask extends DefaultTask {

    @Input
    def tagName

    @TaskAction
    def perform() {
        def path = project.getRootProject().getProjectDir().path
        project.exec {
            commandLine 'git', 'add', 'README.md'
            workingDir path
        }
        project.exec {
            commandLine 'git', 'add', 'CHANGELOG.md'
            workingDir path
        }
        project.exec {
            commandLine 'git', 'commit', '-m', "Release ${tagName}"
            workingDir path
        }
        project.exec {
            commandLine 'git', 'tag', "${tagName}"
            workingDir path
        }
    }
}