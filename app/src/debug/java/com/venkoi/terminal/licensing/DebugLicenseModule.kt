package com.venkoi.terminal.licensing

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DebugLicenseModule {
    @Provides @Singleton fun policy(): RuntimeLicensePolicy = object : RuntimeLicensePolicy {
        override val developerAuthorization = true
        override fun appIntegrityValid() = true
    }
}
