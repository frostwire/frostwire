# AGENTS.md - FrostWire Development Guidelines

This document describes FrostWire's coding standards, architecture preferences, and development practices to help agents (and developers) contribute effectively to the project.

## Project Overview

FrostWire is a peer-to-peer client spanning multiple platforms:
- **android/** - Android client
- **desktop/** - Desktop client (Swing)
- **common/** - Shared code used by both platforms

The codebase is ~20 years old with mature patterns and extensive utilities for most operations. The project is a monorepo with each folder being independently deployable.

## IMPORTANT: Commits & Code Changes

### Never Commit Without Explicit Permission
**DO NOT commit code changes unless explicitly told to do so.** Always ask before committing, even if code is complete and tested.

### Git Commit Message Format
When you ARE asked to commit, prefix commit messages with the affected main directory in square brackets:
- `[common]` - Changes to the common/ directory
- `[desktop]` - Changes to the desktop/ directory
- `[android]` - Changes to the android/ directory
- `[common][desktop][android]` - Changes affecting multiple directories (rare)

**Examples:**
```
[common] Add new utility function for image processing
[desktop] Fix EDT blocking in transfer detail panel
[android] Improve search result filtering performance
[common][desktop][android] Update third-party dependency version
```

These prefixes help parse the monorepo history and quickly identify which clients are affected by each change.

---

## Core Design Philosophy

### Simplicity First
- **Brutal simplicity** is preferred over clever or complex solutions
- **KISS principle** (Keep It Stupid Simple) - everything should be straightforward to understand
- Avoid over-engineering and unnecessary abstractions
- Only add complexity when truly required by the problem domain

### Composition Over Inheritance
- Prefer composition to inheritance - inheritance should be rare and strictly necessary
- Default to composition patterns for code reuse
- Avoid deep inheritance hierarchies

### Immutability Preference
- Favor immutable data structures and objects
- Only introduce mutability when strictly necessary for performance or design requirements

### DRY Principle
- Do not repeat code - if something appears twice, it should be extracted into a function
- The DRY principle naturally uncovers beautiful, maintainable code patterns

### Code Reuse Over Recreation
- FrostWire is mature with 20 years of utility functions
- **Always check if a utility exists before creating new code**
- Common utilities are constantly evolving, understand them before reaching for new solutions

---

## Platform-Specific Guidelines

### Common (Shared Code)

#### Package Organization
The `common/` directory houses core utilities and abstractions used by both platforms:

- **com.frostwire.util** - General utilities
  - `JsonUtils.java` - Use for all JSON serialization/deserialization (wraps Gson)
  - `StringUtils.java` - Comprehensive string manipulation
  - `UrlUtils.java` - URL encoding/decoding
  - `TaskThrottle.java` - Thread-safe task submission throttling (prevents spamming)
  - `Hex.java` - Hexadecimal conversion
  - `Logger.java` - Logging with context prefix support
  - `OSUtils.java` - Platform detection
  - `MimeDetector.java` - MIME type detection

- **com.frostwire.util.http** - HTTP client abstractions
  - `HttpClient.java` - Interface for HTTP operations
  - `HttpClientFactory.java` - Factory for creating appropriate HTTP client

- **com.frostwire.concurrent** - Threading utilities
  - `ExecutorsHelper.java` - Factory methods for executors and thread factories
    - `newProcessingQueue(String name)` - Single-threaded executor (prefer this)
    - `newFixedSizeThreadPool(int size, String name)` - Multi-threaded pool
    - `newScheduledThreadPool(int corePoolSize, String name)` - Scheduled execution
  - All created threads are daemon threads and have automatic timeout

- **com.frostwire.regex** - Advanced regex support
  - `Pattern.java` and `Matcher.java` - Wrappers around java.util.regex with named group support
  - Named groups syntax: `(?<name>expression)`
  - Uses Google RE2J internally for safety and performance

- **com.frostwire.platform** - Platform abstraction layer
  - `Platform.java` - Interface for platform-specific operations
  - Implemented separately for Android and Desktop

- **com.frostwire.service** - Core services
  - `ErrorService.java` - Centralized error reporting
  - `MessageService.java` - Message passing

- **com.frostwire.transfers** - Transfer management abstractions
  - `Transfer.java`, `TransferState.java`, `TransferItem.java`
  - Base classes for implementing downloads and uploads

- **com.frostwire.bittorrent** - BitTorrent protocol support
  - `BTEngine.java` - Core torrent engine
  - `BTDownload.java` - Torrent download abstraction
  - Handles all BitTorrent operations

#### Common Code Style
- Prefer composition and dependency injection
- Use immutable objects whenever possible
- Avoid platform-specific code in common/
- Write tests for any bug-prone or complex logic
- Use existing utilities from common/ before creating new ones

---

### Desktop (Swing)

#### Threading & Event Dispatch Thread (EDT) Protection

**Critical Rule**: Never block the Event Dispatch Thread. This causes UI freezing.

**Infrastructure:**
```java
// Located in: com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService
BackgroundQueuedExecutorService.schedule(Runnable r)  // Queue for background execution
BackgroundQueuedExecutorService.submit(Callable<T> c) // Submit with return value

// Located in: com.limegroup.gnutella.gui.GUIMediator
GUIMediator.safeInvokeAndWait(Runnable r)  // Sync execution on EDT
GUIMediator.safeInvokeLater(Runnable r)    // Async execution on EDT
```

**Threading Pattern Examples:**

```java
// ANTI-PATTERN: Do NOT block EDT
new Thread(() -> { /* expensive work */ }).start();
Thread.sleep(1000);  // NEVER

// PATTERN 1: Simple background work
BackgroundQueuedExecutorService.schedule(() -> {
    // Expensive IO or computation here
    List<File> files = loadFilesFromDisk();
    // Post results back to EDT if needed
    GUIMediator.safeInvokeLater(() -> updateTableWithFiles(files));
});

// PATTERN 2: Background work with return value
Future<Integer> future = BackgroundQueuedExecutorService.submit(() -> {
    return expensiveCalculation();
});
// Later, on background thread:
int result = future.get();  // Block on background thread, not EDT

// PATTERN 3: Icon/resource loading (lazy load)
private Icon getIcon() {
    if (!iconLoaded && !iconScheduledForLoad) {
        iconScheduledForLoad = true;
        BackgroundQueuedExecutorService.schedule(() ->
            GUIMediator.safeInvokeAndWait(() -> {
                icon = IconManager.instance().getIconForFile(file);
                iconLoaded = true;
                tableModel.refresh();  // Trigger table refresh on EDT
            })
        );
        return null;
    }
    return iconLoaded ? icon : null;
}

