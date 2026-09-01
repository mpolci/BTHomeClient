package io.github.mpolci.bthomeclient

import android.app.Application
import io.github.mpolci.bthomeclient.app.AppContainer

class BTHomeApplication : Application() {
    val appContainer by lazy { AppContainer(this) }
}
