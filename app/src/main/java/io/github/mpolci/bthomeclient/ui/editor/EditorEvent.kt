package io.github.mpolci.bthomeclient.ui.editor

sealed interface EditorEvent {
    data object Saved : EditorEvent
}
