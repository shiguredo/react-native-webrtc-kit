package jp.shiguredo.react.webrtckit;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import static jp.shiguredo.react.webrtckit.Readables.array;
import static jp.shiguredo.react.webrtckit.Readables.booleans;
import static jp.shiguredo.react.webrtckit.Readables.doubles;
import static jp.shiguredo.react.webrtckit.Readables.jint;
import static jp.shiguredo.react.webrtckit.Readables.map;
import static jp.shiguredo.react.webrtckit.Readables.string;
import static jp.shiguredo.react.webrtckit.Readables.type;

final class WebRTCConverter {

    private WebRTCConverter() {
    }

    //region RtpParameters

    @NonNull
    static WritableMap rtpParametersJsonValue(@NonNull final RtpParameters parameters) {
        final WritableMap rtcp = Arguments.createMap();
        rtcp.putString("cname", parameters.getRtcp().getCname());
        rtcp.putBoolean("reducedSize", parameters.getRtcp().getReducedSize());

        final WritableArray headerExtensions = Arguments.createArray();
        for (final RtpParameters.HeaderExtension extension : parameters.getHeaderExtensions()) {
            final WritableMap obj = Arguments.createMap();
            obj.putString("uri", extension.getUri());
            obj.putInt("id", extension.getId());
            obj.putBoolean("encrypted", extension.getEncrypted());
            headerExtensions.pushMap(obj);
        }

        final WritableArray encodings = Arguments.createArray();
        for (final RtpParameters.Encoding encoding : parameters.encodings) {
            final WritableMap obj = Arguments.createMap();
            obj.putBoolean("active", encoding.active);
            if (encoding.maxBitrateBps != null) {
                obj.putInt("maxBitrate", encoding.maxBitrateBps);
            }
            if (encoding.minBitrateBps != null) {
                obj.putInt("minBitrate", encoding.minBitrateBps);
            }
            if (encoding.ssrc != null) {
                // XXX: putLong()が存在しない、putIntでは桁落ちする危険性がある、putDoubleでは元の値と異なる値になって壊れる危険性がある、参った
                //      一応putIntで対応するが、桁落ちした場合はputDoubleを試すしかないかもしれない
                obj.putInt("ssrc", encoding.ssrc.intValue());
            }
            encodings.pushMap(obj);
        }

        final WritableArray codecs = Arguments.createArray();
        for (final RtpParameters.Codec codec : parameters.codecs) {
            final WritableMap obj = Arguments.createMap();
            obj.putInt("payloadType", codec.payloadType);
            // XXX: 一体全体なぜかわからないが肝心のkindだけ何故かinternalになっていて普通に取り出せないのでreflectionします、最低最悪、絶対libwebrtcのバグだと思うが他に手がないです
            try {
                final Field kindField = codec.getClass().getField("kind");
                final MediaStreamTrack.MediaType kind = (MediaStreamTrack.MediaType) kindField.get(codec);
                switch (kind) {
                    case MEDIA_TYPE_AUDIO:
                        obj.putString("mimeType", String.format("audio/%s", codec.name));
                    case MEDIA_TYPE_VIDEO:
                        obj.putString("mimeType", String.format("video/%s", codec.name));
                }
            } catch (final Throwable e) {
                // Do nothing
            }
            final WritableMap parametersObj = Arguments.createMap();
            for (final Map.Entry<String, String> entry : codec.parameters.entrySet()) {
                parametersObj.putString(entry.getKey(), entry.getValue());
            }
            obj.putMap("parameters", parametersObj);
            if (codec.clockRate != null) {
                obj.putInt("clockRate", codec.clockRate);
            }
            if (codec.numChannels != null) {
                obj.putInt("channels", codec.numChannels);
            }
            codecs.pushMap(obj);
        }

        final WritableMap json = Arguments.createMap();
        json.putString("transactionId", parameters.transactionId);
        json.putMap("rtcp", rtcp);
        json.putArray("headerExtensions", headerExtensions);
        json.putArray("encodings", encodings);
        json.putArray("codecs", codecs);
        return json;
    }

    //endregion

    //region RtpSender

