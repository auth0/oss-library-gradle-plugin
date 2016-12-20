package com.auth0.gradle.oss.versioning

import org.gradle.api.GradleException
import org.gradle.api.logging.Logging

class Semver {
    @Lazy String version = {
        def stable = "$major.$minor.$patch"
        if (snapshot) {
            return "$stable-SNAPSHOT"
        }
        if (prerelease != null) {
            stable += "-$prerelease.$prereleaseCount"
        }
        stable
    } ()
    @Lazy String nonSnapshot = {
        def stable = "$major.$minor.$patch"
        if (prerelease != null) {
            stable += "-$prerelease.$prereleaseCount"
        }
        stable
    } ()
    boolean snapshot
    String major
    String minor
    String patch
    String prerelease
    String prereleaseCount

    Semver(String version, boolean snapshot) {
        this.snapshot = snapshot
        def matcher = version =~ /(\d+)\.(\d+)\.(\d+)(?:-(\w+)\.(\d+))?/
        if (!matcher.matches()) {
            throw new GradleException("Version $version is not valid")
        }
        this.major = matcher.group(1)
        this.minor = matcher.group(2)
        this.patch = matcher.group(3)
        this.prerelease = matcher.group(4)
        this.prereleaseCount = matcher.group(5)
    }

    String nextPatch(String prerelease = null) {
        def patch = Integer.parseInt(this.patch) + 1
        def stable = "$major.$minor.$patch"
        if (prerelease != null) {
            stable += "-$prerelease.1"
        }
        stable
    }

    String nextMinor(String prerelease = null) {
        def minor = Integer.parseInt(this.minor) + 1
        def stable = "$major.$minor.0"
        if (prerelease != null) {
            stable += "-$prerelease.1"
        }
        stable
    }

    String nextMajor(String prerelease = null) {
        def major = Integer.parseInt(this.major) + 1
        def stable = "$major.0.0"
        if (prerelease != null) {
            stable += "-$prerelease.1"
        }
        stable
    }

    boolean isStable() {
        return this.prerelease == null
    }

    String nextPrerelease() {
        if (isStable()) {
            throw new GradleException("Version $version is stable")
        }
        def count = Integer.parseInt(this.prereleaseCount) + 1
        return "$major.$minor.$patch-$prerelease.$count"
    }


    @Override
    String toString() {
        return version
    }

    static Semver current() {
        def current = describeGit(false)
        def snapshot = current == null
        if (snapshot) {
            current = describeGit(snapshot, '0.0.1')
        }
        return new Semver(current, snapshot)
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