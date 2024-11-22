import { requireNativeViewManager } from 'expo-modules-core';
import * as React from 'react';

import { AmazonChimeSdkComponentViewProps } from './AmazonChimeSdkComponent.types';

const NativeView: React.ComponentType<AmazonChimeSdkComponentViewProps> =
  requireNativeViewManager('AmazonChimeSdkComponent');

export default function AmazonChimeSdkComponentView(props: AmazonChimeSdkComponentViewProps) {
  return <NativeView {...props} />;
}
