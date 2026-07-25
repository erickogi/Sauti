import type { CSSProperties, ReactElement, ReactNode } from 'react';
import { joinClass } from './types.js';

export interface SautiTheme {
  colorBg?: string;
  colorSurface?: string;
  colorText?: string;
  colorMuted?: string;
  colorBorder?: string;
  colorAccent?: string;
  colorOnAccent?: string;
  colorDanger?: string;
  colorDangerSurface?: string;
  colorQualityGood?: string;
  colorQualityFair?: string;
  colorQualityPoor?: string;
  radius?: string;
  font?: string;
  spaceUnit?: string;
  touchTarget?: string;
}

const CSS_VAR: Record<keyof SautiTheme, string> = {
  colorBg: '--sauti-color-bg',
  colorSurface: '--sauti-color-surface',
  colorText: '--sauti-color-text',
  colorMuted: '--sauti-color-muted',
  colorBorder: '--sauti-color-border',
  colorAccent: '--sauti-color-accent',
  colorOnAccent: '--sauti-color-on-accent',
  colorDanger: '--sauti-color-danger',
  colorDangerSurface: '--sauti-color-danger-surface',
  colorQualityGood: '--sauti-color-quality-good',
  colorQualityFair: '--sauti-color-quality-fair',
  colorQualityPoor: '--sauti-color-quality-poor',
  radius: '--sauti-radius',
  font: '--sauti-font',
  spaceUnit: '--sauti-space-unit',
  touchTarget: '--sauti-touch-target'
};

export function themeToStyle(theme?: SautiTheme): CSSProperties {
  const vars: Record<string, string> = {};
  if (theme) {
    for (const key of Object.keys(theme) as Array<keyof SautiTheme>) {
      const value = theme[key];
      if (typeof value === 'string') {
        vars[CSS_VAR[key]] = value;
      }
    }
  }
  return vars as CSSProperties;
}

export interface SautiThemeProviderProps {
  theme?: SautiTheme;
  className?: string;
  style?: CSSProperties;
  children?: ReactNode;
}

export function SautiThemeProvider({
  theme,
  className,
  style,
  children
}: SautiThemeProviderProps): ReactElement {
  const vars = themeToStyle(theme);
  return (
    <div className={joinClass('sauti-root', className)} style={{ ...vars, ...style }}>
      {children}
    </div>
  );
}
