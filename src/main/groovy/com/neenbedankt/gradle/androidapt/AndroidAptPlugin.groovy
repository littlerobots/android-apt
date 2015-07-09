package com.neenbedankt.gradle.androidapt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.bundling.AbstractArchiveTask

class AndroidAptPlugin implements Plugin<Project> {
    void apply(Project project) {
        def variants = null;
        if (project.plugins.findPlugin("com.android.application") || project.plugins.findPlugin("android")) {
            variants = "applicationVariants";
        } else if (project.plugins.findPlugin("com.android.library") || project.plugins.findPlugin("android-library")) {
            variants = "libraryVariants";
        } else {
            throw new ProjectConfigurationException("The android or android-library plugin must be applied to the project", null)
        }
        def aptConfiguration = project.configurations.create('apt').extendsFrom(project.configurations.compile, project.configurations.provided)
        def aptTestConfiguration = project.configurations.create('androidTestApt').extendsFrom(project.configurations.androidTestCompile, project.configurations.androidTestProvided)
        project.extensions.create("apt", AndroidAptExtension)
        project.afterEvaluate {
            if (project.apt.disableDiscovery() && !project.apt.processors()) {
                throw new ProjectConfigurationException('android-apt configuration error: disableDiscovery may only be enabled in the apt configuration when there\'s at least one processor configured', null);
            }
            project.android[variants].all { variant ->
                configureVariant(project, variant, aptConfiguration, project.apt)
                if (variant.testVariant) {
                    configureVariant(project, variant.testVariant, aptTestConfiguration, project.apt)
                }
            }
        }
    }

    static void configureVariant(
            def project,
            def variant, def aptConfiguration, def aptExtension) {
        if (aptConfiguration.empty) {
            project.logger.info("No apt dependencies for configuration ${aptConfiguration.name}");
            return;
        }

        def aptOutputDir = project.file(new File(project.buildDir, "generated/source/apt"))
        def aptOutput = new File(aptOutputDir, variant.dirName)

        def javaCompile = variant.javaCompile;

        variant.addJavaSourceFoldersToModel(aptOutput);
        def processorPath = aptConfiguration.getAsPath();

        def processors = aptExtension.processors()

        javaCompile.options.compilerArgs += [
                '-s', aptOutput
        ]

        if (processors) {
            javaCompile.options.compilerArgs += [
                    '-processor', processors
            ]
        }

        if (!(processors && aptExtension.disableDiscovery())) {
            javaCompile.options.compilerArgs += [
                    '-processorpath', processorPath
            ]
        }

        aptExtension.aptArguments.variant = variant
        aptExtension.aptArguments.project = project
        aptExtension.aptArguments.android = project.android

        def projectDependencies = aptConfiguration.allDependencies.withType(ProjectDependency.class)
        // There must be a better way, but for now grab the tasks that produce some kind of archive and make sure those
        // run before this javaCompile. Packaging makes sure that processor meta data is on the classpath
        projectDependencies.each { p ->
            def archiveTasks = p.dependencyProject.tasks.withType(AbstractArchiveTask.class)
            archiveTasks.each { t -> variant.javaCompile.dependsOn t.path }
        }

        javaCompile.options.compilerArgs += aptExtension.arguments()

        javaCompile.doFirst {
            aptOutput.mkdirs()
        }
    }
}
