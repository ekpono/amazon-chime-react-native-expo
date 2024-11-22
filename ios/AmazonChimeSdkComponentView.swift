import Foundation
import AmazonChimeSDK
import ExpoModulesCore

class AmazonChimeSdkComponentView: ExpoView {
    private var videoRenderView: DefaultVideoRenderView = {
        let view = DefaultVideoRenderView()
        view.contentMode = .scaleAspectFill 
        view.clipsToBounds = true          
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    required init(appContext: AppContext? = nil) {
        super.init(appContext: appContext)

        // Add the video render view to the parent
        addSubview(videoRenderView)

        // Make the video render view fill the entire screen
        NSLayoutConstraint.activate([
            videoRenderView.leadingAnchor.constraint(equalTo: leadingAnchor),
            videoRenderView.trailingAnchor.constraint(equalTo: trailingAnchor),
            videoRenderView.topAnchor.constraint(equalTo: topAnchor),
            videoRenderView.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func setTileId(tileId: Int, meetingSession: DefaultMeetingSession) {
        // Bind the video view with the specific tileId
        meetingSession.audioVideo.bindVideoView(videoView: videoRenderView, tileId: tileId)
    }
}
