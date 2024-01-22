import platform.UIKit.UIDevice

actual val platform: Platform by lazy {
    Platform(
        name = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion,
    )
}
