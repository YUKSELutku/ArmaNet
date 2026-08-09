package com.shieldblock.shieldblock.traffic

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream

class AppResolver(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    @Volatile
    private var cachedApps: List<InstalledApp>? = null

    @Volatile
    private var cachedAt = 0L

    fun resolveUid(uid: Int?): AppIdentity {
        if (uid == null || uid < 0) return AppIdentity()
        val packages = packageManager.getPackagesForUid(uid).orEmpty()
        if (packages.isEmpty()) return AppIdentity(uid = uid)
        val packageName = packages.first()
        val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
        return AppIdentity(
            uid = uid,
            packageName = packageName,
            appName = info?.loadLabel(packageManager)?.toString() ?: packageName,
            confidence = if (packages.size == 1) AttributionConfidence.HIGH else AttributionConfidence.LOW,
        )
    }

    @Synchronized
    fun listLaunchableApps(): List<InstalledApp> {
        val now = System.currentTimeMillis()
        cachedApps?.takeIf { now - cachedAt < APP_CACHE_MS }?.let { return it }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                val applicationInfo = resolveInfo.activityInfo.applicationInfo
                InstalledApp(
                    packageName = packageName,
                    appName = applicationInfo.loadLabel(packageManager).toString(),
                    uid = applicationInfo.uid,
                    iconPng = runCatching { drawableToPng(applicationInfo.loadIcon(packageManager)) }.getOrNull(),
                )
            }
            .distinctBy(InstalledApp::packageName)
            .sortedBy { it.appName.lowercase() }
            .also {
                cachedApps = it
                cachedAt = now
            }
    }

    fun resolvePackage(packageName: String): InstalledApp? {
        val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
        return InstalledApp(
            packageName = packageName,
            appName = info.loadLabel(packageManager).toString(),
            uid = info.uid,
            iconPng = runCatching { drawableToPng(info.loadIcon(packageManager)) }.getOrNull(),
        )
    }

    private fun drawableToPng(drawable: Drawable): ByteArray {
        val ownsSource = drawable !is BitmapDrawable || drawable.bitmap == null
        val source = if (!ownsSource) {
            (drawable as BitmapDrawable).bitmap
        } else {
            Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceIn(1, ICON_SIZE_PX),
                drawable.intrinsicHeight.coerceIn(1, ICON_SIZE_PX),
                Bitmap.Config.ARGB_8888,
            ).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        }
        val scale = minOf(ICON_SIZE_PX.toFloat() / source.width, ICON_SIZE_PX.toFloat() / source.height, 1f)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }
        return try {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        } finally {
            if (bitmap !== source) bitmap.recycle()
            if (ownsSource) source.recycle()
        }
    }

    companion object {
        private const val ICON_SIZE_PX = 96
        private const val APP_CACHE_MS = 60_000L
    }
}

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val iconPng: ByteArray?,
)
