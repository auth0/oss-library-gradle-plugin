package com.auth0.gradle.oss

import com.auth0.gradle.oss.credentials.SonatypeConfiguration
import com.auth0.gradle.oss.extensions.Developer
import com.auth0.gradle.oss.extensions.Library
import com.auth0.gradle.oss.tasks.ChangeLogTask
import com.auth0.gradle.oss.tasks.ReadmeTask
import com.auth0.gradle.oss.tasks.ReleaseTask
import com.auth0.gradle.oss.versioning.Semver
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.plugins.signing.Sign
import org.gradle.testing.jacoco.tasks.JacocoReport

class AndroidLibraryPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.rootProject.pluginManager.apply(RootProjectPlugin)
        project.extensions.create('oss', Library)
        project.oss.extensions.developers = project.container(Developer)
        project.ext.isSnapshot = project.hasProperty('isSnapshot') ? project.isSnapshot.toBoolean() : true
        release(project)
        java(project)
        maven(project)
        jacoco(project)
        project.afterEvaluate {
            def variant = project.android.libraryVariants.toList().first()
            if (variant.hasProperty('javaCompileProvider')) {
                project.javadoc.classpath += variant.javaCompileProvider.get().classpath
            } else {
                project.javadoc.classpath += variant.javaCompile.classpath
            }
            project.publishToMavenLocal.dependsOn project.assemble
        }
    }

    private void java(Project project) {
        project.configure(project) {
            apply plugin: 'com.android.library'

            android {
                compileSdkVersion 28

                defaultConfig {
                    minSdkVersion 15
                    targetSdkVersion 28
                    versionCode 1
                    versionName project.version
                    buildConfigField "String", "LIBRARY_NAME", "\"$project.rootProject.name\""
                }
            }

            apply plugin: 'maven-publish'
            apply plugin: 'signing'
            task('sourcesJar', type: Jar) {
                from android.sourceSets.main.java.srcDirs
                classifier = 'sources'
            }

            task('javadoc', type: Javadoc) {
                source = android.sourceSets.main.java.srcDirs
                classpath += project.files(android.getBootClasspath().join(File.pathSeparator))
                android.libraryVariants.all { variant ->
                    if (variant.name == 'release') {
                        if (variant.hasProperty('javaCompileProvider')) {
                            owner.classpath += variant.javaCompileProvider.get().classpath
                        } else {
                            owner.classpath += variant.javaCompile.classpath
                        }
                    }
                }
                exclude '**/BuildConfig.java'
                exclude '**/R.java'
                failOnError false
            }

            task('javadocJar', type: Jar, dependsOn: javadoc) {
                classifier = 'javadoc'
                from javadoc.destinationDir
            }

            artifacts {
                archives sourcesJar, javadocJar
            }

            publishing {
                publications {
                    androidLibrary(MavenPublication) {
                        artifact("$buildDir/outputs/aar/${project.name}-release.aar")
                        artifact sourcesJar
                        artifact javadocJar
                        groupId project.group
                        artifactId project.name
                        version project.version
                    }
                }
            }

            apply plugin: 'jacoco'
            tasks.withType(Test).configureEach {
                jacoco.includeNoLocationClasses = true
                jacoco.excludes = ['jdk.internal.*']

                maxParallelForks = Runtime.getRuntime().availableProcessors()

                testLogging {
                    events 'passed', 'failed', 'skipped'
                    showStandardStreams = true
                    exceptionFormat 'full'
                }
            }
        }
    }

    private void maven(Project project) {
        def lib = project.extensions.oss

        project.configure(project) {
            publishing {
                publications.all {
                    pom.withXml {
                        def root = asNode()

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
                        configurations.compile.getAllDependencies().each { dep -> addDependency(dep, "compile") }
                        // List all "api" dependencies (for new Gradle) as "compile" dependencies
                        configurations.api.getAllDependencies().each { dep -> addDependency(dep, "compile") }
                        // List all "implementation" dependencies (for new Gradle) as "runtime" dependencies
                        configurations.implementation.getAllDependencies().each { dep -> addDependency(dep, "runtime") }

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
        project.ext.isReleaseVersion = !semver.snapshot
        def version = semver.nonSnapshot
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
        // patch releases
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

    private void jacoco(Project project) {
        project.configure(project) {
            apply plugin: 'jacoco'

            jacoco {
                // https://bintray.com/bintray/jcenter/org.jacoco:org.jacoco.core
                toolVersion = "0.8.5"
            }

            android {
                testOptions {
                    unitTests.all {
                        jacoco {
                            includeNoLocationClasses = true
                        }
                    }
                }
            }
        }

        project.afterEvaluate {

            def jacocoTestReportTask = project.tasks.findByName("jacocoTestReport")
            if (!jacocoTestReportTask) {
                jacocoTestReportTask = project.tasks.create("jacocoTestReport")
                jacocoTestReportTask.group = "Reporting"
                jacocoTestReportTask.description = "Generate Jacoco coverage reports for all builds."
            }

            project.android.libraryVariants.all { variant ->
                def name = variant.name
                def testTaskName = "test${name.capitalize()}UnitTest"

                def reportTask = project.tasks.create(name: "jacocoTest${name.capitalize()}UnitTestReport", type: JacocoReport, dependsOn: "$testTaskName") {
                    group = "Reporting"
                    description = "Generate Jacoco coverage reports for the ${name.capitalize()} build."

                    classDirectories.from = project.fileTree(
                            dir: "${project.buildDir}/intermediates/javac/${name}",
                            excludes: ['**/R.class',
                                       '**/R$*.class',
                                       '**/*$ViewInjector*.*',
                                       '**/*$ViewBinder*.*',
                                       '**/BuildConfig.*',
                                       '**/Manifest*.*']
                    )

                    sourceDirectories.from = ['src/main/java'].plus(project.android.sourceSets[name].java.srcDirs)
                    executionData.from = "${project.buildDir}/jacoco/${testTaskName}.exec"

                    reports {
                        xml.enabled = true
                        html.enabled = true
                    }
                }

                jacocoTestReportTask.dependsOn reportTask
            }
        }
    }
}
