package com.reactlibrary;

import android.support.annotation.NonNull;

import org.webrtc.CameraEnumerationAndroid;

final class WebRTCCameraDeviceCandidate implements Comparable<WebRTCCameraDeviceCandidate> {
    @NonNull
    final String deviceName;
    @NonNull
    final CameraEnumerationAndroid.CaptureFormat format;
    final int score;

    WebRTCCameraDeviceCandidate(@NonNull final String deviceName,
                                @NonNull final CameraEnumerationAndroid.CaptureFormat format,
                                final int score) {
        this.deviceName = deviceName;
        this.format = format;
        this.score = score;
    }

    @Override
    public int compareTo(@NonNull WebRTCCameraDeviceCandidate o) {
        return score - ((WebRTCCameraDeviceCandidate) o).score;
    }
}