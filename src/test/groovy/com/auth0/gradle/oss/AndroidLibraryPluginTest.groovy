package com.auth0.gradle.oss

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
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

}
