package expo.modules.amazonchimesdkcomponent

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.AppContext
import com.amazonaws.services.chime.sdk.meetings.session.DefaultMeetingSession
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSession
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionConfiguration
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionCredentials
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionURLs
import com.amazonaws.services.chime.sdk.meetings.session.defaultUrlRewriter
import com.amazonaws.services.chime.sdk.meetings.audiovideo.AttendeeInfo
import com.amazonaws.services.chime.sdk.meetings.audiovideo.AudioVideoObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.SignalUpdate
import com.amazonaws.services.chime.sdk.meetings.audiovideo.VolumeUpdate
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.RemoteVideoSource
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileState
import com.amazonaws.services.chime.sdk.meetings.realtime.RealtimeObserver
import com.amazonaws.services.chime.sdk.meetings.realtime.datamessage.DataMessage
import com.amazonaws.services.chime.sdk.meetings.realtime.datamessage.DataMessageObserver
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionStatus
import com.amazonaws.services.chime.sdk.meetings.utils.logger.ConsoleLogger
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel
import android.util.Log


class AmazonChimeSdkComponentModule : Module() {
    private val logger = ConsoleLogger(LogLevel.DEBUG)

    private var isAudioSessionStopped = true
    private var isVideoSessionStopped = true

    companion object {
        // Event types
        const val CHIME_EVENT_ERROR = "OnError"
        const val CHIME_EVENT_MEETING_START = "OnMeetingStart"
        const val CHIME_EVENT_MEETING_END = "OnMeetingEnd"
        const val CHIME_EVENT_VIDEO_TILE_ADD = "OnAddVideoTile"
        const val CHIME_EVENT_VIDEO_TILE_REMOVE = "OnRemoveVideoTile"
        const val CHIME_EVENT_ATTENDEES_JOIN = "OnAttendeesJoin"
        const val CHIME_EVENT_ATTENDEES_LEAVE = "OnAttendeesLeave"
        const val CHIME_EVENT_ATTENDEES_MUTE = "OnAttendeesMute"
        const val CHIME_EVENT_ATTENDEES_UNMUTE = "OnAttendeesUnmute"
        const val CHIME_EVENT_DATA_MESSAGE_RECEIVE = "OnDataMessageReceive"

        // Additional data for attendee events
        private const val CHIME_EVENT_KEY_ATTENDEE_ID = "attendeeId"
        private const val CHIME_EVENT_KEY_EXTERNAL_USER_ID = "externalUserId"

        // Additional data for video tile events
        private const val CHIME_EVENT_KEY_VIDEO_TILE_ID = "tileId"
        private const val CHIME_EVENT_KEY_VIDEO_IS_LOCAL = "isLocal"
        private const val CHIME_EVENT_KEY_VIDEO_IS_SCREEN_SHARE = "isScreenShare"
        private const val CHIME_EVENT_KEY_VIDEO_ATTENDEE_ID = "attendeeId"
        private const val CHIME_EVENT_KEY_VIDEO_PAUSE_STATE = "pauseState"
        private const val CHIME_EVENT_KEY_VIDEO_VIDEO_STREAM_CONTENT_HEIGHT = "videoStreamContentHeight"
        private const val CHIME_EVENT_KEY_VIDEO_VIDEO_STREAM_CONTENT_WIDTH = "videoStreamContentWidth"

        // Additional data for data message
        private const val CHIME_EVENT_KEY_DATA_MESSAGE_DATA = "data"
        private const val CHIME_EVENT_KEY_DATA_MESSAGE_SENDER_ATTENDEE_ID = "senderAttendeeId"
        private const val CHIME_EVENT_KEY_DATA_MESSAGE_SENDER_EXTERNAL_USER_ID = "senderExternalUserId"
        private const val CHIME_EVENT_KEY_DATA_MESSAGE_THROTTLED = "throttled"
        private const val CHIME_EVENT_KEY_DATA_MESSAGE_TIMESTAMP_MS = "timestampMs"
        private const val CHIME_EVENT_KEY_DATA_MESSAGE_TOPIC = "topic"

        private const val TAG = "ExpoMeetingObservers"

        private const val WEBRTC_PERMISSION_REQUEST_CODE = 1
        private const val KEY_MEETING_ID = "MeetingId"
        private const val KEY_ATTENDEE_ID = "AttendeeId"
        private const val KEY_JOIN_TOKEN = "JoinToken"
        private const val KEY_EXTERNAL_ID = "ExternalUserId"
        private const val KEY_MEDIA_PLACEMENT = "MediaPlacement"
        private const val KEY_AUDIO_FALLBACK_URL = "AudioFallbackUrl"
        private const val KEY_AUDIO_HOST_URL = "AudioHostUrl"
        private const val KEY_TURN_CONTROL_URL = "TurnControlUrl"
        private const val KEY_SIGNALING_URL = "SignalingUrl"
        private const val TOPIC_CHAT = "chat"

        var meetingSession: MeetingSession? = null

        private var videoView: AmazonChimeSdkComponentView? = null
    }

