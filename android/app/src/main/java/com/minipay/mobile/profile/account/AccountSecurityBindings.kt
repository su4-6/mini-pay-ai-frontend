package com.minipay.mobile.profile.account

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountSecurityBindings {
    @Binds
    abstract fun bindAccountSecurityGateway(
        repository: AccountSecurityRepository
    ): AccountSecurityGateway
}
