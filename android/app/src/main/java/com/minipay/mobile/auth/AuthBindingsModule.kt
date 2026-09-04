package com.minipay.mobile.auth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {
    @Binds
    abstract fun bindAuthGateway(repository: AuthRepository): AuthGateway

    @Binds
    abstract fun bindIdentityService(api: IdentityApi): IdentityService

    @Binds
    abstract fun bindSessionStorage(store: SecureSessionStore): SessionStorage
}
