import android.os.Build

actual val platform: Platform by lazy {
    Platform(
        name = "Android ${Build.VERSION.SDK_INT}",
    )
}
