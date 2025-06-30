package tss.t.tsiptv

import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val isIOS: Boolean
        get() = true
}

actual fun getPlatform(): Platform = IOSPlatform()
