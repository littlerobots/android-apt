package com.neenbedankt.gradle.androidapt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile

class AndroidAptPlugin implements Plugin<Project> {
    void apply(Project project) {
        def variants = null;
        if (project.plugins.findPlugin("com.android.application") || project.plugins.findPlugin("android") || project.plugins.findPlugin("dexguard")) {
            variants = "applicationVariants";
        } else if (project.plugins.findPlugin("com.android.library") || project.plugins.findPlugin("android-library") || project.plugins.findPlugin("dexguard-library")) {
            variants = "libraryVariants";
        } else {
            throw new ProjectConfigurationException("The android, dexguard, android-library or dexguard-library plugin must be applied to the project", null)
        }
        def aptConfiguration = project.configurations.create('apt').extendsFrom(project.configurations.compile)
        def aptTestConfiguration = project.configurations.create('androidTestApt').extendsFrom(project.configurations.androidTestCompile)

        // depending on the plugins used, there might be a testCompile configuration. If it exists that configuration is used
        // for the testApt configuration, otherwise fallback on androidTestCompile
        try {
            project.configurations.getByName('testCompile')
            aptTestConfiguration = project.configurations.create('testApt').extendsFrom(project.configurations.testCompile)
            project.logger.debug("Using testCompile to extend testApt from")
        }
        catch (UnknownConfigurationException ex) {
            aptTestConfiguration = project.configurations.create('testApt').extendsFrom(project.configurations.androidTestCompile)
            project.logger.debug("Using androidTestCompile to extend testApt from")
        }
        project.extensions.create("apt", AndroidAptExtension)
        project.afterEvaluate {
            if (project.apt.disableDiscovery() && !project.apt.processors()) {
                throw new ProjectConfigurationException('android-apt configuration error: disableDiscovery may only be enabled in the apt configuration when there\'s at least one processor configured', null);
            }
            project.android[variants].all { variant ->
                configureVariant(project, variant, aptConfiguration, project.apt)
                configureUnitTestVariant(project, variant, aptTestConfiguration, project.apt);
                if (variant.testVariant) {
                    configureVariant(project, variant.testVariant, aptTestConfiguration, project.apt)
                }
            }
        }
    }

    static void configureUnitTestVariant(def project, def variant, def aptConfiguration, def aptExtension) {
        def javaCompile = variant.javaCompile;
        // find JavaCompile tasks that depend on the variant task, excluding the variant.testVariant.javaCompile since that's
        // already explicitly configured. Then assume these JavaCompile tasks to be unit test-like tasks and configure those
        // to run the processors specified by the testApt configuration
        project.gradle.taskGraph.whenReady {
            project.tasks.withType(JavaCompile.class).each { JavaCompile compileTask ->
                if (compileTask != variant?.testVariant?.javaCompile && compileTask.taskDependencies.getDependencies(compileTask).contains(javaCompile)) {
                    project.logger.info("Configure additional compile task: ${compileTask.name}");
                    configureVariant(project, variant, aptConfiguration, aptExtension, compileTask);
                }
            }
        }
    }

    static void configureVariant(
            def project,
            def variant, def aptConfiguration, def aptExtension, def javaCompile = null) {
        if (aptConfiguration.empty) {
            project.logger.info("No apt dependencies for configuration ${aptConfiguration.name}");
            return;
        }

        def aptOutputDir = project.file(new File(project.buildDir, "generated/source/apt"))
        def aptOutput = new File(aptOutputDir, variant.dirName)

        javaCompile = javaCompile ? javaCompile : variant.javaCompile;

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
