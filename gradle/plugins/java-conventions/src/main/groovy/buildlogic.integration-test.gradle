plugins {
    id "java-library"
    id "jvm-test-suite"
}

// --------------------------------------------------------------------------------
// Define configurations for integrationTest and performanceTest
// --------------------------------------------------------------------------------
testing {
    suites {
        test {
            useJUnitJupiter()
        }
        /*
         * Define the integration test suite
         */
        integrationTest(JvmTestSuite) {
            // This is not supported in Gradle 8.13 and newer, but is needed in older versions
            // testType.set(TestSuiteType.INTEGRATION_TEST)
            dependencies {
                implementation project()
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
            configurations {
                integrationTestImplementation {
                    extendsFrom testImplementation
                }
            }
        }
    }
}

/*
 * The 'check' task will trigger integration tests and generate the coverage report.
 */
tasks.named('check') {
  dependsOn(testing.suites.integrationTest)
  dependsOn tasks.matching { it.name == 'integrationTestCodeCoverageReport' }
}
