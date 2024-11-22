import ExpoModulesCore
import AmazonChimeSDK
import AmazonChimeSDKMedia

public class AmazonChimeSdkComponentModule: Module {
    private var logger = ConsoleLogger(name: "AmazonChimeSdkComponent", level: .DEBUG)
    private var isAudioSessionStopped = true
    private var isVideoSessionStopped = true
    private var meetingSession: DefaultMeetingSession?

    private enum EventKeys {
        static let meetingStart = "OnMeetingStart"
        static let meetingEnd = "OnMeetingEnd"
        static let error = "OnError"
        static let success = "OnSuccess"
        static let cameraSwitchOn = "OnCameraSwitchOn"
        static let addVideoTile = "OnAddVideoTile"
        static let removeVideoTile = "OnRemoveVideoTile"
        static let attendeesJoin = "OnAttendeesJoin"
        static let attendeesLeave = "OnAttendeesLeave"
        static let attendeesMute = "OnAttendeesMute"
        static let attendeesUnmute = "OnAttendeesUnmute"
        static let dataMessageReceive = "OnDataMessageReceive"
        static let audioSessionStartConnecting = "OnAudioSessionStartConnecting"
    }

    public func definition() -> ModuleDefinition {
        Name("AmazonChimeSdkComponent")

        Events(
            EventKeys.meetingStart,
            EventKeys.meetingEnd,
            EventKeys.error,
            EventKeys.success,
            EventKeys.cameraSwitchOn,
            EventKeys.addVideoTile,
            EventKeys.removeVideoTile,
            EventKeys.attendeesJoin,
            EventKeys.attendeesLeave,
            EventKeys.attendeesMute,
            EventKeys.attendeesUnmute,
            EventKeys.dataMessageReceive,
            EventKeys.audioSessionStartConnecting
        )

        AsyncFunction("startMeeting") { (meetingInfo: [String: Any], attendeeInfo: [String: Any]) in
            self.stopMeetingIfExists()
            guard let sessionConfig = self.createSessionConfiguration(meetingInfo: meetingInfo, attendeeInfo: attendeeInfo) else {
                self.sendEvent(EventKeys.error, ["message": "Failed to create meeting session"])
                return
            }
            // let context = UIApplication.shared
            self.meetingSession = DefaultMeetingSession(configuration: sessionConfig, logger: self.logger)
            self.startAudioVideo()
            self.sendEvent(EventKeys.success, ["meetingId": "Ekpono ambrose"])
        }

        AsyncFunction("stopMeeting") {
            self.logger.info(msg: "Called stopMeeting")
            self.sendEvent(EventKeys.meetingEnd, ["meetingStopped": "Meeting has stopped"])
            self.meetingSession?.audioVideo.stop()

            self.isAudioSessionStopped = true
            self.isVideoSessionStopped = true

            // remove tile
            self.sendEvent(EventKeys.removeVideoTile, ["tileId": 0])
            self.meetingSession = nil
        }

        AsyncFunction("setMute") { (isMute: Bool) in
            self.logger.info(msg: "Called setMute: \(isMute)")
            if isMute {
                _ = self.meetingSession?.audioVideo.realtimeLocalMute()
            } else {
                _ = self.meetingSession?.audioVideo.realtimeLocalUnmute()
            }
        }

        AsyncFunction("setCameraOn") { (enabled: Bool) in
            self.sendEvent(EventKeys.cameraSwitchOn, ["camera": enabled])

            do {
                if enabled {
                    try _ = self.meetingSession?.audioVideo.startLocalVideo()
                } else {
                    try _ = self.meetingSession?.audioVideo.stopLocalVideo()
                }
            } catch {
                self.sendEvent(EventKeys.error, ["message": "Failed to start local video"])
            }
            

        }

        //switch camera
        AsyncFunction("switchCamera") {
            self.logger.info(msg: "Called switchCamera")
            self.meetingSession?.audioVideo.switchCamera()
        }


        View(AmazonChimeSdkComponentView.self) {
            Prop("tileId") { (view: AmazonChimeSdkComponentView, tileId: Int) in
                guard let session = self.meetingSession else {
                    self.logger.error(msg: "MeetingSession is null; cannot bind video view")
                    return
                }
                view.setTileId(tileId: tileId, meetingSession: session)
            }
        }
    }

