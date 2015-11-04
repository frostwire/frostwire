package com.apple.eawt;

import java.io.File;
import java.net.URI;
import java.util.EventObject;
import java.util.List;

public abstract class AppEvent extends EventObject {
    AppEvent() {
        super(Application.getApplication());
    }

    public static class AboutEvent extends AppEvent {
        AboutEvent() {
        }
    }

    public static class QuitEvent extends AppEvent {
        QuitEvent() {
        }
    }

    public static class PreferencesEvent extends AppEvent {
        PreferencesEvent() {
        }
    }

    public static class AppReOpenedEvent extends AppEvent {
        AppReOpenedEvent() {
        }
    }

    public static class OpenFilesEvent extends AppEvent.FilesEvent {
        final String searchTerm;

        OpenFilesEvent(List<File> var1, String var2) {
            super(var1);
            this.searchTerm = var2;
        }

        public String getSearchTerm() {
            return this.searchTerm;
        }
    }

    public abstract static class FilesEvent extends AppEvent {
        final List<File> files;

        FilesEvent(List<File> var1) {
            this.files = var1;
        }

        public List<File> getFiles() {
            return this.files;
        }
    }

    public static class OpenURIEvent extends AppEvent {
        final URI uri;

        OpenURIEvent(URI var1) {
            this.uri = var1;
        }

        public URI getURI() {
            return this.uri;
        }
    }
}
