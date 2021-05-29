// @flow

import { DeviceEventEmitter } from 'react-native';
import { NativeModules } from 'react-native';

import RTCMediaStream from '../MediaStream/RTCMediaStream';
import RTCMediaStreamTrack from '../MediaStream/RTCMediaStreamTrack';
import { RTCEvent, RTCMediaStreamTrackEvent, RTCIceCandidateEvent, RTCDataChannelEvent } from '../Event/RTCEvents';
import RTCIceCandidate from './RTCIceCandidate';
import RTCPeerConnectionEventTarget from './RTCPeerConnectionEventTarget';
import RTCSessionDescription from './RTCSessionDescription';
import RTCConfiguration from './RTCConfiguration';
import RTCRtpSender from './RTCRtpSender';
import RTCRtpReceiver from './RTCRtpReceiver';
import RTCRtpTransceiver from './RTCRtpTransceiver';
import type { RTCRtpTransceiverInit } from './RTCRtpTransceiver';
import logger from '../Util/RTCLogger';
import RTCMediaConstraints from './RTCMediaConstraints';
import RTCDataChannel from './RTCDataChannel';
import type { RTCDataChannelInit } from './RTCDataChannel';

/** @private */
const { WebRTCModule } = NativeModules;

/**
 * @package
 *
 * ネイティブレイヤーのオブジェクトに関連付けられたユニークな文字列です。
 * JavaScript からネイティブのオブジェクトを操作するために使われます。
 * 例えばタグが `foo` であれば、
 * ネイティブレイヤーのオブジェクトのうち、タグが `foo` である
 * オブジェクトを指定して操作できます。
 *
 * @typedef {string} ValueTag
 */
export type ValueTag = string;

/**
 * RTCPeerConnection の接続状態です。
 *
 * - `'new'`
 * - `'connecting'`
 * - `'connected'`
 * - `'disconnected'`
 * - `'failed'`
 * - `'closed'`
 *
 * @typedef {string} RTCPeerConnectionState
 */
export type RTCPeerConnectionState =
  | 'new'
  | 'connecting'
  | 'connected'
  | 'disconnected'
  | 'failed'
  | 'closed'

/**
 * RTCPeerConnection のシグナリング接続の状態です。
 *
 * - `'stable'`
 * - `'have-local-offer'`
 * - `'have-remote-offer'`
 * - `'have-local-pranswer'`
 * - `'have-remote-pranswer'`
 *
 * @typedef {string} RTCSignalingState
 */
export type RTCSignalingState =
  | 'stable'
  | 'have-local-offer'
  | 'have-remote-offer'
  | 'have-local-pranswer'
  | 'have-remote-pranswer'

/**
 * RTCPeerConnection の ICE ギャザリングの状態です。
 *
 * - `'new'`
 * - `'gathering'`
 * - `'complete'`
 *
 * @typedef {string} RTCIceGatheringState
 */
export type RTCIceGatheringState =
  | 'new'
  | 'gathering'
  | 'complete'

/**
 * RTCPeerConnection の ICE 接続の状態です。
 *
 * - `'new'`
 * - `'checking'`
 * - `'connected'`
 * - `'completed'`
 * - `'failed'`
 * - `'disconnected'`
 * - `'closed'`
 *
 * @typedef {string} RTCIceConnectionState
 */
export type RTCIceConnectionState =
  | 'new'
  | 'checking'
  | 'connected'
  | 'completed'
  | 'failed'
  | 'disconnected'
  | 'closed'

/**
 * RTCPeerConnection のタグを生成するために使われます。
 */
let nextPeerConnectionValueTag = 0;

/**
 * クライアントとの接続を表すオブジェクトです。
 */
export default class RTCPeerConnection extends RTCPeerConnectionEventTarget {

  /** @private */
  static nativeInit(valueTag: ValueTag,
    configuration: RTCConfiguration,
    constraints: RTCMediaConstraints) {
    WebRTCModule.peerConnectionInit(
      configuration.toJSON(), constraints.toJSON(), valueTag);
  }

