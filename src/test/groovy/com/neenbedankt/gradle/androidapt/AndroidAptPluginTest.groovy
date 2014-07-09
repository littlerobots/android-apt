package com.neenbedankt.gradle.androidapt

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class AndroidAptPluginTest {

    @Test
    public void testRequireAndroidPlugin() {
        Project project = ProjectBuilder.builder().build()
        try {
            project.apply plugin: 'android-apt'
            fail();
        } catch (expected) {
        }
    }

    @Test
    public void testProjectAptDependency() {
        Project root = ProjectBuilder.builder().build();
        Project testProject = ProjectBuilder.builder().withName(":test").withParent(root).build();
        testProject.apply plugin: 'java'
        Project p = ProjectBuilder.builder().withParent(root).build()
        p.apply plugin: 'android'
        p.apply plugin: 'android-apt'
        p.dependencies {
            apt testProject
        }
        p.android {
            compileSdkVersion 19
            buildToolsVersion "19.1"

            defaultConfig {
                minSdkVersion 14
                targetSdkVersion 19
                versionCode 1
                versionName "1.0"
            }
        }
        p.evaluate()
        // FIXME assert that the test:jar task is added as a dependency
//        p.android.applicationVariants.all { v ->
//            dependencies = v.javaCompile.taskDependencies.getDependencies(v.javaCompile)
//        }
    }

    @Test
    public void testProjectandroidTestAptDependency() {
        Project root = ProjectBuilder.builder().build();
        Project testProject = ProjectBuilder.builder().withName(":test").withParent(root).build();
        testProject.apply plugin: 'java'
        Project p = ProjectBuilder.builder().withParent(root).build()
        p.apply plugin: 'android'
        p.apply plugin: 'android-apt'
        p.repositories {
            mavenCentral()
        }
        p.dependencies {
            androidTestApt testProject
        }
        p.android {
            compileSdkVersion 19
            buildToolsVersion "19.1"

            defaultConfig {
                minSdkVersion 14
                targetSdkVersion 19
                versionCode 1
                versionName "1.0"
            }
        }
        p.evaluate()
        println "Variants"
        println p.configurations
        p.android.applicationVariants.all { v ->
            if (v.testVariant) {
                assert !v.testVariant.javaCompile.options.compilerArgs.empty
            }
        }
        println "Variants"
    }
}
