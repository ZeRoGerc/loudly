package ly.loud.loudly.application.models;

import android.graphics.Point;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import ly.loud.loudly.application.Loudly;
import ly.loud.loudly.base.*;
import ly.loud.loudly.base.attachments.Image;
import ly.loud.loudly.base.says.Comment;
import ly.loud.loudly.base.says.Info;
import ly.loud.loudly.base.says.Post;
import ly.loud.loudly.networks.VK.VKClient;
import ly.loud.loudly.networks.VK.VKKeyKeeper;
import ly.loud.loudly.networks.VK.entities.*;
import ly.loud.loudly.util.TimeInterval;
import retrofit2.Call;
import retrofit2.Response;
import rx.Single;
import rx.functions.Func1;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ly.loud.loudly.application.models.GetterModel.*;

/**
 * Created by ZeRoGerc on 21/07/16.
 */
public class VKModel implements NetworkContract {
    private static final String TAG = "VK_MODEL";

    private int offset;
    @NonNull
    private Loudly loudlyApplication;

    @NonNull
    private KeysModel keysModel;

    @NonNull
    private VKClient client;

    @Inject
    public VKModel(
            @NonNull Loudly loudlyApplication,
            @NonNull KeysModel keysModel,
            @NonNull VKClient client
    ) {
        this.loudlyApplication = loudlyApplication;
        this.keysModel = keysModel;
        this.client = client;
        loadFromDB();
        offset = 0;
    }

    @Override
    public Single<Boolean> reset() {
        offset = 0;
        return Single.just(true);
    }

    /**
     * Load wrap from DataBase
     */
    private void loadFromDB() {
        // TODO: implement
    }

    @Override
    @CheckResult
    public Single<String> upload(@NonNull Image image) {
        return Single.just("");
    }

    @Override
    @CheckResult
    public Single<String> upload(@NonNull Post post) {
        return Single.just("");
    }

    @Override
    @CheckResult
    public Single<Boolean> delete(@NonNull Post post) {
        return Single.just(false);
    }

