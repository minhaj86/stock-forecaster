import AddIcon from '@mui/icons-material/Add';
import BookmarksOutlinedIcon from '@mui/icons-material/BookmarksOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { toApiError } from '@/api/apiError';
import { useNotify } from '@/app/providers/notificationContext';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { CardGridSkeleton } from '@/components/common/Skeletons';
import type { WatchlistSummary } from '@/models/watchlist';
import { formatDate } from '@/utils/format';
import { useCreateWatchlist, useDeleteWatchlist, useRenameWatchlist, useWatchlists } from './hooks';
import { WatchlistFormDialog } from './WatchlistFormDialog';

const PREVIEW_SYMBOLS = 6;

export default function WatchlistsPage() {
  const navigate = useNavigate();
  const notify = useNotify();
  const watchlists = useWatchlists();
  const createWatchlist = useCreateWatchlist();
  const renameWatchlist = useRenameWatchlist();
  const deleteWatchlist = useDeleteWatchlist();
  const [creating, setCreating] = useState(false);
  const [renaming, setRenaming] = useState<WatchlistSummary | null>(null);
  const [deleting, setDeleting] = useState<WatchlistSummary | null>(null);
  const [menu, setMenu] = useState<{ anchor: HTMLElement; watchlist: WatchlistSummary } | null>(null);

  const confirmDelete = async () => {
    if (!deleting) return;
    try {
      await deleteWatchlist.mutateAsync(deleting.id);
      notify({ message: `Deleted ${deleting.name}` });
    } catch (error) {
      notify({ message: toApiError(error).message, severity: 'error' });
    } finally {
      setDeleting(null);
    }
  };

  return (
    <>
      <PageHeader
        title="My Watchlists"
        subtitle="Organise the stocks you follow. Market data is collected for every stock on a watchlist."
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Create watchlist</Button>}
      />

      {watchlists.isPending && <CardGridSkeleton count={3} label="Loading watchlists" />}
      {watchlists.isError && (
        <ErrorState title="Unable to load your watchlists" error={watchlists.error} onRetry={() => void watchlists.refetch()} />
      )}
      {watchlists.data?.length === 0 && (
        <Card>
          <EmptyState
            icon={<BookmarksOutlinedIcon />}
            title="No watchlists yet"
            description="Create a watchlist to start following stocks and build your personalized dashboard."
            action={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Create watchlist</Button>}
          />
        </Card>
      )}

      <Grid container spacing={2} component="ul" aria-label="Watchlists" sx={{ listStyle: 'none', p: 0, m: 0 }}>
        {watchlists.data?.map((watchlist) => (
          <Grid key={watchlist.id} size={{ xs: 12, sm: 6, lg: 4 }} component="li">
            <Card sx={{ height: '100%', display: 'flex' }}>
              <CardActionArea onClick={() => navigate(`/app/watchlists/${watchlist.id}`)} sx={{ flexGrow: 1 }}
                aria-label={`Open ${watchlist.name}`}>
                <CardContent>
                  <Typography variant="h6" component="h2" noWrap>{watchlist.name}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {watchlist.stockCount} {watchlist.stockCount === 1 ? 'stock' : 'stocks'} · updated {formatDate(watchlist.updatedAt)}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', mt: 1.5, minHeight: 24 }}>
                    {watchlist.symbols.slice(0, PREVIEW_SYMBOLS).map((symbol) => (
                      <Chip key={symbol} label={symbol} size="small" />
                    ))}
                    {watchlist.symbols.length > PREVIEW_SYMBOLS && (
                      <Chip label={`+${watchlist.symbols.length - PREVIEW_SYMBOLS}`} size="small" variant="outlined" />
                    )}
                  </Box>
                </CardContent>
              </CardActionArea>
              <Box sx={{ p: 1 }}>
                <IconButton
                  aria-label={`Actions for ${watchlist.name}`}
                  onClick={(event) => setMenu({ anchor: event.currentTarget, watchlist })}
                >
                  <MoreVertIcon />
                </IconButton>
              </Box>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Menu anchorEl={menu?.anchor} open={menu !== null} onClose={() => setMenu(null)}>
        <MenuItem onClick={() => { setRenaming(menu?.watchlist ?? null); setMenu(null); }}>
          <ListItemIcon><EditOutlinedIcon fontSize="small" /></ListItemIcon>Rename
        </MenuItem>
        <MenuItem onClick={() => { setDeleting(menu?.watchlist ?? null); setMenu(null); }} sx={{ color: 'error.main' }}>
          <ListItemIcon><DeleteOutlineIcon fontSize="small" color="error" /></ListItemIcon>Delete
        </MenuItem>
      </Menu>

      <WatchlistFormDialog
        open={creating}
        mode="create"
        onClose={() => setCreating(false)}
        onSubmit={async (name) => {
          const created = await createWatchlist.mutateAsync(name);
          notify({ message: `Created ${created.name}` });
        }}
      />
      <WatchlistFormDialog
        open={renaming !== null}
        mode="rename"
        initialName={renaming?.name}
        onClose={() => setRenaming(null)}
        onSubmit={(name) => renameWatchlist.mutateAsync({ id: renaming!.id, name })}
      />
      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.name ?? 'watchlist'}?`}
        description={`This removes the watchlist and its ${deleting?.stockCount ?? 0} stocks. This cannot be undone.`}
        confirmLabel="Delete"
        destructive
        pending={deleteWatchlist.isPending}
        onConfirm={() => void confirmDelete()}
        onClose={() => setDeleting(null)}
      />
    </>
  );
}
