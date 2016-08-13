package ly.loud.loudly.ui.adapters;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import ly.loud.loudly.base.plain.PlainPost;
import ly.loud.loudly.ui.adapters.holders.ViewHolderPost;
import ly.loud.loudly.ui.adapters.holders.ViewHolderPost.ViewHolderPostClickListener;
import solid.collections.SolidList;

public class FeedAdapter extends RecyclerView.Adapter<ViewHolderPost>
        implements ViewHolderPostClickListener {

    @NonNull
    private final List<PlainPost> posts;

    @NonNull
    private final PostClickListener listener;

    public FeedAdapter(@NonNull PostClickListener listener) {
        this.listener = listener;
        this.posts = new ArrayList<>();
    }

    @Override
    public ViewHolderPost onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return ViewHolderPost.provideNewHolderWithListener(
                LayoutInflater.from(parent.getContext()),
                parent,
                this
        );
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderPost holder, int position) {
        holder.bind(posts.get(position));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public void setPosts(@NonNull SolidList<PlainPost> newPosts) {
        posts.clear();
        posts.addAll(newPosts);
        notifyDataSetChanged();
    }

    public void updatePosts(@NonNull SolidList<PlainPost> newPosts) {
        // ToDo: notify better
        setPosts(newPosts);
    }

    @Override
    public void onFullPostClick(int position) {
        listener.onFullPostClick(posts.get(position));
    }

    @Override
    public void onSharesClick(int position) {
        listener.onSharesClick(posts.get(position));
    }

    @Override
    public void onLikesClick(int position) {
        listener.onLikesClick(posts.get(position));
    }

    @Override
    public void onDeleteClick(int position) {
        listener.onDeleteClick(posts.get(position));
    }

    public interface PostClickListener {
        void onFullPostClick(@NonNull PlainPost post);

        void onSharesClick(@NonNull PlainPost post);

        void onLikesClick(@NonNull PlainPost post);

        void onDeleteClick(@NonNull PlainPost post);
    }
}
