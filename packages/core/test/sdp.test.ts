import { describe, it, expect } from 'vitest';
import { transformOpus } from '../src/sdp.js';

const BASE = [
  'v=0',
  'o=- 1 1 IN IP4 127.0.0.1',
  's=-',
  't=0 0',
  'm=audio 9 UDP/TLS/RTP/SAVPF 111 0',
  'a=rtpmap:111 opus/48000/2',
  'a=fmtp:111 minptime=10',
  'a=rtpmap:0 PCMU/8000',
  'a=fmtp:0 something=1'
].join('\r\n');

describe('transformOpus [SDP]', () => {
  it('returns the input unchanged when both flags are false [SDP-01]', () => {
    expect(transformOpus(BASE, { dtx: false, fec: false })).toBe(BASE);
    expect(transformOpus(BASE, {})).toBe(BASE);
  });

  it('merges useinbandfec into an existing fmtp preserving other keys [SDP-02]', () => {
    const out = transformOpus(BASE, { fec: true });
    expect(out).toContain('a=fmtp:111 minptime=10;useinbandfec=1');
    expect(out).toContain('a=fmtp:0 something=1');
    const count = out.split('useinbandfec=1').length - 1;
    expect(count).toBe(1);
  });

  it('inserts an fmtp line once when none exists [SDP-03]', () => {
    const sdp = [
      'm=audio 9 UDP/TLS/RTP/SAVPF 111',
      'a=rtpmap:111 opus/48000/2',
      'a=rtcp-fb:111 nack'
    ].join('\r\n');
    const out = transformOpus(sdp, { fec: true });
    expect(out).toBe(
      [
        'm=audio 9 UDP/TLS/RTP/SAVPF 111',
        'a=rtpmap:111 opus/48000/2',
        'a=fmtp:111 useinbandfec=1',
        'a=rtcp-fb:111 nack'
      ].join('\r\n')
    );
  });

  it('edits only the payload derived from the opus rtpmap [SDP-04]', () => {
    const sdp = [
      'm=audio 9 UDP/TLS/RTP/SAVPF 96 0',
      'a=rtpmap:96 opus/48000/2',
      'a=fmtp:96 minptime=10',
      'a=rtpmap:0 PCMU/8000',
      'a=fmtp:0 x=1'
    ].join('\r\n');
    const out = transformOpus(sdp, { fec: true });
    expect(out).toContain('a=fmtp:96 minptime=10;useinbandfec=1');
    expect(out).toContain('a=fmtp:0 x=1');
    expect(out).not.toContain('a=fmtp:0 x=1;useinbandfec=1');
  });

  it('handles a two-digit and three-digit remap without touching neighbours [SDP-04b]', () => {
    const sdp = [
      'm=audio 9 UDP/TLS/RTP/SAVPF 63 111',
      'a=rtpmap:63 red/48000/2',
      'a=fmtp:63 111/111',
      'a=rtpmap:111 opus/48000/2',
      'a=fmtp:111 minptime=10'
    ].join('\r\n');
    const out = transformOpus(sdp, { fec: true });
    expect(out).toContain('a=fmtp:63 111/111');
    expect(out).toContain('a=fmtp:111 minptime=10;useinbandfec=1');
  });

  it('leaves a section without an opus rtpmap unchanged [SDP-05]', () => {
    const sdp = [
      'm=audio 9 UDP/TLS/RTP/SAVPF 0',
      'a=rtpmap:0 PCMU/8000',
      'a=fmtp:0 x=1'
    ].join('\r\n');
    expect(transformOpus(sdp, { fec: true })).toBe(sdp);
  });

  it('processes two m=audio sections independently [SDP-06]', () => {
    const sdp = [
      'v=0',
      'm=audio 9 UDP/TLS/RTP/SAVPF 111',
      'a=rtpmap:111 opus/48000/2',
      'a=fmtp:111 minptime=10',
      'm=audio 9 UDP/TLS/RTP/SAVPF 96',
      'a=rtpmap:96 opus/48000/2'
    ].join('\r\n');
    const out = transformOpus(sdp, { fec: true });
    expect(out).toContain('a=fmtp:111 minptime=10;useinbandfec=1');
    expect(out).toContain('a=rtpmap:96 opus/48000/2\r\na=fmtp:96 useinbandfec=1');
  });

  it('re-derives the payload type per call across a remap [SDP-07]', () => {
    const first = [
      'm=audio 9 UDP/TLS/RTP/SAVPF 111',
      'a=rtpmap:111 opus/48000/2',
      'a=fmtp:111 minptime=10'
    ].join('\r\n');
    const second = [
      'm=audio 9 UDP/TLS/RTP/SAVPF 96',
      'a=rtpmap:96 opus/48000/2',
      'a=fmtp:96 minptime=10'
    ].join('\r\n');
    expect(transformOpus(first, { fec: true })).toContain('a=fmtp:111 minptime=10;useinbandfec=1');
    expect(transformOpus(second, { fec: true })).toContain('a=fmtp:96 minptime=10;useinbandfec=1');
  });

  it('is idempotent when applied twice [SDP-08]', () => {
    const once = transformOpus(BASE, { fec: true });
    const twice = transformOpus(once, { fec: true });
    expect(twice).toBe(once);
  });

  it('does not duplicate an already present useinbandfec value [SDP-08b]', () => {
    const sdp = [
      'm=audio 9 UDP/TLS/RTP/SAVPF 111',
      'a=rtpmap:111 opus/48000/2',
      'a=fmtp:111 minptime=10;useinbandfec=1'
    ].join('\r\n');
    expect(transformOpus(sdp, { fec: true })).toBe(sdp);
  });

  it('replaces an existing useinbandfec=0 with useinbandfec=1 in place [SDP-08c]', () => {
    const sdp = [
      'm=audio 9 UDP/TLS/RTP/SAVPF 111',
      'a=rtpmap:111 opus/48000/2',
      'a=fmtp:111 useinbandfec=0;minptime=10'
    ].join('\r\n');
    expect(transformOpus(sdp, { fec: true })).toBe(
      [
        'm=audio 9 UDP/TLS/RTP/SAVPF 111',
        'a=rtpmap:111 opus/48000/2',
        'a=fmtp:111 useinbandfec=1;minptime=10'
      ].join('\r\n')
    );
  });

  it('preserves CRLF line endings [SDP-09]', () => {
    const out = transformOpus(BASE, { fec: true });
    expect(out.includes('\r\n')).toBe(true);
    expect(out.includes('\n\n')).toBe(false);
  });

  it('preserves LF-only line endings [SDP-09b]', () => {
    const sdp = [
      'm=audio 9 UDP/TLS/RTP/SAVPF 111',
      'a=rtpmap:111 opus/48000/2',
      'a=fmtp:111 minptime=10'
    ].join('\n');
    const out = transformOpus(sdp, { fec: true });
    expect(out).toBe(
      [
        'm=audio 9 UDP/TLS/RTP/SAVPF 111',
        'a=rtpmap:111 opus/48000/2',
        'a=fmtp:111 minptime=10;useinbandfec=1'
      ].join('\n')
    );
    expect(out.includes('\r')).toBe(false);
  });

  it('inserts correctly when the opus rtpmap is the final line without a trailing newline [SDP-09c]', () => {
    const sdp = 'm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2';
    const out = transformOpus(sdp, { fec: true });
    expect(out).toBe(
      'm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 useinbandfec=1'
    );
  });

  it('applies usedtx for the dtx branch [SDP-10]', () => {
    const out = transformOpus(BASE, { dtx: true });
    expect(out).toContain('a=fmtp:111 minptime=10;usedtx=1');
    expect(out).not.toContain('useinbandfec');
  });

  it('applies both tokens on one fmtp line when dtx and fec are set [SDP-11]', () => {
    const out = transformOpus(BASE, { dtx: true, fec: true });
    expect(out).toContain('a=fmtp:111 minptime=10;useinbandfec=1;usedtx=1');
  });

  it('inserts both tokens on a new fmtp line when none exists [SDP-11b]', () => {
    const sdp = ['m=audio 9 x 111', 'a=rtpmap:111 opus/48000/2'].join('\r\n');
    const out = transformOpus(sdp, { dtx: true, fec: true });
    expect(out).toContain('a=rtpmap:111 opus/48000/2\r\na=fmtp:111 useinbandfec=1;usedtx=1');
  });

  it('accepts opus rtpmap without a channel suffix [SDP-12]', () => {
    const sdp = ['m=audio 9 x 111', 'a=rtpmap:111 opus/48000', 'a=fmtp:111 minptime=10'].join(
      '\r\n'
    );
    const out = transformOpus(sdp, { fec: true });
    expect(out).toContain('a=fmtp:111 minptime=10;useinbandfec=1');
  });

  it('does not treat a longer payload number as a match for the opus fmtp [SDP-13]', () => {
    const sdp = [
      'm=audio 9 x 11 111',
      'a=rtpmap:11 opus/48000/2',
      'a=fmtp:111 minptime=10'
    ].join('\r\n');
    const out = transformOpus(sdp, { fec: true });
    expect(out).toContain('a=rtpmap:11 opus/48000/2\r\na=fmtp:11 useinbandfec=1');
    expect(out).toContain('a=fmtp:111 minptime=10');
    expect(out).not.toContain('a=fmtp:111 minptime=10;useinbandfec=1');
  });

  it('preserves a valueless fmtp token while merging [SDP-15]', () => {
    const sdp = [
      'm=audio 9 x 111',
      'a=rtpmap:111 opus/48000/2',
      'a=fmtp:111 minptime=10;cbr'
    ].join('\r\n');
    const out = transformOpus(sdp, { fec: true });
    expect(out).toContain('a=fmtp:111 minptime=10;cbr;useinbandfec=1');
  });

  it('merges into an fmtp line that carries no parameters [SDP-16]', () => {
    const sdp = ['m=audio 9 x 111', 'a=rtpmap:111 opus/48000/2', 'a=fmtp:111'].join('\r\n');
    const out = transformOpus(sdp, { fec: true });
    expect(out).toBe(
      ['m=audio 9 x 111', 'a=rtpmap:111 opus/48000/2', 'a=fmtp:111 useinbandfec=1'].join('\r\n')
    );
  });

  it('replaces a bare valueless useinbandfec token without duplicating it [SDP-17]', () => {
    const sdp = [
      'm=audio 9 x 111',
      'a=rtpmap:111 opus/48000/2',
      'a=fmtp:111 minptime=10;useinbandfec'
    ].join('\r\n');
    const out = transformOpus(sdp, { fec: true });
    expect(out).toBe(
      [
        'm=audio 9 x 111',
        'a=rtpmap:111 opus/48000/2',
        'a=fmtp:111 minptime=10;useinbandfec=1'
      ].join('\r\n')
    );
  });

  it('does not match an uppercase opus rtpmap [SDP-18]', () => {
    const sdp = [
      'm=audio 9 x 111',
      'a=rtpmap:111 OPUS/48000/2',
      'a=fmtp:111 minptime=10'
    ].join('\r\n');
    expect(transformOpus(sdp, { fec: true })).toBe(sdp);
  });

  it('does not match an overlong clock rate rtpmap [SDP-19]', () => {
    const sdp = [
      'm=audio 9 x 111',
      'a=rtpmap:111 opus/480000/2',
      'a=fmtp:111 minptime=10'
    ].join('\r\n');
    expect(transformOpus(sdp, { fec: true })).toBe(sdp);
  });

  it('does not match a tab-separated rtpmap [SDP-20]', () => {
    const sdp = [
      'm=audio 9 x 111',
      'a=rtpmap:111\topus/48000/2',
      'a=fmtp:111 minptime=10'
    ].join('\r\n');
    expect(transformOpus(sdp, { fec: true })).toBe(sdp);
  });

  it('does not match an rtpmap with trailing junk [SDP-21]', () => {
    const sdp = [
      'm=audio 9 x 111',
      'a=rtpmap:111 opus/48000/2 extra',
      'a=fmtp:111 minptime=10'
    ].join('\r\n');
    expect(transformOpus(sdp, { fec: true })).toBe(sdp);
  });

  it('returns an empty string unchanged [SDP-14]', () => {
    expect(transformOpus('', { fec: true })).toBe('');
    expect(transformOpus('', { dtx: false, fec: false })).toBe('');
  });
});