    override fun definition() = ModuleDefinition {
        Name("AmazonChimeSdkComponent")

        Events("OnMeetingStart", "OnMeetingEnd", "OnError", "OnAttendeesUnmute", "OnSuccess", "OnCameraSwitchOn",
        // Attendee event keys
        CHIME_EVENT_ERROR,
        CHIME_EVENT_MEETING_START,
        CHIME_EVENT_MEETING_END,
        CHIME_EVENT_VIDEO_TILE_ADD,
        CHIME_EVENT_VIDEO_TILE_REMOVE,
        CHIME_EVENT_ATTENDEES_JOIN,
        CHIME_EVENT_ATTENDEES_LEAVE,
        CHIME_EVENT_ATTENDEES_MUTE,
        CHIME_EVENT_ATTENDEES_UNMUTE,
        CHIME_EVENT_DATA_MESSAGE_RECEIVE
    )
        // Define functions for JavaScript to call
        AsyncFunction("startMeeting") { meetingInfo: Map<String, Any?>, attendeeInfo: Map<String, Any?> ->
            if (meetingSession != null) {
                meetingSession?.audioVideo?.stop()
                meetingSession = null
            }


            val sessionConfig = createSessionConfiguration(meetingInfo, attendeeInfo)

            // Initialize meetingSession correctly
            meetingSession = sessionConfig?.let { config ->
                appContext.reactContext?.let { context -> 
                    DefaultMeetingSession(config, logger, context)
                }
            }

            if (meetingSession != null) {
                startAudioVideo()
                sendEvent("OnSuccess", mapOf("meetingId" to meetingSession?.configuration?.meetingId))
            } else {
                sendEvent("OnError", mapOf("message" to "Failed to create meeting session"))
            }
        }

        AsyncFunction("stopMeeting") {
            logger.info("AmazonChimeSdkComponentModule", "Called stopMeeting")
            sendEvent("OnMeetingEnd", mapOf("meetingStopped" to "Meeting has stopped"))
            meetingSession?.audioVideo?.stop()
        }

        AsyncFunction("setMute") { isMute: Boolean ->
            if (isMute) {
                meetingSession?.audioVideo?.realtimeLocalMute()
            } else {
                meetingSession?.audioVideo?.realtimeLocalUnmute()
            }
        }

        AsyncFunction("setCameraOn") { enabled: Boolean ->
            logger.info("AmazonChimeSdkComponentModule", "Called setCameraOn: $enabled")
            sendEvent("OnCameraSwitchOn", mapOf("camera" to enabled))
            if (enabled) {
                meetingSession?.audioVideo?.startLocalVideo()
            } else {
                meetingSession?.audioVideo?.stopLocalVideo()
            }
        }

        AsyncFunction("sendDataMessage") { topic: String, message: String, lifetimeMs: Int ->
            meetingSession?.audioVideo?.realtimeSendDataMessage(topic, message, lifetimeMs)
        }

        // swich camera
        AsyncFunction("switchCamera") {
            meetingSession?.audioVideo?.switchCamera()
        }

        View(AmazonChimeSdkComponentView::class) {
            Prop("tileId") { view: AmazonChimeSdkComponentView, tileId: Int ->
                meetingSession?.let { session ->
                    view.setTileId(tileId, session)
                } ?: logger.error("AmazonChimeSdkComponentModule", "MeetingSession is null; cannot bind video view")
            }

            // Prop("isOnTop") { view: AmazonChimeSdkComponentView, isOnTop: Boolean ->
            //     view.setZOrderMediaOverlay(isOnTop)
            // }
        }
    }

