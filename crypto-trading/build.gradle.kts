import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.protoc

plugins {
    java
    idea
    application
    id("com.palantir.docker") version "0.26.0"
    id("com.google.protobuf") version "0.8.16"
    id("io.freefair.lombok") version "6.0.0-m2"
}

dependencies {
    implementation("com.akkaserverless:akkaserverless-java-sdk:0.7.0-beta.10")
    implementation("com.akkaserverless:akkaserverless-java-sdk-testkit:0.7.0-beta.10")
    implementation("io.cloudstate:cloudstate-java-support:0.6.0")
    implementation("com.google.api.grpc:proto-google-common-protos:2.3.2")
    implementation("ch.qos.logback:logback-classic:1.2.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
    testImplementation("org.assertj:assertj-core:3.20.2")
}

repositories {
    jcenter()
    mavenCentral()
    mavenLocal()
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.17.3"
    }

    generateProtoTasks {
        all().forEach { task ->
            task.generateDescriptorSet = true
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {

    test {
        useJUnitPlatform()
    }

    docker {
      
        dependsOn(clean.get(), distTar.get())

        setDockerfile(rootProject.file("Dockerfile"))

        name = "cscandura/${project.name}:0.1"

        files(distTar.get().outputs)

        buildArgs(mapOf(
                Pair("RUNNABLE_NAME", project.name)
        ))
    }


}

application {
    mainClass.set("com.akkasls.hackathon.CryptoTradingServiceRunner")
}