// PATTERN 4: Cache expensive computations
private volatile int cachedCount = 0;
private volatile long lastUpdateTime = 0;
private final AtomicBoolean isUpdating = new AtomicBoolean(false);

int getCount() {
    long now = System.currentTimeMillis();
    if (now - lastUpdateTime > 500) {  // Update cache every 500ms max
        scheduleCountUpdate();
    }
    return cachedCount;  // Return stale value if update in progress
}

private void scheduleCountUpdate() {
    if (!isUpdating.compareAndSet(false, true)) {
        return;  // Prevent concurrent updates
    }
    BackgroundQueuedExecutorService.schedule(() -> {
        try {
            int newCount = computeCountExpensively();
            cachedCount = newCount;
            lastUpdateTime = System.currentTimeMillis();
        } finally {
            isUpdating.set(false);
        }
    });
}

// PATTERN 5: Deferred component initialization
// HTML rendering in Swing triggers expensive font metrics calculations
SwingUtilities.invokeLater(() -> {
    label.setText("<html>Some HTML content</html>");
    component.setFont(new Font("Helvetica", Font.PLAIN, 12));
});
```

**Key EDT Rules:**
1. Never use bare `new Thread()` - use `BackgroundQueuedExecutorService`
2. Check if work is expensive before executing on EDT
3. Use caching for frequently-accessed expensive computations
4. Load icons, fonts, and HTML content in background
5. Post UI updates via `GUIMediator.safeInvokeLater()` not `SwingUtilities.invokeLater()`
6. Never call `Future.get()` on EDT - only on background threads

#### UI Architecture - MVC Table Pattern

Desktop uses a sophisticated three-tier table architecture:

**Tier 1: Mediator (Controller)**
```java
// Located in: com.frostwire.gui.bittorrent.BTDownloadMediator
public class BTDownloadMediator extends AbstractTableMediator<BTDownloadDataLine, ...> {
    // Controls user interactions, manages table refresh cycle
    public void doRefresh() {
        // Called ~1/sec from global refresh timer
        dataModel.refresh();  // Updates all visible rows
        updateActionButtons();  // Async updates expensive checks
    }
}
```

**Tier 2: Model (Data)**
```java
// Located in: com.frostwire.gui.bittorrent.BTDownloadModel
public class BTDownloadModel extends AbstractTableModel {
    // Holds table data, manages caching and refresh
    // Updates data lines in background
    public void refresh() {
        dataLines.forEach(line -> line.updateAsync());
    }
}
```

**Tier 3: DataLine (Row Data)**
```java
// Located in: com.frostwire.gui.bittorrent.BTDownloadDataLine
public class BTDownloadDataLine {
    // Represents one table row with all column data
    // Caches values computed during previous update
    public Object getValueAt(int column) {
        switch (column) {
            case PROGRESS_COLUMN: return progressCache;  // Pre-computed
            case SIZE_COLUMN: return sizeCache;
            default: return null;
        }
    }
}
```

**Desktop Code Style:**
- Classes: `*Mediator` (controller), `*Model` (data), `*DataLine` (row), `*Manager` (service), `*Panel` (component)
- Methods: `doRefresh()`, `updateData()`, `getValueAt()`, `schedule()`, `safeInvoke*()`
- Fields: Private fields use `_underscore` prefix (legacy LimeWire convention)
- Comments: Explain EDT implications and threading decisions

---

### Android

#### Threading & Main Thread Protection

**Critical Rule**: Never block the main UI thread.

**Infrastructure:**
```java
// Named background handler threads (located in: com.frostwire.android.util.SystemUtils)
enum HandlerThreadName {
    SEARCH_PERFORMER,   // Web queries, search operations, Python initialization
    DOWNLOADER,         // Download/upload operations, media scanning, file I/O
    CONFIG_MANAGER,     // SharedPreferences/ConfigurationManager access
    MISC                // General background work, cleanup, other operations
}

// Usage:
SystemUtils.postToHandler(HandlerThreadName.DOWNLOADER, () -> {
    // Runs on dedicated DOWNLOADER thread
});

SystemUtils.postToUIThread(Runnable r)              // Post to main thread
SystemUtils.postToUIThreadAtFront(Runnable r)       // Post to front of main thread queue
SystemUtils.isUIThread()                            // Check if on main thread
SystemUtils.ensureUIThreadOrCrash(String location)  // Assert on main thread
SystemUtils.ensureBackgroundThreadOrCrash(String)   // Assert NOT on main thread
```

**Threading Pattern Examples:**

```java
// ANTI-PATTERN: Do NOT do this on main thread
new Thread(() -> { /* expensive work */ }).start();
expensiveIO();  // Blocking call on main thread

// PATTERN 1: Simple background task
SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> {
    // Expensive operation here
    File result = performExpensiveFileOperation();
    // Update UI on main thread
    SystemUtils.postToUIThread(() -> updateUI(result));
});

// PATTERN 2: With re-dispatch from main thread
public void someMethod() {
    if (SystemUtils.isUIThread()) {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC,
            () -> someMethod());  // Recursive call on background thread
        return;
    }
    SystemUtils.ensureBackgroundThreadOrCrash("ClassName::someMethod");
    // Heavy work here
}

// PATTERN 3: Search queries (Python, network)
SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, () -> {
    // Initialize Python runtime if needed
    if (!Python.isStarted()) {
        Engine.startPython();
    }
    // Perform search
    List<SearchResult> results = performSearch(query);
    // Post results to UI
    SystemUtils.postToUIThread(() -> displayResults(results));
});

// PATTERN 4: Configuration access (expensive SharedPreferences I/O)
SystemUtils.postToHandler(SystemUtils.HandlerThreadName.CONFIG_MANAGER, () -> {
    ConfigurationManager cm = ConfigurationManager.instance();
    boolean seedingEnabled = cm.isSeedFinishedTorrents();
    if (seedingEnabled) {
        // Chain to DOWNLOADER for heavy file operations
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER,
            () -> seedTheFile(file));
    }
});

// PATTERN 5: Memory leak prevention with WeakReference
WeakReference<Context> ctxRef = Ref.weak(context);
SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> {
    Context ctx = ctxRef.get();
    if (!Ref.alive(ctxRef)) {
        Ref.free(ctxRef);
        return;  // Context was garbage collected
    }
    // Safe to use ctx
    doWorkWithContext(ctx);
});

