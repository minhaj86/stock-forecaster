import { describe, expect, it, vi } from 'vitest';
import { GENERATED_PASSWORD_LENGTH, generatePassword, randomBelow } from './passwordGenerator';

describe('generatePassword', () => {
  it('creates long passwords with upper- and lower-case letters, digits and symbols', () => {
    for (let run = 0; run < 50; run += 1) {
      const password = generatePassword();
      expect(password.length).toBeGreaterThanOrEqual(20);
      expect(password).toHaveLength(GENERATED_PASSWORD_LENGTH);
      expect(password).toMatch(/[A-Z]/);
      expect(password).toMatch(/[a-z]/);
      expect(password).toMatch(/[0-9]/);
      expect(password).toMatch(/[^A-Za-z0-9]/);
      expect(password).not.toMatch(/[IOl01]/);
      expect(new TextEncoder().encode(password).length).toBeLessThanOrEqual(72);
    }
  });

  it('uses the platform CSPRNG and never Math.random', () => {
    const getRandomValues = vi.spyOn(crypto, 'getRandomValues');
    const mathRandom = vi.spyOn(Math, 'random');

    generatePassword();

    expect(getRandomValues).toHaveBeenCalled();
    expect(mathRandom).not.toHaveBeenCalled();
  });

  it('does not repeat itself', () => {
    const passwords = new Set(Array.from({ length: 100 }, () => generatePassword()));
    expect(passwords.size).toBe(100);
  });
});

describe('randomBelow', () => {
  it('rejects values from the biased tail of the 32-bit range', () => {
    // 2^32 = 4294967296; for a bound of 10 the largest unbiased value is 4294967289.
    const draws = [4294967295, 4294967290, 4294967289];
    const random = vi.fn((buffer: Uint32Array) => {
      buffer[0] = draws.shift() ?? 0;
      return buffer;
    });

    expect(randomBelow(10, random)).toBe(9);
    expect(random).toHaveBeenCalledTimes(3);
  });

  it('stays within the bound', () => {
    for (let run = 0; run < 1000; run += 1) {
      const value = randomBelow(7);
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThan(7);
    }
  });
});
