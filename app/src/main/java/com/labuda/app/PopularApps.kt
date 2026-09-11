package com.labuda.app

/**
 * Curated by GPT analysis for apps that commonly need alternative routing
 * when used from Russia. The UI still shows only apps actually installed
 * on the device.
 *
 * Keep this list conservative: an app is included only when there is a
 * meaningful reason to route its traffic. Package names are stable Android
 * identifiers, so the list can be maintained independently from labels.
 */
object PopularApps {
    private val routingPackages = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.twitter.android",
        "com.discord",
        "org.thoughtcrime.securesms",
        "tv.twitch.android.app",
        "com.spotify.music",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.reddit.frontpage"
    )

    fun requiresRouting(packageName: String): Boolean = packageName in routingPackages
}
