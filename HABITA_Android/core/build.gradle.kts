plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.code.gson:gson:2.11.0")
}
tasks.test {
    dependsOn(rootProject.tasks.named("prepareBundledGtfs"))
    systemProperty("habita.gtfsPath", rootProject.layout.buildDirectory.file("generated/habitaAssets/gtfs/feed.zip").get().asFile.absolutePath)
    maxHeapSize = "1536m"
}
