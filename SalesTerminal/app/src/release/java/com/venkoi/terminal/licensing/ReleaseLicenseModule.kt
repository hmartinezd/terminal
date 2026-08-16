package com.venkoi.terminal.licensing

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.venkoi.terminal.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.MessageDigest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReleaseLicenseModule {
    @Provides @Singleton fun policy(@ApplicationContext context: Context): RuntimeLicensePolicy = object : RuntimeLicensePolicy {
        override val developerAuthorization = false
        override fun appIntegrityValid(): Boolean = runCatching {
            val expected = BuildConfig.EXPECTED_RELEASE_CERT_SHA256.replace(":", "").uppercase()
            if (expected.length != 64) return@runCatching false
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
            val info = context.packageManager.getPackageInfo(context.packageName, flags)
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") info.signatures
            signatures.orEmpty().any { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02X".format(it) } == expected
            }
        }.getOrDefault(false)
    }
}
