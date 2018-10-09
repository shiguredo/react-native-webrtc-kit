#import <objc/runtime.h>
#import "WebRTCModule+RTCMediaStream.h"

NS_ASSUME_NONNULL_BEGIN

@implementation RTCMediaStream (ReactNativeWebRTCKit)

static void *valueTagKey = "valueTag";

- (nullable NSString *)valueTag {
    return objc_getAssociatedObject(self, valueTagKey);
}

- (void)setValueTag:(nullable NSString *)valueTag {
    objc_setAssociatedObject(self, valueTagKey, valueTag, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (nullable RTCMediaStreamTrack *)trackForTrackId:(NSString *)trackId
{
    for (RTCMediaStreamTrack *track in self.videoTracks) {
        if ([track.trackId isEqualToString: trackId])
            return track;
    }
    for (RTCMediaStreamTrack *track in self.audioTracks) {
        if ([track.trackId isEqualToString: trackId])
            return track;
    }
    return nil;
}

- (nullable RTCAudioTrack *)createAudioTrack:(NSString *)trackId
{
    RTCAudioSource *audioSource = [self.peerConnectionFactory audioSourceWithConstraints: nil];
    RTCAudioTrack *audioTrack = [self.peerConnectionFactory audioTrackWithSource: audioSource trackId: trackId];
    return audioTrack;
}

- (nullable RTCVideoTrack *)createVideoTrack:(NSString *)trackId
{
    RTCVideoSource *videoSource = [self.peerConnectionFactory videoSource];
    RTCVideoTrack *videoTrack = [self.peerConnectionFactory  videoTrackWithSource: videoSource
                                                                          trackId: trackId];
    return videoTrack;
}


@end

@implementation RTCVideoTrack (ReactNativeWebRTCKit)

static void *aspectRatioKey = "aspectRatio";

- (CGFloat)aspectRatio
{
    NSNumber *ratio = objc_getAssociatedObject(self, aspectRatioKey);
    if (ratio)
        return (CGFloat)[ratio doubleValue];
    else
        return -1;
}

- (void)setAspectRatio:(CGFloat)aspectRatio
{
    objc_setAssociatedObject(self, aspectRatioKey,
                             [[NSNumber alloc] initWithDouble: aspectRatio],
                             OBJC_ASSOCIATION_COPY_NONATOMIC);
}

@end

@implementation WebRTCModule (RTCMediaStream)

// MARK: -trackSetEnabled:trackId:valueTag:

RCT_EXPORT_METHOD(trackSetEnabled:(nonnull NSNumber *)isEnabled
                  trackId:(nonnull NSString *)trackId
                  valueTag:(nonnull NSString *)valueTag)
{
    RTCMediaStream *stream = [self streamForValueTag: valueTag];
    if (stream) {
        RTCMediaStreamTrack *track = [stream trackForTrackId: trackId];
        if (track)
            track.isEnabled = [isEnabled boolValue];
    }
}


RCT_EXPORT_METHOD(addTrack:(nonnull NSString *)trackId
                  valueTag:(nonnull NSString *)valueTag
                  kind:(nonnull NSString*) kind)
{
    RTCMediaStream *stream = [self streamForValueTag: valueTag];
    if (stream) {
        if ([kind isEqualToString:@"audio"]) {
            RTCAudioTrack *track = [self createAudioTrack: trackId]
            [stream addAudioTrack:(RTCAudioTrack *)track];
        } else if([kind isEqualToString:@"video"]) {
            RTCAudioTrack *track = [self createVideoTrack: trackId]
            [stream addVideoTrack:(RTCVideoTrack *)track];
        }
    }
}

@end

NS_ASSUME_NONNULL_END
