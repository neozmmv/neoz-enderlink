plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
}

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
	mavenCentral()
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("neoz_enderlink") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}

	runs {
		configureEach {
			// iroh loads its native library through JNA; silences Java's restricted-method warning
			vmArg("--enable-native-access=ALL-UNNAMED")
		}
	}
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")

	// iroh (https://iroh.computer) - published as Kotlin bindings with bundled native libraries.
	// It is called from Java, so its Kotlin runtime has to ship inside our jar (`include`).
	// JNA is intentionally not included: Minecraft already provides it.
	val irohVersion = providers.gradleProperty("iroh_version").get()
	val kotlinStdlibVersion = providers.gradleProperty("kotlin_stdlib_version").get()
	val kotlinxCoroutinesVersion = providers.gradleProperty("kotlinx_coroutines_version").get()

	implementation("computer.iroh:iroh:$irohVersion")
	implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinStdlibVersion")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")

	include("computer.iroh:iroh:$irohVersion")
	include("org.jetbrains.kotlin:kotlin-stdlib:$kotlinStdlibVersion")
	include("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")
}

tasks.processResources {
	val version = version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