    @Override
    @CheckResult
    public Single<List<Post>> loadPosts(@NonNull TimeInterval timeInterval) {
        return Single.fromCallable(() -> {
            VKKeyKeeper keyKeeper = keysModel.getVKKeyKeeper();
            if (keyKeeper == null) {
                // ToDo: handle
                return Collections.emptyList();
            }
            List<Post> posts = new ArrayList<>();
            Call<VKResponse<VKItems<Say>>> call;
            long currentTime = 0;
            do {
                call = client.getPosts(keyKeeper.getUserId(), offset, keyKeeper.getAccessToken());
                try {
                    Response<VKResponse<VKItems<Say>>> execute = call.execute();
                    VKResponse<VKItems<Say>> body = execute.body();
                    if (body.error != null) {
                        // ToDo: Handle
                        Log.e(TAG, body.error.errorMessage);
                        return Collections.emptyList();
                    }
                    if (body.response.items.isEmpty()) {
                        break;
                    }
                    for (Say say : body.response.items) {
                        currentTime = say.date;
                        if (!timeInterval.contains(currentTime)) {
                            break;
                        }
                        offset++;
                        Post post = new Post(say.text, say.date, null, Networks.VK, new Link(say.id));
                        post.setInfo(getInfo(say));
                        setAttachments(post, say);
                        posts.add(post);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return posts;
                }
            } while (timeInterval.contains(currentTime));
            return posts;
        });
    }

    @NonNull
    private String toCommaSeparated(@NonNull List<String> strings) {
        StringBuilder sb = new StringBuilder();
        for (String string : strings) {
            sb.append(string);
            sb.append(',');
        }
        if (sb.length() > 0) {
            sb.delete(sb.length() - 1, sb.length());
        }
        return sb.toString();
    }

    @Override
    @CheckResult
    public Single<List<Person>> getPersons(@NonNull SingleNetwork element, @RequestType int requestType) {
        return Single.fromCallable(() -> {
            final VKKeyKeeper keyKeeper = keysModel.getVKKeyKeeper();
            if (keyKeeper == null) {
                // ToDo: Handle
                return Collections.emptyList();
            }

            String type, filter;
            if (element instanceof Post) {
                type = "post";
            } else if (element instanceof Image) {
                type = "photo";
            } else if (element instanceof Comment) {
                type = "comment";
            } else {
                return Collections.emptyList();
            }
            switch (requestType) {
                case LIKES:
                    filter = "likes";
                    break;
                case SHARES:
                    filter = "copies";
                    break;
                default:
                    return Collections.emptyList();
            }
            if (element.getLink() == null) {
                return Collections.emptyList();
            }

            Call<VKResponse<VKItems<Profile>>> likersIds = client.getLikersIds(keyKeeper.getUserId(),
                    element.getLink().get(), type, filter, keyKeeper.getAccessToken());
            try {
                Response<VKResponse<VKItems<Profile>>> executed = likersIds.execute();
                VKResponse<VKItems<Profile>> body = executed.body();
                if (body.error != null) {
                    // ToDo: Handle
                    Log.e(TAG, body.error.errorMessage);
                    return Collections.emptyList();
                }

                VKItems<Profile> response = body.response;
                List<String> ids = new ArrayList<>();
                for (Profile profile : response.items) {
                    ids.add(profile.id);
                }
                Call<VKResponse<List<Profile>>> profiles = client.getProfiles(toCommaSeparated(ids),
                        keyKeeper.getAccessToken());
                Response<VKResponse<List<Profile>>> gotPerson = profiles.execute();
                VKResponse<List<Profile>> personsBody = gotPerson.body();
                if (personsBody.error != null) {
                    // ToDo: Handle
                    Log.e(TAG, personsBody.error.errorMessage);
                    return Collections.emptyList();
                }
                List<Person> persons = new ArrayList<>();
                for (Profile profile : personsBody.response) {
                    persons.add(toPerson(profile));
                }
                return persons;
            } catch (IOException e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        });
    }

    @NonNull
    private Person toPerson(Profile profile) {
        return new Person(profile.firstName, profile.lastName, profile.photo50, Networks.VK);
    }

    private int get(@Nullable Counter counter) {
        return counter == null ? 0 : counter.count;
    }

    private Info getInfo(@NonNull Say say) {
        return new Info(get(say.likes), get(say.reposts), get(say.comments));
    }

    @Nullable
    private ly.loud.loudly.base.attachments.Attachment toAttachment(@NonNull Attachment attachment) {
        Photo photo = attachment.photo;
        if (photo != null) {
            return new Image(attachment.photo.photo604, new Point(photo.width, photo.height),
                    Networks.VK, new Link(photo.id));
        }
        return null;
    }

    private void setAttachments(@NonNull ly.loud.loudly.base.says.Say say, @NonNull Say loaded) {
        if (loaded.attachments == null) {
            return;
        }
        for (Attachment attachment : loaded.attachments) {
            ly.loud.loudly.base.attachments.Attachment filled = toAttachment(attachment);
            if (filled == null) {
                continue;
            }
            say.addAttachment(filled);
        }
    }

    @Override
    public Single<List<Comment>> getComments(@NonNull SingleNetwork element) {
        return Single.fromCallable(() -> {
            VKKeyKeeper keyKeeper = keysModel.getVKKeyKeeper();
            if (keyKeeper == null) {
                // ToDo: Handle
                return Collections.emptyList();
            }

            if (element.getLink() == null) {
                return Collections.emptyList();
            }
            Call<VKResponse<VKItems<Say>>> call = client.getComments(
                    keyKeeper.getUserId(), element.getLink().get(),
                    keyKeeper.getAccessToken());
            try {
                Response<VKResponse<VKItems<Say>>> executed = call.execute();
                VKResponse<VKItems<Say>> body = executed.body();
                if (body.error != null) {
                    // ToDo: Handle
                    Log.e(TAG, body.error.errorMessage);
                    return Collections.emptyList();
                }
                List<Comment> comments = new ArrayList<>();
                List<Profile> profiles = body.response.profiles;
                Func1<String, Profile> getProfile = id -> {
                    for (Profile profile : profiles) {
                        if (profile.id.equals(id)) {
                            return profile;
                        }
                    }
                    return null;
                };
                for (Say say : body.response.items) {
                    Profile profile = getProfile.call(say.fromId);

                    Comment comment = new Comment(say.text, say.date,
                            toPerson(profile), Networks.VK, new Link(say.id));
                    comment.setInfo(getInfo(say));
                    setAttachments(comment, say);

                    comments.add(comment);
                }
                return comments;
            } catch (IOException e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        });
    }

    @Override
    @CheckResult
    public Single<Boolean> connect(@NonNull KeyKeeper keyKeeper) {
        if (!(keyKeeper instanceof ly.loud.loudly.networks.VK.VKKeyKeeper))
            throw new AssertionError("KeyKeeper must be VkKeyKeeper");

        keysModel.setVKKeyKeeper((VKKeyKeeper) keyKeeper);
        return Single.just(true);
    }

    @Override
    @CheckResult
    public Single<Boolean> disconnect() {
        return keysModel.disconnectFromNetwork(Networks.VK);
    }

    @Override
    @CheckResult
    public boolean isConnected() {
        return keysModel.getVKKeyKeeper() != null;
    }

    @Override
    public int getId() {
        return Networks.VK;
    }
}

