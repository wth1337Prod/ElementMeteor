plugins {
    java
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "ru.elementcraft.elementmeteor"
version = "1.0"

repositories {
    mavenCentral()

    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }

    maven {
        name = "panda-repository"
        url = uri("https://repo.panda-lang.org/releases")
    }

    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")

    // projectkorra
    compileOnly("com.github.ProjectKorra:ProjectKorra:v1.11.2")

    // litecommands через panda rep
    implementation("dev.rollczi:litecommands-bukkit:3.9.6")

    // hikariCP
    implementation("com.zaxxer:HikariCP:4.0.3")

    // mariadb connector/j
    implementation("org.mariadb.jdbc:mariadb-java-client:3.0.7")

    // triumph-GUI для создания меню
    implementation("dev.triumphteam:triumph-gui:3.1.2")
}

bukkit {
    name = "ElementMeteor"
    version = project.version.toString()
    main = "ru.elementcraft.elementmeteor.ElementMeteor"
    apiVersion = "1.16" // Paper 1.16.5

    authors = listOf("ElementCraft")
    depend = listOf("ProjectKorra")

    permissions {
        register("elementmeteor.use") {
            description = "Разрешает использование команды /elementmeteor"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.TRUE
        }
        register("bending.ability.Meteor") {
            description = "Разрешает использование способности Meteor"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.TRUE
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
    }
    
    shadowJar {
        archiveClassifier.set("")
        relocate("com.zaxxer.hikari", "ru.elementcraft.elementmeteor.libs.hikari")
        relocate("org.mariadb.jdbc", "ru.elementcraft.elementmeteor.libs.mariadb")
        relocate("dev.rollczi", "ru.elementcraft.elementmeteor.libs.litecommands")
        relocate("dev.triumphteam.gui", "ru.elementcraft.elementmeteor.libs.gui")
    }
    
    build {
        dependsOn(shadowJar)
    }
}