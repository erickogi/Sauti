import { describe, it, expect, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MuteButton } from '../src/components/MuteButton.js';
import { EndCallButton } from '../src/components/EndCallButton.js';
import { QualityIndicator } from '../src/components/QualityIndicator.js';
import { DurationTimer } from '../src/components/DurationTimer.js';
import { CallStatus } from '../src/components/CallStatus.js';
import { ParticipantList } from '../src/components/ParticipantList.js';
import { ParticipantTile } from '../src/components/ParticipantTile.js';
import { AudioDevicePicker } from '../src/components/AudioDevicePicker.js';
import { AudioUnlockPrompt } from '../src/components/AudioUnlockPrompt.js';
import { WeakConnectionBanner } from '../src/components/WeakConnectionBanner.js';
import { ControlBar } from '../src/components/ControlBar.js';
import { SautiThemeProvider } from '../src/theme.js';
import { fakeBinding } from './fakeBinding.js';
import { participant } from './fakeCall.js';

afterEach(cleanup);

describe('MuteButton', () => {
  it('renders Mute and toggles to muted on click', async () => {
    const user = userEvent.setup();
    const binding = fakeBinding({ localMuted: false });
    render(<MuteButton binding={binding} />);
    const button = screen.getByRole('button', { name: 'Mute' });
    expect(button).toHaveAttribute('aria-pressed', 'false');
    await user.click(button);
    expect(binding.setMuted).toHaveBeenCalledWith(true);
  });

  it('renders Unmute when already muted and unmutes on click', async () => {
    const user = userEvent.setup();
    const binding = fakeBinding({ localMuted: true });
    render(<MuteButton binding={binding} labels={{ unmute: 'Turn on' }} />);
    const button = screen.getByRole('button', { name: 'Turn on' });
    expect(button).toHaveAttribute('aria-pressed', 'true');
    await user.click(button);
    expect(binding.setMuted).toHaveBeenCalledWith(false);
  });
});

describe('EndCallButton', () => {
  it('leaves the call and fires onEnd', async () => {
    const user = userEvent.setup();
    const binding = fakeBinding();
    let ended = false;
    render(<EndCallButton binding={binding} onEnd={() => (ended = true)} />);
    await user.click(screen.getByRole('button', { name: 'End' }));
    expect(binding.leave).toHaveBeenCalledTimes(1);
    expect(ended).toBe(true);
  });

  it('works without an onEnd handler', async () => {
    const user = userEvent.setup();
    const binding = fakeBinding();
    render(<EndCallButton binding={binding} />);
    await user.click(screen.getByRole('button', { name: 'End' }));
    expect(binding.leave).toHaveBeenCalledTimes(1);
  });
});

describe('QualityIndicator', () => {
  it('maps each quality to its label and token', () => {
    const { rerender } = render(<QualityIndicator quality="good" />);
    expect(screen.getByText('Good')).toHaveAttribute('data-quality', 'good');
    rerender(<QualityIndicator quality="degraded" />);
    expect(screen.getByText('Fair')).toHaveAttribute('data-quality', 'fair');
    rerender(<QualityIndicator quality="poor" />);
    expect(screen.getByText('Poor')).toHaveAttribute('data-quality', 'poor');
  });

  it('exposes a live region only when asked', () => {
    render(<QualityIndicator quality="good" live />);
    expect(screen.getByText('Good')).toHaveAttribute('role', 'status');
  });
});

describe('DurationTimer', () => {
  it('renders the formatted duration', () => {
    render(<DurationTimer durationMs={125000} />);
    expect(screen.getByText('02:05')).toBeInTheDocument();
  });
});

describe('CallStatus', () => {
  it('renders a polite status derived from the snapshot', () => {
    render(<CallStatus snapshot={fakeBinding({ phase: 'connected' })} />);
    const status = screen.getByText('In call');
    expect(status).toHaveAttribute('role', 'status');
    expect(status).toHaveAttribute('aria-live', 'polite');
    expect(status).toHaveAttribute('data-status', 'connected');
  });
});

