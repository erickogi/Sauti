import { test, expect, chromium, type Browser, type Page } from '@playwright/test';
import { startHarness, type Harness } from '../src/harness';
import { mintToken } from '../src/token';

interface ParticipantView {
  participantId: string;
  connectionState: string;
  muted: boolean;
}

interface Snapshot {
  phase: string;
  participants: ParticipantView[];
  localMuted: boolean;
}

interface RemoteAudioReport {
  peerConnectionCount: number;
  liveAudioReceivers: number;
  bytesReceived: number;
  audioLevel: number;
}

let harness: Harness;
let browser: Browser;

test.beforeAll(async () => {
  harness = await startHarness();
  browser = await chromium.launch({
    headless: false,
    args: [
      '--use-fake-device-for-media-stream',
      '--use-fake-ui-for-media-stream',
      '--autoplay-policy=no-user-gesture-required',
      '--ignore-certificate-errors'
    ]
  });
});

test.afterAll(async () => {
  await browser.close();
  await harness.close();
});

async function openPeer(
  roomId: string,
  participantId: string
): Promise<Page> {
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  const page = await context.newPage();
  await page.goto(harness.url);
  await page.waitForFunction(() => Boolean(window.__sauti));
  const token = mintToken({ roomId, participantId });
  await page.evaluate(
    ({ url, token }) => window.__sauti.join(url, token),
    { url: harness.wsUrl, token }
  );
  return page;
}

function snapshot(page: Page): Promise<Snapshot> {
  return page.evaluate(() => window.__sauti.getState() as Snapshot);
}

function remoteAudio(page: Page): Promise<RemoteAudioReport> {
  return page.evaluate(() => window.__sauti.remoteAudio());
}

function localDescriptionType(page: Page): Promise<string | null> {
  return page.evaluate(() => window.__sauti.localDescriptionType());
}

test('two browser peers negotiate a real audio call', async () => {
  const roomId = `room-${Date.now()}`;
  const politeId = 'peer-a';
  const impoliteId = 'peer-z';

  const polite = await openPeer(roomId, politeId);
  const impolite = await openPeer(roomId, impoliteId);

  await expect
    .poll(async () => (await snapshot(polite)).phase, { timeout: 30000 })
    .toBe('connected');
  await expect
    .poll(async () => (await snapshot(impolite)).phase, { timeout: 30000 })
    .toBe('connected');

  await expect
    .poll(
      async () => {
        const state = await snapshot(polite);
        const peer = state.participants.find(
          (p) => p.participantId === impoliteId
        );
        return peer?.connectionState ?? 'missing';
      },
      { timeout: 30000 }
    )
    .toBe('connected');
  await expect
    .poll(
      async () => {
        const state = await snapshot(impolite);
        const peer = state.participants.find(
          (p) => p.participantId === politeId
        );
        return peer?.connectionState ?? 'missing';
      },
      { timeout: 30000 }
    )
    .toBe('connected');

  const politeState = await snapshot(polite);
  expect(
    politeState.participants.map((p) => p.participantId).sort()
  ).toEqual([politeId, impoliteId].sort());
  const impoliteState = await snapshot(impolite);
  expect(
    impoliteState.participants.map((p) => p.participantId).sort()
  ).toEqual([politeId, impoliteId].sort());

  await expect
    .poll(async () => (await remoteAudio(polite)).liveAudioReceivers, {
      timeout: 30000
    })
    .toBeGreaterThanOrEqual(1);
  await expect
    .poll(async () => (await remoteAudio(impolite)).liveAudioReceivers, {
      timeout: 30000
    })
    .toBeGreaterThanOrEqual(1);

  await expect
    .poll(async () => (await remoteAudio(polite)).bytesReceived, {
      timeout: 30000
    })
    .toBeGreaterThan(0);
  await expect
    .poll(async () => (await remoteAudio(impolite)).bytesReceived, {
      timeout: 30000
    })
    .toBeGreaterThan(0);

  const politeDescription = await localDescriptionType(polite);
  const impoliteDescription = await localDescriptionType(impolite);
  expect([politeDescription, impoliteDescription].sort()).toEqual([
    'answer',
    'offer'
  ]);
  expect(impoliteDescription).toBe('offer');
  expect(politeDescription).toBe('answer');

  const politeAudio = await remoteAudio(polite);
  const impoliteAudio = await remoteAudio(impolite);
  console.log(
    `[proof] ${politeId} <- ${impoliteId}: liveAudioReceivers=${politeAudio.liveAudioReceivers} bytesReceived=${politeAudio.bytesReceived} audioLevel=${politeAudio.audioLevel}`
  );
  console.log(
    `[proof] ${impoliteId} <- ${politeId}: liveAudioReceivers=${impoliteAudio.liveAudioReceivers} bytesReceived=${impoliteAudio.bytesReceived} audioLevel=${impoliteAudio.audioLevel}`
  );
  console.log(
    `[proof] offerer=${impoliteId} (${impoliteDescription}) answerer=${politeId} (${politeDescription})`
  );

  await polite.evaluate(() => window.__sauti.setMuted(true));

  await expect
    .poll(
      async () => {
        const state = await snapshot(impolite);
        const peer = state.participants.find(
          (p) => p.participantId === politeId
        );
        return peer?.muted ?? false;
      },
      { timeout: 30000 }
    )
    .toBe(true);
});
