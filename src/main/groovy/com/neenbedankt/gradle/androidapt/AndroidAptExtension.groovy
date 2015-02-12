package com.neenbedankt.gradle.androidapt

class AndroidAptExtension {
    final def aptArguments = new AptArguments()
    private def argsClosure;
    private def processors = [];

    private disableDiscovery = false

    def arguments(Closure closure) {
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = aptArguments;
        argsClosure = closure;
    }

    def processor(className) {
        processors << className
    }

    def processors() {
        return processors.join(',')
    }

    def disableDiscovery(boolean disable) {
        disableDiscovery = disable
    }

    def disableDiscovery() {
        return disableDiscovery;
    }

    def arguments() {
        aptArguments.arguments.clear();
        if (argsClosure) {
            argsClosure()
        }
        return aptArguments.arguments
    }
}
