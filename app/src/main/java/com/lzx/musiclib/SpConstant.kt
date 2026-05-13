package com.lzx.musiclib

import com.lzx.starrysky.utils.KtPreferences

object SpConstant : KtPreferences() {
    var KEY_TOKEN by stringPref()
    var KEY_EXPIRES by stringPref()
}