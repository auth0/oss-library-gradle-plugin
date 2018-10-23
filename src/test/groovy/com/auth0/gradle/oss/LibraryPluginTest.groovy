package com.auth0.gradle.oss

import com.auth0.gradle.oss.extensions.Developer
import com.auth0.gradle.oss.extensions.Library
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class LibraryPluginTest extends Specification {

    Project project

    void setup() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply('com.auth0.gradle.oss-library.java')
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
        "java-library" | _
        "maven-publish" | _
    }

    def "allows to set developers"() {
        given:
        project.configure(project) {
            oss {
                name 'auth0'
                repository 'auth0-repo'
                organization 'auth0'
                description 'Auth0 Lib'
                developers {
                    auth0 {
                        displayName = 'Auth0'
                        email = 'oss@auth0.com'
                    }
                    hzalaz {
                        displayName = 'hzalaz'
                        email = 'hernan@auth0.com'
                    }
                }
            }
        }
        def developers = project.oss.extensions.developers
        expect:
        developers.size() == 2
        developers[0].name == 'auth0'
        developers[1].name == 'hzalaz'
    }
}