describe('ParticipantList', () => {
  it('renders nothing when the roster is empty', () => {
    const { container } = render(<ParticipantList binding={fakeBinding()} />);
    expect(container.querySelector('ul')).toBeNull();
  });

  it('places self first as You without a quality pill and shows peer quality', () => {
    const binding = fakeBinding({
      participants: [
        participant({ participantId: 'p-peer', metadata: { name: 'Ada' }, quality: 'degraded' }),
        participant({ participantId: 'p-self', muted: true, onHold: true })
      ]
    });
    render(<ParticipantList binding={binding} selfId="p-self" />);
    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('You');
    expect(within(items[0]!).queryByText('Good')).toBeNull();
    expect(items[0]).toHaveTextContent('Muted');
    expect(items[0]).toHaveTextContent('On hold');
    expect(items[1]).toHaveTextContent('Ada');
    expect(within(items[1]!).getByText('Fair')).toBeInTheDocument();
  });

  it('supports a custom render function', () => {
    const binding = fakeBinding({ participants: [participant({ participantId: 'p-x' })] });
    render(
      <ParticipantList
        binding={binding}
        renderParticipant={(entry) => <li key={entry.participant.participantId}>row-{entry.name}</li>}
      />
    );
    expect(screen.getByText('row-p-x')).toBeInTheDocument();
  });

  it('supports a custom tile component', () => {
    const binding = fakeBinding({ participants: [participant({ participantId: 'p-x' })] });
    render(
      <ParticipantList
        binding={binding}
        tile={({ participant: p }) => <li>tile-{p.participantId}</li>}
      />
    );
    expect(screen.getByText('tile-p-x')).toBeInTheDocument();
  });
});

describe('ParticipantTile', () => {
  it('renders a peer name and no meta line when clean', () => {
    render(
      <ul>
        <ParticipantTile
          participant={participant({ participantId: 'p-a' })}
          isSelf={false}
          name="Ada"
        />
      </ul>
    );
    expect(screen.getByText('Ada')).toBeInTheDocument();
  });
});

describe('AudioDevicePicker', () => {
  it('renders nothing when there are no input devices', async () => {
    const binding = fakeBinding({}, []);
    const { container } = render(<AudioDevicePicker binding={binding} />);
    await waitFor(() => expect(binding.enumerateDevices).toHaveBeenCalled());
    expect(container.querySelector('select')).toBeNull();
  });

  it('lists input devices and selects one', async () => {
    const user = userEvent.setup();
    const binding = fakeBinding({}, [
      { deviceId: 'mic-1', kind: 'audioinput', label: 'Built-in' },
      { deviceId: 'mic-2', kind: 'audioinput', label: '' },
      { deviceId: 'spk-1', kind: 'audiooutput', label: 'Speaker' }
    ]);
    render(<AudioDevicePicker binding={binding} />);
    const select = await screen.findByRole('combobox', { name: 'Microphone' });
    expect(within(select).getByText('Built-in')).toBeInTheDocument();
    expect(screen.queryByText('Speaker')).toBeNull();
    await user.selectOptions(select, 'mic-1');
    expect(binding.selectInputDevice).toHaveBeenCalledWith('mic-1');
  });
});

describe('AudioUnlockPrompt', () => {
  it('is hidden until audio is blocked, then unlocks on click', async () => {
    const user = userEvent.setup();
    const blocked = fakeBinding({ audioBlocked: true });
    const { container, rerender } = render(
      <AudioUnlockPrompt binding={fakeBinding({ audioBlocked: false })} />
    );
    expect(container.querySelector('button')).toBeNull();
    rerender(<AudioUnlockPrompt binding={blocked} />);
    await user.click(screen.getByRole('button', { name: 'Enable audio' }));
    expect(blocked.unlockAudio).toHaveBeenCalledTimes(1);
  });
});

describe('WeakConnectionBanner', () => {
  it('is hidden until fallback, then shows the warning', () => {
    const { container, rerender } = render(
      <WeakConnectionBanner binding={fakeBinding({ fallback: false })} />
    );
    expect(container.querySelector('.sauti-banner')).toBeNull();
    rerender(<WeakConnectionBanner binding={fakeBinding({ fallback: true })} />);
    expect(screen.getByText('Weak connection')).toBeInTheDocument();
  });
});

describe('ControlBar', () => {
  it('composes mute and end controls', async () => {
    const user = userEvent.setup();
    const binding = fakeBinding();
    render(<ControlBar binding={binding} />);
    await user.click(screen.getByRole('button', { name: 'Mute' }));
    await user.click(screen.getByRole('button', { name: 'End' }));
    expect(binding.setMuted).toHaveBeenCalledWith(true);
    expect(binding.leave).toHaveBeenCalledTimes(1);
  });
});

describe('SautiThemeProvider', () => {
  it('writes theme values as inline custom properties', () => {
    const { container } = render(
      <SautiThemeProvider theme={{ colorAccent: '#f00' }} style={{ opacity: 0.5 }}>
        <span>child</span>
      </SautiThemeProvider>
    );
    const root = container.querySelector('.sauti-root') as HTMLElement;
    expect(root.style.getPropertyValue('--sauti-color-accent')).toBe('#f00');
    expect(root.style.opacity).toBe('0.5');
    expect(screen.getByText('child')).toBeInTheDocument();
  });

  it('renders without a theme', () => {
    const { container } = render(<SautiThemeProvider>plain</SautiThemeProvider>);
    expect(container.querySelector('.sauti-root')).toBeInTheDocument();
  });
});
