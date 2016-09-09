package ly.loud.loudly.networks.instagram;

import android.support.annotation.NonNull;

import java.util.List;

import ly.loud.loudly.networks.facebook.entities.Post;
import ly.loud.loudly.networks.instagram.entities.Data;
import ly.loud.loudly.networks.instagram.entities.InstagramPost;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface InstagramClient {
    String CLIENT_ID = "25767d36bc624fe58215881ac0318ab3";

    @GET("users/self/media/recent?count=10")
    @NonNull
    Call<Data<List<InstagramPost>>> loadPosts(@NonNull @Query("max_id") String maxId,
                                              @NonNull @Query("access_token") String accessToken);

    @GET
    @NonNull
    Call<Data<List<InstagramPost>>> continueLoadPostsWithPagination(@NonNull @Url String url);
}
