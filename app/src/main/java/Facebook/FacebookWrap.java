package Facebook;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;

import base.Networks;
import base.Post;
import base.Wrap;
import base.attachments.Image;
import ly.loud.loudly.Loudly;
import util.BackgroundAction;
import util.Parameter;
import util.Query;

public class FacebookWrap extends Wrap {
    private static final int NETWORK = Networks.FB;
    private static final String MAIN_SERVER = "https://graph.facebook.com/v2.5/";
    private static final String POST_SERVER = "https://graph.facebook.com/me/feed";
    private static final String ACCESS_TOKEN = "access_token";

    @Override
    public Query makePostQuery(Post post) {
        Query query = new Query(POST_SERVER);
        query.addParameter("message", post.getText());
        FacebookKeyKeeper keys = (FacebookKeyKeeper) Loudly.getContext().getKeyKeeper(NETWORK);
        query.addParameter("access_token", keys.getAccessToken());
        return query;
    }

    @Override
    public Parameter uploadImage(Image image, BackgroundAction publish) {
        return null;
    }

    @Override
    public void parsePostResponse(Post post, String response) {
        JSONObject parser;
        try {
            parser = new JSONObject(response);
            String id = parser.getString("id");
            post.setLink(NETWORK, id);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Query[] makeGetQueries(Post post) {
        String[] urls = {"/likes", "/comments", "/sharedposts"};
        for (int i = 0; i < urls.length; i++) {
            urls[i] = MAIN_SERVER + post.getLink(NETWORK) + urls[i];
        }
        Query[] queries = new Query[urls.length];
        FacebookKeyKeeper keyKeeper = (FacebookKeyKeeper) Loudly.getContext().getKeyKeeper(NETWORK);
        for (int i = 0; i < urls.length; i++) {
            queries[i] = new Query(urls[i]);
            queries[i].addParameter(ACCESS_TOKEN, keyKeeper.getAccessToken());
            queries[i].addParameter("field", "data");
        }
        return queries;
    }

    @Override
    public void parseGetResponse(Post post, String[] response) {
        JSONObject parser;
        try {
            parser = new JSONObject(response[0]);
            int likes = parser.getJSONArray("data").length();
            parser = new JSONObject(response[1]);
            int comments = parser.getJSONArray("data").length();
            parser = new JSONObject(response[2]);
            int shares = parser.getJSONArray("data").length();

            post.setInfo(NETWORK, new Post.Info(likes, shares, comments));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Query makeDeleteQuery(Post post) {
        return new Query(MAIN_SERVER + post.getLink(NETWORK));
    }

    @Override
    public void parseDeleteResponse(Post post, String response) {
        try {
            JSONObject parse = new JSONObject(response);
            if (parse.getString("success") == "true") {
                post.detachFromNetwork(NETWORK);
            }
        } catch (JSONException e) {
            // ToDo: tell about fails
        }
    }

    @Override
    public Query makeLoadPostsQuery(long since, long before) {
        return null;
    }

    @Override
    public long parsePostsLoadedResponse(LinkedList<Post> posts, long since, String response) {
        return 0;
    }
}
