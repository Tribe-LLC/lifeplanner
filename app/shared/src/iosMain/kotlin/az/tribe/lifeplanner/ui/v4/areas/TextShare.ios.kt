package az.tribe.lifeplanner.ui.v4.areas

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

internal actual fun shareText(text: String, title: String) {
    val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    top?.presentViewController(sheet, animated = true, completion = null)
}
