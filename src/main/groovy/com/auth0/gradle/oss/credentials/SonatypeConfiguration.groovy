package com.auth0.gradle.oss.credentials

import org.gradle.api.GradleException
import org.gradle.api.Project

class SonatypeConfiguration {
    Project project

    SonatypeConfiguration(Project project) {
        this.project = project
    }

    def assertSonatypeCredentials() {
        def sonatypeConfigured = project.hasProperty('ossrhUsername') && project.hasProperty('ossrhPassword')
        if (!sonatypeConfigured) {
            throw new GradleException("Missing Sonatype credentials. Please, provide 'ossrhUsername' and 'ossrhPassword' properties. Read more at https://github.com/auth0/oss-library-gradle-plugin.")
        }
    }

    def assertSigningConfiguration() {
        def signingConfigured = project.hasProperty('signing.keyId') && project.hasProperty('signing.password') && project.hasProperty('signing.secretKeyRingFile')
        if (!signingConfigured) {
            throw new GradleException("Missing Signing plugin configuration. Please, provide 'signing.keyId', 'signing.password', and 'signing.secretKeyRingFile' properties. Read more at https://docs.gradle.org/current/userguide/signing_plugin.html#sec:signatory_credentials.")
        }
    }
}
