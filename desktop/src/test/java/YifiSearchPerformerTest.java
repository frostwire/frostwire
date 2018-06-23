import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.yify.YifySearchResult;
import com.frostwire.util.ThreadPool;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.OKHTTPClient;
import com.frostwire.search.yify.YifySearchPerformer;

import java.util.concurrent.LinkedBlockingQueue;

public class YifiSearchPerformerTest {
    public static void main(String[] args) throws Throwable {
        String TEST_SEARCH_TERM = "foo bar";
        HttpClient httpClient = new OKHTTPClient(new ThreadPool("testPool", 4, new LinkedBlockingQueue<>(), false));
        String fileStr = httpClient.get("https://www.yify-torrent.org/search/" + TEST_SEARCH_TERM);

        Pattern searchResultsDetailURLPattern = Pattern.compile(YifySearchPerformer.REGEX);
        Pattern detailPagePattern = Pattern.compile(YifySearchPerformer.HTML_REGEX);

        Matcher searchResultsMatcher = searchResultsDetailURLPattern.matcher(fileStr);

        int found = 0;
        while (searchResultsMatcher.find()) {
            found++;
            System.out.println("\nfound " + found);
            System.out.println("result_url: [" + searchResultsMatcher.group(1) + "]");

            String detailUrl = "https://www.yify-torrent.org/movie/" + searchResultsMatcher.group("itemId") + "/" + searchResultsMatcher.group("htmlFileName");

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
                System.out.println("magneturl: [" + sm.group("magneturl") + "]");
                System.out.println("torrenturl: [" + sm.group("torrenturl") + "]");
                System.out.println("displayname: [" + sm.group("displayname") + "]");
                System.out.println("displayname2: [" + sm.group("displayname2") + "]");
                System.out.println("displaynamefallback: [" + sm.group("displaynamefallback") + "]");
                System.out.println("infohash: [" + sm.group("infohash") + "]");
                System.out.println("filesize: [" + sm.group("filesize") + "]");
                System.out.println("creationtime: [" + sm.group("creationtime") + "]");
                YifySearchResult sr = new YifySearchResult(detailUrl, sm);
                System.out.println(sr);
            } else {
                System.out.println("Detail page search matcher failed, check HTML_REGEX");
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
