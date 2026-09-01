package io.github.mpolci.bthomeclient.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

inline fun <reified T : ViewModel> simpleViewModelFactory(
    noinline create: () -> T,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create() }
}
