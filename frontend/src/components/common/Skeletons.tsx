import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid';
import Skeleton from '@mui/material/Skeleton';

export function CardGridSkeleton({ count = 3, label = 'Loading' }: { count?: number; label?: string }) {
  return (
    <Grid container spacing={2} aria-busy aria-label={label}>
      {Array.from({ length: count }, (_, index) => (
        <Grid key={index} size={{ xs: 12, sm: 6, md: 12 / Math.min(count, 4) }}>
          <Card>
            <CardContent>
              <Skeleton width="45%" />
              <Skeleton width="70%" height={36} />
              <Skeleton width="35%" />
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  );
}

export function TableSkeleton({ rows = 5, label = 'Loading table' }: { rows?: number; label?: string }) {
  return (
    <Box aria-busy aria-label={label} sx={{ px: 2, py: 1 }}>
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} height={40} />
      ))}
    </Box>
  );
}

export function ChartSkeleton({ height = 320, label = 'Loading chart' }: { height?: number; label?: string }) {
  return <Skeleton variant="rounded" height={height} aria-busy aria-label={label} />;
}
