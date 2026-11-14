package com.adbutility.guard.ui

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Helper compartido para aplicar márgenes seguros respecto a la barra de estado y navegación
 * en Android 15+ (edge-to-edge), evitando que las cabeceras se solapen con los íconos del sistema.
 */
object UiInsetsHelper {

    fun applyEdgeToEdge(activity: Activity, appBarView: View, contentView: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        val initialAppBarTop = appBarView.paddingTop
        val initialAppBarLeft = appBarView.paddingLeft
        val initialAppBarRight = appBarView.paddingRight

        val initialContentBottom = contentView.paddingBottom
        val initialContentLeft = contentView.paddingLeft
        val initialContentRight = contentView.paddingRight

        ViewCompat.setOnApplyWindowInsetsListener(activity.window.decorView) { _, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            appBarView.setPadding(
                initialAppBarLeft + bars.left,
                initialAppBarTop + bars.top,
                initialAppBarRight + bars.right,
                appBarView.paddingBottom
            )
            contentView.setPadding(
                initialContentLeft + bars.left,
                contentView.paddingTop,
                initialContentRight + bars.right,
                initialContentBottom + bars.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(activity.window.decorView)
    }
}

