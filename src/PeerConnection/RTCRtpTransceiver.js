// @flow

import { NativeModules } from 'react-native';
import RTCRtpSender from './RTCRtpSender';
import RTCRtpReceiver from './RTCRtpReceiver';
import { nativeBoolean } from '../Util/RTCUtil';
import type { ValueTag } from './RTCPeerConnection';

/** @private */
const { WebRTCModule } = NativeModules;

/**
 * {@link RTCRtpTransceiver} のトラックの送受信の方向を表します。
 * 
 * - `'sendrecv'`
 * - `'sendonly'`
 * - `'recvonly'`
 * - `'inactive'`
 * 
 * @typedef {string} RTCRtpTransceiverDirection
 * 
 * @since 1.1.0
 */
export type RTCRtpTransceiverDirection =
  | 'sendrecv'
  | 'sendonly'
  | 'recvonly'
  | 'inactive'

type RTCRtpSendEncodingParameters = {
  active?: boolean,
  rid?: string,
  scaleResolutionDownBy?: number,
  maxBitrate?: number,
  maxFramerate?: number,
};

/**
 * RTCRtpTransceiver の初期化オブジェクトです。
 * @typedef {Object} RTCRtpTransceiverInit
 * TODO(kdxu): since tag をリリース時に設定する
 * @since 2020.x.0
 */
export type RTCRtpTransceiverInit = {
  direction?: RTCRtpTransceiverDirection,
  sendEncodings: Array<RTCRtpSendEncodingParameters>,
  streamIds?: Array<string>,
  // dtx, codecPayloadType は RNKit ではサポートしない
};

/**
 * {@link RTCRtpSender} と {@link RTCRtpReceiver} のペアです。
 * 両方がデータを共有する場合に使われます。
 * 
 * @since 1.1.0
 */
export default class RTCRtpTransceiver {

  /** @private */
  static nativeDirection(valueTag: ValueTag): Promise<RTCRtpTransceiverDirection> {
    return WebRTCModule.transceiverDirection(valueTag)
  }

  /** @private */
  static nativeSetDirection(valueTag: ValueTag, value: RTCRtpTransceiverDirection) {
    WebRTCModule.transceiverSetDirection(valueTag, value)
  }

  /** @private */
  static nativeCurrentDirection(valueTag: ValueTag): Promise<RTCRtpTransceiverDirection> {
    return WebRTCModule.transceiverCurrentDirection(valueTag)
  }

  /** @private */
  static nativeStop(valueTag: ValueTag) {
    WebRTCModule.transceiverStop(valueTag);
  }

  /**
   * メディア ID
   */
  mid: string;

  /**
   * センダー
   */
  sender: RTCRtpSender;

  /**
   * レシーバー
   */
  receiver: RTCRtpReceiver;

  /**
   * センダーとレシーバーによるデータの送受信が停止されていれば `true`
   */
  stopped: boolean;

  _valueTag: ValueTag;

  /**
   * @package
   */
  constructor(info: Object) {
    this._valueTag = info.valueTag;
    this.mid = info.mid;
    this.sender = new RTCRtpSender(info.sender);
    this.receiver = new RTCRtpReceiver(info.receiver);
    this.stopped = nativeBoolean(info.stopped);
  }

  /**
   * センダーとレシーバーによるデータの送受信を停止します。
   * 一旦停止すると、送受信を再開することはできません。
   */
  stop() {
    RTCRtpTransceiver.nativeStop(this._valueTag);
    this.stopped = true;
  }

  /**
   * トランシーバーのデータ送受信の方向を表します。
   * この値は `RTCPeerConnection.createOffer` 及び
   * `RTCPeerConnection.createAnswer` の次回実行時に参照されます。
   */
  direction(): Promise<RTCRtpTransceiverDirection> {
    return RTCRtpTransceiver.nativeDirection(this._valueTag)
  }

  /**
   * トランシーバーのデータ送受信の方向を指定します。
   */
  setDirection(value: RTCRtpTransceiverDirection) {
    RTCRtpTransceiver.nativeSetDirection(this._valueTag, value)
  }

  /**
   * トランシーバーのデータ送受信の方向を指定します。
   * このメソッドの実行時点で使用されている値を返します。
   */
  currentDirection(): Promise<RTCRtpTransceiverDirection> {
    return RTCRtpTransceiver.nativeCurrentDirection(this._valueTag);
  }

}
