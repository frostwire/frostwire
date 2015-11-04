package com.apple.eawt;

import com.apple.eawt.AppEvent.AppReOpenedEvent;

public interface AppReOpenedListener extends AppEventListener {
    void appReOpened(AppReOpenedEvent var1);
}