    private func startVideo() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if granted {
                    self.startVideo()
                } else {
                    self.sendEvent("OnError", ["message": "User denied camera permission"])
                }
            }
        case .authorized:
            do {
                try self.meetingSession?.audioVideo.startLocalVideo()
            } catch {
                self.sendEvent("OnError", ["message": "Failed to start local video"])
            }
        case .denied:
            UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!)
        default:
            break
        }
    }

    private func stopMeetingIfExists() {
        if let session = meetingSession {
            session.audioVideo.stop()
            meetingSession = nil
        }
    }

    private func startAudioVideo() {
    do {
        guard let session = meetingSession else { return }
        session.audioVideo.addRealtimeObserver(observer: observer)
        session.audioVideo.addVideoTileObserver(observer: observer)
        session.audioVideo.addAudioVideoObserver(observer: observer)
        try session.audioVideo.start()
        _ = session.audioVideo.startRemoteVideo()
        sendEvent(EventKeys.meetingStart, ["meetingStarted": "Meeting started"])
        } catch {
            logger.error(msg: "Failed to start audioVideo session: \(error)")
            sendEvent(EventKeys.error, ["message": "Failed to start audio video"])
        }
    }


    let urlRewriter: URLRewriter = { url in
        return url // Just return the input URL without any changes
    }

    private func createSessionConfiguration(meetingInfo: [String: Any], attendeeInfo: [String: Any]) -> MeetingSessionConfiguration? {
        guard let meetingId = meetingInfo["MeetingId"] as? String,
              let attendeeId = attendeeInfo["AttendeeId"] as? String,
              let joinToken = attendeeInfo["JoinToken"] as? String,
              let externalUserId = attendeeInfo["ExternalUserId"] as? String,
              let mediaPlacement = meetingInfo["MediaPlacement"] as? [String: String],
              let audioFallbackUrl = mediaPlacement["AudioFallbackUrl"],
              let audioHostUrl = mediaPlacement["AudioHostUrl"],
              let turnControlUrl = mediaPlacement["TurnControlUrl"],
              let signalingUrl = mediaPlacement["SignalingUrl"] else {
            // logger.error(msg: "Missing or invalid session configuration data")
            return nil
        }

        let credentials = MeetingSessionCredentials(attendeeId: attendeeId, externalUserId: externalUserId, joinToken: joinToken)
        let urls = MeetingSessionURLs(audioFallbackUrl: audioFallbackUrl, audioHostUrl: audioHostUrl, turnControlUrl: turnControlUrl, signalingUrl: signalingUrl, urlRewriter: urlRewriter)
        return MeetingSessionConfiguration(meetingId: meetingId, credentials: credentials, urls: urls, urlRewriter: urlRewriter)
    }

    private lazy var observer = Observer(self)

    @objc class Observer: NSObject, RealtimeObserver, AudioVideoObserver, VideoTileObserver {
        private let module: AmazonChimeSdkComponentModule

        init(_ module: AmazonChimeSdkComponentModule) {
            self.module = module
        }

        func attendeesDidJoin(attendeeInfo: [AttendeeInfo]) {
            attendeeInfo.forEach { attendee in
                module.sendEvent(EventKeys.attendeesJoin, ["attendeeId": attendee.attendeeId, "externalUserId": attendee.externalUserId])
            }
        }

        func attendeesDidLeave(attendeeInfo: [AttendeeInfo]) {
            attendeeInfo.forEach { attendee in
                module.sendEvent(EventKeys.attendeesLeave, ["attendeeId": attendee.attendeeId, "externalUserId": attendee.externalUserId])
            }
        }

        func attendeesDidDrop(attendeeInfo: [AttendeeInfo]) {
            attendeeInfo.forEach { attendee in
                module.sendEvent(EventKeys.attendeesLeave, ["attendeeId": attendee.attendeeId, "externalUserId": attendee.externalUserId])
            }
        }

        func attendeesDidMute(attendeeInfo: [AttendeeInfo]) {
            attendeeInfo.forEach { attendee in
                module.sendEvent(EventKeys.attendeesMute, ["attendeeId": attendee.attendeeId])
            }
        }

        func attendeesDidUnmute(attendeeInfo: [AttendeeInfo]) {
            attendeeInfo.forEach { attendee in
                module.sendEvent(EventKeys.attendeesUnmute, ["attendeeId": attendee.attendeeId])
            }
        }

        func signalStrengthDidChange(signalUpdates: [SignalUpdate]) {
            signalUpdates.forEach { update in
                module.logger.info(msg: "Attendee \(update.attendeeInfo.attendeeId) signalStrength changed to \(update.signalStrength)")
            }
        }

        func volumeDidChange(volumeUpdates: [VolumeUpdate]) {
            volumeUpdates.forEach { update in
                module.logger.info(msg: "Attendee \(update.attendeeInfo.attendeeId) volumeLevel changed to \(update.volumeLevel)")
            }
        }

        func audioSessionDidStartConnecting(reconnecting: Bool) {
            module.logger.info(msg: "Audio session is starting. Reconnecting: \(reconnecting)")
            module.sendEvent(EventKeys.audioSessionStartConnecting, ["reconnecting": reconnecting])
        }

        func audioSessionDidStart(reconnecting: Bool) {
            module.logger.info(msg: "Audio session started. Reconnecting: \(reconnecting)")
            module.sendEvent(EventKeys.meetingStart, ["reconnecting": reconnecting])
        }

        func audioSessionDidDrop() {
            module.logger.info(msg: "Audio session dropped due to poor network conditions.")
            // module.sendEvent(EventKeys.audioSessionDropped, ["reason": "poor network conditions"])
        }

        func audioSessionDidStopWithStatus(sessionStatus: MeetingSessionStatus) {
            module.logger.info(msg: "Audio session stopped. Status: \(sessionStatus.statusCode)")
            module.sendEvent(EventKeys.meetingEnd, ["status": sessionStatus.statusCode.rawValue])
        }

        func audioSessionDidCancelReconnect() {
            module.logger.info(msg: "Audio reconnection was canceled.")
            // module.sendEvent(EventKeys.audioSessionCancelReconnect, ["reason": "canceled"])
        }

        func connectionDidRecover() {
            module.logger.info(msg: "Connection health recovered.")
            // module.sendEvent(EventKeys.connectionRecovered, ["status": "recovered"])
        }

        func videoSessionDidStartConnecting() {
            module.logger.info(msg: "Video session is connecting.")
            // module.sendEvent(EventKeys.videoSessionStartConnecting, ["status": "connecting"])
        }

        func videoSessionDidStartWithStatus(sessionStatus: MeetingSessionStatus) {
            module.logger.info(msg: "Video session started with status: \(sessionStatus.statusCode)")
            // module.sendEvent(EventKeys.videoSessionStart, ["status": sessionStatus.statusCode.rawValue])
        }

        func videoSessionDidStopWithStatus(sessionStatus: MeetingSessionStatus) {
            module.logger.info(msg: "Video session stopped with status: \(sessionStatus.statusCode)")
            // module.sendEvent(EventKeys.videoSessionStop, ["status": sessionStatus.statusCode.rawValue])
        }

        func remoteVideoSourcesDidBecomeAvailable(sources: [RemoteVideoSource]) {
            module.logger.info(msg: "Remote video sources became available.")
            // let sourceIds = sources.map { $0.sourceId }
            // module.sendEvent(EventKeys.remoteVideoSourcesAvailable, ["sources": sourceIds])
        }

        func remoteVideoSourcesDidBecomeUnavailable(sources: [RemoteVideoSource]) {
            module.logger.info(msg: "Remote video sources became unavailable.")
            // let sourceIds = sources.map { $0.sourceId }
            // module.sendEvent(EventKeys.remoteVideoSourcesUnavailable, ["sources": sourceIds])
        }

        func cameraSendAvailabilityDidChange(available: Bool) {
            module.logger.info(msg: "Camera send availability changed. Available: \(available)")
            // module.sendEvent(EventKeys.cameraSendAvailabilityChanged, ["available": available])
        }

        func connectionDidBecomePoor() {
            module.logger.info(msg: "Connection has become poor.")
            // module.sendEvent(EventKeys.connectionPoor, ["status": "poor"])
        }

        func videoTileDidAdd(tileState: VideoTileState) {
            module.logger.info(msg: "Video tile added with state: \(tileState)")
            // Send event with tile details
            module.sendEvent(EventKeys.addVideoTile, 
            [
                "tileId": tileState.tileId, 
                "attendeeId": tileState.attendeeId,
                "isLocal": tileState.isLocalTile,
                "isScreenShare": tileState.isContent,
                "pauseState": tileState.pauseState.rawValue,
                "videoStreamContentWidth": tileState.videoStreamContentWidth,
                "videoStreamContentHeight": tileState.videoStreamContentHeight
            ])
        }

        func videoTileDidRemove(tileState: VideoTileState) {
            module.logger.info(msg: "Video tile removed with state: \(tileState)")
            // Send event with tile ID
            module.sendEvent(EventKeys.removeVideoTile, ["tileId": tileState.tileId])
        }

        func videoTileDidPause(tileState: VideoTileState) {
            module.logger.info(msg: "Video tile paused with state: \(tileState)")
            // Send event to indicate tile pause
            // module.sendEvent(EventKeys.videoTileDidPause, ["tileId": tileState.tileId])
        }

        func videoTileDidResume(tileState: VideoTileState) {
            module.logger.info(msg: "Video tile resumed with state: \(tileState)")
            // Send event to indicate tile resume
            // module.sendEvent(EventKeys.videoTileDidResume, ["tileId": tileState.tileId])
        }

        func videoTileSizeDidChange(tileState: VideoTileState) {
            module.logger.info(msg: "Video tile size changed with state: \(tileState)")
            // Send event with new size details
            // module.sendEvent(EventKeys.videoTileSizeDidChange, ["tileId": tileState.tileId, "width": tileState.videoStreamContentWidth, "height": tileState.videoStreamContentHeight])
        }

    }

    private func emitMeetingEndEventIfNeeded() {
        if isAudioSessionStopped && isVideoSessionStopped {
            sendEvent(EventKeys.meetingEnd, ["meetingEnd": true])
        }
    }
}
