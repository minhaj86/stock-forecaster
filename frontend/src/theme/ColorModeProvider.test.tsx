import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { useColorMode } from './colorModeContext';
import { ColorModeProvider } from './ColorModeProvider';

function Probe() {
  const { mode, preference, setPreference } = useColorMode();
  return (
    <>
      <span>mode:{mode}</span>
      <span>preference:{preference}</span>
      <button type="button" onClick={() => setPreference('dark')}>dark</button>
    </>
  );
}

describe('ColorModeProvider', () => {
  it('follows the system by default and persists an explicit choice', async () => {
    const { unmount } = render(<ColorModeProvider><Probe /></ColorModeProvider>);
    expect(screen.getByText('preference:system')).toBeInTheDocument();
    expect(screen.getByText('mode:light')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'dark' }));

    expect(screen.getByText('mode:dark')).toBeInTheDocument();
    expect(window.localStorage.getItem('stocklens.colorMode')).toBe('dark');
    unmount();

    render(<ColorModeProvider><Probe /></ColorModeProvider>);
    expect(screen.getByText('mode:dark')).toBeInTheDocument();
  });
});
