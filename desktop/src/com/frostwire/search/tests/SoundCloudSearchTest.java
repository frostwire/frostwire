package com.frostwire.search.tests;

import com.frostwire.search.SearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.search.kat.KATSearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import org.apache.commons.io.FileUtils;
import rx.Observable;
import rx.functions.Action1;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Created by gubatron on 10/6/15.
 */
public class SoundCloudSearchTest {

    public static void main(String[] args) throws Throwable {
        SoundcloudSearchPerformer performer = new SoundcloudSearchPerformer("api.sndcdn.com",1,"remix", 5000);

        final CountDownLatch latch = new CountDownLatch(1);

        Action1 onNextAction = new Action1<List<? extends SearchResult>>() {
            @Override
            public void call(List<? extends SearchResult> searchResults) {
                if (searchResults instanceof List) {
                    try {
                        if (testOnSearchResults((List<SoundcloudSearchResult>) searchResults)) {
                            System.out.println("Test passed.");
                        } else {
                            System.out.println("Test failed.");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    latch.countDown();
                }
            }
        };

        final Observable<List<? extends SearchResult>> observable = performer.observable();
        observable.forEach(onNextAction);
        performer.perform();
        System.out.println("performer.perform()\nWaiting...");
        latch.await();
    }

    private static boolean testOnSearchResults(List<SoundcloudSearchResult> results) throws IOException {
        for (SoundcloudSearchResult result : results) {
            try {
                System.out.println(result.getDetailsUrl());
                System.out.println(result.getDisplayName());
                System.out.println(result.getFilename());
                System.out.println(result.getSource());
                System.out.println();
                System.out.println("============================\n");
            } catch (Throwable t) {
                return false;
            }
        }

        return true;
    }

//    private static HttpClient getHttpClient() {
//        return HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
//    }
}