  /** @private */
  static nativeAddICECandidate(valueTag: ValueTag,
    candidate: RTCIceCandidate): Promise<void> {
    return WebRTCModule.peerConnectionAddICECandidate(candidate.toJSON(), valueTag);
  }

  /** @private */
  static nativeAddTrack(valueTag: ValueTag,
    trackValueTag: ValueTag,
    streamIds: Array<string>,
  ): Promise<Object> {
    return WebRTCModule.peerConnectionAddTrack(trackValueTag, streamIds, valueTag);
  }

  /** @private */
  static nativeRemoveTrack(valueTag: ValueTag, senderValueTag: ValueTag) {
    WebRTCModule.peerConnectionRemoveTrack(senderValueTag, valueTag);
  }

  /** @private */
  static nativeClose(valueTag: ValueTag) {
    WebRTCModule.peerConnectionClose(valueTag);
  }

  /** @private */
  static nativeCreateAnswer(valueTag: ValueTag,
    constraints: RTCMediaConstraints): Promise<RTCSessionDescription> {
    return WebRTCModule.peerConnectionCreateAnswer(valueTag, constraints.toJSON());
  }

  /** @private */
  static nativeCreateOffer(valueTag: ValueTag,
    constraints: RTCMediaConstraints): Promise<RTCSessionDescription> {
    return WebRTCModule.peerConnectionCreateOffer(valueTag, constraints.toJSON());
  }

  /** @private */
  static nativeRemoveStream(valueTag: ValueTag, streamValueTag: ValueTag) {
    WebRTCModule.peerConnectionRemoveStream(streamValueTag, valueTag);
  }

  /** @private */
  static nativeSetConfiguration(valueTag: ValueTag,
    configuration: RTCConfiguration) {
    WebRTCModule.peerConnectionSetConfiguration(configuration.toJSON(), valueTag);
  }

  /** @private */
  static nativeSetLocalDescription(valueTag: ValueTag,
    sdp: RTCSessionDescription): Promise<void> {
    return WebRTCModule.peerConnectionSetLocalDescription(sdp.toJSON(), valueTag);
  }

  /** @private */
  static nativeSetRemoteDescription(valueTag: ValueTag, sdp: RTCSessionDescription): Promise<void> {
    return WebRTCModule.peerConnectionSetRemoteDescription(sdp.toJSON(), valueTag);
  }

  /** @private */
  static nativeCreateDataChannel(valueTag: ValueTag, label: string, options: RTCDataChannelInit | null): Promise<Object> {
    return WebRTCModule.peerConnectionCreateDataChannel(label, options, valueTag);
  }

  /** @private */
  static nativeAddTransceiver(
    valueTag: ValueTag,
    trackValueTag: ValueTag,
    init: RTCRtpTransceiverInit | null
  ): Promise<Object> {
    return WebRTCModule.peerConnectionAddTransceiver(
      trackValueTag,
      valueTag,
      init
    );
  }

  /**
   * クライアントとの総合的な接続状態を表します。
   */
  connectionState: RTCPeerConnectionState = 'new';

  /**
   * ICE 接続の状態を表します。
   */
  iceConnectionState: RTCIceConnectionState = 'new';

  /**
   * ICE ギャザリングの状態を表します。
   */
  iceGatheringState: RTCIceGatheringState = 'new';

  /**
   * シグナリング接続の状態を表します。
   */
  signalingState: RTCSignalingState = 'stable';

  /**
   * ローカルマシンで生成した SDP です。
   */
  localDescription: RTCSessionDescription | null;

  /**
   * リモートから受信した SDP です。
   */
  remoteDescription: RTCSessionDescription | null;

  /**
   * センダーのリスト。
   * リストの並びは順不同です。
   *
   * @since 1.1.0
   */
  senders: Array<RTCRtpSender> = [];

  /**
   * レシーバーのリスト。
   * リストの並びは順不同です。
   *
   * @since 1.1.0
   */
  receivers: Array<RTCRtpReceiver> = [];

