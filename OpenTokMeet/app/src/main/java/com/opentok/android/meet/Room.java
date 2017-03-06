package com.opentok.android.meet;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.profiler.PerformanceProfiler;

public class Room extends Session implements PerformanceProfiler.CPUStatListener, PerformanceProfiler.MemStatListener, PerformanceProfiler.BatteryStatListener{

    private static final String LOGTAG = "opentok-meet-room";

    private Context mContext;
    private String mToken;
    private Publisher mPublisher;
    private Participant mLastParticipant;
    private String mPublisherName = null;
    private HashMap<Stream, Participant> mParticipantStream = new HashMap<>();
    private HashMap<String, Participant> mParticipantConnection = new HashMap<>();
    private ArrayList<Participant> mParticipants = new ArrayList<>();

    private ViewGroup mPreview;
    private LinearLayout mParticipantsViewContainer;

    private ViewGroup mLastParticipantView;

    private Handler mHandler;

    private ChatRoomActivity mActivity;

    private PerformanceProfiler mProfiler;
    private int initialBatteryLevel = 0;

    public Room(Context context, String sessionId, String token, String apiKey, String username, boolean h264SupportEnabled) {
        super(context, apiKey, sessionId);
        mToken = token;
        mContext = context;
        mPublisherName = username;
        mHandler = new Handler(context.getMainLooper());
        mActivity = (ChatRoomActivity) this.mContext;

        mProfiler = new PerformanceProfiler(mContext);
        mProfiler.setCPUListener(this);
        mProfiler.setMemoryStatListener(this);
        mProfiler.setBatteryStatListener(this);
    }

    public void setParticipantsViewContainer(
            LinearLayout container,
            ViewGroup lastParticipantView,
            OnClickListener onSubscriberUIClick) {
        mParticipantsViewContainer  = container;
        mLastParticipantView        = lastParticipantView;
    }

    public void setPreviewView(ViewGroup preview) {
        mPreview = preview;
    }

    public void connect() {
        connect(mToken);
    }



    public Publisher getPublisher() {
        return mPublisher;
    }

    public Participant getLastParticipant() {
        return mLastParticipant;
    }

    public ArrayList<Participant> getParticipants() {
        return mParticipants;
    }

    public LinearLayout getParticipantsViewContainer() {
        return mParticipantsViewContainer;
    }

    public ViewGroup getLastParticipantView() {
        return mLastParticipantView;
    }

    @Override
    public void disconnect() {
        super.disconnect();
        if (mProfiler != null) {
            stopGetMetrics();
        }
    }

    private Publisher getCustomPublisher(){
        return new Publisher(
                mContext,
                "Android",
                mActivity.getCapturerResolutionPub(),
                mActivity.getCapturerFpsPub()
        );
    }

    @Override
    protected void onConnected() {
        //check simulcast case for publisher

        //mPublisher = new Publisher(mContext, "Android");
        mPublisher = getCustomPublisher();
        mPublisher.setName(mPublisherName);
        mPublisher.setAudioFallbackEnabled(true);
        mPublisher.setPublisherListener(new PublisherKit.PublisherListener() {
            @Override
            public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
                Log.d(LOGTAG, "onStreamCreated!!");
            }

            @Override
            public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {
                Log.d(LOGTAG, "onStreamDestroyed!!");
            }

            @Override
            public void onError(PublisherKit publisherKit, OpentokError opentokError) {
                Log.d(LOGTAG, "onError!!");
            }
        });

        publish(mPublisher);

        // Add video preview
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        SurfaceView v = (SurfaceView) mPublisher.getView();
        v.setZOrderOnTop(true);

