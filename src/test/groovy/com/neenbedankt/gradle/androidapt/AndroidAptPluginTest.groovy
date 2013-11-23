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
}
