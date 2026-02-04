plugins {
    id("java")
}

group = "com.purityvanilla"
version = "4.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("com.velocitypowered", "velocity-api", "3.5.0-SNAPSHOT")
    implementation("net.luckperms" ,"api", "5.4")
    implementation("org.yaml", "snakeyaml", "2.0")
}
