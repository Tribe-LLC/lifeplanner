package az.tribe.lifeplanner.di

import az.tribe.lifeplanner.data.auth.AuthService
import az.tribe.lifeplanner.data.auth.AuthServiceImpl
import az.tribe.lifeplanner.data.network.AndroidFirebaseTokenProvider
import az.tribe.lifeplanner.data.network.FirebaseTokenProvider

actual fun createFirebaseTokenProvider(): FirebaseTokenProvider {
    return AndroidFirebaseTokenProvider()
}

actual fun createAuthService(): AuthService {
    return AuthServiceImpl()
}
