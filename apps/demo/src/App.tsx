import { useMemo, useState, type ReactElement } from 'react';
import { createSautiCall } from '@sauti/core';
import { CallScreen } from '@sauti/react-ui';

interface TokenResponse {
  callToken: string;
  participantId: string;
  roomId: string;
}

function generateRoomId(): string {
  const alphabet = 'abcdefghijkmnpqrstuvwxyz23456789';
  const bytes = new Uint8Array(6);
  crypto.getRandomValues(bytes);
  let out = '';
  for (const byte of bytes) out += alphabet[byte % alphabet.length];
  return out;
}

function signalingUrl(): string {
  return `${window.location.origin.replace(/^http/, 'ws')}/ws`;
}

export function App(): ReactElement {
  const call = useMemo(() => createSautiCall(), []);
  const [roomId, setRoomId] = useState('');
  const [name, setName] = useState('');
  const [selfId, setSelfId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const join = async (): Promise<void> => {
    const room = roomId.trim().toLowerCase();
    if (!room) return;
    setBusy(true);
    setError(null);
    try {
      const response = await fetch('/token', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ roomId: room, name: name.trim() || undefined })
      });
      if (!response.ok) throw new Error('token request failed');
      const issued = (await response.json()) as TokenResponse;
      await call.join({ url: signalingUrl(), token: issued.callToken });
      setSelfId(issued.participantId);
    } catch {
      setError('Could not join. Is the dev server running, and did you allow the mic?');
    } finally {
      setBusy(false);
    }
  };

  const leave = (): void => {
    setSelfId(null);
  };

  if (selfId) {
    return (
      <main className="demo">
        <h1 className="demo__title">Sauti demo</h1>
        <p className="demo__hint">Room {roomId.trim().toLowerCase()}</p>
        <CallScreen call={call} selfParticipantId={selfId} onEnd={leave} />
      </main>
    );
  }

  return (
    <main className="demo">
      <h1 className="demo__title">Sauti demo</h1>
      <p className="demo__hint">
        Open this page in two tabs, enter the same room, allow the mic, and hear yourself.
      </p>
      <label className="demo__field">
        <span>Room</span>
        <span className="demo__row">
          <input
            value={roomId}
            onChange={(event) => setRoomId(event.target.value)}
            placeholder="room id"
          />
          <button type="button" onClick={() => setRoomId(generateRoomId())}>
            Generate
          </button>
        </span>
      </label>
      <label className="demo__field">
        <span>Display name</span>
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="optional"
        />
      </label>
      <button
        type="button"
        className="demo__join"
        disabled={busy || roomId.trim().length === 0}
        onClick={() => void join()}
      >
        {busy ? 'Joining…' : 'Join'}
      </button>
      {error && <p className="demo__error">{error}</p>}
    </main>
  );
}
