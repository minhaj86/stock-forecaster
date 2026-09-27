import SearchIcon from '@mui/icons-material/Search';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import InputAdornment from '@mui/material/InputAdornment';
import Link from '@mui/material/Link';
import Pagination from '@mui/material/Pagination';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { TableSkeleton } from '@/components/common/Skeletons';
import { AddToWatchlistButton } from '@/features/watchlist/AddToWatchlistButton';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { Stock } from '@/models/stock';
import { useCatalogue, useSectors, useStockSearch } from './hooks';
import { SEARCH_DEBOUNCE_MS } from './StockSearch';

const ASSET_LABELS: Record<Stock['assetType'], string> = { COMMON_STOCK: 'Stock', ADR: 'ADR', ETF: 'ETF' };

function StockList({ stocks }: { stocks: Stock[] }) {
  return (
    <TableContainer>
      <Table aria-label="Stocks">
        <TableHead>
          <TableRow>
            <TableCell>Symbol</TableCell>
            <TableCell>Company</TableCell>
            <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Sector</TableCell>
            <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>Exchange</TableCell>
            <TableCell align="right">Watch</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {stocks.map((stock) => (
            <TableRow key={stock.symbol} hover>
              <TableCell sx={{ fontWeight: 700 }}>
                <Link component={RouterLink} to={`/app/stocks/${encodeURIComponent(stock.symbol)}`} underline="hover">
                  {stock.symbol}
                </Link>
              </TableCell>
              <TableCell>
                <Typography variant="body2">{stock.companyName}</Typography>
                {stock.assetType !== 'COMMON_STOCK' && <Chip size="small" label={ASSET_LABELS[stock.assetType]} sx={{ mt: 0.5 }} />}
              </TableCell>
              <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{stock.sector ?? '—'}</TableCell>
              <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{stock.exchange}</TableCell>
              <TableCell align="right"><AddToWatchlistButton symbol={stock.symbol} variant="icon" /></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export default function ExplorePage() {
  const [input, setInput] = useState('');
  const [sector, setSector] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const query = useDebouncedValue(input, SEARCH_DEBOUNCE_MS).trim();
  const searching = query.length > 0;
  const search = useStockSearch(query);
  const catalogue = useCatalogue(sector, page);
  const sectors = useSectors();
  const active = searching ? search : catalogue;
  const stocks = searching ? search.data ?? [] : catalogue.data?.content ?? [];

  return (
    <>
      <PageHeader title="Explore stocks" subtitle="Search the catalogue or browse by sector, then add stocks to a watchlist." />
      <Stack spacing={2} sx={{ mb: 2 }}>
        <TextField
          value={input}
          onChange={(event) => setInput(event.target.value)}
          label="Search by ticker or company"
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
          sx={{ maxWidth: 520 }}
        />
        {!searching && (
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }} role="group" aria-label="Filter by sector">
            <Chip label="All sectors" color={sector === null ? 'primary' : 'default'} onClick={() => { setSector(null); setPage(0); }} />
            {sectors.data?.map((name) => (
              <Chip key={name} label={name} color={sector === name ? 'primary' : 'default'}
                variant={sector === name ? 'filled' : 'outlined'} onClick={() => { setSector(name); setPage(0); }} />
            ))}
          </Box>
        )}
      </Stack>

      {active.isError && <ErrorState title="Unable to load stocks" error={active.error} onRetry={() => void active.refetch()} />}
      <Card>
        {active.isPending && (searching || !catalogue.data) ? <TableSkeleton rows={8} label="Loading stocks" /> : stocks.length === 0 ? (
          <EmptyState title={searching ? `No stocks match “${query}”` : 'No stocks in this sector'}
            description="Try a ticker such as MSFT or a company name such as Microsoft." />
        ) : (
          <StockList stocks={stocks} />
        )}
        {!searching && catalogue.data && catalogue.data.page.totalPages > 1 && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
            <Pagination
              count={catalogue.data.page.totalPages}
              page={page + 1}
              onChange={(_, next) => setPage(next - 1)}
              color="primary"
              aria-label="Catalogue pages"
            />
          </Box>
        )}
      </Card>
    </>
  );
}
