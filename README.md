# oss-library-gradle-plugin

## Configuring the plugin

### Adding the dependency
Find the latest version of the plugin in the "Gradle Plugins" repository.
- Java: https://plugins.gradle.org/plugin/com.auth0.gradle.oss-library.java
- Android: https://plugins.gradle.org/plugin/com.auth0.gradle.oss-library.android


Apply the plugin to your project. For this, you can use the new plugins DSL.

```groovy
plugins {
  id "com.auth0.gradle.oss-library.java" version "x.y.z"
}
```

Alternatively, you can use the legacy plugins DSL. 

```groovy
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "com.auth0.gradle:oss-library:x.y.z"
  }
}

apply plugin: "com.auth0.gradle.oss-library.java"
```

> Note that the plugin name changes depending on whether you target a Java project or a Android project.


### The OSS extension

The plugin takes in an `oss` extension that must contain all the following project details. These are used when the POM file is generated, before the package can be published to a Maven repository. The `group` property must be defined with the name of the package group (as it will appear in Maven Central).

```groovy
logger.lifecycle("Using version ${version} for ${name}")
group = 'com.auth0.android'

oss {
    name 'Auth0-Java'
    repository 'Auth0-Java'
    organization 'auth0'
    description 'Java toolkit for Auth0 API'

    developers {
        auth0 {
            displayName = 'Auth0'
            email = 'oss@auth0.com'
        }
    }
}
```  

### Tasks included in the plugin

The plugin exposes a few tasks for you to quickly prepare a release and upload it to Maven Central (Sonatype).

Tasks:
- "releaseMajor": Creates a changelog file, bumps the version to the next major, and creates a git tag.
- "releaseMinor": Creates a changelog file, bumps the version to the next minor, and creates a git tag.
- "releasePatch": Creates a changelog file, bumps the version to the next patch, and creates a git tag.
- "releaseBeta": Creates a changelog file, bumps the version to the next beta, and creates a git tag.
- "publish": Publishes all the publications available to Maven Central (Sonatype). 
- "publishAndroidLibraryPublicationToMavenRepository": Publishes only the Android library publication to Maven Central.
- "publishMavenJavaPublicationToMavenRepository": Publishes only the Java library publication to Maven Central.


### Preparing a release

When a new release is about to be generated, the plugin will try to generate a new Changelog entry with the included changes. It will do that fetching from the target Github repository (guessed using the "organization" and "repository" properties) a Github Milestone named after the desired release version. The current version is obtained running `git describe --tags --abbrev=0`, which is known to fail when there is no previous git tag or when PRs are merged with an additional merge/squash commit.  


See the example below:

```
Current version: 1.4.2

Desired version:
   Major -> 2.0.0
   Minor -> 1.5.0
   Patch -> 1.4.3
   Beta -> 1.4.2-beta.0 
```

The Github Milestone must exist and contain all the Issues and Pull Requests associated with the changes introduced in this version. Each entry must have a changelog Github Label from the following list:
- CH: Added
- CH: Breaking Change
- CH: Changed
- CH: Deprecated
- CH: Fixed
- CH: Removed
- CH: Security

> Multiple Labels on a given entry would cause duplicated Changelog entries.

Pick and run a "release" task. If you followed the steps above, a Changelog file will be generated, a new commit created, and a git tag created with the name of the next release. You are ready to publish it.


### Publishing a release 

In order to be able to upload a release, your local git HEAD should point to an existing git tag. You can quickly do this running `git checkout {release version}`, using the version that you generated on the section above.

Now, your environment must be set up with the GPG signing information and Sonatype credentials, otherwise the release wouldn't be signed and wouldn't be uploaded. You can put the properties in the global `gradle.properties` file or pass them directly to the command through the CLI (e.g. `gradle build -P{name}={value}`).

```groovy
# Signing Plugin / GPG
signing.keyId = last_8_symbols_of_the_private_key_id
signing.password = private_key_password
signing.secretKeyRingFile = /path/to/private_key/file

# Sonatype / Maven Central
ossrhUsername = your_sonatype_username
ossrhPassword = your_sonatype_password
```

> When running once of the "publish" tasks and if any of the values above are not provided, a warning will be logged in the console and the operation will fail.

Now you can run "publish" or any variant and the files will be uploaded to Sonatype. Make sure to "assemble" the project first. 

```
gradle clean assemble publish
```


Read how to continue on the Sonatype dashboard in this [article](https://central.sonatype.org/pages/ossrh-guide.html).

## Developing and running locally

First, generate the plugin's `jar`:

```sh
./gradlew clean assemble publishPluginJar
```

This will place the jar in the `build/libs/` folder

```
.
├── README.md
├── build
│   ├── libs
│   │   ├── oss-library-x.y.z-groovydoc.jar
│   │   ├── oss-library-x.y.z-javadoc.jar
│   │   ├── oss-library-x.y.z-sources.jar
│   │   └── oss-library-x.y.z.jar
```

Open the project you want to use this plugin on. From the project-wide `build.gradle` file, edit the builscript dependencies to reference the jar file:

```groovy
buildscript {
    //.... repositories, and so on
    dependencies {
        classpath files('./../oss-library-gradle-plugin/build/libs/oss-library-x.y.z.jar')
        classpath 'com.android.tools.build:gradle:4.1.3'
    }
}
```

Now in order to apply it, go to the app's `build.gradle` file and add the fully qualified package + class name. Note there are no quotes around it. In the case of an Android app, it will look like this:

```groovy
// old plugin syntax
apply plugin: 'com.auth0.gradle.oss.AndroidLibraryPlugin'

// new plugin syntax
plugins {
    id "com.auth0.gradle.oss-library.android"
}
```

For java libraries, the Plugin name will be different:

```groovy
apply plugin: 'com.auth0.gradle.oss.LibraryPlugin'
```

Go ahead and sync the project with the gradle files. If everything went right, your project is now using the most recent local version of the plugin. 

> If you're developing, make sure whenever you update the code that the jar is also updated and not cached. If you don't do that, you might see the behavior of an _old version_ of the plugin.