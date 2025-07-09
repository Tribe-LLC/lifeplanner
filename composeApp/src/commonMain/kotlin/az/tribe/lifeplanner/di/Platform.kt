package az.tribe.lifeplanner.di

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun onApplicationStartPlatformSpecific()
