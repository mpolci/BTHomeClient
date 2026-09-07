package io.github.mpolci.bthomeclient.testing

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

/** Isolated host for native screenshot fixtures. It does not create app repositories or write app data. */
class UxReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }
}
