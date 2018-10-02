package com.auth0.gradle.oss.credentials

import org.gradle.api.Project

class BintrayCredentials {
    String user
    String key
    String passphrasse

    BintrayCredentials(project) {
        this.user = value(project, 'BINTRAY_USER', 'bintray.user')
        this.key = value(project, 'BINTRAY_KEY', 'bintray.key')
        this.passphrasse = value(project, 'BINTRAY_PASSPHRASE', 'bintray.gpg.password')
    }

    def valid() {
        return this.user != null && this.key != null && this.passphrasse != null
    }

    private static def value(Project project, String env, String property) {
        def value = System.getenv(env)
        if (project.hasProperty(property)) {
            value = project.getProperty(property)
        }
        return value
    }
}