// PATTERN 6: Media scanning after download
SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> {
    MediaScannerConnection.scanFile(context,
        new String[]{ filePath },
        new String[]{ mimeType },
        (path, uri) -> LOG.info("Media scan complete"));
});
```

**Handler Thread Selection Guide:**

| Thread | Use For | Examples |
|--------|---------|----------|
| SEARCH_PERFORMER | Web/network queries, search, Python | HTTP requests, torrent searches, Telluride queries |
| DOWNLOADER | Download/upload ops, media scanning, file I/O | Torrent operations, HTTP downloads, file copying, scanning |
| CONFIG_MANAGER | SharedPreferences access | ConfigurationManager reads, preference checks |
| MISC | General background work, initialization, cleanup | App startup, file deletion, thread pool creation, misc I/O |

**Key Android Rules:**
1. Never use bare `new Thread()` - use `SystemUtils.postToHandler()`
2. Choose the appropriate `HandlerThreadName` for the work type
3. Always check `isUIThread()` at method entry if called from unknown context
4. Use `ensureUIThreadOrCrash()` and `ensureBackgroundThreadOrCrash()` to enforce thread contracts
5. Protect against context leaks with `WeakReference`
6. Use `postToUIThread()` or `postToUIThreadAtFront()` to dispatch results back to main thread
7. ConfigurationManager and SharedPreferences must be accessed on CONFIG_MANAGER thread
8. Network I/O should happen on SEARCH_PERFORMER or DOWNLOADER threads
9. Chain handler thread calls when moving between work types (e.g., CONFIG_MANAGER -> DOWNLOADER)

#### Android Activity/Fragment Architecture

**Base Classes:**
- `AbstractActivity` - Base class for all activities, handles common setup
- `AbstractFragment` - Base class for all fragments, handles layout inflation and view binding

**Activity Pattern:**
```java
public class MyActivity extends AbstractActivity {
    public MyActivity() {
        super(R.layout.activity_my);  // Pass layout resource ID
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeComponents();  // Override to initialize views
    }
}
```

**Fragment Pattern:**
```java
public class MyFragment extends AbstractFragment {
    public MyFragment() {
        super(R.layout.fragment_my);  // Pass layout resource ID
    }

    @Override
    protected void initComponents(View rootView, Bundle savedInstanceState) {
        // Find and initialize views here
        Button btn = findView(rootView, R.id.my_button);
        btn.setOnClickListener(v -> onButtonClicked());
    }
}
```

**Android Code Style:**
- Classes: PascalCase (SearchFragment, TransferDetailActivity)
- Methods/Variables: camelCase
- Constants: UPPER_SNAKE_CASE
- Handler thread usage marked in comments: `// Runs on DOWNLOADER thread`
- Resource references use `R.id.name`, `R.layout.name` constants
- Use `findView()` helper from AbstractFragment instead of `findViewById()`
- Store references as `WeakReference` when context/activity is involved

#### Android Memory Management - Never Pass Vanilla Context Objects

**Critical Rule**: Never pass raw `Context` objects across threads or store them directly. Always wrap in `WeakReference` to prevent memory leaks.

**Why**:
- Context objects hold references to Activities/Fragments
- Long-lived threads can keep these references alive indefinitely
- This prevents Activities from being garbage collected, leaking memory
- Memory leaks are especially problematic on mobile devices with limited RAM

**The Ref Utility Class** (located in `com.frostwire.util.Ref`):

```java
// Located in: common/src/main/java/com/frostwire/util/Ref.java
public final class Ref {
    public static <T> WeakReference<T> weak(T obj) {
        return new WeakReference<>(obj);  // Create weak reference
    }

    public static <T> boolean alive(Reference<T> ref) {
        return ref != null && ref.get() != null;  // Check if object still exists
    }

    public static void free(Reference<?> ref) {
        if (ref != null) {
            ref.clear();  // Help GC by clearing the reference
        }
    }

    public static <T> SoftReference<T> soft(T obj) {
        return new SoftReference<>(obj);  // For caching (GC-friendly)
    }

    public static <T> PhantomReference<T> phantom(T obj, ReferenceQueue<? super T> q) {
        return new PhantomReference<>(obj, q);  // For tracking object finalization
    }
}
```

**Pattern 1: Background Task with Context Access**

```java
// AsyncStartDownload.java (Android)
public AsyncStartDownload(final Context ctx, final SearchResult sr, final String message) {
    // ALWAYS wrap context in WeakReference
    WeakReference<Context> ctxRef = Ref.weak(ctx);

    // Post to background thread with weak reference
    postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> run(ctxRef, sr, message));
}

private void run(WeakReference<Context> ctxRef, final SearchResult sr, final String message) {
    try {
        // Check if context is still alive before using
        if (!Ref.alive(ctxRef)) {
            Ref.free(ctxRef);
            return;  // Activity was destroyed, bail out
        }

        // Safe to get context now
        final Transfer transfer = doInBackground(ctxRef.get(), sr, message);

        if (transfer == null) {
            Ref.free(ctxRef);
            return;
        }

        // Post UI update back to main thread
        postToUIThreadAtFront(() -> {
            if (!Ref.alive(ctxRef)) {
                Ref.free(ctxRef);
                return;  // Activity destroyed, UI no longer valid
            }
            try {
                onPostExecute(ctxRef.get(), sr, message, transfer);
            } finally {
                Ref.free(ctxRef);  // Always free the reference
            }
        });
    } catch (Throwable t) {
        LOG.error(t.getMessage(), t);
    }
}
```

**Key Points**:
- Wrap context immediately: `Ref.weak(ctx)`
- Check alive before using: `if (!Ref.alive(ctxRef)) { return; }`
- Get context for use: `ctxRef.get()`
- Always free at end: `Ref.free(ctxRef)` in finally blocks

**Pattern 2: Listener Holding Context References**

