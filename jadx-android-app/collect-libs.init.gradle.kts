// Init script: collect jadx-dex-input runtime jars into /opt/jadx-libs
allprojects {
	afterEvaluate {
		if (project.name == "jadx-dex-input") {
			tasks.register<Copy>("collectLibs") {
				dependsOn("jar")
				from(configurations.named("runtimeClasspath"))
				from(tasks.named<Jar>("jar"))
				into("/opt/jadx-libs")
				duplicatesStrategy = DuplicatesStrategy.EXCLUDE
			}
		}
	}
}
