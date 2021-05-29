#import <objc/runtime.h>

#import "WebRTCModule+RTCDataChannel.h"
#import "WebRTCModule+RTCMediaStream.h"
#import "WebRTCModule+RTCPeerConnection.h"
#import "WebRTCModule+getUserMedia.h"
#import "WebRTCUtils.h"
#import "WebRTCValueManager.h"

NS_ASSUME_NONNULL_BEGIN

static const char *streamIdsKey = "streamIds";

@implementation RTCRtpParameters (ReactNativeWebRTCKit)

- (id)json
{
    NSDictionary *rtcp = @{@"cname": self.rtcp.cname,
                           @"reducedSize":
                               [[NSNumber alloc] initWithBool:
                                self.rtcp.isReducedSize]};
    
    NSMutableArray *headerExts = [[NSMutableArray alloc] init];
    for (RTCRtpHeaderExtension *ext in self.headerExtensions) {
        [headerExts addObject:
         @{@"uri": ext.uri,
           @"id": [[NSNumber alloc] initWithInt: ext.id],
           @"encrypted":
               [[NSNumber alloc] initWithBool: ext.encrypted]}];
    }
    
    NSMutableArray *encodings = [[NSMutableArray alloc] init];
    for (RTCRtpEncodingParameters *enc in self.encodings) {
        NSMutableDictionary *json = [[NSMutableDictionary alloc] init];
        json[@"active"] = [[NSNumber alloc] initWithBool: enc.isActive];
        if (enc.maxBitrateBps) {
            json[@"maxBitrate"] = enc.maxBitrateBps;
        }
        if (enc.minBitrateBps) {
            json[@"minBitrate"] = enc.minBitrateBps;
        }
        if (enc.ssrc) {
            json[@"ssrc"] = enc.ssrc;
        }
        [encodings addObject: json];
    }
    
    NSMutableArray *codecs = [[NSMutableArray alloc] init];
    for (RTCRtpCodecParameters *codec in self.codecs) {
        NSMutableDictionary *json = [[NSMutableDictionary alloc] init];
        json[@"payloadType"] = [[NSNumber alloc] initWithInt: codec.payloadType];
        json[@"mimeType"] = [NSString stringWithFormat: @"%@/%@",
                             codec.kind, codec.name];
        json[@"parameters"] = codec.parameters;
        if (codec.clockRate) {
            json[@"clockRate"] = codec.clockRate;
        }
        if (codec.numChannels) {
            json[@"channels"] = codec.numChannels;
        }
        [codecs addObject: json];
    }
    
    return @{@"transactionId": self.transactionId,
             @"rtcp": rtcp,
             @"headerExtensions": headerExts,
             @"encodings": encodings,
             @"codecs": codecs};
}

@end

@implementation RTCRtpSender (ReactNativeWebRTCKit)

- (nullable NSString *)valueTag
{
    return [WebRTCValueManager valueTagForString: self.senderId];
}

- (void)setValueTag:(nullable NSString *)valueTag
{
    [WebRTCValueManager setValueTag: valueTag
                          forString: self.senderId];
}

- (NSArray<NSString *> *)streamIds
{
    id ids = objc_getAssociatedObject(self, streamIdsKey);
    return ids ? ids : @[];
}

- (void)setStreamIds:(NSArray<NSString *> *)streamIds
{
    objc_setAssociatedObject(self, streamIdsKey,
                             streamIds,
                             OBJC_ASSOCIATION_COPY_NONATOMIC);
}

- (id)json
{
    // RTCRtpSender/Receiver の track プロパティは
    // RTCMediaStreamTrcak を動的に生成するので、
    // 新しい value tag を割り当てる
    RTCMediaStreamTrack *track = self.track;
    if (track) {
        track.valueTag = [[WebRTCModule shared] createNewValueTag];
        [[WebRTCModule shared] addTrack: track forKey: track.valueTag];
    }

    NSMutableDictionary *json = [[NSMutableDictionary alloc] init];
    json[@"id"] = self.senderId;
    json[@"parameters"] = [self.parameters json];
    json[@"streamIds"] = self.streamIds;
    if (self.valueTag) {
        json[@"valueTag"] = self.valueTag;
    }
    if (track) {
        json[@"track"] = [track json];
    }
    return json;
}

