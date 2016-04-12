package com.opentok.android;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * This class contains API's, which are only used Internally.
 */
public class OpenTokConfig {
    static {
        System.loadLibrary("opentok");
    }

    private final static String LOG_TAG = "opentok-config";

    public static void setAPIRootURL(String apiRootURL) throws MalformedURLException {
        URL url = new URL(apiRootURL);
        boolean ssl     = ("https".equals(url.getProtocol()));
        int     port    = (-1 == url.getPort()) ? ((ssl) ? 443 : 80) : url.getPort();
        setAPIRootURLNative(url.getHost(), ssl, port, true);
    }

    public static void setOTKitLogs(boolean otkitLogs) {
        setOTKitLogsNative(otkitLogs);
    }

    public static void setJNILogs(boolean jniLogs) {
        setJNILogsNative(jniLogs);
    }

    public static void setWebRTCLogs(boolean webrtcLogs) {
        setWebRTCLogsNative(webrtcLogs);
    }

    public static String getPublisherInfoStats(PublisherKit publisher) {
        return getPublisherInfoStatsNative(publisher);
    }

    public static String getSubscriberInfoStats(SubscriberKit subscriber) {
        return getSubscriberInfoStatsNative(subscriber);
    }

    public static String getPublisherStat(PublisherKit publisher, long stream, String key){
        return getPublisherStatNative(publisher, stream, key);
    }

    public static long[] getPublisherAudioStreams(PublisherKit publisher){
        return getPublisherAudioStreamsNative(publisher);
    }

    public static long[] getPublisherVideoStreams(PublisherKit publisher){
        return getPublisherVideoStreamsNative(publisher);
    }

    public static String getSubscriberStat(SubscriberKit subscriber, long stream, String key) {
        return getSubscriberStatNative(subscriber, stream, key);
    }

    public static long[] getSubscriberAudioStreams(SubscriberKit subscriber){
        return getSubscriberAudioStreamsNative(subscriber);
    }

    public static long[] getSubscriberVideoStreams(SubscriberKit subscriber){
        return getSubscriberVideoStreamsNative(subscriber);
    }

    public static String getSDKVersion(Session session){
        return getSDKVersionNative(session);
    }

    protected static native void setAPIRootURLNative(String host, boolean ssl, int port, boolean rumorSSL);
    protected static native void setOTKitLogsNative(boolean otkitLogs);
    protected static native void setJNILogsNative(boolean jniLogs);
    protected static native void setWebRTCLogsNative(boolean webrtcLogs);

    protected static native String getPublisherInfoStatsNative(PublisherKit publisher);
    protected static native String getSubscriberInfoStatsNative(SubscriberKit subscriber);
    protected static native String getPublisherStatNative(PublisherKit publisher,long stream, String key);
    protected static native long[] getPublisherAudioStreamsNative(PublisherKit publisher);
    protected static native long[] getPublisherVideoStreamsNative(PublisherKit publisher);
    protected static native long[] getSubscriberAudioStreamsNative(SubscriberKit subscriber);
    protected static native long[] getSubscriberVideoStreamsNative(SubscriberKit subscriber);
    protected static native String getSubscriberStatNative(SubscriberKit subscriber, long stream, String key);
    protected static native String getSDKVersionNative(Session session);
}