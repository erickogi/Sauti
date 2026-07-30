import { describe, it, expect, afterEach, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MinimizedCall } from '../src/components/MinimizedCall.js';
import { fakeBinding } from './fakeBinding.js';
import { participant } from './fakeCall.js';

afterEach(cleanup);

describe('MinimizedCall', () => {
  it('shows status and duration while connected', () => {
    render(<MinimizedCall binding={fakeBinding({ phase: 'connected', durationMs: 125000 })} />);
    expect(screen.getByText('In call')).toBeInTheDocument();
    expect(screen.getByText('02:05')).toBeInTheDocument();
  });

  it('shows status but no duration while connecting', () => {
    render(<MinimizedCall binding={fakeBinding({ phase: 'connecting', durationMs: 4000 })} />);
    expect(screen.getByText('Connecting')).toBeInTheDocument();
    expect(screen.queryByText('00:04')).toBeNull();
  });

  it('reflects a reconnecting peer through CallStatus', () => {
    render(
      <MinimizedCall
        binding={fakeBinding({
          phase: 'connected',
          reconnecting: false,
          participants: [participant({ participantId: 'p-peer', connectionState: 'reconnecting' })]
        })}
      />
    );
    expect(screen.getByText('Reconnecting…')).toBeInTheDocument();
  });

  it('calls onExpand once when the return control is tapped', async () => {
    const user = userEvent.setup();
    const onExpand = vi.fn();
    render(
      <MinimizedCall binding={fakeBinding({ phase: 'connected' })} onExpand={onExpand} />
    );
    await user.click(screen.getByRole('button', { name: 'Return to call' }));
    expect(onExpand).toHaveBeenCalledTimes(1);
  });

  it('mutes without bubbling to onExpand', async () => {
    const user = userEvent.setup();
    const onExpand = vi.fn();
    const binding = fakeBinding({ phase: 'connected', localMuted: false });
    render(<MinimizedCall binding={binding} onExpand={onExpand} />);
    await user.click(screen.getByRole('button', { name: 'Mute' }));
    expect(binding.setMuted).toHaveBeenCalledWith(true);
    expect(onExpand).not.toHaveBeenCalled();
  });

  it('ends the call and fires onEnd', async () => {
    const user = userEvent.setup();
    const onEnd = vi.fn();
    const binding = fakeBinding({ phase: 'connected' });
    render(<MinimizedCall binding={binding} onEnd={onEnd} />);
    await user.click(screen.getByRole('button', { name: 'End' }));
    expect(binding.leave).toHaveBeenCalledTimes(1);
    expect(onEnd).toHaveBeenCalledTimes(1);
  });

  it('labels the return control and honors an override', () => {
    const { rerender } = render(<MinimizedCall binding={fakeBinding({ phase: 'connected' })} />);
    expect(screen.getByRole('button', { name: 'Return to call' })).toBeInTheDocument();
    rerender(
      <MinimizedCall
        binding={fakeBinding({ phase: 'connected' })}
        labels={{ returnToCall: 'Back to call' }}
      />
    );
    expect(screen.getByRole('button', { name: 'Back to call' })).toBeInTheDocument();
  });

  it('gives the return control the touch-target class', () => {
    render(<MinimizedCall binding={fakeBinding({ phase: 'connected' })} />);
    const control = screen.getByRole('button', { name: 'Return to call' });
    expect(control).toHaveClass('sauti-minimized__return');
  });
});
