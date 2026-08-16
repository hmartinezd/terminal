package com.venkoi.terminal.licensing

interface RuntimeLicensePolicy {
    val developerAuthorization: Boolean
    fun appIntegrityValid(): Boolean
}
