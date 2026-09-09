plugins {
    id "org.sonarqube"
    id "jacoco"
}

/*
 * Besides the Sonar configuration found here, there is also some
 * Sonar configuration in the `subproject-configurations.gradle` file
 * found in this same folder. In particular, the path to the jacoco-generated
 * coverage files is defined in the subproject-configurations.gradle file.
 */

def ignoredClasses =
    "**/Exception.java," +
    "**/*Test*.java," +
    "**/*IT.java," +
    "**/ResourceIdTrait.java," +
    "**/ResourceIdentity.java," +
    "**/*Application.java," +
    "**/*TablePopulator.java," +
    "**/*Config.java," +
    "**/*Configuration.java," +
    "**/*Initializer.java," +
    "**/*Exception.java"

sonarqube {
    properties {
        property("sonar.coverage.exclusions", ignoredClasses)
    }
}

/*
 * Add these dependencies so the Sonar task invokes `jacocoTestReport` for us.
 * We could have Sonar depend on the `check` task but, as it turns out,
 * that causes integration tests to run _every_ time the `sonar` task runs
 */
tasks.named('sonar') {
    dependsOn tasks.named('jacocoTestReport')
}
