package com.example.sfmc_plugin

import android.content.Context
import androidx.annotation.NonNull
import com.salesforce.marketingcloud.MarketingCloudSdk
import com.salesforce.marketingcloud.sfmcsdk.SFMCSdk
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import android.util.Log
import java.util.*
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import com.salesforce.marketingcloud.MCLogListener
import com.salesforce.marketingcloud.MarketingCloudConfig
import com.salesforce.marketingcloud.messages.iam.InAppMessage
import com.salesforce.marketingcloud.messages.iam.InAppMessageManager
import com.salesforce.marketingcloud.sfmcsdk.*
import com.salesforce.marketingcloud.notifications.NotificationCustomizationOptions
import com.salesforce.marketingcloud.notifications.NotificationManager

const val LOG_TAG = "MCSDK"

class SfmcPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "sfmc_plugin")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "initialize" -> {
                try {
                    initSDK()
                    result.success(true)
                } catch(e: RuntimeException) {
                    result.error(e.toString(), e.toString(), e.toString())
                }
            }
            "setContactKey" -> {
                var isOperationSuccessful: Boolean
                val contactKey = call.argument<String>("contactKey")
                if (contactKey == null || contactKey == "") {
                    result.error(
                        "Contact Key is not valid",
                        "Contact Key is not valid",
                        "Contact Key is not valid"
                    )
                    return
                }
                isOperationSuccessful = try {
                    SFMCSdk.requestSdk { sdk ->
                        sdk.identity.setProfileId(contactKey)
                    }
                    true
                } catch (e: RuntimeException) {
                    false
                }
                result.success(isOperationSuccessful)
            }
            "getContactKey" -> {
                var key: String? = null
                try {
                    SFMCSdk.requestSdk { sdk ->
                        sdk.mp {
                            var contactKey = it.moduleIdentity.profileId
                            key = contactKey
                            result.success(key)
                        }
                    }
                } catch (e: RuntimeException) {
                    result.success(key)
                }
            }
            "addTag" -> {
                val tagToAdd: String? = call.argument<String?>("tag")
                if (tagToAdd == null || tagToAdd == "") {
                    result.error("Tag is not valid", "Tag is not valid", "Tag is not valid");
                    return
                }
                try {
                    SFMCSdk.requestSdk { sdk ->
                        sdk.mp {
                            it.registrationManager.edit().run {
                                addTags(tagToAdd)
                                commit()
                                result.success(true)
                            }
                        }
                    }
                } catch (e: RuntimeException) {
                    result.error(e.toString(), e.toString(), e.toString())
                }
            }
            "removeTag" -> {
                val tagToRemove: String? = call.argument<String?>("tag")
                if (tagToRemove == null || tagToRemove == "") {
                    result.error("Tag is not valid", "Tag is not valid", "Tag is not valid");
                    return
                }
                try {
                    SFMCSdk.requestSdk { sdk ->
                        sdk.mp {
                            it.registrationManager.edit().run {
                                removeTags(tagToRemove)
                                commit()
                                result.success(true)
                            }
                        }
                    }
                } catch (e: RuntimeException) {
                    result.error(e.toString(), e.toString(), e.toString())
                }
            }
            "setProfileAttribute" -> {
                var isSuccessful: Boolean
                val attributeKey: String? = call.argument<String?>("key")
                val attributeValue: String? = call.argument<String?>("value")
                if (attributeKey == null || attributeKey == "") {
                    result.error("Attribute Key is not valid", "Attribute Key is not valid", "Attribute Key is not valid");
                    return
                }
                if (attributeValue == null || attributeValue == "") {
                    result.error("Attribute Value is not valid", "Attribute Value is not valid", "Attribute Value is not valid");
                    return
                }
                isSuccessful = try {
                    SFMCSdk.requestSdk { sdk ->
                        sdk.identity.setProfileAttribute(attributeKey, attributeValue)
                    }
                    true
                } catch (e: RuntimeException) {
                    false
                }
                result.success(isSuccessful)
            }
            "clearProfileAttribute" -> {
                var isSuccessful: Boolean
                val attributeKey: String? = call.argument<String?>("key")
                if (attributeKey == null || attributeKey == "") {
                    result.error("Attribute Key is not valid", "Attribute Key is not valid", "Attribute Key is not valid");
                    return
                }
                isSuccessful = try {
                    SFMCSdk.requestSdk { sdk ->
                        sdk.identity.clearProfileAttribute(attributeKey)
                    }
                    true
                } catch (e: RuntimeException) {
                    false
                }
                result.success(isSuccessful)
            }
            "setPushEnabled" -> {
                val enablePush: Boolean = call.argument<Boolean>("isEnabled") as Boolean
                if (enablePush) {
                    MarketingCloudSdk.requestSdk { sdk -> sdk.pushMessageManager.enablePush() }
                } else {
                    MarketingCloudSdk.requestSdk { sdk -> sdk.pushMessageManager.disablePush() }
                }
                result.success(true)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun initSDK() {
        // Initialize logging _before_ initializing the SDK to avoid losing valuable debugging information.
        if(BuildConfig.DEBUG) {
            MarketingCloudSdk.setLogLevel(MCLogListener.VERBOSE)
            MarketingCloudSdk.setLogListener(MCLogListener.AndroidLogListener())
        }

        SFMCSdk.configure(context as Application, SFMCSdkModuleConfig.build {
            pushModuleConfig = MarketingCloudConfig.builder().apply {
                setApplicationId(BuildConfig.MC_APP_ID)
                setAccessToken(BuildConfig.MC_ACCESS_TOKEN)
                setSenderId(BuildConfig.MC_SENDER_ID)
                setMid(BuildConfig.MC_MID)
                setMarketingCloudServerUrl(BuildConfig.MC_SERVER_URL)
                setNotificationCustomizationOptions(
                    NotificationCustomizationOptions.create { context, notificationMessage ->
                        val builder = NotificationManager.getDefaultNotificationBuilder(
                            context,
                            notificationMessage,
                            NotificationManager.createDefaultNotificationChannel(context),
                            R.drawable.ic_notification
                        )
                        builder.setContentIntent(
                            NotificationManager.redirectIntentForAnalytics(
                                context,
                                PendingIntent.getActivity(
                                    context,
                                    Random().nextInt(),
                                    Intent(Intent.ACTION_VIEW, Uri.parse(notificationMessage.url)),
                                    PendingIntent.FLAG_IMMUTABLE
                                ),
                                notificationMessage,
                                true,
                            )
                        )
                    }
                )
            // Other configuration options
            }.build(context)
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