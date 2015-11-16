package VK;

import android.content.Context;
import android.os.Parcel;

import base.KeyKeeper;
import ly.loud.loudly.Loudly;
import util.UIAction;
import base.Authorizer;
import base.Networks;
import util.ResultListener;
import util.Query;

public class VKAuthorizer extends Authorizer {
    private static final String AUTHORIZE_URL = "https://oauth.vk.com/authorize";
    private static final String RESPONSE_URL = "https://oauth.vk.com/blank.html";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String ERROR_DESCRIPTION = "error";

    @Override
    public int network() {
        return Networks.VK;
    }

    @Override
    protected VKKeyKeeper beginAuthorize() {
        return new VKKeyKeeper();
    }

    @Override
    public UIAction continueAuthorization(final String url, KeyKeeper inKeys) {
        final VKKeyKeeper keys = (VKKeyKeeper) inKeys;
        final ResultListener listener = Loudly.getContext().getListener();
        Query response = Query.fromURL(url);

        if (response == null) {
            return new UIAction() {
                @Override
                public void execute(Context action, Object... params) {
                    listener.onFail(action, "Failed to parse response: " + url);
                }
            };
        }

        if (response.containsParameter(ACCESS_TOKEN)) {
            String accessToken = response.getParameter(ACCESS_TOKEN);
            String userID = response.getParameter("user_id");
            keys.setAccessToken(accessToken);
            keys.setUserId(userID);

            Loudly.getContext().setKeyKeeper(network(), keys);

            // Add to WrapHolder
            return new UIAction() {
                @Override
                public void execute(Context context, Object... params) {
                    listener.onSuccess(context, keys);
                }
            };
        } else {
            final String errorToken = response.getParameter(ERROR_DESCRIPTION);
            return new UIAction() {
                @Override
                public void execute(Context context, Object... params) {
                    listener.onFail(context, errorToken);
                }
            };
        }
    }

    @Override
    protected Query getAuthQuery() {
        Query query = new Query(AUTHORIZE_URL);
        query.addParameter("client_id", VKKeyKeeper.CLIENT_ID);
        query.addParameter("redirect_uri", RESPONSE_URL);
        query.addParameter("display_type", "mobile");
        query.addParameter("scope", "wall");
        query.addParameter("response_type", "token");
        return query;
    }

    @Override
    public boolean isResponse(String url) {
        return url.startsWith(RESPONSE_URL);
    }

    public static final Creator<VKAuthorizer> CREATOR = new Creator<VKAuthorizer>() {
        @Override
        public VKAuthorizer createFromParcel(Parcel source) {
            return new VKAuthorizer();
        }

        @Override
        public VKAuthorizer[] newArray(int size) {
            return new VKAuthorizer[size];
        }
    };
}
