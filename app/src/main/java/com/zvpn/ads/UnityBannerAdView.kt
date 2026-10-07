package com.zvpn.ads

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import com.zvpn.domain.ads.UnityAdsConfig

/**
 * Compose wrapper for Unity Ads Banner.
 * Place at the bottom or top of a screen for free users.
 *
 * Usage:
 * ```
 * if (!isPro) {
 *     UnityBannerAdView(modifier = Modifier.fillMaxWidth().height(60.dp))
 * }
 * ```
 */
@Composable
fun UnityBannerAdView(
    modifier: Modifier = Modifier,
    placementId: String = UnityAdsConfig.PLACEMENT_BANNER_ANDROID,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val activity = (context as? android.app.Activity)
                ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
            if (activity != null) {
                BannerView(activity, placementId, UnityBannerSize.getDynamicSize(context)).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    listener = object : BannerView.IListener {
                        override fun onBannerLoaded(bannerAdView: BannerView) {}
                        override fun onBannerShown(bannerAdView: BannerView) {}
                        override fun onBannerClick(bannerAdView: BannerView) {}
                        override fun onBannerFailedToLoad(bannerAdView: BannerView, errorInfo: BannerErrorInfo) {}
                        override fun onBannerLeftApplication(bannerAdView: BannerView) {}
                    }
                    load()
                }
            } else {
                android.view.View(context)
            }
        },
        update = { /* Banner auto-refreshes */ },
    )
}
