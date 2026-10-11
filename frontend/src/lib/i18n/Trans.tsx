import { Fragment, type ReactNode } from 'react';

/**
 * Renders a translated string whose `{slot}` placeholders are React nodes (links, code), so each
 * language can put them where its grammar needs them.
 */
export function Trans({ text, slots }: { text: string; slots: Record<string, ReactNode> }) {
  const parts = text.split(/\{(\w+)\}/);
  return (
    <>
      {parts.map((part, i) =>
        i % 2 === 1 ? <Fragment key={i}>{slots[part] ?? `{${part}}`}</Fragment> : part,
      )}
    </>
  );
}
