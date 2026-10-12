import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import { useTheme } from '@mui/material/styles';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import useMediaQuery from '@mui/material/useMediaQuery';
import type { MouseEvent, ReactNode } from 'react';

export interface Column<T> {
  id: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  align?: 'left' | 'right';
  /** Hides the column on medium screens (it remains part of the small-screen card). */
  hideBelowMd?: boolean;
}

interface DataTableProps<T> {
  /** Accessible name of the table (or of the card list on small screens). */
  label: string;
  rows: T[];
  columns: Column<T>[];
  rowKey: (row: T) => string;
  /** Opens the row's details; rows also contain a real link for keyboard users. */
  onRowClick?: (row: T) => void;
  /** Small-screen rendering of a row. */
  renderCard: (row: T) => ReactNode;
  /** Previous page shown while the next one loads. */
  dimmed?: boolean;
}

const INTERACTIVE = 'a, button, input, label, [role="menuitem"], [role="checkbox"]';

/** Server-paginated admin table that becomes a list of cards below the `sm` breakpoint. */
export function DataTable<T>({ label, rows, columns, rowKey, onRowClick, renderCard, dimmed }: DataTableProps<T>) {
  const theme = useTheme();
  const compact = useMediaQuery(theme.breakpoints.down('sm'));

  if (compact) {
    return (
      <Stack component="ul" spacing={1.5} aria-label={label}
        sx={{ listStyle: 'none', p: 1.5, m: 0, opacity: dimmed ? 0.6 : 1 }}>
        {rows.map((row) => (
          <Box component="li" key={rowKey(row)}>
            <Card variant="outlined">{renderCard(row)}</Card>
          </Box>
        ))}
      </Stack>
    );
  }

  const clickRow = (event: MouseEvent<HTMLTableRowElement>, row: T) => {
    const target = event.target as HTMLElement;
    // Controls in the row handle their own clicks; portalled content (menus, dialogs) is not part of the row.
    if (!onRowClick || !event.currentTarget.contains(target) || target.closest(INTERACTIVE)) return;
    onRowClick(row);
  };

  const hidden = (column: Column<T>) => (column.hideBelowMd ? { display: { xs: 'none', md: 'table-cell' } } : undefined);

  return (
    <TableContainer sx={{ opacity: dimmed ? 0.6 : 1, transition: 'opacity 150ms' }}>
      <Table aria-label={label}>
        <TableHead>
          <TableRow>
            {columns.map((column) => (
              <TableCell key={column.id} align={column.align} sx={hidden(column)}>{column.header}</TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow
              key={rowKey(row)}
              hover={Boolean(onRowClick)}
              onClick={(event) => clickRow(event, row)}
              sx={onRowClick ? { cursor: 'pointer' } : undefined}
            >
              {columns.map((column) => (
                <TableCell key={column.id} align={column.align} sx={{ ...hidden(column), fontVariantNumeric: 'tabular-nums' }}>
                  {column.cell(row)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