    @NonNull
    static WritableMap rtpSenderJsonValue(@NonNull final RtpSender sender,
                                          @NonNull final WebRTCRepository repository) {
        final String valueTag = repository.senders.getValueTag(sender.id());
        final List<String> streamIds = repository.getStreamIdsForSender(sender);

        final WritableArray streamIdsArray = Arguments.createArray();
        if (streamIds != null) {
            for (final String streamId : streamIds) {
                streamIdsArray.pushString(streamId);
            }
        }

        final WritableMap json = Arguments.createMap();
        json.putString("id", sender.id());
        json.putMap("parameters", rtpParametersJsonValue(sender.getParameters()));
        json.putArray("streamIds", streamIdsArray);
        if (valueTag != null) {
            json.putString("valueTag", valueTag);
        }
        // iOSの実装と異なり、Androidでは RtpReceiver.track() はコンストラクタの地点で生成・準備されているため、呼び出し時に突然trackの新規インスタンスが生成されることはない。
        // したがってここでは単純にtrackをJSONとして追加するだけで良い。
        final MediaStreamTrack track = sender.track();
        if (track != null) {
            json.putMap("track", mediaStreamTrackJsonValue(track, repository));
        }
        return json;
    }

    @NonNull
    static String rtpSenderDump(@NonNull final RtpSender sender) {
        final MediaStreamTrack track = sender.track();
        if (track == null) {
            return "sender -> no track";
        } else {
            return String.format("sender -> %s",
                    mediaStreamTrackDump(track));
        }
    }

    //endregion

    //region RtpReceiver

    @NonNull
    static WritableMap rtpReceiverJsonValue(@NonNull final RtpReceiver receiver,
                                            @NonNull final WebRTCRepository repository) {
        final String valueTag = repository.receivers.getValueTag(receiver.id());
        final List<String> streamIds = repository.getStreamIdsForReceiver(receiver);

        final WritableArray streamIdsArray = Arguments.createArray();
        if (streamIds != null) {
            for (final String streamId : streamIds) {
                streamIdsArray.pushString(streamId);
            }
        }

        final WritableMap json = Arguments.createMap();
        json.putString("id", receiver.id());
        json.putMap("parameters", rtpParametersJsonValue(receiver.getParameters()));
        json.putArray("streamIds", streamIdsArray);
        if (valueTag != null) {
            json.putString("valueTag", valueTag);
        }
        // iOSの実装と異なり、Androidでは RtpReceiver.track() はコンストラクタの地点で生成・準備されているため、呼び出し時に突然trackの新規インスタンスが生成されることはない。
        // したがってここでは単純にtrackをJSONとして追加するだけで良い。
        final MediaStreamTrack track = receiver.track();
        if (track != null) {
            json.putMap("track", mediaStreamTrackJsonValue(track, repository));
        }
        return json;
    }

    @NonNull
    static String rtpReceiverDump(@NonNull final RtpReceiver receiver) {
        final MediaStreamTrack track = receiver.track();
        if (track == null) {
            return "receiver -> no track";
        } else {
            return String.format("receiver -> %s",
                    mediaStreamTrackDump(track));
        }
    }

    //endregion

    //region RtpTransceiver

    @NonNull
    static WritableMap rtpTransceiverJsonValue(@NonNull final RtpTransceiver transceiver,
                                               @NonNull final WebRTCRepository repository) {
        final String valueTag = repository.transceivers.getValueTag(transceiver.getMid());
        final WritableMap json = Arguments.createMap();
        json.putString("mid", transceiver.getMid());
        json.putMap("sender", rtpSenderJsonValue(transceiver.getSender(), repository));
        json.putMap("receiver", rtpReceiverJsonValue(transceiver.getReceiver(), repository));
        json.putBoolean("stopped", transceiver.isStopped());
        if (valueTag != null) {
            json.putString("valueTag", valueTag);
        }
        return json;
    }

