# IP Filter Lessons Learned (#1291)

## Critical Bug: libtorrent ip_filter flags were inverted

In libtorrent 2.x, `ip_filter.add_rule(start, end, flags)` semantics are:
- `flags=0` → **ALLOW** (no flags set)
- `flags=1` (`ip_filter::blocked`) → **BLOCK**

Our codebase had been passing `0` to `add_rule()` for years, meaning the IP Filter was **explicitly allowing the bad IPs instead of blocking them**.

Fixed all 5 call sites:
- `IPFilterPaneItem.java` (import, rebuild, load — 3 occurrences)
- `IPFilterImportTool.java`
- `IPFilterAddTool.java`

Test: `IPFilterBlockingTest` validates this headlessly without starting a full BTEngine session.

## Session state does NOT persist ip_filter

The assumption that `save_state_flags_t.all()` would persist `ip_filter` across restarts was **wrong**. We must use our own `ip_filter.db` file with atomic temp-file writes.

## Atomic file writes for binary DB

Writing directly to `ip_filter.db` from background threads caused corruption on partial writes or concurrent access. Fixed by:
1. Writing to `.tmp` file first
2. `fos.flush(); fos.close();`
3. `synchronized(IP_FILTER_DB_LOCK) { tmpFile.renameTo(getIPFilterDBFile()); }`

## Binary serialization hardening

`IPRange.writeObjectTo/readObjectFrom` uses a custom binary format. Hardened against corruption:
- Description capped to 255 bytes
- Uses `readFully()` instead of `read()`
- `EOFException` handling with graceful break
- Unknown `ipVersionType` falls back safely

## Table edit disappearance bug

`BasicDataLineModel.refresh()` had an off-by-one bug:
```java
// BUG — size is an invalid row index
fireTableRowsUpdated(0, _list.size());

// FIXED
if (end > 0) fireTableRowsUpdated(0, end - 1);
```

Passing `size` instead of `size-1` caused `JTable` paint to abort with an `ArrayIndexOutOfBoundsException`, making rows vanish after edit operations.

## Index-based editing is safer

For replacing a table row, use `getRow()` to find the exact index, then `remove(index)` + `add(newObj, index)`. `remove(Object)` relies on `equals()` which can match the wrong row if duplicates exist.

## macOS tooltip EDT freeze

On macOS with Java 26, tooltips on `JTable` trigger Input Method initialization on the EDT, causing freezes. Fixed by:
```java
ToolTipManager.sharedInstance().unregisterComponent(TABLE);
```

## macOS green popup menu background

`SkinPopupMenu` renders with a green tint on macOS when using Nimbus L&F. This is a **macOS-specific Nimbus L&F issue**, not our `SkinPopupMenu` class. Using plain `JPopupMenu` + explicit background does not fix it either.

## Always use com.frostwire.util.Logger

Never `org.apache.commons.logging` or `java.util.logging` in FrostWire desktop modules.

## Headless native tests work

`IPFilterBlockingTest` proves we can test jlibtorrent `ip_filter` behavior in a headless JUnit test without starting a full `BTEngine` session. This pattern should be used for other native behavior validation.
