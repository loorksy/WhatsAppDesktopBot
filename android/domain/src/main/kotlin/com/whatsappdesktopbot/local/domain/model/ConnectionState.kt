package com.whatsappdesktopbot.local.domain.model

enum class ConnectionState {
    NOT_LINKED,
    REQUESTING_CODE,
    PAIRING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    WAITING_FOR_NETWORK,
    PAUSED_NETWORK_CHANGE,
    AUTH_FAILED,
    LOGGED_OUT,
    ERROR,
}

fun ConnectionState.isLinked(): Boolean =
    this == ConnectionState.CONNECTED ||
        this == ConnectionState.CONNECTING ||
        this == ConnectionState.PAIRING

fun ConnectionState.canRunBot(): Boolean = this == ConnectionState.CONNECTED