@end

@implementation RTCRtpReceiver (ReactNativeWebRTCKit)

- (nullable NSString *)valueTag
{
    return [WebRTCValueManager valueTagForString: self.receiverId];
}

- (void)setValueTag:(nullable NSString *)valueTag
{
    [WebRTCValueManager setValueTag: valueTag
                          forString: self.receiverId];
}

- (NSArray<NSString *> *)streamIds
{
    id ids = objc_getAssociatedObject(self, streamIdsKey);
    return ids ? ids : @[];
}

- (void)setStreamIds:(NSArray<NSString *> *)streamIds
{
    objc_setAssociatedObject(self, streamIdsKey,
                             streamIds,
                             OBJC_ASSOCIATION_COPY_NONATOMIC);
}

- (id)json
{
    // RTCRtpSender/Receiver の track プロパティは
    // RTCMediaStreamTrack を動的に生成するので、
    // 新しい value tag を割り当てる
    RTCMediaStreamTrack *track = self.track;
    if (track) {
        track.valueTag = [[WebRTCModule shared] createNewValueTag];
        [[WebRTCModule shared] addTrack: track forKey: track.valueTag];
    }
    
    NSMutableDictionary *json = [[NSMutableDictionary alloc] init];
    json[@"id"] = self.receiverId;
    json[@"parameters"] = [self.parameters json];
    json[@"streamIds"] = self.streamIds;
    if (self.valueTag)
        json[@"valueTag"] = self.valueTag;
    if (track)
        json[@"track"] = [track json];
    return json;
}

@end

@implementation RTCRtpTransceiver (ReactNativeWebRTCKit)

- (NSString *)valueTagKey
{
    return [NSString stringWithFormat: @"%@:%@",
            self.sender.senderId, self.receiver.receiverId];
}

- (nullable NSString *)valueTag
{
    return [WebRTCValueManager valueTagForString: [self valueTagKey]];
}

- (void)setValueTag:(nullable NSString *)valueTag
{
    [WebRTCValueManager setValueTag: valueTag
                          forString: [self valueTagKey]];
}

- (id)json
{
    NSMutableDictionary *json =
    [[NSMutableDictionary alloc]
     initWithDictionary:
     @{@"sender": [self.sender json],
       @"receiver": [self.receiver json],
       @"stopped": [NSNumber numberWithBool: self.isStopped]}];
    // mid 及び valueTag は nil になる場合があるので、dict に直接代入する前に nil check を入れている
    if (self.mid) {
        json[@"mid"] = self.mid;
    }
    if (self.valueTag) {
        json[@"valueTag"] = self.valueTag;
    }
    return json;
}

+ (NSString *)directionDescription:(RTCRtpTransceiverDirection)direction
{
    switch (direction) {
        case RTCRtpTransceiverDirectionSendRecv:
            return @"sendrecv";
        case RTCRtpTransceiverDirectionSendOnly:
            return @"sendonly";
        case RTCRtpTransceiverDirectionRecvOnly:
            return @"recvonly";
        case RTCRtpTransceiverDirectionInactive:
            return @"inactive";
        case RTCRtpTransceiverDirectionStopped:
            return @"stopped";
    }
}

+ (RTCRtpTransceiverDirection)directionFromString:(NSString *)string
{
    if ([string isEqualToString: @"sendrecv"]) {
        return RTCRtpTransceiverDirectionSendRecv;
    }
    else if ([string isEqualToString: @"sendonly"]) {
        return RTCRtpTransceiverDirectionSendOnly;
    }
    else if ([string isEqualToString: @"recvonly"]) {
        return RTCRtpTransceiverDirectionRecvOnly;
    }
    else if ([string isEqualToString: @"inactive"]) {
        return RTCRtpTransceiverDirectionInactive;
    }
    else {
        NSAssert(NO, @"invalid direction %@", string);
        return RTCRtpTransceiverDirectionSendRecv;
    }
}