```java
// HandpickedTorrentDownloadDialogOnFetch.java (Android)
public class HandpickedTorrentDownloadDialogOnFetch implements TorrentFetcherListener {
    private final WeakReference<Context> contextRef;
    private final WeakReference<FragmentManager> fragmentManagerRef;

    public HandpickedTorrentDownloadDialogOnFetch(AppCompatActivity activity, boolean openTransfersOnCancel) {
        // Wrap all references immediately
        contextRef = Ref.weak(activity);
        fragmentManagerRef = Ref.weak(activity.getSupportFragmentManager());
        // ... more initialization
    }

    @Override
    public void onTorrentInfoFetched(byte[] torrentInfoData, String magnetUri, long id) {
        createHandpickedTorrentDownloadDialog(torrentInfoData, magnetUri, id, openTransfersOnCancel);
    }

    private void createHandpickedTorrentDownloadDialog(byte[] data, String magnetUri,
                                                        long id, boolean openTransfers) {
        // Check all references are alive before proceeding
        if (!Ref.alive(contextRef) || !Ref.alive(fragmentManagerRef) ||
            data == null || data.length == 0) {
            LOG.warn("Incomplete conditions to create dialog.");
            return;
        }

        FragmentManager fragmentManager = fragmentManagerRef.get();
        if (fragmentManager == null) {
            LOG.warn("FragmentManager is null.");
            return;
        }

        try {
            final HandpickedTorrentDownloadDialog dlg =
                HandpickedTorrentDownloadDialog.newInstance(
                    contextRef.get(),  // Safe to use now
                    TorrentInfo.bdecode(data),
                    magnetUri,
                    id,
                    openTransfers);

            dlg.show(fragmentManager, "DIALOG_TAG");
        } catch (Throwable t) {
            LOG.warn("Could not create or show dialog", t);
        }
    }
}
```

**Pattern 3: Adapter Holding Context**

```java
// MenuAdapter.java (Android)
public class MenuAdapter extends BaseAdapter {
    private final WeakReference<Context> contextRef;  // Store as weak reference
    private final LayoutInflater inflater;
    private final List<MenuAction> items;

    public MenuAdapter(Context context, String title, List<MenuAction> items) {
        // Wrap context in WeakReference
        this.contextRef = new WeakReference<>(context);
        this.inflater = LayoutInflater.from(context);  // OK to use immediately
        this.items = items;
    }

    public Context getContext() {
        // Safe getter: checks if context is still alive
        Context result = null;
        if (Ref.alive(contextRef)) {
            result = contextRef.get();
        }
        return result;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MenuAction item = getItem(position);

        if (convertView == null) {
            // LayoutInflater is safe to use (not holding Context)
            convertView = inflater.inflate(R.layout.view_menu_list_item, parent, false);
        }

        TextView textView = (TextView) convertView;
        textView.setTag(item);
        textView.setText(item.getText());
        return convertView;
    }
}
```

**Pattern 4: Cross-Thread Context Passing Checklist**

```java
// Best practice pattern for passing Context across threads

// 1. Accept context
public void somePublicMethod(Context context, SomeData data) {
    // 2. Wrap immediately
    WeakReference<Context> ctxRef = Ref.weak(context);

    // 3. Post to background thread
    SystemUtils.postToHandler(HandlerThreadName.DOWNLOADER, () -> {
        doBackgroundWork(ctxRef, data);
    });
}

private void doBackgroundWork(WeakReference<Context> ctxRef, SomeData data) {
    try {
        // 4. Check alive before each use
        if (!Ref.alive(ctxRef)) {
            return;
        }

        Context ctx = ctxRef.get();
        // ... use ctx safely ...

        // 5. Post results back to main thread if needed
        SystemUtils.postToUIThread(() -> {
            if (!Ref.alive(ctxRef)) {
                return;
            }
            updateUI(ctxRef.get());
        });
    } finally {
        // 6. Always free the reference
        Ref.free(ctxRef);
    }
}
```

**Reference Types Available**:
- `Ref.weak(T)` - **Most common** - GC collects whenever memory is needed
- `Ref.soft(T)` - For caching - GC only collects when memory is critical
- `Ref.phantom(T, queue)` - For cleanup tracking - use rarely

**When to Check Alive**:
- Before every use of `ref.get()`
- At function entry points
- After returning from background operations
- Before updating UI with stale context

**Android UI Components**

**Adapter Pattern** - Extends `AbstractListAdapter` or `SearchResultListAdapter`:
```java
public class MySearchResultAdapter extends SearchResultListAdapter {
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        SearchResult sr = getItem(position);
        // Build view for search result
        return view;
    }
}
```

**Dialog Pattern** - Custom dialogs inherit from `AbstractDialog`
**View Pattern** - Custom views extend appropriate Android base classes

---

## Synchronization Primitives

### CountdownLatch - Our Preferred Synchronization Tool

`CountdownLatch` is our first choice for many synchronization scenarios. It's a simple, one-way gate that's perfect for "signal when complete" patterns.

