package ly.loud.loudly;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.LinkedList;

import base.KeyKeeper;
import base.Networks;
import base.Post;
import base.Tasks;
import base.Wrap;
import util.LongTask;
import util.ResultListener;
import util.UIAction;

/**
 * Core application of Loudly app
 * Stores run-time variables
 */
public class Loudly extends Application {
    public static final String LOADED_KEYS = "ly.loud.loudly.keys";
    public static final String LOADED_POSTS = "ly.loud.loudly.posts";
    public static final String AUTHORIZATION_FINISHED = "ly.loud.loudly.auth.finished";

    public static final String POST_UPLOAD_PROGRESS = "ly.loud.loudly.post.progress";
    public static final String POST_UPLOAD_FINISHED = "ly.loud.loudly.post.finished";

    public static final String POST_GET_INFO_PROGRESS = "ly.loud.loudly.post.info.progress";
    public static final String POST_GET_INFO_FINISHED = "ly.loud.loudly.post.info.finished";


    private static Loudly context;
    private KeyKeeper[] keyKeepers;
    private LongTask task;
    private UIAction action;
    private ResultListener listener;
    private LinkedList<Post> posts;
    private boolean postsLoaded = false;

    /**
     * @param network ID of the network
     * @return KeyKeeper or null
     */
    public KeyKeeper getKeyKeeper(int network) {
        return keyKeepers[network];
    }

    /**
     * @param network ID of the network
     * @param keyKeeper KeyKeeper, that should be stored
     */
    public void setKeyKeeper(int network, KeyKeeper keyKeeper) {
        keyKeepers[network] = keyKeeper;
    }

    /**
     * Get context of the Application.
     * As the application can't die until user kills it, it's possible to store the context here
     * @return link to the Loudly
     */
    public static Loudly getContext() {
        return context;
    }

    public LongTask getTask() {
        return task;
    }

    public void setTask(LongTask task) {
        this.task = task;
    }

    public UIAction getAction() {
        return action;
    }

    public void setAction(UIAction action) {
        this.action = action;
    }

    public ResultListener getListener() {
        return listener;
    }

    public void setListener(ResultListener listener) {
        this.listener = listener;
    }

    public void addPost(Post post) {
        posts.add(post);
    }

    public LinkedList<Post> getPosts() {
        return posts;
    }

    public boolean arePostsLoaded() {
        return postsLoaded;
    }

    public Wrap[] getWraps() {
        ArrayList<Wrap> list = new ArrayList<>();
        for (int i = 0; i < Networks.NETWORK_COUNT; i++) {
            if (keyKeepers[i] != null) {
                list.add(Wrap.makeWrap(i));
            }
        }
        return list.toArray(new Wrap[]{});
    }

    @Override
    public void onCreate() {
        super.onCreate();
        keyKeepers = new KeyKeeper[Networks.NETWORK_COUNT];
        context = this;
        posts = new LinkedList<>();

        Tasks.LoadKeysTask loadKeys = new Tasks.LoadKeysTask(context) {
            @Override
            public void ExecuteInUI(Context context, Integer integer) {
                Intent message = new Intent(LOADED_KEYS);
                LocalBroadcastManager.getInstance(context).sendBroadcast(message);
            }
        };
        loadKeys.execute();

        Tasks.LoadPostsTask loadPosts = new Tasks.LoadPostsTask(context) {
            @Override
            public void ExecuteInUI(Context context, Integer integer) {
                postsLoaded = true;
                Intent message = new Intent(LOADED_POSTS);
                LocalBroadcastManager.getInstance(context).sendBroadcast(message);
            }
        };
        loadPosts.execute();
    }
}
