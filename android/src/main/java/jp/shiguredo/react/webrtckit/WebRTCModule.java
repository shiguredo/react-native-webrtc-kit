package jp.shiguredo.react.webrtckit;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.Metrics;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.DataChannel;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static jp.shiguredo.react.webrtckit.WebRTCConverter.dataChannelBuffer;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.dataChannelInit;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.dataChannelJsonValue;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.iceCandidate;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.mediaConstraints;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.mediaStreamTrackJsonValue;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.rtcConfiguration;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.rtpSenderJsonValue;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.rtpTransceiverJsonValue;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.rtpTransceiverInit;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.rtpTransceiverDirection;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.rtpTransceiverDirectionStringValue;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.sessionDescription;
import static jp.shiguredo.react.webrtckit.WebRTCConverter.toStringList;

@ReactModule(name = "WebRTCModule")
public class WebRTCModule extends ReactContextBaseJavaModule {

    @NonNull
    private final ReactApplicationContext reactContext;
    @NonNull
    private final EglBase eglBase;
    @NonNull
    private final PeerConnectionFactory peerConnectionFactory;
    @NonNull
    private final WebRTCCamera cameraCapturer;
    /**
     * XXX: Maybe we should move the surfaceTextureHelper to the WebRTCCamera so that the camera module handles all capturing tasks instead of this module
     */
    @NonNull
    private final SurfaceTextureHelper surfaceTextureHelper;
    @NonNull
    final WebRTCRepository repository = new WebRTCRepository();

    @NonNull public ReactApplicationContext getReactContext() {
        return reactContext;
    }

    public WebRTCModule(@NonNull final ReactApplicationContext reactContext) {
        super(reactContext);

        // PeerConnectionFactory自体を最初に初期化する必要がある
        final PeerConnectionFactory.InitializationOptions pcfInitializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(reactContext)
                        .setEnableInternalTracer(false)
                        .setFieldTrials("")
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(pcfInitializationOptions);

        // WebRTCのロギングを有効化する
        // XXX: 不要になったら削除するかも、またはデバッグビルドでのみ有効にする必要があるかも
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);

