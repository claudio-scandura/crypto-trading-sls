import com.google.protobuf.gradle.*


plugins {
    id("java")
    id("io.freefair.lombok")
    id("com.google.protobuf") version "0.8.16"
    id("com.palantir.docker")
}

dependencies {
    implementation("com.akkaserverless:akkaserverless-java-sdk:0.7.0-beta.10")
    implementation("com.akkaserverless:akkaserverless-java-sdk-testkit:0.7.0-beta.10")
    implementation("io.cloudstate:cloudstate-java-support:0.6.0")
    implementation("com.google.api.grpc:proto-google-common-protos:2.3.2")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("io.grpc:grpc-stub:1.38.1")
    implementation("io.grpc:grpc-protobuf:1.38.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
    testImplementation("org.assertj:assertj-core:3.20.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.17.3"
    }

    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.38.1"
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.generateDescriptorSet = true
            task.plugins {
                id("grpc")
            }
        }
    }
}

tasks {

    test {
        useJUnitPlatform()
    }

    docker {
      
        dependsOn(clean.get(), distTar.get())

        setDockerfile(rootProject.file("Dockerfile"))

        name = "cscandura/${project.name}:0.4"

        files(distTar.get().outputs)

        buildArgs(mapOf(
                Pair("RUNNABLE_NAME", project.name)
        ))
    }


}

application {
    mainClass.set("com.akkasls.hackathon.CryptoTradingServiceRunner")
}
