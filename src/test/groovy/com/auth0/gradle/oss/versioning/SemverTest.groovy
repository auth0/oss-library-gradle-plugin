package com.auth0.gradle.oss.versioning

import spock.lang.Specification

class SemverTest extends Specification {

    def "should build string version"() {
        given:
        def actualVersion = new Semver(version, snapshot).version
        expect:
        actualVersion == "$expectedVersion"

        where:
        version | snapshot || expectedVersion
        "1.2.3" | false    || "1.2.3"
        "1.2.3" | true    || "1.2.3-SNAPSHOT"
        "1.2.3-alpha.1" | false    || "1.2.3-alpha.1"
        "1.2.3-alpha.1" | true    || "1.2.3-SNAPSHOT"
        "1.2.3-beta.1" | false    || "1.2.3-beta.1"
        "1.2.3-beta.1" | true    || "1.2.3-SNAPSHOT"
        "1.2.3-rc.1" | false    || "1.2.3-rc.1"
        "1.2.3-rc.1" | true    || "1.2.3-SNAPSHOT"
    }

    def "should provide next patch"() {
        given:
        def actualVersion = new Semver(version, true).nextPatch()
        expect:
        actualVersion == expectedVersion

        where:
        version || expectedVersion
        "1.2.3" || "1.2.4"
        "0.1.0" || "0.1.1"
        "0.0.1" || "0.0.2"
        "3.2.9" || "3.2.10"
        "3.2.9-beta.1" || "3.2.10"
        "3.2.9-alpha.1" || "3.2.10"
        "3.2.9-rc.1" || "3.2.10"
    }

    def "should provide next prerelease patch"() {
        given:
        def actualVersion = new Semver(version, true).nextPatch("beta")
        expect:
        actualVersion == expectedVersion

        where:
        version || expectedVersion
        "1.2.3" || "1.2.4-beta.1"
        "0.1.0" || "0.1.1-beta.1"
        "0.0.1" || "0.0.2-beta.1"
        "3.2.9" || "3.2.10-beta.1"
        "3.2.9-beta.1" || "3.2.10-beta.1"
        "3.2.9-alpha.1" || "3.2.10-beta.1"
        "3.2.9-rc.1" || "3.2.10-beta.1"
    }

    def "should provide next minor"() {
        given:
        def actualVersion = new Semver(version, true).nextMinor()
        expect:
        actualVersion == expectedVersion

        where:
        version || expectedVersion
        "1.2.3" || "1.3.0"
        "0.1.0" || "0.2.0"
        "0.0.1" || "0.1.0"
        "3.9.9" || "3.10.0"
        "3.2.9-beta.1" || "3.3.0"
        "3.2.9-alpha.1" || "3.3.0"
        "2.5.9-rc.1" || "2.6.0"
    }

    def "should provide next prerlease minor"() {
        given:
        def actualVersion = new Semver(version, true).nextMinor("rc")
        expect:
        actualVersion == expectedVersion

        where:
        version || expectedVersion
        "1.2.3" || "1.3.0-rc.1"
        "0.1.0" || "0.2.0-rc.1"
        "0.0.1" || "0.1.0-rc.1"
        "3.9.9" || "3.10.0-rc.1"
        "3.2.9-beta.1" || "3.3.0-rc.1"
        "3.2.9-alpha.1" || "3.3.0-rc.1"
        "2.5.9-rc.1" || "2.6.0-rc.1"
    }

    def "should provide next major"() {
        given:
        def actualVersion = new Semver(version, true).nextMajor()
        expect:
        actualVersion == expectedVersion

        where:
        version || expectedVersion
        "1.2.3" || "2.0.0"
        "0.1.0" || "1.0.0"
        "0.0.1" || "1.0.0"
        "9.2.1" || "10.0.0"
        "3.2.9-beta.1" || "4.0.0"
        "3.2.9-alpha.1" || "4.0.0"
        "2.5.9-rc.1" || "3.0.0"
    }

    def "should provide next prerelease major"() {
        given:
        def actualVersion = new Semver(version, true).nextMajor("alpha")
        expect:
        actualVersion == expectedVersion

        where:
        version || expectedVersion
        "1.2.3" || "2.0.0-alpha.1"
        "0.1.0" || "1.0.0-alpha.1"
        "0.0.1" || "1.0.0-alpha.1"
        "9.2.1" || "10.0.0-alpha.1"
        "3.2.9-beta.1" || "4.0.0-alpha.1"
        "3.2.9-alpha.1" || "4.0.0-alpha.1"
        "2.5.9-rc.1" || "3.0.0-alpha.1"
    }

    def "should provide next prerelease"() {
        given:
        def actualVersion = new Semver(version, true).nextPrerelease()
        expect:
        actualVersion == expectedVersion

        where:
        version || expectedVersion
        "3.2.9-beta.1" || "3.2.9-beta.2"
        "3.2.9-alpha.1" || "3.2.9-alpha.2"
        "2.5.9-rc.1" || "2.5.9-rc.2"
    }

    def "should tell if its prerelease"() {
        given:
        def actualVersion = new Semver(version, true)
        expect:
        actualVersion.isStable() == stable

        where:
        version || stable
        "1.2.3" || true
        "0.1.0" || true
        "0.0.1" || true
        "9.2.1" || true
        //noinspection GroovyPointlessBoolean
        "3.2.9-beta.1" || false
        //noinspection GroovyPointlessBoolean
        "3.2.9-alpha.1" || false
        //noinspection GroovyPointlessBoolean
        "2.5.9-rc.1" || false
    }

}
