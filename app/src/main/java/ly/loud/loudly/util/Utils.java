package ly.loud.loudly.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import ly.loud.loudly.R;
import ly.loud.loudly.application.Loudly;
import ly.loud.loudly.base.entities.Person;
import ly.loud.loudly.base.multiple.LoudlyPost;
import ly.loud.loudly.base.plain.PlainPost;
import ly.loud.loudly.base.single.SinglePost;
import ly.loud.loudly.networks.Networks;

public class Utils {
    private static final String TAG = "UTIL_TAG";

    public static String getDateFormatted(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date * 1000);
        SimpleDateFormat formatter = new SimpleDateFormat("h 'hours', EEEE, d.MM", Locale.US);

        return formatter.format(cal.getTime());
    }

    public static int getResourceByPost(@NonNull PlainPost post) {
        if (post instanceof SinglePost) {
            return getResourceByNetwork(((SinglePost) post).getNetwork());
        } else {
            return getResourceByNetwork(Networks.LOUDLY);
        }
    }

    public static int getResourceByNetwork(int network) {
        switch (network) {
            case Networks.LOUDLY:
                return R.mipmap.ic_launcher;
            case Networks.FB:
                return R.mipmap.ic_facebook_round;
            case Networks.TWITTER:
                return R.mipmap.ic_twitter_round;
            case Networks.INSTAGRAM:
                return R.mipmap.ic_instagram_round;
            case Networks.VK:
                return R.mipmap.ic_vk_round;
            case Networks.OK:
                return R.mipmap.ic_ok_round;
            case Networks.MAILRU:
                return R.mipmap.ic_mail_ru_round;
            default:
                return R.mipmap.ic_launcher;
        }
    }

    public static int getResourceWhiteByNetwork(int network) {
        int resource;
        switch (network) {
            case Networks.LOUDLY:
                return R.drawable.ic_loudly_white;
            case Networks.FB:
                resource = R.drawable.ic_facebook_white;
                break;
            case Networks.TWITTER:
                resource = R.drawable.ic_twitter_white;
                break;
            case Networks.INSTAGRAM:
                resource = R.drawable.ic_instagram_white;
                break;
            case Networks.VK:
                resource = R.drawable.ic_vk_white;
                break;
            case Networks.OK:
                resource = R.drawable.ic_ok_white;
                break;
            case Networks.MAILRU:
                resource = R.drawable.ic_myworld_white;
                break;
            default:
                resource = R.drawable.ic_loudly_white;
        }
        return resource;
    }

    public static Bitmap toGrayscale(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    public static void loadAvatar(@NonNull Person person, @NonNull ImageView icon) {
        int iconSize = icon.getResources().getDimensionPixelSize(R.dimen.standard_icon_size_48);

        if (person.getPhotoUrl() != null) {
            Glide.with(icon.getContext())
                    .load(person.getPhotoUrl())
                    .asBitmap()
                    .override(iconSize, iconSize)
                    .fitCenter()
                    .into(new BitmapImageViewTarget(icon) {
                        @Override
                        protected void setResource(Bitmap resource) {
                            RoundedBitmapDrawable circularBitmapDrawable =
                                    RoundedBitmapDrawableFactory.create(icon.getResources(), resource);
                            circularBitmapDrawable.setCircular(true);
                            icon.setImageDrawable(circularBitmapDrawable);
                        }
                    });
        } else {
            Glide.with(icon.getContext())
                    .load(R.mipmap.ic_launcher)
                    .override(iconSize, iconSize)
                    .fitCenter()
                    .into(icon);
        }
    }

    public static void loadName(@NonNull Person person, @NonNull TextView name) {
        String text = person.getFirstName() + " " + person.getLastName();
        name.setText(text);
    }

    @NonNull
    public static ArrayList<SinglePost> getInstances(@NonNull PlainPost post) {
        if (post instanceof SinglePost) {
            return ListUtils.asArrayList(((SinglePost) post));
        } else if (post instanceof LoudlyPost) {
            return ((LoudlyPost) post).getNetworkInstances();
        } else {
            return ListUtils.emptyArrayList();
        }
    }

    @StringRes
    public static int getNetworkTitleResourceByPost(@NonNull PlainPost post) {
        if (post instanceof SinglePost) {
            return Networks.nameResourceOfNetwork(((SinglePost) post).getNetwork());
        } else {
            return Networks.nameResourceOfNetwork(Networks.LOUDLY);
        }
    }

    @NonNull
    public static Loudly getApplicationContext(@NonNull Context context) {
        return (Loudly) context.getApplicationContext();
    }

    public static void launchCustomTabs(@NonNull String url, @NonNull Activity context) {
        new CustomTabsIntent.Builder()
                .enableUrlBarHiding()
                .setToolbarColor(ContextCompat.getColor(context, R.color.primary))
                .build()
                .launchUrl(context, Uri.parse(url));
    }
}
