import { describe, expect, it } from 'vitest';
import { cleanTag, taggedLink } from './partnerLinks';

describe('partner links', () => {
  it('cleans tags to what the backend accepts', () => {
    expect(cleanTag(' TG Main! ')).toBe('tg-main');
    expect(cleanTag('канал')).toBe('');
    expect(cleanTag('x'.repeat(40))).toHaveLength(30);
  });

  it('appends the tag to the invite link', () => {
    const base = 'https://t.me/bot?startapp=rabcd1234';
    expect(taggedLink(base, 'x-oct')).toBe(`${base}_sx-oct`);
    expect(taggedLink(base, '')).toBe(base);
  });
});
