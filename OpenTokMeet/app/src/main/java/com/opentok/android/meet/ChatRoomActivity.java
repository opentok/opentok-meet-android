package com.opentok.android.meet;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

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
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;


import com.opentok.android.OpenTokConfig;
import com.opentok.android.Publisher;
import com.opentok.android.meet.services.ClearNotificationService;
import com.opentok.android.meet.services.ClearNotificationService.ClearBinder;

import meet.android.opentok.com.opentokmeet.R;


public class ChatRoomActivity extends Activity  {

    private static final String LOGTAG = ChatRoomActivity.class.getName();

    public static final String ARG_ROOM_ID = "roomId";
    public static final String ARG_USERNAME_ID = "usernameId";
    public static final String PUB_CAPTURER_RESOLUTION= "PUB_CAPTURER_RESOLUTION";
    public static final String PUB_CAPTURER_FPS= "PUB_CAPTURER_FPS";

    private String serverURL = null;
    private String mRoomName;
    private Room mRoom;
    private String mUsername = null;

    private Publisher.CameraCaptureResolution mCapturerResolutionPub = Publisher.CameraCaptureResolution.MEDIUM;
    private Publisher.CameraCaptureFrameRate mCapturerFpsPub = Publisher.CameraCaptureFrameRate.FPS_30;

    private ProgressDialog mConnectingDialog;

    private Handler mHandler = new Handler();
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

    public ArrayList<String> statsInfo = new ArrayList<>() ;
    private   ProgressDialog dialog;


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.chat_room_layout);

        // customize title bar (Lint warns if actionbar is null although that should never happen)
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

        mPreview = (ViewGroup) findViewById(R.id.publisherview);
        mParticipantsView = (LinearLayout) findViewById(R.id.gallery);
        mLastParticipantView = (ViewGroup) findViewById(R.id.mainsubscriberView);
        mLoadingSub = (ProgressBar) findViewById(R.id.loadingSpinner);

        Uri url = getIntent().getData();
        serverURL = getResources().getString(R.string.serverURL);

        if (url == null) {
            mRoomName = getIntent().getStringExtra(ARG_ROOM_ID);
            mUsername = getIntent().getStringExtra(ARG_USERNAME_ID);
        } else {
            if (url.getScheme().equals("otmeet")) {
                mRoomName = url.getHost();
            } else {
                mRoomName = url.getPathSegments().get(0);
            }
        }

        TextView title = (TextView) findViewById(R.id.title);
        title.setText(mRoomName);

        statsInfo.add("CPU info stats are not available");
        statsInfo.add("Memory info stats are not available");
        statsInfo.add("Battery info stats are not available");

        String resolution =  getIntent().getStringExtra(PUB_CAPTURER_RESOLUTION);
        mCapturerResolutionPub = getPubCapturerResolution(resolution);

        String framerate =  getIntent().getStringExtra(PUB_CAPTURER_FPS);
        mCapturerFpsPub = getPubCapturerFrameRate(framerate);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        initializeRoom();

    }

    private Publisher.CameraCaptureResolution getPubCapturerResolution(String resolution) {
        if (resolution.contains("Low")) {
            return Publisher.CameraCaptureResolution.LOW;
        } else if (resolution.contains("Medium")) {
            return Publisher.CameraCaptureResolution.MEDIUM;
        } else {
            return Publisher.CameraCaptureResolution.HIGH;
        }
    }

    private Publisher.CameraCaptureFrameRate getPubCapturerFrameRate(String fps) {
        if (fps.contains("FPS_1")) {
            return Publisher.CameraCaptureFrameRate.FPS_1;
        } else if (fps.contains("FPS_7")) {
            return Publisher.CameraCaptureFrameRate.FPS_7;
        } else if (fps.contains("FPS_15")) {
            return Publisher.CameraCaptureFrameRate.FPS_15;
        } else {
            return Publisher.CameraCaptureFrameRate.FPS_30;
        }
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

        //Pause implies go to audio only mode
        if (mRoom != null) {
            mRoom.onPause();
        }

        //Add notification to status bar which gets removed if the user force kills the application.
        mNotifyBuilder = new NotificationCompat.Builder(this)
                .setContentTitle("Meet TokBox")
                .setContentText("Ongoing call")
                .setSmallIcon(R.mipmap.ic_launcher).setOngoing(true);

        Intent notificationIntent = new Intent(this, ChatRoomActivity.class);
        notificationIntent
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra(ChatRoomActivity.ARG_ROOM_ID, mRoomName);
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

    private void initializeRoom() {
        Log.i(LOGTAG, "initializing chat room fragment for room: " + mRoomName);
        setTitle(mRoomName);

        //Show connecting dialog
        mConnectingDialog = new ProgressDialog(this);
        mConnectingDialog.setTitle("Joining Room...");
        mConnectingDialog.setMessage("Please wait.");
        mConnectingDialog.setCancelable(false);
        mConnectingDialog.setIndeterminate(true);
        mConnectingDialog.show();

        GetRoomDataTask task = new GetRoomDataTask();
        task.execute(mRoomName, mUsername);
    }

    private class GetRoomDataTask extends AsyncTask<String, Void, Room> {

        private String getRoomDetails(String room) throws IOException {
            URL url = new URL(getResources().getString(R.string.serverURL) + room);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.connect();
            InputStream inputStream = connection.getInputStream();
            try {
                return IOUtils.toString(inputStream);
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected Room doInBackground(String... params) {
            try {
                JSONObject  roomJson    = new JSONObject(getRoomDetails(params[0]));
                String      sessionId   = roomJson.getString("sessionId");
                String      token       = roomJson.getString("token");
                String      apiKey      = roomJson.getString("apiKey");
                return new Room(ChatRoomActivity.this, sessionId, token, apiKey, params[1]);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(final Room room) {
            mConnectingDialog.dismiss();
            if (room != null) {
                mRoom = room;
                mRoom.setPreviewView(mPreview);
                mRoom.setParticipantsViewContainer(mParticipantsView, mLastParticipantView, null);
                mRoom.connect();
            } else {
                mConnectingDialog = null;
                showErrorDialog();
            }
        }
    }

    private DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
            finish();
        }
    };

    private void showErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.error_title);
        builder.setMessage(R.string.error);
        builder.setCancelable(false);
        builder.setPositiveButton("OK", errorListener);
        builder.create().show();
    }

    public void onClickShareLink(View v) {
        String roomUrl = serverURL + mRoomName;
        String text = getString(R.string.sharingLink) + " " + roomUrl;
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");
        startActivity(sendIntent);
    }

    public Room getRoom() {
        return mRoom;
    }

    public Handler getHandler() {
        return this.mHandler;
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

    public Publisher.CameraCaptureResolution getCapturerResolutionPub() {
        return mCapturerResolutionPub;
    }

    public Publisher.CameraCaptureFrameRate getCapturerFpsPub() {
        return mCapturerFpsPub;
    }

    public void showReconnectingDialog(boolean show){
        if (show) {
            dialog = new ProgressDialog(this);
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setMessage("Reconnecting. Please wait...");
            dialog.setIndeterminate(true);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }
        else {
            dialog.dismiss();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Session has been reconnected")
                    .setPositiveButton(android.R.string.ok, null);
            builder.create();
            builder.show();
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