    // Function to send attendee update events
    public fun sendAttendeeUpdateEvent(eventName: String, attendeeInfo: AttendeeInfo) {
        val eventData = mapOf(
            CHIME_EVENT_KEY_ATTENDEE_ID to attendeeInfo.attendeeId,
            CHIME_EVENT_KEY_EXTERNAL_USER_ID to attendeeInfo.externalUserId
        )
        sendEvent(eventName, eventData)
    }

    private val observer = object : RealtimeObserver, VideoTileObserver, AudioVideoObserver, DataMessageObserver {

        override fun onAttendeesJoined(attendeeInfo: Array<AttendeeInfo>) {
            Log.d(TAG, "onAttendeesJoined called with ${attendeeInfo.size} attendees")
            
            attendeeInfo.forEach { attendee ->
                Log.i(TAG, "Processing join for attendee: ${attendee.attendeeId}")

                val eventData = mapOf(
                    "attendeeId" to attendee.attendeeId,
                    "externalUserId" to attendee.externalUserId,
                )

                sendEvent(CHIME_EVENT_ATTENDEES_JOIN, eventData)
            }
        }
        
      
    
        override fun onAttendeesLeft(attendeeInfo: Array<AttendeeInfo>) {
            attendeeInfo.forEach { attendee ->
                Log.i(TAG, "Processing join for attendee: ${attendee.attendeeId}")

                val eventData = mapOf(
                    "attendeeId" to attendee.attendeeId,
                    "externalUserId" to attendee.externalUserId
                )
                sendEvent(CHIME_EVENT_ATTENDEES_LEAVE, eventData)
            }
        }
    
        override fun onAttendeesDropped(attendeeInfo: Array<AttendeeInfo>) {
            attendeeInfo.forEach { attendee ->
                Log.i(TAG, "Processing join for attendee: ${attendee.attendeeId}")

                val eventData = mapOf(
                    "attendeeId" to attendee.attendeeId,
                    "externalUserId" to attendee.externalUserId
                )

                sendEvent(CHIME_EVENT_ATTENDEES_LEAVE, eventData)
            }
        }
    
        override fun onAttendeesMuted(attendeeInfo: Array<AttendeeInfo>) {
            attendeeInfo.forEach { attendee ->
                Log.i(TAG, "Processing join for attendee: ${attendee.attendeeId}")

                val eventData = mapOf(
                    "attendeeId" to attendee.attendeeId,
                    "externalUserId" to attendee.externalUserId
                )

                sendEvent(CHIME_EVENT_ATTENDEES_MUTE, eventData)
            }
        }
    
        override fun onAttendeesUnmuted(attendeeInfo: Array<AttendeeInfo>) {
            attendeeInfo.forEach { attendee ->
                Log.i(TAG, "Processing join for attendee: ${attendee.attendeeId}")

                val eventData = mapOf(
                    "attendeeId" to attendee.attendeeId,
                    "externalUserId" to attendee.externalUserId
                )

                sendEvent(CHIME_EVENT_ATTENDEES_UNMUTE, eventData)
            }
        }
    
        override fun onSignalStrengthChanged(signalUpdates: Array<SignalUpdate>) {
            // Not implemented for demo purposes
        }
    
        override fun onVolumeChanged(volumeUpdates: Array<VolumeUpdate>) {
            // Not implemented for demo purposes
        }
    
        override fun onVideoTileAdded(tileState: VideoTileState) {
            meetingSession?.let { session ->
                videoView?.setTileId(tileState.tileId, session)
            } ?: logger.error(TAG, "meetingSession or videoView is null; cannot set tileId")
        
            sendEvent(CHIME_EVENT_VIDEO_TILE_ADD, videoTileStateToMap(tileState))
        }
    
        override fun onVideoTilePaused(tileState: VideoTileState) {
            // Not implemented for demo purposes
        }
    
        override fun onVideoTileRemoved(tileState: VideoTileState) {
            sendEvent(CHIME_EVENT_VIDEO_TILE_REMOVE, videoTileStateToMap(tileState))
        }
    
        override fun onVideoTileResumed(tileState: VideoTileState) {
            // Not implemented for demo purposes
        }
    
        override fun onVideoTileSizeChanged(tileState: VideoTileState) {
            // Not implemented for demo purposes
        }
    
        override fun onAudioSessionCancelledReconnect() {
            // Not implemented for demo purposes
        }
    
        override fun onAudioSessionDropped() {
            // Not implemented for demo purposes
        }
    
        override fun onAudioSessionStarted(reconnecting: Boolean) {
            logger.info(TAG, "Received event for audio session started. Reconnecting: $reconnecting")
    
            if (!reconnecting) {
                isAudioSessionStopped = false
                isVideoSessionStopped = false
                sendEvent(CHIME_EVENT_MEETING_START, mapOf("meetingStop" to reconnecting))
            }
        }
    
        override fun onAudioSessionStartedConnecting(reconnecting: Boolean) {
            // Not implemented for demo purposes
        }
    
        override fun onAudioSessionStopped(sessionStatus: MeetingSessionStatus) {
            // Not implemented for demo purposes
            isAudioSessionStopped = true
            emitRnMeetingEndEventIfNeeded()
        }
    
        override fun onCameraSendAvailabilityUpdated(available: Boolean) {
            // Not implemented for demo purposes
        }
    
        override fun onConnectionBecamePoor() {
            // Not implemented for demo purposes
        }
    
        override fun onConnectionRecovered() {
            // Not implemented for demo purposes
        }
    
        override fun onVideoSessionStarted(sessionStatus: MeetingSessionStatus) {
            // Not implemented for demo purposes
        }
    
        override fun onVideoSessionStartedConnecting() {
            // Not implemented for demo purposes
        }
    
        override fun onVideoSessionStopped(sessionStatus: MeetingSessionStatus) {
            // Not implemented for demo purposes
            isVideoSessionStopped = true
            emitRnMeetingEndEventIfNeeded()
        }
    
        override fun onDataMessageReceived(dataMessage: DataMessage) {
            // eventEmitter.sendDataMessageEvent(CHIME_EVENT_DATA_MESSAGE_RECEIVE, dataMessage)
        }
    
        override fun onRemoteVideoSourceAvailable(sources: List<RemoteVideoSource>) {
            // Not implemented for demo purposes
        }
    
        override fun onRemoteVideoSourceUnavailable(sources: List<RemoteVideoSource>) {
            // Not implemented for demo purposes
        }
    
        fun emitRnMeetingEndEventIfNeeded() {
            if(isAudioSessionStopped && isVideoSessionStopped) {
                sendEvent(CHIME_EVENT_MEETING_END, mapOf("meetingEnd" to true))
            }
        }
    }

