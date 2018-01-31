package com.aa.facet.dataSharingNfc;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.util.Log;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import com.aa.facet.dataSharingNfc.DataSharingNfcPlugin;
import com.aa.facet.dataSharingNfc.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NdefReaderTask extends AsyncTask<Tag, Void, String> {
    public static final String TAG = "NdefReaderTask";
    private NfcUiDataHandler nfcUiDataHandler;
    private static final String READ_NDEF_DATA_LISTENER = "readNdefDataListener";

    private Parcelable[] mParcelable;
    private Tag mTag;

    public NdefReaderTask(DataSharingNfcPlugin mainActivity, Tag tag, Parcelable[] parcelable) {
        this.nfcUiDataHandler = mainActivity;
        this.mTag = tag;
        this.mParcelable = parcelable;
    }

    String javaScriptEventTemplate =
            "var e = document.createEvent(''Events'');\n" +
                    "e.initEvent(''{0}'');\n" +
                    "e.tag = {1};\n" +
                    "document.dispatchEvent(e);";

    private String fireNdefEvent(String type, Ndef ndef, Parcelable[] messages) {
        JSONObject jsonObject = buildNdefJSON(ndef, messages);
        String tag = jsonObject.toString();

        String command = MessageFormat.format(javaScriptEventTemplate, type, tag);

        return command;
    }



    @Override
    protected String doInBackground(Tag... params) {
        Tag tag = this.mTag;
        Parcelable[] messages = this.mParcelable;

        Ndef ndef = Ndef.get(tag);

        if (ndef == null) {
            return null;
        }
 
        return fireNdefEvent(READ_NDEF_DATA_LISTENER, ndef, messages);
    }

     
    @Override
    protected void onPostExecute(String result) {
        if (result != null) {
            nfcUiDataHandler.printUi(result);
        }
    }

    JSONObject buildNdefJSON(Ndef ndef, Parcelable[] messages) {

        JSONObject json = Util.ndefToJSON(ndef);


        return json;
    }
}