  /**
   * トランシーバーのリスト。
   * リストの並びは順不同です。
   *
   * @since 1.1.0
   */
  transceivers: Array<RTCRtpTransceiver> = [];

  _valueTag: ValueTag;
  _nativeEventListeners: Array<any> = [];
  _constraints: RTCMediaConstraints;

  /**
   * オブジェクトを生成し、リモートのピアまたはサーバーに接続します。
   *
   * @param {RTCConfiguration|null} [configuration=null] 設定
   * @param {RTCMediaConstraints|null} [constraints=null] メディアに関する制約
  *
   * @listens {connectionstatechange} `RTCEvent`: `connectionState` が変更されると送信されます。
   * @listens {icecandidate} `RTCIceCandidateEvent`: ICE Candidate が生成されると送信されます。
   * @listens {iceconnectionstatechange} `RTCEvent`: `iceConnectionState` が変更されると送信されます。
   * @listens {icegatheringstatechange} `RTCIceCandidateEvent` | `RTCIceCandidateEvent`: `iceGatheringState` が変更されると送信されるイベント
   * @listens {negotiationneeded} `RTCEvent`: ネゴシエーションが必要になったときに送信されます。
   * @listens {signalingstatechange} `RTCEvent`: `signalingState` が変更されると送信されます。
   * @listens {track} `RTCEvent`: RTCPeerConnection にトラックが追加・削除されると送信されます。
   * @listens {datachannel} `RTCDataChannelEvent`: RTCPeerConnection に DataChannel がリモートから追加されると送信されます。
   * @listens {addstream} このイベントは廃止されました。
   * @listens {removestream} このイベントは廃止されました。
   */
  constructor(configuration: RTCConfiguration | null = null,
    constraints: RTCMediaConstraints | null = null) {
    super();
    if (configuration == null) {
      configuration = new RTCConfiguration();
    }
    if (constraints == null) {
      constraints = new RTCMediaConstraints();
    }
    this._valueTag = (nextPeerConnectionValueTag++).toString();
    this._constraints = constraints;
    RTCPeerConnection.nativeInit(this._valueTag,
      configuration,
      constraints);
    this._registerEventsFromNative();
  }

  /**
   * 接続を解除します。
   * すべてのストリームの接続も閉じられます。
   */
  close(): void {
    logger.log(`# PeerConnection[${this._valueTag}]: connection close`);
    this._finish();
  }

  _finish(): void {
    logger.log(`# PeerConnection[${this._valueTag}]: connection finish`);
    this.senders.forEach(sender => this.removeTrack(sender));
    RTCPeerConnection.nativeClose(this._valueTag);
  }

  /**
   * Answer SDP を生成します。
   *
   * @param {RTCMediaConstraints} constraints 制約
   * @return {Promise<RTCSessionDescription>} 生成結果を示す Promise
   */
  createAnswer(constraints: Object): Promise<RTCSessionDescription> {
    logger.log(`# PeerConnection[${this._valueTag}]: create answer`);
    return RTCPeerConnection.nativeCreateAnswer(this._valueTag, constraints)
      .then(data => new RTCSessionDescription(data.type, data.sdp));
  }

  /**
   * Offer SDP を生成します。
   *
   * @param {RTCMediaConstraints} constraints 制約
   * @return {Promise<RTCSessionDescription>} 生成結果を示す Promise
   */
  createOffer(constraints: RTCMediaConstraints): Promise<RTCSessionDescription> {
    logger.log(`# PeerConnection[${this._valueTag}]: create offer`);
    return RTCPeerConnection.nativeCreateOffer(this._valueTag, constraints)
      .then(data => new RTCSessionDescription(data.type, data.sdp));
  }

  /**
   * ICE candidate を追加します。
   *
   * @param {RTCIceCandidate} candidate ICE candidate
   * @return {Promise<Void>} 結果を示す Promise
   */
  addIceCandidate(candidate: RTCIceCandidate): Promise<void> {
    return RTCPeerConnection.nativeAddICECandidate(this._valueTag, candidate);
  }

