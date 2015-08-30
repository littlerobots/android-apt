package com.neenbedankt.gradle.androidapt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.tasks.compile.GroovyCompile

class AndroidAptPlugin implements Plugin<Project> {
    void apply(Project project) {
        def variants = null;
        if (project.plugins.findPlugin("com.android.application") || project.plugins.findPlugin("android") ||
                project.plugins.findPlugin("com.android.test")) {
            variants = "applicationVariants";
        } else if (project.plugins.findPlugin("com.android.library") || project.plugins.findPlugin("android-library")) {
            variants = "libraryVariants";
        } else {
            throw new ProjectConfigurationException("The android or android-library plugin must be applied to the project", null)
        }
        def aptConfiguration = project.configurations.create('apt').extendsFrom(project.configurations.compile, project.configurations.provided)
        def aptTestConfiguration = null;
        try {
            aptTestConfiguration = project.configurations.create('androidTestApt').extendsFrom(project.configurations.getByName('androidTestCompile'), project.configurations.getByName('androidTestProvided'))
        } catch (UnknownConfigurationException ex) {
            // this can be missing in the case of the com.android.test plugin
        }
        def aptUnitTestConfiguration
        try {
            aptUnitTestConfiguration = project.configurations.create('testApt').extendsFrom(project.configurations.getByName('testCompile'), project.configurations.getByName('testProvided'))
        } catch (UnknownConfigurationException ex) {
            // missing on older plugin version
        }
        project.extensions.create("apt", AndroidAptExtension)
        project.afterEvaluate {
            if (project.apt.disableDiscovery() && !project.apt.processors()) {
                throw new ProjectConfigurationException('android-apt configuration error: disableDiscovery may only be enabled in the apt configuration when there\'s at least one processor configured', null);
            }
            project.android[variants].all { variant ->
                configureVariant(project, variant, aptConfiguration, project.apt)
                if (variant.testVariant && aptTestConfiguration) {
                    configureVariant(project, variant.testVariant, aptTestConfiguration, project.apt)
                }
                if (variant.hasProperty("unitTestVariant") && aptUnitTestConfiguration) {
                    configureVariant(project, variant.unitTestVariant, aptUnitTestConfiguration, project.apt)
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

        def javaCompile = variant.hasProperty('javaCompiler') ? variant.javaCompiler : variant.javaCompile

        variant.addJavaSourceFoldersToModel(aptOutput);
        def processorPath = (aptConfiguration + javaCompile.classpath).asPath
        def taskDependency = aptConfiguration.buildDependencies
        if (taskDependency) {
            javaCompile.dependsOn += taskDependency
        }

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

        javaCompile.options.compilerArgs += aptExtension.arguments()

        javaCompile.doFirst {
            aptOutput.mkdirs()
        }

        // Groovy compilation is added by the groovy-android-gradle-plugin in finalizedBy
        def dependency = javaCompile.finalizedBy;
        def dependencies = dependency.getDependencies(javaCompile);
        for (def dep : dependencies) {
            if (dep instanceof GroovyCompile) {
                if (dep.groovyOptions.hasProperty("javaAnnotationProcessing")) {
                    dep.options.compilerArgs += javaCompile.options.compilerArgs;
                    dep.groovyOptions.javaAnnotationProcessing = true
                }
            }
        }
    }
}
