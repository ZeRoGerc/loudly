package VK;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import base.Networks;
import base.Person;
import base.SingleNetwork;
import base.Tasks;
import base.Wrap;
import base.attachments.Image;
import base.attachments.LoudlyImage;
import base.says.Comment;
import base.says.Info;
import base.says.LoudlyPost;
import base.says.Post;
import ly.loud.loudly.Loudly;
import util.BackgroundAction;
import util.InvalidTokenException;
import util.Network;
import util.Query;
import util.TimeInterval;
import util.parsers.json.ArrayParser;
import util.parsers.json.ObjectParser;


public class VKWrap extends Wrap {
    private static final int NETWORK = Networks.VK;
    private static int offset = 0;

    private static final String TAG = "VK_WRAP_TAG";
    private static final String API_VERSION = "5.40";
    private static final String MAIN_SERVER = "https://api.vk.com/method/";
    private static final String POST_METHOD = "wall.post";
    private static final String GET_METHOD = "wall.getById";
    private static final String DELETE_METHOD = "wall.delete";
    private static final String LOAD_POSTS_METHOD = "wall.get";
    private static final String PHOTO_UPLOAD_METHOD = "photos.getWallUploadServer";
    private static final String SAVE_PHOTO_METHOD = "photos.saveWallPhoto";

    private static final String ACCESS_TOKEN = "access_token";

    @Override
    public int shouldUploadImage() {
        return Wrap.IMAGE_ONLY_UPLOAD;
    }

    @Override
    public void resetState() {
        offset = 0;
    }

    @Override
    public int networkID() {
        return NETWORK;
    }

    @Override
    protected Query makeAPICall(String method) {
        Query query = new Query(MAIN_SERVER + method);
        query.addParameter("v", API_VERSION);
        return query;
    }

    @Override
    protected Query makeSignedAPICall(String method) throws InvalidTokenException {
        Query query = makeAPICall(method);
        VKKeyKeeper keys = (VKKeyKeeper) Loudly.getContext().getKeyKeeper(networkID());
        if (!keys.isValid()) {
            throw new InvalidTokenException();
        }
        query.addParameter(ACCESS_TOKEN, keys.getAccessToken());
        return query;
    }

    @Override
    public String checkPost(LoudlyPost post) {
        if (post.getText().isEmpty() && post.getAttachments().isEmpty()) {
            return "Either text or image should be on post";
        }
        if (post.getAttachments().size() > 1) {
            return "Sorry, we can upload only 1 image";
        }
        return null;
    }

