package com.auth0.gradle.oss

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files

class AndroidLibraryPluginTest extends Specification {

    Project project

    void setup() {
        project = ProjectBuilder.builder().build()
        Files.copy(
                new File("local.properties").toPath(),
                new File(project.rootDir, "local.properties").toPath()
        )

        project.pluginManager.apply('com.auth0.gradle.oss-library.android')
    }

    @Unroll
    def "artifact task with name #name"() {
        given:
        def tasks = project.tasks
        expect:
        tasks.getByName(name)

        where:
        name         | _
        "sourcesJar" | _
        "javadocJar" | _
    }

    def "Ensure task configuration succeeds"() {
        when:
        def buildFile = new File(project.rootDir, "build.gradle")
        buildFile.createNewFile()
        buildFile.text = """
        buildscript {
            repositories.google()
            dependencies.classpath("com.android.tools.build:gradle:3.6.2")
        }

        plugins {
            id 'com.auth0.gradle.oss-library.android'
        }
        """

        then:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(project.rootDir)
                .withArguments("tasks", "--all")
                .build()
        result.task(":tasks").outcome == TaskOutcome.SUCCESS
    }
}
