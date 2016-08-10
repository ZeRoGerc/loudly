package ly.loud.loudly.legacy_base.attachments;

import android.os.Parcelable;

import ly.loud.loudly.legacy_base.SingleNetwork;

public interface Attachment extends SingleNetwork, Parcelable {
    int IMAGE = 0;

    int getType();
    String getExtra();

//    static Attachment makeAttachment(int type, String extra, String[] links) {
//        switch (type) {
//            case IMAGE:
//                return new Image(extra, links);
//            default:
//                return null;
//        }
//    }
}