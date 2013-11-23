package com.neenbedankt.gradle.androidapt

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.ProjectConfigurationException

class AndroidAptPlugin implements Plugin<Project> {
    void apply(Project project) {
        //TODO does it make sense to apply this to a library project?
        if(!project.plugins.findPlugin("android")) {
            throw new ProjectConfigurationException("Android plugin must be applied to the project")
        }
        project.configurations.create('apt').extendsFrom(project.configurations.compile)

        project.afterEvaluate {
            project.android.applicationVariants.all { variant ->
                def aptOutputDir = project.file(new File(project.buildDir, "source/apt"))
                def aptOutput = new File(aptOutputDir, variant.dirName)
                def sourceSet = new File(variant.dirName).getName()

                project.android.sourceSets[sourceSet].java.srcDirs+= aptOutput.getPath()

                variant.javaCompile.options.compilerArgs += [
                        '-processorpath', project.configurations.apt.getAsPath(),
                        '-s', aptOutput
                ]
                variant.javaCompile.source = variant.javaCompile.source.filter { p ->
                    return !p.getPath().startsWith(aptOutputDir.getPath())
                }

                variant.javaCompile.doFirst {
                    aptOutput.mkdirs()
                }
            }
        }
    }
}
