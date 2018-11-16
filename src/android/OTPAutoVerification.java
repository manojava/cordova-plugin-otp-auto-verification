package org.apache.cordova.OTPAutoVerification;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * This class echoes a string called from JavaScript.
 */
public class OTPAutoVerification extends CordovaPlugin {

    private BroadcastReceiver mSmsReceiver;
    private IntentFilter filter;
    public static final String SMS_READ_PERMISSION = Manifest.permission.READ_SMS;
    public static final String RECEIVE_SMS_PERMISSION = Manifest.permission.RECEIVE_SMS;
    public static final int REQUEST_CODE = 125;
    private static final String TAG = OTPAutoVerification.class.getSimpleName();

    public static String SMS_ORIGIN = null;
    public static String OTP_DELIMITER = null;
    public static String OTP_END_DELIMITER = null;
    public static int OTP_LENGTH = 0;
    public JSONArray options;
    public CallbackContext callbackContext;
    @Override
    public boolean execute(String action, JSONArray options, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("startOTPListener")) {
            Log.i(TAG, options.toString());
            this.options = options;
            this.callbackContext = callbackContext;
            if(cordova.hasPermission(SMS_READ_PERMISSION)) {
                Log.i("OTPAutoVerification", "Has Permission");
                startOTPListener(options, callbackContext);
            }
            else
            {
                getPermission(REQUEST_CODE);
            }

            return true;
        }else if (action.equals("stopOTPListener")) {
            stopOTPListener();
            return true;
        }
        return false;
    }

    private void startOTPListener(JSONArray options, final CallbackContext callbackContext) {
    /* take init parameter from JS call */
        try {
            SMS_ORIGIN = options.getJSONObject(0).getString("origin");
            OTP_DELIMITER = options.getJSONObject(0).getString("delimiter");
            OTP_LENGTH = options.getJSONObject(0).getInt("length");
            OTP_END_DELIMITER = options.getJSONObject(0).getString("end_delimiter");
            Log.i(TAG, "OTP_END_DELIMITER");
        } catch (JSONException e) {
            e.printStackTrace();
        }


        filter = new IntentFilter();
        filter.addAction("android.provider.Telephony.SMS_RECEIVED");

        if (this.mSmsReceiver == null) {
            this.mSmsReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {

                    final Bundle bundle = intent.getExtras();
                    try {
                        if (bundle != null) {
                            Object[] pdusObj = (Object[]) bundle.get("pdus");
                            for (Object aPdusObj : pdusObj) {
                                SmsMessage currentMessage = SmsMessage.createFromPdu((byte[]) aPdusObj);
                                String senderAddress = currentMessage.getDisplayOriginatingAddress();
                                String message = currentMessage.getDisplayMessageBody();

                                Log.e(TAG, "Received SMS: " + message + ", Sender: " + senderAddress);

                                // if the SMS is not from our gateway, ignore the message
                                if (!senderAddress.toLowerCase().contains(SMS_ORIGIN.toLowerCase())) {
                                    return;
                                }

                                // verification code from sms
                                String verificationCode = getVerificationCode(message);

                                Log.e(TAG, "OTP received: " + verificationCode);
                                stopOTPListener();
                                callbackContext.success(verificationCode);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Exception: " + e.getMessage());
                    }
                }
            };
            cordova.getActivity().registerReceiver(this.mSmsReceiver, filter);
        }

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
        Log.i("SMS pluginResult", pluginResult.toString());
    }

    private void stopOTPListener(){
        if(this.mSmsReceiver !=null){
            cordova.getActivity().unregisterReceiver(mSmsReceiver);
            Log.d("OTPAutoVerification", "stopOTPListener");
            this.mSmsReceiver = null;
            Log.d("SANDY Debugger", "stopOTPListener");
        }
    }
    /**
     * Getting the OTP from sms message body
     * ':' is the separator of OTP from the message
     *
     * @param message
     * @return
     */
    private String getVerificationCode(String message) {
        String code = null;
        int index = message.indexOf(OTP_DELIMITER);
        int endIndex;
        if(index != -1) {
            int start = index + OTP_DELIMITER.length()+1;
            if(OTP_END_DELIMITER == null) {
                code = message.substring(start);
            } else if(OTP_END_DELIMITER != null && OTP_END_DELIMITER.equals("")){
                code = message.substring(start);
            } else {
                endIndex = message.indexOf(OTP_END_DELIMITER);
                code = message.substring(start, endIndex);
            }
        }

        return code;
    }

    protected void getPermission(int requestCode)
    {
        cordova.requestPermission(this, requestCode, SMS_READ_PERMISSION);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException
    {
        for(int r:grantResults)
        {
            if(r == PackageManager.PERMISSION_DENIED)
            {
                Log.i("OTPAutoVerification", "SMS Permission Denied");
//                callbackContext.failure("User Denied the permission to read SMS");
                return;
            }
        }
        Log.i("OTPAutoVerification", "SMS Permission Granted");
        startOTPListener(options, callbackContext);
    }

}
