package com.auth0.gradle.oss

import com.auth0.gradle.oss.credentials.BintrayCredentials
import com.auth0.gradle.oss.extensions.Developer
import com.auth0.gradle.oss.extensions.Library
import com.auth0.gradle.oss.tasks.ChangeLogTask
import com.auth0.gradle.oss.tasks.ReadmeTask
import com.auth0.gradle.oss.tasks.ReleaseTask
import com.auth0.gradle.oss.versioning.Semver
import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

class LibraryPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.rootProject.pluginManager.apply(RootProjectPlugin)
        project.extensions.create('oss', Library)
        project.oss.extensions.developers = project.container(Developer)
        release(project)
        java(project)
        maven(project)
        bintray(project)
        japiCmp(project)
    }

    private void java(Project project) {
        project.configure(project) {
            apply plugin: 'java-library'
            apply plugin: 'maven-publish'
            task('sourcesJar', type: Jar, dependsOn: classes) {
                classifier = 'sources'
                from sourceSets.main.allSource
            }
            task('javadocJar', type: Jar, dependsOn: javadoc) {
                classifier = 'javadoc'
                from javadoc.getDestinationDir()
            }

            artifacts {
                archives sourcesJar, javadocJar
            }

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        artifact("$buildDir/libs/${project.name}-${project.version}.jar")
                        artifact sourcesJar
                        artifact javadocJar
                    }
                }
            }
        }
        project.tasks.withType(Jar) {
            manifest {
                attributes 'Implementation-Title': project.name, 'Implementation-Version': project.version
            }
        }
    }

    private void maven(Project project, String packaging = 'jar') {
        def lib = project.extensions.oss
        project.configure(project) {
            publishing.publications.all {
                pom.withXml {
                    def root = asNode()

                    root.appendNode('packaging', packaging)
                    root.appendNode('name', lib.name)
                    root.appendNode('description', lib.description)
                    root.appendNode('url', "https://github.com/${lib.organization}/${lib.repository}")

                    def developersNode = root.appendNode('developers')
                    project.oss.extensions.developers.each {
                        def node = developersNode.appendNode('developer')
                        node.appendNode('id', it.name)
                        node.appendNode('name', it.displayName)
                        node.appendNode('email', it.email)
                    }

                    def dependenciesNode = root.appendNode('dependencies')
                    def artifacts = []

                    ext.addDependency = { Dependency dep, String scope ->
                        if (dep.group == null || dep.version == null || dep.name == null || dep.name == "unspecified")
                            return // ignore invalid dependencies
                        if (artifacts.contains("${dep.group}:${dep.name}"))
                            return // ignore duplicates

                        final dependencyNode = dependenciesNode.appendNode('dependency')
                        dependencyNode.appendNode('groupId', dep.group)
                        dependencyNode.appendNode('artifactId', dep.name)
                        dependencyNode.appendNode('version', dep.version)
                        dependencyNode.appendNode('scope', scope)
                        artifacts.add("${dep.group}:${dep.name}")

                        if (!dep.transitive) {
                            // If this dependency is transitive, we should force exclude all its dependencies them from the POM
                            final exclusionNode = dependencyNode.appendNode('exclusions').appendNode('exclusion')
                            exclusionNode.appendNode('groupId', '*')
                            exclusionNode.appendNode('artifactId', '*')
                        } else if (!dep.properties.excludeRules.empty) {
                            // Otherwise add specified exclude rules
                            final exclusionNode = dependencyNode.appendNode('exclusions').appendNode('exclusion')
                            dep.properties.excludeRules.each { ExcludeRule rule ->
                                exclusionNode.appendNode('groupId', rule.group ?: '*')
                                exclusionNode.appendNode('artifactId', rule.module ?: '*')
                            }
                        }
                    }

                    // List all "compile" dependencies (for Gradle < 3.x)
                    configurations.compile.getDependencies().each { dep -> addDependency(dep, "compile") }
                    // List all "api" dependencies (for new Gradle) as "compile" dependencies
                    configurations.api.getDependencies().each { dep -> addDependency(dep, "compile") }
                    // List all "implementation" dependencies (for new Gradle) as "runtime" dependencies
                    configurations.implementation.getDependencies().each { dep -> addDependency(dep, "runtime") }
                    // List all "compileOnly" dependencies (for new Gradle) as "provided" dependencies
                    configurations.compileOnly.getDependencies().each { dep -> addDependency(dep, "provided") }

                    def licenceNode = root.appendNode('licenses').appendNode('license')
                    licenceNode.appendNode('name', 'The MIT License (MIT)')
                    licenceNode.appendNode('url', "https://raw.githubusercontent.com/${lib.organization}/${lib.repository}/master/LICENSE")
                    licenceNode.appendNode('distribution', 'repo')

                    def scmNode = root.appendNode('scm')
                    scmNode.appendNode('connection', "scm:git@github.com:${lib.organization}/${lib.repository}.git")
                    scmNode.appendNode('developerConnection', "scm:git@github.com:${lib.organization}/${lib.repository}.git")
                    scmNode.appendNode('url', "https://github.com/${lib.organization}/${lib.repository}")
                }
            }
        }
    }

    private void release(Project project) {
        def semver = Semver.current()
        project.version = semver.version
        def version = semver.nonSnapshot
        def nextMinor = semver.nextMinor()
        def nextPatch = semver.nextPatch()
        project.task('changelogMinor', type: ChangeLogTask) {
            current = version
            next = nextMinor
        }
        project.task('readmeMinor', type: ReadmeTask, dependsOn: 'changelogMinor') {
            current = version
            next = nextMinor
        }
        project.task('releaseMinor', type: ReleaseTask, dependsOn: 'readmeMinor') {
            tagName = nextMinor
        }
        project.task('changelogPatch', type: ChangeLogTask) {
            current = version
            next = nextPatch
        }
        project.task('readmePatch', type: ReadmeTask, dependsOn: 'changelogPatch') {
            current = version
            next = nextPatch
        }
        project.task('releasePatch', type: ReleaseTask, dependsOn: 'readmePatch') {
            tagName = nextPatch
        }
        if (!semver.isStable()) {
            def nextPrerelease = semver.nextPrerelease()
            project.task('changelogPrerelease', type: ChangeLogTask) {
                current = version
                next = nextPrerelease
            }
            project.task('readmePrerelease', type: ReadmeTask, dependsOn: 'changelogPrerelease') {
                current = version
                next = nextPrerelease
            }
            project.task('releasePrerelease', type: ReleaseTask, dependsOn: 'readmePrerelease') {
                tagName = nextPrerelease
            }
        }
    }

    private void bintray(Project project) {
        def credentials = new BintrayCredentials(project)

        if (credentials.valid()) {
            project.pluginManager.apply('com.jfrog.bintray')
            project.configure(project) {
                bintray {
                    user = credentials.user
                    key = credentials.key
                    publications = ['mavenJava']
                    dryRun = project.version.endsWith("-SNAPSHOT")
                    publish = false
                    pkg {
                        repo = 'java'
                        name = project.name
                        licenses = ["MIT"]
                        userOrg = 'auth0'
                        publish = false
                        version {
                            gpg {
                                sign = true
                                passphrase = credentials.passphrasse
                            }
                            vcsTag = project.version
                            name = project.version
                            released = new Date()
                        }
                    }
                }
            }
        }
    }

    private void japiCmp(Project project) {
        project.afterEvaluate {
            def lib = project.extensions.oss
            def baselineVersion = lib.baselineCompareVersion
            if (!baselineVersion) {
                return
            }
            project.configure(project) {
                apply plugin: 'me.champeau.gradle.japicmp'
                task('apiDiff', type: JapicmpTask, dependsOn: 'jar') {
                    oldClasspath = files(getBaselineJar(project, baselineVersion))
                    newClasspath = files(jar.archiveFile)
                    onlyModified = true
                    failOnModification = true
                    ignoreMissingClasses = true
                    htmlOutputFile = file("$buildDir/reports/apiDiff/apiDiff.html")
                    txtOutputFile = file("$buildDir/reports/apiDiff/apiDiff.txt")
                    doLast {
                        project.logger.quiet("Comparing against baseline version ${baselineVersion}")
                    }
                }
            }
        }

    }

    private static File getBaselineJar(Project project, String baselineVersion) {
        // Use detached configuration: https://github.com/square/okhttp/blob/master/build.gradle#L270
        def group = project.group
        try {
            def baseline = "${project.group}:${project.name}:$baselineVersion"
            project.group = 'virtual_group_for_japicmp'
            def dependency = project.dependencies.create(baseline + "@jar")
            return project.configurations.detachedConfiguration(dependency).files.find {
                it.name ==  "${project.name}-${baselineVersion}.jar"
            }
        } finally {
            project.group = group
        }
    }

}
