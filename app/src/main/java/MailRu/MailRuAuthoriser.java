package MailRu;

import android.content.Context;
import android.os.Parcel;

import base.KeyKeeper;
import ly.loud.loudly.Loudly;
import util.UIAction;
import base.Authorizer;
import base.Networks;
import util.ResultListener;
import util.Query;

public class MailRuAuthoriser extends Authorizer {
    private static final String AUTHORIZE_URL = "https://connect.mail.ru/oauth/authorize";
    private static final String REDIRECT_URL = "http://connect.mail.ru/oauth/success.html";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String ERROR_TOKEN = "error";

    @Override
    public int network() {
        return Networks.MAILRU;
    }

    @Override
    protected MailRuKeyKeeper beginAuthorize() {
        return new MailRuKeyKeeper();
    }

    @Override
    public UIAction continueAuthorization(final String url, KeyKeeper inKeys) {
        final MailRuKeyKeeper keys = (MailRuKeyKeeper) inKeys;
        final ResultListener listener = Loudly.getContext().getListener();
        Query response = Query.fromURL(url);
        if (response == null) {
            return new UIAction() {
                @Override
                public void execute(Context context, Object... params) {
                    listener.onFail(context, "Failed to parse response: " + url);
                }
            };
        }
        if (response.containsParameter(ACCESS_TOKEN)) {
            String accessToken = response.getParameter(ACCESS_TOKEN);
            String refreshToken = response.getParameter("refresh_token");
            keys.setSessionKey(accessToken);
            keys.setRefreshToken(refreshToken);
            Loudly.getContext().setKeyKeeper(network(), keys);
            return new UIAction() {
                @Override
                public void execute(Context context, Object... params) {
                    listener.onSuccess(context, keys);
                }
            };
        } else {
            final String error = response.getParameter(ERROR_TOKEN);
            return new UIAction() {
                @Override
                public void execute(Context context, Object... params) {
                    listener.onFail(context, error);
                }
            };
        }
    }

    @Override
    protected Query getAuthQuery() {
        Query query = new Query(AUTHORIZE_URL);
        query.addParameter("client_id", MailRuKeyKeeper.CLIENT_ID);
        query.addParameter("response_type", "token");
        query.addParameter("scope", "stream");
        query.addParameter("redirect_uri", REDIRECT_URL);
        return query;
    }

    @Override
    public boolean isResponse(String url) {
        return url.startsWith(REDIRECT_URL);
    }
    public static final Creator<MailRuAuthoriser> CREATOR = new Creator<MailRuAuthoriser>() {
        @Override
        public MailRuAuthoriser createFromParcel(Parcel source) {
            return new MailRuAuthoriser();
        }

        @Override
        public MailRuAuthoriser[] newArray(int size) {
            return new MailRuAuthoriser[size];
        }
    };
}
