package com.apple.eawt;

import com.apple.eawt.QuitResponse;
import com.apple.eawt.AppEvent.QuitEvent;

public interface QuitHandler {
    void handleQuitRequestWith(QuitEvent var1, QuitResponse var2);
}
