import { useId, type ChangeEvent, type ReactElement } from 'react';
import { useAudioDevices } from '../hooks/useAudioDevices.js';
import { resolveLabels } from '../labels.js';
import { joinClass, type BindingProps } from '../types.js';

export function AudioDevicePicker({
  binding,
  labels,
  className,
  style
}: BindingProps): ReactElement | null {
  const resolved = resolveLabels(labels);
  const { devices, selectedId, select } = useAudioDevices(binding);
  const id = useId();
  if (devices.length === 0) return null;
  const onChange = (event: ChangeEvent<HTMLSelectElement>): void => {
    void select(event.target.value);
  };
  return (
    <span className={joinClass('sauti-devicepicker', className)} style={style}>
      <label className="sauti-devicepicker__label" htmlFor={id}>
        {resolved.microphoneLabel}
      </label>
      <select
        id={id}
        className="sauti-devicepicker__select"
        value={selectedId ?? ''}
        onChange={onChange}
      >
        <option value="" disabled>
          {resolved.microphoneDefault}
        </option>
        {devices.map((device) => (
          <option key={device.deviceId} value={device.deviceId}>
            {device.label.trim().length > 0 ? device.label : resolved.microphoneLabel}
          </option>
        ))}
      </select>
    </span>
  );
}
