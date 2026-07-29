# @sauti/push

Turns a `WakePushPayload` from `@sauti/protocol` into a neutral, provider-agnostic
delivery envelope. The library never sends anything, holds no credentials, and
knows nothing about recipients. An adopter maps the envelope onto whichever push
transport it runs.

```ts
import { buildWakePushEnvelope } from '@sauti/push';

const env = buildWakePushEnvelope(payload, { ttlSeconds: 30 });
```

`env` is:

```ts
{
  dataOnly: true,
  priority: 'high',
  ttlSeconds: number,
  collapseKey: string,
  data: Record<string, string>
}
```

## Mapping to FCM

```ts
await messaging.send({
  token,
  android: {
    priority: env.priority,
    ttl: env.ttlSeconds * 1000,
    collapseKey: env.collapseKey
  },
  data: env.data
});
```

## Mapping to Raven

Raven has no transport collapse key and wants a non-optional title and body, so
drop `collapseKey` and supply placeholders for a data-only wake.

```ts
await raven.send({
  priority: env.priority,
  ttl: env.ttlSeconds,
  dataOnly: env.dataOnly,
  title: ' ',
  body: ' ',
  data: env.data
});
```