    @Override
    public void uploadPost(LoudlyPost post) throws IOException {
        Query query = makeSignedAPICall(POST_METHOD);
        if (post.getText().length() > 0) {
            query.addParameter("message", post.getText());
        }
        if (post.getAttachments().size() > 0) {
            Image image = (Image) post.getAttachments().get(0);
            String userID = ((VKKeyKeeper) Loudly.getContext().getKeyKeeper(networkID())).getUserId();
            query.addParameter("attachments", "photo" + userID + "_" + image.getId());
        }

        String response = Network.makePostRequest(query);

        JSONObject parser;
        try {
            parser = new JSONObject(response);
            String id = parser.getJSONObject("response").getString("post_id");
            post.setId(id);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void uploadImage(LoudlyImage image, BackgroundAction progress) throws IOException {
        Query getUploadServerAddress = makeSignedAPICall(PHOTO_UPLOAD_METHOD);
        VKKeyKeeper keys = (VKKeyKeeper) Loudly.getContext().getKeyKeeper(networkID());
        getUploadServerAddress.addParameter("user_id", keys.getUserId());

        String response = Network.makeGetRequest(getUploadServerAddress);

        String uploadURL;
        JSONObject parser;
        try {
            parser = new JSONObject(response).getJSONObject("response");

            uploadURL = parser.getString("upload_url");
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        Query imageUploadQuery = new Query(uploadURL);

        response = Network.makePostRequest(imageUploadQuery, progress, "photo", image);

        Query getPhotoId = makeSignedAPICall(SAVE_PHOTO_METHOD);

        try {
            parser = new JSONObject(response);
            getPhotoId.addParameter("photo", parser.getString("photo"));
            getPhotoId.addParameter("server", parser.getString("server"));
            getPhotoId.addParameter("hash", parser.getString("hash"));
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        getPhotoId.addParameter("user_id", ((VKKeyKeeper) Loudly.getContext().getKeyKeeper(networkID())).getUserId());

        response = Network.makeGetRequest(getPhotoId);

        try {
            parser = new JSONObject(response).getJSONArray("response").getJSONObject(0);
            String id = parser.getString("id");
            String url = parser.getString("photo_604");
            if (image.getExternalLink() == null) {
                image.setExternalLink(url);
            }
            int height = parser.getInt("height");
            int width = parser.getInt("width");
            image.setId(id);
            image.setHeight(height);
            image.setWidth(width);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void getImageInfo(List<Image> images) throws IOException {
        Query query = makeSignedAPICall("photos.getById");
        StringBuilder sb = new StringBuilder();
        VKKeyKeeper keyKeeper = ((VKKeyKeeper) Loudly.getContext().getKeyKeeper(networkID()));
        for (Image image : images) {
            sb.append(keyKeeper.getUserId());
            sb.append('_');
            sb.append(image.getId());
        }
        if (sb.length() > 0) {
            sb.delete(sb.length() - 1, sb.length());
        }
        query.addParameter("photos", sb);

        ObjectParser photoParser = makePhotoParser();
        ObjectParser parser = new ObjectParser()
                .parseArray("response", new ArrayParser(-1, photoParser));

        ArrayParser response = Network.makeGetRequestAndParse(query, parser).getArray();
        if (response.size() != images.size()) {
            throw new IOException("Can't find image in network " + networkID());
        }
        int ind = 0;
        for (Image image : images) {
            fillImageFromParser(image, response.getObject(ind++));
        }
    }

    @Override
    public void deletePost(Post post) throws IOException {
        Query query = makeSignedAPICall(DELETE_METHOD);
        VKKeyKeeper keys = (VKKeyKeeper) Loudly.getContext().getKeyKeeper(NETWORK);
        query.addParameter("owner_id", keys.getUserId());
        query.addParameter("post_id", post.getId());

        String response = Network.makeGetRequest(query);

        // todo: check for delete
        post.cleanIds();
    }

    @Override
    public void getPostsInfo(List<Post> posts, Tasks.GetInfoCallback callback) throws IOException {
        Query query = makeSignedAPICall(GET_METHOD);
        VKKeyKeeper keys = (VKKeyKeeper) Loudly.getContext().getKeyKeeper(networkID());
        StringBuilder sb = new StringBuilder();
        for (Post post : posts) {
            if (post.existsIn(networkID())) {
                sb.append(keys.getUserId());
                sb.append('_');
                sb.append(post.getId());
                sb.append(',');
            }
        }
        if (sb.length() > 0) {
            sb.delete(sb.length() - 1, sb.length());
        }
        query.addParameter("posts", sb);

        ObjectParser infoParser = new ObjectParser()
                .parseInt("count");

        ObjectParser postParser = new ObjectParser()
                .parseString("id")
                .parseObject("likes", (ObjectParser) infoParser.copyStructure())
                .parseObject("comments", (ObjectParser) infoParser.copyStructure())
                .parseObject("shares", (ObjectParser) infoParser.copyStructure());

        ObjectParser responseParser = new ObjectParser()
                .parseArray("response", new ArrayParser(-1, postParser));

        ArrayParser response = Network.makeGetRequestAndParse(query, responseParser)
                .getArray();

        Iterator<Post> iterator = posts.listIterator();
        for (int i = 0; i < response.size(); i++) {
            postParser = response.getObject(i);
            String id = postParser.getString("");
            int likes = postParser.getObject().getInt(0);
            int comments = postParser.getObject().getInt(0);
            int shares = postParser.getObject().getInt(0);

            while (iterator.hasNext()) {
                Post post = iterator.next();
                if (post.getId().equals(id)) {
                    Info info = new Info(likes, shares, comments);
                    callback.infoLoaded(post, info);
                    break;
                } else {
                    callback.foundDeletedPost(post);
                }
            }
        }
    }


    private ObjectParser makePhotoParser() {
        return new ObjectParser()
                .parseString("id")
                .parseString("photo_604")
                .parseInt("width")
                .parseInt("height");
    }

    private void fillImageFromParser(Image image, ObjectParser photoParser) {
        String photoId = photoParser.getString("");
        String link = photoParser.getString("");
        int width = photoParser.getInt(0);
        int height = photoParser.getInt(0);

        image.setExternalLink(link);
        image.setId(photoId);
        image.setWidth(width);
        image.setHeight(height);
    }

    @Override
    public void loadPosts(TimeInterval timeInterval, Tasks.LoadCallback callback) throws IOException {
        offset = Math.max(0, offset - 5);

        ObjectParser photoParser = makePhotoParser();

        ObjectParser attachmentParser = new ObjectParser()
                .parseString("type")
                .parseObject("photo", photoParser);

        ObjectParser postParser = new ObjectParser()
                .parseString("id")
                .parseLong("date")
                .parseString("text")
                .parseObject("likes", new ObjectParser().parseInt("count"))
                .parseObject("reposts", new ObjectParser().parseInt("count"))
                .parseObject("comments", new ObjectParser().parseInt("count"))
                .parseArray("attachments", new ArrayParser(-1, attachmentParser));

        ArrayParser itemsParser = new ArrayParser(-1, postParser);
        ObjectParser responseParser = new ObjectParser()
                .parseArray("items", itemsParser);

        ObjectParser parser = new ObjectParser()
                .parseObject("response", responseParser);
        long earliestPost = -1;
        do {
            Query query = makeAPICall(LOAD_POSTS_METHOD);
            VKKeyKeeper keys = (VKKeyKeeper) Loudly.getContext().getKeyKeeper(networkID());
            query.addParameter("owner_id", keys.getUserId());
            query.addParameter("filter", "owner");
            query.addParameter("count", "10");
            query.addParameter("offset", offset);

            ObjectParser response = Network.makeGetRequestAndParse(query, parser);

            ArrayParser arrayParser = response.getObject().getArray();
            for (int i = 0; i < arrayParser.size(); i++) {
                postParser = arrayParser.getObject(i);
                String id = postParser.getString("");
                long date = postParser.getLong(0l);
                String text = postParser.getString("");

                int likes = postParser.getObject().getInt(0);
                int shares = postParser.getObject().getInt(0);
                int comments = postParser.getObject().getInt(0);

                ArrayParser attachments = postParser.getArray();

                Info info = new Info(likes, shares, comments);

                boolean updated = callback.updateLoudlyPostInfo(id, networkID(), info);
                if (updated) {
                    continue;
                }

                if (timeInterval.contains(date)) {
                    Post res = new Post(text, date, null, networkID(), id);
                    res.setInfo(info);

                    for (int j = 0; j < attachments.size(); j++) {
                        attachmentParser = attachments.getObject(j);
                        String type = attachmentParser.getString("");
                        if (type.equals("photo") || type.equals("posted_photo")) {
                            photoParser = attachmentParser.getObject();
                            Image image = new Image();
                            fillImageFromParser(image, photoParser);
                            res.addAttachment(image);
                        }
                    }
                    callback.postLoaded(res);
                    offset++;
                }

            }
        } while (timeInterval.contains(earliestPost));
    }

    @Override
    public List<Comment> getComments(SingleNetwork element) throws IOException {
        if (!(element instanceof Post)) {
            return new LinkedList<>(); // TODO: 12/12/2015  
        }
        Query query = makeSignedAPICall("wall.getComments");
        VKKeyKeeper keyKeeper = ((VKKeyKeeper) Loudly.getContext().getKeyKeeper(networkID()));

        query.addParameter("owner_id", keyKeeper.getUserId());
        query.addParameter("post_id", element.getId());
        query.addParameter("need_likes", 1);
        query.addParameter("count", 20);
        query.addParameter("sort", "asc");
        query.addParameter("preview_length", 0);
        query.addParameter("extended", 1);

        ObjectParser photoParser = new ObjectParser()
                .parseString("id")
                .parseString("photo_604")
                .parseInt("width")
                .parseInt("height");

        ObjectParser attachmentParser = new ObjectParser()
                .parseString("type")
                .parseObject("photo", photoParser);

        ObjectParser commentParser = new ObjectParser()
                .parseString("id")
                .parseString("from_id")
                .parseLong("date")
                .parseString("text")
                .parseObject("likes", new ObjectParser().parseInt("count"))
                .parseObject("reposts", new ObjectParser().parseInt("count"))
                .parseObject("comments", new ObjectParser().parseInt("count"))
                .parseArray("attachments", new ArrayParser(-1, attachmentParser));

        ObjectParser personParser = new ObjectParser()
                .parseString("id")
                .parseString("first_name")
                .parseString("last_name")
                .parseString("photo_50");

        ObjectParser parser = new ObjectParser()
                .parseArray("items", new ArrayParser(-1, commentParser))
                .parseArray("profiles", new ArrayParser(-1, personParser));

        ObjectParser response = Network.makeGetRequestAndParse(query,
                new ObjectParser().parseObject("response", parser))
                .getObject();

        ArrayParser posts = response.getArray();
        ArrayParser persons = response.getArray();

        LinkedList<Person> profiles = new LinkedList<>();
        for (int i = 0; i < persons.size(); i++) {
            ObjectParser person = persons.getObject(i);
            String id = person.getString("");
            String firstName = person.getString("");
            String lastName = person.getString("");
            String photo = person.getString("");
            Person p = new Person(firstName, lastName, photo, networkID());
            p.setId(id);
            profiles.add(p);
        }

        LinkedList<Comment> comments = new LinkedList<>();

        for (int i = 0; i < posts.size(); i++) {
            commentParser = posts.getObject(i);
            String id = commentParser.getString("");
            String userID = commentParser.getString("");
            Person author = null;
            for (Person p : profiles) {
                if (p.getId().equals(userID)) {
                    author = p;
                    break;
                }
            }

            long date = commentParser.getLong(0l);
            String text = commentParser.getString("");

            int likes = commentParser.getObject().getInt(0);
            ArrayParser attachments = commentParser.getArray();

            Comment comment = new Comment(text, date, author, networkID(), id);
            if (attachments != null) {
                for (int j = 0; j < attachments.size(); j++) {
                    attachmentParser = attachments.getObject(i);
                    String type = attachmentParser.getString("");
                    if (type.equals("photo")) {
                        Image image = new Image();
                        fillImageFromParser(image, attachmentParser.getObject());
                        comment.addAttachment(image);
                    }
                }
            }
            comment.setInfo(new Info(likes, 0, 0));
            comments.add(comment);
        }
        return comments;
    }

    @Override
    public LinkedList<Person> getPersons(int what, SingleNetwork element) throws IOException {
        Query query = makeSignedAPICall("likes.getList");
        String type;
        if (element instanceof Post) {
            type = "post";
        } else if (element instanceof Image) {
            type = "photo";
        } else if (element instanceof Comment) {
            type = "comment";
        } else {
            return new LinkedList<>();
        }

        query.addParameter("type", type);
        VKKeyKeeper keys = ((VKKeyKeeper) Loudly.getContext().getKeyKeeper(networkID()));
        query.addParameter("owner_id", keys.getUserId());
        query.addParameter("item_id", element.getId());
        String filter;
        switch (what) {
            case Tasks.LIKES:
                filter = "likes";
                break;
            case Tasks.SHARES:
                filter = "copies";
                break;
            default:
                filter = "";
                break;
        }
        query.addParameter("filter", filter);
        query.addParameter("extended", 1);
        // TODO: 12/3/2015 Add offset here

        String response = Network.makeGetRequest(query);

        Query getPeopleQuery = makeSignedAPICall("users.get");

        JSONObject parser;
        try {
            parser = new JSONObject(response).getJSONObject("response");
            JSONArray likers = parser.getJSONArray("items");

            if (likers.length() == 0) {
                return new LinkedList<>();
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < likers.length(); i++) {
                long id = likers.getJSONObject(i).getLong("id");
                sb.append(id);
                sb.append(',');
            }
            sb.delete(sb.length() - 1, sb.length());

            getPeopleQuery.addParameter("user_ids", sb);
            getPeopleQuery.addParameter("fields", "photo_50");
        } catch (JSONException e) {
            e.printStackTrace();
            return new LinkedList<>();
        }

        response = Network.makeGetRequest(getPeopleQuery);

        LinkedList<Person> result = new LinkedList<>();

        JSONArray people;
        try {
            people = new JSONObject(response).getJSONArray("response");
            for (int i = 0; i < people.length(); i++) {
                JSONObject person = people.getJSONObject(i);
                String id = person.getString("id");
                String firstName = person.getString("first_name");
                String lastName = person.getString("last_name");
                String photoURL = person.getString("photo_50");

                Person person1 = new Person(firstName, lastName, photoURL, networkID());
                person1.setId(id);
                result.add(person1);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return new LinkedList<>();
        }

        return result;
    }
}