**Why We Prefer CountdownLatch:**
- **Simple semantics** - Clear intent: "Wait for this to complete"
- **One-way gate** - Count down from N to 0, then stays at 0 (can't be reset)
- **Non-reusable** - Forces correct usage (prevents accidental latch reuse bugs)
- **Multiple waiters** - Many threads can await the same latch
- **Timeout support** - Prevents deadlocks with `await(timeout, unit)`
- **Efficient** - Minimal overhead for signaling

**Common Use Cases in FrostWire:**

#### 1. One-Time Initialization Barrier
```java
// ConfigurationManager.java (Android)
private static final CountDownLatch creationLatch = new CountDownLatch(1);
private static ConfigurationManager instance;
private static Thread creatorThread;

public static void create(Context context) {
    if (instance != null) {
        throw new RuntimeException("CHECK YOUR LOGIC: create() can only be called once.");
    }
    creatorThread = new Thread(() -> {
        instance = new ConfigurationManager(context.getApplicationContext());
        creationLatch.countDown();  // Signal: initialization complete
    });
    creatorThread.start();
}

public static ConfigurationManager instance() {
    if (instance == null) {
        try {
            // Wait for initialization (with timeout to prevent ANR on Android)
            // ANRs triggered after 5 seconds, so timeout after 4 seconds
            creationLatch.await(4, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    if (instance == null) {
        waitForCreatorThread();  // Fallback wait with join
        if (instance == null) {
            throw new RuntimeException("Initialization timed out");
        }
    }
    return instance;
}
```

**Pattern**: Create a static latch with count=1, initialize in background thread, call `countDown()` when ready, callers `await()` the latch before accessing.

#### 2. Lazy Initialization with Early Access Protection
```java
// Engine.java (Android) - Python runtime initialization
private static final CountDownLatch pythonStarterLatch = new CountDownLatch(1);
private static Python pythonInstance;
private static final Object pythonStarterLock = new Object();

public static void startPython() {
    if (Python.isStarted()) {
        return;  // Already initialized
    }
    if (SystemUtils.isUIThread()) {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, Engine::startPython);
        return;
    }
    try {
        synchronized (pythonStarterLock) {
            Python.start(androidPlatform);
            pythonInstance = Python.getInstance();
            if (pythonStarterLatch.getCount() > 0) {
                pythonStarterLatch.countDown();  // Signal: Python ready
            }
        }
    } catch (Throwable t) {
        LOG.error("Python initialization FAILED", t);
        if (!Python.isStarted()) {
            SystemUtils.postToHandlerDelayed(SystemUtils.HandlerThreadName.MISC,
                Engine::startPython, 10000);  // Retry in 10 seconds
        }
    }
}

public static Python getPythonInstance() {
    try {
        pythonStarterLatch.await();  // Block until Python is ready
    } catch (InterruptedException e) {
        LOG.error("Error waiting for Python", e);
    }
    return pythonInstance;
}
```

**Pattern**: Lazily initialize expensive resource in background, multiple threads can call the getter and will block until ready.

#### 3. One-Time Setup Signal
```java
// BTEngine.java (Common) - BitTorrent context setup
private final static CountDownLatch ctxSetupLatch = new CountDownLatch(1);
public static BTContext ctx;

public static BTEngine getInstance() {
    if (ctx == null) {
        try {
            ctxSetupLatch.await();  // Wait for context to be set up
        } catch (InterruptedException e) {
            LOG.error(e.getMessage(), e);
        }
        if (ctx == null && Loader.INSTANCE.isRunning()) {
            throw new IllegalStateException("BTContext can't be null");
        }
    }
    return Loader.INSTANCE;
}

public static void onCtxSetupComplete() {
    ctxSetupLatch.countDown();  // Signal: context is ready
}
```

**Pattern**: Some initialization event signals completion via `countDown()`, waiters block on `await()`.

#### 4. Per-Instance Operation Completion
```java
// MediaScanner.java (Android) - Media scanning completion
private static void scanFiles(final Context context, List<String> paths, int retries) {
    final CountDownLatch finishSignal = new CountDownLatch(paths.size());

    MediaScannerConnection.scanFile(context, paths.toArray(new String[0]), null,
        (path, uri) -> {
            try {
                // Process result
                boolean success = (uri != null);
                // ... validation ...
            } finally {
                finishSignal.countDown();  // Signal: one file scanned
            }
        });

    try {
        finishSignal.await();  // Wait for all files to be scanned
    } catch (InterruptedException e) {
        LOG.error("Error waiting for media scan", e);
    }
}
```

**Pattern**: Create latch with count=N (number of operations), each callback `countDown()`, wait for all to complete.

#### 5. Per-Instance with Timeout and Retry
```java
// TellurideSearchPerformer.java (Common) - Search operation completion
public class TellurideSearchPerformer implements ISearchPerformer {
    private final CountDownLatch performerLatch = new CountDownLatch(1);

    public void perform() {
        stopped = false;
        TellurideLauncher.launch(tellurideLauncher, url, null, false, true, false,
            new TellurideProcessListener(this));
        try {
            performerLatch.await();  // Wait for search to complete
        } catch (InterruptedException e) {
            LOG.error("Search interrupted", e);
        }
        stopped = true;
    }

    // Called by listener when search completes
    void onSearchComplete() {
        performerLatch.countDown();  // Signal: search done
    }
}
```

**Pattern**: Per-instance latch for operation tracking, signals when operation completes.

### When NOT to Use CountdownLatch

- **Reusable gates**: Need to reset/reuse → use `CyclicBarrier` instead
- **Reader/Writer patterns**: Multiple readers, exclusive writers → use `ReadWriteLock`
- **Mutual exclusion**: Protecting critical section → use `synchronized` or `ReentrantLock`
- **Atomic operations**: Simple counters/flags → use `AtomicInteger`, `AtomicBoolean`
- **Semaphores**: Controlling access to N resources → use `Semaphore`

### Synchronization Strategy Summary

| Scenario | Tool | Why |
|----------|------|-----|
| One-time initialization gate | CountdownLatch | Simple, clear intent, prevents reuse bugs |
| Lazy initialization with early access | CountdownLatch | Multiple waiters, timeout-capable |
| Prevent concurrent updates | AtomicBoolean.compareAndSet() | Lock-free, high performance |
| Volatile cross-thread access | volatile keyword | For simple flags/references |
| Multi-threaded caching | volatile + compareAndSet | Combine for cache updates |
| Protect critical section | synchronized block | Simple, when you control the data |
| Complex threading scenarios | Choose appropriate concurrency util | Locks, semaphores, barriers |

---

## Utility Libraries & Reusable Components

FrostWire is mature with 20 years of accumulated utilities. **Always check if something exists before building it.**

### Checking for Existing Utilities

**Android Developers**: Pay special attention to `Ref` utility for managing Context references.

```java
// Android Context Management - CRITICAL
// ALWAYS use Ref utility for Context references
import com.frostwire.util.Ref;
import java.lang.ref.WeakReference;
WeakReference<Context> ctxRef = Ref.weak(context);
if (Ref.alive(ctxRef)) {
    Context ctx = ctxRef.get();
    // Use ctx safely here
}
Ref.free(ctxRef);

// JSON - Do NOT use raw Gson
import com.frostwire.util.JsonUtils;
Object obj = JsonUtils.toObject(jsonString, MyClass.class);
String json = JsonUtils.toJson(obj, true);  // true = pretty-print

// Regex - Use our wrapper with named group support
import com.frostwire.regex.Pattern;
import com.frostwire.regex.Matcher;
Pattern p = Pattern.compile("(?<year>\\d{4})-(?<month>\\d{2})");
Matcher m = p.matcher("2024-12");
if (m.find()) {
    String year = m.group("year");  // Named groups work!
}

// HTTP - Use HttpClientFactory
import com.frostwire.util.http.HttpClientFactory;
HttpClient client = HttpClientFactory.getInstance(HttpContext.GENERAL);
String response = client.get(url);

// Strings - Use StringUtils for common operations
import com.frostwire.util.StringUtils;
String[] parts = StringUtils.split(str, delimiter);
boolean match = StringUtils.startsWithIgnoreCase(str, "prefix");
String truncated = StringUtils.truncate(str, maxLen);

// Threading - Use ExecutorsHelper
import com.frostwire.concurrent.concurrent.ExecutorsHelper;
ExecutorService executor = ExecutorsHelper.newProcessingQueue("MyWorker");
executor.submit(() -> doWork());

// MIME types
import com.frostwire.util.MimeDetector;
String mimeType = MimeDetector.getMimeType("file.mp4");

// URL encoding
import com.frostwire.util.UrlUtils;
String encoded = UrlUtils.encode(urlString);

// Hex conversion
import com.frostwire.util.Hex;
byte[] bytes = Hex.decode(hexString);
String hex = Hex.encode(bytes, false);  // false = lowercase

// Logging
import com.frostwire.util.Logger;
private static final Logger LOG = Logger.getLogger(ClassName.class);
LOG.info("Message");
LOG.error("Message", exception);
```

### When to Create New Utilities

Only create new utilities if:
1. The functionality doesn't already exist in `common/`
2. It's used in multiple places (DRY principle)
3. It's general-purpose, not specific to one class
4. It belongs in one of the existing utility packages

---

## Security & Privacy

FrostWire is a peer-to-peer client with important security and privacy considerations:

### User Privacy
- Minimize data collection and sharing
- Default to secure, privacy-respecting implementations
- No telemetry or tracking without explicit user consent
- Consider anonymity implications of features

### Security Bugs
Avoid common vulnerabilities:
- **Injection attacks** - Sanitize all external input
- **XSS attacks** - Escape HTML/JavaScript when necessary
- **CSRF attacks** - Use appropriate security tokens for state-changing operations
- **Insecure cryptography** - Use strong, modern crypto algorithms
- **Command injection** - Never pass unsanitized input to shell commands
- **SQL injection** - Use parameterized queries
- **Insecure file operations** - Validate file paths, use appropriate permissions
- **Information disclosure** - Don't log sensitive data (passwords, tokens, etc.)

### P2P Specific Concerns
- Leaking real IP address through DHT/trackers
- Exposing user's file list without consent
- Downloading malware or malicious content
- Seeding copyrighted content inadvertently

---

## Testing

### Test Philosophy
- Write tests for programmatically reproducible bugs
- Tests help ensure correctness and prevent regressions
- Tests serve as documentation for expected behavior
- Prefer tests in `common/` as they don't depend on platform-specific code

### Where to Write Tests

**Preferred: common/**
```java
// Located in: common/src/test/java/com/frostwire/...
public class MyUtilityTest {
    @Test
    public void testSomeFunction() {
        assertEquals(expected, MyUtility.someFunction(input));
    }
}
```

**Platform-Specific (if necessary):**
```java
// android/src/androidTest/java/com/frostwire/android/...
// desktop/src/test/java/com/frostwire/gui/...
```

### Testing Guidelines
- Test boundary conditions and edge cases
- Test error handling and exceptions
- Keep tests focused on one behavior
- Use clear, descriptive test names (e.g., `testShouldReturnNegativeWhenInputIsEmpty`)
- Mock external dependencies (network, file system, etc.)

---

## Development Workflow

### General Approach

1. **Read existing code first** - Don't propose changes without understanding the context
2. **Check for existing utilities** - FrostWire likely has what you need
3. **Keep it simple** - Prefer straightforward solutions to clever ones
4. **Follow established patterns** - Look at similar code for style guidance
5. **Consider threading implications** - Especially for UI-related changes
6. **Think about security and privacy** - Always consider P2P client implications
7. **Write tests for bugs** - Reproducible bugs should have test cases
8. **Respect immutability** - Prefer immutable designs unless mutability is necessary
9. **Don't commit without permission** - Always ask before committing

### Code Organization Rules

**File Organization:**
- One public class per file (matching file name)
- Inner classes only for narrow, focused purposes
- Tests in parallel directory structure (`src/test` mirrors `src/main`)

**Method Organization:**
- Public static factory methods first
- Public instance methods second
- Protected/private implementation methods last
- Inner classes at the end

**Package Organization:**
- By feature/subsystem in desktop and android
- By functionality/utility type in common
- Keep related classes in same package
- Avoid default package access

### Code Review Checklist

Before asking for code to be committed, ensure:
- [ ] Is the solution as simple as possible?
- [ ] Are existing utilities being used?
- [ ] For desktop: Is the EDT protected from blocking?
- [ ] For Android: Are expensive operations off the main thread?
- [ ] Does this impact user privacy or security?
- [ ] Is there any duplicated code that should be extracted?
- [ ] Are tests written for any bugs being fixed?
- [ ] Do new utilities belong in `common/`?
- [ ] Are appropriate `HandlerThreadName` values used (Android)?
- [ ] Is error handling appropriate?

---

## Anti-Patterns (What to Avoid)

### Threading
- Creating bare `new Thread()` instances (use executors instead)
- Blocking the EDT (desktop) or main thread (Android)
- Not protecting frequent refresh operations with `AtomicBoolean`
- Ignoring cache invalidation for expensive computations
- Using `Future.get()` on UI threads
- Not re-dispatching when method called from wrong thread

### Code Organization
- Complex inheritance hierarchies (use composition)
- Mutable data structures as defaults
- Ignoring existing utilities and recreating solutions
- Putting platform-specific code in `common/`
- Package-private classes (use public or private explicitly)

### Data & Security
- Logging sensitive information (passwords, tokens, real IPs)
- Storing sensitive data in SharedPreferences unencrypted (Android)
- Not validating external input
- Assuming user input is safe
- **Leaking context references** (always use WeakReference - see Android Memory Management section)
- Hardcoded IP addresses or secrets
- Passing raw Context objects to background threads or storing in long-lived objects (Android)

### General
- Over-engineering solutions
- Over-complicated abstractions
- Duplicated code (extract to functions immediately)
- Comments that don't add value (code should be self-explanatory)
- Ignoring privacy/security implications

---

## Examples by Feature

### Adding a Simple Download Feature

1. **common/** - Add transfer type to `TransferState`, `TransferItem`
2. **common/** - Create `FooDownload implements Transfer` class
3. **desktop/** - Create `FooDownloadMediator extends AbstractTableMediator`
4. **desktop/** - Add `FooDownloadDataLine` for row data
5. **android/** - Create `FooDownloadAdapter extends SearchResultListAdapter`
6. **android/** - Use `SystemUtils.postToHandler(DOWNLOADER, ...)` to start downloads
7. **common/** - Write tests for `FooDownload` logic

### Fixing a Thread Safety Bug

1. **Identify** which thread(s) access the problematic data
2. **Add synchronization** - Use `volatile`, `AtomicBoolean`, `synchronized`, or move to single thread
3. **Add enforcement** - Use `ensureUIThreadOrCrash()` or `ensureBackgroundThreadOrCrash()`
4. **Test** - Write test that reproduces the race condition
5. **Document** - Add comment explaining thread safety strategy

### Adding a Search Provider

1. **common/** - Create `FooSearchPerformer implements SearchPerformer`
2. **common/** - Create `FooSearchResult implements SearchResult`
3. **android/** - Register performer in search initialization code
4. **desktop/** - Register performer if needed
5. **android/** - Use `SystemUtils.postToHandler(SEARCH_PERFORMER, ...)` for queries
6. **common/** - Write tests for parsing and result extraction

---

## Additional Patterns & Best Practices

Based on deeper codebase exploration, here are additional patterns commonly used throughout FrostWire:

### Error Handling

**Pattern**: Always catch `Throwable` (not just `Exception`) in critical paths, provide context in error messages (URLs, keys, file paths), and handle specific exception types for guidance.

```java
try {
    url = getSearchUrl(page, keywords);
    String text = fetchSearchPage(url);
} catch (Throwable e) {
    if (url == null) url = "n.a";
    if (e instanceof SSLPeerUnverifiedException) {
        LOG.error("Add " + getDomain() + " to Ssl.FWHostnameVerifier...");
    }
    LOG.error("Error [" + url + "]: " + e.getMessage(), e);
}
```

**Why**: Provides actionable error information; catches all failure types including `Error`; avoids silent failures.

### Resource Cleanup

**Pattern**: Use try-with-resources for AutoCloseable; defensive try-finally for others; always suppress exceptions in finally.

```java
// Modern: try-with-resources
try (InputStream is = new FileInputStream(file)) {
    // ... use is ...
}

// Legacy: try-finally
ZipFile zip = null;
try {
    zip = new ZipFile(file);
    // ... use zip ...
} finally {
    try {
        if (zip != null) zip.close();
    } catch (Throwable e) {
        // Suppress cleanup exceptions
    }
}
```

### Logging Conventions

**Pattern**: Use structured logging with context, appropriate levels, and stack trace extraction.

```java
private static final Logger LOG = Logger.getLogger(ClassName.class);

// Methods available:
LOG.info("Message");
LOG.info("Message", showCallerInfo);  // Include stack trace
LOG.error("Message", exception);
LOG.warn("Message", exception);

// Set context prefix for thread identification
Logger.setContextPrefix("prefix");
```

**Guidelines**:
- Never log passwords, tokens, API keys, or real IP addresses
- Use SEVERE/ERROR only for unexpected conditions
- Use INFO for normal operations users might want to see
- Use DEBUG for development diagnostics

### Exception Handling Strategy

**Pattern**: Create specific exception types for different error conditions; include causes; make classes final.

```java
// Don't use generic exceptions
throw new IOException("error");  // BAD

// Use specific types
final class RangeNotSupportedException extends HttpRangeException {
    private static final long serialVersionUID = -3356618211960630147L;
    RangeNotSupportedException(String message) {
        super(message);
    }
}

final class HttpRangeOutOfBoundsException extends HttpRangeException {
    HttpRangeOutOfBoundsException(int rangeStart, long expectedFileSize) {
        super("Range out of bounds: start=" + rangeStart +
              " expected size=" + expectedFileSize);
    }
}
```

**Why**: Allows catch blocks to handle specific error categories; includes serialVersionUID for serialization safety.

### Callback/Listener Pattern

**Pattern**: Define listener interfaces with `on*` methods; provide adapter classes with empty implementations; wrap callbacks in try-catch.

```java
interface HttpClientListener {
    void onError(HttpClient client, Throwable e);
    void onData(HttpClient client, byte[] buffer, int offset, int length);
    void onComplete(HttpClient client);
    void onCancel(HttpClient client);
    void onHeaders(HttpClient client, Map<String, List<String>> headerFields);
}

abstract class HttpClientListenerAdapter implements HttpClientListener {
    public void onError(HttpClient client, Throwable e) { }
    public void onData(HttpClient client, byte[] buffer, int offset, int length) { }
    public void onComplete(HttpClient client) { }
    public void onCancel(HttpClient client) { }
    public void onHeaders(HttpClient client, Map<String, List<String>> headerFields) { }
}

// Always wrap callbacks to prevent exceptions escaping
protected void onResults(List<? extends SearchResult> results) {
    try {
        if (results == null) results = new ArrayList<>();
        listener.onResults(token, results);
    } catch (Throwable e) {
        LOG.warn("Error sending results to listener: " + e.getMessage());
    }
}
```

### State Management & Caching

**Pattern**: Use volatile for cached status; AtomicBoolean with compareAndSet for concurrent updates; ConcurrentHashMap for lock-free access.

```java
// Cached expensive computation
private volatile TorrentStatus cachedStatus;
private volatile long lastStatusUpdateTime;
private final AtomicBoolean statusRefreshScheduled = new AtomicBoolean(false);

// Usage: check then update atomically
if (!statusRefreshScheduled.compareAndSet(false, true)) {
    return;  // Already updating
}
try {
    cachedStatus = expensiveComputation();
} finally {
    statusRefreshScheduled.set(false);
}
```

**Why**: Volatile for visibility; compareAndSet prevents concurrent cache updates; separates read-heavy from write-heavy operations.

### Task Throttling

**Pattern**: Prevent tasks from executing more frequently than necessary using TaskThrottle utility.

```java
// Only runs if 1000ms has passed since last execution
if (TaskThrottle.isReadyToSubmitTask("search-operation", 1000)) {
    performSearch();
}
```

**Why**: Reduces wake-ups, prevents CPU spikes, respects rate limits, prevents duplicate work.

### Configuration Management (Android)

**Pattern**: Initialize ConfigurationManager in background thread; use CountDownLatch for synchronization; timeout to prevent ANR.

```java
public static void create(Context context) {
    if (instance != null) {
        throw new RuntimeException("create() can only be called once");
    }
    creatorThread = new Thread(() -> {
        instance = new ConfigurationManager(context.getApplicationContext());
        creationLatch.countDown();
    });
    creatorThread.setName("ConfigurationManager::creator");
    creatorThread.start();
}

public static ConfigurationManager instance() {
    if (instance == null) {
        try {
            // ANRs triggered after 5 seconds, timeout after 4 seconds
            creationLatch.await(4, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    if (instance == null) {
        throw new RuntimeException("ConfigurationManager initialization timed out");
    }
    return instance;
}
```

**Why**: SharedPreferences I/O is expensive and can block UI; background initialization with timeout prevents ANR.

### HTTP Client Factory Pattern

**Pattern**: Create context-specific HTTP client pools to limit concurrency per operation type.

```java
public enum HttpContext {
    SEARCH,    // Web queries (limited connections)
    DOWNLOAD,  // Downloads (more connections)
    MISC       // Other operations
}

public static HttpClient getInstance(HttpContext context) {
    if (okHttpClientPools == null) {
        okHttpClientPools = buildThreadPools();
    }
    synchronized (okHTTPClientLock) {
        if (!fwOKHTTPClients.containsKey(context)) {
            fwOKHTTPClients.put(context,
                new OkHttpClientWrapper(okHttpClientPools.get(context)));
        }
    }
    return fwOKHTTPClients.get(context);
}

private static Map<HttpContext, ThreadPool> buildThreadPools() {
    final HashMap<HttpContext, ThreadPool> map = new HashMap<>();
    map.put(HttpContext.SEARCH, new ThreadPool("OkHttpClient-searches", 2, 2, 2, ...));
    map.put(HttpContext.DOWNLOAD, new ThreadPool("OkHttpClient-downloads", 2, 2, 2, ...));
    map.put(HttpContext.MISC, new ThreadPool("OkHttpClient-misc", 2, 2, 2, ...));
    return map;
}
```

**Why**: Prevents search operations from starving downloads; limits per-context resource usage; allows tuning per operation type.

### Immutability Patterns

**Pattern**: Use enums for state machines, readonly interfaces for data, final classes for exceptions.

```java
// Enum for state machine
public enum TransferState {
    DOWNLOADING,
    SEEDING,
    PAUSED,
    ERROR,
    COMPLETE;

    public static boolean isErrored(TransferState state) {
        return state == ERROR || state == /* other errors */;
    }
}

