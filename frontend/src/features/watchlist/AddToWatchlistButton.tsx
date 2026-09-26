import BookmarkAddOutlinedIcon from '@mui/icons-material/BookmarkAddOutlined';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import { useState } from 'react';
import { AddToWatchlistDialog } from './AddToWatchlistDialog';

export function AddToWatchlistButton({ symbol, variant = 'button' }: { symbol: string; variant?: 'button' | 'icon' }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      {variant === 'icon' ? (
        <Tooltip title={`Add ${symbol} to a watchlist`}>
          <IconButton size="small" onClick={() => setOpen(true)} aria-label={`Add ${symbol} to a watchlist`}>
            <BookmarkAddOutlinedIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      ) : (
        <Button variant="contained" startIcon={<BookmarkAddOutlinedIcon />} onClick={() => setOpen(true)}>
          Watchlist
        </Button>
      )}
      <AddToWatchlistDialog symbol={open ? symbol : null} onClose={() => setOpen(false)} />
    </>
  );
}
