plugins {
    idea
    java
    scala
    application
    id("io.freefair.lombok") version "6.0.0-m2" apply false
    id("com.palantir.docker") version "0.26.0" apply false
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

allprojects {

    repositories {
        jcenter()
        mavenCentral()
        mavenLocal()
    }

    apply {
        plugin("idea")
        plugin("application")
        plugin("io.freefair.lombok")
    }

}

tasks {

    wrapper {
        gradleVersion = "7.0.2"
        distributionType = Wrapper.DistributionType.BIN
    }
}
