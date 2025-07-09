package az.tribe.lifeplanner.data.model

sealed interface DataError : Error {

    sealed class Remote : DataError {
        object REQUEST_TIMEOUT : Remote()
        object SERVER_ERROR : Remote()
        object UNATHORIZATION : Remote()
        object TOO_MANY_REQUESTS : Remote()
        object NO_INTERNET : Remote()
        object SERVER : Remote()
        object SERIALIZATION : Remote()
        object UNKNOWN : Remote()
        object NOT_FOUND : Remote()
        object CANCELLED : Remote()
        object PAYMENT_ERROR : Remote()
        data class VALIDATION(val message: String?) : Remote()
    }

    enum class Local : DataError {
        DISK_FULL,
        UNKNOWN,
        CANCELLED
    }
}