// Readonly interface
public interface Transfer {
    String getName();      // immutable String
    double getSize();      // primitive
    TransferState getState();  // immutable enum
    List<TransferItem> getItems();  // defensive copy
    // No setters!
}

// Final exceptions
final class RangeNotSupportedException extends HttpRangeException { }
```

### Build File Conventions

**Pattern**: Extract version info from manifest in Gradle; generate descriptive outputs; use platform detection for JVM args.

```gradle
// Extract version from manifest
def manifestVersionCode() {
    def ns = new Namespace("http://schemas.android.com/apk/res/android", "android")
    def xml = new groovy.xml.XmlParser().parse(manifestFile)
    return Integer.parseInt(xml.attributes()[ns.versionCode].toString())
}

// Dynamic APK naming
def changeApkOutput(variant) {
    def suffix = project.ext.versionName + '-b' + project.ext.versionCode
    variant.outputs.all { output ->
        outputFileName = "frostwire-android-" + suffix + '.apk'
    }
}

// Platform-specific JVM arguments
application {
    mainClass = 'com.limegroup.gnutella.gui.Main'
    applicationDefaultJvmArgs = [
        '-Djava.library.path=lib/native',
        '-Xms64m', '-Xmx512m', '-Xss256k'
    ]
    if (OperatingSystem.current().isMacOsX()) {
        applicationDefaultJvmArgs += ['-Dsun.java2d.metal=true']
    }
}
```

### File I/O with Progress

**Pattern**: Pre-allocate buffers; provide progress callbacks; support cancellation; use proper exception handling.

```java
byte[] buffer = new byte[32768];  // Pre-allocated 32KB buffer
int itemCount = getItemCount(zipFile);
int item = 0;

