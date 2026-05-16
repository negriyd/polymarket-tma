/**
 * Browser polyfills that must load before any third-party SDK that assumes a Node-like environment.
 *
 * Privy's {@code @privy-io/js-sdk-core} uses {@code Buffer} inside
 * {@code generate-authorization-signature.mjs}; without this shim the embedded wallet
 * fails to sign typed data with a generic "An error has occurred, please try again" while the
 * console reports {@code ReferenceError: Buffer is not defined}.
 */
import { Buffer as BufferPolyfill } from 'buffer';

const g = globalThis as unknown as { Buffer?: typeof BufferPolyfill };
if (typeof g.Buffer === 'undefined') {
  g.Buffer = BufferPolyfill;
}
