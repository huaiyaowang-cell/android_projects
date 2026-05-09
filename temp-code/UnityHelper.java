package com.unity3d.player;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;


import com.android.billingclient.api.BillingClient;

import com.bidderdesk.DeviceUtil;
import com.bidderdesk.abtest.ABTestManager;
import com.bidderdesk.ad.ADManager;
import com.bidderdesk.ad.AdHelper;
import com.bidderdesk.ad.utils.ReportUtil;
import com.bidderdesk.firebase.FirebaseConst;
import com.bidderdesk.firebase.util.FirebaseReportUtil;
import com.bidderdesk.util.log.LogUtil;
import com.blankj.utilcode.util.GsonUtils;

import java.util.List;


import android.net.TrafficStats;
import android.view.ViewGroup;

import androidx.annotation.IntRange;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;


/**
 * author : xinlv
 * date : 2022/12/26 11:48
 * mail : 13051256003@163.com
 * description : desc
 */
public class UnityHelper {
    private static UnityHelper singleIns = null;
    private static long lastRxBytes = 0;
    private static long lastTxBytes = 0;
    private static long lastTimeStamp = 0;
    private UnityHelper() {
    }

    public static UnityHelper getInstance() {
        if (singleIns == null) {
            synchronized (UnityHelper.class) {
                if (singleIns == null) {
                    singleIns = new UnityHelper();
                }
            }
        }
        return singleIns;
    }
    public String getUserId() {
        return UserHelper.createUserId();
    }

    public String getCountry(Context context) {
        return DeviceUtil.getDeviceCountryCode(context);
    }