        mPreview.addView(v, lp);
        mPublisher.getView().setOnClickListener(mActivity.onPubViewClick);
        mPublisher.getView().setOnLongClickListener(mActivity.onPubStatusClick);
        mPublisher.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
                BaseVideoRenderer.STYLE_VIDEO_FILL);

        startGetMetrics();

    }

    @Override
    protected void onReconnecting() {
        super.onReconnecting();
        mActivity.showReconnectingDialog(true);
    }

    @Override
    protected void onReconnected() {
        super.onReconnected();
        mActivity.showReconnectingDialog(false);
    }

    @Override
    protected void onStreamReceived(Stream stream) {

        Participant p = new Participant(mContext, stream);

        if (mParticipants.size() > 0) {
            final Participant lastParticipant = mParticipants.get(mParticipants.size() - 1);
            this.mLastParticipantView.removeView(lastParticipant.getView());

            final LinearLayout.LayoutParams lp = mActivity.getQVGALayoutParams();
            lastParticipant.setPreferredResolution(Participant.QVGA_VIDEO_RESOLUTION);
            lastParticipant.setPreferredFrameRate(Participant.MID_FPS);
            this.mParticipantsViewContainer.addView(lastParticipant.getView(), lp);
            lastParticipant.setSubscribeToVideo(true);
            lastParticipant.getView().setOnLongClickListener(longClickListener);
            lastParticipant.getView().setOnClickListener(clickListener);
        }

        mActivity.getLoadingSub().setVisibility(View.VISIBLE);
        p.setPreferredResolution(Participant.VGA_VIDEO_RESOLUTION);
        p.setPreferredFrameRate(Participant.MAX_FPS);
        p.getView().setOnClickListener(clickLastParticipantListener);
        mLastParticipant = p;

        //Subscribe to this participant
        this.subscribe(p);

        mParticipants.add(p);
        p.getView().setTag(stream);

        mParticipantStream.put(stream, p);
        mParticipantConnection.put(stream.getConnection().getConnectionId(), p);
    }

    @Override
    protected void onStreamDropped(Stream stream) {
        Participant p = mParticipantStream.get(stream);
        if (p != null) {

            mParticipants.remove(p);
            mParticipantStream.remove(stream);
            mParticipantConnection.remove(stream.getConnection().getConnectionId());

            mLastParticipant = null;

            int index = mParticipantsViewContainer.indexOfChild(p.getView());
            if ( index != -1 ) {
                mParticipantsViewContainer.removeViewAt(index);
            }
            else {
                mLastParticipantView.removeView(p.getView());
                if (mParticipants.size() > 0 ) {
                    //add last participant to this view
                    Participant currentLast = mParticipants.get(mParticipants.size() - 1);
                    mParticipantsViewContainer.removeView(currentLast.getView());
                    mLastParticipantView.addView(currentLast.getView(), mActivity.getMainLayoutParams());
                }
            }
        }

    }


    @Override
    protected void onError(OpentokError error) {
        super.onError(error);
        Toast.makeText(this.mContext, error.getMessage(), Toast.LENGTH_SHORT).show();
        mProfiler = null;
    }

    public void loadSubscriberView() {
        //stop loading spinning
        if (mActivity.getLoadingSub().getVisibility() == View.VISIBLE) {
            mActivity.getLoadingSub().setVisibility(View.GONE);

            this.mLastParticipantView.addView(mLastParticipant.getView());
        }
    }

    private void swapSubPriority(View view){
        int index = this.mParticipantsViewContainer.indexOfChild(view);

        //the last participant view will go to the index
        this.mParticipantsViewContainer.removeView(view);
        this.mLastParticipantView.removeView(mLastParticipant.getView());

        //update lastParticipant view
        LinearLayout.LayoutParams lp = mActivity.getMainLayoutParams();
        Participant currentSelected = mParticipantStream.get(view.getTag());
        currentSelected.setPreferredResolution(Participant.VGA_VIDEO_RESOLUTION);
        currentSelected.setPreferredFrameRate(Participant.MAX_FPS);
        currentSelected.getView().setOnClickListener(clickLastParticipantListener);
        currentSelected.getView().setOnLongClickListener(null);
        this.mLastParticipantView.addView(currentSelected.getView(), lp);

        lp = mActivity.getQVGALayoutParams();
        mLastParticipant.getView().setOnClickListener(clickListener);
        mLastParticipant.getView().setOnLongClickListener(longClickListener);
        mLastParticipant.setPreferredResolution(Participant.QVGA_VIDEO_RESOLUTION);
        mLastParticipant.setPreferredFrameRate(Participant.MID_FPS);
        this.mParticipantsViewContainer.addView(mLastParticipant.getView(), index, lp);

        mLastParticipant = currentSelected;

    }

    View.OnLongClickListener longClickListener = new View.OnLongClickListener() {
        public boolean onLongClick(View view) {
            if (!view.equals(mLastParticipantView)){
                swapSubPriority(view);
            }

            return true;
        }
    };

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            Participant participant = mParticipantStream.get(view.getTag());

            boolean enableAudioOnly = participant.getSubscribeToVideo();

            if (enableAudioOnly) {
                participant.setSubscribeToVideo(false);
            } else {
                participant.setSubscribeToVideo(true);
            }
        int index = mParticipantsViewContainer.indexOfChild(participant.getView());
        if (index == -1) {
            index = view.getId();
        }
        mActivity.setAudioOnlyViewListPartcipants(enableAudioOnly, participant, index, this);

        }
    };

    private View.OnClickListener clickLastParticipantListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            boolean enableAudioOnly = mLastParticipant.getSubscribeToVideo();
            if (enableAudioOnly) {
                mLastParticipant.setSubscribeToVideo(false);
            } else {
                mLastParticipant.setSubscribeToVideo(true);
            }
            mActivity.setAudioOnlyViewLastParticipant(enableAudioOnly, mLastParticipant, this);
        }
    };


    private void startGetMetrics(){
        mProfiler.startBatteryMetrics();

        //start cpu profiling
        mProfiler.startCPUMetrics();

        //start mem profiling
        mProfiler.startMemMetrics();

    }

    private void stopGetMetrics(){
        mProfiler.stopBatteryMetrics();

        //start cpu profiling
        mProfiler.stopCPUMetrics();

        //start mem profiling
        mProfiler.stopMemMetrics();

    }

    @Override
    public void onCPU(float totalCpu, float pidCpu) {
        Log.d(LOGTAG, "cpu values total " + totalCpu + "% process:" + pidCpu+"%");
        DecimalFormat df = new DecimalFormat("##.##");

        mActivity.statsInfo.set(0, "CPU stats. TotalCPU:  " + df.format(totalCpu) + "% PidCPU: "+ df.format(pidCpu)+"%");
    }

    @Override
    public void onMemoryStat(double available_mem, double total_mem, double used_mem, double used_per) {
        Log.d(LOGTAG, "available mem: " + available_mem + "total_mem:" + total_mem + " used_mem: "+ used_mem + " used_per: " + used_per +"%");
        DecimalFormat df = new DecimalFormat("####.##");

        mActivity.statsInfo.set(1, "Memory stats. UsedMem: " + df.format(used_mem) +" UsedMem per: "+ df.format(used_per) + "%");
    }

    @Override
    public void onBatteryStat(int level, int scale, float batteryPer) {
        Log.d(LOGTAG, "Battery level: " + level + "scale:" + scale + " batteryPer: " + batteryPer);

        if (initialBatteryLevel == 0) {
            initialBatteryLevel = level;
        }
        mActivity.statsInfo.set(2, "Battery stats. Battery consume: "+ (initialBatteryLevel-level)+"%");
    }
}
