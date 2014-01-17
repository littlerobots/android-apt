package com.neenbedankt.gradle.androidapt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException

class AndroidAptPlugin implements Plugin<Project> {
    void apply(Project project) {
        def variants = null;
        if (project.plugins.findPlugin("android")) {
            variants = "applicationVariants";
        } else if (project.plugins.findPlugin("android-library")) {
            variants = "libraryVariants";
        } else {
            throw new ProjectConfigurationException("The android or android-library plugin must be applied to the project", null)
        }
        project.configurations.create('apt').extendsFrom(project.configurations.compile)
        project.extensions.create("apt", AndroidAptExtension)
        project.afterEvaluate {
            project.android[variants].all { variant ->
                def aptOutputDir = project.file(new File(project.buildDir, "source/apt"))
                def aptOutput = new File(aptOutputDir, variant.dirName)

                variant.addJavaSourceFoldersToModel(aptOutput);

                variant.javaCompile.options.compilerArgs += [
                        '-processorpath', project.configurations.apt.getAsPath(),
                        '-s', aptOutput
                ]

                project.apt.aptArguments.variant = variant
                project.apt.aptArguments.project = project
                project.apt.aptArguments.android = project.android

                variant.javaCompile.options.compilerArgs+=project.apt.arguments()

                variant.javaCompile.doFirst {
                    aptOutput.mkdirs()
                }
            }
        }
    }
}
