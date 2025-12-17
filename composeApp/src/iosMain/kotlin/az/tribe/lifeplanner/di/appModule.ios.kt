package az.tribe.lifeplanner.di

import az.tribe.lifeplanner.data.auth.AuthService
import az.tribe.lifeplanner.data.auth.AuthServiceImpl
import az.tribe.lifeplanner.data.network.FirebaseTokenProvider
import az.tribe.lifeplanner.data.network.IOSFirebaseTokenProvider

actual fun createFirebaseTokenProvider(): FirebaseTokenProvider {
    return IOSFirebaseTokenProvider()
}

actual fun createAuthService(): AuthService {
    return AuthServiceImpl()
}
