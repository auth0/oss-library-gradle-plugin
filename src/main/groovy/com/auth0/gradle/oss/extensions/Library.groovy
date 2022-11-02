package com.auth0.gradle.oss.extensions

class Library {
    String name
    String organization
    String repository
    String description
    String baselineCompareVersion
    Boolean skipAssertSigningConfiguration
    Boolean enableJava8Testing
    Boolean enableJava11Testing
    Boolean enableJava17Testing
}
