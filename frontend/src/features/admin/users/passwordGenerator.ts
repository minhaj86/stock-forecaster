/**
 * Temporary passwords for accounts created or reset by an administrator. Randomness comes only from the platform
 * CSPRNG (`crypto.getRandomValues`), with rejection sampling so every character is equally likely. Visually
 * ambiguous characters (I, O, l, 0, 1) are left out because these passwords are often passed on by hand.
 */
const UPPER = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
const LOWER = 'abcdefghijkmnopqrstuvwxyz';
const DIGITS = '23456789';
const SYMBOLS = '!@#$%^&*-_=+?';
const CHARACTER_CLASSES = [UPPER, LOWER, DIGITS, SYMBOLS];

export const GENERATED_PASSWORD_LENGTH = 24;

type RandomSource = (buffer: Uint32Array<ArrayBuffer>) => void;

const cryptoRandom: RandomSource = (buffer) => {
  crypto.getRandomValues(buffer);
};

/** Uniform integer in `[0, bound)`: values from the biased tail of the 32-bit range are drawn again. */
export function randomBelow(bound: number, random: RandomSource = cryptoRandom): number {
  const limit = Math.floor(0x1_0000_0000 / bound) * bound;
  const buffer = new Uint32Array(1);
  for (;;) {
    random(buffer);
    if (buffer[0] < limit) return buffer[0] % bound;
  }
}

/** A password with at least one character of every class, shuffled with Fisher–Yates. */
export function generatePassword(length = GENERATED_PASSWORD_LENGTH, random: RandomSource = cryptoRandom): string {
  const alphabet = CHARACTER_CLASSES.join('');
  const characters = CHARACTER_CLASSES.map((set) => set[randomBelow(set.length, random)]);
  while (characters.length < length) characters.push(alphabet[randomBelow(alphabet.length, random)]);
  for (let index = characters.length - 1; index > 0; index -= 1) {
    const other = randomBelow(index + 1, random);
    [characters[index], characters[other]] = [characters[other], characters[index]];
  }
  return characters.join('');
}
