package com.auth0.gradle.oss

import com.auth0.gradle.oss.extensions.Developer
import com.auth0.gradle.oss.extensions.Library
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class LibraryPluginTest extends Specification {

    Project project

    void setup() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply('com.auth0.gradle.java-oss-library')
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

    @Unroll
    def "release task with name #name"() {
        given:
        def tasks = project.tasks
        expect:
        tasks.getByName(name)

        where:
        name         | _
        "changelogMinor" | _
        "changelogPatch" | _
        "readmeMinor" | _
        "readmePatch" | _
        "releaseMinor" | _
        "releasePatch" | _
    }

    def "oss extension"() {
        expect:
        project.extensions.getByName("oss") instanceof Library
        project.oss.extensions.getByName("developers") instanceof NamedDomainObjectContainer<Developer>
    }

    @Unroll
    def "applies plugin #name"() {
        expect:
        project.plugins.getPlugin(name)

        where:
        name | _
        "java" | _
        "maven-publish" | _
    }
}
