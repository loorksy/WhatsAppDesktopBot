package com.whatsappbot.bulk.data

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val force: Boolean,
    val apkUrl: String,
    val title: String,
    val changelog: List<String>,
)
