import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ColorModeProvider } from '@/theme/ColorModeProvider';
import { ChangeIndicator } from './ChangeIndicator';

const renderIndicator = (ui: React.ReactNode) => render(<ColorModeProvider>{ui}</ColorModeProvider>);

describe('ChangeIndicator', () => {
  it('conveys direction with an arrow, a sign and an accessible label, not colour alone', () => {
    renderIndicator(<ChangeIndicator value={0.0423} />);

    const indicator = screen.getByLabelText('up +4.23%');
    expect(indicator).toHaveTextContent('+4.23%');
    expect(indicator.querySelector('svg')).not.toBeNull();
  });

  it('includes the absolute change when provided', () => {
    renderIndicator(<ChangeIndicator value={-0.0134} change={-4.22} currency="USD" />);

    expect(screen.getByLabelText('down -$4.22 (-1.34%)')).toBeInTheDocument();
  });

  it('handles missing and unchanged values', () => {
    renderIndicator(<><ChangeIndicator value={null} /><ChangeIndicator value={0} /></>);

    expect(screen.getByLabelText('no data')).toHaveTextContent('—');
    expect(screen.getByLabelText('unchanged, 0.00%')).toBeInTheDocument();
  });
});
