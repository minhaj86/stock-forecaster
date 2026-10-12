import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

export interface DetailItem {
  label: string;
  value: ReactNode;
}

/** Label/value pairs as a description list, in one or two columns. */
export function DetailList({ items, columns = 1 }: { items: DetailItem[]; columns?: 1 | 2 }) {
  return (
    <Box
      component="dl"
      sx={{
        m: 0,
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: columns === 2 ? 'repeat(2, minmax(0, 1fr))' : 'minmax(0, 1fr)' },
        columnGap: 3,
        rowGap: 1.75,
      }}
    >
      {items.map((item) => (
        <Box key={item.label} sx={{ minWidth: 0 }}>
          <Typography component="dt" variant="caption" color="text.secondary" sx={{ fontWeight: 600, display: 'block' }}>
            {item.label}
          </Typography>
          <Typography component="dd" variant="body2" sx={{ m: 0, mt: 0.25, overflowWrap: 'anywhere' }}>
            {item.value}
          </Typography>
        </Box>
      ))}
    </Box>
  );
}
