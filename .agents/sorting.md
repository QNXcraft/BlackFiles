# .agents/sorting.md
## Sorting Engine — Implementation Details

### SortMode Enum Values

| Constant    | Description                              |
|-------------|------------------------------------------|
| `NAME_ASC`  | Alphabetical A→Z (case-insensitive)      |
| `NAME_DESC` | Alphabetical Z→A (case-insensitive)      |
| `DATE_ASC`  | Oldest first (lastModified millis)       |
| `DATE_DESC` | Newest first (lastModified millis)       |
| `SIZE_ASC`  | Smallest first (bytes)                   |
| `SIZE_DESC` | Largest first (bytes)                    |
| `TYPE`      | Extension alphabetical A→Z               |

### Directory-First Rule
`FileSorter.sort()` wraps every comparator with `dirFirst()`:
- All directories are always sorted **before** files regardless of the active `SortMode`.
- Within the directory group and within the file group, the selected `SortMode` applies.

### Comparator Implementation Notes
- All comparators are `static final` fields in `FileSorter` (no lambdas — Java 7).
- `String.compareToIgnoreCase()` is used for name and extension comparisons.
- Tie-breaking: name ascending is used as a secondary comparator when primary values are equal.

### Edge Cases
- Hidden files (name starts with `.`) are included in sort results; visibility filtering is
  done by `FileProvider` **before** `FileSorter.sort()` is called.
- Files with no extension return `""` from `FileItem.getExtension()` — they sort to the
  **front** of the TYPE sort (empty string < any letter).
- Remote file sizes for directories are typically 0 — the SIZE sort is less useful for remote
  listings but still functions correctly.
- `lastModified` for some remote protocols (especially SMB and WebDAV) may be 0 if the server
  does not provide modification times. These sort consistently to the front/back but display
  as empty in the UI.
