import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.yify.YifySearchResult;
import com.frostwire.util.ThreadPool;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.OKHTTPClient;
import com.frostwire.search.yify.YifySearchPerformer;

import java.util.concurrent.LinkedBlockingQueue;

public class YifySearchPerformerTest {
    public static void main(String[] args) throws Throwable {
        String TEST_SEARCH_TERM = "one";
        HttpClient httpClient = new OKHTTPClient(new ThreadPool("testPool", 4, new LinkedBlockingQueue<>(), false));
        String fileStr = httpClient.get("https://www.yify-torrent.org/search/" + TEST_SEARCH_TERM);

        Pattern searchResultsDetailURLPattern = Pattern.compile(YifySearchPerformer.SEARCH_RESULTS_REGEX);
        Pattern detailPagePattern = Pattern.compile(YifySearchPerformer.TORRENT_DETAILS_PAGE_REGEX);

        Matcher searchResultsMatcher = searchResultsDetailURLPattern.matcher(fileStr);

        int found = 0;
        while (searchResultsMatcher.find()) {
            found++;
            System.out.println("\nfound " + found);
            System.out.println("result_url: [" + searchResultsMatcher.group(1) + "]");

            String detailUrl = "https://www.yify-torrent.org/torrent/" + searchResultsMatcher.group("itemId") + "/" + searchResultsMatcher.group("htmlFileName");

            System.out.println("Fetching details from " + detailUrl + " ....");
            long start = System.currentTimeMillis();
            String detailPage = httpClient.get(detailUrl, 5000);
            if (detailPage == null) {
                System.out.println("Error fetching from " + detailUrl);
                continue;
            }
            long downloadTime = System.currentTimeMillis() - start;
            System.out.println("Downloaded " + detailPage.length() + " bytes in " + downloadTime + "ms");
            SearchMatcher sm = new SearchMatcher(detailPagePattern.matcher(detailPage));

            if (sm.find()) {
                System.out.println("displayname: [" + sm.group("displayName") + "]");
                System.out.println("infohash: [" + sm.group("infohash") + "]");
                System.out.println("size: [" + sm.group("size") + "]");
                System.out.println("creationDate: [" + sm.group("creationDate") + "]");
                System.out.println("seeds: [" + sm.group("seeds") + "]");
                System.out.println("magnet: [" + sm.group("magnet") + "]");

                YifySearchResult sr = new YifySearchResult(detailUrl, sm);
                System.out.println(sr);
            } else {
                System.out.println("Detail page search matcher failed, check TORRENT_DETAILS_PAGE_REGEX");
            }
            System.out.println("===");
            System.out.println("Sleeping 5 seconds...");
            Thread.sleep(5000);
        }
        System.out.println("-done-");

        if (found == 0) {
            System.out.println(fileStr);
        }
    }
}
