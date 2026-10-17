package com.qimian233.ztool.data.sogouime

/** Enter-key mapping modes exposed by the Sogou OEM IME hook. */
enum class SogouEnterKeyMode(val storageValue: String) {
    EnterSend("enter_send"),
    EnterNewline("enter_newline");

    companion object {
        fun fromStorage(raw: String?): SogouEnterKeyMode =
            entries.firstOrNull { it.storageValue == raw } ?: EnterSend
    }
}
