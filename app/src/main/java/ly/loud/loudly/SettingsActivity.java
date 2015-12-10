package ly.loud.loudly;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import base.Authorizer;
import base.KeyKeeper;
import base.Tasks;
import util.AttachableReceiver;
import util.Broadcasts;
import util.UIAction;
import util.database.DatabaseActions;
import util.database.DatabaseException;

public class SettingsActivity extends AppCompatActivity {
    private static AttachableReceiver authReceiver = null;
    private IconsHolder iconsHolder;
    private Fragment webViewFragment;
    private View webViewFragmentView;
    public static String webViewURL;
    public static Authorizer webViewAuthorizer;
    public static KeyKeeper webViewKeyKeeper;

    public void setIconsClick() {
        UIAction action1 = new UIAction() {
            @Override
            public void execute(Context context, Object... params) {
                int network = ((int) params[0]);
                startReceiver();
                Authorizer authorizer = Authorizer.getAuthorizer(network);
                authorizer.createAsyncTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            }
        };
        iconsHolder.setGrayItemClick(action1);


        UIAction action2 = new UIAction() {
            @Override
            public void execute(Context context, Object... params) {
                LogoutClick(((int) params[0]));
            }
        };
        iconsHolder.setColorItemsClick(action2);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = (Toolbar)findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);

        iconsHolder = (IconsHolder)findViewById(R.id.settings_icons_holder);
        setIconsClick();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        FragmentManager manager = getFragmentManager();
        webViewFragment = manager.findFragmentById(R.id.setting_web_view);


        webViewFragmentView = findViewById(R.id.setting_web_view);
        webViewFragmentView.getBackground().setAlpha(100);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.hide(webViewFragment);
        ft.commit();

        if (authReceiver != null) {
            authReceiver.attach(this);
        }
    }

    private static class AuthReceiver extends AttachableReceiver {
        public AuthReceiver(Context context) {
            super(context, Broadcasts.AUTHORIZATION);
        }

        @Override
        public void onMessageReceive(Context context, Intent message) {
            String status = message.getStringExtra(Broadcasts.STATUS_FIELD);
            SettingsActivity activity = (SettingsActivity)context;
            Toast toast;
            switch (status) {
                case Broadcasts.FINISHED:
                    toast = Toast.makeText(context, "Success", Toast.LENGTH_SHORT);
                    toast.show();
                    int network = message.getIntExtra(Broadcasts.NETWORK_FIELD, -1);
                    MainActivity.posts.clear();
                    break;
                case Broadcasts.ERROR:
                    String error = message.getStringExtra(Broadcasts.ERROR_FIELD);
                    toast = Toast.makeText(context, "Fail: " + error, Toast.LENGTH_SHORT);
                    toast.show();
                    break;
            }
            stop();
            authReceiver = null;
        }
    }

    // That's here because of 3 different click listeners
    private void startReceiver() {
        authReceiver = new AuthReceiver(this);
    }

    // ToDo: make buttons onclickable during authorization

    public void startWebView() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.show(webViewFragment);
        ft.commit();
    }
//
//    public void VKButtonClick() {
//        startReceiver();
//        Authorizer authorizer = new VKAuthorizer();
//        authorizer.createAsyncTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//    }
//
//    public void FBButtonClick() {
//        startReceiver();
//        Authorizer authorizer = new FacebookAuthorizer();
//        authorizer.createAsyncTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//    }
//
//    public void MailRuButtonClick() {
//        startReceiver();
//        Authorizer authorizer = new MailRuAuthoriser();
//        authorizer.createAsyncTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//    }

    public void LogoutClick(final int network) {
        AsyncTask<Object, Void, Object> task = new AsyncTask<Object, Void, Object>() {
            @Override
            protected Object doInBackground(Object... params) {
                if (Loudly.getContext().getKeyKeeper(network) != null) {
                    try {
                        DatabaseActions.deleteKey(network);
                        Loudly.getContext().setKeyKeeper(network, null);
                    } catch (DatabaseException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Object o) {
                Toast toast = Toast.makeText(Loudly.getContext(), "Deleted", Toast.LENGTH_SHORT);
                toast.show();
            }
        };
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }


    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    public void onBackPressed() {
        if (webViewFragmentView.isShown()) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.hide(webViewFragment);
            ft.commit();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Save keys for further use
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isFinishing()) {
            if (authReceiver != null) {
                authReceiver.stop();
            }
            authReceiver = null;
            Tasks.SaveKeysTask task = new Tasks.SaveKeysTask();
            task.execute();
        }
    }
}
