import { describe, it, expect, afterEach } from 'vitest';
import { act, cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CallScreen } from '../src/components/CallScreen.js';
import { FakeCall, participant } from './fakeCall.js';

afterEach(cleanup);

describe('CallScreen', () => {
  it('shows the idle status and no controls before joining', () => {
    const call = new FakeCall();
    render(<CallScreen call={call} />);
    expect(screen.getByText('Ready')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'End' })).toBeNull();
  });

  it('renders the roster with self first as You and peers with quality', () => {
    const call = new FakeCall();
    render(<CallScreen call={call} selfParticipantId="p-self" />);
    act(() =>
      call.push({
        phase: 'connected',
        durationMs: 5000,
        quality: 'good',
        participants: [
          participant({ participantId: 'p-peer', metadata: { name: 'Ada' }, quality: 'poor' }),
          participant({ participantId: 'p-self' })
        ]
      })
    );
    expect(screen.getByText('In call')).toBeInTheDocument();
    expect(screen.getByText('00:05')).toBeInTheDocument();
    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('You');
    expect(items[1]).toHaveTextContent('Ada');
    expect(within(items[1]!).getByText('Poor')).toBeInTheDocument();
  });

  it('uses an injected label for the self row', () => {
    const call = new FakeCall();
    render(<CallScreen call={call} selfParticipantId="p-self" labels={{ you: 'Me' }} />);
    act(() =>
      call.push({ phase: 'connected', participants: [participant({ participantId: 'p-self' })] })
    );
    expect(screen.getByText('Me')).toBeInTheDocument();
  });

  it('toggles mute and ends the call through the binding', async () => {
    const user = userEvent.setup();
    const call = new FakeCall();
    let ended = false;
    render(<CallScreen call={call} onEnd={() => (ended = true)} />);
    act(() => call.push({ phase: 'connected' }));
    await user.click(screen.getByRole('button', { name: 'Mute' }));
    expect(call.setMuted).toHaveBeenCalledWith(true);
    expect(screen.getByRole('button', { name: 'Unmute' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'End' }));
    expect(call.leave).toHaveBeenCalledTimes(1);
    expect(ended).toBe(true);
  });

  it('surfaces the unlock prompt when audio is blocked', async () => {
    const user = userEvent.setup();
    const call = new FakeCall();
    render(<CallScreen call={call} />);
    act(() => call.push({ phase: 'connected', audioBlocked: true }));
    await user.click(screen.getByRole('button', { name: 'Enable audio' }));
    expect(call.unlockAudio).toHaveBeenCalledTimes(1);
  });

  it('surfaces the weak connection banner on fallback', () => {
    const call = new FakeCall();
    render(<CallScreen call={call} />);
    act(() => call.push({ phase: 'connected', fallback: true }));
    expect(screen.getByText('Weak connection')).toBeInTheDocument();
  });

  it('shows the aggregate quality while connecting', () => {
    const call = new FakeCall();
    render(<CallScreen call={call} />);
    act(() => call.push({ phase: 'connecting', quality: 'degraded' }));
    expect(screen.getByText('Connecting')).toBeInTheDocument();
    expect(screen.getByText('Fair')).toBeInTheDocument();
  });

  it('accepts custom slots for the control bar and participant tiles', () => {
    const call = new FakeCall();
    render(
      <CallScreen
        call={call}
        selfParticipantId="p-self"
        slots={{
          controlBar: () => <div>custom-controls</div>,
          participantTile: ({ participant: p }) => <li>slot-{p.participantId}</li>
        }}
      />
    );
    act(() =>
      call.push({ phase: 'connected', participants: [participant({ participantId: 'p-self' })] })
    );
    expect(screen.getByText('custom-controls')).toBeInTheDocument();
    expect(screen.getByText('slot-p-self')).toBeInTheDocument();
  });

  it('renders participants through a custom render function', () => {
    const call = new FakeCall();
    render(
      <CallScreen
        call={call}
        renderParticipant={(entry) => <li key={entry.participant.participantId}>fn-{entry.name}</li>}
      />
    );
    act(() =>
      call.push({ phase: 'connected', participants: [participant({ participantId: 'p-z' })] })
    );
    expect(screen.getByText('fn-p-z')).toBeInTheDocument();
  });

  it('reports the ended status after leaving', () => {
    const call = new FakeCall();
    render(<CallScreen call={call} />);
    act(() => call.push({ phase: 'left' }));
    expect(screen.getByText('Ended')).toBeInTheDocument();
  });
});
