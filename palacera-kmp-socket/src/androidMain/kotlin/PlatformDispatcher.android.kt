import android.provider.Settings
import com.ncorti.kotlin.template.library.android.applicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual object PlatformDispatcher {
    actual val io: CoroutineDispatcher = Dispatchers.IO
}

actual val inAirplaneMode = Settings.System.getInt(
    applicationContext.contentResolver,
    Settings.Global.AIRPLANE_MODE_ON, 0
) != 0