        // 各フィールドを初期化
        // XXX: DefaultVideoEncoderFactory - VP8 encoder / H264 の利用可否を適切に判断する
        //      現在のところは決め打ちで両方有効にしているが、ハードウェアによっては利用できない場合がある
        //      RN経由でユーザーから調整可能にしてもよいが、可能であればここで利用可否を判断できるのが望ましい
        this.reactContext = reactContext;
        this.eglBase = EglBase.create();
        this.peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(getEglContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(getEglContext()))
                .createPeerConnectionFactory();
        this.cameraCapturer = new WebRTCCamera();
        this.surfaceTextureHelper = SurfaceTextureHelper.create("WebRTCCameraCaptureThread", getEglContext());
    }


    //region ReactContextBaseJavaModule

    @Override
    public String getName() {
        return "WebRTCModule";
    }

    @Override
    public void initialize() {
        // Do nothing
        // Android版のReact Nativeはリロード時などにNative Moduleインスタンスを使い回さず新たに作り直すため、
        // 通常のコンストラクタで問題なく動作する。
        Log.d(getName(), "initialize()");
    }

    @Override
    public void onCatalystInstanceDestroy() {
        /* Native Moduleが破棄される直前に呼び出される (要するにJSレイヤーがリロードされるタイミング)
         * ここでリロード前の古い RTCPeerConnection の終了処理を行わないと、
         * RTCPeerConnection の接続が残ったままになってしまう。
         * Android版のReact Nativeはリロード時などにNative Moduleインスタンスを使い回さず新たに作り直すため、
         * finishLoading()は古いインスタンスではなく新しいインスタンスで呼び出されてしまい、うまくいかない。
         */
        Log.d(getName(), "onCatalystInstanceDestroy()");
        cameraCapturer.stopCapture();

        // PeerConnection.dispose()を実施するとそのPeerConnectionが内部で持っているすべてのオブジェクトを破棄するので、
        // 同時にSender, Receiver, Streamなども適切に破棄される。
        for (final PeerConnection peerConnection : repository.allPeerConnections()) {
            peerConnection.dispose();
        }
        repository.clear();

        peerConnectionFactory.dispose();
        surfaceTextureHelper.dispose();
        eglBase.release();
    }

    //endregion


    //region ReactMethod

    /**
     * JS レイヤーがロードまたはリロードされたときに呼ばれます。
     * ネイティブ実装の内部状態をクリーンアップするために使用されます。
     */
    @ReactMethod
    public void finishLoading() {
        // Do nothing
        // Android版はこの仕組みではなくNative Moduleの仕組みを利用して初期化とクリーンアップを行う
        Log.d(getName(), "finishLoading()");
    }

    @ReactMethod
    public void enableMetrics() {
        Log.d(getName(), "enableMetrics()");
        Metrics.enable();
    }

    /**
     * getAndResetMetrics(): Promise<Array<RTCMetricsSampleInfo>>
     */
    @ReactMethod
    public void getAndResetMetrics(@NonNull final Promise promise) {
        Log.d(getName(), "getAndResetMetrics()");
        final Metrics metrics = Metrics.getAndReset();
        final List<Map<String, Object>> results = new ArrayList<>();
        for (final String infoName : metrics.map.keySet()) {
            final Metrics.HistogramInfo info = metrics.map.get(infoName);
            final Map<String, Object> infoMap = new HashMap<>();
            infoMap.put("name", infoName);
            infoMap.put("min", info.min);
            infoMap.put("max", info.max);
            infoMap.put("bucketCount", info.bucketCount);
            infoMap.put("samples", info.samples);
            results.add(infoMap);
        }
        promise.resolve(results);
    }

    /**
     * This feature is not supported by the Android SDK.
     * getAudioPort(): Promise<RTCAudioPort>
     */
    @ReactMethod
    public void getAudioPort(@NonNull final Promise promise) {
        Log.d(getName(), "getAudioPort()");
        promise.resolve("none");
    }

    /**
     * This feature is not supported by the Android SDK.
     * setAudioPort(port: RTCAudioPort): Promise<void>
     */
    @ReactMethod
    public void setAudioPort(@Nullable final String port, @NonNull final Promise promise) {
        Log.d(getName(), "setAudioPort()");
        promise.resolve(null);
    }

    /**
     * getUserMedia(constraints: RTCMediaStreamConstraints): Promise<Object>
     */
    @ReactMethod
    public void getUserMedia(@Nullable final ReadableMap constraintsJson, @NonNull final Promise promise) {
        Log.d(getName(), "getUserMedia() - constraints=" + constraintsJson);
        final WebRTCMediaStreamConstraints constraints = new WebRTCMediaStreamConstraints(constraintsJson);
        final boolean isVideoEnabled = (constraints.video != null);
        final boolean isAudioEnabled = (constraints.audio != null);

        final WebRTCCameraDeviceCandidate deviceCandidate;
        final VideoCapturer videoCapturer;
        if (isVideoEnabled) {
            // カメラとマイクを起動する
            // libwebrtc でカメラを起動すると自動的にマイクも起動される
            // そのため、音声のみ必要な場合でもカメラを起動する必要がある
            deviceCandidate = cameraCapturer.getSuitableDeviceCandidate(constraints.video);
            if (deviceCandidate == null) {
                promise.reject("NotFoundError", "No suitable camera device is found for the given facing mode.");
                return;
            }
            videoCapturer = cameraCapturer.createCapturer(deviceCandidate);
        } else {
            // 映像が不要の場合でも、マイクを起動するためにカメラを起動しておく
            // その場合は後々ストリームから映像トラックを外す
            deviceCandidate = null;
            videoCapturer = cameraCapturer.createCapturerFromAllDevices();
        }

        // カメラ用のトラックを持つストリームを生成する
        // このストリームを管理する必要はなく、
        // ストリーム ID のみ getUserMedia に渡せればよい
        final MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream(createNewValueTag());

        // 映像と音声のトラックをストリームに追加する
        final VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, reactContext, videoSource.getCapturerObserver());
        final VideoTrack videoTrack = peerConnectionFactory.createVideoTrack(createNewValueTag(), videoSource);
        final AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        final AudioTrack audioTrack = peerConnectionFactory.createAudioTrack(createNewValueTag(), audioSource);

        repository.tracks.add(videoTrack.id(), createNewValueTag(), videoTrack);
        repository.tracks.add(audioTrack.id(), createNewValueTag(), audioTrack);
        mediaStream.addTrack(videoTrack);
        mediaStream.addTrack(audioTrack);

        // constraints の指定に従ってトラックの可否を決める
        videoTrack.setEnabled(isVideoEnabled);
        audioTrack.setEnabled(isAudioEnabled);

        // アスペクト比の設定と、カメラデバイスのキャプチャ開始
        // XXX: キャプチャ開始はlocal stream追加まで待ったほうがいいかもしれないけど、ここではiOS版に揃えて即開始します。ダメそうなら待つように実装を修正する。
        if (isVideoEnabled) {
            repository.setVideoTrackAspectRatio(videoTrack, constraints.video.aspectRatio);
            cameraCapturer.stopCapture();
            cameraCapturer.startCapture(videoSource, videoCapturer, deviceCandidate, constraints.video);
        }

        // JS に処理を戻す
        final WritableMap result = Arguments.createMap();
        result.putString("streamId", mediaStream.getId());
        final WritableArray tracks = Arguments.createArray();
        tracks.pushMap(mediaStreamTrackJsonValue(videoTrack, repository));
        tracks.pushMap(mediaStreamTrackJsonValue(audioTrack, repository));
        result.putArray("tracks", tracks);
        promise.resolve(result);
    }

    @ReactMethod
    public void stopUserMedia() {
        Log.d(getName(), "stopUserMedia()");
        cameraCapturer.stopCapture();
    }

    /**
     * trackSetEnabled(valueTag: ValueTag, enabled: boolean)
     */
    @ReactMethod
    public void trackSetEnabled(boolean isEnabled, @NonNull String valueTag) {
        Log.d(getName(), "trackSetEnabled()");
        final MediaStreamTrack track = repository.tracks.getByValueTag(valueTag);
        if (track == null) return;
        track.setEnabled(isEnabled);
    }

    /**
     * trackSetAspectRatio(valueTag: ValueTag, aspectRatio: number)
     */
    @ReactMethod
    public void trackSetAspectRatio(double aspectRatio, @NonNull String valueTag) {
        Log.d(getName(), "trackSetAspectRatio()");
        final MediaStreamTrack track = repository.tracks.getByValueTag(valueTag);
        if (!(track instanceof VideoTrack)) return;
        final VideoTrack videoTrack = (VideoTrack) track;
        repository.setVideoTrackAspectRatio(videoTrack, aspectRatio);
    }

    /**
     * transceiverDirection(valueTag: ValueTag): Promise<RTCRtpTransceiverDirection>
     */
    @ReactMethod
    public void transceiverDirection(@NonNull String valueTag, @NonNull Promise promise) {
        Log.d(getName(), "transceiverDirection()");
        final RtpTransceiver transceiver = repository.transceivers.getByValueTag(valueTag);
        if (transceiver == null) {
            promise.reject("NotFoundError", "transceiver is not found", (Throwable) null);
            return;
        }
        promise.resolve(rtpTransceiverDirectionStringValue(transceiver.getDirection()));
    }

    /**
     * transceiverSetDirection(valueTag: ValueTag, value: RTCRtpTransceiverDirection)
     */
    @ReactMethod
    public void transceiverSetDirection(@NonNull String valueTag, @NonNull String value, @NonNull Promise promise) {
        Log.d(getName(), "transceiverSetDirection()");
        final RtpTransceiver transceiver = repository.transceivers.getByValueTag(valueTag);
        if (transceiver == null) {
            promise.reject("NotFoundError", "transceiver is not found", (Throwable) null);
            return;
        }
        transceiver.setDirection(rtpTransceiverDirection(value));
        promise.resolve(null);
    }

    /**
     * transceiverCurrentDirection(valueTag: ValueTag): Promise<RTCRtpTransceiverDirection>
     */
    @ReactMethod
    public void transceiverCurrentDirection(@NonNull String valueTag, @NonNull Promise promise) {
        Log.d(getName(), "transceiverCurrentDirection()");
        final RtpTransceiver transceiver = repository.transceivers.getByValueTag(valueTag);
        if (transceiver == null) {
            promise.reject("NotFoundError", "transceiver is not found", (Throwable) null);
            return;
        }
        final RtpTransceiver.RtpTransceiverDirection currentDirection = transceiver.getCurrentDirection();
        if (currentDirection == null) {
            promise.resolve(null);
            return;
        }
        promise.resolve(rtpTransceiverDirectionStringValue(currentDirection));
    }

    /**
     * transceiverStop(valueTag: ValueTag)
     */
    @ReactMethod
    public void transceiverStop(@NonNull String valueTag, @NonNull Promise promise) {
        Log.d(getName(), "transceiverStop()");
        final RtpTransceiver transceiver = repository.transceivers.getByValueTag(valueTag);
        if (transceiver == null) {
            promise.reject("NotFoundError", "transceiver is not found", (Throwable) null);
            return;
        }
        transceiver.stop();
        promise.resolve(null);
    }

    /**
     * peerConnectionInit(valueTag: ValueTag, configuration: RTCConfiguration, constraints: RTCMediaConstraints)
     * TODO: MediaConstraintsがdeprecated扱いになっているがどうするべきか？とりあえず現状はconstraints引数を無視するようにしているが・・・
     */
    @ReactMethod
    public void peerConnectionInit(@NonNull ReadableMap configurationJson, @Nullable ReadableMap constraintsJson, @NonNull String valueTag) {
        Log.d(getName(), "peerConnectionInit() - valueTag=" + valueTag);
        final PeerConnection.RTCConfiguration configuration = rtcConfiguration(configurationJson);
        final WebRTCPeerConnectionObserver observer = new WebRTCPeerConnectionObserver(reactContext);
        final PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(configuration, observer);
        if (peerConnection == null) {
            throw new IllegalStateException("createPeerConnection failed");
        }
        final Pair<String, PeerConnection> peerConnectionPair = new Pair<>(valueTag, peerConnection);
        // observerもrepositoryに保存するようにして、close時に明示的にかつ確実に破棄するようにしたほうが良いかもしれないが、
        // Java側で明示的に破棄してもしなくても結局はlibwebrtcのobserverのラッパーでしかないため、
        // libwebrtc側できちんと破棄されればJava側もそのうち適切にGCされて消えることがわかったので、
        // ひとまずこの状態で大丈夫
        observer.peerConnectionPair = peerConnectionPair;
        repository.addPeerConnection(peerConnectionPair);
    }

    /**
     * peerConnectionSetConfiguration(valueTag: ValueTag, configuration: RTCConfiguration)
     */
    @ReactMethod
    public void peerConnectionSetConfiguration(@NonNull ReadableMap configurationJson, @NonNull String valueTag) {
        Log.d(getName(), "peerConnectionSetConfiguration()");
        final PeerConnection.RTCConfiguration configuration = rtcConfiguration(configurationJson);
        final PeerConnection peerConnection = repository.getPeerConnectionByValueTag(valueTag);
        if (peerConnection == null) return;
        peerConnection.setConfiguration(configuration);
    }

    /**
     * peerConnectionAddTrack(valueTag: ValueTag, trackValueTag: ValueTag, streamIds: Array<String>): Promise<Object>
     */
    @ReactMethod
    public void peerConnectionAddTrack(@NonNull String trackValueTag,
                                       @NonNull ReadableArray streamIds,
                                       @NonNull String valueTag,
                                       @NonNull Promise promise) {
        Log.d(getName(), "peerConnectionAddTrack()");
        final PeerConnection peerConnection = repository.getPeerConnectionByValueTag(valueTag);
        if (peerConnection == null) {
            promise.reject("NotFoundError", "peer connection is not found");
            return;
        }
        final MediaStreamTrack track = repository.tracks.getByValueTag(trackValueTag);
        if (track == null) {
            promise.reject("NotFoundError", "track is not found");
            return;
        }
        final List<String> streamIdsList = toStringList(streamIds);
        final RtpSender sender = peerConnection.addTrack(track, streamIdsList);
        if (sender == null) {
            promise.reject("PeerConnectionError", "cannot add the track");
            return;
        }
        repository.senders.add(sender.id(), createNewValueTag(), sender);
        repository.setStreamIdsForSender(sender, streamIdsList);

        promise.resolve(rtpSenderJsonValue(sender, repository));
    }

    /**
     * peerConnectionRemoveTrack(valueTag: ValueTag, senderValueTag: ValueTag)
     */
    @ReactMethod

    public void peerConnectionRemoveTrack(@NonNull String senderValueTag,
                                          @NonNull String valueTag,
                                          @NonNull Promise promise) {
        Log.d(getName(), "peerConnectionRemoveTrack()");
        final PeerConnection peerConnection = repository.getPeerConnectionByValueTag(valueTag);
        if (peerConnection == null) {
            promise.reject("NotFoundError", "peer connection is not found");
            return;
        }
        final RtpSender sender = repository.senders.getByValueTag(senderValueTag);
        if (sender == null) {
            promise.reject("NotFoundError", "sender is not found");
            return;
        }

        repository.senders.removeById(sender.id());
        if (peerConnection.removeTrack(sender)) {
            promise.resolve(null);
        } else {
            promise.reject("RemoveTrackFailed", "cannot remove track");
        }
    }

    /**
     * peerConnectionCreateOffer(valueTag: ValueTag, constraints: RTCMediaConstraints): Promise<RTCSessionDescription>
     */
    @ReactMethod
    public void peerConnectionCreateOffer(@NonNull String valueTag, @Nullable ReadableMap constraintsJson, @NonNull Promise promise) {
        Log.d(getName(), "peerConnectionCreateOffer() - valueTag=" + valueTag);
        final PeerConnection peerConnection = repository.getPeerConnectionByValueTag(valueTag);
        if (peerConnection == null) {
            promise.reject("NotFoundError", "peer connection is not found");
            return;
        }
        if (constraintsJson == null) {
            promise.reject("NotFoundError", "constraints is null");
            return;
        }
        final SdpObserver observer = new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                final WritableMap result = Arguments.createMap();
                result.putString("sdp", sessionDescription.description);
                result.putString("type", sessionDescription.type.canonicalForm());
                promise.resolve(result);
            }

            @Override
            public void onSetSuccess() {
                promise.reject("FatalError", "this must not be called");
            }

            @Override
            public void onCreateFailure(String s) {
                promise.reject("CreateOfferFailed", s);
            }

            @Override
            public void onSetFailure(String s) {
                promise.reject("FatalError", "this must not be called");
            }
        };
        peerConnection.createOffer(observer, mediaConstraints(constraintsJson));
    }

    /**
     * peerConnectionCreateAnswer(valueTag: ValueTag, constraints: RTCMediaConstraints): Promise<RTCSessionDescription>
     */
    @ReactMethod
    public void peerConnectionCreateAnswer(@NonNull String valueTag, @Nullable ReadableMap constraintsJson, @NonNull Promise promise) {
        Log.d(getName(), "peerConnectionCreateAnswer()");
        final PeerConnection peerConnection = repository.getPeerConnectionByValueTag(valueTag);
        if (peerConnection == null) {
            promise.reject("NotFoundError", "peer connection is not found");
            return;
        }
        if (constraintsJson == null) {
            promise.reject("NotFoundError", "constraints is null");
            return;
        }
        final SdpObserver observer = new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                final WritableMap result = Arguments.createMap();
                result.putString("sdp", sessionDescription.description);
                result.putString("type", sessionDescription.type.canonicalForm());
                promise.resolve(result);
            }

            @Override
            public void onSetSuccess() {
                promise.reject("FatalError", "this must not be called");
            }

            @Override
            public void onCreateFailure(String s) {
                promise.reject("CreateOfferFailed", s);
            }

            @Override
            public void onSetFailure(String s) {
                promise.reject("FatalError", "this must not be called");
            }
        };
        peerConnection.createAnswer(observer, mediaConstraints(constraintsJson));
    }

    /**
     * peerConnectionSetLocalDescription(valueTag: ValueTag, sdp: RTCSessionDescription): Promise<void>
     */
    @ReactMethod
    public void peerConnectionSetLocalDescription(@NonNull ReadableMap sdpJson, @NonNull String valueTag, @NonNull Promise promise) {
        Log.d(getName(), "peerConnectionSetLocalDescription()");
        final PeerConnection peerConnection = repository.getPeerConnectionByValueTag(valueTag);
        if (peerConnection == null) {
            promise.reject("NotFoundError", "peer connection is not found");
            return;
        }
        final SdpObserver observer = new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                promise.reject("FatalError", "this must not be called");
            }

            @Override
            public void onSetSuccess() {
                promise.resolve(null);
            }

            @Override
            public void onCreateFailure(String s) {
                promise.reject("FatalError", "this must not be called");
            }

            @Override
            public void onSetFailure(String s) {
                promise.reject("SetLocalDescriptionFailed", s);
            }
        };
        peerConnection.setLocalDescription(observer, sessionDescription(sdpJson));
    }

    /**
     * peerConnectionSetRemoteDescription(valueTag: ValueTag, sdp: RTCSessionDescription): Promise<void>
     */
    @ReactMethod
    public void peerConnectionSetRemoteDescription(@NonNull ReadableMap sdpJson, @NonNull String valueTag, @NonNull Promise promise) {
        Log.d(getName(), "peerConnectionSetRemoteDescription()");
        final PeerConnection peerConnection = repository.getPeerConnectionByValueTag(valueTag);
        if (peerConnection == null) {
            promise.reject("NotFoundError", "peer connection is not found");
            return;
        }
        final SdpObserver observer = new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                promise.reject("FatalError", "this must not be called");
            }

            @Override
            public void onSetSuccess() {
                promise.resolve(null);
            }

            @Override
            public void onCreateFailure(String s) {
                promise.reject("FatalError", "this must not be called");
            }

            @Override
            public void onSetFailure(String s) {
                promise.reject("SetRemoteDescriptionFailed", s);
            }
        };
        peerConnection.setRemoteDescription(observer, sessionDescription(sdpJson));
    }

    /**
     * peerConnectionAddICECandidate(valueTag: ValueTag, candidate: RTCIceCandidate): Promise<void>
     */
    @ReactMethod
    public void peerConnectionAddICECandidate(@NonNull ReadableMap iceCandidateJson, @NonNull String valueTag, @NonNull Promise promise) {
        Log.d(getName(), "peerConnectionAddICECandidate()");
        final PeerConnection peerConnection = repository.getPeerConnectionByValueTag(valueTag);
        if (peerConnection == null) {
            promise.reject("NotFoundError", "peer connection is not found");
            return;
        }
        peerConnection.addIceCandidate(iceCandidate(iceCandidateJson));
        promise.resolve(null);
    }

    /**
     * peerConnectionClose(valueTag: ValueTag)
     */
    @ReactMethod
    public void peerConnectionClose(@NonNull String valueTag) {
        Log.d(getName(), "peerConnectionClose() - valueTag=" + valueTag);
        final PeerConnection peerConnection = repository.getPeerConnectionByValueTag(valueTag);
        if (peerConnection == null) {
            return;
        }
        repository.removePeerConnectionByValueTag(valueTag);
        peerConnection.dispose();
    }

    /**
     * rtpEncodingParametersSetActive(owner: ValueTag, ssrc: number | null, flag: boolean)
     */
    @ReactMethod
    public void rtpEncodingParametersSetActive(boolean flag,
                                               long ssrc,
                                               @NonNull String ownerValueTag) {
        Log.d(getName(), "rtpEncodingParametersSetActive()");
        final RtpParameters.Encoding encodingParams = repository.getRtpEncodingParametersByValueTag(ownerValueTag, ssrc);
        if (encodingParams == null) return;
        encodingParams.active = flag;
    }

    /**
     * rtpEncodingParametersSetMaxBitrate(owner: ValueTag, ssrc: number | null, value: number | null)
     */
    @ReactMethod
    public void rtpEncodingParametersSetMaxBitrate(int bitrate,
                                                   long ssrc,
                                                   @NonNull String ownerValueTag) {
        Log.d(getName(), "rtpEncodingParametersSetMaxBitrate()");
        final RtpParameters.Encoding encodingParams = repository.getRtpEncodingParametersByValueTag(ownerValueTag, ssrc);
        if (encodingParams == null) return;
        encodingParams.maxBitrateBps = bitrate;
    }

    /**
     * rtpEncodingParametersSetMinBitrate(owner: ValueTag, ssrc: number | null, value: number | null)
     */
    @ReactMethod
    public void rtpEncodingParametersSetMinBitrate(int bitrate,
                                                   long ssrc,
                                                   @NonNull String ownerValueTag) {
        Log.d(getName(), "rtpEncodingParametersSetMinBitrate()");
        final RtpParameters.Encoding encodingParams = repository.getRtpEncodingParametersByValueTag(ownerValueTag, ssrc);
        if (encodingParams == null) return;
        encodingParams.minBitrateBps = bitrate;
    }

    /**
     * peerConnectionCreateDataChannel(String label, DataChannel.Init init, String valueTag): Promise<RTCDataChannel>
     */
    @ReactMethod
    public void peerConnectionCreateDataChannel(@NonNull String label, @Nullable ReadableMap initJson, @NonNull String valueTag, @NonNull Promise promise) {
        Log.d(getName(), "peerConnectionCreateDataChannel()");
        final PeerConnection peerConnection = repository.getPeerConnectionByValueTag(valueTag);
        if (peerConnection == null) {
            promise.reject("NotFoundError", "peer connection is not found");
            return;
        }
        final DataChannel dataChannel = peerConnection.createDataChannel(label, dataChannelInit(initJson));
        if (dataChannel == null) {
            promise.reject("FatalError", "createDataChannel failed");
            return;
        }
        // observer を登録する
        final String dataChannelValueTag = createNewValueTag();
        final WebRTCDataChannelObserver observer = new WebRTCDataChannelObserver(reactContext);
        final Pair<String, DataChannel> dataChannelPair = new Pair<>(dataChannelValueTag, dataChannel);
        observer.dataChannelPair = dataChannelPair;
        dataChannel.registerObserver(observer);
        repository.addDataChannel(dataChannelPair);
        Log.d(getName(), "peerConnectionCreateDataChannel()" + dataChannelJsonValue(dataChannel, dataChannelValueTag));
        promise.resolve(dataChannelJsonValue(dataChannel, dataChannelValueTag));
    }

    /**
     * dataChannelClose(valueTag: ValueTag)
     * XXX(kdxu): PeerConnection.close() と統一性をもたせるため、こちらは同期メソッドとする
     */
    @ReactMethod
    public void dataChannelClose(@NonNull String valueTag) {
        Log.d(getName(), "dataChannelClose() - valueTag=" + valueTag);
        final DataChannel dataChannel = repository.getDataChannelByValueTag(valueTag);
        if (dataChannel == null) {
            return;
        }
        // dataChannel の state が open でないときは実行しない
        if (dataChannel.state() != DataChannel.State.OPEN) {
          return;
        }
        dataChannel.close();
    }

    /**
     * dataChannelSend(buffer: ReadableMap, valueTag: ValueTag): Promise<void>
     */
    @ReactMethod
    public void dataChannelSend(@NonNull ReadableMap sendBufferJson, @NonNull String valueTag, @NonNull Promise promise) {
        Log.d(getName(), "dataChannelSend() - valueTag=" + valueTag + " sendBufferJson=" + sendBufferJson);
        final DataChannel dataChannel = repository.getDataChannelByValueTag(valueTag);
        if (dataChannel == null) {
            promise.reject("NotFoundError", "dataChannel is not found");
            return;
        }
        final DataChannel.Buffer buffer = dataChannelBuffer(sendBufferJson);
        dataChannel.send(buffer);
        promise.resolve(null);
    }

    /**
     * peerConnectionAddTransceiver(String trackValueTag, String valueTag, RtpTransceiver.Init init): Promise<RtpTransceiver>
     */
    @ReactMethod
    public void peerConnectionAddTransceiver(@NonNull String trackValueTag, @NonNull String valueTag, @NonNull ReadableMap initJson, @NonNull Promise promise) {
      Log.d(getName(), "peerConnectionAddTransceiver()");
      final PeerConnection peerConnection = repository.getPeerConnectionByValueTag(valueTag);
      if (peerConnection == null) {
        promise.reject("NotFoundError", "peer connection is not found");
        return;
      }
      final MediaStreamTrack track = repository.tracks.getByValueTag(trackValueTag);
      if (track == null) {
        promise.reject("NotFoundError", "track is not found");
        return;
      }
      final RtpTransceiver transceiver = peerConnection.addTransceiver(track, rtpTransceiverInit(initJson));
      if (transceiver == null) {
        promise.reject("PeerConnectionError", "cannot add the transceiver");
        return;
      }
      // リポジトリの管理下に追加する
      repository.transceivers.add(transceiver.getMid(), createNewValueTag(), transceiver);
      promise.resolve(rtpTransceiverJsonValue(transceiver, repository));
    }

    //endregion


    @NonNull
    EglBase.Context getEglContext() {
        return eglBase.getEglBaseContext();
    }

    @NonNull
    String createNewValueTag() {
        return UUID.randomUUID().toString();
    }

}
