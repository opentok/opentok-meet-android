package com.opentok.android.meet;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;


import com.opentok.android.OpenTokConfig;
import com.opentok.android.Publisher;
import com.opentok.android.meet.services.ClearNotificationService;
import com.opentok.android.meet.services.ClearNotificationService.ClearBinder;

import meet.android.opentok.com.opentokmeet.R;


public class ChatRoomActivity extends Activity  {

    private static final String LOGTAG = ChatRoomActivity.class.getName();

    public static final String INTENT_APIKEY        = "api_key";
    public static final String INTENT_SESSION_ID    = "session_id";
    public static final String INTENT_SESSION_TOKEN = "session_token";
    public static final String INTENT_CAP_RESOLUTION= "capturer_resolution";
    public static final String INTENT_CAP_FPS       = "capturer_fps";
    public static final String INTENT_ROOM_NAME     = "room_name";
    public static final String INTENT_USER_NAME     = "user_name";
    public static final String INTENT_H264          = "h264";

    private static final Map<String, Publisher.CameraCaptureResolution> RESOLUTION_TBL =
            new HashMap<String, Publisher.CameraCaptureResolution>() {
        {
            put("Low", Publisher.CameraCaptureResolution.LOW);
            put("Medium", Publisher.CameraCaptureResolution.MEDIUM);
            put("High", Publisher.CameraCaptureResolution.HIGH);
        }
    };

    private static final Map<String, Publisher.CameraCaptureFrameRate> FRAMERATE_TBL =
            new HashMap<String, Publisher.CameraCaptureFrameRate>() {
        {
            put("1", Publisher.CameraCaptureFrameRate.FPS_1);
            put("7", Publisher.CameraCaptureFrameRate.FPS_7);
            put("15", Publisher.CameraCaptureFrameRate.FPS_15);
            put("30", Publisher.CameraCaptureFrameRate.FPS_30);
        }
    };

    private String mRoomName;
    private String mUsername;
    private String mApiKey;
    private String mSessionId;
    private String mSessionToken;
    private Publisher.CameraCaptureResolution mCapturerRes = Publisher.CameraCaptureResolution.MEDIUM;
    private Publisher.CameraCaptureFrameRate  mCapturerFps = Publisher.CameraCaptureFrameRate.FPS_30;

    private Room    mRoom;

    private NotificationCompat.Builder mNotifyBuilder;
    private NotificationManager mNotificationManager;
    private ServiceConnection mConnection;
    private boolean mIsBound = false;

    private ViewGroup mPreview;
    private ViewGroup mLastParticipantView;
    private LinearLayout mParticipantsView;
    private ProgressBar mLoadingSub; // Spinning wheel for loading subscriber view

    private String subsInfoStats = "SubInfoStat ";
    private String pubInfoStats;

    public ArrayList<String> statsInfo = new ArrayList<String>() {
        {
            add("CPU info stats are not available");
            add("Memory info stats are not available");
            add("Battery info stats are not available");
        }
    };

