import type { AudioSinkLike, SautiRuntime, StreamLike } from './types.js';

export class AudioSinkManager {
  private readonly sinks = new Map<string, AudioSinkLike>();
  private readonly blocked = new Set<AudioSinkLike>();

  constructor(
    private readonly runtime: SautiRuntime,
    private readonly onBlocked: () => void,
    private readonly onUnblocked: () => void
  ) {}

  attach(participantId: string, stream: StreamLike): void {
    let sink = this.sinks.get(participantId);
    if (!sink) {
      sink = this.runtime.createAudioSink();
      this.sinks.set(participantId, sink);
    }
    sink.srcObject = stream;
    this.playSink(sink);
  }

  private playSink(sink: AudioSinkLike): void {
    sink.play().then(
      () => this.mark(sink, false),
      () => this.mark(sink, true)
    );
  }

  private mark(sink: AudioSinkLike, isBlocked: boolean): void {
    if (![...this.sinks.values()].includes(sink)) return;
    if (isBlocked) this.blocked.add(sink);
    else this.blocked.delete(sink);
    this.emitAggregate();
  }

  private emitAggregate(): void {
    if (this.blocked.size > 0) this.onBlocked();
    else this.onUnblocked();
  }

  remove(participantId: string): void {
    const sink = this.sinks.get(participantId);
    if (!sink) return;
    sink.srcObject = null;
    sink.remove();
    this.sinks.delete(participantId);
    if (this.blocked.delete(sink)) this.emitAggregate();
  }

  async unlock(): Promise<void> {
    const entries = [...this.sinks.values()];
    const results = await Promise.allSettled(entries.map((sink) => sink.play()));
    results.forEach((result, index) => {
      const sink = entries[index]!;
      if (result.status === 'rejected') this.blocked.add(sink);
      else this.blocked.delete(sink);
    });
    this.emitAggregate();
  }

  clear(): void {
    for (const id of [...this.sinks.keys()]) this.remove(id);
  }
}
