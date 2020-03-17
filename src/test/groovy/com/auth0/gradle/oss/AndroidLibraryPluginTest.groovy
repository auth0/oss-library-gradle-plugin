package com.auth0.gradle.oss

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

class AndroidLibraryPluginTest extends Specification {

    Project project

    void setup() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply('com.auth0.gradle.oss-library.android')
    }

    @Ignore
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
