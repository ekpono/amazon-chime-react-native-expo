import * as React from 'react';

import { AmazonChimeSdkComponentViewProps } from './AmazonChimeSdkComponent.types';

export default function AmazonChimeSdkComponentView(props: AmazonChimeSdkComponentViewProps) {
  return (
    <div>
      <span>{props.name}</span>
    </div>
  );
}
