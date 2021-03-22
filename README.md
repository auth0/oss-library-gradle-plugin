# oss-library-gradle-plugin

## Instructions to run the plugin locally

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
│   │   ├── oss-library-0.12.0-groovydoc.jar
│   │   ├── oss-library-0.12.0-javadoc.jar
│   │   ├── oss-library-0.12.0-sources.jar
│   │   └── oss-library-0.12.0.jar
```

Open the project you want to use this plugin on. From the project-wide `build.gradle` file, edit the builscript dependencies to reference the jar file:

```groovy
buildscript {
    //.... repositories, and so on
    dependencies {
        classpath files('./../oss-library-gradle-plugin/build/libs/oss-library-0.12.0.jar')
        classpath 'com.android.tools.build:gradle:3.4.2'
        classpath 'com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.4'
    }
}
```

Now in order to apply it, go to the app's `build.gradle` file and add the fully qualified package + class name. Note there are no quotes around it. In the case of an Android app, it will look like this:

```groovy
apply plugin: 'com.auth0.gradle.oss.AndroidLibraryPlugin'

//...
android {
    compileSdkVersion 28

    defaultConfig {
        minSdkVersion 15
        targetSdkVersion 28
    }
}
```

For java libraries, the Plugin name will be different:

```groovy
apply plugin: 'com.auth0.gradle.oss.LibraryPlugin'
```

Go ahead and sync the project with the gradle files. If everything went right, your project is now using the most recent local version of the plugin. 

> If you're developing, make sure whenever you update the code that the jar is also updated and not cached. If you don't do that, uou might see the behavior of an _old version_ of the plugin.