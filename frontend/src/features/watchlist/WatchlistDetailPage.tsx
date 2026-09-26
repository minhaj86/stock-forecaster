import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd';
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutlined';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import { useMemo, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { toApiError } from '@/api/apiError';
import { useNotify } from '@/app/providers/notificationContext';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { Disclaimer } from '@/components/common/Disclaimer';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { TableSkeleton } from '@/components/common/Skeletons';
import { StockMetricsTable, type StockRow } from '@/components/tables/StockMetricsTable';
import { useSnapshots } from '@/features/stocks/hooks';
import { AddStockDialog } from './AddStockDialog';
import { useAddStock, useDeleteWatchlist, useRemoveStock, useRenameWatchlist, useWatchlist } from './hooks';
import { WatchlistFormDialog } from './WatchlistFormDialog';

export default function WatchlistDetailPage() {
  const { watchlistId = '' } = useParams();
  const navigate = useNavigate();
  const notify = useNotify();
  const watchlist = useWatchlist(watchlistId);
  const symbols = useMemo(() => watchlist.data?.items.map((item) => item.stock.symbol) ?? [], [watchlist.data]);
  const snapshots = useSnapshots(symbols);
  const removeStock = useRemoveStock();
  const addStock = useAddStock();
  const renameWatchlist = useRenameWatchlist();
  const deleteWatchlist = useDeleteWatchlist();
  const [adding, setAdding] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const rows: StockRow[] = useMemo(() => {
    const bySymbol = new Map(snapshots.data?.map((snapshot) => [snapshot.symbol, snapshot]));
    return (watchlist.data?.items ?? []).map((item) => ({
      symbol: item.stock.symbol,
      companyName: item.stock.companyName,
      exchange: item.stock.exchange,
      currency: item.stock.currency,
      snapshot: bySymbol.get(item.stock.symbol),
    }));
  }, [watchlist.data, snapshots.data]);

  if (watchlist.isPending) {
    return <Card><TableSkeleton rows={6} label="Loading watchlist" /></Card>;
  }
  if (watchlist.isError) {
    const notFound = toApiError(watchlist.error).status === 404;
    return notFound ? (
      <Card>
        <EmptyState
          title="Watchlist not found"
          description="It may have been deleted, or the link is not one of your watchlists."
          action={<Button component={RouterLink} to="/app/watchlists" variant="contained">Back to watchlists</Button>}
        />
      </Card>
    ) : (
      <ErrorState title="Unable to load this watchlist" error={watchlist.error} onRetry={() => void watchlist.refetch()} />
    );
  }

  const list = watchlist.data;
  const remove = async (symbol: string) => {
    try {
      await removeStock.mutateAsync({ watchlistId: list.id, symbol });
      notify({
        message: `Removed ${symbol} from ${list.name}`,
        action: { label: 'Undo', onClick: () => void addStock.mutateAsync({ watchlistId: list.id, symbol }) },
      });
    } catch (error) {
      notify({ message: toApiError(error).message, severity: 'error' });
    }
  };

  return (
    <>
      <Button component={RouterLink} to="/app/watchlists" startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>
        All watchlists
      </Button>
      <PageHeader
        title={list.name}
        subtitle={`${list.stockCount} ${list.stockCount === 1 ? 'stock' : 'stocks'}`}
        actions={(
          <>
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAdding(true)}>Add stock</Button>
            <Button startIcon={<EditOutlinedIcon />} onClick={() => setRenaming(true)}>Rename</Button>
            <Button color="error" startIcon={<DeleteOutlineIcon />} onClick={() => setDeleting(true)}>Delete</Button>
          </>
        )}
      />

      {snapshots.isError && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Unable to load market data: {toApiError(snapshots.error).message} The stock list is still shown.
        </Alert>
      )}

      <Card>
        {list.items.length === 0 ? (
          <EmptyState
            icon={<PlaylistAddIcon />}
            title="This watchlist is empty"
            description="Add stocks to track their prices, returns and volatility."
            action={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setAdding(true)}>Add stock</Button>}
          />
        ) : (
          <StockMetricsTable
            rows={rows}
            columns={['price', 'today', 'return1M', 'return1Y', 'volatility30d']}
            label={`Stocks in ${list.name}`}
            sortable
            rowAction={(row) => (
              <Tooltip title={`Remove ${row.symbol}`}>
                <IconButton size="small" onClick={() => void remove(row.symbol)} aria-label={`Remove ${row.symbol} from ${list.name}`}>
                  <RemoveCircleOutlineIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
          />
        )}
      </Card>
      <Disclaimer />

      <AddStockDialog
        open={adding}
        watchlistId={list.id}
        watchlistName={list.name}
        existingSymbols={symbols}
        onClose={() => setAdding(false)}
      />
      <WatchlistFormDialog
        open={renaming}
        mode="rename"
        initialName={list.name}
        onClose={() => setRenaming(false)}
        onSubmit={(name) => renameWatchlist.mutateAsync({ id: list.id, name })}
      />
      <ConfirmDialog
        open={deleting}
        title={`Delete ${list.name}?`}
        description={`This removes the watchlist and its ${list.stockCount} stocks. This cannot be undone.`}
        confirmLabel="Delete"
        destructive
        pending={deleteWatchlist.isPending}
        onConfirm={async () => {
          try {
            await deleteWatchlist.mutateAsync(list.id);
            notify({ message: `Deleted ${list.name}` });
            navigate('/app/watchlists', { replace: true });
          } catch (error) {
            notify({ message: toApiError(error).message, severity: 'error' });
            setDeleting(false);
          }
        }}
        onClose={() => setDeleting(false)}
      />
    </>
  );
}
