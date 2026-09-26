import AddIcon from '@mui/icons-material/Add';
import CheckIcon from '@mui/icons-material/Check';
import SearchIcon from '@mui/icons-material/Search';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import InputAdornment from '@mui/material/InputAdornment';
import LinearProgress from '@mui/material/LinearProgress';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { toApiError } from '@/api/apiError';
import { useNotify } from '@/app/providers/notificationContext';
import { useStockSearch } from '@/features/stocks/hooks';
import { SEARCH_DEBOUNCE_MS } from '@/features/stocks/StockSearch';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { useAddStock } from './hooks';

interface AddStockDialogProps {
  open: boolean;
  watchlistId: string;
  watchlistName: string;
  existingSymbols: string[];
  onClose: () => void;
}

export function AddStockDialog({ open, watchlistId, watchlistName, existingSymbols, onClose }: AddStockDialogProps) {
  const [input, setInput] = useState('');
  const query = useDebouncedValue(input, SEARCH_DEBOUNCE_MS);
  const search = useStockSearch(query);
  const addStock = useAddStock();
  const notify = useNotify();
  const [pendingSymbol, setPendingSymbol] = useState<string | null>(null);
  const results = input.trim() ? search.data ?? [] : [];

  const add = async (symbol: string) => {
    setPendingSymbol(symbol);
    try {
      await addStock.mutateAsync({ watchlistId, symbol });
      notify({ message: `Added ${symbol} to ${watchlistName}` });
    } catch (error) {
      notify({ message: toApiError(error).message, severity: 'error' });
    } finally {
      setPendingSymbol(null);
    }
  };

  const close = () => {
    setInput('');
    onClose();
  };

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="sm" aria-labelledby="add-stock-title">
      <DialogTitle id="add-stock-title">Add stocks to {watchlistName}</DialogTitle>
      <DialogContent dividers>
        <TextField
          autoFocus
          fullWidth
          label="Search by ticker or company"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
        />
        {search.isFetching && <LinearProgress sx={{ mt: 1 }} aria-label="Searching" />}
        {search.isError && <Alert severity="error" sx={{ mt: 2 }}>{toApiError(search.error).message}</Alert>}
        {input.trim() && query.trim() && !search.isFetching && results.length === 0 && !search.isError && (
          <Alert severity="info" sx={{ mt: 2 }}>No stocks match “{query}”.</Alert>
        )}
        <List aria-label="Search results">
          {results.map((stock) => {
            const added = existingSymbols.includes(stock.symbol);
            return (
              <ListItem
                key={stock.symbol}
                divider
                secondaryAction={added ? (
                  <Button size="small" disabled startIcon={<CheckIcon />}>Added</Button>
                ) : (
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<AddIcon />}
                    loading={pendingSymbol === stock.symbol}
                    onClick={() => void add(stock.symbol)}
                    aria-label={`Add ${stock.symbol}`}
                  >
                    Add
                  </Button>
                )}
              >
                <ListItemText primary={`${stock.symbol} · ${stock.companyName}`} secondary={`${stock.exchange}${stock.sector ? ` · ${stock.sector}` : ''}`} />
              </ListItem>
            );
          })}
        </List>
      </DialogContent>
      <DialogActions>
        <Button onClick={close}>Done</Button>
      </DialogActions>
    </Dialog>
  );
}
