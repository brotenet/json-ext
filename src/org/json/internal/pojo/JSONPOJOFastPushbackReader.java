package org.json.internal.pojo;

import java.io.Closeable;
import java.io.IOException;

public interface JSONPOJOFastPushbackReader extends Closeable {

    int getCol();

    int getLine();

    void unread(int c) throws IOException;

    int read() throws IOException;

    String getLastSnippet();
}
