export function politePeer(a: string, b: string): string {
  return a <= b ? a : b;
}

export function isPolite(selfId: string, peerId: string): boolean {
  return politePeer(selfId, peerId) === selfId;
}
