package com.opentok.android.meet;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import meet.android.opentok.com.opentokmeet.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import com.opentok.android.OpenTokConfig;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.io.UnsupportedEncodingException;

public class HomeActivity extends Activity implements AdapterView.OnItemSelectedListener {
    private static final String LOGTAG = "meet.tokbox";

    private static final String PREF_TAG_CONFERENCE = "CONFERENCE_SETTINGS";
    private static final String PREF_ROOMNAME   = "room_name";
    private static final String PREF_USERNAME   = "user_name";
    private static final String PREF_CAPTURERRES= "capturer_res";
    private static final String PREF_CAPTURERFPS= "capturer_fps";
    private static final String PREF_H264       = "h264_support";

    private static final String PREF_TAG_DEBUG = "DEBUG_SETTINGS";
    private static final String PREF_JNI_LOG   = "enbale_jni_log";
    private static final String PREF_OTK_LOG   = "enable_otk_log";
    private static final String PREF_RTC_LOG   = "enable_webrtc_log";
    private static final String PREF_MEDIACODEC= "enable_webrtc_mediacodec";


    private String mRoomName;
    private String mUsername;
    private String mCapturerResolution;
    private String mCapturerFps;
    private boolean mH264Support;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setup/load views
        setContentView(R.layout.main_layout);
        if (getActionBar() != null) {
            ActionBar actionBar = getActionBar();
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayUseLogoEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            @SuppressLint("InflateParams")
            View cView = getLayoutInflater().inflate(R.layout.custom_home_title, null);
            actionBar.setCustomView(cView);
        }

        // setup action handlers
        ((Spinner)findViewById(R.id.combo_capturer_resolution)).setOnItemSelectedListener(this);
        ((Spinner)findViewById(R.id.combo_capturer_fps)).setOnItemSelectedListener(this);

        // restore last used conference settings
        restoreSettings();

