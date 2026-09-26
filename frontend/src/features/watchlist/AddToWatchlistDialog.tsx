import AddIcon from '@mui/icons-material/Add';
import CheckIcon from '@mui/icons-material/Check';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Skeleton from '@mui/material/Skeleton';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { toApiError } from '@/api/apiError';
import { useNotify } from '@/app/providers/notificationContext';
import { useAddStock, useCreateWatchlist, useWatchlists } from './hooks';
import { WatchlistFormDialog } from './WatchlistFormDialog';

interface AddToWatchlistDialogProps {
  symbol: string | null;
  onClose: () => void;
}

/** Choose a watchlist for a stock (or create one). Lists that already contain the stock are marked. */
export function AddToWatchlistDialog({ symbol, onClose }: AddToWatchlistDialogProps) {
  const watchlists = useWatchlists();
  const addStock = useAddStock();
  const createWatchlist = useCreateWatchlist();
  const notify = useNotify();
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);
  const [pendingId, setPendingId] = useState<string | null>(null);

  const add = async (watchlistId: string, watchlistName: string) => {
    if (!symbol) return;
    setPendingId(watchlistId);
    try {
      await addStock.mutateAsync({ watchlistId, symbol });
      notify({
        message: `Added ${symbol} to ${watchlistName}`,
        action: { label: 'View', onClick: () => navigate(`/app/watchlists/${watchlistId}`) },
      });
      onClose();
    } catch (error) {
      notify({ message: toApiError(error).message, severity: 'error' });
    } finally {
      setPendingId(null);
    }
  };

  return (
    <>
      <Dialog open={symbol !== null && !creating} onClose={onClose} fullWidth maxWidth="xs" aria-labelledby="add-to-watchlist-title">
        <DialogTitle id="add-to-watchlist-title">Add {symbol} to a watchlist</DialogTitle>
        <DialogContent dividers sx={{ p: 0 }}>
          {watchlists.isPending && <Skeleton height={56} sx={{ mx: 2 }} aria-label="Loading watchlists" />}
          {watchlists.isError && (
            <Alert severity="error" sx={{ m: 2 }}>{toApiError(watchlists.error).message}</Alert>
          )}
          {watchlists.data?.length === 0 && (
            <Alert severity="info" sx={{ m: 2 }}>You have no watchlists yet. Create one to start following stocks.</Alert>
          )}
          <List aria-label="Your watchlists">
            {watchlists.data?.map((watchlist) => {
              const contains = symbol !== null && watchlist.symbols.includes(symbol);
              return (
                <ListItem
                  key={watchlist.id}
                  secondaryAction={contains ? (
                    <Button size="small" disabled startIcon={<CheckIcon />}>Added</Button>
                  ) : (
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={<AddIcon />}
                      loading={pendingId === watchlist.id}
                      disabled={pendingId !== null && pendingId !== watchlist.id}
                      onClick={() => add(watchlist.id, watchlist.name)}
                      aria-label={`Add ${symbol} to ${watchlist.name}`}
                    >
                      Add
                    </Button>
                  )}
                >
                  <ListItemText primary={watchlist.name} secondary={`${watchlist.stockCount} stocks`} />
                </ListItem>
              );
            })}
          </List>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 1.5, justifyContent: 'space-between' }}>
          <Button startIcon={<AddIcon />} onClick={() => setCreating(true)}>New watchlist</Button>
          <Button onClick={onClose}>Close</Button>
        </DialogActions>
      </Dialog>
      <WatchlistFormDialog
        open={creating}
        mode="create"
        onClose={() => setCreating(false)}
        onSubmit={async (name) => {
          const created = await createWatchlist.mutateAsync(name);
          setCreating(false);
          await add(created.id, created.name);
        }}
      />
    </>
  );
}
