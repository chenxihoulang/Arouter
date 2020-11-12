package com.chw.plugin;

import org.gradle.api.Project
import org.gradle.api.Plugin

/**
 * @author ChaiHongwei* @date 2020-11-12 16:29
 */
public class ApkChangeNamePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        //判断project中是否有android配置

        Plugin plugin = project.plugins.findPlugin("com.android.application")

        println("plugin:" + plugin.properties)

        println("project:" + project.properties)

        if (!project.android) {
            throw new IllegalStateException("Must apply com.android.application or com.android.library first!");
        }

        project.android.applicationVariants.all {
            variant ->
                variant.outputs.all {
                    outputFileName = "${variant.name}-${variant.versionName}-" + System.currentTimeMillis() + ".apk"
                }
        }

        project.task('customTask', type: CustomTask)
        def extension = project.extensions.create('myExt', MyExtension)
        project.beforeEvaluate {
            println("project.beforeEvaluate:" + project.getName())
        }
        project.afterEvaluate {
            println("Hello from " + extension.toString())
        }
    }
}

