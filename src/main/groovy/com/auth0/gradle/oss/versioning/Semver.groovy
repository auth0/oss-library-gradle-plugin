package com.auth0.gradle.oss.versioning

import org.gradle.api.logging.Logging

import javax.print.attribute.standard.Severity

class Semver {
    String version
    def snapshot

    def getStringVersion() {
        return snapshot ?  "$version-SNAPSHOT" : version
    }

    def nextPatch() {
        def parts = version.split("\\.")
        def patch = Integer.parseInt(parts[2]) + 1
        return "${parts[0]}.${parts[1]}.${patch}"
    }

    def nextMinor() {
        def parts = version.split("\\.")
        def minor = Integer.parseInt(parts[1]) + 1
        return "${parts[0]}.${minor}.0"
    }

    static Semver current() {
        def current = describeGit(false)
        def snapshot = current == null
        if (snapshot) {
            current = describeGit(snapshot, '0.0.1')
        }
        return new Semver(snapshot: snapshot, version: current)
    }

    static describeGit(boolean snapshot, String defaultValue = null) {
        def command = ['git', 'describe', '--tags', (snapshot ? '--abbrev=0' : '--exact-match')].execute()
        def stdout = new ByteArrayOutputStream()
        def errout = new ByteArrayOutputStream()
        command.consumeProcessOutput(stdout, errout)
        if (command.waitFor() != 0) {
            Logging.getLogger(Semver.class).debug(errout.toString())
            return defaultValue
        }
        if (stdout.toByteArray().length > 0) {
            return stdout.toString().replace('\n', "")
        }

        return defaultValue
    }

}