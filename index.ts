import {
  NativeModulesProxy,
  EventEmitter,
  Subscription,
} from "expo-modules-core";

// Import the native module. On web, it will be resolved to AmazonChimeSdkComponent.web.ts
// and on native platforms to AmazonChimeSdkComponent.ts
import AmazonChimeSdkComponentModule from "./src/AmazonChimeSdkComponentModule";
import AmazonChimeSdkComponentView from "./src/AmazonChimeSdkComponentView";
import {
  ChangeEventPayload,
  AmazonChimeSdkComponentViewProps,
} from "./src/AmazonChimeSdkComponent.types";


const chimeEventEmitter = new EventEmitter(
  AmazonChimeSdkComponentModule ?? NativeModulesProxy.AmazonChimeSdkComponent
);

const removeChimeListeners = (eventName: string) => {
  chimeEventEmitter.removeAllListeners(eventName);
};

// Listener management functions
const addChimeListener = (
  eventName: string,
  listener: (event: ChangeEventPayload) => void
): Subscription => {
  return chimeEventEmitter.addListener<ChangeEventPayload>(eventName, listener);
};

export default {
  initializeMeetingSession: async (config: any) =>
    AmazonChimeSdkComponentModule.initializeMeetingSession(config),
  startMeeting: async (meetingInfo: any, attendeeInfo: any): Promise<any> => {
    console.log(meetingInfo, attendeeInfo);
    await AmazonChimeSdkComponentModule.startMeeting(meetingInfo, attendeeInfo);
  },
  stopMeeting: async () => await AmazonChimeSdkComponentModule.stopMeeting(),
  muteAttendee: (isMuted: any) =>
    AmazonChimeSdkComponentModule.muteAttendee(isMuted),
  enableCamera: (isEnabled: any) =>
    AmazonChimeSdkComponentModule.setCameraOn(isEnabled),
  switchCamera: () => AmazonChimeSdkComponentModule.switchCamera(),
  //set mute
  setMute: (isMuted: Boolean) => AmazonChimeSdkComponentModule.setMute(isMuted),
  addListener: addChimeListener,
  removeListeners: removeChimeListeners,
};

export {
  AmazonChimeSdkComponentView,
  AmazonChimeSdkComponentViewProps,
  ChangeEventPayload,
};
