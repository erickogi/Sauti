import type { ReactElement } from 'react';
import type { Quality } from '@sauti/core';
import { qualityLabel, qualityToken, resolveLabels } from '../labels.js';
import { joinClass, type Styleable } from '../types.js';
import type { SautiLabels } from '../labels.js';

export interface QualityIndicatorProps extends Styleable {
  quality: Quality;
  labels?: Partial<SautiLabels>;
  live?: boolean;
}

export function QualityIndicator({
  quality,
  labels,
  live,
  className,
  style
}: QualityIndicatorProps): ReactElement {
  const resolved = resolveLabels(labels);
  const token = qualityToken(quality);
  return (
    <span
      className={joinClass('sauti-quality', `sauti-quality--${token}`, className)}
      style={style}
      data-quality={token}
      role={live ? 'status' : undefined}
      aria-live={live ? 'polite' : undefined}
    >
      {qualityLabel(quality, resolved)}
    </span>
  );
}