    private fun startAudioVideo() {
        meetingSession?.let {
            it.audioVideo.addRealtimeObserver(observer) // this observer is crashing the app
            it.audioVideo.addVideoTileObserver(observer)
            it.audioVideo.addAudioVideoObserver(observer)
            it.audioVideo.start()
            it.audioVideo.startRemoteVideo()
            sendEvent("OnMeetingStart", mapOf("meeting-started" to "meeting started"))
        }
    }

    private fun createSessionConfiguration(meetingInfo: Map<String, Any?>, attendeeInfo: Map<String, Any?>): MeetingSessionConfiguration? {
        return try {
            val meetingId = meetingInfo["MeetingId"] as? String ?: ""
            val attendeeId = attendeeInfo["AttendeeId"] as? String ?: ""
            val joinToken = attendeeInfo["JoinToken"] as? String ?: ""
            val externalUserId = attendeeInfo["ExternalUserId"] as? String ?: ""

            val mediaPlacement = meetingInfo["MediaPlacement"] as Map<String, String>
            val audioFallbackUrl = mediaPlacement?.get("AudioFallbackUrl") ?: ""
            val audioHostUrl = mediaPlacement?.get("AudioHostUrl") ?: ""
            val turnControlUrl = mediaPlacement?.get("TurnControlUrl") ?: ""
            val signalingUrl = mediaPlacement?.get("SignalingUrl") ?: ""

            MeetingSessionConfiguration(
                meetingId,
                MeetingSessionCredentials(attendeeId, externalUserId, joinToken),
                MeetingSessionURLs(audioFallbackUrl, audioHostUrl, turnControlUrl, signalingUrl, ::defaultUrlRewriter)
            )
        } catch (exception: Exception) {
            logger.error("AmazonChimeSdkComponentModule", "Error creating session configuration: ${exception.localizedMessage}")
            sendEvent("OnError", mapOf("message" to "Error creating session configuration: ${exception.localizedMessage}"))
            null
        }
    }
    // Function to send data message events
    public fun sendDataMessageEvent(eventName: String, dataMessage: DataMessage) {
        val eventData = mapOf(
            CHIME_EVENT_KEY_DATA_MESSAGE_DATA to dataMessage.text(),
            CHIME_EVENT_KEY_DATA_MESSAGE_SENDER_ATTENDEE_ID to dataMessage.senderAttendeeId,
            CHIME_EVENT_KEY_DATA_MESSAGE_SENDER_EXTERNAL_USER_ID to dataMessage.senderExternalUserId,
            CHIME_EVENT_KEY_DATA_MESSAGE_THROTTLED to dataMessage.throttled,
            CHIME_EVENT_KEY_DATA_MESSAGE_TIMESTAMP_MS to dataMessage.timestampMs.toDouble(),
            CHIME_EVENT_KEY_DATA_MESSAGE_TOPIC to dataMessage.topic
        )
        sendEvent(eventName, eventData)
    }

