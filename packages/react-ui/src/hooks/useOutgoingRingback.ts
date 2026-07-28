import { useEffect, useRef } from 'react';
import type { SautiCallBinding } from '@sauti/react';
import { ringbackMode, type RingbackMode } from './ringbackMode.js';
import {
  createRingbackPort,
  type RingbackPort,
  type RingbackToneOptions
} from '../audio/ringbackPort.js';

export interface RingbackOptions {
  selfParticipantId?: string;
  enabled?: boolean;
  tone?: RingbackToneOptions;
  port?: RingbackPort;
}

export function useOutgoingRingback(
  binding: SautiCallBinding,
  options: RingbackOptions = {}
): RingbackMode {
  const { selfParticipantId, enabled, tone, port } = options;
  const mode: RingbackMode =
    enabled === false ? 'silent' : ringbackMode(binding, selfParticipantId);
  const desiredAudible = mode === 'ringing' && !binding.audioBlocked;

  const toneRef = useRef(tone);
  const suppliedPortRef = useRef(port);
  const portRef = useRef<RingbackPort | null>(null);
  const ownedRef = useRef(false);
  const appliedRef = useRef(false);

  useEffect(() => {
    if (desiredAudible === appliedRef.current) return;
    appliedRef.current = desiredAudible;
    if (desiredAudible) {
      if (portRef.current == null) {
        const supplied = suppliedPortRef.current;
        if (supplied != null) {
          portRef.current = supplied;
          ownedRef.current = false;
        } else {
          portRef.current = createRingbackPort(toneRef.current);
          ownedRef.current = true;
        }
      }
      portRef.current.start();
    } else {
      portRef.current?.stop();
    }
  }, [desiredAudible]);

  useEffect(() => {
    return () => {
      const active = portRef.current;
      if (active == null) return;
      active.stop();
      if (ownedRef.current) active.close();
    };
  }, []);

  return mode;
}
