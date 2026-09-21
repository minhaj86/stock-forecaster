import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import { useTheme } from '@mui/material/styles';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TableSortLabel from '@mui/material/TableSortLabel';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import type { KeyboardEvent, ReactNode } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { ChangeIndicator } from '@/components/common/ChangeIndicator';
import { DataStatusChip } from '@/components/common/DataStatusChip';
import { useSort } from '@/hooks/useSort';
import type { StockSnapshot } from '@/models/analytics';
import { formatCurrency, formatPercent } from '@/utils/format';

export type MetricColumn = 'price' | 'today' | 'return1M' | 'return1Y' | 'volatility30d';
type SortKey = 'symbol' | MetricColumn;

export interface StockRow {
  symbol: string;
  companyName: string;
  exchange: string;
  currency: string;
  snapshot?: StockSnapshot;
}

const COLUMN_LABELS: Record<MetricColumn, string> = {
  price: 'Price',
  today: 'Today',
  return1M: '1M',
  return1Y: '1Y',
  volatility30d: 'Volatility (30D)',
};

const ACCESSORS: Record<SortKey, (row: StockRow) => string | number | null | undefined> = {
  symbol: (row) => row.symbol,
  price: (row) => row.snapshot?.price,
  today: (row) => row.snapshot?.changePercent,
  return1M: (row) => row.snapshot?.return1M,
  return1Y: (row) => row.snapshot?.return1Y,
  volatility30d: (row) => row.snapshot?.volatility30d,
};

function MetricCell({ row, column }: { row: StockRow; column: MetricColumn }) {
  const snapshot = row.snapshot;
  if (!snapshot || snapshot.dataStatus !== 'READY') {
    return column === 'price' ? <DataStatusChip status={snapshot?.dataStatus ?? 'PENDING'} /> : <>—</>;
  }
  switch (column) {
    case 'price':
      return <>{formatCurrency(snapshot.price, snapshot.currency)}</>;
    case 'today':
      return <ChangeIndicator value={snapshot.changePercent} size="small" />;
    case 'return1M':
      return <ChangeIndicator value={snapshot.return1M} size="small" />;
    case 'return1Y':
      return <ChangeIndicator value={snapshot.return1Y} size="small" />;
    case 'volatility30d':
      return <>{formatPercent(snapshot.volatility30d, { signed: false, digits: 1 })}</>;
  }
}

interface StockMetricsTableProps {
  rows: StockRow[];
  columns: MetricColumn[];
  /** Accessible name of the table, e.g. "Stocks in Technology". */
  label: string;
  sortable?: boolean;
  rowAction?: (row: StockRow) => ReactNode;
}

/**
 * Stocks with backend-computed metrics. Rows open the stock page; on small screens the table becomes a card list.
 */
export function StockMetricsTable({ rows, columns, label, sortable = false, rowAction }: StockMetricsTableProps) {
  const theme = useTheme();
  const compact = useMediaQuery(theme.breakpoints.down('sm'));
  const navigate = useNavigate();
  const { sorted, sort, toggle } = useSort<StockRow, SortKey>(rows, ACCESSORS, { key: 'symbol', direction: 'asc' });
  const visible = sortable ? sorted : rows;
  const open = (symbol: string) => navigate(`/app/stocks/${encodeURIComponent(symbol)}`);

  if (compact) {
    return (
      <Stack spacing={1.5} component="ul" aria-label={label} sx={{ listStyle: 'none', p: 0, m: 0 }}>
        {visible.map((row) => (
          <Box component="li" key={row.symbol}>
            <Card>
              <Box sx={{ display: 'flex', alignItems: 'stretch' }}>
                <CardActionArea onClick={() => open(row.symbol)} sx={{ p: 2 }} aria-label={`Open ${row.symbol}`}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
                    <Box sx={{ minWidth: 0 }}>
                      <Typography sx={{ fontWeight: 700 }}>{row.symbol}</Typography>
                      <Typography variant="body2" color="text.secondary" noWrap>{row.companyName}</Typography>
                    </Box>
                    <Box sx={{ textAlign: 'right' }}>
                      <MetricCell row={row} column="price" />
                      <Box><MetricCell row={row} column="today" /></Box>
                    </Box>
                  </Box>
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mt: 1 }}>
                    {columns.filter((column) => column !== 'price' && column !== 'today').map((column) => (
                      <Box key={column}>
                        <Typography variant="caption" color="text.secondary" component="div">{COLUMN_LABELS[column]}</Typography>
                        <MetricCell row={row} column={column} />
                      </Box>
                    ))}
                  </Box>
                </CardActionArea>
                {rowAction && <Box sx={{ display: 'flex', alignItems: 'center', pr: 1 }}>{rowAction(row)}</Box>}
              </Box>
            </Card>
          </Box>
        ))}
      </Stack>
    );
  }

  const header = (key: SortKey, text: string, align: 'left' | 'right') => (
    <TableCell key={key} align={align} sortDirection={sortable && sort.key === key ? sort.direction : false}>
      {sortable ? (
        <TableSortLabel
          active={sort.key === key}
          direction={sort.key === key ? sort.direction : key === 'symbol' ? 'asc' : 'desc'}
          onClick={() => toggle(key, key === 'symbol' ? 'asc' : 'desc')}
        >
          {text}
        </TableSortLabel>
      ) : text}
    </TableCell>
  );

  const onRowKeyDown = (event: KeyboardEvent, symbol: string) => {
    if (event.key === 'Enter') open(symbol);
  };

  return (
    <TableContainer>
      <Table aria-label={label} size="medium">
        <TableHead>
          <TableRow>
            {header('symbol', 'Symbol', 'left')}
            <TableCell>Company</TableCell>
            {columns.map((column) => header(column, COLUMN_LABELS[column], 'right'))}
            {rowAction && <TableCell align="right"><Box component="span" sx={visuallyHidden}>Actions</Box></TableCell>}
          </TableRow>
        </TableHead>
        <TableBody>
          {visible.map((row) => (
            <TableRow
              key={row.symbol}
              hover
              tabIndex={-1}
              onClick={() => open(row.symbol)}
              onKeyDown={(event) => onRowKeyDown(event, row.symbol)}
              sx={{ cursor: 'pointer' }}
            >
              <TableCell sx={{ fontWeight: 700 }}>
                <Link
                  component={RouterLink}
                  to={`/app/stocks/${encodeURIComponent(row.symbol)}`}
                  underline="hover"
                  color="inherit"
                  onClick={(event) => event.stopPropagation()}
                >
                  {row.symbol}
                </Link>
              </TableCell>
              <TableCell sx={{ maxWidth: 280 }}>
                <Typography variant="body2" noWrap>{row.companyName}</Typography>
                <Typography variant="caption" color="text.secondary">{row.exchange}</Typography>
              </TableCell>
              {columns.map((column) => (
                <TableCell key={column} align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                  <MetricCell row={row} column={column} />
                </TableCell>
              ))}
              {rowAction && (
                <TableCell align="right" onClick={(event) => event.stopPropagation()}>{rowAction(row)}</TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

const visuallyHidden = {
  border: 0, clip: 'rect(0 0 0 0)', height: '1px', margin: -1, overflow: 'hidden', padding: 0,
  position: 'absolute', whiteSpace: 'nowrap', width: '1px',
} as const;