  /**
   * @deprecated ストリームの操作は廃止されました。 senders を使用してください。
   *
   * @version 1.1.0
   */
  getLocalStreams(): Array<RTCMediaStream> {
    throw new Error("getLocalStream() is deprecated")
  }

  /**
   * @deprecated ストリームの操作は廃止されました。 receivers を使用してください。
   *
   * @version 1.1.0
   */
  getRemoteStreams(): Array<RTCMediaStream> {
    return this._remoteStreams.slice();
  }

  /**
   * @deprecated ストリームの操作は廃止されました。 addTrack() を使用してください。
   *
   * @version 1.1.0
   */
  addStream(stream: RTCMediaStream): void {
    throw new Error("addStream() is deprecated");
  }

  /**
   * @deprecated ストリームの操作は廃止されました。 addTrack() を使用してください。
   *
   * @version 1.1.0
   */
  addLocalStream(stream: RTCMediaStream): void {
    throw new Error("addLocalStream() is deprecated");
  }

  /**
   * @deprecated ストリームの操作は廃止されました。 removeTrack() を使用してください。
   *
   * @version 1.1.0
   */
  removeLocalStream(stream: RTCMediaStream): void {
    throw new Error("removeLocalStream() is deprecated")
  }

  /**
   * 指定したストリームにトラックを追加します。
   *
   * @param {RTCMediaStreamTrack} track 追加するトラック
   * @param {Array<string>} streamIds トラックを追加するストリーム ID
   * @return {Promise<RTCRtpSender>} 結果を表す Promise 。追加されたトラックを返す
   *
   * @since 1.1.0
   */
  addTrack(track: RTCMediaStreamTrack, streamIds: Array<string>): Promise<RTCRtpSender> {
    return RTCPeerConnection.nativeAddTrack(this._valueTag, track._valueTag, streamIds)
      .then((info) => {
        logger.log(`# PeerConnection[${this._valueTag}]: addTrack finished: sender => `, info);
        let sender = new RTCRtpSender(info);
        this.senders.push(sender);
        return sender;
      });
  }

  /**
   * 送信用のトラックを取り除きます。
   *
   * @param {RTCRtpSender} sender 取り除くトラック
   *
   * @since 1.1.0
   */
  removeTrack(sender: RTCRtpSender): Promise<void> {
    this.senders = this.senders.filter(
      e => e.id != sender.id);
    return RTCPeerConnection.nativeRemoveTrack(this._valueTag, sender._valueTag);
  }

  /**
   * 設定を反映します。
   *
   * @param {RTCConfiguration} configuration 設定
   */
  setConfiguration(configuration: RTCConfiguration): void {
    logger.log(`# PeerConnection[${this._valueTag}]: set configuration`);
    RTCPeerConnection.nativeSetConfiguration(this._valueTag, configuration);
  }

  /**
   * ローカルの SDP を設定します。
   *
   * @param {RTCSessionDescription} sessionDescription 設定する SDP
   * @return {Promise<void>} セットした結果を示す Promise
   */
  setLocalDescription(sessionDescription: RTCSessionDescription): Promise<void> {
    logger.log(`# PeerConnection[${this._valueTag}]: set local description`);
    return RTCPeerConnection.nativeSetLocalDescription(
      this._valueTag, sessionDescription).then(() => {
        this.localDescription = sessionDescription;
        return;
      });
  }

  /**
   * リモートの SDP を設定します。
   *
   * @param {RTCSessionDescription} sessionDescription 設定する SDP
   * @return {Promise<void>} セットした結果を示す Promise
   */
  setRemoteDescription(sessionDescription: RTCSessionDescription): Promise<void> {
    logger.log(`# PeerConnection[${this._valueTag}]: set remote description`);
    return RTCPeerConnection.nativeSetRemoteDescription(
      this._valueTag, sessionDescription)
      .then(() => {
        this.remoteDescription = sessionDescription;
        return;
      });
  }

