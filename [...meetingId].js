import { useContext, useEffect, useRef, useState } from "react";
import {
    Alert,
    Animated,
    Image,
    Pressable,
    SafeAreaView,
    Text,
    View,
    StyleSheet,
    Dimensions,
} from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import AmazonChimeSdkComponent, {
    AmazonChimeSdkComponentView,
} from "@/modules/amazon-chime-sdk-component";
import Feather from "@expo/vector-icons/Feather";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import MaterialCommunityIcons from "@expo/vector-icons/MaterialCommunityIcons";
import API from "@/lib/API";
import { SvgCss } from "react-native-svg/css";
import DraggableView from "@/components/DraggableView";
import { useKeepAwake, deactivateKeepAwake } from "expo-keep-awake";

const attendeeNameMap = {};

const xml = `
<svg id="Layer_1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><defs><style>.cls-1{fill:#fff;}.cls-2{fill:#012528;isolation:isolate;opacity:.4;}</style></defs><g id="Group_2717"><circle id="Ellipse_160" class="cls-2" cx="20" cy="20" r="20"/><path id="Icon_ionic-ios-reverse-camera" class="cls-1" d="M27.99,15.25h-2.7c-.23,0-.44-.1-.59-.27-1.4-1.57-1.93-2.11-2.51-2.11h-4.22c-.58,0-1.15.53-2.56,2.11-.15.17-.36.26-.59.26h-.2v-.4c0-.22-.18-.4-.4-.4h-1.28c-.22,0-.4.18-.4.4v.4h-.37c-.88-.04-1.62.64-1.66,1.52v8.71c.03.9.76,1.62,1.66,1.65h15.83c.87-.04,1.55-.78,1.51-1.65v-8.71c.02-.81-.62-1.49-1.43-1.52-.03,0-.06,0-.08,0ZM22.59,24.18c-.04.12-.13.23-.24.3-.69.48-1.51.73-2.35.73-1.05,0-2.06-.41-2.82-1.12-.71-.65-1.18-1.53-1.34-2.48h-1.01c-.16,0-.28-.13-.28-.29,0-.01,0-.02,0-.04,0-.03.02-.06.03-.09l1.56-2.09c.09-.12.26-.14.38-.05.02.02.04.03.05.05l1.59,2.07c.09.13.07.3-.05.4-.01,0-.02.02-.03.02-.03.02-.06.03-.09.02h-1.03c.14.65.49,1.24.98,1.68,1.04.98,2.63,1.08,3.78.24.1-.06.21-.1.32-.1.16,0,.31.07.41.18.14.15.19.36.12.56h0ZM25.39,20.84l-1.54,2.12c-.09.12-.26.15-.39.06,0,0-.02-.01-.03-.02,0,0-.02-.02-.02-.03l-1.61-2.13c-.09-.13-.07-.31.06-.4.01,0,.02-.02.03-.02.03-.02.06-.02.09-.02h1.04c-.14-.68-.48-1.29-.98-1.76-1.03-.99-2.62-1.1-3.78-.26-.09.06-.2.1-.32.1-.15,0-.3-.06-.41-.18-.21-.23-.2-.59.03-.8.03-.02.05-.05.08-.06.69-.48,1.51-.73,2.35-.73,1.06,0,2.08.39,2.86,1.1.71.7,1.16,1.61,1.3,2.59h1.04s.06,0,.09.02c.14.08.19.27.11.41,0,0,0,.01-.01.02h0Z"/></g></svg>
`;

const { width: screenWidth, height: screenHeight } = Dimensions.get("window");

