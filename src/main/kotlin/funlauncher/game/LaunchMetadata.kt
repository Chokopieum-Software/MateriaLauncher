package funlauncher.game

internal fun rulesApply(
    rules: List<VersionInfo.Rule>,
    os: String,
    arch: String = System.getProperty("os.arch"),
    version: String = System.getProperty("os.version"),
    features: Map<String, Boolean> = emptyMap()
): Boolean {
    if (rules.isEmpty()) return true
    var allowed = false
    for (rule in rules) {
        val platform = rule.os
        if (platform?.name != null && platform.name != os) continue
        if (platform?.arch != null && !Regex(platform.arch).matches(arch)) continue
        if (platform?.version != null && !Regex(platform.version).containsMatchIn(version)) continue
        if (rule.features.any { (key, value) -> (features[key] ?: false) != value }) continue
        allowed = rule.action == "allow"
    }
    return allowed
}

internal fun nativeArch(arch: String = System.getProperty("os.arch")): String =
    if (arch.lowercase() in listOf("x86", "i386", "i686")) "32" else "64"

internal fun mavenArtifactPath(name: String, classifier: String? = null): String {
    val coordinate = name.substringBefore('@').split(':')
    require(coordinate.size in 3..4) { "Invalid Maven coordinate: $name" }
    val (group, artifact, version) = coordinate
    val suffix = (classifier ?: coordinate.getOrNull(3))?.let { "-$it" } ?: ""
    val extension = name.substringAfter('@', "jar")
    return "${group.replace('.', '/')}/$artifact/$version/$artifact-$version$suffix.$extension"
}
