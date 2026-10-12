import AddIcon from '@mui/icons-material/Add';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CircularProgress from '@mui/material/CircularProgress';
import Link from '@mui/material/Link';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { EmptyState } from '@/components/common/EmptyState';
import { AdminErrorState } from '../components/AdminErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { TableSkeleton } from '@/components/common/Skeletons';
import type { AdminStock, AdminStockFilters, StockActivityFilter } from '@/models/admin';
import { formatDate, formatDateTime, formatInteger } from '@/utils/format';
import { DataTable, type Column } from '../components/DataTable';
import { DebouncedSearchField } from '../components/DebouncedSearchField';
import { FilterBar } from '../components/Filters';
import { ListPagination } from '../components/ListPagination';
import { RefreshWarning } from '../components/RefreshWarning';
import { MarketDataStatusChip, StockActiveChip } from '../components/StatusChips';
import { useListParams } from '../components/useListParams';
import { JOB_STATUS_LABELS, JOB_TYPE_LABELS, parseOption } from '../labels';
import { adminPaths } from '../paths';
import { AddStockDialog } from './AddStockDialog';
import { useAdminStocks } from './hooks';

const ACTIVITY: StockActivityFilter[] = ['ACTIVE', 'INACTIVE'];

function StockStatus({ stock }: { stock: AdminStock }) {
  const job = stock.marketData.activeJob;
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 0.75 }}>
      <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap' }}>
        <StockActiveChip active={stock.active} />
        <MarketDataStatusChip status={stock.marketData.dataStatus} />
      </Box>
      {job && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
          <CircularProgress size={12} aria-hidden {...(job.status === 'RUNNING' ? { variant: 'determinate', value: Math.max(job.progress, 5) } : {})} />
          <Typography variant="caption">
            {JOB_TYPE_LABELS[job.type]} {job.status === 'RUNNING' ? `${job.progress}%` : JOB_STATUS_LABELS[job.status].toLowerCase()}
          </Typography>
        </Box>
      )}
    </Box>
  );
}

export default function AdminStocksPage() {
  const navigate = useNavigate();
  const { get, page, size, setFilters, setPage, setSize } = useListParams();
  const status = parseOption(get('status'), ACTIVITY);
  const filters: AdminStockFilters = { query: get('query') || undefined, status, page, size };
  const stocks = useAdminStocks(filters);
  const filtered = Boolean(filters.query || status);
  const [adding, setAdding] = useState(false);
  const clearFilters = () => setFilters({ query: null, status: null });

  const symbolLink = (stock: AdminStock) => (
    <Link component={RouterLink} to={adminPaths.stock(stock.symbol)} underline="hover" sx={{ fontWeight: 700 }}>
      {stock.symbol}
    </Link>
  );

  const columns: Column<AdminStock>[] = [
    { id: 'symbol', header: 'Symbol', cell: symbolLink },
    {
      id: 'company',
      header: 'Company',
      cell: (stock) => <Typography variant="body2" sx={{ maxWidth: 260 }} noWrap title={stock.companyName}>{stock.companyName}</Typography>,
    },
    { id: 'exchange', header: 'Exchange', cell: (stock) => stock.exchange, hideBelowMd: true },
    { id: 'currency', header: 'Currency', cell: (stock) => stock.currency, hideBelowMd: true },
    { id: 'dataThrough', header: 'Data through', cell: (stock) => formatDate(stock.marketData.lastBarDate) },
    { id: 'records', header: 'Records', align: 'right', cell: (stock) => formatInteger(stock.marketData.barCount) },
    { id: 'status', header: 'Status', cell: (stock) => <StockStatus stock={stock} /> },
    { id: 'lastSync', header: 'Last sync', cell: (stock) => formatDateTime(stock.marketData.lastSuccessAt), hideBelowMd: true },
  ];

  return (
    <>
      <PageHeader
        title="Stocks"
        subtitle="The stock catalogue and the market data stored for each stock."
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setAdding(true)}>Add stock</Button>}
      />
      <Card>
        <FilterBar label="Filter stocks" active={filtered} onClear={clearFilters}>
          <DebouncedSearchField label="Search by symbol or company" value={get('query')} onChange={(query) => setFilters({ query })}
            sx={{ minWidth: { xs: '100%', sm: 300 } }} />
          <ToggleButtonGroup
            exclusive
            size="small"
            value={status ?? 'ALL'}
            aria-label="Stock status"
            onChange={(_, next: string | null) => next && setFilters({ status: next === 'ALL' ? null : next })}
          >
            <ToggleButton value="ALL">All</ToggleButton>
            <ToggleButton value="ACTIVE">Active</ToggleButton>
            <ToggleButton value="INACTIVE">Inactive</ToggleButton>
          </ToggleButtonGroup>
        </FilterBar>

        {stocks.isPending && <TableSkeleton rows={8} label="Loading stocks" />}
        {stocks.isError && !stocks.data && (
          <Box sx={{ p: 2 }}>
            <AdminErrorState title="Unable to load stocks" error={stocks.error} onRetry={() => void stocks.refetch()} />
          </Box>
        )}
        {stocks.isError && stocks.data && <Box sx={{ px: 2, pt: 2 }}><RefreshWarning error={stocks.error} /></Box>}
        {stocks.data && stocks.data.content.length === 0 && (
          <EmptyState
            icon={<Inventory2OutlinedIcon />}
            title={filtered ? 'No stocks match these filters' : 'The catalogue is empty'}
            description={filtered ? 'Try another symbol or company name, or clear the filters.'
              : 'Add stocks from the market-data provider or enter them manually.'}
            action={filtered
              ? <Button variant="outlined" onClick={clearFilters}>Clear filters</Button>
              : <Button variant="contained" onClick={() => setAdding(true)}>Add stock</Button>}
          />
        )}
        {stocks.data && stocks.data.content.length > 0 && (
          <>
            <DataTable
              label="Stocks"
              rows={stocks.data.content}
              columns={columns}
              rowKey={(stock) => stock.id}
              onRowClick={(stock) => navigate(adminPaths.stock(stock.symbol))}
              dimmed={stocks.isPlaceholderData}
              renderCard={(stock) => (
                <CardActionArea onClick={() => navigate(adminPaths.stock(stock.symbol))} sx={{ p: 2 }}
                  aria-label={`Open ${stock.symbol}`}>
                  <Typography sx={{ fontWeight: 700 }}>{stock.symbol}</Typography>
                  <Typography variant="body2" color="text.secondary" noWrap>{stock.companyName} · {stock.exchange}</Typography>
                  <Box sx={{ my: 1 }}><StockStatus stock={stock} /></Box>
                  <Typography variant="caption" color="text.secondary">
                    {formatInteger(stock.marketData.barCount)} records · data through {formatDate(stock.marketData.lastBarDate)}
                  </Typography>
                </CardActionArea>
              )}
            />
            <ListPagination page={stocks.data.page} label="Stocks pages" onPageChange={setPage} onSizeChange={setSize} />
          </>
        )}
      </Card>
      <AddStockDialog open={adding} onClose={() => setAdding(false)} />
    </>
  );
}
