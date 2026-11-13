package com.tutpro.baresip

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.util.IdentityHashMap

private val protectedWindows = IdentityHashMap<Window,Int>()

/** Prevent customer/account screens from entering screenshots or recent-task
 * thumbnails. Reference counting also covers concurrent call/customer panels. */
@Composable
fun InstitutionScreenPrivacy() {
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        if(window != null) {
            protectedWindows[window] = (protectedWindows[window] ?: 0) + 1
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if(window != null) {
                val count = (protectedWindows[window] ?: 1) - 1
                if(count == 0) {
                    protectedWindows.remove(window)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else protectedWindows[window] = count
            }
        }
    }
}