while ((ze = zis.getNextEntry()) != null) {
    item++;
    File newFile = new File(folder, ze.getName());

    // Report progress
    if (listener != null) {
        int progress = (int)(((double)(item * 100)) / itemCount);
        listener.onUnzipping(ze.getName(), progress);
    }

    try (FileOutputStream fos = new FileOutputStream(newFile)) {
        int n;
        while ((n = zis.read(buffer)) > 0) {
            fos.write(buffer, 0, n);
            // Support cancellation
            if (listener != null && listener.isCanceled()) {
                throw new IOException("Operation cancelled");
            }
        }
    } finally {
        zis.closeEntry();
    }
}
```

### Dependency Injection Pattern

**Pattern**: Pass dependencies via constructor (required); provide setters for optional; use factories for complex construction.

```java
// Constructor injection for required dependencies
public BTDownload(BTEngine engine, TorrentHandle th) {
    this.engine = engine;      // Required
    this.th = th;              // Required
    this.innerListener = new InnerListener();
    engine.addListener(innerListener);
}

// Setter injection for optional dependencies
public void setListener(SearchListener listener) {
    this.listener = listener;
}

// Factory for complex construction
public static HttpClient getInstance(HttpContext context) {
    // Logic to decide which implementation to create
}
```

### Testing Organization

**Pattern**: Mirror source structure; name tests descriptively; test complex logic, especially in common/.

```
common/src/main/java/.../SearchPattern.java
desktop/src/test/java/.../SearchPatternTest.java
                         (.../SearchPerformerTest.java)
