import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.6.10"
  application
  id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "net.metacheck"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
  maven {
    setUrl("https://jitpack.io")
  }
}

val vertxVersion = "4.2.7"
val junitJupiterVersion = "5.7.0"

val mainVerticleName = "net.metacheck.website_parser.ScrapeVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
  mainClass.set(launcherClassName)
}


dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-lang-kotlin")
  implementation(kotlin("stdlib-jdk8"))
  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
  implementation("org.jsoup:jsoup:1.14.3")
  implementation("io.vertx:vertx-web:4.2.7")
  implementation("com.google.code.gson:gson:2.9.0")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.8.9")
  implementation ("com.github.metacheck:essence_extended:0.14.0")
  implementation("io.vertx:vertx-lang-kotlin-coroutines:4.2.7")
  implementation("com.google.firebase:firebase-admin:8.1.0")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "11"

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles()
}

//tasks.withType<Test> {
//  useJUnitPlatform()
//  testLogging {
//    events = setOf(PASSED, SKIPPED, FAILED)
//  }
//}

tasks.withType<JavaExec> {
  args = listOf(
    "run",
    mainVerticleName,
    "--redeploy=$watchForChange",
    "--launcher-class=$launcherClassName",
    "--on-redeploy=$doOnChange"
  )
}

