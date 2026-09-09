plugins {
    id "com.diffplug.spotless"
}

subprojects {
    apply {
        plugin("com.diffplug.spotless")
    }
}

spotless {
    java {
        palantirJavaFormat()
        removeUnusedImports()
        importOrder()           // imports are ordered
        leadingTabsToSpaces(4)
        trimTrailingWhitespace()
        toggleOffOn()           // enable toggling code formatting with @formatter
    }
    format 'misc', {
        // define the files to apply `misc` to
        target '*.gradle', '*.md', '.gitignore'

        // define the steps to apply to those files
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }
}

// With this dependsOn, spotlessApply is auto-applied when the 'build' or 'check' task runs
build.dependsOn 'spotlessApply'
check.dependsOn 'spotlessApply'