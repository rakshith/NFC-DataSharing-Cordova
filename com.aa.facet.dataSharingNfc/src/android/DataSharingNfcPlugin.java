package com.aa.facet.dataSharingNfc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.nfc.Tag;
import android.os.Environment;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;
// using wildcard imports so we can support Cordova 3.x
// Cordova 3.x

public class DataSharingNfcPlugin extends CordovaPlugin implements NfcAdapter.OnNdefPushCompleteCallback, NfcUiDataHandler{

    private static final String TAG = "DataSharingNfcPlugin";

    private static final String SHOW_SETTINGS = "showNfcSettings";
    private static final String SHARE_DATA = "shareData";
    private static final String STOP_SHARE_DATA = "stopShareData";
    private static final String GET_NFC_STATUS = "getNfcStatus";
    private static final String STATUS_NFC_OK = "NFC_OK";
    private static final String STATUS_NO_NFC = "NO_NFC";
    private static final String STATUS_NFC_DISABLED = "NFC_DISABLED";
    private static final String STATUS_NDEF_PUSH_DISABLED = "NDEF_PUSH_DISABLED";
    private static final String RECEIVE_DATA = "receiveData";
    
    public static final String MIME_TEXT_PLAIN = "text/plain";

    private final List<IntentFilter> intentFilters = new ArrayList<IntentFilter>();

    private NdefMessage p2pMessage = null;

    private PendingIntent pendingIntent = null;

    private CallbackContext shareDataCallback;

