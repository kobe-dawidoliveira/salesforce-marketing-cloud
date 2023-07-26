package com.example.sfmc_plugin

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.salesforce.marketingcloud.MCLogListener
import com.salesforce.marketingcloud.MarketingCloudConfig
import com.salesforce.marketingcloud.MarketingCloudSdk
import com.salesforce.marketingcloud.UrlHandler
import com.salesforce.marketingcloud.messages.iam.InAppMessage
import com.salesforce.marketingcloud.messages.iam.InAppMessageManager
import com.salesforce.marketingcloud.sfmcsdk.*
import com.salesforce.marketingcloud.notifications.NotificationCustomizationOptions
import com.salesforce.marketingcloud.notifications.NotificationManager
import java.util.*

const val LOG_TAG = "MCSDK"

abstract class BaseApplication : Application(), UrlHandler {

    internal abstract val configBuilder: MarketingCloudConfig.Builder

    override fun onCreate() {
        super.onCreate()
        initSDK()
    }

    override fun handleUrl(context: Context, url: String, urlSource: String): PendingIntent? {
        return PendingIntent.getActivity(
            context,
            1,
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun initSDK() {
        // Initialize logging _before_ initializing the SDK to avoid losing valuable debugging information.
        if(BuildConfig.DEBUG) {
            SFMCSdk.setLogging(LogLevel.DEBUG, LogListener.AndroidLogger())
            MarketingCloudSdk.setLogLevel(MCLogListener.VERBOSE)
            MarketingCloudSdk.setLogListener(MCLogListener.AndroidLogListener())
        }

        SFMCSdk.configure(applicationContext as Application, SFMCSdkModuleConfig.build {
            pushModuleConfig = MarketingCloudConfig.builder().apply {
                setApplicationId(BuildConfig.MC_APP_ID)
                setAccessToken(BuildConfig.MC_ACCESS_TOKEN)
                setSenderId(BuildConfig.MC_SENDER_ID)
                setMid(BuildConfig.MC_MID)
                setMarketingCloudServerUrl(BuildConfig.MC_SERVER_URL)
                setNotificationCustomizationOptions(
                    NotificationCustomizationOptions.create(R.drawable.ic_notification_icon)
                )
            // Other configuration options
            }.build(applicationContext)
        }) { initStatus ->
            when (initStatus.status) {
                InitializationStatus.SUCCESS -> {
                    Log.v(LOG_TAG, "Marketing Cloud initialization successful.")
                }
                InitializationStatus.FAILURE -> {
                    // Given that this app is used to show SDK functionality we will hard exit if SDK init outright failed.
                    Log.e(LOG_TAG, "Marketing Cloud initialization failed.")
                    throw RuntimeException("Init failed")
                }
            }
        }

        SFMCSdk.requestSdk { sdk ->
            sdk.mp {
                it.pushMessageManager.enablePush()
            }
        }
    }
}
