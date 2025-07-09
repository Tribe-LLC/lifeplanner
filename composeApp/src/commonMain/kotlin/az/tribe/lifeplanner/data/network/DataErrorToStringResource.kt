package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.data.model.DataError
import az.tribe.lifeplanner.data.model.UiText
import leanlifeplanner.composeapp.generated.resources.Res
import leanlifeplanner.composeapp.generated.resources.error_disk_full
import leanlifeplanner.composeapp.generated.resources.error_no_internet
import leanlifeplanner.composeapp.generated.resources.error_request_timeout
import leanlifeplanner.composeapp.generated.resources.error_serialization
import leanlifeplanner.composeapp.generated.resources.error_too_many_requests
import leanlifeplanner.composeapp.generated.resources.error_unknown
import leanlifeplanner.composeapp.generated.resources.error_authorization

fun DataError.toUiText(): UiText {
    return when (this) {
        is DataError.Remote.VALIDATION -> UiText.DynamicString(message ?: "Validation error")
        DataError.Local.DISK_FULL -> UiText.StringResourceId(Res.string.error_disk_full)
        DataError.Local.CANCELLED -> UiText.StringResourceId(Res.string.error_unknown)
        DataError.Local.UNKNOWN -> UiText.StringResourceId(Res.string.error_unknown)
        DataError.Remote.REQUEST_TIMEOUT -> UiText.StringResourceId(Res.string.error_request_timeout)
        DataError.Remote.TOO_MANY_REQUESTS -> UiText.StringResourceId(Res.string.error_too_many_requests)
        DataError.Remote.UNATHORIZATION -> UiText.StringResourceId(Res.string.error_authorization)
        DataError.Remote.NO_INTERNET -> UiText.StringResourceId(Res.string.error_no_internet)
        DataError.Remote.SERVER -> UiText.StringResourceId(Res.string.error_unknown)
        DataError.Remote.CANCELLED -> UiText.StringResourceId(Res.string.error_unknown)
        DataError.Remote.NOT_FOUND -> UiText.StringResourceId(Res.string.error_unknown)
        DataError.Remote.PAYMENT_ERROR -> UiText.StringResourceId(Res.string.error_unknown)
        DataError.Remote.SERVER_ERROR -> UiText.StringResourceId(Res.string.error_unknown)
        DataError.Remote.SERIALIZATION -> UiText.StringResourceId(Res.string.error_serialization)
        DataError.Remote.UNKNOWN -> UiText.StringResourceId(Res.string.error_unknown)
    }
}