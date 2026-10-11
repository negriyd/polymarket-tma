import { describe, expect, it } from 'vitest';
import { refCodeOf } from './authorRef';
import { cardImageUrl } from '@/features/share/cardImage';

describe('author links', () => {
  it('finds the invite code among startapp tokens', () => {
    expect(refCodeOf('rynp8bf6n_stest')).toBe('ynp8bf6n');
    expect(refCodeOf('m_559652_y_rk3f9a2qx')).toBe('k3f9a2qx');
    expect(refCodeOf('rynp8bf6n_lb')).toBe('ynp8bf6n');
    expect(refCodeOf('stg1')).toBeNull();
    expect(refCodeOf(undefined)).toBeNull();
  });

  it('builds the public card image URL', () => {
    const url = cardImageUrl('559652', 'ru', 'story', 'ynp8bf6n', 'https://x.test');
    expect(url).toMatch(/^https:\/\/x\.test\/api\/markets\/559652\/card\.png\?lang=ru&size=story&ref=ynp8bf6n$/);
    expect(cardImageUrl('559652', 'en', 'square', null, 'https://x.test')).not.toContain('ref=');
  });
});
