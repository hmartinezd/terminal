package com.venkoi.terminal.licensing

import com.venkoi.terminal.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DebugLicenseModule {
    @Provides @Singleton fun policy(): RuntimeLicensePolicy = object : RuntimeLicensePolicy {
        override val developerAuthorization = !BuildConfig.ENFORCE_LICENSE_IN_DEBUG
        override fun appIntegrityValid() = true
    }
}
