object AppBuildConfig {
    const val compileSdk = 34
    const val minSdk = 27
    const val targetSdk = 33

    const val appId = "cloud.keyspace.android"
    const val namespace = "cloud.keyspace.android"
    const val versionName = "1.4.2"
    val versionCode = versionNameToVersionCode(versionName)

    /**
     * Converts the given version name (String) to a version code (Integer).
     *
     * @param versionName The version name to be converted.  It must be in the format:
     *                    <b><code>x.y.z</code></b><br>
     *                    where:
     *                    <ul>
     *                        <li>x is the major version (at least 1 digit)
     *                        <li>y is the minor version (at least 1 digit, at most 3 digits)
     *                        <li>y is the patch number (at least 1 digit, at most 3 digits)
     *                    </ul>
     *
     * @return The version code encoded in the format: <b><code>xyyyzzz</code></b><br>
     *         where:
     *         <ul>
     *             <li>x is the major version (at least 1 digit)
     *             <li>yyy is the minor version (3 digits; from 000 to 999)
     *             <li>zzz is the patch number (3 digits; from 000 to 999)
     *         <ul>
     */
    fun versionNameToVersionCode(versionName: String): Int {
        println("versionNameToVersionCode: versionName = $versionName")

        val parts = versionName.split(".")

        if (parts.size != 3) {
            throw IllegalArgumentException("version name must be in the format: x.y.z")
        }

        val major = parts[0]
        println("versionNameToVersionCode: major = $major")

        val minor = parts[1]
        println("versionNameToVersionCode: minor = $minor")

        val patch = parts[2]
        println("versionNameToVersionCode: patch = $patch")

        if (major.isEmpty()) {
            throw IllegalArgumentException("major version must be at least 1 digit")
        }

        if ((minor.length > 3) || (minor.isEmpty())) {
            throw IllegalArgumentException("minor version must be at least 1 digit, at most 3 digits")
        }

        if ((patch.length > 3) || (patch.isEmpty())) {
            throw IllegalArgumentException("patch number must be at least 1 digit, at most 3 digits")
        }

        val versionCodeString = parts[0] + parts[1].padStart(3, '0') + parts[2].padStart(3, '0')
        val versionCode = versionCodeString.toInt()
        println("versionNameToVersionCode: versionCode = $versionCode")

        return versionCode
    }
}
