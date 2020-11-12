package com.chw.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

public class CustomTask extends DefaultTask {

    @TaskAction
    void output() {
        println "extName is ${project.myExt.extName}"
        println "version is ${project.myExt.version}"
    }
}