package ly.loud.loudly;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import java.util.Iterator;

import base.Authorizer;
import base.KeyKeeper;
import base.Networks;
import base.says.Info;
import base.says.LoudlyPost;
import base.says.Post;
import util.AttachableReceiver;
import util.Broadcasts;
import util.UIAction;
import util.database.DatabaseActions;
import util.database.DatabaseException;

public class SettingsActivity extends AppCompatActivity {
    private static SettingsActivity self;
    private static AttachableReceiver authReceiver = null;
    static int aliveCopy = 0;

    private IconsHolder iconsHolder;

    private AuthFragment webViewFragment;
    private View webViewFragmentView;

    public static String webViewURL;
    public static Authorizer webViewAuthorizer;
    public static KeyKeeper webViewKeyKeeper;

    public static void executeOnUI(final UIAction action) {
        if (self != null) {
            self.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    action.execute(self);
                }
            });
        }
    }

    public void setIconsClick() {
        UIAction grayItemClick = new UIAction() {
            @Override
            public void execute(Context context, Object... params) {
                int network = ((int) params[0]);
                //TODO remove when all networks will have been implemented
                if (network == Networks.VK || network == Networks.FB) {
                    startReceiver();
                    Authorizer authorizer = Networks.makeAuthorizer(network);
                    authorizer.createAsyncTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        };
        iconsHolder.setGrayItemClick(grayItemClick);


        UIAction colorItemClick = new UIAction() {
            @Override
            public void execute(Context context, Object... params) {
                LogoutClick(((int) params[0]));
                iconsHolder.setInvisible(((int) params[0]));
            }
        };
        iconsHolder.setColorItemsClick(colorItemClick);
    }

    private void initFragment() {
        FragmentManager manager = getFragmentManager();
        webViewFragment = ((AuthFragment) manager.findFragmentById(R.id.setting_web_view));

        webViewFragmentView = findViewById(R.id.setting_web_view);
        webViewFragmentView.getBackground().setAlpha(100);
        FragmentTransaction ft = manager.beginTransaction();
        ft.hide(webViewFragment);
        ft.commit();
    }




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = this;
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = (Toolbar) findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);

        iconsHolder = (IconsHolder) findViewById(R.id.settings_icons_holder);
        iconsHolder.prepareView(IconsHolder.SHOW_ALL);
        setIconsClick();

        initFragment();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                int count = getFragmentManager().getBackStackEntryCount();
                if (count > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        getSupportActionBar().hide();
                    }
                }
                if (count == 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        getSupportActionBar().show();
                    }
                }
            }
        });

        if (authReceiver != null) {
            authReceiver.attach(this);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        aliveCopy++;
    }

    @Override
    protected void onResume() {
        super.onResume();
        self = this;
    }


    private static class AuthReceiver extends AttachableReceiver {
        private IconsHolder iconsHolder;

        public AuthReceiver(SettingsActivity context, IconsHolder iconHolder) {
            super(context, Broadcasts.AUTHORIZATION);
            this.iconsHolder = iconHolder;
        }

        @Override
        public void onMessageReceive(Context context, Intent message) {
            int status = message.getIntExtra(Broadcasts.STATUS_FIELD, 0);
            SettingsActivity activity = (SettingsActivity) context;
            switch (status) {
                case Broadcasts.FINISHED:
                    Snackbar.make(activity.findViewById(R.id.settings_parent_layout),
                            "Successful login", Snackbar.LENGTH_SHORT)
                            .show();
                    int network = message.getIntExtra(Broadcasts.NETWORK_FIELD, -1);
                    iconsHolder.setVisible(network);
                    activity.finishWebView();

                    break;
                case Broadcasts.ERROR:
                    int kind = message.getIntExtra(Broadcasts.ERROR_KIND, -1);
                    String error = "";
                    switch (kind) {
                        case Broadcasts.NETWORK_ERROR:
                            error = "Problems with network";
                            break;
                        case Broadcasts.DATABASE_ERROR:
                            error = "Can't save your login";
                    }
                    if (!error.isEmpty()) {
                        Snackbar.make(activity.findViewById(R.id.settings_parent_layout),
                                error, Snackbar.LENGTH_SHORT)
                                .show();
                    }
                    activity.finishWebView();
                    Log.e("LOGIN", message.getStringExtra(Broadcasts.ERROR_FIELD));
                    break;
            }
            stop();
            authReceiver = null;
        }
    }

    // That's here because of 3 different click listeners
    private void startReceiver() {
        authReceiver = new AuthReceiver(this, iconsHolder);
    }

    // ToDo: make buttons onclickable during authorization

    public void startWebView() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.show(webViewFragment);
        ft.addToBackStack(null);
        ft.commit();
    }

    public void finishWebView() {
        webViewFragment.clearWebView();
        getFragmentManager().popBackStack();
    }

    @Override
    public void onBackPressed() {
        int count = getFragmentManager().getBackStackEntryCount();

        if (count == 0) {
            super.onBackPressed();
            return;
        }
        getFragmentManager().popBackStack();
    }

    @Override
    protected void onPause() {
        super.onPause();
        self = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        aliveCopy--;
        if (isFinishing()) {
            if (authReceiver != null) {
                authReceiver.stop();
            }
            authReceiver = null;
        }
    }

    public void LogoutClick(int network) {
        Loudly.getContext().stopGetInfoService();
        if (MainActivity.loadPosts != null) {
            MainActivity.loadPosts.stop();
        }
        Networks.makeWrap(network).resetState();

        new AsyncTask<Integer, Void, Integer>() {
            @Override
            protected Integer doInBackground(Integer... params) {
                int network = params[0];
                if (Loudly.getContext().getKeyKeeper(network) != null) {
                    try {
                        DatabaseActions.deleteKey(network);
                    } catch (DatabaseException e) {
                        e.printStackTrace();
                    }
                }
                return params[0];
            }

            @Override
            protected void onPostExecute(Integer network) {
                Loudly.getContext().setKeyKeeper(network, null);
                // Clean posts from this network
                Iterator<Post> iterator = MainActivity.posts.listIterator();
                int ind = 0;
                while (iterator.hasNext()) {
                    Post post = iterator.next();
                    if (post.existsIn(network)) {
                        if (post instanceof LoudlyPost) {
                            Info oldInfo = post.getInfo();
                            ((LoudlyPost) post).setInfo(network, new Info());
                            boolean visible = false;
                            for (int j = 0; j < Networks.NETWORK_COUNT; j++) {
                                if (post.existsIn(j)) {
                                    visible = true;
                                    break;
                                }
                            }
                            if (!visible) {
                                iterator.remove();
                                final int fixed = ind;
                                MainActivity.executeOnUI(new UIAction() {
                                    @Override
                                    public void execute(Context context, Object... params) {
                                        ((MainActivity) context).recyclerViewAdapter.notifyDeletedAtPosition(fixed);
                                    }
                                });
                            } else {
                                if (!oldInfo.equals(post.getInfo())) {
                                    final int fixed = ind;
                                    MainActivity.executeOnUI(new UIAction() {
                                        @Override
                                        public void execute(Context context, Object... params) {
                                            ((MainActivity) context).recyclerViewAdapter.notifyItemChanged(fixed);
                                        }
                                    });
                                }
                            }
                        } else {
                            iterator.remove();
                            final int fixed = ind;
                            MainActivity.executeOnUI(new UIAction() {
                                @Override
                                public void execute(Context context, Object... params) {
                                    ((MainActivity) context).recyclerViewAdapter.notifyDeletedAtPosition(fixed);
                                }
                            });
                        }
                    }
                }
                MainActivity.loadedNetworks[network] = false;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, network);
    }
}
