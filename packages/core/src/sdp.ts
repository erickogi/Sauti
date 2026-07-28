export interface OpusOptions {
  dtx?: boolean;
  fec?: boolean;
}

interface Line {
  content: string;
  eol: string;
}

const RTPMAP = /^a=rtpmap:(\d+) opus\/48000(?:\/\d+)?$/;

function splitLines(sdp: string): Line[] {
  return sdp.split(/(?<=\n)/).map((part) => {
    if (part.endsWith('\r\n')) return { content: part.slice(0, -2), eol: '\r\n' };
    if (part.endsWith('\n')) return { content: part.slice(0, -1), eol: '\n' };
    return { content: part, eol: '' };
  });
}

function defaultEol(lines: Line[]): string {
  for (const line of lines) {
    if (line.eol !== '') return line.eol;
  }
  return '\n';
}

function requestedTokens(dtx: boolean, fec: boolean): [string, string][] {
  const tokens: [string, string][] = [];
  if (fec) tokens.push(['useinbandfec', '1']);
  if (dtx) tokens.push(['usedtx', '1']);
  return tokens;
}

function mergeParams(params: string, tokens: [string, string][]): string {
  const parts = params.length > 0 ? params.split(';') : [];
  const wanted = new Map(tokens);
  const handled = new Set<string>();
  const merged = parts.map((part) => {
    const eq = part.indexOf('=');
    const key = eq === -1 ? part : part.slice(0, eq);
    const value = wanted.get(key);
    if (value !== undefined) {
      handled.add(key);
      return `${key}=${value}`;
    }
    return part;
  });
  for (const [key, value] of tokens) {
    if (!handled.has(key)) merged.push(`${key}=${value}`);
  }
  return merged.join(';');
}

function transformSection(
  section: Line[],
  tokens: [string, string][],
  fallbackEol: string
): Line[] {
  const lines = section.map((line) => ({ ...line }));
  let pt = '';
  let rtpmapIndex = -1;
  for (let i = 0; i < lines.length; i += 1) {
    const match = RTPMAP.exec(lines[i]!.content);
    if (match) {
      pt = match[1]!;
      rtpmapIndex = i;
      break;
    }
  }
  if (rtpmapIndex === -1) return lines;

  const fmtp = new RegExp(`^a=fmtp:${pt}(?: (.*))?$`);
  for (let i = 0; i < lines.length; i += 1) {
    const match = fmtp.exec(lines[i]!.content);
    if (match) {
      const params = match[1] ?? '';
      lines[i] = { content: `a=fmtp:${pt} ${mergeParams(params, tokens)}`, eol: lines[i]!.eol };
      return lines;
    }
  }

  const body = tokens.map(([key, value]) => `${key}=${value}`).join(';');
  const rtpmap = lines[rtpmapIndex]!;
  const inserted: Line = { content: `a=fmtp:${pt} ${body}`, eol: rtpmap.eol };
  if (rtpmap.eol === '') {
    rtpmap.eol = fallbackEol;
    inserted.eol = '';
  }
  lines.splice(rtpmapIndex + 1, 0, inserted);
  return lines;
}

export function transformOpus(sdp: string, opts: OpusOptions): string {
  const dtx = opts.dtx ?? false;
  const fec = opts.fec ?? false;
  if (!dtx && !fec) return sdp;

  const lines = splitLines(sdp);
  const tokens = requestedTokens(dtx, fec);
  const fallbackEol = defaultEol(lines);
  const out: Line[] = [];
  let i = 0;
  while (i < lines.length) {
    if (lines[i]!.content.startsWith('m=audio')) {
      let end = i + 1;
      while (end < lines.length && !lines[end]!.content.startsWith('m=')) end += 1;
      out.push(...transformSection(lines.slice(i, end), tokens, fallbackEol));
      i = end;
    } else {
      out.push(lines[i]!);
      i += 1;
    }
  }
  return out.map((line) => line.content + line.eol).join('');
}
