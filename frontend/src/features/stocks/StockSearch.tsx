import BookmarkAddOutlinedIcon from '@mui/icons-material/BookmarkAddOutlined';
import SearchIcon from '@mui/icons-material/Search';
import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { AddToWatchlistDialog } from '@/features/watchlist/AddToWatchlistDialog';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { Stock } from '@/models/stock';
import { useStockSearch } from './hooks';

export const SEARCH_DEBOUNCE_MS = 350;

/**
 * Global type-ahead search. Requests are debounced (one request per pause in typing, not per keystroke); choosing a
 * result opens the stock, the bookmark button adds it to a watchlist. Ctrl/⌘+K focuses the field.
 */
export function StockSearch() {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const [input, setInput] = useState('');
  const [addSymbol, setAddSymbol] = useState<string | null>(null);
  const query = useDebouncedValue(input, SEARCH_DEBOUNCE_MS);
  const search = useStockSearch(query);
  const options = input.trim() && query.trim() ? search.data ?? [] : [];

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        inputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  return (
    <>
      <Autocomplete<Stock, false, false, false>
        options={options}
        filterOptions={(candidates) => candidates}
        getOptionLabel={(option) => `${option.symbol} ${option.companyName}`}
        isOptionEqualToValue={(option, value) => option.symbol === value.symbol}
        value={null}
        inputValue={input}
        onInputChange={(_, value, reason) => reason !== 'reset' && setInput(value)}
        onChange={(_, option) => {
          if (option) {
            setInput('');
            navigate(`/app/stocks/${encodeURIComponent(option.symbol)}`);
          }
        }}
        loading={search.isFetching}
        noOptionsText={input.trim() ? (search.isFetching ? 'Searching…' : 'No matching stocks') : 'Type a ticker or company name'}
        size="small"
        sx={{ width: { xs: '100%', sm: 320, md: 400 } }}
        slotProps={{ popper: { sx: { minWidth: { sm: 420 } } } }}
        renderOption={(props, option) => {
          const { key, ...optionProps } = props;
          return (
            <Box component="li" key={key} {...optionProps} sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
              <Typography sx={{ fontWeight: 700, minWidth: 64 }}>{option.symbol}</Typography>
              <Typography variant="body2" noWrap sx={{ flexGrow: 1, minWidth: 0 }}>{option.companyName}</Typography>
              <Chip label={option.exchange} size="small" variant="outlined" />
              <Tooltip title={`Add ${option.symbol} to a watchlist`}>
                <IconButton
                  size="small"
                  aria-label={`Add ${option.symbol} to a watchlist`}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={(event) => {
                    event.stopPropagation();
                    setAddSymbol(option.symbol);
                  }}
                >
                  <BookmarkAddOutlinedIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Box>
          );
        }}
        renderInput={(params) => (
          <TextField
            {...params}
            inputRef={inputRef}
            placeholder="Search stocks…"
            slotProps={{
              ...params.slotProps,
              htmlInput: { ...params.slotProps.htmlInput, 'aria-label': 'Search stocks' },
              input: {
                ...params.slotProps.input,
                startAdornment: (
                  <InputAdornment position="start"><SearchIcon fontSize="small" aria-hidden /></InputAdornment>
                ),
                endAdornment: (
                  <>
                    {search.isFetching && <CircularProgress color="inherit" size={16} aria-label="Searching" />}
                    {params.slotProps.input.endAdornment}
                  </>
                ),
              },
            }}
          />
        )}
      />
      <AddToWatchlistDialog symbol={addSymbol} onClose={() => setAddSymbol(null)} />
    </>
  );
}