// MARK: - React Native Exports

// MARK: transceiverDirection:resolver:rejecter:

RCT_EXPORT_METHOD(transceiverDirection:(nonnull NSString *)valueTag
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    RTCRtpTransceiver *transceiver =
    [[WebRTCModule shared] transceiverForKey: valueTag];
    if (!transceiver) {
        reject(@"NotFoundError", @"transceiver is not found", nil);
    }
    resolve([RTCRtpTransceiver
             directionDescription: transceiver.direction]);
}

// MARK: transceiverSetDirection:value:resolver:rejecter:

RCT_EXPORT_METHOD(transceiverSetDirection:(nonnull NSString *)valueTag
                  value:(nonnull NSString *)value
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    RTCRtpTransceiver *transceiver = [[WebRTCModule shared] transceiverForKey: valueTag];

    if (!transceiver) {
        reject(@"NotFoundError", @"transceiver is not found", nil);
    }
    
    RTCRtpTransceiverDirection newDir = [RTCRtpTransceiver directionFromString: value];
    NSError *error = nil;
    [transceiver setDirection:newDir error:&error];
    if (error != nil) {
        reject(@"SetDirectionFailed", error.localizedDescription, error);
    }
    resolve([NSNull null]);
}

// MARK: transceiverCurrentDirection:resolver:rejecter:

RCT_EXPORT_METHOD(transceiverCurrentDirection:(nonnull NSString *)valueTag
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    RTCRtpTransceiver *transceiver = [[WebRTCModule shared] transceiverForKey: valueTag];
    if (!transceiver) {
        reject(@"NotFoundError", @"transceiver is not found", nil);
    }
  
    RTCRtpTransceiverDirection dir;
    id ret;
    if ([transceiver currentDirection: &dir]) {
        ret = [RTCRtpTransceiver directionDescription: dir];
    }
    else {
        ret = [NSNull null];
    }
    resolve(ret);
}

// MARK: transceiverStop:resolver:rejecter:

RCT_EXPORT_METHOD(transceiverStop:(nonnull NSString *)valueTag
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    RTCRtpTransceiver *transceiver = [[WebRTCModule shared] transceiverForKey: valueTag];
    if (!transceiver) {
        reject(@"NotFoundError", @"transceiver is not found", nil);
    }
    [transceiver stopInternal];
    resolve([NSNull null]);
}

@end

@implementation RTCPeerConnection (ReactNativeWebRTCKit)

static void *peerConnectionValueTagKey = "peerConnectionValueTag";
static void *peerConnectionMicrophoneInitializedKey = "peerConnectionMicrophoneInitialized";

- (BOOL)microphoneInitialized {
    return [objc_getAssociatedObject(self, peerConnectionMicrophoneInitializedKey) boolValue];
}

