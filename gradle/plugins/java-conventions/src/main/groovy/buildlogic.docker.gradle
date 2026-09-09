plugins {
    id "com.google.cloud.tools.jib"
}
// If you want successful builds to automatically create a docker image, uncomment the next line
// tasks.build.dependsOn tasks.jibDockerBuild

// Jib resolves the Docker image classpath from runtimeClasspath.
// Spring Boot's Gradle plugin adds developmentOnly to runtimeClasspath so that bootRun
// picks it up, but bootJar excludes it. Jib has no such exclusion, so devtools (and any
// other developmentOnly dep) end up in the image unless we remove it here.
// We restore it for bootRun so local hot-reload still works.

afterEvaluate {
  def devOnly = configurations.findByName('developmentOnly')
  if (devOnly) {
    configurations.runtimeClasspath.setExtendsFrom(
      configurations.runtimeClasspath.extendsFrom.findAll { it != devOnly }
      )
   def bootRunTask = tasks.findByName('bootRun')
   if (bootRunTask) {
     bootRunTask.classpath += devOnly
   }
  }
}