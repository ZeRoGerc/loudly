package MailRu;

import android.os.Parcel;

import base.Action;
import base.Authorizer;
import base.ListenerHolder;
import base.ResponseListener;
import util.Query;

public class MailRuAuthoriser extends Authorizer<MailRuWrap, MailRuKeyKeeper> {
    private static final String AUTHORIZE_URL = "https://connect.mail.ru/oauth/authorize";
    private static final String REDIRECT_URL = "http://connect.mail.ru/oauth/success.html";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String ERROR_TOKEN = "error";

    @Override
    protected MailRuKeyKeeper beginAuthorize() {
        return new MailRuKeyKeeper();
    }

    @Override
    public Action continueAuthorization(final String url, final MailRuKeyKeeper keys) {
        final ResponseListener listener = ListenerHolder.getListener();
        Query response = Query.fromURL(url);
        if (response == null) {
            return new Action() {
                @Override
                public void execute() {
                    listener.onFail("Failed to parse response: " + url);
                }
            };
        }
        if (response.containsParameter(ACCESS_TOKEN)) {
            String accessToken = response.getParameter(ACCESS_TOKEN);
            String refreshToken = response.getParameter("refresh_token");
            keys.setSessionKey(accessToken);
            keys.setRefreshToken(refreshToken);
            return new Action() {
                @Override
                public void execute() {
                    listener.onSuccess(new MailRuWrap(keys));
                }
            };
        } else {
            final String error = response.getParameter("error");
            return new Action() {
                @Override
                public void execute() {
                    listener.onFail(error);
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