        // update info if activity was launched by a url
        if (null != getIntent().getData()) {
            Uri url = getIntent().getData();
            if (url.getScheme().equals("otmeet")) {
                mRoomName = url.getHost();
            } else {
                mRoomName = url.getPathSegments().get(0);
            }
            // update ui
            ((EditText)findViewById(R.id.input_room_name)).setText(mRoomName);
        }
    }

    public void joinRoom(View v) {
        Log.i(LOGTAG, "join room button clicked.");

        AsyncTask<String, Void, Exception> fetchRoomData =
                new AsyncTask<String, Void, Exception>() {
            private Intent          mLaunchIntent   = null;
            private ProgressDialog  mCxnDialog      = null;

            private String _fetch(String room) throws IOException, NullPointerException {
                URL url = new URL(getResources().getString(R.string.serverURL) + room);
                HttpURLConnection cxn = (HttpURLConnection)url.openConnection();
                cxn.setRequestMethod("GET");
                cxn.setRequestProperty("Accept", "application/json, text/plain, */*");
                cxn.connect();
                InputStream inputStream = cxn.getInputStream();
                return IOUtils.toString(inputStream);
            }

            @Override
            protected void onPreExecute() {
                /* disable join button */
                ((Button)findViewById(R.id.button_join_room)).setEnabled(false);
                /* throw up connecting dialog */
                mCxnDialog = new ProgressDialog(HomeActivity.this);
                mCxnDialog.setTitle("Joining Room...");
                mCxnDialog.setMessage("Please wait.");
                mCxnDialog.setCancelable(false);
                mCxnDialog.setIndeterminate(true);
                mCxnDialog.show();
            }

            @Override
            protected Exception doInBackground(String... params) {
                try {
                    JSONObject roomJson    = new JSONObject(_fetch(params[0]));
                    /* create intent and launch room activity */
                    mLaunchIntent = new Intent(
                            HomeActivity.this,
                            ChatRoomActivity.class
                    );
                    mLaunchIntent.putExtra(
                            ChatRoomActivity.INTENT_APIKEY,
                            roomJson.getString("apiKey")
                    );
                    mLaunchIntent.putExtra(
                            ChatRoomActivity.INTENT_SESSION_ID,
                            roomJson.getString("sessionId")
                    );
                    mLaunchIntent.putExtra(
                            ChatRoomActivity.INTENT_SESSION_TOKEN,
                            roomJson.getString("token")
                    );
                    mLaunchIntent.putExtra(
                            ChatRoomActivity.INTENT_ROOM_NAME,
                            mRoomName
                    );
                    mLaunchIntent.putExtra(
                            ChatRoomActivity.INTENT_USER_NAME,
                            mUsername
                    );
                    mLaunchIntent.putExtra(
                            ChatRoomActivity.INTENT_CAP_RESOLUTION,
                            mCapturerResolution
                    );
                    mLaunchIntent.putExtra(
                            ChatRoomActivity.INTENT_CAP_FPS,
                            mCapturerFps
                    );
                    mLaunchIntent.putExtra(
                            ChatRoomActivity.INTENT_H264,
                            mH264Support
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(final Exception except) {
                /* re-enable join button */
                ((Button)findViewById(R.id.button_join_room)).setEnabled(true);
                /* dismiss connection dialog */
                mCxnDialog.dismiss();
                /* either launch video chat room activity or report error */
                if (null == except) {
                    saveSettings();
                    /* launch activity */
                    startActivity(mLaunchIntent);
                } else {
                    /* report error */
                    (new AlertDialog.Builder(HomeActivity.this))
                            .setTitle(R.string.error_title)
                            .setMessage(R.string.error)
                            .setCancelable(false)
                            .setPositiveButton(
                                    "OK",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            finish();
                                        }
                                    })
                            .create().show();
                }
            }
        };
        try {
            /* update values from ui */
            mRoomName = ((EditText) findViewById(R.id.input_room_name)).getText().toString();
            mUsername = ((EditText) findViewById(R.id.input_username)).getText().toString();
            mH264Support = ((Switch) findViewById(R.id.h264Support)).isChecked();
            /* save conference settings */
            saveSettings();
            /* set debug settings (setup from advanced settings menu) */
            updatePreferences();

            //Replace all leading spaces
            mRoomName = mRoomName.replaceAll("^\\s+", "");
            /* make sure there is a room name defined */
            mRoomName = encodeSpecialCharacters(mRoomName);


            if (!mRoomName.isEmpty()) {
            /* request conference information from server */
                fetchRoomData.execute(mRoomName);
            } else { 
                (Toast.makeText(this, "Room name must be specified", Toast.LENGTH_LONG)).show();
            }
        }
        catch (Exception e){
            (Toast.makeText(this, "Invalid String Cannot be Encoded to URL", Toast.LENGTH_LONG)).show();
        }
    }

    // Convert remaining string to Web supported UTF-8 with space replaced by %20
    // Matches behavior on webbrowser implementation.
    public String encodeSpecialCharacters (String s) throws UnsupportedEncodingException {
        String temp = URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        return temp;
    }

    private void saveSettings() {
        SharedPreferences.Editor editor     = null;
        /* Conference Settings */
        editor = this.getSharedPreferences(PREF_TAG_CONFERENCE, 0).edit();
        editor.putString(PREF_ROOMNAME, mRoomName);
        editor.putString(PREF_USERNAME, mUsername);
        editor.putString(PREF_CAPTURERRES, mCapturerResolution);
        editor.putString(PREF_CAPTURERFPS, mCapturerFps);
        editor.putBoolean(PREF_H264, mH264Support);
        editor.apply();
    }

    private void updatePreferences() {
        SharedPreferences settings = this.getSharedPreferences(PREF_TAG_DEBUG, 0);
        OpenTokConfig.setJNILogs(settings.getBoolean(PREF_JNI_LOG, false));
        OpenTokConfig.setOTKitLogs(settings.getBoolean(PREF_OTK_LOG, false));
        OpenTokConfig.setOTKitLogs(settings.getBoolean(PREF_RTC_LOG, false));
        OpenTokConfig.setUseMediaCodecFactories(settings.getBoolean(PREF_MEDIACODEC, false));
    }

    private void restoreSettings() {
        SharedPreferences settings = null;
        /* Conference Settings */
        settings = this.getSharedPreferences(PREF_TAG_CONFERENCE, 0);
        mRoomName           = settings.getString(PREF_ROOMNAME, "");
        mUsername           = settings.getString(PREF_USERNAME, "");
        mCapturerResolution = settings.getString(PREF_CAPTURERRES, "Medium");
        mCapturerFps        = settings.getString(PREF_CAPTURERFPS, "30");
        mH264Support        = settings.getBoolean(PREF_H264, true);
        /* restore room name/user name UI */
        ((EditText)findViewById(R.id.input_room_name)).setText(mRoomName);
        ((EditText)findViewById(R.id.input_username)).setText(mUsername);
        /* restore capturer resolution selection */
        String[] resolution_lst = getResources().getStringArray(R.array.pub_capturer_resolution);
        ((Spinner)findViewById(R.id.combo_capturer_resolution)).setSelection(
                Arrays.asList(resolution_lst).indexOf(mCapturerResolution)
        );
        /* restore capturer frame reate selection */
        String[] framerate_lst = getResources().getStringArray(R.array.pub_capturer_fps);
        ((Spinner)findViewById(R.id.combo_capturer_fps)).setSelection(
                Arrays.asList(framerate_lst).indexOf(mCapturerFps)
        );
        /* restore H264 support flag */
        ((Switch)findViewById(R.id.h264Support)).setChecked(mH264Support);
        /* Debug Preferences */
        updatePreferences();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.combo_capturer_resolution:
                mCapturerResolution = parent.getItemAtPosition(position).toString();
                break;
            case R.id.combo_capturer_fps:
                mCapturerFps = parent.getItemAtPosition(position).toString();
                break;
            default: break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    public void onAdvancedSettingsClick(@SuppressWarnings("UnusedParameters") View v) {
        Intent intent = new Intent(this, AdvancedSettingsActivity.class);
        startActivity(intent);
    }
}
