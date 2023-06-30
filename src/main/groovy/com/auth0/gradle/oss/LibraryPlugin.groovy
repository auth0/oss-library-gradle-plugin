package com.auth0.gradle.oss

import com.auth0.gradle.oss.credentials.SonatypeConfiguration
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
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.plugins.signing.Sign

class LibraryPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.rootProject.pluginManager.apply(RootProjectPlugin)
        project.extensions.create('oss', Library)
        project.oss.extensions.developers = project.container(Developer)
        project.ext.isSnapshot = project.hasProperty('isSnapshot') ? project.isSnapshot.toBoolean() : true
        release(project)
        java(project)
        maven(project)
        japiCmp(project)
    }

    private void java(Project project) {
        def lib = project.extensions.oss
        project.configure(project) {
            apply plugin: 'java-library'
            apply plugin: 'maven-publish'
            apply plugin: 'signing'
            task('sourcesJar', type: Jar, dependsOn: classes) {
                archiveClassifier = 'sources'
                from sourceSets.main.allSource
            }
            task('javadocJar', type: Jar, dependsOn: javadoc) {
                archiveClassifier = 'javadoc'
                from javadoc.getDestinationDir()
            }
            tasks.withType(Javadoc).configureEach {
                javadocTool = javaToolchains.javadocToolFor {
                    // Use latest JDK for javadoc generation
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }
            project.afterEvaluate {
                for (ossPluginJavaTestVersion in lib.testInJavaVersions) {
                    def taskName = "testInJava-${ossPluginJavaTestVersion}"
                    tasks.register(taskName, Test) {
                        def versionToUse = taskName.split("-").getAt(1) as Integer
                        println "Test will be running in ${versionToUse}"
                        description = "Runs unit tests on different Java version ${versionToUse}."
                        group = 'verification'
                        javaLauncher.set(javaToolchains.launcherFor {
                            languageVersion = JavaLanguageVersion.of(versionToUse)
                        })
                        shouldRunAfter(tasks.named('test'))
                    }
                    tasks.named('check') {
                        dependsOn(taskName)
                    }
                }
                // tasks.named('signMavenJavaPublication') {
                //     dependsOn('jar')
                // }
            }
            javadoc {
                // Specify the Java version that the project will use
                options.addStringOption('-release', "8")
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
            publishing {
                publications.all {
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
                        // TODO not needed anymore?
                        // configurations.compile.getDependencies().each { dep -> addDependency(dep, "compile") }
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
                repositories {
                    maven {
                        url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
                        // Note: New non-existing packages uploaded since Feb 2021 use a different URL. See https://central.sonatype.org/pages/ossrh-guide.html
                        // url = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                        credentials {
                            // Attempts to find the Sonatype credentials.
                            // Fallback values are required so the plugin can still be applied.
                            username = project.findProperty('ossrhUsername') ?: "Missing ossrhUsername"
                            password = project.findProperty('ossrhPassword') ?: "Missing ossrhPassword"
                        }
                    }
                }
            }
            // gpg signing task
            signing {
                afterEvaluate {
                    required { isReleaseVersion && gradle.taskGraph.hasTask("publish") }
                    sign publishing.publications
                }
            }
        }

        def sonatypeConfiguration = new SonatypeConfiguration(project)
        project.tasks.withType(Sign) {
            dependsOn 'jar'
            doFirst {
                if (!lib.skipAssertSigningConfiguration) {
                    sonatypeConfiguration.assertSigningConfiguration()
                }
            }
        }
        project.tasks.withType(PublishToMavenRepository) {
            doFirst {
                sonatypeConfiguration.assertSonatypeCredentials()
                if (!project.ext.isReleaseVersion) {
                    project.logger.warn("WARN: Upload to a remote repository is not available when the version is of type snapshot. Version found: $project.version")
                }
            }
        }
    }

    private void release(Project project) {
        def hasVersion = project.version != null && project.version != "unspecified"
        def semver = hasVersion ? new Semver(project.version, project.ext.isSnapshot) : Semver.current()
        project.version = semver.version
        def version = semver.nonSnapshot
        project.ext.isReleaseVersion = !semver.snapshot
        def nextBeta = semver.nextBeta()
        def nextMajor = semver.nextMajor()
        def nextMinor = semver.nextMinor()
        def nextPatch = semver.nextPatch()
        // beta releases
        project.task('changelogBeta', type: ChangeLogTask) {
            current = version
            next = nextBeta
        }
        project.task('readmeBeta', type: ReadmeTask, dependsOn: 'changelogBeta') {
            current = version
            next = nextBeta
        }
        project.task('releaseBeta', type: ReleaseTask, dependsOn: 'readmeBeta') {
            tagName = nextBeta
        }
        // major releases
        project.task('changelogMajor', type: ChangeLogTask) {
            current = version
            next = nextMajor
        }
        project.task('readmeMajor', type: ReadmeTask, dependsOn: 'changelogMajor') {
            current = version
            next = nextMajor
        }
        project.task('releaseMajor', type: ReleaseTask, dependsOn: 'readmeMajor') {
            tagName = nextMajor
        }
        // minor releases
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
                it.name == "${project.name}-${baselineVersion}.jar"
            }
        } finally {
            project.group = group
        }
    }

}
