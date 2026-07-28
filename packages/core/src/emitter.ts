export type Listener<T> = (payload: T) => void;

export class Emitter<Events> {
  private readonly handlers = new Map<keyof Events, Set<Listener<unknown>>>();

  on<K extends keyof Events>(type: K, handler: Listener<Events[K]>): () => void {
    let set = this.handlers.get(type);
    if (!set) {
      set = new Set();
      this.handlers.set(type, set);
    }
    set.add(handler as Listener<unknown>);
    return () => {
      set!.delete(handler as Listener<unknown>);
    };
  }

  off<K extends keyof Events>(type: K, handler: Listener<Events[K]>): void {
    this.handlers.get(type)?.delete(handler as Listener<unknown>);
  }

  has<K extends keyof Events>(type: K): boolean {
    const set = this.handlers.get(type);
    return set !== undefined && set.size > 0;
  }

  emit<K extends keyof Events>(type: K, payload: Events[K]): void {
    const set = this.handlers.get(type);
    if (!set) return;
    for (const handler of [...set]) {
      (handler as Listener<Events[K]>)(payload);
    }
  }
}
