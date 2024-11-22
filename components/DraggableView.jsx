import React, { useRef } from "react";
import { Animated, PanResponder, StyleSheet, Dimensions } from "react-native";

const { width: screenWidth, height: screenHeight } = Dimensions.get("window");

const DraggableView = ({ children }) => {
    const pan = useRef(new Animated.ValueXY()).current;

    const snapToCorner = (x, y) => {
        // Determine which corner is closest
        const topLeft = { x: 0, y: 0 };
        const topRight = { x: screenWidth - 150, y: 0 }; // Adjust for the draggable size
        const bottomLeft = { x: 0, y: screenHeight - 100 };
        const bottomRight = { x: screenWidth - 150, y: screenHeight - 100 };

        const corners = [topLeft, topRight, bottomLeft, bottomRight];
        const distances = corners.map(
            (corner) => Math.hypot(corner.x - x, corner.y - y) // Calculate distance
        );

        // Find the index of the closest corner
        const closestCornerIndex = distances.indexOf(Math.min(...distances));
        return corners[closestCornerIndex];
    };

    const panResponder = useRef(
        PanResponder.create({
            onStartShouldSetPanResponder: () => true,
            onPanResponderGrant: () => {
                pan.setOffset({
                    x: pan.x._value,
                    y: pan.y._value,
                });
                pan.setValue({ x: 0, y: 0 });
            },
            onPanResponderMove: Animated.event([null, { dx: pan.x, dy: pan.y }], {
                useNativeDriver: false,
            }),
            onPanResponderRelease: () => {
                pan.flattenOffset();
                // console.log("End Position:", pan.x._value, pan.y._value);
                // pan.flattenOffset();
                // const { x, y } = pan;
                // const snapPosition = snapToCorner(x._value, y._value);

                // console.log("Snap Position:", snapPosition);

                // // Animate to the closest corner
                // Animated.spring(pan, {
                //   toValue: snapPosition,
                //   useNativeDriver: false,
                // }).start();
            },
        })
    ).current;

    return (
        <Animated.View
            {...panResponder.panHandlers}
            style={[styles.box, { transform: pan.getTranslateTransform() }]}
        >
            {children}
        </Animated.View>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        justifyContent: "center",
        alignItems: "center",
    },
    box: {
        width: 150,
        height: 100,
        borderRadius: 4,
    },
});

export default DraggableView;
