package com.auth0.gradle.oss.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import java.text.SimpleDateFormat

class ChangeLogTask extends DefaultTask {

    @Input
    def current
    @Input
    def next

    @TaskAction
    void update() {
        def repository = project.oss.repository
        def file = new File('CHANGELOG.md')
        def output = new File('CHANGELOG.md.release')
        output.newWriter().withWriter { writer ->

            file.eachLine { line, number ->
                if (number == 0 && !line.startsWith('# Change Log')) {
                    throw new GradleException('Change Log file is not properly formatted')
                }

                writer.println(line)

                if (number == 0 || number > 1) {
                    return
                }

                def formatter = new SimpleDateFormat('yyyy-MM-dd')
                writer.println()
                writer.println("## [${next}](https://github.com/auth0/${repository}/tree/${next}) (${formatter.format(new Date())})")
                writer.println("[Full Changelog](https://github.com/auth0/${repository}/compare/${current}...${next})")
                def command = ["curl", "https://webtask.it.auth0.com/api/run/wt-hernan-auth0_com-0/oss-changelog.js?webtask_no_cache=1&repo=${repository}&milestone=${next}", "-f", "-s", "-H", "Accept: text/markdown"]
                def content = command.execute()
                content.consumeProcessOutputStream(writer)
                if (content.waitFor() != 0) {
                    throw new GradleException("Failed to request changelog for version ${next}")
                }
            }
        }
        file.delete()
        output.renameTo('CHANGELOG.md')
    }
}