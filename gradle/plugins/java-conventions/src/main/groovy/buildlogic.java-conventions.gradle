plugins {
    id "java-library"
    id "java-test-fixtures"
    id "buildlogic.testing"
    id "buildlogic.sonar-jacoco"
    id "buildlogic.jacoco"
    id "buildlogic.spotless"
    id "buildlogic.lint"
}

// --------------------------------------------------------------------------------
// Enable compiling with a specific Java version,
// independent of the developer's current Java version.
// The java version can be pinned by setting the version in the ".java-version"
// file found in the root project. Fall back to version 17 if anything goes wrong.
// --------------------------------------------------------------------------------
java {
    def javaVersion = 21
    try {
        javaVersion = rootProject.file('.java-version').text.trim()
    }
    catch (Exception e) {
        javaVersion = 21
    }
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion as String)
    }
    sourceCompatibility = JavaLanguageVersion.of(javaVersion as String)
    targetCompatibility = JavaLanguageVersion.of(javaVersion as String)
}

/**
 * Example of setting lint arguments
 */
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ["-Xlint:unchecked"]
}

// Share sources folder with other projects for aggregated JavaDoc and JaCoCo reports
configurations.create("transitiveSourcesElements") {
    visible = false
    canBeResolved = false
    canBeConsumed = true
    extendsFrom(configurations.implementation)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, "source-folders"))
    }
    sourceSets.main.java.srcDirs.forEach { outgoing.artifact(it) }
}
