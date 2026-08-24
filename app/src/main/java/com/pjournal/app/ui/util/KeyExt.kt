package com.pjournal.app.ui.util

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed

val KeyEvent.isCtrl: Boolean
    get() = isCtrlPressed && !isAltPressed
