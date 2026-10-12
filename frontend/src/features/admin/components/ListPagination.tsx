import Box from '@mui/material/Box';
import TablePagination from '@mui/material/TablePagination';
import type { Page } from '@/models/api';
import { PAGE_SIZES } from './useListParams';

interface ListPaginationProps {
  page: Page<unknown>['page'];
  /** Names the navigation landmark, e.g. "Users pages". */
  label: string;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
}

/** Server-side pagination for admin lists (`page` is 0-based, as in the API). */
export function ListPagination({ page, label, onPageChange, onSizeChange }: ListPaginationProps) {
  const lastPage = Math.max(0, page.totalPages - 1);
  return (
    <Box component="nav" aria-label={label} sx={{ borderTop: 1, borderColor: 'divider' }}>
      <TablePagination
        component="div"
        count={page.totalElements}
        page={Math.min(page.number, lastPage)}
        rowsPerPage={page.size}
        rowsPerPageOptions={[...PAGE_SIZES]}
        onPageChange={(_, next) => onPageChange(next)}
        onRowsPerPageChange={(event) => onSizeChange(Number(event.target.value))}
        showFirstButton
        showLastButton
      />
    </Box>
  );
}
