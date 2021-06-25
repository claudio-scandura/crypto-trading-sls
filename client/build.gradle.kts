
plugins {
    id("scala")
}

dependencies {
    implementation(project(":crypto-trading"))
    implementation("com.typesafe.akka:akka-actor-typed_2.13:2.6.14")
    implementation("com.lightbend.akka:akka-stream-alpakka-csv_2.13:3.0.0")
    implementation("com.lightbend.akka:akka-stream-alpakka-file_2.13:3.0.0")
    implementation("com.typesafe.akka:akka-stream_2.13:2.6.14")
    implementation("com.typesafe.play:play-json_2.13:2.10.0-RC2")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.typesafe.akka:akka-http_2.13:10.2.4")

    implementation("com.google.api.grpc:proto-google-common-protos:2.0.1")
    implementation("io.grpc:grpc-protobuf:1.36.0")
    implementation("io.grpc:grpc-netty-shaded:1.36.0")
    implementation("io.grpc:grpc-stub:1.36.0")
}


application {
    mainClass.set("com.akkasls.hackathon.Client")
}