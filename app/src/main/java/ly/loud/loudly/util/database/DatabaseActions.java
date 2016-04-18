package ly.loud.loudly.util.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ly.loud.loudly.base.KeyKeeper;
import ly.loud.loudly.base.Link;
import ly.loud.loudly.base.Location;
import ly.loud.loudly.base.MultipleNetwork;
import ly.loud.loudly.base.Networks;
import ly.loud.loudly.base.attachments.Attachment;
import ly.loud.loudly.base.attachments.LoudlyImage;
import ly.loud.loudly.base.says.LoudlyPost;
import ly.loud.loudly.base.says.Post;
import ly.loud.loudly.ui.Loudly;
import ly.loud.loudly.util.TimeInterval;
import ly.loud.loudly.util.Utils;
import ly.loud.loudly.util.database.AttachmentsContract.AttachmentsEntry;
import ly.loud.loudly.util.database.KeysContract.KeysEntry;
import ly.loud.loudly.util.database.LinksContract.LinksEntry;
import ly.loud.loudly.util.database.LocationContract.LocationEntry;
import ly.loud.loudly.util.database.PostContract.PostEntry;

public class DatabaseActions {

    /**
     * Function for saving post to Database
     *
     * @param post Post for saving
     * @throws DatabaseException if anything went wrong with database
     */
    public static <T extends Post & MultipleNetwork> void savePost(T post) throws DatabaseException {
        SQLiteDatabase db = PostDbHelper.getInstance().getWritableDatabase();

        db.beginTransaction();
        try {

            // Insert post's links
            long linksId = db.insert(LinksEntry.TABLE_NAME, LinksEntry.COLUMN_NAME_LOUDLY,
                    createLinkRow(post.getLinks()));
            if (linksId == -1) {
                throw new DatabaseException("Can't insert links");
            }

            // Insert location
            long locID = -1;
            if (post.getLocation() != null) {
                locID = db.insert(LocationEntry.TABLE_NAME, null,
                        createLocationRow(post.getLocation()));
                if (locID == -1) {
                    throw new DatabaseException("Can't insert location");
                }
            }

            long prevId = AttachmentsEntry.END_OF_LIST_VALUE;
            long firstId = AttachmentsEntry.END_OF_LIST_VALUE;
            for (Attachment att : post.getAttachments()) {
                long curId = Long.parseLong(att.getLink().get());

                // Update attachments link in DB. Structure - Double-linked list
                if (prevId != AttachmentsEntry.END_OF_LIST_VALUE) {
                    ContentValues update = new ContentValues();

                    // Link from previous to current
                    update.put(AttachmentsEntry.COLUMN_NAME_NEXT, curId);
                    int count = db.update(AttachmentsEntry.TABLE_NAME, update, sqlEqual(AttachmentsEntry._ID, prevId), null);
                    if (count != 1) {
                        throw new DatabaseException("Can't update attachment");
                    }
                    update.clear();

                    // Link from current to previous
                    update.put(AttachmentsEntry.COLUMN_NAME_PREV, prevId);
                    count = db.update(AttachmentsEntry.TABLE_NAME, update, sqlEqual(AttachmentsEntry._ID, curId), null);
                    if (count != 1) {
                        throw new DatabaseException("Can't update attachment");
                    }
                }

                if (prevId == AttachmentsEntry.END_OF_LIST_VALUE) {
                    firstId = curId;
                }

                prevId = curId;
            }

            // Insert post to the DB

            long localId = db.insert(PostEntry.TABLE_NAME, null,
                    createPostRow(post, firstId, linksId, locID));

            if (localId == -1) {
                throw new DatabaseException("Can't insert post");
            }

            ContentValues update = new ContentValues();
            update.put(LinksEntry.COLUMN_NAME_LOUDLY, localId);
            if (upsert(db, LinksEntry.TABLE_NAME, LinksEntry._ID, localId, update) == -1) {
                throw new DatabaseException("Can't update id");
            }
            post.setLink(Networks.LOUDLY, new Link(localId));

            // Success
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static <T extends Attachment & MultipleNetwork> void saveAttachment(T attachment) throws DatabaseException {
        SQLiteDatabase db = PostDbHelper.getInstance().getWritableDatabase();

        db.beginTransaction();
        try {
            long atLinkId = db.insert(LinksEntry.TABLE_NAME, LinksEntry.COLUMN_NAME_FB,
                    createLinkRow(attachment.getLinks()));

            if (atLinkId == -1) {
                throw new DatabaseException("Can't insert links for an attachment");
            }

            // Insert attachment
            long curId = db.insert(AttachmentsEntry.TABLE_NAME, null,
                    createAttachmentsRow(attachment, AttachmentsEntry.END_OF_LIST_VALUE, atLinkId));
            if (curId == -1) {
                throw new DatabaseException("Can't insert attachment");
            }

            attachment.setLink(new Link(curId));
            // Success
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Updates post's link in DB according to current links in post
     *
     * @param networks IDs of networks that must be updated
     * @param post     LoudlyPost, that should be updated
     * @throws DatabaseException if anything went wrong with database
     */
    public static void updatePostLinks(int[] networks, LoudlyPost post) throws DatabaseException {
        SQLiteDatabase db = PostDbHelper.getInstance().getWritableDatabase();

        db.beginTransaction();
        Cursor cursor = null;
        try {
            String[] projection = {
                    PostEntry.COLUMN_NAME_LINKS, PostEntry.COLUMN_NAME_FIRST_ATTACHMENT};
            cursor = db.query(PostEntry.TABLE_NAME,
                    projection, sqlEqual(PostEntry._ID, post.getLink(Networks.LOUDLY)),
                    null, null, null, null);
            if (cursor.getCount() == 0) {
                throw new DatabaseException("Can't find post: " + post.getLink(Networks.LOUDLY));
            }

            cursor.moveToFirst();
            long linkTd = cursor.getLong(cursor.getColumnIndex(PostEntry.COLUMN_NAME_LINKS));
            long atId = cursor.getLong(cursor.getColumnIndex(PostEntry.COLUMN_NAME_FIRST_ATTACHMENT));

            ContentValues update = new ContentValues();
            for (int network : networks) {
                update.put(LinksEntry.LINK_COLUMNS[network], post.getLink(network).get());
            }
            if (db.update(LinksEntry.TABLE_NAME, update,
                    sqlEqual(LinksEntry._ID, linkTd), null) != 1) {
                throw new DatabaseException("Failed updating links for post: " + post.getLink(Networks.LOUDLY));
            }

            projection = new String[]{AttachmentsEntry.COLUMN_NAME_LINK,
                    AttachmentsEntry.COLUMN_NAME_NEXT};

            int ind = 0;
            while (atId != AttachmentsEntry.END_OF_LIST_VALUE) {
                // Update internal link
                update = new ContentValues();
                update.put(AttachmentsEntry.COLUMN_NAME_EXTRA, post.getAttachments().get(ind).getExtra());
                if (db.update(AttachmentsEntry.TABLE_NAME,
                        update, sqlEqual(AttachmentsEntry._ID, atId), null) != 1) {
                    throw new DatabaseException("Can't update extra for attachment: " + atId);
                }

                cursor = db.query(AttachmentsEntry.TABLE_NAME,
                        projection, sqlEqual(AttachmentsEntry._ID, atId),
                        null, null, null, null);
                if (cursor.getCount() == 0) {
                    throw new DatabaseException("Can't find attachments for post: " + post.getLink(Networks.LOUDLY));
                }

                cursor.moveToFirst();
                atId = cursor.getLong(cursor.getColumnIndex(AttachmentsEntry.COLUMN_NAME_NEXT));
                linkTd = cursor.getLong(cursor.getColumnIndex(AttachmentsEntry.COLUMN_NAME_LINK));

                update = new ContentValues();
                for (int network : networks) {
                    MultipleNetwork attachment = (MultipleNetwork) post.getAttachments().get(ind);
                    update.put(LinksEntry.LINK_COLUMNS[network], attachment.getLink(network).get());
                }
                ind++;
                if (db.update(LinksEntry.TABLE_NAME,
                        update, sqlEqual(LinksEntry._ID, linkTd), null) != 1) {
                    throw new DatabaseException("Can't update links for attachment: " + atId);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            Utils.closeQuietly(cursor);
            db.endTransaction();
        }
    }

    public static List<Post> loadPosts(TimeInterval time) throws DatabaseException {
        SQLiteDatabase db = PostDbHelper.getInstance().getReadableDatabase();

        String sortOrder = PostEntry.COLUMN_NAME_DATE + " DESC";
        Cursor cursor = null;

        String sinceTimeQuery = (time.from != -1) ? PostEntry.COLUMN_NAME_DATE + " > " + Long.toString(time.from) : "";
        String beforeTimeQuery = (time.to != -1) ? PostEntry.COLUMN_NAME_DATE + " < " + Long.toString(time.to) : "";

        String select = (sinceTimeQuery.equals("") ? beforeTimeQuery : sinceTimeQuery) +
                (beforeTimeQuery.equals("") ? "" : " AND " + beforeTimeQuery);

        LinkedList<Post> posts = new LinkedList<>();
        try {
            cursor = db.query(
                    PostEntry.TABLE_NAME,
                    PostEntry.POST_COLUMNS,
                    select, null, null, null, sortOrder);

            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                String text = cursor.getString(cursor.getColumnIndex(PostEntry.COLUMN_NAME_TEXT));
                long date = cursor.getLong(cursor.getColumnIndex(PostEntry.COLUMN_NAME_DATE));

                long atId = cursor.getLong(cursor.getColumnIndex(PostEntry.COLUMN_NAME_FIRST_ATTACHMENT));
                long linksId = cursor.getLong(cursor.getColumnIndex(PostEntry.COLUMN_NAME_LINKS));
                long locId = cursor.getLong(cursor.getColumnIndex(PostEntry.COLUMN_NAME_LOCATION));
                long localId = cursor.getLong(cursor.getColumnIndex(PostEntry._ID));

                Location location = (locId == -1) ? null : readLocation(db, locId);
                Link[] links = readLinks(db, linksId);
                ArrayList<Attachment> attachments = readAttachments(db, atId);

                LoudlyPost post = new LoudlyPost(text, attachments, links, date, location);
                post.setLink(Networks.LOUDLY, new Link(Long.toString(localId), false));

                posts.add(post);
                cursor.moveToNext();
            }
        } finally {
            Utils.closeQuietly(cursor);
        }
        return posts;
    }

    // ToDo: Split into deletion of attachments and deletion post
    public static void deletePost(LoudlyPost post) throws DatabaseException {
        SQLiteDatabase db = PostDbHelper.getInstance().getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(PostEntry.TABLE_NAME, PostEntry.POST_COLUMNS,
                    sqlEqual(PostEntry._ID, post.getLink(Networks.LOUDLY)), null, null, null, null);
            if (cursor.getCount() == 0) {
                return;
            }

            cursor.moveToFirst();
            long locId = cursor.getLong(cursor.getColumnIndex(PostEntry.COLUMN_NAME_LOCATION));
            long atId = cursor.getLong(cursor.getColumnIndex(PostEntry.COLUMN_NAME_FIRST_ATTACHMENT));
            long linkId = cursor.getLong(cursor.getColumnIndex(PostEntry.COLUMN_NAME_LINKS));

            db.beginTransaction();
            if (locId != -1 && db.delete(LocationEntry.TABLE_NAME, sqlEqual(LocationEntry._ID, locId), null) != 1) {
                throw new DatabaseException("Can't delete location: " + locId);
            }

            if (linkId != -1 && db.delete(LinksEntry.TABLE_NAME, sqlEqual(LinksEntry._ID, linkId), null) != 1) {
                throw new DatabaseException("Can't delete post's link: " + linkId);
            }

            while (atId != AttachmentsEntry.END_OF_LIST_VALUE) {
                atId = deleteAttachment(db, atId);
            }

            if (db.delete(PostEntry.TABLE_NAME, sqlEqual(PostEntry._ID, post.getLink(Networks.LOUDLY)), null) != 1) {
                throw new DatabaseException("Can't delete post: " + post.getLink(Networks.LOUDLY));
            }
            if (post.getLink(Networks.LOUDLY) != null) {
                post.getLink(Networks.LOUDLY).setValid(false);
            }
            db.setTransactionSuccessful();
        } finally {
            Utils.closeQuietly(cursor);
            if (db.inTransaction()) {
                db.endTransaction();
            }
        }
    }

    /**
     * Function that saves KeyKeepers to database
     *
     * @throws DatabaseException if anything went wrong with DB
     */
    public static void saveKeys() throws DatabaseException {
        SQLiteDatabase db = KeysDbHelper.getInstance().getWritableDatabase();
        Loudly context = Loudly.getContext();

        db.beginTransaction();
        try {
            for (int i = 0; i < Networks.NETWORK_COUNT; i++) {
                if (context.getKeyKeeper(i) != null) {
                    KeyKeeper keys = context.getKeyKeeper(i);

                    ContentValues values = new ContentValues();
                    values.put(KeysEntry.COLUMN_NAME_NETWORK, i);
                    values.put(KeysEntry.COLUMN_NAME_VALUE, keys.toStringBundle());

                    long keyId = upsert(db, KeysEntry.TABLE_NAME,
                            KeysEntry.COLUMN_NAME_NETWORK, Integer.toString(i), values);

                    if (keyId == -1) {
                        throw new DatabaseException("Can't save key for the network # " + i);
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Function that loads KeyKeepers from DB
     *
     * @throws DatabaseException if anything went wrong with DB
     */
    public static void loadKeys() throws DatabaseException {
        SQLiteDatabase db = KeysDbHelper.getInstance().getReadableDatabase();
        Loudly context = Loudly.getContext();

        Cursor cursor = null;
        try {
            String[] projection = {
                    KeysEntry.COLUMN_NAME_NETWORK,
                    KeysEntry.COLUMN_NAME_VALUE
            };
            cursor = db.query(KeysEntry.TABLE_NAME, projection, null, null, null, null, null);
            cursor.moveToFirst();

            while (!cursor.isAfterLast()) {
                int network = cursor.getInt(cursor.getColumnIndex(KeysEntry.COLUMN_NAME_NETWORK));
                String bundle = cursor.getString(cursor.getColumnIndex(KeysEntry.COLUMN_NAME_VALUE));

                context.setKeyKeeper(network, KeyKeeper.fromStringBundle(network, bundle));
                cursor.moveToNext();
            }
        } finally {
            Utils.closeQuietly(cursor);
        }
    }

    /**
     * Delete KeyKeeper from DB
     *
     * @param network ID of network, whose key should be deleted
     * @throws DatabaseException if anything went wrong with DB
     */
    public static void deleteKey(int network) throws DatabaseException {
        SQLiteDatabase db = KeysDbHelper.getInstance().getWritableDatabase();
        int count = db.delete(KeysEntry.TABLE_NAME,
                sqlEqual(KeysEntry.COLUMN_NAME_NETWORK, network), null);
        if (count == 0) {
            throw new DatabaseException("Can't delete keys for network: " + network);
        }
    }

    public static void updateKey(int network, KeyKeeper keyKeeper) throws DatabaseException {
        SQLiteDatabase db = KeysDbHelper.getInstance().getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KeysEntry.COLUMN_NAME_NETWORK, network);
        values.put(KeysEntry.COLUMN_NAME_VALUE, keyKeeper.toStringBundle());
        if (upsert(db, KeysEntry.TABLE_NAME, KeysEntry.COLUMN_NAME_NETWORK, network, values) == -1) {
            throw new DatabaseException("Can't update key for: " + network);
        }
    }

    /**
     * Method that inserts or updates one row in DB
     *
     * @param db           database
     * @param tableName    name of the Table
     * @param findByColumn name of column for searching
     * @param columnValue  unique value of column
     * @param values       new (or updated) values in a row
     * @return ID of newly created entry, 0 if update is successful or -1 if was error during update
     */
    private static long upsert(SQLiteDatabase db, String tableName,
                               String findByColumn, Object columnValue, ContentValues values) {
        Cursor cursor = null;
        try {
            cursor = db.query(
                    tableName,
                    new String[]{findByColumn},
                    sqlEqual(findByColumn, columnValue),
                    null, null, null, null);

            cursor.moveToFirst();
            if (cursor.getCount() == 0) {
                return db.insert(tableName, null, values);
            } else {
                int count = db.update(tableName,
                        values,
                        sqlEqual(findByColumn, columnValue),
                        null);

                return (count == 1) ? 0 : -1;
            }
        } finally {
            Utils.closeQuietly(cursor);
        }
    }

    private static ContentValues createLinkRow(Link[] links) {
        ContentValues result = new ContentValues();
        for (int i = 0; i < Networks.NETWORK_COUNT; i++) {
            if (links[i] != null && links[i].isValid()) {
                result.put(LinksEntry.LINK_COLUMNS[i], links[i].get());
            }
        }
        return result;
    }

    private static ContentValues createLocationRow(Location location) {
        ContentValues result = new ContentValues();
        result.put(LocationEntry.COLUMN_NAME_LATITUDE, location.latitude);
        result.put(LocationEntry.COLUMN_NAME_LONGITUDE, location.longitude);
        result.put(LocationEntry.COLUMN_NAME_NAME, location.name);
        return result;
    }

    private static ContentValues createAttachmentsRow(Attachment attachment, long prev, long atLink) {
        ContentValues result = new ContentValues();
        result.put(AttachmentsEntry.COLUMN_NAME_TYPE, attachment.getType());
        result.put(AttachmentsEntry.COLUMN_NAME_EXTRA, attachment.getExtra());
        result.put(AttachmentsEntry.COLUMN_NAME_PREV, prev);
        result.put(AttachmentsEntry.COLUMN_NAME_NEXT, AttachmentsEntry.END_OF_LIST_VALUE);
        result.put(AttachmentsEntry.COLUMN_NAME_LINK, atLink);
        return result;
    }

    private static ContentValues createPostRow(Post post, long atId, long linkId, long locId) {
        ContentValues values = new ContentValues();
        values.put(PostEntry.COLUMN_NAME_TEXT, post.getText());
        values.put(PostEntry.COLUMN_NAME_FIRST_ATTACHMENT, atId);
        values.put(PostEntry.COLUMN_NAME_LINKS, linkId);
        values.put(PostEntry.COLUMN_NAME_DATE, post.getDate());
        values.put(PostEntry.COLUMN_NAME_LOCATION, locId);
        return values;
    }

    private static Location readLocation(SQLiteDatabase db, long locId) throws DatabaseException {
        Cursor cursor = null;
        try {
            cursor = db.query(
                    LocationEntry.TABLE_NAME,
                    LocationEntry.LOCATION_COLUMNS,
                    sqlEqual(LocationEntry._ID, locId),
                    null, null, null, null);

            cursor.moveToFirst();
            if (cursor.getCount() == 0) {
                throw new DatabaseException("Can't read location: " + locId);
            }
            String name = cursor.getString(cursor.getColumnIndex(LocationEntry.COLUMN_NAME_NAME));
            double longitude = cursor.getDouble(cursor.getColumnIndex(LocationEntry.COLUMN_NAME_LONGITUDE));
            double latitude = cursor.getDouble(cursor.getColumnIndex(LocationEntry.COLUMN_NAME_LATITUDE));

            return new Location(latitude, longitude, name);
        } finally {
            Utils.closeQuietly(cursor);
        }
    }

    private static Link[] readLinks(SQLiteDatabase db, long linksId) throws DatabaseException {
        Cursor cursor = null;
        try {
            cursor = db.query(
                    LinksEntry.TABLE_NAME,
                    LinksEntry.LINK_COLUMNS,
                    sqlEqual(LocationEntry._ID, linksId),
                    null, null, null, null);

            cursor.moveToFirst();

            if (cursor.getCount() == 0) {
                throw new DatabaseException("Can't read links");
            }

            // Links aren't valid, when we load it from DB
            Link[] links = new Link[Networks.NETWORK_COUNT];
            for (int i = 0; i < Networks.NETWORK_COUNT; i++) {
                String link = cursor.getString(cursor.getColumnIndex(LinksEntry.LINK_COLUMNS[i]));
                if (link == null) {
                    links[i] = null;
                } else {
                    links[i] = new Link(link, false);
                }
            }
            return links;
        } finally {
            Utils.closeQuietly(cursor);
        }
    }

    private static ArrayList<Attachment> readAttachments(SQLiteDatabase db, long firstId) throws DatabaseException {
        ArrayList<Attachment> list = new ArrayList<>();

        Cursor cursor = null;
        long nextId = firstId;

        try {
            while (nextId != AttachmentsEntry.END_OF_LIST_VALUE) {
                cursor = db.query(
                        AttachmentsEntry.TABLE_NAME,
                        AttachmentsEntry.ATTACHMENTS_COLUMNS,
                        sqlEqual(AttachmentsEntry._ID, nextId),
                        null, null, null, null);

                cursor.moveToFirst();
                if (cursor.getCount() == 0) {
                    throw new DatabaseException("Can't read attachment");
                }
                int type = cursor.getInt(cursor.getColumnIndex(AttachmentsEntry.COLUMN_NAME_TYPE));
                String extra = cursor.getString(cursor.getColumnIndex(AttachmentsEntry.COLUMN_NAME_EXTRA));
                nextId = cursor.getLong(cursor.getColumnIndex(AttachmentsEntry.COLUMN_NAME_NEXT));
                long linkId = cursor.getLong(cursor.getColumnIndex(AttachmentsEntry.COLUMN_NAME_LINK));

                Link[] links = readLinks(db, linkId);
                list.add(makeAttachment(type, extra, links));
            }
        } finally {
            Utils.closeQuietly(cursor);
        }
        return list;
    }

    private static Attachment makeAttachment(int type, String extra, Link[] links) {
        switch (type) {
            case Attachment.IMAGE:
                return new LoudlyImage(extra, links);
            default:
                return null;
        }
    }

    private static long deleteAttachment(SQLiteDatabase db, long atId) throws DatabaseException {
        Cursor cursor = null;
        long nextId = AttachmentsEntry.END_OF_LIST_VALUE;
        try {
            cursor = db.query(AttachmentsEntry.TABLE_NAME, AttachmentsEntry.ATTACHMENTS_COLUMNS,
                    sqlEqual(AttachmentsEntry._ID, atId), null, null, null, null);

            cursor.moveToFirst();
            if (cursor.getCount() == 0) {
                throw new DatabaseException("Can't find attachment: " + atId);
            }
            long linkId = cursor.getLong(cursor.getColumnIndex(AttachmentsEntry.COLUMN_NAME_LINK));
            nextId = cursor.getLong(cursor.getColumnIndex(AttachmentsEntry.COLUMN_NAME_NEXT));


            if (db.delete(LinksEntry.TABLE_NAME, sqlEqual(LinksEntry._ID, linkId), null) != 1) {
                throw new DatabaseException("Can't delete attachment's link: " + linkId);
            }
            if (db.delete(AttachmentsEntry.TABLE_NAME, sqlEqual(AttachmentsEntry._ID, atId), null) != 1) {
                throw new DatabaseException("Can't delete attachment: " + atId);
            }
        } finally {
            Utils.closeQuietly(cursor);
        }
        return nextId;
    }

    private static String sqlEqual(String column, Object value) {
        return column + " = " + value.toString();
    }
}