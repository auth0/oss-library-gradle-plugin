package com.auth0.gradle.oss

import com.auth0.gradle.oss.credentials.BintrayCredentials
import com.auth0.gradle.oss.extensions.Developer
import com.auth0.gradle.oss.extensions.Library
import com.auth0.gradle.oss.tasks.ChangeLogTask
import com.auth0.gradle.oss.tasks.ReadmeTask
import com.auth0.gradle.oss.tasks.ReleaseTask
import com.auth0.gradle.oss.versioning.Semver
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc

class AndroidLibraryPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create('oss', Library)
        project.oss.extensions.developers = project.container(Developer)
        release(project)
        java(project)
        maven(project)
        bintray(project)
    }

    private void java(Project project) {
        project.configure(project) {
            apply plugin: 'com.android.library'

            android {
                compileSdkVersion 25
                buildToolsVersion '25.0.1'

                defaultConfig {
                    minSdkVersion 15
                    targetSdkVersion 25
                    versionCode 1
                    versionName project.version
                    buildConfigField "String", "LIBRARY_NAME", "\"$project.oss.name\""
                }
                buildTypes {
                    release {
                        minifyEnabled false
                        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
                    }
                }
                lintOptions {
                    warning 'InvalidPackage'
                }
            }

            apply plugin: 'maven-publish'
            task('sourcesJar', type: Jar) {
                from android.sourceSets.main.java.srcDirs
                classifier = 'sources'
            }

            task('javadoc', type: Javadoc) {
                source = android.sourceSets.main.java.srcDirs
                classpath += project.files(android.getBootClasspath().join(File.pathSeparator))
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
                    mavenAAR(MavenPublication) {
                        artifact("$buildDir/outputs/aar/${project.name}-release.aar")
                        artifact sourcesJar
                        artifact javadocJar
                        groupId project.group
                        artifactId project.name
                        version project.version
                    }
                }
            }
        }
    }

    private void maven(Project project, String packaging = 'aar') {
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

                    def compileArtifacts = []
                    configurations.compile.allDependencies.each {
                        def dependencyNode = dependenciesNode.appendNode('dependency')
                        dependencyNode.appendNode('groupId', it.group)
                        dependencyNode.appendNode('artifactId', it.name)
                        dependencyNode.appendNode('version', it.version)
                        compileArtifacts.add("${it.group}:${it.name}")
                    }

                    configurations.compileOnly.allDependencies.findAll {
                        !compileArtifacts.contains("${it.group}:${it.name}")
                    }.each {
                        def dependencyNode = dependenciesNode.appendNode('dependency')
                        dependencyNode.appendNode('groupId', it.group)
                        dependencyNode.appendNode('artifactId', it.name)
                        dependencyNode.appendNode('version', it.version)
                        dependencyNode.appendNode('scope', 'provided')
                    }

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
                    publications = ['mavenAAR']
                    dryRun = project.version.endsWith("-SNAPSHOT")
                    publish = false
                    pkg {
                        repo = 'lock-android'
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
}
