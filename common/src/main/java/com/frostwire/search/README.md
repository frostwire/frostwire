# FrostWire Search V2 Architecture

This directory contains the simplified, flat search architecture that replaces the deep inheritance hierarchies of the legacy search system.

## Overview

The V2 architecture eliminates 6-level inheritance chains and replaces them with:
- **SearchEngine**: A single concrete class that replaces entire hierarchies
- **SearchPattern**: Strategy interface for defining search logic
- **CrawlingStrategy**: Optional strategy for crawling results
- **FileSearchResult**: Simple flat result class with optional metadata composition

## Key Principles

1. **Composition Over Inheritance** - All behaviors are composed, not inherited
2. **Zero Class Hierarchies** - No abstract base classes, only concrete SearchEngine
3. **Single Page Only** - Pagination is removed; we always fetch page 1
4. **Optional Capabilities** - Torrent, streaming, and crawlable features are optional

## Architecture

### Core Components

#### SearchEngine
The main performer class that replaces all previous performers:
- `AbstractSearchPerformer` - Removed
- `WebSearchPerformer` - Removed
- `PagedWebSearchPerformer` - Removed (pagination removed)
- `CrawlPagedWebSearchPerformer` - Removed
- `CrawlRegexSearchPerformer` - Removed
- All torrent/streaming performers - Removed

```java
SearchEngine engine = new SearchEngine(
    token,
    keywords,
    encodedKeywords,
    new MySearchPattern(),      // How to search
    new MyCrawlingStrategy(),   // Optional: how to crawl
    30000                       // timeout
);
engine.setListener(listener);
engine.perform();  // Fetch ONE page only
```

#### FileSearchResult
Single result class with optional metadata:

```java
FileSearchResult result = FileSearchResult.builder()
    .displayName("My File")
    .filename("my-file.pdf")
    .size(1024)
    .detailsUrl("https://example.com/file")
    .source("example")
    .creationTime(System.currentTimeMillis())
    .torrent(new TorrentMetadata(
        "magnet:?xt=urn:btih:...",  // url
        "abcd1234",                 // hash
        100,                        // seeds
        "https://example.com"       // referrer
    ))
    .build();
```

#### SearchPattern
Defines HOW to search and parse results:

```java
public class MySearchPattern implements SearchPattern {
    @Override
    public String getSearchUrl(String encodedKeywords) {
        return "https://example.com/search?q=" + encodedKeywords;
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        // Parse HTML/JSON and return results
        return results;
    }
}
```

#### CrawlingStrategy
Optional: defines HOW to crawl results for more details:

```java
public class MyCrawlingStrategy implements CrawlingStrategy {
    @Override
    public void crawlResults(List<FileSearchResult> results, SearchListener listener, long token) {
        // For each result, fetch more details
        // Update result.setCrawlableChildren(children);
        listener.onResults(token, results);
    }
}
```

## Migration Guide

### Before (Legacy)
```java
// Deep inheritance hierarchy
public class MyTorrentSearchPerformer extends CrawlPagedWebSearchPerformer<TorrentCrawlableSearchResult> {
    public MyTorrentSearchPerformer(long token, String keywords) {
        super("mydomain.com", token, keywords, 30000, 5, 100);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) { ... }

    @Override
    protected List<? extends SearchResult> searchPage(String page) { ... }

    @Override
    protected String getCrawlUrl(TorrentCrawlableSearchResult sr) { ... }

    @Override
    protected List<? extends SearchResult> crawlResult(TorrentCrawlableSearchResult sr, byte[] data) { ... }
}
```

### After (V2)
```java
// Flat, composed architecture
SearchEngine engine = new SearchEngine(
    token,
    keywords,
    UrlUtils.encode(keywords),
    new MyTorrentSearchPattern(),           // Just define search logic
    new TorrentCrawlingStrategy(),          // Optional: define crawling logic
    30000
);
```

## Result Capabilities

All capabilities are optional through composition:

```java
// Torrent capability
if (result.isTorrent()) {
    String url = result.getTorrentUrl().get();
    int seeds = result.getSeeds().get();
}

// Streaming capability
if (result.isStreamable()) {
    String streamUrl = result.getStreamUrl().get();
}

// Crawlable capability
if (result.isCrawlable()) {
    result.setCrawlableChildren(children);
}

// Access via Optional (null-safe)
result.getTorrentUrl().ifPresent(url -> {
    // use url
});
```

## Examples

See the `v2/idope/` directory for a complete migration example:
- `IdopeSearchPattern` - Replaces IdopeSearchPerformer

## Benefits

| Aspect | Before | After |
|--------|--------|-------|
| **Performer Depth** | 6 levels | 1 level |
| **Class Count** | ~30+ | ~10 |
| **Adding New Search** | Create new subclass chain | Implement SearchPattern |
| **Understanding** | Trace inheritance chain | Read pattern implementation |
| **Testing** | Mock complex hierarchy | Mock strategy interface |
| **Code Reuse** | Limited by inheritance | Flexible composition |

## Backward Compatibility

The v2 architecture maintains backward compatibility by:
1. Implementing the `SearchPerformer` interface
2. Using legacy `SearchListener` callbacks
3. Casting `FileSearchResult` to `SearchResult` interface as needed
4. Adapter classes like `LegacyTorrentCrawlableResult` for legacy APIs

## Creating Custom Search Patterns

```java
public class MyCustomSearchPattern extends RegexSearchPattern {
    @Override
    public String getSearchUrl(String keywords) {
        return "https://example.com/search?q=" + keywords;
    }

    @Override
    protected Pattern getPattern() {
        return Pattern.compile(
            "<a href=\"([^\"]+)\" title=\"([^\"]+)\">.*?<span>([0-9]+)</span>"
        );
    }

    @Override
    protected FileSearchResult fromMatch(SearchMatcher m) {
        return FileSearchResult.builder()
            .detailsUrl(m.group(1))
            .displayName(m.group(2))
            .size(Long.parseLong(m.group(3)))
            .source("example")
            .build();
    }
}
```

## Migration Checklist

When migrating a search performer to v2:

- [ ] Create `MySearchPattern` implementing `SearchPattern`
- [ ] Create `MyCrawlingStrategy` if needed, else pass `null`
- [ ] Use `SearchEngineFactory.createSearch()` or construct `SearchEngine` directly
- [ ] Update UI layer to use `FileSearchResult` instead of legacy result types
- [ ] Update desktop UI if needed (should be minimal)
- [ ] Update android UI if needed (should be minimal)
- [ ] Test single-page search functionality
- [ ] Remove legacy performer class
- [ ] Update any references in `SearchManager` factory

## Performance Considerations

1. **Caching**: `TorrentCrawlingStrategy` includes built-in caching (configurable size)
2. **Threading**: Handled by `SearchManager` - strategies are called from background thread
3. **Memory**: Flat composition uses less memory than deep inheritance chains
4. **HTTP**: Reuses existing `HttpClient` infrastructure
