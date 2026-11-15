package com.example.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdLoadStatus {
    data object Idle : AdLoadStatus
    data object Loading : AdLoadStatus
    data object Ready : AdLoadStatus
    data class Failed(val reason: String) : AdLoadStatus
}

class AdManager(private val appContext: Context) {

    private val adScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var lastInterstitialShownMs: Long = 0L
    private var adsRemoved: Boolean = false
    private val initStarted = AtomicBoolean(false)

    private val _interstitialStatus = MutableStateFlow<AdLoadStatus>(AdLoadStatus.Idle)
    val interstitialStatus: StateFlow<AdLoadStatus> = _interstitialStatus.asStateFlow()

    private val _rewardedStatus = MutableStateFlow<AdLoadStatus>(AdLoadStatus.Idle)
    val rewardedStatus: StateFlow<AdLoadStatus> = _rewardedStatus.asStateFlow()

    fun initialize() {
        if (!initStarted.compareAndSet(false, true)) return
        adScope.launch {
            try {
                MobileAds.initialize(appContext) {
                    adScope.launch(Dispatchers.Main) {
                        preloadInterstitial()
                        preloadRewarded()
                    }
                }
            } catch (t: Throwable) {
                initStarted.set(false)
                Log.w(TAG, "MobileAds init skipped: ${t.message}")
            }
        }
    }

    fun setAdsRemoved(removed: Boolean) {
        adsRemoved = removed
    }

    fun preloadInterstitial() {
        if (adsRemoved) return
        _interstitialStatus.value = AdLoadStatus.Loading
        try {
            val request = AdRequest.Builder().build()
            InterstitialAd.load(
                appContext,
                TEST_INTERSTITIAL_AD_UNIT_ID,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        _interstitialStatus.value = AdLoadStatus.Ready
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        _interstitialStatus.value = AdLoadStatus.Failed(error.message)
                    }
                }
            )
        } catch (t: Throwable) {
            _interstitialStatus.value = AdLoadStatus.Failed(t.message ?: "Ad load unavailable")
        }
    }

    fun preloadRewarded() {
        _rewardedStatus.value = AdLoadStatus.Loading
        try {
            val request = AdRequest.Builder().build()
            RewardedAd.load(
                appContext,
                TEST_REWARDED_AD_UNIT_ID,
                request,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        _rewardedStatus.value = AdLoadStatus.Ready
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        _rewardedStatus.value = AdLoadStatus.Failed(error.message)
                    }
                }
            )
        } catch (t: Throwable) {
            _rewardedStatus.value = AdLoadStatus.Failed(t.message ?: "Rewarded ad unavailable")
        }
    }

    fun showPostMatchInterstitialIfReady(activity: Activity, onFinished: () -> Unit) {
        val now = System.currentTimeMillis()
        if (adsRemoved || now - lastInterstitialShownMs < INTERSTITIAL_COOLDOWN_MS) {
            onFinished()
            return
        }
        val ad = interstitialAd
        if (ad == null) {
            preloadInterstitial()
            onFinished()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                lastInterstitialShownMs = System.currentTimeMillis()
                preloadInterstitial()
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                onFinished()
            }
        }
        try {
            ad.show(activity)
        } catch (t: Throwable) {
            Log.w(TAG, "Interstitial show failed: ${t.message}")
            interstitialAd = null
            onFinished()
        }
    }

    fun showRewardedAd(
        activity: Activity,
        onRewardGranted: (amount: Int) -> Unit,
        onUnavailable: (String) -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            preloadRewarded()
            onUnavailable("Rewarded ad is still loading or unavailable in this environment.")
            return
        }
        var rewarded = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preloadRewarded()
                if (rewarded) {
                    onRewardGranted(100)
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                onUnavailable(adError.message)
            }
        }
        try {
            ad.show(activity) { rewardItem ->
                rewarded = rewardItem.amount > 0
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Rewarded show failed: ${t.message}")
            rewardedAd = null
            onUnavailable(t.message ?: "Rewarded ad unavailable")
        }
    }

    companion object {
        private const val TAG = "CobraAdManager"
        private const val INTERSTITIAL_COOLDOWN_MS = 90_000L

        // Official Google Mobile Ads Test Ad Unit IDs (development only)
        const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }
}