    @NonNull
    static String rtpTransceiverDump(@NonNull final RtpTransceiver transceiver) {
        try {
            return String.format("%s, %s %b [%s] [%s]",
                    transceiver.getMediaType(),
                    rtpTransceiverDirectionStringValue(transceiver.getDirection()),
                    transceiver.isStopped(),
                    rtpReceiverDump(transceiver.getReceiver()),
                    rtpSenderDump(transceiver.getSender()));
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
    }

    //endregion

    //region RtpTransceiver.RtpTransceiverDirection

    @NonNull
    static RtpTransceiver.RtpTransceiverDirection rtpTransceiverDirection(@NonNull final String string) {
        switch (string) {
            case "sendrecv":
                return RtpTransceiver.RtpTransceiverDirection.SEND_RECV;
            case "sendonly":
                return RtpTransceiver.RtpTransceiverDirection.SEND_ONLY;
            case "recvonly":
                return RtpTransceiver.RtpTransceiverDirection.RECV_ONLY;
            case "inactive":
                return RtpTransceiver.RtpTransceiverDirection.INACTIVE;
            default:
                throw new IllegalArgumentException("invalid direction string " + string);
        }
    }

    @NonNull
    static String rtpTransceiverDirectionStringValue(@NonNull final RtpTransceiver.RtpTransceiverDirection direction) {
        switch (direction) {
            case SEND_RECV:
                return "sendrecv";
            case SEND_ONLY:
                return "sendonly";
            case RECV_ONLY:
                return "recvonly";
            case INACTIVE:
                return "inactive";
            default:
                throw new IllegalArgumentException("invalid direction " + direction);
        }
    }

    //endregion


    //region PeerConnection.IceServer

    @NonNull
    static PeerConnection.IceServer iceServer(@NonNull final ReadableMap json) {
        final ReadableArray urlsJson = array(json, "urls");
        if (urlsJson == null) {
            throw new NullPointerException("RTCIceServer.urls");
        } else if (urlsJson.size() == 0) {
            throw new IllegalArgumentException("RTCIceServer.urls is empty");
        }
        final List<String> urls = toStringList(urlsJson);
        final PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder(urls);
        final String usernameString = string(json, "username");
        if (usernameString != null) {
            builder.setUsername(usernameString);
        }
        final String credentialString = string(json, "credential");
        if (credentialString != null) {
            builder.setPassword(credentialString);
        }

        return builder.createIceServer();
    }

    //endregion


    //region PeerConnection.RTCConfiguration

    @NonNull
    static PeerConnection.RTCConfiguration rtcConfiguration(@NonNull final ReadableMap json) {
        final ReadableArray iceServersJson = array(json, "iceServers");
        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        if (iceServersJson != null) {
            for (int i = 0; i < iceServersJson.size(); i++) {
                final ReadableMap iceServerJson = iceServersJson.getMap(i);
                if (iceServerJson == null) {
                    throw new NullPointerException("each RTCConfiguration.iceServers");
                }
                iceServers.add(iceServer(iceServerJson));
            }
        }

        final PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(iceServers);

        configuration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        configuration.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        configuration.keyType = PeerConnection.KeyType.ECDSA;
        configuration.enableDtlsSrtp = true;

        final String policyString = string(json, "iceTransportPolicy");
        if (policyString != null) {
            configuration.iceTransportsType = iceTransportsType(policyString);
        }

        // XXX:  configuration.tcpCandidatePolicy

        final String semanticsString = string(json, "sdpSemantics");
        if (semanticsString != null) {
            configuration.sdpSemantics = sdpSemantics(semanticsString);
        }

        return configuration;
    }

    //endregion

    // region RtpParameters.Encoding
    @NonNull
    static RtpParameters.Encoding sendEncoding(@NonNull final ReadableMap json) {
        String rid = null;
        Boolean active = true;
        Double scaleResolutionDownBy = null;
        Double maxFramerate = null;
        // TODO(kdxu): maxBitrate / maxFramerate の変換処理を実装する
        if (json.hasKey("rid")) {
            rid = json.getString("rid");
        }
        if (json.hasKey("active")) {
            active = json.getBoolean("active");
        }
        if (json.hasKey("scaleResolutionDownBy")) {
            scaleResolutionDownBy = json.getDouble("scaleResolutionDownBy");
        }
        final RtpParameters.Encoding encoding = new RtpParameters.Encoding(rid, active, scaleResolutionDownBy);
        return encoding;
    }
    //endregion

    // region RtpTransceiver.RtpTransceiverInit
    @NonNull
    static RtpTransceiver.RtpTransceiverInit rtpTransceiverInit(@Nullable final ReadableMap json) {
        if (json != null) {
            RtpTransceiver.RtpTransceiverDirection direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV;
            final List<String> streamIds = new ArrayList<>();
            final List<RtpParameters.Encoding> sendEncodings = new ArrayList<>();
            if (json.hasKey("direction")) {
                direction = rtpTransceiverDirection(string(json, "direction"));
            }
            if (json.hasKey("streamIds")) {
                final ReadableArray streamIdsJson = array(json, "streamIds");
                for (int i = 0; i < streamIdsJson.size(); i++) {
                    final String streamId = streamIdsJson.getString(i);
                    if (streamId == null) {
                        throw new NullPointerException("each RtpTransceiverInit.streamId");
                    }
                    streamIds.add(streamId);
                }
            }
            final ReadableArray sendEncodingsJson = array(json, "sendEncodings");
            if (sendEncodingsJson != null) {
                for (int i = 0; i < sendEncodingsJson.size(); i++) {
                    final ReadableMap sendEncodingJson = sendEncodingsJson.getMap(i);
                    if (sendEncodingJson == null) {
                        throw new NullPointerException("each RtpTransceiverInit.sendEncodings");
                    }
                    sendEncodings.add(sendEncoding(sendEncodingJson));
                }
            }
            return new RtpTransceiver.RtpTransceiverInit(direction, streamIds, sendEncodings);
        }
        return new RtpTransceiver.RtpTransceiverInit();
    }

    // endregion
    //region PeerConnection.IceTransportsType

    @NonNull
    static PeerConnection.IceTransportsType iceTransportsType(@NonNull final String string) {
        switch (string) {
            case "none":
                return PeerConnection.IceTransportsType.NONE;
            case "relay":
                return PeerConnection.IceTransportsType.RELAY;
            case "nohost":
                return PeerConnection.IceTransportsType.NOHOST;
            case "all":
                return PeerConnection.IceTransportsType.ALL;
            default:
                throw new IllegalArgumentException("invalid type string " + string);
        }
    }

    //endregion


    //region PeerConnection.SdpSemantics

    @NonNull
    static PeerConnection.SdpSemantics sdpSemantics(@NonNull final String string) {
        switch (string) {
            case "planb":
                return PeerConnection.SdpSemantics.PLAN_B;
            case "unified":
                return PeerConnection.SdpSemantics.UNIFIED_PLAN;
            default:
                throw new IllegalArgumentException("invalid semantics string " + string);
        }
    }

    //endregion


    //region PeerConnection.SignalingState

    @NonNull
    static String signalingStateStringValue(@NonNull final PeerConnection.SignalingState signalingState) {
        switch (signalingState) {
            case STABLE:
                return "stable";
            case HAVE_LOCAL_OFFER:
                return "have-local-offer";
            case HAVE_LOCAL_PRANSWER:
                return "have-local-pranswer";
            case HAVE_REMOTE_OFFER:
                return "have-remote-offer";
            case HAVE_REMOTE_PRANSWER:
                return "have-remote-pranswer";
            case CLOSED:
                return "closed";
            default:
                throw new IllegalArgumentException("invalid signalingState");
        }
    }

    //endregion


    //region PeerConnection.PeerConnectionState
    @NonNull
    static String peerConnectionStateStringValue(@NonNull final PeerConnection.PeerConnectionState connectionState) {
        switch (connectionState) {
            case NEW:
                return "new";
            case CONNECTING:
                return "connecting";
            case CONNECTED:
                return "connected";
            case FAILED:
                return "failed";
            case DISCONNECTED:
                return "disconnected";
            case CLOSED:
                return "closed";
            default:
                throw new IllegalArgumentException("invalid peerConnectionState");
        }
    }
    //endregion


    //region PeerConnection.IceConnectionState

    @NonNull
    static String iceConnectionStateStringValue(@NonNull final PeerConnection.IceConnectionState iceConnectionState) {
        switch (iceConnectionState) {
            case NEW:
                return "new";
            case CHECKING:
                return "checking";
            case CONNECTED:
                return "connected";
            case COMPLETED:
                return "completed";
            case FAILED:
                return "failed";
            case DISCONNECTED:
                return "disconnected";
            case CLOSED:
                return "closed";
            // XXX: IceConnectionState.COUNT がAndroid SDKに存在しないので、COUNTが飛んできたら多分死ぬ。SDK側が対応しない限りどうしようもない。
            default:
                throw new IllegalArgumentException("invalid iceConnectionState");
        }
    }

    //endregion


    //region PeerConnection.IceGatheringState

    @NonNull
    static String iceGatheringStateStringValue(@NonNull final PeerConnection.IceGatheringState iceGatheringState) {
        switch (iceGatheringState) {
            case NEW:
                return "new";
            case GATHERING:
                return "gathering";
            case COMPLETE:
                return "complete";
            default:
                throw new IllegalArgumentException("invalid iceGatheringState");
        }
    }

    //endregion


    //region SessionDescription

    @NonNull
    static SessionDescription sessionDescription(@NonNull final ReadableMap json) {
        final String sdp = string(json, "sdp");
        final SessionDescription.Type type = SessionDescription.Type.fromCanonicalForm(string(json, "type"));
        return new SessionDescription(type, sdp);
    }

    //endregion


    //region IceCandidate

    @NonNull
    static IceCandidate iceCandidate(@NonNull final ReadableMap json) {
        String sdp = "";
        String sdpMid = "";
        final int sdpMLineIndex = jint(json, "sdpMLineIndex");
        if (json.hasKey("sdp") && !json.isNull("sdp")) {
          sdp = string(json, "sdp");
        }
        if (json.hasKey("sdpMid") && !json.isNull("sdpMid")) {
          sdpMid = string(json, "sdpMid");
        }
        return new IceCandidate(sdpMid, sdpMLineIndex, sdp);
    }

    //endregion


    //region MediaStreamTrack

    @NonNull
    static WritableMap mediaStreamTrackJsonValue(@NonNull final MediaStreamTrack track,
                                                 @NonNull final WebRTCRepository repository) {
        final String valueTag = repository.tracks.getValueTag(track.id());
        final WritableMap json = Arguments.createMap();
        json.putString("id", track.id());
        json.putBoolean("enabled", track.enabled());
        json.putString("kind", track.kind());
        json.putString("readyState", mediaStreamTrackStateStringValue(track.state()));
        if (valueTag != null) {
            json.putString("valueTag", valueTag);
        }
        return json;
    }

    @NonNull
    static String mediaStreamTrackDump(@NonNull final MediaStreamTrack track) {
        try {
            return String.format("%s (%s) %b",
                    track.kind(),
                    mediaStreamTrackStateStringValue(track.state()),
                    track.enabled());
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
    }

    //endregion


    //region MediaStreamTrack.State

    @NonNull
    static String mediaStreamTrackStateStringValue(@NonNull final MediaStreamTrack.State state) {
        switch (state) {
            case LIVE:
                return "live";
            case ENDED:
                return "ended";
            default:
                throw new IllegalArgumentException("invalid mediaStreamTrackState");
        }
    }

    //endregion


    //region MediaConstraints

    @NonNull
    static MediaConstraints mediaConstraints(@NonNull final ReadableMap json) {
        final MediaConstraints mediaConstraints = new MediaConstraints();
        final ReadableMap mandatoryJson = map(json, "mandatory");
        final ReadableArray optionalArrayJson = array(json, "optional");

        if (mandatoryJson != null) {
            mediaConstraints.mandatory.addAll(toMediaConstraintsKeyValueList(mandatoryJson));
        }

        if (optionalArrayJson != null) {
            for (int i = 0; i < optionalArrayJson.size(); i++) {
                final ReadableMap optionalJson = optionalArrayJson.getMap(i);
                if (optionalJson == null) continue;
                mediaConstraints.optional.addAll(toMediaConstraintsKeyValueList(optionalJson));
            }
        }

        return mediaConstraints;
    }

    @NonNull
    private static List<MediaConstraints.KeyValuePair> toMediaConstraintsKeyValueList(@NonNull final ReadableMap json) {
        final List<MediaConstraints.KeyValuePair> result = new ArrayList<>();
        final ReadableMapKeySetIterator iterator = json.keySetIterator();
        String key;
        while ((key = iterator.nextKey()) != null) {
            final ReadableType type = type(json, key);
            switch (type) {
                case String:
                    result.add(new MediaConstraints.KeyValuePair(key, string(json, key)));
                    break;
                case Number:
                    result.add(new MediaConstraints.KeyValuePair(key, String.valueOf(doubles(json, key))));
                    break;
                case Boolean:
                    result.add(new MediaConstraints.KeyValuePair(key, String.valueOf(booleans(json, key))));
                    break;
                case Map:
                    result.add(new MediaConstraints.KeyValuePair(key, String.valueOf(map(json, key))));
                    break;
                case Array:
                    result.add(new MediaConstraints.KeyValuePair(key, String.valueOf(array(json, key))));
                    break;
                case Null:
                    result.add(new MediaConstraints.KeyValuePair(key, "null"));
                    break;
            }
        }
        return result;
    }

    //endregion

    //region DataChannel

    @NonNull
    static WritableMap dataChannelJsonValue(@NonNull final DataChannel dataChannel, @NonNull final String valueTag) {
        final WritableMap json = Arguments.createMap();
        json.putInt("id", dataChannel.id());
        json.putString("label", dataChannel.label());
        json.putString("readyState", dataChannelStateStringValue(dataChannel.state()));
        final long bufferedAmount = dataChannel.bufferedAmount();
        // XXX(kdxu): putLong()が存在しない。putIntでは桁落ちする危険性があるが、暫定的に intValue に変換することで対応する
        json.putInt("bufferedAmount", (int) bufferedAmount);
        json.putString("valueTag", valueTag);
        return json;
    }
    //endregion

    //region DataChannel.Init

    @NonNull
    static DataChannel.Init dataChannelInit(@Nullable final ReadableMap json) {
        final DataChannel.Init init = new DataChannel.Init();
        if (json != null) {
            if (json.hasKey("id")) {
                init.id = json.getInt("id");
            }
            if (json.hasKey("ordered")) {
                init.ordered = json.getBoolean("ordered");
            }
            // XXX(kdxu): android では `maxPacketLifeTime` が未定義。代わりに `maxRetransmitTimeMs` に代入する
            if (json.hasKey("maxPacketLifeTime")) {
                init.maxRetransmitTimeMs = json.getInt("maxPacketLifeTime");
            }
            if (json.hasKey("maxRetransmits")) {
                init.maxRetransmits = json.getInt("maxRetransmits");
            }
            if (json.hasKey("protocol")) {
                init.protocol = json.getString("protocol");
            }
            if (json.hasKey("negotiated")) {
                init.negotiated = json.getBoolean("negotiated");
            }
        }
        return init;
    }

    //endregion

    //region DataChannel.Buffer

    @NonNull
    static DataChannel.Buffer dataChannelBuffer(@NonNull final ReadableMap json) {
        if (!json.hasKey("binary")) {
            throw new IllegalArgumentException("invalid dataChannelBuffer");
        }
        if (!json.hasKey("data")) {
            throw new IllegalArgumentException("invalid dataChannelBuffer");
        }
        final Boolean isBinary = json.getBoolean("binary");
        final String data = json.getString("data");
        if (data == null) {
            throw new IllegalArgumentException("invalid dataChannelBuffer");
        }
        byte[] byteArray;
        // バイナリデータの場合、base64 で decode してバイト列にして送る
        if (isBinary) {
            byteArray = Base64.decode(data, Base64.NO_WRAP);
        } else {
            // そうでない場合 UTF-8 バイト列を取得する
            byteArray = data.getBytes(Charset.forName("UTF-8"));
        }
        final ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
        final DataChannel.Buffer buffer = new DataChannel.Buffer(byteBuffer, isBinary);
        return buffer;
    }


    // region DataChannel.State

    @NonNull
    static String dataChannelStateStringValue(@NonNull final DataChannel.State dataChannelState) {
        switch (dataChannelState) {
            case CONNECTING:
                return "connecting";
            case OPEN:
                return "open";
            case CLOSING:
                return "closing";
            case CLOSED:
                return "closed";
            default:
                throw new IllegalArgumentException("invalid dataChannelState");
        }
    }

    // endregion

    @NonNull
    static List<String> toStringList(@NonNull final ReadableArray arrayJson) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < arrayJson.size(); i++) {
            final String value = arrayJson.getString(i);
            if (value == null) {
                throw new IllegalStateException(String.format("Index %d is not a string: %s", i, arrayJson));
            }
            result.add(value);
        }
        return result;
    }
}
