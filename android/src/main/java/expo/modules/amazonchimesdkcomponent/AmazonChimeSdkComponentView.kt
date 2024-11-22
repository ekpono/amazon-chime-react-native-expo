package expo.modules.amazonchimesdkcomponent

import android.content.Context
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import com.amazonaws.services.chime.sdk.meetings.utils.logger.ConsoleLogger
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.DefaultVideoRenderView
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSession

class AmazonChimeSdkComponentView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
        private val logger = ConsoleLogger(LogLevel.DEBUG)

        companion object {
                // The React Native side is expecting a component named RNVideoView
                private const val TAG = "AmazonChimeSdkComponentView"
        }

        internal val videoRenderView = DefaultVideoRenderView(context).also {
                it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                addView(it)
        }

        fun setTileId(tileId: Int, meetingSession: MeetingSession) {
                // logger.info(TAG, "Setting tileId: $tileId")
                meetingSession.audioVideo.bindVideoView(videoRenderView, tileId)
        }
}