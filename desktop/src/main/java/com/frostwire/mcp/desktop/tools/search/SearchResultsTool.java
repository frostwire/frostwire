package com.frostwire.mcp.desktop.tools.search;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.SearchAdapter;
import com.frostwire.mcp.desktop.state.SearchSession;
import com.frostwire.mcp.desktop.state.SearchSessionManager;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SearchResultsTool implements MCPTool {
    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_OFFSET = 0;

    @Override
    public String name() {
        return "frostwire_search_results";
    }

    @Override
    public String description() {
        return "Retrieve search results for a given search token with optional filtering, sorting, and pagination.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject tokenProp = new JsonObject();
        tokenProp.addProperty("type", "string");
        tokenProp.addProperty("description", "The search token returned by frostwire_search");
        properties.add("token", tokenProp);

        JsonObject offsetProp = new JsonObject();
        offsetProp.addProperty("type", "integer");
        offsetProp.addProperty("description", "Offset for pagination (default: 0)");
        properties.add("offset", offsetProp);

        JsonObject limitProp = new JsonObject();
        limitProp.addProperty("type", "integer");
        limitProp.addProperty("description", "Maximum number of results to return (default: 50)");
        properties.add("limit", limitProp);

        JsonObject sortByProp = new JsonObject();
        sortByProp.addProperty("type", "string");
        sortByProp.addProperty("description", "Sort field: 'seeds', 'size', 'creationTime', or 'displayName' (default: no sorting)");
        properties.add("sortBy", sortByProp);

        JsonObject filterProp = new JsonObject();
        filterProp.addProperty("type", "object");
        filterProp.addProperty("description", "Optional filters: minSize, maxSize (bytes), minSeeds, source, fileType ('torrent' or 'cloud')");
        properties.add("filter", filterProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("token");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String tokenStr = arguments.get("token").getAsString();
        long token = Long.parseLong(tokenStr);

        SearchSession session = SearchSessionManager.instance().getSession(token);
        JsonObject result = new JsonObject();

        if (session == null) {
            result.addProperty("error", "No search session found for token " + tokenStr);
            return result;
        }

        int offset = arguments.has("offset") ? arguments.get("offset").getAsInt() : DEFAULT_OFFSET;
        int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : DEFAULT_LIMIT;
        String sortBy = arguments.has("sortBy") ? arguments.get("sortBy").getAsString() : null;

        List<SearchResult> allResults = new ArrayList<>(session.getResults());

        if (arguments.has("filter") && arguments.get("filter").isJsonObject()) {
            JsonObject filter = arguments.getAsJsonObject("filter");
            allResults = applyFilters(allResults, filter);
        }

        if (sortBy != null) {
            allResults = applySorting(allResults, sortBy);
        }

        int totalResults = allResults.size();
        int from = Math.min(offset, totalResults);
        int to = Math.min(offset + limit, totalResults);
        List<SearchResult> page = allResults.subList(from, to);

        JsonArray resultsArray = new JsonArray();
        for (SearchResult sr : page) {
            resultsArray.add(SearchAdapter.toMCPJson(sr));
        }

        result.add("results", resultsArray);
        result.addProperty("totalResults", totalResults);
        result.addProperty("offset", from);
        result.addProperty("limit", limit);
        result.addProperty("hasMore", to < totalResults);

        return result;
    }

    private List<SearchResult> applyFilters(List<SearchResult> results, JsonObject filter) {
        Long minSize = filter.has("minSize") ? filter.get("minSize").getAsLong() : null;
        Long maxSize = filter.has("maxSize") ? filter.get("maxSize").getAsLong() : null;
        Integer minSeeds = filter.has("minSeeds") ? filter.get("minSeeds").getAsInt() : null;
        String source = filter.has("source") ? filter.get("source").getAsString() : null;
        String fileType = filter.has("fileType") ? filter.get("fileType").getAsString() : null;

        return results.stream().filter(sr -> {
            if (fileType != null) {
                if ("torrent".equals(fileType) && !(sr instanceof TorrentSearchResult)) {
                    return false;
                }
                if ("cloud".equals(fileType) && !(sr instanceof HttpSearchResult)) {
                    return false;
                }
            }

            if (sr instanceof TorrentSearchResult) {
                TorrentSearchResult tsr = (TorrentSearchResult) sr;
                if (minSize != null && tsr.getSize() < minSize) return false;
                if (maxSize != null && tsr.getSize() > maxSize) return false;
                if (minSeeds != null && tsr.getSeeds() < minSeeds) return false;
            } else if (sr instanceof HttpSearchResult) {
                HttpSearchResult hsr = (HttpSearchResult) sr;
                if (minSize != null && hsr.getSize() < minSize) return false;
                if (maxSize != null && hsr.getSize() > maxSize) return false;
            }

            if (source != null && !source.equalsIgnoreCase(sr.getSource())) {
                return false;
            }

            return true;
        }).collect(Collectors.toList());
    }

    private List<SearchResult> applySorting(List<SearchResult> results, String sortBy) {
        Comparator<SearchResult> comparator = null;
        switch (sortBy) {
            case "seeds":
                comparator = (a, b) -> {
                    int sa = (a instanceof TorrentSearchResult) ? ((TorrentSearchResult) a).getSeeds() : 0;
                    int sb = (b instanceof TorrentSearchResult) ? ((TorrentSearchResult) b).getSeeds() : 0;
                    return Integer.compare(sb, sa);
                };
                break;
            case "size":
                comparator = (a, b) -> {
                    long sa = (a instanceof TorrentSearchResult) ? ((TorrentSearchResult) a).getSize() :
                            (a instanceof HttpSearchResult) ? ((HttpSearchResult) a).getSize() : 0L;
                    long sbb = (b instanceof TorrentSearchResult) ? ((TorrentSearchResult) b).getSize() :
                            (b instanceof HttpSearchResult) ? ((HttpSearchResult) b).getSize() : 0L;
                    return Long.compare(sbb, sa);
                };
                break;
            case "creationTime":
                comparator = (a, b) -> Long.compare(b.getCreationTime(), a.getCreationTime());
                break;
            case "displayName":
                comparator = Comparator.comparing(SearchResult::getDisplayName, String.CASE_INSENSITIVE_ORDER);
                break;
            default:
                break;
        }

        if (comparator != null) {
            results.sort(comparator);
        }
        return results;
    }
}
