package MailRu;

import android.os.Parcel;

import base.KeyKeeper;

public class MailRuKeyKeeper extends KeyKeeper {
    public static final String CLIENT_ID = "738872";
    private String sessionKey;
    private String refreshToken;

    public MailRuKeyKeeper() {
    }

    public MailRuKeyKeeper(String sessionKey, String refreshToken) {
        this.sessionKey = sessionKey;
        this.refreshToken = refreshToken;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(sessionKey);
        dest.writeString(refreshToken);
    }

    public static final Creator<MailRuKeyKeeper> CREATOR = new Creator<MailRuKeyKeeper>() {
        @Override
        public MailRuKeyKeeper createFromParcel(Parcel source) {
            return new MailRuKeyKeeper(source.readString(), source.readString());
        }

        @Override
        public MailRuKeyKeeper[] newArray(int size) {
            return new MailRuKeyKeeper[size];
        }
    };
}