```

**Guidelines**:
- Write tests for reproducible bugs
- Prefer tests in `common/` when possible (no platform dependencies)
- Use platform-specific tests only when necessary
- Name tests descriptively: `testShouldReturnNegativeWhenInputIsEmpty`
- Mock external dependencies (network, file system)

### Security Best Practices

**Pattern**: Validate response codes, escape filenames, check SSL certificates, avoid logging sensitive data.

```java
// Validate response codes
if (responseCode == 403) {
    throw new ResponseCodeNotSupportedException(403);
}

// Escape filenames to prevent directory traversal
private static String escapeFilename(String s) {
    return s.replaceAll("[\\\\/:*?\"<>|\\[\\]]+", "_");
}

// Check SSL certificates
if (e instanceof SSLPeerUnverifiedException) {
    LOG.error("Add domain to SSL verifier list");
}

// Never log sensitive data
LOG.error("Login failed");  // OK
LOG.error("Login failed: " + password);  // BAD - never do this!
```

---

## Summary

When working on FrostWire:

1. **Keep it simple** - Brutal simplicity wins every time
2. **Respect threading** - Protect UI threads from blocking work
3. **Reuse utilities** - FrostWire has solutions for most problems
4. **Think about privacy/security** - Always consider P2P implications
5. **Write tests** - For reproducible bugs
6. **Follow DRY** - Extract duplicated code immediately
7. **Use composition** - Avoid inheritance
8. **Don't commit without permission** - Always ask first
9. **Prefix commits** - Use `[common]`, `[desktop]`, or `[android]` prefixes
10. **Keep code readable** - Code is read far more than written

The codebase has mature, battle-tested patterns. Study them, follow them, and the code will remain beautiful and maintainable for the next 20 years.
