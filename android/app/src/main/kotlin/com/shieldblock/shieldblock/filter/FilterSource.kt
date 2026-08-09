package com.shieldblock.shieldblock.filter

import android.content.Context

fun interface FilterSource {
    fun readText(path: String): String
}

class AndroidAssetFilterSource(
    context: Context,
    private val assetRoot: String = "flutter_assets/assets/filters",
) : FilterSource {
    private val assets = context.applicationContext.assets

    override fun readText(path: String): String =
        assets.open("$assetRoot/$path").bufferedReader(Charsets.UTF_8).use { it.readText() }
}
