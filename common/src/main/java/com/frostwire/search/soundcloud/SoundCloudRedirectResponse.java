package com.frostwire.search.soundcloud;

/**
 * Sometimes soundcloud now returns something like this
 * {"status":"302 - Found","location":"https://api.soundcloud.com/tracks/1222222.json?client_id=b45b1aa10f1ac2941910a7f0d10f8e28"}
 * @author gubatron
 *
 */
public class SoundCloudRedirectResponse {
    public String status;
    public String location;
}
