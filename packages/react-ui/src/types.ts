import type { CSSProperties, ComponentType } from 'react';
import type { ParticipantView } from '@sauti/core';
import type { SautiCallBinding } from '@sauti/react';
import type { SautiLabels } from './labels.js';

export interface Styleable {
  className?: string;
  style?: CSSProperties;
}

export interface BindingProps extends Styleable {
  binding: SautiCallBinding;
  labels?: Partial<SautiLabels>;
}

export interface ControlBarProps extends BindingProps {
  onEnd?: () => void;
}

export interface ParticipantTileProps extends Styleable {
  participant: ParticipantView;
  isSelf: boolean;
  name: string;
  labels?: Partial<SautiLabels>;
}

export interface CallScreenSlots {
  controlBar?: ComponentType<ControlBarProps>;
  participantTile?: ComponentType<ParticipantTileProps>;
}

export function joinClass(...parts: Array<string | undefined | false>): string {
  return parts.filter(Boolean).join(' ');
}