export default function Route() {
    useKeepAwake();
    const {
        meetingId: meetingParam,
        name,
        uuid,
        image,
    } = useLocalSearchParams();
    const draggableRef = useRef();


    const meetingId = meetingParam[0];

    // meeting state
    const [videoTiles, setVideoTiles] = useState([]);
    const [elapsedTime, setElapsedTime] = useState(0);
    const [startTime, setStartTime] = useState(Date.now());
    const [selfVideoEnabled, setSelfVideoEnabled] = useState(false);
    const [remoteVideoEnabled, setRemoteVideoEnabled] = useState(false);
    const [selfAudioEnabled, setSelfAudioEnabled] = useState(true);
    const [showShowButton, setShowShowButton] = useState(true);
    const [showNameAndTime, setShowNameAndTime] = useState(true);
    const [lastCall, setLastCall] = useState(false);
    const [attendees, setAttendees] = useState([]);

    const [isHangUp, setIsHangUp] = useState(false);

    // Tile States
    const [localTileId, setLocalTileId] = useState(null);
    const [remoteTileId, setRemoteTileId] = useState(null);

    // Animation
    const slideButtonInOut = useRef(new Animated.Value(0)).current;
    const slideNameAndTime = useRef(new Animated.Value(-100)).current;

    const { mutate: updateCallDuration, isError } = useUpdateCallDuration({
        meetingId,
    });

    //Start meeting
    useEffect(() => {
        const joinChimeMeeting = async () => {
            try {
                const request = await API.get(`meetings/join-meeting`, {
                    meetingId: meetingId,
                });

                if (request.error) {
                    alert("Error joining meeting");
                    // router.push("/dashboard");
                }

                const meetingInfo = request.data.meeting.Meeting;
                const attendeeInfo = request.data.attendee.Attendee;
                await AmazonChimeSdkComponent.startMeeting(meetingInfo, attendeeInfo);
            } catch (error) {
                console.error("Error joining meeting", error);
                alert("Error joining meeting");
                // router.push("/dashboard");
            }
        };

        try {
            joinChimeMeeting();
        } catch (error) {
            console.error("Error starting meeting", error);
            alert("Error starting meeting");
            // router.push("/dashboard");
        }

        startHeartbeat();
        useKeepAwake();

        return () => {
            deactivateKeepAwake();
        };
    }, []);

    //The possible fault is here
    useEffect(() => {
        const onAttendeesJoin = AmazonChimeSdkComponent.addListener(
            "OnAttendeesJoin",
            ({ attendeeId, externalUserId }) => {
                attendeeNameMap[attendeeId] = externalUserId.split("#")[1];
                setAttendees((prev) => [...prev, attendeeId]);
            }
        );

        const onAttendeesLeave = AmazonChimeSdkComponent.addListener(
            "OnAttendeesLeave",
            ({ attendeeId }) => {
                setAttendees((prev) => prev.filter((id) => id !== attendeeId));

                const currentElapsedTime = Math.floor((Date.now() - startTime) / 1000);
            }
        );

        // Video tile Add & Remove Handlers
        const onAddVideoTile = AmazonChimeSdkComponent.addListener(
            "OnAddVideoTile",
            (tileState) => {
                console.log("OnAddVideoTile", tileState);
                setVideoTiles((prev) => [...prev, tileState.tileId]);
                if (tileState.isLocal) {
                    setLocalTileId(tileState.tileId);
                    setSelfVideoEnabled(true);
                } else {
                    setRemoteTileId(tileState.tileId);
                    setRemoteVideoEnabled(true);
                }
            }
        );

        const onRemoveVideoTile = AmazonChimeSdkComponent.addListener(
            "OnRemoveVideoTile",
            (tileState) => {
                console.log("OnRemoveVideoTile", tileState);
                setVideoTiles((prev) =>
                    prev.filter((tileId) => tileId !== tileState.tileId)
                );
                if (tileState.isLocal) {
                    setLocalTileId(null);
                    setSelfVideoEnabled(false);
                } else {
                    setRemoteTileId(null);
                    setRemoteVideoEnabled(false);
                }
            }
        );

        const onError = AmazonChimeSdkComponent.addListener(
            "OnError",
            (errorType) => {
                if (errorType === "OnMaximumConcurrentVideoReached") {
                    Alert.alert(
                        "Video limit reached",
                        "Cannot enable more video streams"
                    );
                } else {
                    Alert.alert("Error", errorType);
                }
            }
        );

        const OnMeetingEnd = AmazonChimeSdkComponent.addListener(
            "OnMeetingEnd",
            (event) => {
                console.log("Meeting Ended");
            }
        );

        const onSuccess = AmazonChimeSdkComponent.addListener(
            "OnSuccess",
            (event) => {
                console.log("success");
                console.log(event);
            }
        );

        return () => {
            // Cleanup all listeners on unmount
            onAttendeesJoin.remove();
            onAttendeesLeave.remove();
            onAddVideoTile.remove();
            onRemoveVideoTile.remove();
            onError.remove();
            onSuccess.remove();
            OnMeetingEnd.remove();
        };
    }, []);

    useEffect(() => {
        // Slide in if `showShowButton` is true; slide out if false
        Animated.timing(slideButtonInOut, {
            toValue: showShowButton ? 0 : 100, // 0 = on-screen, 100 = off-screen
            duration: 300,
            useNativeDriver: true,
        }).start();
    }, [showShowButton]);

    useEffect(() => {
        // Slide down if `showNameAndTime` is true; slide up if false
        Animated.timing(slideNameAndTime, {
            toValue: showNameAndTime ? 10 : -200, // Adjust values to control end position
            duration: 300,
            useNativeDriver: true,
        }).start();
    }, [showNameAndTime]);

    useEffect(() => {
        if (startTime) {
            const timer = setInterval(() => {
                setElapsedTime(Math.floor((Date.now() - startTime) / 1000));
            }, 1000);

            return () => clearInterval(timer);
        }
    }, [startTime]);

    const startHeartbeat = () => {
        setInterval(() => {
            updateCallDuration();
        }, 5000); // Update every 5 second
    };

    const formatElapsedTime = (seconds) => {
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = seconds % 60;
        return `${String(minutes).padStart(2, "0")}:${String(
            remainingSeconds
        ).padStart(2, "0")}`;
    };

    const hangUp = async () => {
        await AmazonChimeSdkComponent.stopMeeting();
    };

    const toggleCamera = async () => {
        setSelfVideoEnabled(!selfVideoEnabled);
        await AmazonChimeSdkComponent.enableCamera(!selfVideoEnabled);
    };

    const switchCamera = async () => {
        await AmazonChimeSdkComponent.switchCamera();
    };

    const toggleAudio = async () => {
        setSelfAudioEnabled((prev) => !prev);
        await AmazonChimeSdkComponent.setMute(!selfAudioEnabled);
    };

    return (
        <View
            className={cn("flex-1 items-center bg-glare ")}
        >
            {/* Name and Time holder */}
            <Animated.View
                style={[
                    {
                        transform: [{ translateY: slideNameAndTime }],
                        position: "absolute",
                        zIndex: 100,
                    },
                ]}
            >
                <SafeAreaView
                    className="text-center mt-20"
                    style={{
                        zIndex: 1001,
                    }}
                >
                    <Text className="text-center text-white font-acumin-semibold text-xl">
                        {name}
                    </Text>
                    <Text className="text-center text-white font-acumin-semibold text-md">
                        {formatElapsedTime(elapsedTime)}
                    </Text>
                </SafeAreaView>
            </Animated.View>

            {/* SMALL GUY */}

            <Pressable
                style={{
                    zIndex: 1000,
                    position: "absolute",
                    top: 0,
                    right: 0,
                    opacity: selfVideoEnabled ? 1 : 0,
                }}
            >
                <DraggableView>
                    <SafeAreaView
                        className={cn(
                            " items-end justify-end  mr-2 mt-20 top-24 w-44 h-64 rounded-md"
                        )}
                    >
                        <View
                            style={{
                                width: "100%",
                                height: "100%",
                            }}
                            className="rounded-md items-end justify-end"
                        >
                            {selfVideoEnabled && (
                                <View className="rounded-md" style={styles.videoContainer}>
                                    <AmazonChimeSdkComponentView
                                        tileId={localTileId}
                                        isOnTop={true}
                                        style={styles.videoView}
                                    />
                                    <Pressable
                                        style={styles.switchCameraButton}
                                        onPress={switchCamera}
                                    >
                                        <SvgCss xml={xml} width="100%" height="100%" />
                                    </Pressable>
                                </View>
                            )}
                        </View>
                    </SafeAreaView>
                </DraggableView>
            </Pressable>

            {/* BIG GUY */}

            <Pressable
                className="h-full w-full items-center justify-center rounded-full"
                style={{
                    zIndex: 0,
                }}
            >
                <View
                    style={
                        remoteVideoEnabled
                            ? {
                                width: "100%",
                                height: "100%",
                                flexDirection: "row",
                                justifyContent: "space-around",
                                flex: 1,
                            }
                            : { width: 200, height: 200 }
                    }
                >
                    {remoteVideoEnabled ? (
                        <AmazonChimeSdkComponentView
                            style={{
                                width: "100%",
                                height: "100%",
                                zIndex: 0,
                                resizeMode: "cover",
                            }}
                            // resizeMode="cover"
                            tileId={remoteTileId}
                        />
                    ) : (
                        <Image
                            source={{
                                uri: remoteUrl,
                            }}
                            resizeMethod="resize"
                            height="100%"
                            width="100%"
                            style={{ borderRadius: 100 }}
                        />
                    )}
                </View>
            </Pressable>

            {/* Buttons */}
            <Animated.View
                className="absolute bottom-4 p-2 w-min flex-row justify-center items-center h-20 bg-[#000000B3] px-20 gap-4"
                style={[
                    {
                        backgroundColor: "rgba(0, 0, 0, 0.4)",
                        borderTopLeftRadius: 20,
                        borderTopRightRadius: 20,
                        borderBottomRightRadius: 20,
                        borderBottomLeftRadius: 20,
                    },
                    {
                        transform: [
                            {
                                translateY: slideButtonInOut.interpolate({
                                    inputRange: [0, 100],
                                    outputRange: [0, 200],
                                }),
                            },
                        ],
                    },
                ]}
            >
                <Button className={cn("rounded-full bg-white  ")} onPress={toggleAudio}>
                    <Feather
                        name={selfAudioEnabled ? "mic-off" : "mic"}
                        size={24}
                        color={selfAudioEnabled ? "red" : "#00B776"}
                    />
                </Button>
                <Pressable
                    className="h-16 w-16 bg-red-500 rounded-full items-center justify-center"
                    onPress={async () => await hangUp()}
                >
                    <MaterialCommunityIcons name="phone-hangup" size={35} color="white" />
                </Pressable>
                <Button className={cn("rounded-full bg-white")} onPress={toggleCamera}>
                    <Feather
                        name={selfVideoEnabled ? "video" : "video-off"}
                        size={24}
                        color={selfVideoEnabled ? "#00B776" : "red"}
                    />
                </Button>
            </Animated.View>
        </View>
    );
}

const styles = StyleSheet.create({
    videoContainer: {
        width: "100%", // Adjust as needed
        height: "100%", // Adjust as needed
        borderRadius: 15,
        overflow: "hidden",
    },
    videoView: {
        flex: 1,
    },
    draggableContainer: {
        width: 150,
        height: 100,
        borderRadius: 15,
        overflow: "hidden",
        backgroundColor: "#000", // Optional: Ensure the background isn't transparent
    },
    videoView: {
        width: "100%",
        height: "100%",
    },
    switchCameraButton: {
        position: "absolute",
        top: 0,
        right: 0,
        width: 40,
        height: 40,
    },
});