  /**
  * DataChannel を作成します。
  *
  *  @param {string} label DataChannel の label
  *  @param {RTCDataChannelInit|null} options DataChannel で指定するオプション
  *  @return {Promise<RTCDataChannel>} 結果を表す Promise。作成した DataChannel を返す
  *
  *  @since 2020.3.0
  */
  createDataChannel(label: string, options: RTCDataChannelInit | null = null): Promise<RTCDataChannel> {
    logger.log(`# PeerConnection[${this._valueTag}]: create data channel`);
    // maxPacketLifeTime と maxRetransmits を両方指定された場合はエラーを投げる
    if (options && options.maxPacketLifeTime && options.maxRetransmits) {
      throw new Error("maxRetransmits and maxPacketLifeTime should not be both set.");
    }
    return RTCPeerConnection.nativeCreateDataChannel(
      this._valueTag,
      label,
      options
    ).then((info) => {
      logger.log(`# PeerConnection[${this._valueTag}]: createDataChannel finished: channel => `, info);
      return new RTCDataChannel(info);
    })
  }

  /**
   * RTCRtpTransceiver を追加します。
   *
   *  @param {RTCMediaStreamTrack} track 追加する track
   *  @param {RTCRtpTransceiverInit|null} init RTCRtpTransceiver の初期化情報
   *  @return {Promise<RTCRtpTransceiver>} 結果を表す Promise。作成した Transceiver を返す
   *  TODO(kdxu): リリース時にあわせて @since タグを更新する
   *  @since 2020.x.0
   */
  addTransceiver(
    track: RTCMediaStreamTrack,
    init: RTCRtpTransceiverInit | null
  ): Promise<RTCRtpTransceiver> {
    logger.log(`# PeerConnection[${this._valueTag}]: add transceiver`);
    return RTCPeerConnection.nativeAddTransceiver(
      this._valueTag,
      track._valueTag,
      init
    ).then((info) => {
      logger.log(
        `# PeerConnection[${this._valueTag}]: addTransceiver finished: channel => `,
        info
      );
      const trans = new RTCRtpTransceiver(info);
      this.transceivers.push(trans);
      return trans;
    });
  }

