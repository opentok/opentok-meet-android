package com.opentok.android.meet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import com.opentok.android.VideoUtils;

import meet.android.opentok.com.opentokmeet.R;

public class Participant extends Subscriber {

    private static final String LOGTAG = "Participant";

    protected static final VideoUtils.Size VGA_VIDEO_RESOLUTION = new VideoUtils.Size(640, 480);
    protected static final VideoUtils.Size QVGA_VIDEO_RESOLUTION = new VideoUtils.Size(320, 240);
    protected static final int MAX_FPS = 30;
    protected static final int MID_FPS = 15;

    private final String  mName;
    private final ChatRoomActivity mActivity;
    private Boolean mSubscriberVideoOnly = false;

    public Participant(Context context, Stream stream) {
        super(context, stream);

        mActivity   = (ChatRoomActivity)context;
        mName       = "User" + ((int) (Math.random() * 1000));
        setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
    }

    public String getName() {
        return mName;
    }

    public Boolean getSubscriberVideoOnly() {
        return mSubscriberVideoOnly;
    }

    @Override
    public void onVideoDisabled(String reason) {
        super.onVideoDisabled(reason);
        Log.i(LOGTAG, "Video is disabled for the subscriber. Reason: " + reason);
        if (reason.equals("quality")) {
            mSubscriberVideoOnly = true;
        }
    }

    @Override
    public void onVideoEnabled(String reason) {
        super.onVideoEnabled(reason);
        Log.i(LOGTAG, "Subscriber is enabled:" + reason);

        if (reason.equals("quality")) {

            mSubscriberVideoOnly = false;
        }
    }

    @Override
    public void onVideoDisableWarning() {
        Log.i(LOGTAG,
                "Video may be disabled soon due to network quality degradation. Add UI handling here.");
    }

    @Override
    public void onVideoDisableWarningLifted() {
        Log.i(LOGTAG,
                "Video may no longer be disabled as stream quality improved. Add UI handling here.");
    }

    @Override
    protected void onVideoDataReceived() {
        super.onVideoDataReceived();
        Log.i(LOGTAG, "First frame received");
        mActivity.updateLoadingSub();
    }

    @Override
    protected void onError(OpentokError error) {
        super.onError(error);
        showErrorDialog(error);
    }

    private void showErrorDialog(OpentokError error) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mActivity);

        alertDialogBuilder.setTitle(R.string.error_title);
        alertDialogBuilder
                .setMessage(error.getMessage())
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mActivity.finish();
                    }
                });
        AlertDialog alertDialog = alertDialogBuilder.create();

        alertDialog.show();
    }

}