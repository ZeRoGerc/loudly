package ly.loud.loudly.base.interfaces.attachments;

import android.support.annotation.Nullable;

import java.io.File;

/**
 * File that stored on local device
 *
 * @author Danil Kolikov
 */
public interface LocalFile {
    @Nullable
    File getFile();
}