  _registerEventsFromNative(): void {
    logger.log(`# PeerConnection[${this._valueTag}]: register events from native`);
    this._nativeEventListeners = [
      DeviceEventEmitter.addListener('peerConnectionShouldNegotiate', ev => {
        if (ev.valueTag !== this._valueTag) {
          return;
        }
        logger.log(`# PeerConnection[${this._valueTag}]: event: peerConnectionShouldNegotiate`);
        this.dispatchEvent(new RTCEvent('negotiationneeded'));
      }),

      DeviceEventEmitter.addListener('peerConnectionConnectionStateChanged', ev => {
        logger.log("# event: peerConnectionConnectionStateChanged =>", ev.connectionState);
        if (ev.valueTag !== this._valueTag) {
          return;
        }
        this.connectionState = ev.connectionState;
        this.dispatchEvent(new RTCEvent('connectionstatechange'));
        if (ev.connectionState === 'closed') {
          // This PeerConnection is done, clean up event handlers.
          this._unregisterEventsFromNative();
        }
      }),

      DeviceEventEmitter.addListener('peerConnectionIceConnectionChanged', ev => {
        if (ev.valueTag !== this._valueTag) {
          return;
        }
        logger.log(`# PeerConnection[${this._valueTag}]: event: peerConnectionIceConnectionChanged`);
        this.iceConnectionState = ev.iceConnectionState;
        this.dispatchEvent(new RTCEvent('iceconnectionstatechange'));
        if (ev.iceConnectionState === 'closed') {
          // This PeerConnection is done, clean up event handlers.
          this._unregisterEventsFromNative();
        }
      }),

      DeviceEventEmitter.addListener('peerConnectionSignalingStateChanged', ev => {
        if (ev.valueTag !== this._valueTag) {
          return;
        }
        logger.log(`# PeerConnection[${this._valueTag}]: event: peerConnectionSignalingStateChanged`);
        this.signalingState = ev.signalingState;
        this.dispatchEvent(new RTCEvent('signalingstatechange'));
      }),

      // NOTE(enm10k): ontrack イベントの種類について
      // 3 つの Listener から、 type = 'track' の RTCMediaStreamTrackEvent が dispatchEvent されますが、
      // それぞれ以下のユースケースを想定しています。
      //
      // - peerConnectionAddedReceiver    ... receiver のトラック追加
      // - peerConnectionRemovedReceiver  ... receiver のトラック削除
      // - peerConnectionStartTransceiver ... sender のトラック追加
      //
      // peerConnectionStartTransceiver からも、 transciever を通して receiver にアクセスできますが、
      // libwebrtc のソースコードを確認する限り、 peerConnectionStartTransceiver と同時に peerConnectionAddedReceiver も呼ばれるため、
      // receiver のトラック追加時に peerConnectionStartTransceiver の transciever をチェックする必要はありません。
      DeviceEventEmitter.addListener('peerConnectionAddedReceiver', ev => {
        if (ev.valueTag !== this._valueTag) {
          return;
        }
        logger.log(`# PeerConnection[${this._valueTag}]: event: peerConnectionAddedReceiver`);
        let receiver = new RTCRtpReceiver(ev.receiver);
        this.receivers.push(receiver);
        this.dispatchEvent(new RTCMediaStreamTrackEvent('track',
          { track: receiver.track, receiver: receiver }));
      }),

      DeviceEventEmitter.addListener('peerConnectionRemovedReceiver', ev => {
        if (ev.valueTag !== this._valueTag) {
          return;
        }
        logger.log(`# PeerConnection[${this._valueTag}]: event: peerConnectionRemovedReceiver`);
        let receiver = new RTCRtpReceiver(ev.receiver);
        this.receivers.push(receiver);
        this.dispatchEvent(new RTCMediaStreamTrackEvent('track',
          { track: receiver.track, receiver: receiver }));
      }),

      DeviceEventEmitter.addListener('peerConnectionStartTransceiver', ev => {
        if (ev.valueTag !== this._valueTag) {
          return;
        }
        logger.log(`# PeerConnection[${this._valueTag}]: event: peerConnectionStartTransceiver`);
        let trans = new RTCRtpTransceiver(ev.transceiver);
        this.transceivers.push(trans);
        this.dispatchEvent(new RTCMediaStreamTrackEvent('track',
          { track: trans.sender.track, transceiver: trans }));
      }),

      DeviceEventEmitter.addListener('peerConnectionGotICECandidate', ev => {
        if (ev.valueTag !== this._valueTag) {
          return;
        }
        logger.log(`# PeerConnection[${this._valueTag}]: event: peerConnectionGotICECandidate`);
        const candidate = new RTCIceCandidate(ev.candidate);
        this.dispatchEvent(new RTCIceCandidateEvent('icecandidate', { candidate }));
      }),

      DeviceEventEmitter.addListener('peerConnectionIceGatheringChanged', ev => {
        if (ev.valueTag !== this._valueTag) {
          return;
        }
        logger.log(`# PeerConnection[${this._valueTag}]: event: peerConnectionIceGatheringChanged`);
        this.iceGatheringState = ev.iceGatheringState;
        if (this.iceGatheringState === 'complete') {
          this.dispatchEvent(new RTCIceCandidateEvent('icecandidate'));
        }
        this.dispatchEvent(new RTCEvent('icegatheringstatechange'));
      }),

      DeviceEventEmitter.addListener('peerConnectionOnDataChannel', ev => {
        if (ev.valueTag !== this._valueTag) {
          return;
        }
        logger.log(`# PeerConnection[${this._valueTag}]: event: peerConnectionOnDataChannel`);
        const channel = new RTCDataChannel(ev.channel);
        this.dispatchEvent(new RTCDataChannelEvent('datachannel', channel));
      }),
    ]
  }

  _unregisterEventsFromNative(): void {
    logger.log(`# PeerConnection[${this._valueTag}]: unregister events from native`);
    this._nativeEventListeners.forEach(e => e.remove());
    this._nativeEventListeners = [];
  }

}