    public static void reset() {
        lastRxBytes = TrafficStats.getTotalRxBytes();
        lastTxBytes = TrafficStats.getTotalTxBytes();
        lastTimeStamp = System.currentTimeMillis();
    }
    //监控网络速率
    public  String getNetworkSpeed() {
        long nowRxBytes = TrafficStats.getTotalRxBytes();
        long nowTxBytes = TrafficStats.getTotalTxBytes();
        long nowTimeStamp = System.currentTimeMillis();

        long rxBytes = nowRxBytes - lastRxBytes;
        long txBytes = nowTxBytes - lastTxBytes;
        long timeDiff = nowTimeStamp - lastTimeStamp;

        // Convert bytes to kilobytes and milliseconds to seconds
        float downloadSpeed = (rxBytes / 1024f) / (timeDiff / 1000f);
        float uploadSpeed = (txBytes / 1024f) / (timeDiff / 1000f);

        // Reset for next calculation
        lastRxBytes = nowRxBytes;
        lastTxBytes = nowTxBytes;
        lastTimeStamp = nowTimeStamp;
        Log.d("----->", "Download Speed: " + downloadSpeed + " KB/s, Upload Speed: " + uploadSpeed + " KB/s");
        return downloadSpeed + "_" + uploadSpeed;
    }
    /**
     * unity 调用android 上报埋点
     *
     * @param eventName  事件名称
     * @param paramNames 埋点key数组
     * @param params     与 paramNames 相对应的value
     */
    public void unityCallReport(String eventName, String[] paramNames, String[] params, String[] paramClassType) {
        if (paramNames.length != params.length || paramNames.length != paramClassType.length) {
            return;
        }
        try {
            Bundle bundle = new Bundle();
            int len = params.length;
            for (int i = 0; i < len; i++) {
                if (TextUtils.isEmpty(paramNames[i])) {
                    continue;
                }
                if (TextUtils.isEmpty(paramClassType[i])) {
                    continue;
                }
                try {
                    switch (paramClassType[i]) {
                        case "1":
//                        Log.d("埋点-----", "paramNames =  " + paramNames[i] + "..... type = int");
                            bundle.putInt(paramNames[i], Integer.parseInt(params[i]));
                            break;
                        case "2":
//                        Log.d("埋点-----", "paramNames =  " + paramNames[i] + "..... type = double");
                            bundle.putDouble(paramNames[i], Double.parseDouble(params[i]));
                            break;
                        case "3":
//                        Log.d("埋点-----", "paramNames =  " + paramNames[i] + "..... type = long");
                            bundle.putLong(paramNames[i], Long.parseLong(params[i]));
                            break;
                        case "4":
//                        Log.d("埋点-----", "paramNames =  " + paramNames[i] + "..... type = float");
                            bundle.putFloat(paramNames[i], Float.parseFloat(params[i]));
                            break;
                        default:
//                        Log.d("埋点-----", "paramNames =  " + paramNames[i] + "..... type = string");
                            bundle.putString(paramNames[i], params[i] == null ? "" : params[i]);
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
//            LogUtils.dTag("埋点-----", "埋点---" + eventName + "。。。。。bundle = " + bundle.toString());
            ReportUtil.INSTANCE.report(eventName, bundle);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("埋点-----", "统计失败 " + e.getMessage());
        }
    }


    /**
     * 展示激励或者插屏广告
     *
     * @param activity  当前所以扶的activity
     * @return true 表示广告已准备好并请求展示了，false 广告为准备好
     */

    public  void  LoadAD(Activity activity)
    {
        Log.d("SDK-AD", "LoadAD");
        ADManager.Companion.getAsInstance().loadAdByPlacement(activity,
                new String[]{
                        "banner","native",
                        "open",
                        "endinginter","startgameinter",
                        "backhomepageinter","backgameinter",
                        "starteventinter","endingeventinter",
                        "keeponinter","takebreakinter",


                        "revival","itemshoprv",
                        "puzzletipsrv","TurnTablerv",
                        "puzzlebonusrv","screndingrv",
                        "awardrotaterv","magnetrv",
                        "takebreakrv"
        });

    }

    public boolean ShowAD(Activity activity, String placement) {
        Log.d("SDK-AD", "currentThread:" + Thread.currentThread().getName());
        Log.d("SDK-AD", "show ad placement = " + placement);
        try {
            boolean state = AdHelper.INSTANCE.showAd(activity, placement, new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    AndroidCallUnityHelper.invokeMethod(
                            UnityConst.UNITY_ANDROID_RESULT,
                            UnityConst.APP_ADRESULT,
                            placement
                    );
                    return Unit.INSTANCE;
                }
            });
            Log.d("SDK-AD", "state = " + state);
            return state;
        } catch (Exception e) {
            Log.d("SDK-AD", "Exception = " +e.toString());
            e.printStackTrace();
            return false;
        }
    }
    public void ShowOpenAD(Activity activity, String placement) {
        ADManager.Companion.getAsInstance().ShowOpenAD(activity);
    }
    public String getABTestValue(String key) {
        Object appTestValue = ABTestManager.Companion.getInstance().getAppTestValue(key);
        return GsonUtils.toJson(appTestValue);
    }
    public String  getABTestValueInt(String key) {
        Object appTestValue = ABTestManager.Companion.getInstance().getAppTestValue(key);
        int intValue = 1;
        if (appTestValue instanceof Number) {
            intValue = ((Number) appTestValue).intValue();
        }
        return GsonUtils.toJson(intValue);
    }

    private  Vibrator vibrate = null;

    public void StartVibrate(Activity activity, int milliseconds, @IntRange(from = 1, to = 255) int amplitude) {
        if (vibrate == null) {
            vibrate = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (!vibrate.hasVibrator()) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrate.vibrate(VibrationEffect.createOneShot(milliseconds, amplitude));
            } else {
                vibrate.vibrate(milliseconds);
            }
        } catch (Exception e) {
        }
    }


    public void appPurchase(Activity activity, String productId, String productType) {
        GooglePayNHelper googlePayHelper = new GooglePayNHelper(activity);
        googlePayHelper.setOnProductPurchaseListener(purchase -> {
            LogUtil.d("----sub", "支付成功，发放奖励");
            AndroidCallUnityHelper.setCallParams(UnityConst.UNITY_ANDROID_RESULT, UnityConst.UNITY_PURCHASE_RESULT);
            AndroidCallUnityHelper.invokeMethod(productId);

            FirebaseReportUtil.INSTANCE.reportPurchase(FirebaseConst.PURCHASE_GRANT, true, null, productId, false);
        });
        String pType = TextUtils.equals(productType, BillingClient.ProductType.INAPP) ?
                BillingClient.ProductType.INAPP : BillingClient.ProductType.SUBS;
        googlePayHelper.startConnection(productId, pType, false, false, null);
    }



}