- (void)setMicrophoneInitialized:(BOOL)microphoneInitialized {
    objc_setAssociatedObject(self,
                             peerConnectionMicrophoneInitializedKey,
                             [NSNumber numberWithBool:microphoneInitialized],
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (nullable NSString *)valueTag {
    return objc_getAssociatedObject(self, peerConnectionValueTagKey);
}

- (void)setValueTag:(nullable NSString *)valueTag {
    objc_setAssociatedObject(self,
                             peerConnectionValueTagKey,
                             valueTag,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (void)closeAndFinish
{
    if (self.connectionState != RTCPeerConnectionStateConnected) {
        return;
    }
    [self close];
    [self finish];
}

- (void)finish
{
    // モジュールの管理から外す
    [SharedModule removePeerConnectionForKey: self.valueTag];
}

@end

@implementation WebRTCModule (RTCPeerConnection)

- (nullable RTCRtpParameters *)rtpParametersForValueTag:(NSString *)valueTag
{
    for (RTCRtpSender *sender in self.senders) {
        NSLog(@"# rtpParametersForValueTag: sender %@", [sender description]);
        if ([sender.valueTag isEqualToString: valueTag]) {
            return sender.parameters;
        }
    }
    for (RTCRtpReceiver *receiver in self.receivers) {
        NSLog(@"# rtpParametersForValueTag: receiver %@", [receiver description]);
        if ([receiver.valueTag isEqualToString: valueTag]) {
            return receiver.parameters;
        }
    }
    return nil;
}

- (nullable RTCRtpEncodingParameters *)rtpEncodingParametersForValueTag:(nonnull NSString *)valueTag ssrc:(nullable NSNumber *)ssrc
{
    RTCRtpParameters *params = [self rtpParametersForValueTag: valueTag];
    if (!params) {
        return nil;
    }
    for (RTCRtpEncodingParameters *encParams in params.encodings) {
        if ([encParams.ssrc isEqualToNumber: ssrc]) {
            return encParams;
        }
    }
    return nil;
}

- (void) dataChannelInit:(RTCDataChannel *)channel {
    // 新たに valueTag を紐付ける
    channel.valueTag = [self createNewValueTag];
    channel.delegate = self;
    [self addDataChannel:channel forKey:channel.valueTag];
}

#pragma mark - React Native Exports

/**
 * NOTE: RCT_EXPORT_METHOD などの React Native のマクロは
 * NS_ASSUME_NONNULL_BEGIN/END セクションを無視するため、
 * JavaScript 側から受け取る引数の nullability を明示的に指定しています。
 * ただし、 NSNumber に変換される number 型の引数は nonnull である必要があります。
 */

// MARK: -peerConnectionInit:constraints:valueTag:

RCT_EXPORT_METHOD(peerConnectionInit:(nonnull RTCConfiguration *)configuration
                  constraints:(nullable NSDictionary *)constraints
                  valueTag:(nonnull NSString *)valueTag) {
    RTCMediaConstraints *mediaConsts = [WebRTCUtils parseMediaConstraints: constraints];
    RTCPeerConnection *peerConnection =
    [self.peerConnectionFactory peerConnectionWithConfiguration:configuration
                                                    constraints: mediaConsts
                                                       delegate: self];
    peerConnection.valueTag = valueTag;
    peerConnection.microphoneInitialized = NO;
    [self addPeerConnection: peerConnection forKey: valueTag];
}

// MARK: -peerConnectionSetConfiguration:valueTag:

RCT_EXPORT_METHOD(peerConnectionSetConfiguration:(nonnull RTCConfiguration *)configuration
                  valueTag:(nonnull NSString *)valueTag) {
    RTCPeerConnection *peerConnection = [self peerConnectionForKey: valueTag];
    if (!peerConnection) {
        return;
    }
    
    [peerConnection setConfiguration:configuration];
}

// MARK: -peerConnectionAddTrack:streamIds:valueTag:

RCT_EXPORT_METHOD(peerConnectionAddTrack:(nonnull NSString *)trackValueTag
                  streamIds:(nonnull NSArray *)streamIds
                  valueTag:(nonnull NSString *)valueTag
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    RTCPeerConnection *peerConnection = [self peerConnectionForKey: valueTag];
    if (!peerConnection) {
        reject(@"NotFoundError", @"peer connection is not found", nil);
        return;
    }
    RTCMediaStreamTrack *track = [self trackForKey: trackValueTag];
    if (!track) {
        reject(@"NotFoundError", @"track is not found", nil);
        return;
    }

    RTCRtpSender *sender = [peerConnection addTrack: track
                                          streamIds: streamIds];
    if (!sender) {
        reject(@"PeerConnectionError", @"cannot add the track", nil);
        return;
    }
    sender.valueTag = [self createNewValueTag];
    [self addSender: sender forKey: sender.valueTag];
    sender.streamIds = streamIds;
    
    resolve([sender json]);
}

// MARK: -peerConnectionRemoveTrack:valueTag:

RCT_EXPORT_METHOD(peerConnectionRemoveTrack:(nonnull NSString *)senderValueTag
                  valueTag:(nonnull NSString *)valueTag
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    RTCPeerConnection *peerConnection = [self peerConnectionForKey: valueTag];
    if (!peerConnection) {
        reject(@"NotFoundError", @"peer connection is not found", nil);
        return;
    }
    RTCRtpSender *sender = [self senderForKey: senderValueTag];
    if (!sender) {
        reject(@"NotFoundError", @"sender is not found", nil);
        return;
    }
    
    [self removeSenderForKey: senderValueTag];
    if ([peerConnection removeTrack: sender]) {
        resolve(nil);
    }
    else {
        reject(@"RemoveTrackFailed", @"cannot remove track", nil);
    }
}

// MARK: -peerConnectionCreateOffer:constraints:resolver:rejecter:

RCT_EXPORT_METHOD(peerConnectionCreateOffer:(nonnull NSString *)valueTag
                  constraints:(nullable NSDictionary *)constraints
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    RTCPeerConnection *peerConnection =
    [self peerConnectionForKey: valueTag];
    if (!peerConnection) {
        return;
    }
    
    [peerConnection offerForConstraints:[WebRTCUtils parseMediaConstraints:constraints]
                      completionHandler:^(RTCSessionDescription *sdp, NSError *error) {
                          if (error) {
                              reject(@"CreateOfferFailed", error.userInfo[@"error"], error);
                          }
                          else {
                              NSString *type = [RTCSessionDescription stringForType:sdp.type];
                              resolve(@{@"sdp": sdp.sdp, @"type": type});
                          }
                      }];
}

// MARK: -peerConnectionCreateAnswer:constraints:resolver:rejecter:

RCT_EXPORT_METHOD(peerConnectionCreateAnswer:(nonnull NSString *)valueTag
                  constraints:(nullable NSDictionary *)constraints
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    RTCPeerConnection *peerConnection = [self peerConnectionForKey: valueTag];
    if (!peerConnection) {
        return;
    }
    
    [peerConnection answerForConstraints:[WebRTCUtils parseMediaConstraints:constraints]
                       completionHandler:^(RTCSessionDescription *sdp, NSError *error) {
                           if (error) {
                               reject(@"CreateAnswerFailed", error.userInfo[@"error"], error);
                            }
                            else {
                               NSString *type = [RTCSessionDescription stringForType:sdp.type];
                               resolve(@{@"sdp": sdp.sdp, @"type": type});
                           }
                       }];
}

// MARK: -peerConnectionSetLocalDescription:valueTag:resolver:rejecter:

RCT_EXPORT_METHOD(peerConnectionSetLocalDescription:(nonnull RTCSessionDescription *)sdp
                  valueTag:(nonnull NSString *)valueTag
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    RTCPeerConnection *peerConnection = [self peerConnectionForKey: valueTag];
    if (!peerConnection) {
        return;
    }

    [peerConnection setLocalDescription:sdp
                      completionHandler: ^(NSError *error) {
                          if (error) {
                              reject(@"SetLocalDescriptionFailed", error.localizedDescription, error);
                          }
                          else {
                              resolve(nil);
                          }
                      }];
}

// MARK: -peerConnectionSetRemoteDescription:valueTag:resolver:rejecter:

RCT_EXPORT_METHOD(peerConnectionSetRemoteDescription:(nonnull RTCSessionDescription *)sdp
                  valueTag:(nonnull NSString *)valueTag
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    RTCPeerConnection *peerConnection = [self peerConnectionForKey: valueTag];
    if (!peerConnection) {
        return;
    }
    
    // マイクの初期化処理
    BOOL microphoneEnabled = [[WebRTCModule shared] microphoneEnabled];
    BOOL microphoneInitialized = peerConnection.microphoneInitialized;
    if (microphoneEnabled && !microphoneInitialized) {
        RTCAudioSessionConfiguration.webRTCConfiguration.category = AVAudioSessionCategoryPlayAndRecord;
        RTCAudioSession *session = [RTCAudioSession sharedInstance];
        [session initializeInput:^(NSError * _Nullable error) {
            if (error != NULL) {
                NSLog(@"failed to initialize audio input: %@", error);
                return;
            }
            peerConnection.microphoneInitialized = YES;
            NSLog(@"audio input is initialized");
        }];
    }

    [peerConnection setRemoteDescription:sdp
                       completionHandler: ^(NSError *error) {
                           if (error) {
                               reject(@"SetRemoteDescriptionFailed", error.localizedDescription, error);
                           } else {
                               resolve(nil);
                           }
                       }];
}

// MARK: -peerConnectionAddICECandidate:valueTag:resolver:rejecter:

RCT_EXPORT_METHOD(peerConnectionAddICECandidate:(nonnull RTCIceCandidate*)candidate
                  valueTag:(nonnull NSString *)valueTag
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    RTCPeerConnection *peerConnection = [self peerConnectionForKey: valueTag];
    if (!peerConnection) {
        return;
    }
    
    [peerConnection addIceCandidate:candidate];
    resolve(nil);
}

// MARK: -peerConnectionClose:

RCT_EXPORT_METHOD(peerConnectionClose:(nonnull NSString *)valueTag)
{
    RTCPeerConnection *peerConnection = [self peerConnectionForKey: valueTag];
    if (!peerConnection) {
        return;
    }
    
    [peerConnection closeAndFinish];
}

// MARK: -rtpEncodingParametersSetActive:ssrc:ownerValueTag:

RCT_EXPORT_METHOD(rtpEncodingParametersSetActive:(BOOL)flag
                  ssrc:(nonnull NSNumber *)ssrc
                  ownerValueTag:(nonnull NSString *)ownerValueTag)
{
    RTCRtpEncodingParameters *params =
    [self rtpEncodingParametersForValueTag: ownerValueTag
                                      ssrc: ssrc];
    if (params && [params.ssrc isEqualToNumber: ssrc]) {
        params.isActive = flag;
    }
}

// MARK: -rtpEncodingParametersSetMaxBitrate:ssrc:ownerValueTag:

RCT_EXPORT_METHOD(rtpEncodingParametersSetMaxBitrate:(nonnull NSNumber *)bitrate
                  ssrc:(nonnull NSNumber *)ssrc
                  ownerValueTag:(nonnull NSString *)ownerValueTag)
{
    RTCRtpEncodingParameters *params =
    [self rtpEncodingParametersForValueTag: ownerValueTag
                                      ssrc: ssrc];
    if (params && [params.ssrc isEqualToNumber: ssrc]) {
        if ([bitrate intValue] >= 0) {
            params.maxBitrateBps = bitrate;
        }
        else {
            params.maxBitrateBps = nil;
        }
    }
}

// MARK: -rtpEncodingParametersSetMinBitrate:ssrc:ownerValueTag:

RCT_EXPORT_METHOD(rtpEncodingParametersSetMinBitrate:(nonnull NSNumber *)bitrate
                  ssrc:(nonnull NSNumber *)ssrc
                  ownerValueTag:(nonnull NSString *)ownerValueTag)
{
    RTCRtpEncodingParameters *params =
    [self rtpEncodingParametersForValueTag: ownerValueTag
                                      ssrc: ssrc];
    if (params && [params.ssrc isEqualToNumber: ssrc]) {
        if ([bitrate intValue] >= 0) {
            params.minBitrateBps = bitrate;
        }
        else {
            params.minBitrateBps = nil;
        }
    }
}

// MARK: -peerConnectionCreateDataChannel:label:config:valueTag:resolver:rejecter

RCT_EXPORT_METHOD(peerConnectionCreateDataChannel: (NSString *)label
                  options:(nullable RTCDataChannelConfiguration *)options
                  valueTag: (NSString *) valueTag
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject)
{

  // valueTag に相当する peer Connection を見つける
  RTCPeerConnection *peerConnection = [self peerConnectionForKey: valueTag];
  if (!peerConnection) {
    // peer connection がなければ reject する
    reject(@"NotFoundError", @"peer connection is not found", nil);
    return;
  }
  RTCDataChannelConfiguration *config = [[RTCDataChannelConfiguration alloc] init];
  // options が nil でなければ config に代入して利用する
  // nil の場合は config の初期値が適用される
  if (options) {
    config = options;
  }
  // DataChannel を Peer Connection に追加する
  RTCDataChannel *dataChannel = [peerConnection dataChannelForLabel:label configuration:config];
  [self dataChannelInit:dataChannel];
  resolve([dataChannel json]);
}

// MARK: -peerConnectionAddTransceiver:trackValueTag:valueTag:resolver:rejecter

RCT_EXPORT_METHOD(peerConnectionAddTransceiver:(nonnull NSString *)trackValueTag
                  valueTag:(nonnull NSString *)valueTag
                  init:(nullable RTCRtpTransceiverInit *)init
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject)
{

  // valueTag に相当する peer Connection を見つける
  RTCPeerConnection *peerConnection = [self peerConnectionForKey: valueTag];
  if (!peerConnection) {
    // peer connection がなければ reject する
    reject(@"NotFoundError", @"peer connection is not found", nil);
    return;
  }
  // trackValueTag に相当する peer Connection を見つける
  RTCMediaStreamTrack *track = [self trackForKey: trackValueTag];
  if (!track) {
      // track がなければ reject する
      reject(@"NotFoundError", @"track is not found", nil);
      return;
  }
  RTCRtpTransceiverInit *initParams = [[RTCRtpTransceiverInit alloc] init];
  // init が nil でなければ initParams に代入して利用する
  // nil の場合は initParams の初期値が適用される
  if (init) {
    initParams = init;
  }
  // transceiver を追加する
  RTCRtpTransceiver *transceiver = [peerConnection addTransceiverWithTrack: track init: initParams];
  // WebRTC module の管理下に入れる
  if (!transceiver) {
    // transceiver を作成できなければ reject する
    reject(@"NotFoundError", @"cannot add transceiver", nil);
    return;
  }
  transceiver.valueTag = [self createNewValueTag];
  [self addTransceiver: transceiver forKey: transceiver.valueTag];
  // JSON シリアライズして JS 側に返却
  NSLog(@"transceiver=> %@", transceiver);
  resolve([transceiver json]);
}

#pragma mark - RTCPeerConnectionDelegate

- (void)peerConnection:(RTCPeerConnection *)peerConnection
didChangeConnectionState:(RTCPeerConnectionState)newState
{
    if (newState == RTCPeerConnectionStateClosed) {
        [peerConnection finish];
    }
    
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"peerConnectionConnectionStateChanged"
                                                    body:@{@"valueTag": peerConnection.valueTag,
                                                           @"connectionState": [WebRTCUtils stringForPeerConnectionState:newState]}];
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection didChangeSignalingState:(RTCSignalingState)newState {
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"peerConnectionSignalingStateChanged"
                                                    body:@{@"valueTag": peerConnection.valueTag,
                                                           @"signalingState": [WebRTCUtils stringForSignalingState:newState]}];
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
          didAddStream:(RTCMediaStream *)stream
{
    stream.valueTag = [self createNewValueTag];
    [self addStream: stream forKey: stream.valueTag];
    
    for (RTCMediaStreamTrack *track in stream.allTracks) {
        track.valueTag = [self createNewValueTag];
        [self addTrack: track forKey: track.valueTag];
    }
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
       didRemoveStream:(RTCMediaStream *)stream
{
    if (stream.valueTag) {
        [WebRTCValueManager removeValueTagForObject: stream];
        [self removeStreamForKey: stream.valueTag];
    }
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
        didAddReceiver:(RTCRtpReceiver *)rtpReceiver
               streams:(NSArray<RTCMediaStream *> *)mediaStreams
{
    rtpReceiver.valueTag = [self createNewValueTag];
    [self addReceiver: rtpReceiver forKey: rtpReceiver.valueTag];
    NSMutableArray *streamIds = [[NSMutableArray alloc] init];
    for (RTCMediaStream *stream in mediaStreams) {
        [streamIds addObject: stream.streamId];
    }
    rtpReceiver.streamIds = streamIds;
    
    [self.bridge.eventDispatcher
     sendDeviceEventWithName: @"peerConnectionAddedReceiver"
     body:@{@"valueTag": peerConnection.valueTag,
            @"receiver": [rtpReceiver json]}];
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
     didRemoveReceiver:(RTCRtpReceiver *)rtpReceiver
{
    if (rtpReceiver.valueTag != nil) {
        [self removeReceiverForKey: rtpReceiver.valueTag];
        [WebRTCValueManager removeValueTagForString: rtpReceiver.receiverId];
    }

    [self.bridge.eventDispatcher
     sendDeviceEventWithName: @"peerConnectionRemovedReceiver"
     body:@{@"valueTag": peerConnection.valueTag,
            @"receiver": [rtpReceiver json]}];
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
didStartReceivingOnTransceiver:(RTCRtpTransceiver *)transceiver
{
    transceiver.valueTag = [self createNewValueTag];
    transceiver.receiver.valueTag = [self createNewValueTag];
    transceiver.sender.valueTag = [self createNewValueTag];
    [self addTransceiver:transceiver forKey:transceiver.valueTag];
    [self addSender:transceiver.sender forKey:transceiver.sender.valueTag];
    [self addReceiver: transceiver.receiver forKey: transceiver.receiver.valueTag];
    [self.bridge.eventDispatcher
     sendDeviceEventWithName: @"peerConnectionStartTransceiver"
     body:@{@"valueTag": peerConnection.valueTag,
            @"transceiver": [transceiver json]}];
}

- (void)peerConnectionShouldNegotiate:(RTCPeerConnection *)peerConnection {
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"peerConnectionShouldNegotiate"
                                                    body:@{@"valueTag": peerConnection.valueTag}];
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection didChangeIceConnectionState:(RTCIceConnectionState)newState {
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"peerConnectionIceConnectionChanged"
                                                    body:@{@"valueTag": peerConnection.valueTag,
                                                           @"iceConnectionState": [WebRTCUtils stringForICEConnectionState:newState]}];
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection didChangeIceGatheringState:(RTCIceGatheringState)newState {
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"peerConnectionIceGatheringChanged"
                                                    body:@{@"valueTag": peerConnection.valueTag,
                                                           @"iceGatheringState": [WebRTCUtils stringForICEGatheringState:newState]}];
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection didGenerateIceCandidate:(RTCIceCandidate *)candidate {
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"peerConnectionGotICECandidate"
                                                    body:@{@"valueTag": peerConnection.valueTag,
                                                           @"candidate": @{
                                                                   @"candidate": candidate.sdp,
                                                                   @"sdpMLineIndex": @(candidate.sdpMLineIndex),
                                                                   @"sdpMid": candidate.sdpMid}}];
}

- (void)peerConnection:(RTCPeerConnection*)peerConnection didOpenDataChannel:(RTCDataChannel*)dataChannel {
    [self dataChannelInit:dataChannel];
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"peerConnectionOnDataChannel"
                                                        body:@{@"valueTag": peerConnection.valueTag,
                                                               @"channel": [dataChannel json]}];
}

// MARK: Deprecated

- (void)peerConnection:(RTCPeerConnection *)peerConnection didRemoveIceCandidates:(NSArray<RTCIceCandidate *> *)candidates {
    [peerConnection removeIceCandidates: candidates];
}

@end

NS_ASSUME_NONNULL_END
