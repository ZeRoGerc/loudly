package base.attachments;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Данил on 12/25/2015.
 */
public interface LocalFile {
    Uri getUri();
    String getMIMEType();
    InputStream getContent() throws IOException;
    long getFileSize() throws IOException;
}