    private   ProgressDialog dialog;


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        /* setup UI */
        setContentView(R.layout.chat_room_layout);
        mPreview            = (ViewGroup)findViewById(R.id.publisherview);
        mParticipantsView   = (LinearLayout)findViewById(R.id.gallery);
        mLastParticipantView= (ViewGroup)findViewById(R.id.mainsubscriberView);
        mLoadingSub         = (ProgressBar)findViewById(R.id.loadingSpinner);
        /* setup action bar */
        if (null != getActionBar()) {
            ActionBar actionBar = getActionBar();
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayUseLogoEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowCustomEnabled(true);
            @SuppressLint("InflateParams")
            View cView = getLayoutInflater().inflate(R.layout.custom_title, null);
            actionBar.setCustomView(cView);
        }
        /* parse out intent data */
        final Intent intent = getIntent();
        mRoomName       = intent.getStringExtra(INTENT_ROOM_NAME);
        mUsername       = intent.getStringExtra(INTENT_USER_NAME);
        mApiKey         = intent.getStringExtra(INTENT_APIKEY);
        mSessionId      = intent.getStringExtra(INTENT_SESSION_ID);
        mSessionToken   = intent.getStringExtra(INTENT_SESSION_TOKEN);
        mCapturerRes    = RESOLUTION_TBL.get(intent.getStringExtra(INTENT_CAP_RESOLUTION));
        mCapturerFps    = FRAMERATE_TBL.get(intent.getStringExtra(INTENT_CAP_FPS));
        /* setup use of H264 & WebRTC MediaCodecFactory */
        OpenTokConfig.setPreferH264Codec(
                intent.getBooleanExtra(INTENT_H264, false)
        );
        /* update UI to reflect information received */
        ((TextView)findViewById(R.id.title)).setText(mRoomName);
        /* setup room */
        mRoom = new Room(this, mSessionId, mSessionToken, mApiKey, mUsername);
        mRoom.setPreviewView(mPreview);
        mRoom.setParticipantsViewContainer(mParticipantsView, mLastParticipantView, null);
        mRoom.setPublisherSettings(mCapturerRes, mCapturerFps);
        mRoom.connect();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Pause implies go to audio only mode
        if (mRoom != null) {
            mRoom.onPause();
        }

        //Add notification to status bar which gets removed if the user force kills the application.
        mNotifyBuilder = new NotificationCompat.Builder(this)
                .setContentTitle("Meet TokBox")
                .setContentText("Ongoing call")
                .setSmallIcon(R.mipmap.ic_launcher).setOngoing(true);

        Intent notificationIntent = new Intent(this, ChatRoomActivity.class);
        notificationIntent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        notificationIntent.putExtra(ChatRoomActivity.INTENT_ROOM_NAME, mRoomName);
        PendingIntent intent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        mNotifyBuilder.setContentIntent(intent);

        //Creates a service which removes the notification after application is forced closed.
        if (mConnection == null) {
            mConnection = new ServiceConnection() {

                public void onServiceConnected(ComponentName className, IBinder binder) {
                    ((ClearBinder) binder).service.startService(
                            new Intent(ChatRoomActivity.this, ClearNotificationService.class));
                    NotificationManager mNotificationManager
                            = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    mNotificationManager.notify(ClearNotificationService.NOTIFICATION_ID,
                            mNotifyBuilder.build());
                }

                public void onServiceDisconnected(ComponentName className) {
                    mConnection = null;
                }

            };
        }
        if (!mIsBound) {
            bindService(new Intent(ChatRoomActivity.this,
                            ClearNotificationService.class), mConnection,
                    Context.BIND_AUTO_CREATE);
            mIsBound = true;
            startService(notificationIntent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        //Resume implies restore video mode if it was enable before pausing app

        //If service is binded remove it, so that the next time onPause can bind service.
        if (mIsBound) {
            unbindService(mConnection);
            stopService(new Intent(ClearNotificationService.MY_SERVICE));
            mIsBound = false;
        }

        if (mRoom != null) {
            mRoom.onResume();
        }

        mNotificationManager.cancel(ClearNotificationService.NOTIFICATION_ID);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }

        if (isFinishing()) {
            mNotificationManager.cancel(ClearNotificationService.NOTIFICATION_ID);
            if (mRoom != null) {
                mRoom.disconnect();
            }
        }
    }

    @Override
    public void onDestroy() {
        mNotificationManager.cancel(ClearNotificationService.NOTIFICATION_ID);

        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }

        super.onDestroy();
    }

    public void onClickShareLink(View v) {
        String roomUrl = getResources().getString(R.string.serverURL) + mRoomName;
        String text = getString(R.string.sharingLink) + " " + roomUrl;
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");
        startActivity(sendIntent);
    }

    public void updateLoadingSub() {
        mRoom.loadSubscriberView();
    }

    //Show audio only icon when video quality changed and it is disabled for the last subscriber
    public void setAudioOnlyViewLastParticipant(boolean audioOnlyEnabled, Participant participant, View.OnClickListener clickLastParticipantListener) {
        if (audioOnlyEnabled) {
            this.mRoom.getLastParticipantView().removeView(participant.getView());
            View audioOnlyView = getAudioOnlyIcon();
            this.mRoom.getLastParticipantView().addView(audioOnlyView);
            audioOnlyView.setOnClickListener(clickLastParticipantListener);
        } else {
            this.mRoom.getLastParticipantView().removeAllViews();
            this.mRoom.getLastParticipantView().addView(participant.getView());
        }
    }

    public void setAudioOnlyViewListPartcipants (boolean audioOnlyEnabled, Participant participant, int index , View.OnClickListener clickListener) {

        final LinearLayout.LayoutParams lp = getQVGALayoutParams();

        if (audioOnlyEnabled) {
            this.mRoom.getParticipantsViewContainer().removeViewAt(index);
            View audioOnlyView = getAudioOnlyIcon();
            audioOnlyView.setTag(participant.getStream());
            audioOnlyView.setId(index);
            audioOnlyView.setOnClickListener(clickListener);
            this.mRoom.getParticipantsViewContainer().addView(audioOnlyView, index, lp);
        } else {
            this.mRoom.getParticipantsViewContainer().removeViewAt(index);
            this.mRoom.getParticipantsViewContainer().addView(participant.getView(), index, lp);
        }

    }

    public ProgressBar getLoadingSub() {
        return mLoadingSub;
    }

    //Convert dp to real pixels, according to the screen density.
    public int dpToPx(int dp) {
        double screenDensity = this.getResources().getDisplayMetrics().density;
        return (int) (screenDensity * (double) dp);
    }

    private ImageView getAudioOnlyIcon() {

        ImageView imageView = new ImageView(this);

        imageView.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imageView.setBackgroundResource(R.drawable.avatar_borders);
        imageView.setImageResource(R.mipmap.avatar);

        return imageView;
    }

    protected LinearLayout.LayoutParams getVGALayoutParams(){
        return new LinearLayout.LayoutParams(640, 480);
    }

    LinearLayout.LayoutParams getQVGALayoutParams(){
        return new LinearLayout.LayoutParams(480, 320);
    }

    LinearLayout.LayoutParams getMainLayoutParams(){
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    public View.OnLongClickListener onPubStatusClick = new View.OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            return false;
        }

    };


    public View.OnClickListener onPubViewClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            if (mRoom.getPublisher() != null) {
                if (mRoom.getPublisher().getPublishVideo()) {
                    mRoom.getPublisher().setPublishVideo(false);
                    View audioOnlyView = getAudioOnlyIcon();
                    audioOnlyView.setOnClickListener(this);
                    audioOnlyView.setOnLongClickListener(onPubStatusClick);
                    mPreview.removeAllViews();
                    mPreview.addView(audioOnlyView);
                }
                else {
                    mRoom.getPublisher().setPublishVideo(true);
                    mRoom.getPublisher().getView().setOnClickListener(this);
                    mRoom.getPublisher().getView().setOnLongClickListener(onPubStatusClick);
                    mPreview.addView(mRoom.getPublisher().getView());
                }
            }
            }

    };

    public void onStatsInfoClick(@SuppressWarnings("UnusedParameters") View v) {
            getPubStats();
            getSubStats();
            new AlertDialog.Builder(ChatRoomActivity.this)
                    .setTitle("Stats info")
                    .setMessage(statsInfo.get(0) + "\n" + statsInfo.get(1) + "\n" +statsInfo.get(2) + "\n" + subsInfoStats + "\n" +pubInfoStats)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // continue with delete
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
    }

    public void showReconnectingDialog(boolean show){
        if (show) {
            dialog = new ProgressDialog(this);
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setMessage("Reconnecting. Please wait...");
            dialog.setIndeterminate(true);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        } else {
            dialog.dismiss();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Session has been reconnected")
                    .setPositiveButton(android.R.string.ok, null);
            builder.create();
            builder.show();
        }
    }

    public void onCameraSwapClick(View view) {
        if (null != mRoom) {
            mRoom.getPublisher().cycleCamera();
        }
    }

    public void onEndCallClick(View view) {
        finish();
    }

    public void onPublisherMuteClick(View view) {
        if (null != mRoom) {
            if (mRoom.getPublisher().getPublishAudio()) {
                ((ImageButton)findViewById(R.id.mute_publisher)).setImageResource(
                        R.mipmap.mute_pub
                );
                mRoom.getPublisher().setPublishAudio(false);
            } else {
                ((ImageButton)findViewById(R.id.mute_publisher)).setImageResource(
                        R.mipmap.unmute_pub
                );
                mRoom.getPublisher().setPublishAudio(true);
            }
        }
    }

    private void getPubStats(){
        if(mRoom.getPublisher() != null) {
            String audioBytesSent, videoBytesSent;
            String videoFramerate, videoWidth, videoHeight;

            audioBytesSent = videoBytesSent = null;
            videoFramerate = videoWidth = videoHeight = null;

            Publisher mPublisher = mRoom.getPublisher();
            long[] videoStreams  = OpenTokConfig.getPublisherVideoStreams(mPublisher);
            long[] audioStreams = OpenTokConfig.getPublisherAudioStreams(mPublisher);

            if (audioStreams!= null && audioStreams.length > 0 ) {
                audioBytesSent = OpenTokConfig.getPublisherStat(mPublisher, audioStreams[0], "bytesSent");
            }
            if (videoStreams!=null && videoStreams.length > 0 ) {
                videoBytesSent = OpenTokConfig.getPublisherStat(mPublisher, videoStreams[0], "bytesSent");

                videoFramerate = OpenTokConfig.getPublisherStat(mPublisher, videoStreams[0], "googFrameRateSent");

                videoWidth = OpenTokConfig.getPublisherStat(mPublisher, videoStreams[0], "googFrameWidthSent");

                videoHeight = OpenTokConfig.getPublisherStat(mPublisher, videoStreams[0], "googFrameHeightSent");
            }

            pubInfoStats = "PubInfoStats -> "+ " audioBytesSent:"+audioBytesSent + " videoBytesSent:"+videoBytesSent+
                    "videoFps:"+videoFramerate + " width:"+videoWidth + " height:"+videoHeight;
        }
    }

    private void getSubStats(){
        Log.d(LOGTAG, "Method getSubStat got called.");
        subsInfoStats = "SubInfoStat ";
        if(mRoom.getParticipants() != null && mRoom.getParticipants().size() > 0) {
            for (int i=0; i<mRoom.getParticipants().size(); i++){
                String audioBytesReceived, videoBytesReceived;
                String videoWidth, videoHeight, videoFramerate;
                videoWidth = videoHeight = videoFramerate = null;
                audioBytesReceived = videoBytesReceived = null;

                Participant mParticipant = mRoom.getParticipants().get(i);

                long[] videoStreams  = OpenTokConfig.getSubscriberVideoStreams(mParticipant);
                long[] audioStreams = OpenTokConfig.getSubscriberAudioStreams(mParticipant);
                if (audioStreams!= null && audioStreams.length > 0 ) {
                    audioBytesReceived = OpenTokConfig.getSubscriberStat(mParticipant, audioStreams[0], "bytesReceived");
                }

                if (videoStreams!= null && videoStreams.length > 0 ) {
                    videoBytesReceived = OpenTokConfig.getSubscriberStat(mParticipant, videoStreams[0], "bytesReceived");

                    videoFramerate = OpenTokConfig.getSubscriberStat(mParticipant, videoStreams[0], "googFrameRateReceived");

                    videoWidth = OpenTokConfig.getSubscriberStat(mParticipant, videoStreams[0], "googFrameWidthReceived");

                    videoHeight = OpenTokConfig.getSubscriberStat(mParticipant, videoStreams[0], "googFrameHeightReceived");
                }


                String stats = mParticipant.getName() + "-> audioBytesReceived:"+audioBytesReceived + " videoBytesReceived:"+videoBytesReceived+
                        "videoFps:"+videoFramerate + " width:"+videoWidth + " height:"+videoHeight;

                Log.d(LOGTAG, stats);

                subsInfoStats = subsInfoStats + "; " + stats + "\n";
            }
            }

    }
}