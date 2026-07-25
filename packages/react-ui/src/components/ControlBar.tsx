import type { ReactElement } from 'react';
import { joinClass, type ControlBarProps } from '../types.js';
import { MuteButton } from './MuteButton.js';
import { AudioDevicePicker } from './AudioDevicePicker.js';
import { EndCallButton } from './EndCallButton.js';

export function ControlBar({
  binding,
  labels,
  onEnd,
  className,
  style
}: ControlBarProps): ReactElement {
  return (
    <div className={joinClass('sauti-controlbar', className)} style={style}>
      <MuteButton binding={binding} labels={labels} />
      <AudioDevicePicker binding={binding} labels={labels} />
      <EndCallButton binding={binding} labels={labels} onEnd={onEnd} />
    </div>
  );
}