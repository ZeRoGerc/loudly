package ly.loud.loudly.networks.vk.entities;

import android.support.annotation.Nullable;

/**
 * Response from VK api
 *
 * @author Danil Kolikov
 */
public class VKResponse<T> {
    @Nullable
    public T response;

    @Nullable
    public Error error;
}
