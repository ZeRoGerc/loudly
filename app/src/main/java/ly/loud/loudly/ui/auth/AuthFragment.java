package ly.loud.loudly.ui.auth;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import ly.loud.loudly.R;
import ly.loud.loudly.application.models.AuthModel;
import ly.loud.loudly.base.exceptions.NoNetworkConnectionException;
import ly.loud.loudly.networks.Networks.Network;
import ly.loud.loudly.util.Utils;
import rx.Observable;
import rx.Single;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static ly.loud.loudly.util.RxUtils.changeSubscription;
import static rx.android.schedulers.AndroidSchedulers.mainThread;
import static rx.schedulers.Schedulers.io;

public class AuthFragment extends DialogFragment {
    private static final String NETWORK_FIELD = "network";

    @SuppressWarnings("NullableProblems") // Butterkniife
    @BindView(R.id.progressLayout)
    @NonNull
    View circle;

    @SuppressWarnings("NullableProblems") // Butterkniife
    @BindView(R.id.webView)
    @NonNull
    WebView webView;

    @Inject
    @SuppressWarnings("NullableProblems") // onCreate
    @NonNull
    AuthModel authModel;

    @Network
    int network;

    @Nullable
    private Unbinder unbinder;

    /**
     * Indicates whether this fragment created view for the first time.
     * Should be accessed only from main thread.
     */
    private boolean firstRun = true;

    public static AuthFragment newInstance(@Network int network) {
        Bundle args = new Bundle();
        args.putInt(NETWORK_FIELD, network);

        AuthFragment fragment = new AuthFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.getApplicationContext(getContext()).getAppComponent().inject(this);
        setStyle(STYLE_NO_TITLE, getTheme());
        //noinspection ResourceType Only network is saved into this field
        network = getArguments().getInt(NETWORK_FIELD);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View rootView = inflater.inflate(R.layout.auth_fragment, null);
        unbinder = ButterKnife.bind(this, rootView);
        builder.setView(rootView);
        return builder.create();
    }

    @Override
    public void onDestroyView() {
        if (unbinder != null) {
            clearWebView();
            unbinder.unbind();
        }
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@Nullable Bundle outState) {
        super.onSaveInstanceState(outState);
        if (outState != null) {
            outState.putInt(NETWORK_FIELD, network);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }
        if (firstRun) {
            circle.setVisibility(VISIBLE);
            Single<String> authUrl = authModel.getAuthUrl(network).subscribeOn(io());
            Observable<String> urls = changeSubscription(authUrl, mainThread())
                    .flatMapObservable(this::createUrlsObservable);
            authModel.finishAuthorization(changeSubscription(urls, io()), network)
                    .observeOn(mainThread())
                    .subscribe(
                            () -> {
                                clearWebView();
                                dismiss();
                            },
                            error -> {
                                // ToDo: Handle NetworkError
                                clearWebView();
                                dismiss();
                            }
                    );
            firstRun = false;
        }
    }

    @UiThread
    @CheckResult
    @NonNull
    private Observable<String> createUrlsObservable(@Nullable String initialUrl) {
        return Observable.create(observer -> {
            webView.setWebViewClient(new WebViewClient() {
                @SuppressWarnings("deprecation") // deprecated in 23 api
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    observer.onError(new NoNetworkConnectionException());
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    super.onReceivedError(view, request, error);
                    observer.onError(new NoNetworkConnectionException());
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    observer.onNext(url);
                    return super.shouldOverrideUrlLoading(view, url);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    //noinspection ConstantConditions Circle is null after ButterKnife unbinds it
                    if (circle != null) {
                        circle.setVisibility(GONE);
                    }
                }
            });
            webView.loadUrl(initialUrl);
        });
    }

    public void clearWebView() {
        //noinspection ConstantConditions webView is null after ButterKnife unbinds it
        if (webView != null) {
            webView.loadUrl("about:blank");
        }
    }
}