    private NfcAdapter mNfcAdapter;



    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(cordova.getActivity());
    }

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, "execute " + action);

        final String finalAction = action;
        final CallbackContext finalCallbackContex = callbackContext;
        final JSONArray finalData = data;

        if(finalAction.equalsIgnoreCase(SHARE_DATA)){
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    shareData(finalData, finalCallbackContex);
                }
            });
        }else if(finalAction.equalsIgnoreCase(STOP_SHARE_DATA)){
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    stopNdefPush(finalCallbackContex);
                }
            });
        }else if (action.equalsIgnoreCase(SHOW_SETTINGS)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    showNfcSettings(finalCallbackContex);
                }
            });
        }else if (action.equalsIgnoreCase(RECEIVE_DATA)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    registerNdef(finalCallbackContex);
                }
            });
        }else if(finalAction.equalsIgnoreCase(GET_NFC_STATUS)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    getNfcStatus(finalCallbackContex);
                }
            });
        }else {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    finalCallbackContex.error(finalAction + " action not supported");
                }
            });

            return false;
        }

        return true;
    }
    private Activity getActivity() {
        return this.cordova.getActivity();
    }

    /**
     * @description
     * Intent to open the NFC and Android beam settings for enabling / disabling the NFC capability
     * @param callbackContext
     */
    private void showNfcSettings(CallbackContext callbackContext) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
                getActivity().startActivity(intent);
            } else {
                Intent intent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                getActivity().startActivity(intent);
            }
    }

    /**
     * Required to check NFC status from the Javascript
     * @param callbackContext
     * @return
     */
    private void getNfcStatus(CallbackContext callbackContext) {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
        if (nfcAdapter == null) {
            callbackContext.error(STATUS_NO_NFC);
        } else if (!nfcAdapter.isEnabled()) {
            callbackContext.error(STATUS_NFC_DISABLED);
        } if (!nfcAdapter.isNdefPushEnabled()) {
            callbackContext.error(STATUS_NDEF_PUSH_DISABLED);
        } else {
            callbackContext.success(STATUS_NFC_OK);
        }
    }

    /**
     * Required to check NFC Status from the java itself
     * @return
     */
    private String getNfcStatus() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
        if (nfcAdapter == null) {
            return STATUS_NO_NFC;
        } else if (!nfcAdapter.isEnabled()) {
            return STATUS_NFC_DISABLED;
        } if (!nfcAdapter.isNdefPushEnabled()) {
            return STATUS_NDEF_PUSH_DISABLED;
        } else {
            return STATUS_NFC_OK;
        }
    }

    /**
     * @description
     * register the listener for recieving the data from peer device
     * @param callbackContext
     */
    private void registerNdef(CallbackContext callbackContext){
            setupForegroundDispatch(getActivity(), mNfcAdapter);
            stopNdefPush(callbackContext);
    }

    /**
     * @description
     * convert the string data to NdefRecord format to transmit the data throught NFC channel
     * @param text
     * @param locale
     * @param encodeInUtf8
     * @return
     */
    public static NdefRecord newTextRecord(String text, Locale locale, boolean encodeInUtf8) {
        byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));

        Charset utfEncoding = encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
        byte[] textBytes = text.getBytes(utfEncoding);

        int utfBit = encodeInUtf8 ? 0 : (1 << 7);
        char status = (char) (utfBit + langBytes.length);

        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        data[0] = (byte) status;
        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);

        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);
    }

    /**
     * @description
     * before sharing the data compress it and then send compressed data, zip it wheather it is large or small.
     * @param string
     * @return
     * @throws IOException
     * @throws NoSuchPaddingException 
     * @throws NoSuchAlgorithmException 
     * @throws BadPaddingException 
     * @throws IllegalBlockSizeException 
     * @throws InvalidKeyException 
     */
    private static String compress(String string) throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(string.length());
        GZIPOutputStream gos = new GZIPOutputStream(os);
        gos.write(string.getBytes());
        gos.close();
        byte[] compressed = os.toByteArray();
        
        String encryptedData = EncryptByteArray(compressed, Util.ENCRYPTION_KEY.getBytes());
        
        os.close();
        return encryptedData;
    }
    
    private static String EncryptByteArray(byte[] array, byte[] key) throws IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return Base64.encodeToString(cipher.doFinal(array), Base64.DEFAULT);
    }
    
    /**
     * @description
     * Pass the actual data to NFC and build proper message format for sharing with the peer device
     * @param data
     * @param callbackContext
     */
    private void shareData(JSONArray data, CallbackContext callbackContext) {
            try {
                Log.d(TAG, "shareData called = " + data.getString(0));
                //NdefRecord[] records = Util.jsonToNdefRecords(data.getString(0));
                //this.p2pMessage = new NdefMessage(new NdefRecord[]{newTextRecord(Base64.encodeToString(data.getString(0), Base64.DEFAULT), Locale.ENGLISH, true)});
                String compressed = compress(data.getString(0));
                this.p2pMessage = new NdefMessage(new NdefRecord[] { newTextRecord(compressed, Locale.ENGLISH, true)});
                //this.p2pMessage = new NdefMessage(records);
                Log.d(TAG, "shareData called and record set");
                startNdefPush(callbackContext);
            }catch (JSONException e){
                Log.e(TAG, "share data failed = "+e.getMessage());
            }catch (IOException e1){
                Log.e(TAG, "compress  share data failed = "+e1.getMessage());
            } catch (Exception ex) {
            	Log.e(TAG, "Data excyprtion error "+ex.getMessage());
            	ex.printStackTrace();
            	
            	callbackContext.error("Data excyprtion error "+ ex.getMessage());
            }
    }

    /**
     * @description
     * init for the stopping ndef push, basically stoping the data sharing
     * @param callbackContext
     */
    private void stopNdefPush(final CallbackContext callbackContext) {
        p2pMessage = null;

        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                try {
                    NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

                    if (nfcAdapter != null) {
                        nfcAdapter.setNdefPushMessage(null, getActivity());
                    }
                    callbackContext.success();
                }catch(Exception e){
                    Log.e(TAG, "Stop NdefPush failed "+e.getMessage());
                    callbackContext.error(e.getMessage());
                }

            }
        });
    }

    /**
     * @description
     * init NdefPush, register callback to capture the event 'Message sent / message sending failed'
     * @param callbackContext
     */
    private void startNdefPush(final CallbackContext callbackContext) {

        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                Log.d(TAG, "startNdefPush called");
                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

                if (nfcAdapter == null) {
                    callbackContext.error(STATUS_NO_NFC);
                } else if (!nfcAdapter.isNdefPushEnabled()) {
                    callbackContext.error(STATUS_NDEF_PUSH_DISABLED);
                } else {
                    nfcAdapter.setNdefPushMessage(p2pMessage, getActivity());
                    nfcAdapter.setOnNdefPushCompleteCallback(DataSharingNfcPlugin.this, getActivity());

                    Log.d(TAG, "setNdefPushMessage called");

                    PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                    result.setKeepCallback(true);
                    shareDataCallback = callbackContext;
                    callbackContext.sendPluginResult(result);
                }
            }
        });
    }

    @Override
    public void onNdefPushComplete(NfcEvent event) {
        Log.d(TAG, "onNdefPushComplete called");
            PluginResult result = new PluginResult(PluginResult.Status.OK, "ShareCompleted");
            result.setKeepCallback(true);
            shareDataCallback.sendPluginResult(result);
    }

    @Override
    public void onNewIntent(Intent intent) {
        Log.i(TAG, "onNewIntent called" + intent );
        handleIntent(intent);
    }

    @Override
    public void onResume(boolean multitasking) {
        Log.d(TAG, "onResume called");
        super.onResume(multitasking);

        /**
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        setupForegroundDispatch(getActivity(), mNfcAdapter);
    }

    @Override
    public void onPause(boolean multitasking) {
        Log.d(TAG, "onPause called");

        stopForegroundDispatch(getActivity(), mNfcAdapter);

        super.onPause(multitasking);
    }

    /**
     * @param activity The corresponding {@link Activity} requesting to stop the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filters[0].addDataType(MIME_TEXT_PLAIN);
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }


    /**
     * @description
     * filter intent for NDEF's and extract the data from the intent
     * build proper data and send over to javascript in the form of commands to fire events,
     * events are handled in javascript
     * @param intent
     */
    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "handleIntent called" + intent );
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Log.i(TAG, "ACTION_NDEF_DISCOVERED" );
            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {
                Log.i(TAG, "MIME Type found" + type);
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                Parcelable[] messages = intent.getParcelableArrayExtra((NfcAdapter.EXTRA_NDEF_MESSAGES));
                new NdefReaderTask(this, tag, messages).execute((Tag)null);
            } else {
                Log.i(TAG, "Wrong mime type:" + type);
            }
        }
    }

    @Override
    public void printUi(String command) {
        Log.d(TAG, "Print ui " + command);
        if (command != null && !command.isEmpty()) {
            Log.v(TAG, command);
            this.webView.sendJavascript(command);
        }
    }
}