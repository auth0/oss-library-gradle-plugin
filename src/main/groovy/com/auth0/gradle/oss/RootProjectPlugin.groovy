package com.auth0.gradle.oss

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.wrapper.Wrapper

@CompileStatic
class RootProjectPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        if (project != project.rootProject) {
            throw new IllegalStateException()
        }

        project.pluginManager.apply('lifecycle-base')
        project.afterEvaluate {
            project.tasks.named("clean", Delete) {
                it.delete 'CHANGELOG.md.release'
            }
            project.tasks.named("wrapper", Wrapper) {
                it.setDistributionType(Wrapper.DistributionType.ALL)
            }
        }
    }
}
