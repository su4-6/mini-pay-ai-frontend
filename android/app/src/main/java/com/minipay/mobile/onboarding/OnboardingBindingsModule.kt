package com.minipay.mobile.onboarding

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingBindingsModule {
    @Binds
    abstract fun bindOnboardingGateway(repository: OnboardingRepository): OnboardingGateway
}