    // Function to send video tile events
    public fun sendVideoTileEvent(eventName: String, tileState: VideoTileState) {
        val eventData = mapOf(
            CHIME_EVENT_KEY_VIDEO_TILE_ID to tileState.tileId,
            CHIME_EVENT_KEY_VIDEO_IS_LOCAL to tileState.isLocalTile,
            CHIME_EVENT_KEY_VIDEO_IS_SCREEN_SHARE to tileState.isContent,
            CHIME_EVENT_KEY_VIDEO_ATTENDEE_ID to tileState.attendeeId,
            CHIME_EVENT_KEY_VIDEO_PAUSE_STATE to tileState.pauseState.ordinal,
            CHIME_EVENT_KEY_VIDEO_VIDEO_STREAM_CONTENT_HEIGHT to tileState.videoStreamContentHeight,
            CHIME_EVENT_KEY_VIDEO_VIDEO_STREAM_CONTENT_WIDTH to tileState.videoStreamContentWidth
        )
        sendEvent(eventName, eventData)
    }

    private fun emitRnMeetingEndEventIfNeeded() {
        if(isAudioSessionStopped && isVideoSessionStopped) {
            sendEvent(CHIME_EVENT_MEETING_END, mapOf("meetingEnd" to true))
        }
    }

    private fun attendeeInfoToMap(attendeeInfo: AttendeeInfo): Map<String, Any?> {
        return mapOf(
            "attendeeId" to attendeeInfo.attendeeId,
            "externalUserId" to attendeeInfo.externalUserId
        )
    }
    
    // Conversion for VideoTileState
    private fun videoTileStateToMap(videoTileState: VideoTileState): Map<String, Any?> {
        return mapOf(
            "tileId" to videoTileState.tileId,
            "isLocal" to videoTileState.isLocalTile,
            "isScreenShare" to videoTileState.isContent,
            "attendeeId" to videoTileState.attendeeId,
            "pauseState" to videoTileState.pauseState.ordinal,
            "videoStreamContentHeight" to videoTileState.videoStreamContentHeight,
            "videoStreamContentWidth" to videoTileState.videoStreamContentWidth
        )
    }
}
